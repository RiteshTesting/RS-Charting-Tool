package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "watchlists")
data class WatchlistGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "watchlist_items",
    primaryKeys = ["watchlistId", "symbol"],
    foreignKeys = [
        ForeignKey(
            entity = WatchlistGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["watchlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["watchlistId"]),
        Index(value = ["symbol"])
    ]
)
data class WatchlistItemEntity(
    val watchlistId: Long,
    val symbol: String,
    val companyName: String,
    val sector: String,
    val addedAt: Long = System.currentTimeMillis()
)

data class WatchlistWithItems(
    @Embedded val watchlist: WatchlistGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "watchlistId"
    )
    val items: List<WatchlistItemEntity>
)

@Entity(tableName = "cached_candles", primaryKeys = ["symbol", "timeframe"])
data class CachedCandleSeriesEntity(
    val symbol: String,
    val timeframe: String,
    val candlesJson: String,
    val source: String,
    val updatedAt: Long
)

@Entity(tableName = "cached_quotes")
data class CachedQuoteEntity(
    @PrimaryKey val symbol: String,
    val companyName: String,
    val sector: String,
    val lastPrice: Double,
    val change: Double,
    val changePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val volume: Long,
    val yearHigh: Double,
    val yearLow: Double,
    val indexTag: String,
    val updatedAt: Long
)

@Entity(tableName = "saved_presets")
data class SavedPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timeframe: String,
    val rsiPeriod: Int,
    val rsiMode: String,
    val rsiMin: Float,
    val rsiMax: Float,
    val maType: String,
    val maPeriod: Int,
    val maCondition: String,
    val createdAt: Long
)

@Entity(
    tableName = "price_alerts",
    indices = [Index(value = ["symbol"]), Index(value = ["isActive"])]
)
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val companyName: String,
    val targetPrice: Double,
    val condition: String,
    val isActive: Boolean = true,
    val lastCheckedPrice: Double? = null,
    val triggeredPrice: Double? = null,
    val triggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
