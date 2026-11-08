package nl.jaap.filamentspools.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.product
import nl.jaap.filamentspools.testutil.spool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductDaoTest {
    private val db = inMemoryDb()
    private val spools = db.spoolDao()
    private val products = db.productDao()

    @After
    fun close() = db.close()

    @Before
    fun seed() = runTest {
        // A: on the holder only (running out). B: on holder + boxed. C: nothing owned.
        products.upsert(
            listOf(
                product(1, "A Red", number = "10001", color = "red", material = "PLA"),
                product(2, "B Blue", number = "10002", color = "blue", material = "PETG"),
                product(3, "C Green", number = "10003", color = "green", material = "PLA"),
            ),
        )
        spools.upsert(
            listOf(
                spool("a1", 1, "on_spool"),
                spool("b1", 2, "on_spool"), spool("b2", 2, "boxed"), spool("b3", 2, "boxed"),
                spool("b4", 2, "used_up"),
            ),
        )
    }

    private suspend fun ids(owned: OwnedFilter, q: String = "", material: String? = null) =
        products.observeFiltered(q, material, null, null, null, owned).first().map { it.product.id }

    @Test
    fun countsDerivedFromSpools() = runTest {
        val b = products.observe(2).first()!!
        assertEquals(2, b.boxed)
        assertEquals(1, b.onSpool)
        assertEquals(3, b.owned)
        val c = products.observe(3).first()!!
        assertEquals(0, c.owned)
    }

    @Test
    fun runningOutMeansOnSpoolWithoutBoxed() = runTest {
        assertEquals(listOf(1L), products.observeRunningOut().first().map { it.product.id })
        assertEquals(listOf(1L), ids(OwnedFilter.RunningOut))
    }

    @Test
    fun ownedFilters() = runTest {
        assertEquals(listOf(1L, 2L, 3L), ids(OwnedFilter.All))
        assertEquals(listOf(3L), ids(OwnedFilter.NotOwned))
        assertEquals(listOf(1L, 2L), ids(OwnedFilter.Owned))
        assertEquals(listOf(2L), ids(OwnedFilter.Boxed))
        assertEquals(listOf(1L, 2L), ids(OwnedFilter.OnSpool))
    }

    @Test
    fun searchAndMaterialFilter() = runTest {
        assertEquals(listOf(2L), ids(OwnedFilter.All, q = "blue"))
        assertEquals(listOf(3L), ids(OwnedFilter.All, q = "10003"))
        assertEquals(listOf(1L, 3L), ids(OwnedFilter.All, material = "PLA"))
    }

    @Test
    fun distinctOptions() = runTest {
        assertEquals(listOf("PETG", "PLA"), products.distinctMaterials().first())
        assertEquals(listOf("blue", "green", "red"), products.distinctColors().first())
    }
}
