package nl.jaap.filamentspools.ui.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PairingPayloadTest {

    @Test
    fun validPayloadParses() {
        val p = PairingPayloadParser.parse(
            """{"type":"filament-spools-pairing","api":"http://localhost:8000/api/v1","code":"ABC123","expires_at":"2026-11-07T12:10:00+00:00"}""",
        )!!
        assertEquals("http://localhost:8000/api/v1", p.api)
        assertEquals("ABC123", p.code)
        assertEquals(Instant.parse("2026-11-07T12:10:00Z"), p.expiresAt)
    }

    @Test
    fun wrongTypeIsRejected() =
        assertNull(PairingPayloadParser.parse("""{"type":"something-else","api":"x","code":"y"}"""))

    @Test
    fun nonJsonIsRejected() = assertNull(PairingPayloadParser.parse("https://example.com"))

    @Test
    fun missingExpiryIsAllowed() =
        assertNull(PairingPayloadParser.parse("""{"type":"filament-spools-pairing","api":"a","code":"c"}""")!!.expiresAt)

    @Test
    fun missingCodeIsRejected() =
        assertNull(PairingPayloadParser.parse("""{"type":"filament-spools-pairing","api":"a"}"""))
}
