package nl.jaap.filamentspools.api

import kotlinx.serialization.encodeToString
import nl.jaap.filamentspools.testutil.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoParsingTest {

    @Test
    fun pairParses() {
        val dto = ApiJson.decodeFromString<Envelope<PairedDeviceDto>>(fixture("pair_201.json")).data
        assertEquals("7|kq3J1nVb0pXr8eLw2YtZ5aGc9hDf4sMu6iOyTlNe", dto.token)
        assertEquals(listOf("spools:read", "spools:write"), dto.abilities)
        assertEquals("http://localhost:8000/api/v1", dto.api)
        assertEquals("Alex", dto.user.name)
    }

    @Test
    fun meParses() {
        val me = ApiJson.decodeFromString<Envelope<MeDto>>(fixture("me_200.json")).data
        assertEquals("Alex", me.name)
        assertEquals(listOf("spools:read", "spools:write"), me.abilities)
    }

    @Test
    fun spoolsPageParses() {
        val page = ApiJson.decodeFromString<Page<SpoolDto>>(fixture("spools_200.json"))
        assertEquals(1, page.data.size)
        val spool = page.data.single()
        assertEquals("9d3c6f1e-4b7a-4c2e-9f1d-2a8b5e7c0d43", spool.uuid)
        assertEquals("on_spool", spool.status)
        assertEquals("24.99", spool.price)
        assertEquals("#FFFFFF", spool.product.colorHex)
        assertNull(spool.deletedAt)
        assertNull(page.links.next)
    }

    @Test
    fun catalogPageParses() {
        val product = ApiJson.decodeFromString<Page<ProductDto>>(fixture("catalog_200.json")).data.single()
        assertEquals("10100", product.articleNumber)
        assertEquals("https://eu.store.bambulab.com/search?q=10100", product.buyUrl)
        assertTrue(product.hasRefill)
    }

    @Test
    fun eventsParse() {
        val events = ApiJson.decodeFromString<Page<SpoolEventDto>>(fixture("events_200.json")).data
        assertEquals(2, events.size)
        assertEquals("X1C AMS slot 2", events[1].note)
    }

    @Test
    fun unknownFieldsAndStatusesParse() {
        val json = fixture("spools_200.json")
            .replace("\"status\": \"on_spool\"", "\"status\": \"lent_out\", \"extra\": 1")
        val spool = ApiJson.decodeFromString<Page<SpoolDto>>(json).data.single()
        assertEquals("lent_out", spool.status)
    }

    @Test
    fun createRequestSerializesSnakeCase() {
        val body = ApiJson.encodeToString(CreateSpoolRequest(1, "boxed", false, null, null, null, null))
        assertTrue(body, body.contains("\"filament_product_id\":1"))
        assertFalse(body, body.contains("\"price\""))
    }
}
