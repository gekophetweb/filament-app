package nl.jaap.filamentspools.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.data.session.SessionStore
import nl.jaap.filamentspools.data.session.SignOutReason

open class FakeSessionStore(initial: Session? = null) : SessionStore {
    val state = MutableStateFlow(initial)
    override val current: StateFlow<Session?> = state
    val signOuts = mutableListOf<SignOutReason>()

    override suspend fun signOut(reason: SignOutReason) {
        signOuts += reason
        state.value = null
    }
}
