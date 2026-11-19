package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchlistGroupEntity::class,
        WatchlistItemEntity::class,
        CachedCandleSeriesEntity::class,
        CachedQuoteEntity::class,
        SavedPresetEntity::class,
        PriceAlertEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watchlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watchlist_items` (
                        `watchlistId` INTEGER NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `companyName` TEXT NOT NULL,
                        `sector` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`watchlistId`, `symbol`),
                        FOREIGN KEY(`watchlistId`) REFERENCES `watchlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watchlist_items_watchlistId` ON `watchlist_items` (`watchlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watchlist_items_symbol` ON `watchlist_items` (`symbol`)")

                val now = System.currentTimeMillis()
                db.execSQL("INSERT INTO `watchlists` (`id`, `name`, `createdAt`) VALUES (1, 'Favorites', $now)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `watchlist_items` (`watchlistId`, `symbol`, `companyName`, `sector`, `addedAt`)
                    SELECT 1, `symbol`, `companyName`, `sector`, `addedAt` FROM `watchlist_stocks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS `watchlist_stocks`")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `price_alerts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `companyName` TEXT NOT NULL,
                        `targetPrice` REAL NOT NULL,
                        `condition` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `lastCheckedPrice` REAL,
                        `triggeredPrice` REAL,
                        `triggeredAt` INTEGER,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_alerts_symbol` ON `price_alerts` (`symbol`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_alerts_isActive` ON `price_alerts` (`isActive`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nsepulse_market.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
