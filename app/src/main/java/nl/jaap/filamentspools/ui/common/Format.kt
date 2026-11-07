package nl.jaap.filamentspools.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatLocal(instant: Instant, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern).format(instant.atZone(ZoneId.systemDefault()))
