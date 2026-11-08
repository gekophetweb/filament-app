package nl.jaap.filamentspools.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The API dropped price, location and notes from spools. SQLite before 3.35 (Android < 12)
 * cannot drop columns, so the table is rebuilt; cached spools are kept.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `spools_new` (`uuid` TEXT NOT NULL, `productId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`isRefill` INTEGER NOT NULL, `purchasedAt` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, " +
                "PRIMARY KEY(`uuid`))",
        )
        db.execSQL(
            "INSERT INTO `spools_new` (uuid, productId, status, isRefill, purchasedAt, createdAt, updatedAt) " +
                "SELECT uuid, productId, status, isRefill, purchasedAt, createdAt, updatedAt FROM `spools`",
        )
        db.execSQL("DROP TABLE `spools`")
        db.execSQL("ALTER TABLE `spools_new` RENAME TO `spools`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_spools_productId` ON `spools` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_spools_status` ON `spools` (`status`)")
    }
}
