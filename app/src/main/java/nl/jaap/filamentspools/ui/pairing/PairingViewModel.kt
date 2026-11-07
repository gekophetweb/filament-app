package nl.jaap.filamentspools.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.data.Pairer
import nl.jaap.filamentspools.ui.common.Messages
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named

data class PairingUiState(
    val serverUrl: String = "",
    val code: String = "",
    val deviceName: String = "",
    val expiresAt: Instant? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairer: Pairer,
    private val clock: Clock,
    @Named("deviceName") deviceName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState(deviceName = deviceName))
    val state: StateFlow<PairingUiState> = _state

    fun onScanned(text: String) {
        val payload = PairingPayloadParser.parse(text)
        if (payload == null) {
            _state.update { it.copy(error = NOT_PAIRING_QR) }
            return
        }
        _state.update {
            it.copy(
                serverUrl = payload.api.ifBlank { it.serverUrl },
                code = payload.code,
                expiresAt = payload.expiresAt,
                error = if (isExpired(payload.expiresAt)) EXPIRED else null,
            )
        }
    }

    fun onServerUrl(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onCode(value: String) = _state.update { it.copy(code = value, expiresAt = null, error = null) }
    fun onDeviceName(value: String) = _state.update { it.copy(deviceName = value) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        when {
            s.serverUrl.isBlank() || s.code.isBlank() -> { _state.update { it.copy(error = MISSING) }; return }
            isExpired(s.expiresAt) -> { _state.update { it.copy(error = EXPIRED) }; return }
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = pairer.pair(s.serverUrl, s.code, s.deviceName)
            val error = result.exceptionOrNull()?.let {
                if (it == ApiError.Unauthorized) BAD_CODE else Messages.text(it, s.serverUrl.trim())
            }
            _state.update { it.copy(busy = false, error = error) }
        }
    }

    private fun isExpired(expiresAt: Instant?) = expiresAt != null && expiresAt.isBefore(clock.instant())

    companion object {
        const val NOT_PAIRING_QR = "This QR code is not a Filament Spools pairing code"
        const val EXPIRED = "This code has expired — show a new QR on the Devices page"
        const val BAD_CODE = "Code invalid, used, expired, or the token limit (10) is reached"
        const val MISSING = "Enter the server address and the pairing code"
    }
}
