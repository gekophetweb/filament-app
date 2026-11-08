package nl.jaap.filamentspools.ui.printer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.data.SyncState
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.FakeSpoolActions
import nl.jaap.filamentspools.testutil.FakeSyncer
import nl.jaap.filamentspools.testutil.MainDispatcherRule
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.product
import nl.jaap.filamentspools.testutil.spool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class PrinterViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val db = inMemoryDb()
    private val actions = FakeSpoolActions()
    private val syncer = FakeSyncer()
    private val session = FakeSessionStore(Session("http://h/api/v1/", "t", setOf("spools:read", "spools:write"), "Alex"))

    @Before
    fun seed() = runBlocking {
        db.productDao().upsert(
            listOf(
                product(1, "Jade White", color = "white"),
                product(2, "Bambu Green", color = "green"),
                product(3, "Lime Green", color = "green"),
            ),
        )
        // 1: on holder + boxed. 2: boxed only. 3: on holder only (running out).
        db.spoolDao().upsert(
            listOf(spool("h1", 1, "on_spool"), spool("b1", 1, "boxed"), spool("b2", 2, "boxed"), spool("h3", 3, "on_spool")),
        )
    }

    @After
    fun close() = db.close()

    private fun vm() = PrinterViewModel(db.spoolDao(), db.productDao(), actions, syncer, session)

    private suspend fun PrinterViewModel.awaitState(predicate: (PrinterUiState) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }

    @Test
    fun sectionsFromCache() = runBlocking {
        val s = vm().awaitState { it.boxed.size == 2 }
        assertEquals(setOf("h1", "h3"), s.onHolder.map { it.spool.uuid }.toSet())
        assertEquals(listOf(3L), s.runningOut.map { it.product.id })
        assertTrue(s.canWrite)
    }

    @Test
    fun queryFiltersSections() = runBlocking {
        val vm = vm()
        vm.awaitState { it.boxed.size == 2 }
        vm.onQuery("green")
        val s = vm.awaitState { it.query == "green" && it.boxed.size == 1 }
        assertEquals(listOf("b2"), s.boxed.map { it.spool.uuid })
        assertEquals(listOf(3L), s.runningOut.map { it.product.id })
        assertEquals(listOf("h3"), s.onHolder.map { it.spool.uuid })
        vm.onQuery("jade")
        val s2 = vm.awaitState { it.query == "jade" && it.runningOut.isEmpty() }
        assertEquals(listOf("b1"), s2.boxed.map { it.spool.uuid })
    }

    @Test
    fun doubleTapSendsOneRequest() = runBlocking {
        actions.gate = CompletableDeferred()
        val vm = vm()
        vm.open("b1")
        vm.open("b1")
        assertEquals(listOf("open:b1"), actions.calls)
        vm.awaitState { "b1" in it.busyUuids }
        actions.gate!!.complete(Unit)
        vm.awaitState { "b1" !in it.busyUuids }
        assertEquals("Put on spool", withTimeout(5_000) { vm.messages.first() })
    }

    @Test
    fun readOnlyHidesActions() = runBlocking {
        session.updateAbilities(setOf("spools:read"))
        assertFalse(vm().awaitState { it.boxed.isNotEmpty() }.canWrite)
    }

    @Test
    fun networkErrorShowsOfflineBanner() = runBlocking {
        val at = Instant.parse("2026-11-07T10:00:00Z")
        session.lastSync.value = at
        syncer.state.value = SyncState(lastError = ApiError.Network(IOException("down")))
        assertEquals(at, vm().awaitState { it.offlineSince != null }.offlineSince)
    }

    @Test
    fun otherSyncErrorIsNotOffline() = runBlocking {
        session.lastSync.value = Instant.parse("2026-11-07T10:00:00Z")
        syncer.state.value = SyncState(lastError = ApiError.Http(500))
        val s = vm().awaitState { it.boxed.isNotEmpty() }
        assertNull(s.offlineSince)
        assertEquals("Server error (HTTP 500)", s.syncError)
    }

    @Test
    fun useUpFailureEmitsMessage() = runBlocking {
        actions.result = Result.failure(ApiError.RateLimited(42))
        val vm = vm()
        vm.useUp("h1")
        assertEquals("Too many requests — try again in 42 s", withTimeout(5_000) { vm.messages.first() })
    }

    @Test
    fun refreshSyncs() = runBlocking {
        vm().refresh()
        assertEquals(1, syncer.calls)
    }
}
