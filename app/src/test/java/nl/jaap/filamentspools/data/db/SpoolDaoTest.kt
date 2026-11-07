package nl.jaap.filamentspools.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.product
import nl.jaap.filamentspools.testutil.spool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpoolDaoTest {
    private val db = inMemoryDb()
    private val spools = db.spoolDao()
    private val products = db.productDao()

    @After
    fun close() = db.close()

    @Test
    fun observeByStatusFiltersAndSearches() = runTest {
        products.upsert(listOf(product(1, "Jade White", color = "white"), product(2, "Bambu Green", color = "green")))
        spools.upsert(listOf(spool("a", 1, "boxed"), spool("b", 2, "boxed"), spool("c", 1, "on_spool")))

        assertEquals(listOf("a"), spools.observeByStatus("boxed", "jade").first().map { it.spool.uuid })
        assertEquals(2, spools.observeByStatus("boxed", "").first().size)
        assertEquals(listOf("b"), spools.observeByStatus("boxed", "GREEN").first().map { it.spool.uuid })
        assertEquals(listOf("c"), spools.observeByStatus("on_spool", "").first().map { it.spool.uuid })
    }

    @Test
    fun newestChangeFirst() = runTest {
        products.upsert(listOf(product(1)))
        spools.upsert(
            listOf(
                spool("old", 1, updatedAt = "2026-10-01T00:00:00+00:00"),
                spool("new", 1, updatedAt = "2026-11-01T00:00:00+00:00"),
            ),
        )
        assertEquals(listOf("new", "old"), spools.observeByStatus("boxed", "").first().map { it.spool.uuid })
    }

    @Test
    fun spoolWithUnknownProductIsStored() = runTest {
        spools.upsert(listOf(spool("x", 999)))
        val row = spools.observe("x").first()!!
        assertEquals("x", row.spool.uuid)
        assertNull(row.product)
    }

    @Test
    fun replaceAllDropsMissing() = runTest {
        spools.upsert(listOf(spool("a", 1), spool("b", 1)))
        spools.replaceAll(listOf(spool("b", 1, status = "on_spool")))
        assertNull(spools.observe("a").first())
        assertEquals("on_spool", spools.observe("b").first()!!.spool.status)
    }

    @Test
    fun deleteByUuids() = runTest {
        spools.upsert(listOf(spool("a", 1), spool("b", 1)))
        spools.deleteByUuids(listOf("a"))
        assertNull(spools.observe("a").first())
        assertEquals(1, spools.observeForProduct(1).first().size)
    }
}
