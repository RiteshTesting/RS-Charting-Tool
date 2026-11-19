package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Transaction
    @Query("SELECT * FROM watchlists ORDER BY createdAt ASC")
    fun observeWatchlistsWithItems(): Flow<List<WatchlistWithItems>>

    @Query("SELECT DISTINCT symbol FROM watchlist_items")
    fun observeAllWatchlistedSymbols(): Flow<List<String>>

    @Query("SELECT * FROM watchlist_items WHERE watchlistId = :watchlistId ORDER BY addedAt DESC")
    fun observeItemsForWatchlist(watchlistId: Long): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlists ORDER BY createdAt ASC")
    suspend fun getAllWatchlistGroups(): List<WatchlistGroupEntity>

    @Query("SELECT * FROM watchlists WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findWatchlistByName(name: String): WatchlistGroupEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchlistGroup(group: WatchlistGroupEntity): Long

    @Query("UPDATE watchlists SET name = :newName WHERE id = :watchlistId")
    suspend fun renameWatchlistGroup(watchlistId: Long, newName: String)

    @Query("DELETE FROM watchlists WHERE id = :watchlistId")
    suspend fun deleteWatchlistGroup(watchlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlistItem(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist_items WHERE watchlistId = :watchlistId AND symbol = :symbol")
    suspend fun removeStockFromWatchlist(watchlistId: Long, symbol: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE watchlistId = :watchlistId AND symbol = :symbol)")
    suspend fun isStockInWatchlist(watchlistId: Long, symbol: String): Boolean

    @Query("SELECT * FROM cached_quotes WHERE indexTag = :indexTag ORDER BY symbol ASC")
    suspend fun getQuotesForIndex(indexTag: String): List<CachedQuoteEntity>

    @Query("SELECT * FROM cached_quotes WHERE symbol = :symbol LIMIT 1")
    suspend fun getQuote(symbol: String): CachedQuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuotes(quotes: List<CachedQuoteEntity>)

    @Query("SELECT * FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe LIMIT 1")
    suspend fun getCachedCandles(symbol: String, timeframe: String): CachedCandleSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(entity: CachedCandleSeriesEntity)

    @Query("SELECT * FROM saved_presets ORDER BY createdAt DESC")
    fun observeSavedPresets(): Flow<List<SavedPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: SavedPresetEntity)

    @Query("DELETE FROM saved_presets WHERE id = :id")
    suspend fun deletePreset(id: Int)

    @Query("SELECT * FROM price_alerts ORDER BY isActive DESC, createdAt DESC")
    fun observeAllPriceAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol ORDER BY createdAt DESC")
    fun observePriceAlertsForSymbol(symbol: String): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE isActive = 1")
    suspend fun getActivePriceAlerts(): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceAlert(alert: PriceAlertEntity): Long

    @Query(
        """
        UPDATE price_alerts
        SET isActive = 0, triggeredPrice = :triggeredPrice, triggeredAt = :triggeredAt, lastCheckedPrice = :triggeredPrice
        WHERE id = :alertId
        """
    )
    suspend fun markPriceAlertTriggered(alertId: Long, triggeredPrice: Double, triggeredAt: Long)

    @Query("UPDATE price_alerts SET lastCheckedPrice = :lastPrice WHERE id = :alertId")
    suspend fun updatePriceAlertLastChecked(alertId: Long, lastPrice: Double)

    @Query("UPDATE price_alerts SET isActive = :isActive, triggeredPrice = NULL, triggeredAt = NULL WHERE id = :alertId")
    suspend fun setPriceAlertActive(alertId: Long, isActive: Boolean)

    @Query("DELETE FROM price_alerts WHERE id = :alertId")
    suspend fun deletePriceAlert(alertId: Long)
}
