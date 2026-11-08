package nl.jaap.filamentspools.ui.common

import nl.jaap.filamentspools.data.db.SpoolStatus

/** Human label for a spool status; unknown statuses are shown as sent by the server. */
fun statusLabel(status: String): String = when (status) {
    SpoolStatus.BOXED -> "Boxed"
    SpoolStatus.ON_SPOOL -> "On spool"
    SpoolStatus.USED_UP -> "Used up"
    SpoolStatus.ARCHIVED -> "Archived"
    else -> status
}

/** Human label for a history event type. */
fun eventLabel(type: String): String = when (type) {
    "added" -> "Added"
    "opened" -> "Put on spool"
    "used_up" -> "Used up"
    "moved" -> "Moved"
    "removed", "deleted" -> "Removed"
    "archived" -> "Archived"
    "weighed" -> "Weighed"
    "used" -> "Used"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
