package nl.jaap.filamentspools.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import nl.jaap.filamentspools.data.db.AppDatabase
import nl.jaap.filamentspools.data.db.ProductEntity
import nl.jaap.filamentspools.data.db.SpoolEntity

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun product(
    id: Long,
    name: String = "Jade White",
    number: String = "1010$id",
    color: String? = "white",
    material: String? = "PLA",
    buyUrl: String? = null,
    hasRefill: Boolean = true,
) = ProductEntity(
    id = id, brand = "Bambu Lab", material = material, line = "PLA Basic", articleName = name,
    articleNumber = number, colorHex = "#FFFFFF", colorHex2 = null, colorFamily = color, diameter = 1.75,
    nominalWeight = 1000, hasRefill = hasRefill, productUrl = null, buyUrl = buyUrl,
    updatedAt = "2026-11-06T10:00:00+00:00",
)

fun spool(
    uuid: String,
    productId: Long,
    status: String = "boxed",
    updatedAt: String = "2026-11-01T09:30:00+00:00",
    purchasedAt: String? = "2026-10-02",
) = SpoolEntity(
    uuid = uuid, productId = productId, status = status, isRefill = false, purchasedAt = purchasedAt,
    createdAt = "2026-10-02T18:12:40+00:00", updatedAt = updatedAt,
)
