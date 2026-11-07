package nl.jaap.filamentspools.ui.common

import java.math.BigDecimal
import java.time.LocalDate

/** Blank → null; otherwise YYYY-MM-DD. */
fun parseDateField(text: String): Result<LocalDate?> {
    val t = text.trim()
    if (t.isEmpty()) return Result.success(null)
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(t)) return Result.failure(IllegalArgumentException("Use YYYY-MM-DD"))
    return runCatching { LocalDate.parse(t) }
}

/** Blank → null; otherwise a non-negative decimal. A comma counts as the decimal separator. */
fun parsePriceField(text: String): Result<BigDecimal?> {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return Result.success(null)
    val value = t.toBigDecimalOrNull() ?: return Result.failure(IllegalArgumentException("Enter a number"))
    return if (value.signum() < 0) Result.failure(IllegalArgumentException("Enter a number")) else Result.success(value)
}
