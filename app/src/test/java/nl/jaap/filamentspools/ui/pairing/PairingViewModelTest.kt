package nl.jaap.filamentspools.ui.pairing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.data.Pairer
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PairingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakePairer : Pairer {
        val calls = mutableListOf<Triple<String, String, String>>()
        var result: Result<Unit> = Result.success(Unit)
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun pair(serverUrl: String, code: String, deviceName: String): Result<Unit> {
            calls += Triple(serverUrl, code, deviceName)
            gate?.await()
            return result
        }
    }

    private val pairer = FakePairer()
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private val session = FakeSessionStore()
    private val vm by lazy { PairingViewModel(pairer, session, clock, "Pixel 8") }

    /** Review #8: the QR's `api` says localhost, which the phone cannot reach; keep what the user typed. */
    @Test
    fun scanLocalhostKeepsTypedAddress() {
        vm.onServerUrl("192.168.1.20:8000")
        vm.onScanned(qr("2026-11-07T12:10:00+00:00"))
        assertEquals("192.168.1.20:8000", vm.state.value.serverUrl)
        assertEquals("ABC", vm.state.value.code)
    }

    @Test
    fun scanNonLocalAddressReplacesField() {
        vm.onServerUrl("192.168.1.20:8000")
        vm.onScanned("""{"type":"filament-spools-pairing","api":"https://spools.example.com/api/v1","code":"ABC"}""")
        assertEquals("https://spools.example.com/api/v1", vm.state.value.serverUrl)
    }

    @Test
    fun prefillsLastServerUrl() {
        session.lastUrl = "http://192.168.1.20:8000/api/v1/"
        val fresh = PairingViewModel(pairer, session, clock, "Pixel 8")
        assertEquals("http://192.168.1.20:8000/api/v1/", fresh.state.value.serverUrl)
    }

    private fun qr(expires: String) =
        """{"type":"filament-spools-pairing","api":"http://localhost:8000/api/v1","code":"ABC","expires_at":"$expires"}"""

    @Test
    fun deviceNameDefaultsToModel() = assertEquals("Pixel 8", vm.state.value.deviceName)

    @Test
    fun scanFillsFields() {
        vm.onScanned(qr("2026-11-07T12:10:00+00:00"))
        assertEquals("http://localhost:8000/api/v1", vm.state.value.serverUrl)
        assertEquals("ABC", vm.state.value.code)
        assertNull(vm.state.value.error)
        vm.onServerUrl("http://10.0.2.2:8000")
        assertEquals("http://10.0.2.2:8000", vm.state.value.serverUrl)
    }

    @Test
    fun badQrShowsMessage() {
        vm.onScanned("hello")
        assertEquals("This QR code is not a Filament Spools pairing code", vm.state.value.error)
    }

    @Test
    fun expiredCodeShowsWarning() = runTest {
        vm.onScanned(qr("2026-11-07T11:50:00+00:00"))
        assertEquals("This code has expired — show a new QR on the Devices page", vm.state.value.error)
        vm.submit()
        assertTrue(pairer.calls.isEmpty())
    }

    @Test
    fun submitPassesFields() = runTest {
        vm.onScanned(qr("2026-11-07T12:10:00+00:00"))
        vm.onServerUrl("10.0.2.2:8000")
        vm.submit()
        assertEquals(listOf(Triple("10.0.2.2:8000", "ABC", "Pixel 8")), pairer.calls)
    }

    @Test
    fun unauthorizedShowsPairCopy() = runTest {
        pairer.result = Result.failure(ApiError.Unauthorized)
        vm.onServerUrl("10.0.2.2:8000"); vm.onCode("ABC")
        vm.submit()
        assertEquals("Code invalid, used, expired, or the token limit (10) is reached", vm.state.value.error)
    }

    @Test
    fun networkErrorNamesServer() = runTest {
        pairer.result = Result.failure(ApiError.Network(java.io.IOException("x")))
        vm.onServerUrl("10.0.2.2:8000"); vm.onCode("ABC")
        vm.submit()
        assertEquals("Can't reach the server at 10.0.2.2:8000", vm.state.value.error)
    }

    @Test
    fun invalidUrlShowsMessage() = runTest {
        pairer.result = Result.failure(IllegalArgumentException("Not a valid server address"))
        vm.onServerUrl("http://"); vm.onCode("ABC")
        vm.submit()
        assertEquals("Not a valid server address", vm.state.value.error)
    }

    @Test
    fun missingFieldsBlockSubmit() = runTest {
        vm.submit()
        assertEquals("Enter the server address and the pairing code", vm.state.value.error)
        assertTrue(pairer.calls.isEmpty())
    }

    @Test
    fun busyWhileSubmitting() = runTest {
        pairer.gate = CompletableDeferred()
        vm.onServerUrl("10.0.2.2:8000"); vm.onCode("ABC")
        vm.submit()
        assertTrue(vm.state.value.busy)
        vm.submit()
        assertEquals(1, pairer.calls.size)
        pairer.gate!!.complete(Unit)
        assertFalse(vm.state.value.busy)
    }
}
