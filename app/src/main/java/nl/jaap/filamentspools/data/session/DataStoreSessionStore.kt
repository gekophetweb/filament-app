package nl.jaap.filamentspools.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/** [SessionStore] on DataStore Preferences. The token is encrypted with [TokenCipher]. */
class DataStoreSessionStore(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
    scope: CoroutineScope,
) : SessionStore {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token_enc")
        val abilities = stringSetPreferencesKey("abilities")
        val userName = stringPreferencesKey("user_name")
        val signOutReason = stringPreferencesKey("sign_out_reason")
        val lastSync = stringPreferencesKey("last_sync")
        fun cursor(key: String) = stringPreferencesKey("cursor_$key")
    }

    private val prefs: StateFlow<Preferences?> =
        store.data.stateIn(scope, SharingStarted.Eagerly, null)

    override val loaded: StateFlow<Boolean> =
        prefs.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    override val current: StateFlow<Session?> =
        prefs.map { it?.toSession() }.stateIn(scope, SharingStarted.Eagerly, null)

    override val signOutReason: StateFlow<SignOutReason?> =
        prefs.map { p -> p?.get(Keys.signOutReason)?.let { runCatching { SignOutReason.valueOf(it) }.getOrNull() } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val lastSync: StateFlow<Instant?> =
        prefs.map { p -> p?.get(Keys.lastSync)?.let { runCatching { Instant.parse(it) }.getOrNull() } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private fun Preferences.toSession(): Session? {
        val url = this[Keys.baseUrl] ?: return null
        val token = this[Keys.token]?.let { runCatching { cipher.decrypt(it) }.getOrNull() } ?: return null
        return Session(url, token, this[Keys.abilities].orEmpty(), this[Keys.userName].orEmpty())
    }

    override suspend fun signIn(session: Session) {
        store.edit {
            it.clear()
            it[Keys.baseUrl] = session.baseUrl
            it[Keys.token] = cipher.encrypt(session.token)
            it[Keys.abilities] = session.abilities
            it[Keys.userName] = session.userName
        }
    }

    override suspend fun signOut(reason: SignOutReason) {
        store.edit {
            val url = it[Keys.baseUrl]
            it.clear()
            // Keep the server address so pairing again is quicker.
            if (url != null) it[Keys.baseUrl] = url
            it[Keys.signOutReason] = reason.name
        }
    }

    override suspend fun updateAbilities(abilities: Set<String>) {
        store.edit { it[Keys.abilities] = abilities }
    }

    override suspend fun clearSignOutReason() {
        store.edit { it.remove(Keys.signOutReason) }
    }

    override suspend fun cursor(key: String): String? = store.data.first()[Keys.cursor(key)]

    override suspend fun setCursor(key: String, value: String?) {
        store.edit { if (value == null) it.remove(Keys.cursor(key)) else it[Keys.cursor(key)] = value }
    }

    override suspend fun setLastSync(at: Instant) {
        store.edit { it[Keys.lastSync] = at.toString() }
    }

    /** Server address left after sign-out, to prefill pairing. */
    suspend fun lastServerUrl(): String? = store.data.first()[Keys.baseUrl]
}
