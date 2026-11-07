package nl.jaap.filamentspools.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which spools this app wrote, so a sync whose pages were fetched before a write
 * does not overwrite (or resurrect) that spool in Room.
 */
@Singleton
class LocalWrites @Inject constructor() {
    private val clock = AtomicLong()
    private val written = ConcurrentHashMap<String, Long>()

    /** A point in the write sequence; pass it to [writtenSince]. */
    fun mark(): Long = clock.get()

    fun record(uuid: String) {
        written[uuid] = clock.incrementAndGet()
    }

    /** Spools written after [mark]. */
    fun writtenSince(mark: Long): Set<String> = written.filterValues { it > mark }.keys

    /** Forgets entries no running sync can need any more. */
    fun forgetUpTo(mark: Long) {
        written.entries.removeIf { it.value <= mark }
    }
}
