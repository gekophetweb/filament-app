package nl.jaap.filamentspools.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.ApiProvider
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.Routes
import nl.jaap.filamentspools.testutil.fixture
import nl.jaap.filamentspools.testutil.inMemoryDb
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
class PairingRepositoryTest {
    private val db = inMemoryDb()
    private val routes = Routes()
    private val server = MockWebServer().apply { dispatcher = routes; start() }
    private val session = FakeSessionStore()
    private val provider = ApiProvider(session)
    private val sync = SyncRepository(
        provider, db.spoolDao(), db.productDao(), session,
        CoroutineScope(SupervisorJob() + Dispatchers.IO), Clock.systemUTC(), LocalWrites(),
    )
    private val repo = PairingRepository(provider, session, sync)

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private val typedUrl get() = "${server.hostName}:${server.port}"

    @Test
    fun storesTypedUrlNotResponseApi() = runBlocking {
        routes.json("POST /api/v1/pair", fixture("pair_201.json"), code = 201)

        assertEquals(Result.success(Unit), repo.pair(typedUrl, "ABC", "Pixel 8"))

        val s = session.current.value!!
        assertEquals("http://$typedUrl/api/v1/", s.baseUrl)
        assertEquals("7|kq3J1nVb0pXr8eLw2YtZ5aGc9hDf4sMu6iOyTlNe", s.token)
        assertEquals(setOf("spools:read", "spools:write"), s.abilities)
        assertEquals("Alex", s.userName)
        val body = routes.requests.first { it.path == "/api/v1/pair" }.body.readUtf8()
        assertEquals("""{"code":"ABC","device_name":"Pixel 8"}""", body)
    }

    @Test
    fun syncFailureDoesNotFailPairing() = runBlocking {
        routes.json("POST /api/v1/pair", fixture("pair_201.json"), code = 201)
        // No spools route: the first sync answers 404.
        assertEquals(Result.success(Unit), repo.pair(typedUrl, "ABC", "Pixel 8"))
    }

    @Test
    fun existingTokenSignsInViaMe() = runBlocking {
        routes.json("GET /api/v1/me", fixture("me_200.json"))

        assertEquals(Result.success(Unit), repo.useToken(typedUrl, " 1|abc "))

        val me = routes.requests.first { it.path == "/api/v1/me" }
        assertEquals("Bearer 1|abc", me.getHeader("Authorization"))
        val s = session.current.value!!
        assertEquals("http://$typedUrl/api/v1/", s.baseUrl)
        assertEquals("1|abc", s.token)
        assertEquals(setOf("spools:read", "spools:write"), s.abilities)
        assertEquals("Alex", s.userName)
    }

    @Test
    fun badTokenLeavesSessionEmptyAndDoesNotSignOut() = runBlocking {
        routes.json("GET /api/v1/me", """{"message":"Unauthenticated."}""", code = 401)
        assertEquals(ApiError.Unauthorized, repo.useToken(typedUrl, "nope").exceptionOrNull())
        assertNull(session.current.value)
        assertEquals(emptyList<Any>(), session.signOuts)
    }

    @Test
    fun badCodeLeavesSessionEmpty() = runBlocking {
        routes.json("POST /api/v1/pair", """{"message":"Unauthenticated."}""", code = 401)
        assertEquals(ApiError.Unauthorized, repo.pair(typedUrl, "BAD", "Pixel 8").exceptionOrNull())
        assertNull(session.current.value)
    }
}
