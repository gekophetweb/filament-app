package nl.jaap.filamentspools.testutil

import kotlinx.coroutines.CompletableDeferred
import nl.jaap.filamentspools.api.Page
import nl.jaap.filamentspools.api.SpoolEventDto
import nl.jaap.filamentspools.data.SpoolActions
import nl.jaap.filamentspools.data.SpoolChanges
import java.math.BigDecimal
import java.time.LocalDate

class FakeSpoolActions : SpoolActions {
    val calls = mutableListOf<String>()
    var result: Result<Unit> = Result.success(Unit)
    var addResult: Result<String> = Result.success("new-uuid")
    var gate: CompletableDeferred<Unit>? = null
    var lastChanges: SpoolChanges? = null
    var lastAdd: List<Any?>? = null
    var eventPages: MutableMap<Int, Result<Page<SpoolEventDto>>> = mutableMapOf()

    private suspend fun record(call: String): Result<Unit> {
        calls += call
        gate?.await()
        return result
    }

    override suspend fun open(uuid: String, note: String?) = record("open:$uuid")
    override suspend fun useUp(uuid: String, note: String?) = record("useUp:$uuid")
    override suspend fun remove(uuid: String, note: String?) = record("remove:$uuid:$note")

    override suspend fun update(uuid: String, changes: SpoolChanges): Result<Unit> {
        lastChanges = changes
        return record("update:$uuid")
    }

    override suspend fun add(
        productId: Long, onSpool: Boolean, isRefill: Boolean, purchasedAt: LocalDate?,
        price: BigDecimal?, location: String?, notes: String?,
    ): Result<String> {
        calls += "add:$productId"
        lastAdd = listOf(productId, onSpool, isRefill, purchasedAt, price, location, notes)
        gate?.await()
        return addResult
    }

    override suspend fun events(uuid: String, page: Int): Result<Page<SpoolEventDto>> {
        calls += "events:$uuid:$page"
        return eventPages[page] ?: Result.success(Page(emptyList()))
    }
}
