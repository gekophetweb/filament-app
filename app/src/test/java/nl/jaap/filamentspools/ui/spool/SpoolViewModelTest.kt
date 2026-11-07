package nl.jaap.filamentspools.ui.spool

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.Page
import nl.jaap.filamentspools.api.PageLinks
import nl.jaap.filamentspools.api.SpoolEventDto
import nl.jaap.filamentspools.data.Change
import nl.jaap.filamentspools.data.SpoolChanges
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.FakeSpoolActions
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
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class SpoolViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = inMemoryDb()
    private val actions = FakeSpoolActions()
    private val session = FakeSessionStore(Session("http://h/api/v1/", "t", setOf("spools:read", "spools:write"), "Alex"))

    @Before
    fun seed() = runBlocking {
        db.productDao().upsert(listOf(product(1)))
        db.spoolDao().upsert(listOf(spool("boxed1", 1, "boxed", location = "Shelf A"), spool("done1", 1, "used_up")))
    }

    @After
    fun close() = db.close()

    private fun event(type: String) = SpoolEventDto(type, null, "2026-11-01T09:30:00+00:00")

    private fun vm(uuid: String) = SpoolViewModel(SavedStateHandle(mapOf("uuid" to uuid)), db.spoolDao(), actions, session)

    private suspend fun SpoolViewModel.await(predicate: (SpoolUiState) -> Boolean) = withTimeout(5_000) { state.first(predicate) }

    @Test
    fun editSendsOnlyChangedFields() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.startEdit()
        vm.onEdit(vm.state.value.editing!!.copy(location = "Shelf B"))
        vm.saveEdit()
        assertEquals(SpoolChanges(location = Change.Set("Shelf B")), actions.lastChanges)
    }

    @Test
    fun equalPriceIsNotAChange() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.startEdit()
        assertEquals("24.99", vm.state.value.editing!!.price)
        vm.onEdit(vm.state.value.editing!!.copy(price = "24,990", notes = "  "))
        vm.saveEdit()
        assertTrue(actions.calls.none { it.startsWith("update") })
        assertNull(vm.state.value.editing)
    }

    @Test
    fun priceChangeIsSent() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.startEdit()
        vm.onEdit(vm.state.value.editing!!.copy(price = "19.5", purchasedAt = ""))
        vm.saveEdit()
        assertEquals(SpoolChanges(purchasedAt = Change.Set(null), price = Change.Set(BigDecimal("19.5"))), actions.lastChanges)
    }

    @Test
    fun noChangesNoRequest() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.startEdit()
        vm.saveEdit()
        assertTrue(actions.calls.none { it.startsWith("update") })
        assertNull(vm.state.value.editing)
    }

    @Test
    fun invalidEditDateShowsError() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.startEdit()
        vm.onEdit(vm.state.value.editing!!.copy(purchasedAt = "yesterday"))
        vm.saveEdit()
        assertEquals("Use YYYY-MM-DD", vm.state.value.fieldErrors["purchased_at"])
        assertTrue(actions.calls.none { it.startsWith("update") })
    }

    @Test
    fun actionsByStatus() = runBlocking {
        val boxed = vm("boxed1").await { it.spool != null }
        assertTrue(boxed.canOpen)
        assertTrue(boxed.canUseUp)
        val done = vm("done1").await { it.spool != null }
        assertFalse(done.canOpen)
        assertFalse(done.canUseUp)
        assertTrue(done.canRemove)
    }

    @Test
    fun readOnlyAllowsNothing() = runBlocking {
        session.updateAbilities(setOf("spools:read"))
        val s = vm("boxed1").await { it.spool != null }
        assertFalse(s.canOpen || s.canUseUp || s.canRemove || s.canEdit)
    }

    @Test
    fun notFoundMarksGone() = runBlocking {
        actions.result = Result.failure(ApiError.NotFound)
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.useUp()
        assertEquals("This spool no longer exists", vm.await { it.goneMessage != null }.goneMessage)
    }

    @Test
    fun removeSendsNoteAndMarksGone() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        vm.remove("sold")
        assertTrue("remove:boxed1:sold" in actions.calls)
        assertEquals("Spool removed", vm.await { it.goneMessage != null }.goneMessage)
    }

    @Test
    fun rowDisappearingMarksGone() = runBlocking {
        val vm = vm("boxed1")
        vm.await { it.spool != null }
        db.spoolDao().deleteByUuids(listOf("boxed1"))
        assertEquals("This spool no longer exists", vm.await { it.goneMessage != null }.goneMessage)
    }

    @Test
    fun eventsLoadAndPage() = runBlocking {
        actions.eventPages[1] = Result.success(Page(listOf(event("added")), PageLinks(next = "x")))
        actions.eventPages[2] = Result.success(Page(listOf(event("opened"))))
        val vm = vm("boxed1")
        assertTrue(vm.await { it.events.size == 1 }.hasMoreEvents)
        vm.loadMoreEvents()
        val s = vm.await { it.events.size == 2 }
        assertEquals(listOf("added", "opened"), s.events.map { it.type })
        assertFalse(s.hasMoreEvents)
    }

    /** Review #6: "Load more" while a page is loading must not fetch that page twice. */
    @Test
    fun loadMoreWhileLoadingDoesNotDuplicate() = runBlocking {
        actions.eventsGate = kotlinx.coroutines.CompletableDeferred()
        actions.eventPages[1] = Result.success(Page(listOf(event("added")), PageLinks(next = "x")))
        val vm = vm("boxed1")
        assertFalse(vm.state.value.hasMoreEvents)
        vm.loadMoreEvents()
        vm.loadMoreEvents()
        actions.eventsGate!!.complete(Unit)
        vm.await { it.events.isNotEmpty() }
        assertEquals(listOf("events:boxed1:1"), actions.calls.filter { it.startsWith("events") })
        assertEquals(1, vm.state.value.events.size)
    }

    @Test
    fun eventsOfflineShowsMessage() = runBlocking {
        actions.eventPages[1] = Result.failure(ApiError.Network(IOException("down")))
        val vm = vm("boxed1")
        assertEquals("History needs a connection to the server", vm.await { it.eventsError != null }.eventsError)
    }
}
