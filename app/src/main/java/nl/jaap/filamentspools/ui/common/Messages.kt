package nl.jaap.filamentspools.ui.common

import nl.jaap.filamentspools.api.ApiError

/** User-facing text for every failure (spec section 7). */
object Messages {
    const val TOKEN_INVALID = "Your device token is no longer valid — pair again"
    const val SPOOL_GONE = "This spool no longer exists"

    fun text(error: Throwable?, serverUrl: String?): String = when (error) {
        ApiError.Unauthorized -> TOKEN_INVALID
        ApiError.MissingAbility -> "This device has a read-only token, so it cannot make changes."
        ApiError.IpBlocked -> "This IP address is blocked for 10 minutes after too many failed attempts."
        ApiError.NotFound -> SPOOL_GONE
        is ApiError.Validation -> error.message
        is ApiError.RateLimited ->
            error.retryAfterSeconds?.let { "Too many requests — try again in $it s" } ?: "Too many requests — try again shortly"
        is ApiError.Network -> "Can't reach the server at ${serverUrl ?: "the configured address"}"
        is ApiError.Http ->
            if (error.code < 0) "The server sent a response this app does not understand" else "Server error (HTTP ${error.code})"
        is IllegalArgumentException -> error.message ?: "Invalid input"
        else -> "Something went wrong"
    }
}
