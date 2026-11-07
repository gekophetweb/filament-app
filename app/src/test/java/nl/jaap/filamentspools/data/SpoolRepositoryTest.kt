package nl.jaap.filamentspools.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.ApiProvider
import nl.jaap.filamentspools.data.session.Session
import nl.jaap.filamentspools.testutil.FakeSessionStore
import nl.jaap.filamentspools.testutil.Routes
import nl.jaap.filamentspools.testutil.inMemoryDb
import nl.jaap.filamentspools.testutil.spool
import nl.jaap.filamentspools.testutil.spoolJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SpoolRepositoryTest {
    private val db = inMemoryDb()
    private val routes = Routes()
    private val server = MockWebServer().apply { dispatcher = routes; start() }
    private val session = FakeSessionStore(
        Session(server.url("/api/v1/").toString(), "t", setOf("spools:read", "spools:write"), "Alex"),
    )
    private val repo = SpoolRepository(ApiProvider(session), db.spoolDao(), db.productDao(), session, LocalWrites())

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun body(method: String, path: String): JsonObject =
        Json.parseToJsonElement(routes.requests.last { it.method == method && it.path == path }.body.readUtf8()) as JsonObject

    @Test
    fun openUpdatesCache() = runBlocking {
        db.spoolDao().upsert(listOf(spool("u1", 1, "boxed")))
        routes.json("POST /api/v1/spools/u1/open", """{"data":${spoolJson("u1", status = "on_spool")}}""")

        assertEquals(Result.success(Unit), repo.open("u1"))

        assertEquals("on_spool", db.spoolDao().observe("u1").first()!!.spool.status)
        assertEquals(Json.parseToJsonElement("""{}"""), body("POST", "/api/v1/spools/u1/open"))
    }

    @Test
    fun removeDeletesLocally() = runBlocking {
        db.spoolDao().upsert(listOf(spool("u1", 1)))
        routes.on("DELETE /api/v1/spools/u1", MockResponse().setResponseCode(204))

        assertEquals(Result.success(Unit), repo.remove("u1", "sold"))

        assertNull(db.spoolDao().observe("u1").first())
        assertEquals(Json.parseToJsonElement("""{"note":"sold"}"""), body("DELETE", "/api/v1/spools/u1"))
    }

    @Test
    fun removeFailureKeepsRow() = runBlocking {
        db.spoolDao().upsert(listOf(spool("u1", 1)))
        routes.json("DELETE /api/v1/spools/u1", """{"message":"Too Many Attempts."}""", code = 429)

        assertEquals(ApiError.RateLimited(null), repo.remove("u1", null).exceptionOrNull())
        assertEquals("u1", db.spoolDao().observe("u1").first()!!.spool.uuid)
    }

    @Test
    fun notFoundDeletesLocally() = runBlocking {
        db.spoolDao().upsert(listOf(spool("u1", 1)))
        routes.json("POST /api/v1/spools/u1/used-up", """{"message":"Not Found."}""", code = 404)

        assertEquals(ApiError.NotFound, repo.useUp("u1").exceptionOrNull())
        assertNull(db.spoolDao().observe("u1").first())
    }

    @Test
    fun missingAbilityRefreshesAbilities() = runBlocking {
        routes.json("POST /api/v1/spools/u1/open", """{"message":"Invalid ability provided."}""", code = 403)
        routes.json("GET /api/v1/me", """{"data":{"id":1,"name":"Alex","email":"a@x","abilities":["spools:read"]}}""")

        assertEquals(ApiError.MissingAbility, repo.open("u1").exceptionOrNull())
        assertFalse(session.canWrite)
    }

    @Test
    fun patchSendsOnlyChangedFields() = runBlocking {
        routes.json("PATCH /api/v1/spools/u1", """{"data":${spoolJson("u1", location = "Shelf B")}}""")

        repo.update("u1", SpoolChanges(location = Change.Set("Shelf B"), notes = Change.Set(null)))

        assertEquals(Json.parseToJsonElement("""{"location":"Shelf B","notes":null}"""), body("PATCH", "/api/v1/spools/u1"))
        assertEquals("Shelf B", db.spoolDao().observe("u1").first()!!.spool.location)
    }

    @Test
    fun patchFormatsDatesAndPrices() = runBlocking {
        routes.json("PATCH /api/v1/spools/u1", """{"data":${spoolJson("u1")}}""")

        repo.update(
            "u1",
            SpoolChanges(isRefill = Change.Set(true), purchasedAt = Change.Set(LocalDate.of(2026, 10, 2)), price = Change.Set(BigDecimal("19.50"))),
        )

        assertEquals(
            Json.parseToJsonElement("""{"is_refill":true,"purchased_at":"2026-10-02","price":19.5}"""),
            body("PATCH", "/api/v1/spools/u1"),
        )
    }

    @Test
    fun addSendsSnakeCaseAndStatus() = runBlocking {
        routes.json("POST /api/v1/spools", """{"data":${spoolJson("new1", status = "on_spool")}}""", code = 201)

        val uuid = repo.add(1, onSpool = true, isRefill = false, purchasedAt = null, price = BigDecimal("24.99"), location = "Shelf A", notes = null)

        assertEquals("new1", uuid.getOrThrow())
        assertEquals(
            Json.parseToJsonElement("""{"filament_product_id":1,"status":"on_spool","is_refill":false,"price":24.99,"location":"Shelf A"}"""),
            body("POST", "/api/v1/spools"),
        )
        assertEquals("on_spool", db.spoolDao().observe("new1").first()!!.spool.status)
    }

    @Test
    fun eventsPage() = runBlocking {
        routes.json(
            "GET /api/v1/spools/u1/events?page=1",
            """{"data":[{"type":"added","note":null,"created_at":"2026-10-02T18:12:40+00:00"}],"links":{"next":null},"meta":{"current_page":1,"last_page":1}}""",
        )
        assertEquals("added", repo.events("u1", 1).getOrThrow().data.single().type)
    }
}
