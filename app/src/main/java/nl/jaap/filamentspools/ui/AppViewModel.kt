package nl.jaap.filamentspools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jaap.filamentspools.data.Syncer
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.data.session.SignOutReason
import nl.jaap.filamentspools.ui.common.Messages
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data class NeedsPairing(val message: String?) : AppState
    data object Ready : AppState
}

/** Session gate: decides between pairing and the main app, clears the cache on sign-out, syncs on start. */
@HiltViewModel
class AppViewModel @Inject constructor(
    session: SessionStore,
    private val syncer: Syncer,
    private val spoolDao: SpoolDao,
    private val productDao: ProductDao,
) : ViewModel() {

    val state: StateFlow<AppState> =
        combine(session.loaded, session.current, session.signOutReason) { loaded, current, reason ->
            when {
                !loaded -> AppState.Loading
                current == null ->
                    AppState.NeedsPairing(if (reason == SignOutReason.TokenInvalid) Messages.TOKEN_INVALID else null)
                else -> AppState.Ready
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    init {
        viewModelScope.launch {
            combine(session.loaded, session.current) { loaded, current -> loaded to (current != null) }
                .filter { (loaded, _) -> loaded }
                .map { (_, signedIn) -> signedIn }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn) {
                        syncer.sync()
                    } else {
                        spoolDao.clear()
                        productDao.clear()
                    }
                }
        }
    }
}
