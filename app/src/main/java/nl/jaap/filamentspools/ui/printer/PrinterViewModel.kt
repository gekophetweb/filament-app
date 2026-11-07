package nl.jaap.filamentspools.ui.printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.data.SpoolActions
import nl.jaap.filamentspools.data.Syncer
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.ProductWithCounts
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.db.SpoolStatus
import nl.jaap.filamentspools.data.db.SpoolWithProduct
import nl.jaap.filamentspools.data.session.ABILITY_WRITE
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.ui.common.Messages
import java.time.Instant
import javax.inject.Inject

data class PrinterUiState(
    val query: String = "",
    val onHolder: List<SpoolWithProduct> = emptyList(),
    val runningOut: List<ProductWithCounts> = emptyList(),
    val boxed: List<SpoolWithProduct> = emptyList(),
    val canWrite: Boolean = false,
    val busyUuids: Set<String> = emptySet(),
    val offlineSince: Instant? = null,
    val syncError: String? = null,
    val refreshing: Boolean = false,
    val neverSynced: Boolean = false,
)

private data class Lists(
    val onHolder: List<SpoolWithProduct>,
    val runningOut: List<ProductWithCounts>,
    val boxed: List<SpoolWithProduct>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrinterViewModel @Inject constructor(
    spoolDao: SpoolDao,
    productDao: ProductDao,
    private val actions: SpoolActions,
    private val syncer: Syncer,
    private val session: SessionStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off snackbar texts. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val lists: Flow<Lists> = query.flatMapLatest { q ->
        combine(
            spoolDao.observeByStatus(SpoolStatus.ON_SPOOL, q),
            productDao.observeRunningOut(),
            spoolDao.observeByStatus(SpoolStatus.BOXED, q),
        ) { onHolder, runningOut, boxed -> Lists(onHolder, runningOut.filter { it.matches(q) }, boxed) }
    }

    val state: StateFlow<PrinterUiState> =
        combine(query, lists, busy, session.current, combine(syncer.state, session.lastSync, ::Pair)) { q, l, b, current, (sync, lastSync) ->
            val error = sync.lastError
            PrinterUiState(
                query = q,
                onHolder = l.onHolder,
                runningOut = l.runningOut,
                boxed = l.boxed,
                canWrite = current != null && ABILITY_WRITE in current.abilities,
                busyUuids = b,
                offlineSince = if (error is ApiError.Network) lastSync else null,
                syncError = if (error != null && error !is ApiError.Network) Messages.text(error, current?.baseUrl) else null,
                refreshing = sync.running,
                neverSynced = lastSync == null,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PrinterUiState())

    fun onQuery(value: String) = query.update { value }

    fun refresh() {
        viewModelScope.launch { syncer.sync() }
    }

    fun open(uuid: String) = act(uuid, "Put on the holder") { actions.open(uuid) }

    /** Called after the screen's confirm dialog. */
    fun useUp(uuid: String) = act(uuid, "Marked used up") { actions.useUp(uuid) }

    private fun act(uuid: String, success: String, call: suspend () -> Result<Unit>) {
        if (uuid in busy.value) return
        busy.update { it + uuid }
        viewModelScope.launch {
            val result = call()
            busy.update { it - uuid }
            _messages.send(result.fold({ success }, { Messages.text(it, session.current.value?.baseUrl) }))
        }
    }
}

private fun ProductWithCounts.matches(q: String): Boolean {
    val needle = q.trim()
    if (needle.isEmpty()) return true
    return listOf(product.articleName, product.articleNumber, product.colorFamily, product.line, product.material)
        .any { it?.contains(needle, ignoreCase = true) == true }
}
