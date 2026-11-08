package nl.jaap.filamentspools.ui.common

import java.time.LocalDate

/** Blank → null; otherwise YYYY-MM-DD. */
fun parseDateField(text: String): Result<LocalDate?> {
    val t = text.trim()
    if (t.isEmpty()) return Result.success(null)
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(t)) return Result.failure(IllegalArgumentException("Use YYYY-MM-DD"))
    return runCatching { LocalDate.parse(t) }
}
