package nl.jaap.filamentspools.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.ApiProvider
import nl.jaap.filamentspools.api.CreateSpoolRequest
import nl.jaap.filamentspools.api.NoteRequest
import nl.jaap.filamentspools.api.Page
import nl.jaap.filamentspools.api.SpoolDto
import nl.jaap.filamentspools.api.SpoolEventDto
import nl.jaap.filamentspools.api.apiCall
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.db.SpoolStatus
import nl.jaap.filamentspools.data.db.toEntity
import nl.jaap.filamentspools.data.session.SessionStore
import retrofit2.HttpException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One field of a PATCH: left out, or set (possibly to null). */
sealed interface Change<out T> {
    data object Unchanged : Change<Nothing>
    data class Set<T>(val value: T?) : Change<T>
}

data class SpoolChanges(
    val isRefill: Change<Boolean> = Change.Unchanged,
    val purchasedAt: Change<LocalDate> = Change.Unchanged,
) {
    val isEmpty: Boolean get() = toJson().isEmpty()

    fun toJson(): JsonObject = buildJsonObject {
        (isRefill as? Change.Set)?.let { put("is_refill", it.value?.let(::JsonPrimitive) ?: JsonNull) }
        (purchasedAt as? Change.Set)?.let { put("purchased_at", it.value?.toString()?.let(::JsonPrimitive) ?: JsonNull) }
    }
}

/** Spool writes and history, as screens use them; lets ViewModels be tested with a fake. */
interface SpoolActions {
    suspend fun open(uuid: String, note: String? = null): Result<Unit>
    suspend fun useUp(uuid: String, note: String? = null): Result<Unit>
    /** Adds [amount] identical spools (1 to 50); returns their uuids. */
    suspend fun add(
        productId: Long, amount: Int, onSpool: Boolean, isRefill: Boolean, purchasedAt: LocalDate?,
    ): Result<List<String>>
    suspend fun update(uuid: String, changes: SpoolChanges): Result<Unit>
    suspend fun remove(uuid: String, note: String?): Result<Unit>
    suspend fun events(uuid: String, page: Int): Result<Page<SpoolEventDto>>
}

/** Writes go straight to the API; on success the returned spool is stored in Room (spec section 6). */
@Singleton
class SpoolRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val spoolDao: SpoolDao,
    private val productDao: ProductDao,
    private val session: SessionStore,
    private val writes: LocalWrites,
) : SpoolActions {
    override suspend fun open(uuid: String, note: String?): Result<Unit> =
        write(uuid) { apiProvider.api().openSpool(uuid, NoteRequest(note)).data }.map { }

    override suspend fun useUp(uuid: String, note: String?): Result<Unit> =
        write(uuid) { apiProvider.api().useUpSpool(uuid, NoteRequest(note)).data }.map { }

    override suspend fun add(
        productId: Long,
        amount: Int,
        onSpool: Boolean,
        isRefill: Boolean,
        purchasedAt: LocalDate?,
    ): Result<List<String>> {
        val result = apiCall {
            apiProvider.api().createSpool(
                CreateSpoolRequest(
                    filamentProductId = productId,
                    amount = amount,
                    status = if (onSpool) SpoolStatus.ON_SPOOL else SpoolStatus.BOXED,
                    isRefill = isRefill,
                    purchasedAt = purchasedAt?.toString(),
                ),
            ).data
        }
        return handle(uuid = null, result).onSuccess { spools -> spools.forEach { store(it) } }.map { list -> list.map { it.uuid } }
    }

    override suspend fun update(uuid: String, changes: SpoolChanges): Result<Unit> =
        write(uuid) { apiProvider.api().patchSpool(uuid, changes.toJson()).data }.map { }

    override suspend fun remove(uuid: String, note: String?): Result<Unit> {
        val result = apiCall {
            val response = apiProvider.api().deleteSpool(uuid, NoteRequest(note))
            if (!response.isSuccessful) throw HttpException(response)
        }
        return handle(uuid, result).onSuccess {
            writes.record(uuid)
            spoolDao.deleteByUuids(listOf(uuid))
        }
    }

    override suspend fun events(uuid: String, page: Int): Result<Page<SpoolEventDto>> =
        apiCall { apiProvider.api().events(uuid, page) }

    suspend fun refreshAbilities(): Result<Unit> =
        apiCall { apiProvider.api().me().data }.map { session.updateAbilities(it.abilities.toSet()) }

    private suspend fun write(uuid: String?, call: suspend () -> SpoolDto): Result<SpoolDto> =
        handle(uuid, apiCall { call() }).onSuccess { store(it) }

    private suspend fun store(spool: SpoolDto) {
        writes.record(spool.uuid) // before the Room write, so a sync applying now skips this spool
        productDao.insertIgnore(listOf(spool.product.toEntity()))
        spoolDao.upsert(listOf(spool.toEntity()))
    }

    /** Side effects of failures: a missing spool leaves the cache; a refused write refreshes abilities. */
    private suspend fun <T> handle(uuid: String?, result: Result<T>): Result<T> {
        when (result.exceptionOrNull()) {
            ApiError.NotFound -> if (uuid != null) {
                writes.record(uuid)
                spoolDao.deleteByUuids(listOf(uuid))
            }
            ApiError.MissingAbility -> refreshAbilities()
            else -> Unit
        }
        return result
    }
}
