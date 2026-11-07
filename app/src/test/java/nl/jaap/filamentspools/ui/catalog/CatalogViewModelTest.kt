package nl.jaap.filamentspools.ui.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.jaap.filamentspools.data.db.OwnedFilter
import nl.jaap.filamentspools.testutil.MainDispatcherRule
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.product
import nl.jaap.filamentspools.testutil.spool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = inMemoryDb()

    @Before
    fun seed() = runBlocking {
        db.productDao().upsert(
            listOf(
                product(1, "PLA Red", number = "10001", color = "red", material = "PLA"),
                product(2, "PETG Red", number = "10002", color = "red", material = "PETG"),
                product(3, "PLA Blue", number = "10003", color = "blue", material = "PLA"),
            ),
        )
        db.spoolDao().upsert(listOf(spool("a", 1, "on_spool"), spool("b", 2, "on_spool"), spool("c", 3, "boxed")))
    }

    @After
    fun close() = db.close()

    private suspend fun CatalogViewModel.ids(predicate: (CatalogUiState) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }.products.map { it.product.id }

    @Test
    fun listsAllByDefault() = runBlocking {
        assertEquals(listOf(1L, 2L, 3L), CatalogViewModel(db.productDao()).ids { it.products.size == 3 })
    }

    @Test
    fun filtersPassedToDao() = runBlocking {
        val vm = CatalogViewModel(db.productDao())
        vm.onFilters(CatalogFilters(material = "PLA", owned = OwnedFilter.RunningOut))
        assertEquals(listOf(1L), vm.ids { it.filters.material == "PLA" && it.products.size == 1 })
    }

    @Test
    fun queryFilters() = runBlocking {
        val vm = CatalogViewModel(db.productDao())
        vm.onQuery("blue")
        assertEquals(listOf(3L), vm.ids { it.query == "blue" && it.products.size == 1 })
    }

    @Test
    fun optionsFromCatalog() = runBlocking {
        val vm = CatalogViewModel(db.productDao())
        val s = withTimeout(5_000) { vm.state.first { it.options.materials.isNotEmpty() } }
        assertEquals(listOf("PETG", "PLA"), s.options.materials)
        assertEquals(listOf("blue", "red"), s.options.colors)
    }
}
