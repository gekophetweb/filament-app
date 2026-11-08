package nl.jaap.filamentspools.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings shared by Retrofit and tests. Tolerant of fields the app does not know yet. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class Envelope<T>(val data: T)

@Serializable
data class Page<T>(
    val data: List<T>,
    val links: PageLinks = PageLinks(),
    val meta: PageMeta = PageMeta(),
)

@Serializable
data class PageLinks(val next: String? = null)

@Serializable
data class PageMeta(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
)

/** A catalog product. The API's count fields are left out on purpose: counts are derived locally. */
@Serializable
data class ProductDto(
    val id: Long,
    val brand: String? = null,
    val material: String? = null,
    val line: String? = null,
    @SerialName("article_name") val articleName: String,
    @SerialName("article_number") val articleNumber: String,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("color_hex_2") val colorHex2: String? = null,
    @SerialName("color_family") val colorFamily: String? = null,
    val diameter: Double? = null,
    @SerialName("nominal_weight") val nominalWeight: Int? = null,
    @SerialName("has_refill") val hasRefill: Boolean = false,
    @SerialName("product_url") val productUrl: String? = null,
    @SerialName("buy_url") val buyUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SpoolDto(
    val uuid: String,
    val product: ProductDto,
    val status: String,
    @SerialName("is_refill") val isRefill: Boolean = false,
    @SerialName("purchased_at") val purchasedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class SpoolEventDto(
    val type: String,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MeDto(
    val id: Long,
    val name: String,
    val email: String,
    val abilities: List<String> = emptyList(),
)

@Serializable
data class PairedDeviceDto(
    val token: String,
    val abilities: List<String> = emptyList(),
    @SerialName("expires_at") val expiresAt: String? = null,
    val api: String? = null,
    val user: PairUserDto,
)

@Serializable
data class PairUserDto(val name: String, val email: String = "")

@Serializable
data class PairRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class NoteRequest(val note: String? = null)

/** `amount` is always sent, so the response is always a list (the API returns a single spool without it). */
@Serializable
data class CreateSpoolRequest(
    @SerialName("filament_product_id") val filamentProductId: Long,
    val amount: Int,
    val status: String,
    @SerialName("is_refill") val isRefill: Boolean,
    @SerialName("purchased_at") val purchasedAt: String? = null,
)

const val MAX_SPOOLS_PER_ADD = 50
