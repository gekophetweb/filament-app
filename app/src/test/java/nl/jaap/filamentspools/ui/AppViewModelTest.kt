package nl.jaap.filamentspools.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.data.session.SignOutReason
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.FakeSyncer
import nl.jaap.filamentspools.testutil.MainDispatcherRule
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.product
import nl.jaap.filamentspools.testutil.spool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val db = inMemoryDb()
    private val syncer = FakeSyncer()
    private val signedIn = Session("http://h/api/v1/", "t", setOf("spools:read"), "Alex")

    @After
    fun close() = db.close()

    private fun vm(session: FakeSessionStore) = AppViewModel(session, syncer, db.spoolDao(), db.productDao())

    @Test
    fun loadingUntilSessionLoaded() {
        val session = FakeSessionStore().apply { loaded.value = false }
        assertEquals(AppState.Loading, vm(session).state.value)
    }

    @Test
    fun noSessionNeedsPairing() {
        assertEquals(AppState.NeedsPairing(null), vm(FakeSessionStore()).state.value)
    }

    @Test
    fun readyTriggersSyncOnce() = runBlocking {
        val session = FakeSessionStore(signedIn)
        val vm = vm(session)
        assertEquals(AppState.Ready, vm.state.value)
        session.updateAbilities(setOf("spools:read", "spools:write"))
        assertEquals(1, syncer.calls)
    }

    @Test
    fun tokenInvalidShowsMessageAndClearsCache() = runBlocking {
        db.productDao().upsert(listOf(product(1)))
        db.spoolDao().upsert(listOf(spool("a", 1)))
        val session = FakeSessionStore(signedIn)
        val vm = vm(session)

        session.signOut(SignOutReason.TokenInvalid)

        assertEquals(AppState.NeedsPairing("Your device token is no longer valid — pair again"), vm.state.value)
        withTimeout(5_000) { db.spoolDao().observeForProduct(1).first { it.isEmpty() } }

        withTimeout(5_000) { db.productDao().observe(1).first { it == null } }
        Unit
    }

    @Test
    fun userSignOutShowsNoMessage() = runBlocking {
        val session = FakeSessionStore(signedIn)
        val vm = vm(session)
        session.signOut(SignOutReason.User)
        assertEquals(AppState.NeedsPairing(null), vm.state.value)
    }
}
