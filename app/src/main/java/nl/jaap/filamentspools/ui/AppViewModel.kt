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

    // `current` is read via .value: the store sets it before `loaded`, so it is never stale here.
    val state: StateFlow<AppState> =
        combine(session.loaded, session.current, session.signOutReason) { loaded, _, reason ->
            val current = session.current.value
            when {
                !loaded -> AppState.Loading
                current == null ->
                    AppState.NeedsPairing(if (reason == SignOutReason.TokenInvalid) Messages.TOKEN_INVALID else null)
                else -> AppState.Ready
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    init {
        viewModelScope.launch {
            var wasSignedIn = false
            combine(session.loaded, session.current) { loaded, _ -> loaded to (session.current.value != null) }
                .filter { (loaded, _) -> loaded }
                .map { (_, signedIn) -> signedIn }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn) {
                        wasSignedIn = true
                        launch { syncer.sync() }
                    } else if (wasSignedIn) {
                        // Only an actual sign-out clears the cache (sign-out also clears the sync cursors).
                        // A cold start never does, so a slow session load cannot wipe the offline copy.
                        wasSignedIn = false
                        spoolDao.clear()
                        productDao.clear()
                    }
                }
        }
    }
}
