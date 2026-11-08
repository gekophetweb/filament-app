package nl.jaap.filamentspools.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Version 1 had price, location and notes on spools; the API dropped them, version 2 drops the columns. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migration1To2KeepsSpools() = runBlocking {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE `products` (`id` INTEGER NOT NULL, `brand` TEXT, `material` TEXT, `line` TEXT, " +
                    "`articleName` TEXT NOT NULL, `articleNumber` TEXT NOT NULL, `colorHex` TEXT, `colorHex2` TEXT, " +
                    "`colorFamily` TEXT, `diameter` REAL, `nominalWeight` INTEGER, `hasRefill` INTEGER NOT NULL, " +
                    "`productUrl` TEXT, `buyUrl` TEXT, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE `spools` (`uuid` TEXT NOT NULL, `productId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                    "`isRefill` INTEGER NOT NULL, `purchasedAt` TEXT, `price` TEXT, `location` TEXT, `notes` TEXT, " +
                    "`createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`uuid`))",
            )
            db.execSQL("CREATE INDEX `index_spools_productId` ON `spools` (`productId`)")
            db.execSQL("CREATE INDEX `index_spools_status` ON `spools` (`status`)")
            db.execSQL(
                "INSERT INTO spools VALUES ('u1', 7, 'on_spool', 0, '2026-10-02', '24.99', 'Shelf A', NULL, " +
                    "'2026-10-02T18:12:40+00:00', '2026-11-01T09:30:00+00:00')",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val row = room.spoolDao().observe("u1").first()!!.spool
            assertEquals("on_spool", row.status)
            assertEquals("2026-10-02", row.purchasedAt)
            assertEquals(7L, row.productId)
        } finally {
            room.close()
        }
    }
}
