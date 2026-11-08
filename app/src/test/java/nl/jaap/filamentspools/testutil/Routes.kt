package nl.jaap.filamentspools.testutil

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/** MockWebServer dispatcher keyed by "METHOD /path?page=N" (page omitted when absent). */
class Routes : Dispatcher() {
    private val routes = mutableMapOf<String, MockResponse>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    fun on(key: String, response: MockResponse) = apply { routes[key] = response }

    fun json(key: String, body: String, code: Int = 200) = on(
        key, MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body),
    )

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val url = request.requestUrl!!
        val page = url.queryParameter("page")
        val key = request.method + " " + url.encodedPath + (page?.let { "?page=$it" } ?: "")
        return routes[key] ?: MockResponse().setResponseCode(404).setBody("""{"message":"No route $key"}""")
    }
}

/** A JSON page of items with the given `next` link. */
fun page(items: List<String>, next: String? = null): String =
    """{"data":[${items.joinToString(",")}],"links":{"next":${next?.let { "\"$it\"" } ?: "null"}},"meta":{"current_page":1,"last_page":1}}"""

fun productJson(id: Long, buyUrl: String? = null, updatedAt: String = "2026-11-06T10:00:00+00:00") =
    """{"id":$id,"brand":"Bambu Lab","material":"PLA","line":"PLA Basic","article_name":"Product $id",
       "article_number":"1000$id","color_hex":"#FFFFFF","color_family":"white","has_refill":true,
       "buy_url":${buyUrl?.let { "\"$it\"" } ?: "null"},"updated_at":"$updatedAt"}"""

fun spoolJson(
    uuid: String,
    productId: Long = 1,
    status: String = "boxed",
    updatedAt: String = "2026-11-01T09:30:00+00:00",
    deletedAt: String? = null,
    purchasedAt: String? = "2026-10-02",
) = """{"uuid":"$uuid","product":${productJson(productId)},"status":"$status","is_refill":false,
       "purchased_at":${purchasedAt?.let { "\"$it\"" } ?: "null"},
       "created_at":"2026-10-02T18:12:40+00:00","updated_at":"$updatedAt",
       "deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"}}"""
