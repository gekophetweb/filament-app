package nl.jaap.filamentspools.ui.catalog

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.jaap.filamentspools.api.ApiError
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ProductViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = inMemoryDb()
    private val actions = FakeSpoolActions()
    private val session = FakeSessionStore(Session("http://h/api/v1/", "t", setOf("spools:read", "spools:write"), "Alex"))

    @Before
    fun seed() = runBlocking {
        db.productDao().upsert(listOf(product(1, hasRefill = true), product(2, hasRefill = false)))
        db.spoolDao().upsert(listOf(spool("a", 1, "boxed")))
    }

    @After
    fun close() = db.close()

    private fun vm(id: Long) =
        ProductViewModel(SavedStateHandle(mapOf("id" to id)), db.productDao(), db.spoolDao(), actions, session)

    private suspend fun ProductViewModel.loaded() = withTimeout(5_000) { state.first { it.product != null } }

    @Test
    fun showsProductCountsAndSpools() = runBlocking {
        val s = vm(1).loaded()
        assertEquals(1, s.product!!.boxed)
        assertEquals(listOf("a"), withTimeout(5_000) { vm(1).state.first { it.spools.isNotEmpty() } }.spools.map { it.spool.uuid })
        assertTrue(s.canWrite)
    }

    @Test
    fun invalidDateBlocksSave() = runBlocking {
        val vm = vm(1)
        vm.loaded()
        vm.onForm(AddSpoolForm(purchasedAt = "2-10-2026"))
        vm.save()
        assertTrue(actions.calls.isEmpty())
        assertEquals("Use YYYY-MM-DD", vm.state.value.fieldErrors["purchased_at"])
    }

    @Test
    fun invalidPriceBlocksSave() = runBlocking {
        val vm = vm(1)
        vm.loaded()
        vm.onForm(AddSpoolForm(price = "abc"))
        vm.save()
        assertTrue(actions.calls.isEmpty())
        assertEquals("Enter a number", vm.state.value.fieldErrors["price"])
    }

    @Test
    fun validFormSendsValues() = runBlocking {
        val vm = vm(1)
        vm.loaded()
        vm.onForm(AddSpoolForm(onSpool = true, isRefill = true, purchasedAt = "2026-10-02", price = "24,99", location = " Shelf A ", notes = ""))
        vm.save()
        assertEquals(listOf(1L, true, true, LocalDate.of(2026, 10, 2), BigDecimal("24.99"), "Shelf A", null), actions.lastAdd)
    }

    @Test
    fun serverValidationMapsToFields() = runBlocking {
        actions.addResult = Result.failure(ApiError.Validation("Too long.", mapOf("location" to listOf("Too long."))))
        val vm = vm(1)
        vm.loaded()
        vm.onForm(AddSpoolForm(location = "x".repeat(300)))
        vm.save()
        val s = withTimeout(5_000) { vm.state.first { !it.saving } }
        assertEquals("Too long.", s.fieldErrors["location"])
    }

    @Test
    fun successResetsForm() = runBlocking {
        val vm = vm(1)
        vm.loaded()
        vm.onForm(AddSpoolForm(location = "Shelf A"))
        vm.save()
        assertEquals("Spool added", withTimeout(5_000) { vm.messages.first() })
        assertEquals(AddSpoolForm(), vm.state.value.form)
    }

    @Test
    fun refillHiddenWhenProductHasNoRefill() = runBlocking {
        assertFalse(vm(2).loaded().showRefill)
        assertTrue(vm(1).loaded().showRefill)
    }
}
