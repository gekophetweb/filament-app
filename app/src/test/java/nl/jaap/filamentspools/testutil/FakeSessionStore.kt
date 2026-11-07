package nl.jaap.filamentspools.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.data.session.SignOutReason
import java.time.Instant

open class FakeSessionStore(initial: Session? = null) : SessionStore {
    val state = MutableStateFlow(initial)
    override val current: StateFlow<Session?> = state
    override val loaded = MutableStateFlow(true)
    override val signOutReason = MutableStateFlow<SignOutReason?>(null)
    override val lastSync = MutableStateFlow<Instant?>(null)
    val cursors = mutableMapOf<String, String>()
    val signOuts = mutableListOf<SignOutReason>()
    var lastUrl: String? = null

    override suspend fun lastServerUrl(): String? = lastUrl ?: state.value?.baseUrl

    override suspend fun signIn(session: Session) {
        state.value = session
        signOutReason.value = null
    }

    override suspend fun signOut(reason: SignOutReason) {
        signOuts += reason
        state.value = null
        cursors.clear()
        signOutReason.value = reason
    }

    override suspend fun updateAbilities(abilities: Set<String>) {
        state.value = state.value?.copy(abilities = abilities)
    }

    override suspend fun clearSignOutReason() {
        signOutReason.value = null
    }

    override suspend fun cursor(key: String): String? = cursors[key]

    override suspend fun setCursor(key: String, value: String?) {
        if (value == null) cursors.remove(key) else cursors[key] = value
    }

    override suspend fun setLastSync(at: Instant) {
        lastSync.value = at
    }
}
