package nl.jaap.filamentspools.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import nl.jaap.filamentspools.api.ProductDto
import nl.jaap.filamentspools.api.SpoolDto

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Long,
    val brand: String?,
    val material: String?,
    val line: String?,
    val articleName: String,
    val articleNumber: String,
    val colorHex: String?,
    val colorHex2: String?,
    val colorFamily: String?,
    val diameter: Double?,
    val nominalWeight: Int?,
    val hasRefill: Boolean,
    val productUrl: String?,
    val buyUrl: String?,
    val updatedAt: String,
)

/** A spool. `productId` has no foreign key: spools may reference products not in the cached catalog. */
@Entity(tableName = "spools", indices = [Index("productId"), Index("status")])
data class SpoolEntity(
    @PrimaryKey val uuid: String,
    val productId: Long,
    val status: String,
    val isRefill: Boolean,
    val purchasedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class SpoolWithProduct(
    @Embedded val spool: SpoolEntity,
    @Relation(parentColumn = "productId", entityColumn = "id")
    val product: ProductEntity?,
)

/** A product with your own counts, derived from the cached spools. */
data class ProductWithCounts(
    @Embedded val product: ProductEntity,
    val boxed: Int,
    val onSpool: Int,
) {
    val owned: Int get() = boxed + onSpool
}

enum class OwnedFilter { All, Owned, NotOwned, Boxed, OnSpool, RunningOut }

object SpoolStatus {
    const val BOXED = "boxed"
    const val ON_SPOOL = "on_spool"
    const val USED_UP = "used_up"
    const val ARCHIVED = "archived"
}

fun ProductDto.toEntity() = ProductEntity(
    id = id, brand = brand, material = material, line = line, articleName = articleName,
    articleNumber = articleNumber, colorHex = colorHex, colorHex2 = colorHex2, colorFamily = colorFamily,
    diameter = diameter, nominalWeight = nominalWeight, hasRefill = hasRefill, productUrl = productUrl,
    buyUrl = buyUrl, updatedAt = updatedAt,
)

fun SpoolDto.toEntity() = SpoolEntity(
    uuid = uuid, productId = product.id, status = status, isRefill = isRefill, purchasedAt = purchasedAt,
    createdAt = createdAt, updatedAt = updatedAt,
)
