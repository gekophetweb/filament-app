package nl.jaap.filamentspools.api

import kotlinx.coroutines.test.runTest
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.data.session.SignOutReason
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ApiProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var session: FakeSessionStore
    private lateinit var provider: ApiProvider

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        session = FakeSessionStore(
            Session(server.url("/api/v1/").toString(), "t0k", setOf("spools:read"), "Alex"),
        )
        provider = ApiProvider(session)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok(body: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun authenticatedRequestCarriesHeaders() = runTest {
        server.enqueue(ok(fixture("me_200.json")))
        provider.api().me()
        val req = server.takeRequest()
        assertEquals("Bearer t0k", req.getHeader("Authorization"))
        assertEquals("application/json", req.getHeader("Accept"))
        assertEquals("/api/v1/me", req.path)
    }

    @Test
    fun unauthenticatedSendsNoToken() = runTest {
        server.enqueue(ok(fixture("pair_201.json")))
        provider.unauthenticated(server.url("/api/v1/").toString()).pair(PairRequest("abc"))
        val req = server.takeRequest()
        assertNull(req.getHeader("Authorization"))
        assertEquals("application/json", req.getHeader("Accept"))
    }

    @Test
    fun unauthorizedSignsOutOnce() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
        apiCall { provider.api().me() }
        assertEquals(listOf(SignOutReason.TokenInvalid), session.signOuts)
    }

    @Test
    fun pairing401DoesNotSignOut() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
        apiCall { provider.unauthenticated(server.url("/api/v1/").toString()).pair(PairRequest("bad")) }
        assertEquals(emptyList<SignOutReason>(), session.signOuts)
    }
}
