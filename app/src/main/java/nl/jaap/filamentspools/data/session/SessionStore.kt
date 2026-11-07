package nl.jaap.filamentspools.data.session

import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** A paired device: where the server is and which token to use. */
data class Session(
    val baseUrl: String,
    val token: String,
    val abilities: Set<String>,
    val userName: String,
)

enum class SignOutReason { User, TokenInvalid }

const val ABILITY_WRITE = "spools:write"
const val CURSOR_SPOOLS = "spools"
const val CURSOR_CATALOG = "catalog"

interface SessionStore {
    /** The paired session, or null when not paired (or not loaded yet; see [loaded]). */
    val current: StateFlow<Session?>

    /** True once the stored session has been read from disk. */
    val loaded: StateFlow<Boolean>

    /** Why the last sign-out happened, until cleared. */
    val signOutReason: StateFlow<SignOutReason?>

    val lastSync: StateFlow<Instant?>

    val canWrite: Boolean get() = ABILITY_WRITE in (current.value?.abilities ?: emptySet())

    suspend fun signIn(session: Session)
    suspend fun signOut(reason: SignOutReason)
    suspend fun updateAbilities(abilities: Set<String>)
    suspend fun clearSignOutReason()
    suspend fun cursor(key: String): String?
    suspend fun setCursor(key: String, value: String?)
    suspend fun setLastSync(at: Instant)
}
