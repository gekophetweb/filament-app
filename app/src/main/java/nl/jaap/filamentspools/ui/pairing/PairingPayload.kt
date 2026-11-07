package nl.jaap.filamentspools.ui.pairing

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import nl.jaap.filamentspools.api.ApiJson
import java.time.Instant
import java.time.OffsetDateTime

/** The JSON inside the Devices-page QR code. */
data class PairingPayload(val api: String, val code: String, val expiresAt: Instant?)

object PairingPayloadParser {
    private const val TYPE = "filament-spools-pairing"

    fun parse(text: String): PairingPayload? {
        val obj = runCatching { ApiJson.parseToJsonElement(text.trim()) as? JsonObject }.getOrNull() ?: return null
        fun field(name: String) = runCatching { obj[name]?.jsonPrimitive?.contentOrNull }.getOrNull()
        if (field("type") != TYPE) return null
        val code = field("code")?.takeIf { it.isNotBlank() } ?: return null
        val api = field("api").orEmpty()
        val expiresAt = field("expires_at")?.let {
            runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(it) }.getOrNull()
        }
        return PairingPayload(api, code, expiresAt)
    }
}
