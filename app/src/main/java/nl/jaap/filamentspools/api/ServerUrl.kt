package nl.jaap.filamentspools.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns whatever the user typed (or the QR held) into a base URL ending in `/api/v1/`. */
object ServerUrl {
    private const val API_PATH = "/api/v1"

    fun normalize(input: String): String {
        var url = input.trim()
        require(url.isNotEmpty()) { "Enter the server address" }
        if (!url.contains("://")) url = "http://$url"
        require(url.substringAfter("://").trimEnd('/').isNotBlank()) { "Not a valid server address" }
        url = url.trimEnd('/')
        if (!url.endsWith(API_PATH)) url += API_PATH
        url += "/"
        val parsed = url.toHttpUrlOrNull()
        require(parsed != null && parsed.host.isNotEmpty()) { "Not a valid server address" }
        return parsed.toString()
    }
}
