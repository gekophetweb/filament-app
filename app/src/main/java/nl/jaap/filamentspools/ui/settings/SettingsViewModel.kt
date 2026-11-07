package nl.jaap.filamentspools.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jaap.filamentspools.data.Syncer
import nl.jaap.filamentspools.data.session.ABILITY_WRITE
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.data.session.SignOutReason
import nl.jaap.filamentspools.ui.common.Messages
import java.time.Instant
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val userName: String = "",
    val tokenType: String = "",
    val lastSync: Instant? = null,
    val syncing: Boolean = false,
    val syncError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionStore,
    private val syncer: Syncer,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(session.current, session.lastSync, syncer.state) { current, lastSync, sync ->
            SettingsUiState(
                serverUrl = current?.baseUrl.orEmpty(),
                userName = current?.userName.orEmpty(),
                tokenType = if (current != null && ABILITY_WRITE in current.abilities) "Read-write" else "Read-only",
                lastSync = lastSync,
                syncing = sync.running,
                syncError = sync.lastError?.let { Messages.text(it, current?.baseUrl) },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun syncNow() {
        viewModelScope.launch { syncer.sync() }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut(SignOutReason.User) }
    }

    /** Changing the server means pairing again with it. */
    fun changeServer() = signOut()
}
