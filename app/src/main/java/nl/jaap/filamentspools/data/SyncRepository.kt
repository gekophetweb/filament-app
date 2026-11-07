package nl.jaap.filamentspools.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.jaap.filamentspools.api.ApiError
import nl.jaap.filamentspools.api.ApiProvider
import nl.jaap.filamentspools.api.Page
import nl.jaap.filamentspools.api.apiCall
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.SpoolDao
import nl.jaap.filamentspools.data.db.toEntity
import nl.jaap.filamentspools.data.session.CURSOR_CATALOG
import nl.jaap.filamentspools.data.session.CURSOR_SPOOLS
import nl.jaap.filamentspools.data.session.SessionStore
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

data class SyncState(val running: Boolean = false, val lastError: ApiError? = null)

/** What screens need from sync; lets ViewModels be tested with a fake. */
interface Syncer {
    val state: StateFlow<SyncState>
    suspend fun sync(): Result<Unit>
}

/**
 * Pulls spools and catalog into Room (spec section 6).
 * Pages are requested by number, never by following `links.next`, whose host may be unreachable.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val spoolDao: SpoolDao,
    private val productDao: ProductDao,
    private val session: SessionStore,
    @AppScope private val scope: CoroutineScope,
    private val clock: Clock,
) : Syncer {
    private val _state = MutableStateFlow(SyncState())
    override val state: StateFlow<SyncState> = _state

    private val lock = Mutex()
    private var inFlight: Deferred<Result<Unit>>? = null

    /** Syncs spools, then catalog. A call while a sync runs joins it. */
    override suspend fun sync(): Result<Unit> {
        val job = lock.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async { runSync() }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun runSync(): Result<Unit> {
        _state.update { it.copy(running = true) }
        val result = syncSpools().fold(onSuccess = { syncCatalog() }, onFailure = { Result.failure(it) })
        if (result.isSuccess) session.setLastSync(clock.instant())
        _state.value = SyncState(running = false, lastError = result.exceptionOrNull() as? ApiError)
        return result
    }

    private suspend fun syncSpools(): Result<Unit> {
        val cursor = session.cursor(CURSOR_SPOOLS)
        val rows = fetchAll(cursor) { page, since -> apiProvider.api().spools(PER_PAGE, page, since) }
            .getOrElse { return Result.failure(it) }

        productDao.insertIgnore(rows.map { it.product.toEntity() }.distinctBy { it.id })
        val (removed, live) = rows.partition { it.deletedAt != null }
        if (cursor == null) {
            spoolDao.replaceAll(live.map { it.toEntity() })
        } else {
            spoolDao.upsert(live.map { it.toEntity() })
            if (removed.isNotEmpty()) spoolDao.deleteByUuids(removed.map { it.uuid })
        }
        advanceCursor(CURSOR_SPOOLS, rows.map { it.updatedAt })
        return Result.success(Unit)
    }

    private suspend fun syncCatalog(): Result<Unit> {
        val cursor = session.cursor(CURSOR_CATALOG)
        val rows = fetchAll(cursor) { page, since -> apiProvider.api().catalog(PER_PAGE, page, since) }
            .getOrElse { return Result.failure(it) }
        productDao.upsert(rows.map { it.toEntity() })
        advanceCursor(CURSOR_CATALOG, rows.map { it.updatedAt })
        return Result.success(Unit)
    }

    private suspend fun <T> fetchAll(
        cursor: String?,
        call: suspend (page: Int, since: String?) -> Page<T>,
    ): Result<List<T>> {
        val since = cursor?.let { Instant.parse(it).minusSeconds(1).toString() }
        val all = mutableListOf<T>()
        var page = 1
        while (true) {
            val result = apiCall { call(page, since) }.getOrElse { return Result.failure(it) }
            all += result.data
            if (result.links.next == null || result.data.isEmpty() || page >= MAX_PAGES) break
            page++
        }
        return Result.success(all)
    }

    private suspend fun advanceCursor(key: String, updatedAts: List<String>) {
        val newest = updatedAts.mapNotNull(::parseInstant).maxOrNull() ?: return
        val old = session.cursor(key)?.let(Instant::parse)
        if (old == null || newest > old) session.setCursor(key, newest.toString())
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(value) }.getOrNull()

    private companion object {
        const val PER_PAGE = 100
        /** Safety stop against a server that never reports a last page. */
        const val MAX_PAGES = 1000
    }
}
