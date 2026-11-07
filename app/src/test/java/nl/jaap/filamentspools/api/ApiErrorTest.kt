package nl.jaap.filamentspools.api

import kotlinx.coroutines.test.runTest
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiErrorTest {
    private lateinit var server: MockWebServer
    private lateinit var api: FilamentApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = ApiProvider(FakeSessionStore()).unauthenticated(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private suspend fun errorFor(response: MockResponse): ApiError {
        server.enqueue(response)
        val result = apiCall { api.me() }
        return result.exceptionOrNull() as ApiError
    }

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun unauthorized() = runTest {
        assertTrue(errorFor(json(401, """{"message":"Unauthenticated."}""")) is ApiError.Unauthorized)
    }

    @Test
    fun forbiddenJsonIsMissingAbility() = runTest {
        assertTrue(errorFor(json(403, """{"message":"Invalid ability provided."}""")) is ApiError.MissingAbility)
    }

    @Test
    fun forbiddenPlainTextIsIpBlocked() = runTest {
        val r = MockResponse().setResponseCode(403).setHeader("Content-Type", "text/plain").setBody("Blocked")
        assertTrue(errorFor(r) is ApiError.IpBlocked)
    }

    @Test
    fun notFound() = runTest {
        assertTrue(errorFor(json(404, """{"message":"Not Found."}""")) is ApiError.NotFound)
    }

    @Test
    fun validationKeepsFieldMessages() = runTest {
        val e = errorFor(json(422, fixture("error_422.json"))) as ApiError.Validation
        assertEquals("The filament product id field is required.", e.message)
        assertEquals(listOf("The filament product id field is required."), e.fields["filament_product_id"])
    }

    @Test
    fun rateLimitedReadsRetryAfter() = runTest {
        val e = errorFor(json(429, """{"message":"Too Many Attempts."}""").setHeader("Retry-After", "42"))
        assertEquals(ApiError.RateLimited(42), e)
    }

    @Test
    fun networkFailure() = runTest {
        server.shutdown()
        val e = apiCall { api.me() }.exceptionOrNull()
        assertTrue(e.toString(), e is ApiError.Network)
    }

    @Test
    fun otherStatusIsHttp() = runTest {
        assertEquals(ApiError.Http(500), errorFor(json(500, """{"message":"Server Error"}""")))
    }

    @Test
    fun unparsableBodyIsHttpMinusOne() = runTest {
        assertEquals(ApiError.Http(-1), errorFor(json(200, """{"data":{"nope":true}}""")))
    }
}
