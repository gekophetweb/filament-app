package nl.jaap.filamentspools.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.jaap.filamentspools.testutil.InMemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionStoreTest {

    private val cipher = object : TokenCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(cipher: String) = cipher.removePrefix("enc:").reversed()
    }

    private val session = Session("http://10.0.2.2:8000/api/v1/", "7|secret-token", setOf("spools:read", "spools:write"), "Alex")

    /** Backing store shared across store instances, like the file on disk. */
    private val disk = InMemoryPreferences()

    private suspend fun <T> withStore(block: suspend (DataStoreSessionStore) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DataStoreSessionStore(disk, cipher, scope)
        withTimeout(5_000) { store.loaded.first { it } }
        return try { block(store) } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test
    fun signInRoundTrips() = runBlocking {
        withStore { it.signIn(session) }
        val restored = withStore { store -> withTimeout(5_000) { store.current.first { it != null } } }
        assertEquals(session, restored)
    }

    @Test
    fun tokenIsStoredEncrypted() = runBlocking {
        withStore { it.signIn(session) }
        val raw = disk.state.value.asMap().values.joinToString()
        assertFalse(raw, raw.contains("7|secret-token"))
        assertTrue(raw, raw.contains("http://10.0.2.2:8000/api/v1/"))
    }

    @Test
    fun signOutClearsTokenAndCursors() = runBlocking {
        withStore { store ->
            store.signIn(session)
            store.setCursor(CURSOR_SPOOLS, "2026-11-01T09:30:00Z")
            store.signOut(SignOutReason.TokenInvalid)
            withTimeout(5_000) { store.current.first { it == null } }
            assertNull(store.cursor(CURSOR_SPOOLS))
            assertEquals(SignOutReason.TokenInvalid, withTimeout(5_000) { store.signOutReason.first { it != null } })
        }
    }

    @Test
    fun canWriteReflectsAbilities() = runBlocking {
        withStore { store ->
            store.signIn(session)
            withTimeout(5_000) { store.current.first { it != null } }
            assertTrue(store.canWrite)
            store.updateAbilities(setOf("spools:read"))
            withTimeout(5_000) { store.current.first { it?.abilities == setOf("spools:read") } }
            assertFalse(store.canWrite)
        }
    }

    @Test
    fun lastSyncPersists() = runBlocking {
        val at = Instant.parse("2026-11-07T10:00:00Z")
        withStore { it.setLastSync(at) }
        assertEquals(at, withStore { store -> withTimeout(5_000) { store.lastSync.first { it != null } } })
    }
}
