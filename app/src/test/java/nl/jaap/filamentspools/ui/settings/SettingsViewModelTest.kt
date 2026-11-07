package nl.jaap.filamentspools.ui.settings

import kotlinx.coroutines.runBlocking
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.data.session.SignOutReason
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.FakeSyncer
import nl.jaap.filamentspools.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val syncer = FakeSyncer()

    private fun session(abilities: Set<String>) =
        FakeSessionStore(Session("http://10.0.2.2:8000/api/v1/", "t", abilities, "Alex"))

    @Test
    fun tokenTypeReadOnly() {
        val vm = SettingsViewModel(session(setOf("spools:read")), syncer)
        assertEquals("Read-only", vm.state.value.tokenType)
        assertEquals("Alex", vm.state.value.userName)
        assertEquals("http://10.0.2.2:8000/api/v1/", vm.state.value.serverUrl)
    }

    @Test
    fun tokenTypeReadWrite() {
        val vm = SettingsViewModel(session(setOf("spools:read", "spools:write")), syncer)
        assertEquals("Read-write", vm.state.value.tokenType)
    }

    @Test
    fun lastSyncShown() {
        val s = session(setOf("spools:read"))
        val vm = SettingsViewModel(s, syncer)
        s.lastSync.value = Instant.parse("2026-11-07T10:00:00Z")
        assertEquals(Instant.parse("2026-11-07T10:00:00Z"), vm.state.value.lastSync)
    }

    @Test
    fun syncNowSyncs() {
        val vm = SettingsViewModel(session(setOf("spools:read")), syncer)
        vm.syncNow()
        assertEquals(1, syncer.calls)
    }

    @Test
    fun signOutUsesUserReason() = runBlocking {
        val s = session(setOf("spools:read"))
        SettingsViewModel(s, syncer).signOut()
        assertEquals(listOf(SignOutReason.User), s.signOuts)
    }
}
