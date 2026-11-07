package nl.jaap.filamentspools.data.session

import kotlinx.coroutines.flow.StateFlow

/** A paired device: where the server is and which token to use. */
data class Session(
    val baseUrl: String,
    val token: String,
    val abilities: Set<String>,
    val userName: String,
)

enum class SignOutReason { User, TokenInvalid }

interface SessionStore {
    val current: StateFlow<Session?>
    suspend fun signOut(reason: SignOutReason)
}
