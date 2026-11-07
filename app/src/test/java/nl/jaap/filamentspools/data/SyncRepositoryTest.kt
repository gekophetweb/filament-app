package nl.jaap.filamentspools.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.ApiProvider
import nl.jaap.filamentspools.data.db.SpoolStatus
import nl.jaap.filamentspools.data.session.CURSOR_CATALOG
import nl.jaap.filamentspools.data.session.CURSOR_SPOOLS
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.Routes
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.page
import nl.jaap.filamentspools.testutil.productJson
import nl.jaap.filamentspools.testutil.spool
import nl.jaap.filamentspools.testutil.spoolJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {
    private val db = inMemoryDb()
    private val routes = Routes()
    private val server = MockWebServer().apply { dispatcher = routes; start() }
    private val session = FakeSessionStore(Session(server.url("/api/v1/").toString(), "t", setOf("spools:read"), "Alex"))
    private val now = Instant.parse("2026-11-07T12:00:00Z")
    private val repo = SyncRepository(
        ApiProvider(session), db.spoolDao(), db.productDao(), session,
        CoroutineScope(SupervisorJob() + Dispatchers.IO), Clock.fixed(now, ZoneOffset.UTC),
    )

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun emptyCatalog() = routes.json("GET /api/v1/catalog?page=1", page(emptyList()))

    @Test
    fun followsPagesByNumberNotNextUrl() = runBlocking {
        routes.json("GET /api/v1/spools?page=1", page(listOf(spoolJson("a")), next = "http://unreachable.invalid/api/v1/spools?page=2"))
        routes.json("GET /api/v1/spools?page=2", page(listOf(spoolJson("b"))))
        emptyCatalog()

        assertEquals(Result.success(Unit), repo.sync())

        val spoolRequests = routes.requests.filter { it.requestUrl!!.encodedPath == "/api/v1/spools" }
        assertEquals(listOf("/api/v1/spools?per_page=100&page=1", "/api/v1/spools?per_page=100&page=2"), spoolRequests.map { it.path })
        assertNotNull(db.spoolDao().observe("a").first())
        assertNotNull(db.spoolDao().observe("b").first())
        assertEquals(now, session.lastSync.value)
    }

    @Test
    fun deletedRowsAreRemoved() = runBlocking {
        db.spoolDao().upsert(listOf(spool("a", 1)))
        session.setCursor(CURSOR_SPOOLS, "2026-11-01T00:00:00Z")
        routes.json(
            "GET /api/v1/spools?page=1",
            page(listOf(spoolJson("a", deletedAt = "2026-11-02T00:00:00+00:00", updatedAt = "2026-11-02T00:00:00+00:00"))),
        )
        emptyCatalog()

        repo.sync()

        assertNull(db.spoolDao().observe("a").first())
    }

    @Test
    fun firstSyncReplacesTable() = runBlocking {
        db.spoolDao().upsert(listOf(spool("stale", 1)))
        routes.json("GET /api/v1/spools?page=1", page(listOf(spoolJson("fresh"))))
        emptyCatalog()

        repo.sync()

        assertNull(db.spoolDao().observe("stale").first())
        assertNotNull(db.spoolDao().observe("fresh").first())
    }

    @Test
    fun incrementalSyncKeepsOtherRows() = runBlocking {
        db.spoolDao().upsert(listOf(spool("kept", 1)))
        session.setCursor(CURSOR_SPOOLS, "2026-11-01T00:00:00Z")
        routes.json("GET /api/v1/spools?page=1", page(listOf(spoolJson("new", updatedAt = "2026-11-05T00:00:00+00:00"))))
        emptyCatalog()

        repo.sync()

        assertNotNull(db.spoolDao().observe("kept").first())
        assertEquals("2026-11-05T00:00:00Z", session.cursor(CURSOR_SPOOLS))
    }

    @Test
    fun cursorSentMinusOneSecond() = runBlocking {
        session.setCursor(CURSOR_SPOOLS, "2026-11-01T09:30:00Z")
        routes.json("GET /api/v1/spools?page=1", page(emptyList()))
        emptyCatalog()

        repo.sync()

        val first = routes.requests.first { it.requestUrl!!.encodedPath == "/api/v1/spools" }
        assertEquals("2026-11-01T09:29:59Z", first.requestUrl!!.queryParameter("updated_since"))
        assertEquals("2026-11-01T09:30:00Z", session.cursor(CURSOR_SPOOLS))
    }

    @Test
    fun cursorNotAdvancedOnFailure() = runBlocking {
        session.setCursor(CURSOR_SPOOLS, "2026-11-01T09:30:00Z")
        routes.json("GET /api/v1/spools?page=1", page(listOf(spoolJson("a", updatedAt = "2026-11-05T00:00:00+00:00")), next = "x"))
        routes.json("GET /api/v1/spools?page=2", """{"message":"Server Error"}""", code = 500)
        emptyCatalog()

        val result = repo.sync()

        assertEquals(ApiError.Http(500), result.exceptionOrNull())
        assertEquals("2026-11-01T09:30:00Z", session.cursor(CURSOR_SPOOLS))
        assertNull(db.spoolDao().observe("a").first())
        assertEquals(ApiError.Http(500), repo.state.value.lastError)
    }

    @Test
    fun embeddedProductDoesNotWipeCatalogFields() = runBlocking {
        session.setCursor(CURSOR_SPOOLS, "2026-11-01T00:00:00Z")
        session.setCursor(CURSOR_CATALOG, "2026-11-01T00:00:00Z")
        routes.json("GET /api/v1/catalog?page=1", page(listOf(productJson(1, buyUrl = "https://buy.example/1"))))
        routes.json("GET /api/v1/spools?page=1", page(emptyList()))
        repo.sync()

        routes.json("GET /api/v1/spools?page=1", page(listOf(spoolJson("a", productId = 1))))
        routes.json("GET /api/v1/catalog?page=1", page(emptyList()))
        repo.sync()

        val row = db.spoolDao().observe("a").first()!!
        assertEquals("https://buy.example/1", row.product!!.buyUrl)
        assertEquals(SpoolStatus.BOXED, row.spool.status)
    }

    @Test
    fun spoolOfUncachedProductGetsEmbeddedProduct() = runBlocking {
        routes.json("GET /api/v1/spools?page=1", page(listOf(spoolJson("a", productId = 42))))
        emptyCatalog()

        repo.sync()

        assertEquals("Product 42", db.spoolDao().observe("a").first()!!.product!!.articleName)
    }

    @Test
    fun concurrentSyncRunsOnce() = runBlocking {
        routes.on(
            "GET /api/v1/spools?page=1",
            MockResponse().setHeader("Content-Type", "application/json").setBody(page(listOf(spoolJson("a"))))
                .setBodyDelay(300, TimeUnit.MILLISECONDS),
        )
        emptyCatalog()

        val results = listOf(async(Dispatchers.IO) { repo.sync() }, async(Dispatchers.IO) { repo.sync() }).awaitAll()

        assertEquals(listOf(Result.success(Unit), Result.success(Unit)), results)
        assertEquals(1, routes.requests.count { it.requestUrl!!.encodedPath == "/api/v1/spools" })
    }
}
