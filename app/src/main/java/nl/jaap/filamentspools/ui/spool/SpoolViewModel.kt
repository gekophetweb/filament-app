package nl.jaap.filamentspools.ui.spool

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.SpoolEventDto
import nl.jaap.filamentspools.data.Change
import nl.jaap.filamentspools.data.SpoolActions
import nl.jaap.filamentspools.data.SpoolChanges
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.db.SpoolEntity
import nl.jaap.filamentspools.data.db.SpoolStatus
import nl.jaap.filamentspools.data.db.SpoolWithProduct
import nl.jaap.filamentspools.data.session.ABILITY_WRITE
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.ui.common.Messages
import nl.jaap.filamentspools.ui.common.parseDateField
import nl.jaap.filamentspools.ui.common.parsePriceField
import java.math.BigDecimal
import javax.inject.Inject

data class EditForm(
    val isRefill: Boolean = false,
    val purchasedAt: String = "",
    val price: String = "",
    val location: String = "",
    val notes: String = "",
)

data class SpoolUiState(
    val spool: SpoolWithProduct? = null,
    val editing: EditForm? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val events: List<SpoolEventDto> = emptyList(),
    val eventsError: String? = null,
    val hasMoreEvents: Boolean = false,
    val busy: Boolean = false,
    val canWrite: Boolean = false,
    /** Set when the spool is gone (removed here, or no longer on the server); the screen leaves. */
    val goneMessage: String? = null,
) {
    private val status get() = spool?.spool?.status
    val canOpen get() = canWrite && status == SpoolStatus.BOXED
    val canUseUp get() = canWrite && (status == SpoolStatus.BOXED || status == SpoolStatus.ON_SPOOL)
    val canRemove get() = canWrite && spool != null
    val canEdit get() = canWrite && spool != null
}

private data class Local(
    val editing: EditForm? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val events: List<SpoolEventDto> = emptyList(),
    val eventsError: String? = null,
    val nextEventPage: Int? = 1,
    val loadingEvents: Boolean = false,
    val busy: Boolean = false,
    val removing: Boolean = false,
    val goneMessage: String? = null,
)

@HiltViewModel
class SpoolViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    spoolDao: SpoolDao,
    private val actions: SpoolActions,
    private val session: SessionStore,
) : ViewModel() {

    private val uuid: String = checkNotNull(savedStateHandle.get<String>("uuid")) { "spool uuid missing" }
    private val local = MutableStateFlow(Local())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Pair of (loaded, row) so "not loaded yet" differs from "not in the cache". */
    private val row = spoolDao.observe(uuid).map { true to it }

    val state: StateFlow<SpoolUiState> =
        combine(row, local, session.current) { (loaded, spool), l, s ->
            val gone = l.goneMessage
                ?: if (loaded && spool == null) (if (l.removing) "Spool removed" else Messages.SPOOL_GONE) else null
            SpoolUiState(
                spool = spool,
                editing = l.editing,
                fieldErrors = l.fieldErrors,
                formError = l.formError,
                events = l.events,
                eventsError = l.eventsError,
                hasMoreEvents = l.nextEventPage != null && l.eventsError == null && !l.loadingEvents,
                busy = l.busy,
                canWrite = s != null && ABILITY_WRITE in s.abilities,
                goneMessage = gone,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SpoolUiState())

    init {
        loadMoreEvents()
    }

    fun loadMoreEvents() {
        val current = local.value
        if (current.loadingEvents) return
        val page = current.nextEventPage ?: return
        local.update { it.copy(loadingEvents = true) }
        viewModelScope.launch {
            actions.events(uuid, page)
                .onSuccess { p ->
                    local.update {
                        it.copy(events = it.events + p.data, nextEventPage = if (p.links.next != null && p.data.isNotEmpty()) page + 1 else null, eventsError = null, loadingEvents = false)
                    }
                }
                .onFailure { e ->
                    val text = if (e is ApiError.Network) "History needs a connection to the server" else Messages.text(e, serverUrl())
                    local.update { it.copy(eventsError = text, loadingEvents = false) }
                }
        }
    }

    fun startEdit() {
        val s = state.value.spool?.spool ?: return
        local.update { it.copy(editing = s.toForm(), fieldErrors = emptyMap(), formError = null) }
    }

    fun cancelEdit() = local.update { it.copy(editing = null, fieldErrors = emptyMap(), formError = null) }

    fun onEdit(form: EditForm) = local.update { it.copy(editing = form, fieldErrors = emptyMap(), formError = null) }

    fun saveEdit() {
        val form = local.value.editing ?: return
        val cached = state.value.spool?.spool ?: return
        if (local.value.busy) return
        val date = parseDateField(form.purchasedAt)
        val price = parsePriceField(form.price)
        val errors = buildMap {
            if (date.isFailure) put("purchased_at", "Use YYYY-MM-DD")
            if (price.isFailure) put("price", "Enter a number")
        }
        if (errors.isNotEmpty()) {
            local.update { it.copy(fieldErrors = errors) }
            return
        }
        val changes = diff(cached, form, date.getOrNull(), price.getOrNull())
        if (changes.isEmpty) {
            cancelEdit()
            return
        }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            actions.update(uuid, changes)
                .onSuccess {
                    local.update { it.copy(busy = false, editing = null) }
                    _messages.send("Saved")
                }
                .onFailure { e -> fail(e, keepEditing = true) }
        }
    }

    fun open() = act("Put on the holder") { actions.open(uuid) }

    fun useUp() = act("Marked used up") { actions.useUp(uuid) }

    fun remove(note: String?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, removing = true) }
        viewModelScope.launch {
            actions.remove(uuid, note?.trim()?.ifBlank { null })
                .onSuccess { local.update { it.copy(busy = false, goneMessage = "Spool removed") } }
                .onFailure { e -> local.update { it.copy(removing = false) }; fail(e, keepEditing = false) }
        }
    }

    private fun act(success: String, call: suspend () -> Result<Unit>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            call()
                .onSuccess {
                    local.update { it.copy(busy = false) }
                    _messages.send(success)
                }
                .onFailure { e -> fail(e, keepEditing = false) }
        }
    }

    private suspend fun fail(e: Throwable, keepEditing: Boolean) {
        if (e == ApiError.NotFound) {
            local.update { it.copy(busy = false, goneMessage = Messages.SPOOL_GONE) }
            return
        }
        val fields = if (keepEditing) {
            (e as? ApiError.Validation)?.fields.orEmpty().mapValues { (_, v) -> v.firstOrNull().orEmpty() }.filterValues { it.isNotEmpty() }
        } else {
            emptyMap()
        }
        val text = Messages.text(e, serverUrl())
        local.update { it.copy(busy = false, fieldErrors = fields, formError = if (keepEditing && fields.isEmpty()) text else null) }
        if (!keepEditing) _messages.send(text)
    }

    private fun serverUrl() = session.current.value?.baseUrl
}

private fun SpoolEntity.toForm() = EditForm(
    isRefill = isRefill,
    purchasedAt = purchasedAt.orEmpty(),
    price = price.orEmpty(),
    location = location.orEmpty(),
    notes = notes.orEmpty(),
)

/** Only fields that differ from the cached spool; prices compare by value (24.99 == 24,990). */
private fun diff(cached: SpoolEntity, form: EditForm, date: java.time.LocalDate?, price: BigDecimal?): SpoolChanges {
    fun text(value: String) = value.trim().ifBlank { null }
    val cachedPrice = cached.price?.toBigDecimalOrNull()
    val priceChanged = when {
        price == null || cachedPrice == null -> price != cachedPrice
        else -> price.compareTo(cachedPrice) != 0
    }
    return SpoolChanges(
        isRefill = if (form.isRefill != cached.isRefill) Change.Set(form.isRefill) else Change.Unchanged,
        purchasedAt = if (date?.toString() != cached.purchasedAt) Change.Set(date) else Change.Unchanged,
        price = if (priceChanged) Change.Set(price) else Change.Unchanged,
        location = if (text(form.location) != cached.location) Change.Set(text(form.location)) else Change.Unchanged,
        notes = if (text(form.notes) != cached.notes) Change.Set(text(form.notes)) else Change.Unchanged,
    )
}
