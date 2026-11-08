package nl.jaap.filamentspools.ui.catalog

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.MAX_SPOOLS_PER_ADD
import nl.jaap.filamentspools.data.SpoolActions
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.ProductWithCounts
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.db.SpoolWithProduct
import nl.jaap.filamentspools.data.session.ABILITY_WRITE
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.ui.common.Messages
import nl.jaap.filamentspools.ui.common.parseDateField
import javax.inject.Inject

data class AddSpoolForm(
    val amount: String = "1",
    val onSpool: Boolean = false,
    val isRefill: Boolean = false,
    val purchasedAt: String = "",
)

data class ProductUiState(
    val product: ProductWithCounts? = null,
    val spools: List<SpoolWithProduct> = emptyList(),
    val form: AddSpoolForm = AddSpoolForm(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val saving: Boolean = false,
    val canWrite: Boolean = false,
) {
    val showRefill: Boolean get() = product?.product?.hasRefill == true
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    productDao: ProductDao,
    spoolDao: SpoolDao,
    private val actions: SpoolActions,
    private val session: SessionStore,
) : ViewModel() {

    private val productId: Long = checkNotNull(savedStateHandle.get<Long>("id")) { "product id missing" }

    private data class Edit(
        val form: AddSpoolForm = AddSpoolForm(),
        val fieldErrors: Map<String, String> = emptyMap(),
        val formError: String? = null,
        val saving: Boolean = false,
    )

    private val edit = MutableStateFlow(Edit())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<ProductUiState> =
        combine(productDao.observe(productId), spoolDao.observeForProduct(productId), edit, session.current) { p, spools, e, s ->
            ProductUiState(
                product = p,
                spools = spools,
                form = e.form,
                fieldErrors = e.fieldErrors,
                formError = e.formError,
                saving = e.saving,
                canWrite = s != null && ABILITY_WRITE in s.abilities,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ProductUiState())

    fun onForm(form: AddSpoolForm) = edit.update { it.copy(form = form, fieldErrors = emptyMap(), formError = null) }

    fun save() {
        val e = edit.value
        if (e.saving) return
        val f = e.form
        val date = parseDateField(f.purchasedAt)
        val amount = f.amount.trim().toIntOrNull()?.takeIf { it in 1..MAX_SPOOLS_PER_ADD }
        val errors = buildMap {
            if (amount == null) put("amount", "Enter 1 to $MAX_SPOOLS_PER_ADD")
            if (date.isFailure) put("purchased_at", "Use YYYY-MM-DD")
        }
        if (errors.isNotEmpty()) {
            edit.update { it.copy(fieldErrors = errors) }
            return
        }
        val refill = f.isRefill && state.value.showRefill
        edit.update { it.copy(saving = true, fieldErrors = emptyMap(), formError = null) }
        viewModelScope.launch {
            val result = actions.add(
                productId = productId,
                amount = amount!!,
                onSpool = f.onSpool,
                isRefill = refill,
                purchasedAt = date.getOrNull(),
            )
            result.onSuccess { uuids ->
                edit.value = Edit()
                _messages.send(if (uuids.size == 1) "Spool added" else "${uuids.size} spools added")
            }.onFailure { error ->
                val fields = (error as? ApiError.Validation)?.fields.orEmpty()
                    .mapValues { (_, v) -> v.firstOrNull().orEmpty() }
                    .filterValues { it.isNotEmpty() }
                edit.update {
                    it.copy(
                        saving = false,
                        fieldErrors = fields,
                        formError = if (fields.isEmpty()) Messages.text(error, session.current.value?.baseUrl) else null,
                    )
                }
            }
        }
    }
}
