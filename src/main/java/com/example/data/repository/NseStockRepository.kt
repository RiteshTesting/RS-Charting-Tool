package com.example.data.repository

import com.example.data.local.CachedCandleSeriesEntity
import com.example.data.local.CachedQuoteEntity
import com.example.data.local.PriceAlertEntity
import com.example.data.local.SavedPresetEntity
import com.example.data.local.StockDao
import com.example.data.local.WatchlistGroupEntity
import com.example.data.local.WatchlistItemEntity
import com.example.data.remote.CandleSeriesResult
import com.example.data.remote.EodScreenerDataClient
import com.example.data.remote.EodScreenerPayload
import com.example.data.remote.NseDataClient
import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.AlertCondition
import com.example.model.Candle
import com.example.model.DataSource
import com.example.model.IndicatorSnapshot
import com.example.model.MaCondition
import com.example.model.MaType
import com.example.model.NamedWatchlist
import com.example.model.NseIndexUniverse
import com.example.model.NseSearchSuggestion
import com.example.model.NseStockItem
import com.example.model.PriceAlert
import com.example.model.RsiFilterMode
import com.example.model.SavedPreset
import com.example.model.ScreenerFilterState
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class UniverseScanUpdate(
    val stocks: List<NseStockItem>,
    val scannedCount: Int,
    val totalCount: Int,
    val primarySource: DataSource,
    val isComplete: Boolean
)

class NseStockRepository(
    private val dao: StockDao,
    private val nseClient: NseDataClient = NseDataClient(),
    private val eodClient: EodScreenerDataClient = EodScreenerDataClient()
) {
    private val memoryStockCache = ConcurrentHashMap<String, NseStockItem>()

    fun getCachedStock(symbol: String): NseStockItem? {
        return memoryStockCache[symbol.trim().uppercase()]
    }

    suspend fun fetchEodScreenerData(configuredUrl: String? = null): EodScreenerPayload? =
        withContext(Dispatchers.IO) {
            val payload = eodClient.fetchScreenerData(
                configuredUrl = configuredUrl,
                timestampMillis = System.currentTimeMillis()
            ) ?: return@withContext null

            cacheEodPayload(payload)
            payload
        }

    suspend fun importEodScreenerJson(jsonStr: String): EodScreenerPayload? =
        withContext(Dispatchers.IO) {
            val payload = EodScreenerDataClient.parseScreenerJson(
                jsonStr = jsonStr,
                originLabel = "EOD Local File"
            ) ?: return@withContext null
            cacheEodPayload(payload)
            payload
        }

    private suspend fun cacheEodPayload(payload: EodScreenerPayload) {
        val now = System.currentTimeMillis()
        payload.stocks.forEach { stock ->
            memoryStockCache[stock.symbol] = stock
            stock.indicators[Timeframe.D1]?.let { snap ->
                if (snap.candles.isNotEmpty()) {
                    dao.upsertCandles(
                        CachedCandleSeriesEntity(
                            symbol = stock.symbol,
                            timeframe = Timeframe.D1.name,
                            candlesJson = encodeCandles(snap.candles),
                            source = DataSource.EOD_JSON.name,
                            updatedAt = now
                        )
                    )
                }
            }
        }
        if (payload.stocks.isNotEmpty()) {
            dao.upsertQuotes(payload.stocks.map { it.toEntity("EOD_JSON") })
        }
    }

    val watchlistSymbols: Flow<Set<String>> = dao.observeAllWatchlistedSymbols().map { it.toSet() }

    val namedWatchlists: Flow<List<NamedWatchlist>> = dao.observeWatchlistsWithItems().map { list ->
        list.map { rel ->
            NamedWatchlist(
                id = rel.watchlist.id,
                name = rel.watchlist.name,
                symbols = rel.items.map { it.symbol }.toSet(),
                createdAt = rel.watchlist.createdAt
            )
        }
    }

    val savedPresets: Flow<List<SavedPreset>> = dao.observeSavedPresets().map { entities ->
        entities.mapNotNull { e ->
            runCatching {
                SavedPreset(
                    id = e.id,
                    name = e.name,
                    timeframe = Timeframe.valueOf(e.timeframe),
                    rsiPeriod = e.rsiPeriod,
                    rsiFilterMode = RsiFilterMode.valueOf(e.rsiMode),
                    rsiMin = e.rsiMin,
                    rsiMax = e.rsiMax,
                    maType = MaType.valueOf(e.maType),
                    maPeriod = e.maPeriod,
                    maCondition = MaCondition.valueOf(e.maCondition)
                )
            }.getOrNull()
        }
    }

    val priceAlerts: Flow<List<PriceAlert>> = dao.observeAllPriceAlerts().map { list ->
        list.map { it.toDomain() }
    }

    fun observePriceAlertsForSymbol(symbol: String): Flow<List<PriceAlert>> {
        return dao.observePriceAlertsForSymbol(symbol).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun createPriceAlert(
        symbol: String,
        companyName: String,
        targetPrice: Double,
        condition: AlertCondition,
        lastKnownPrice: Double?
    ): Long {
        return dao.insertPriceAlert(
            PriceAlertEntity(
                symbol = symbol.trim().uppercase(),
                companyName = companyName,
                targetPrice = targetPrice,
                condition = condition.name,
                isActive = true,
                lastCheckedPrice = lastKnownPrice,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setPriceAlertActive(alertId: Long, isActive: Boolean) {
        dao.setPriceAlertActive(alertId, isActive)
    }

    suspend fun deletePriceAlert(alertId: Long) {
        dao.deletePriceAlert(alertId)
    }

    private fun PriceAlertEntity.toDomain() = PriceAlert(
        id = id,
        symbol = symbol,
        companyName = companyName,
        targetPrice = targetPrice,
        condition = runCatching { AlertCondition.valueOf(condition) }.getOrDefault(AlertCondition.ABOVE),
        isActive = isActive,
        lastCheckedPrice = lastCheckedPrice,
        triggeredPrice = triggeredPrice,
        triggeredAt = triggeredAt,
        createdAt = createdAt
    )

    suspend fun ensureDefaultWatchlist() {
        if (dao.getAllWatchlistGroups().isEmpty()) {
            dao.insertWatchlistGroup(
                WatchlistGroupEntity(
                    name = "Favorites",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createWatchlist(name: String): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Watchlist name cannot be empty" }
        val existing = dao.findWatchlistByName(clean)
        if (existing != null) return existing.id
        return dao.insertWatchlistGroup(
            WatchlistGroupEntity(
                name = clean,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun renameWatchlist(watchlistId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        dao.renameWatchlistGroup(watchlistId, clean)
    }

    suspend fun deleteWatchlist(watchlistId: Long) {
        dao.deleteWatchlistGroup(watchlistId)
        ensureDefaultWatchlist()
    }

    suspend fun saveStockToWatchlistByName(stock: NseStockItem, watchlistName: String): Long {
        val id = createWatchlist(watchlistName)
        dao.upsertWatchlistItem(
            WatchlistItemEntity(
                watchlistId = id,
                symbol = stock.symbol,
                companyName = stock.companyName,
                sector = stock.sector,
                addedAt = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun toggleStockInWatchlist(stock: NseStockItem, watchlistId: Long) {
        if (dao.isStockInWatchlist(watchlistId, stock.symbol)) {
            dao.removeStockFromWatchlist(watchlistId, stock.symbol)
        } else {
            dao.upsertWatchlistItem(
                WatchlistItemEntity(
                    watchlistId = watchlistId,
                    symbol = stock.symbol,
                    companyName = stock.companyName,
                    sector = stock.sector,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeStockFromWatchlist(watchlistId: Long, symbol: String) {
        dao.removeStockFromWatchlist(watchlistId, symbol)
    }

    suspend fun saveFilterPreset(name: String, filter: ScreenerFilterState) {
        dao.insertPreset(
            SavedPresetEntity(
                name = name.trim(),
                timeframe = filter.timeframe.name,
                rsiPeriod = filter.rsiPeriod,
                rsiMode = filter.rsiFilterMode.name,
                rsiMin = filter.rsiMin,
                rsiMax = filter.rsiMax,
                maType = filter.maType.name,
                maPeriod = filter.maPeriod,
                maCondition = filter.maCondition.name,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePreset(id: Int) = dao.deletePreset(id)

    suspend fun searchNseEquities(query: String): List<NseSearchSuggestion> {
        return nseClient.searchNseSymbols(query)
    }

    suspend fun scanUniverse(
        universe: NseIndexUniverse,
        timeframe: Timeframe,
        forceRefresh: Boolean,
        onProgress: suspend (UniverseScanUpdate) -> Unit
    ): Result<UniverseScanUpdate> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedQuotes = dao.getQuotesForIndex(universe.name).map { it.toDomain() }
            if (cachedQuotes.isNotEmpty() && !forceRefresh) {
                val hydrated = cachedQuotes.map { q ->
                    val existing = memoryStockCache[q.symbol]
                    val cachedSeries = loadCachedSnapshot(q.symbol, timeframe, q.lastPrice)
                    val mergedMap = (existing?.indicators ?: emptyMap()) +
                        (cachedSeries?.let { mapOf(timeframe to it) } ?: emptyMap())
                    q.copy(indicators = mergedMap).also { memoryStockCache[it.symbol] = it }
                }
                onProgress(
                    UniverseScanUpdate(
                        stocks = hydrated,
                        scannedCount = hydrated.count { it.indicators.containsKey(timeframe) },
                        totalCount = hydrated.size,
                        primarySource = DataSource.LOCAL_CACHE,
                        isComplete = false
                    )
                )
            }

            // 1. Fetch live NSE index quotes
            val (nseQuotes, indexSource) = nseClient.fetchIndexQuotes(universe)
            val baseStocks: MutableList<NseStockItem> = if (nseQuotes.isNotEmpty()) {
                dao.upsertQuotes(nseQuotes.map { it.toEntity(universe.name) })
                nseQuotes.map { fresh ->
                    val prev = memoryStockCache[fresh.symbol]
                    fresh.copy(indicators = prev?.indicators ?: emptyMap())
                        .also { memoryStockCache[it.symbol] = it }
                }.toMutableList()
            } else {
                val fallbackList = universe.seedSymbols.map { sym ->
                    memoryStockCache[sym] ?: cachedQuotes.find { it.symbol == sym } ?: NseStockItem(
                        symbol = sym,
                        companyName = sym,
                        sector = universe.displayName,
                        lastPrice = 0.0,
                        change = 0.0,
                        changePercent = 0.0,
                        dayHigh = 0.0,
                        dayLow = 0.0,
                        volume = 0L,
                        yearHigh = 0.0,
                        yearLow = 0.0
                    )
                }
                fallbackList.toMutableList()
            }

            if (baseStocks.isEmpty()) {
                error("No NSE constituent symbols available")
            }

            val total = baseStocks.size
            val stockMap = ConcurrentHashMap<String, NseStockItem>()
            baseStocks.forEach { stockMap[it.symbol] = it }

            onProgress(
                UniverseScanUpdate(
                    stocks = baseStocks.toList(),
                    scannedCount = 0,
                    totalCount = total,
                    primarySource = indexSource,
                    isComplete = false
                )
            )

            // 2. Fetch historical candles & compute RSI + Moving Averages concurrently
            val semaphore = Semaphore(6)
            var completed = 0
            var resolvedSource = indexSource
            val progressLock = Any()

            coroutineScope {
                baseStocks.map { item ->
                    async {
                        semaphore.withPermit {
                            val updated = computeIndicatorForStock(
                                base = item,
                                timeframe = timeframe,
                                forceRefresh = forceRefresh
                            )
                            if (updated != null) {
                                stockMap[item.symbol] = updated
                                memoryStockCache[item.symbol] = updated
                                val snapSource = updated.indicators[timeframe]?.source
                                if (snapSource == DataSource.NSE_DIRECT) {
                                    resolvedSource = DataSource.NSE_DIRECT
                                }
                            }
                            val shouldEmit: Boolean
                            val currentCount: Int
                            synchronized(progressLock) {
                                completed++
                                currentCount = completed
                                shouldEmit = (completed % 5 == 0) || (completed == total)
                            }
                            if (shouldEmit) {
                                val ordered = baseStocks.mapNotNull { stockMap[it.symbol] }
                                    .filter { it.lastPrice > 0.0 }
                                onProgress(
                                    UniverseScanUpdate(
                                        stocks = ordered,
                                        scannedCount = currentCount,
                                        totalCount = total,
                                        primarySource = resolvedSource,
                                        isComplete = currentCount == total
                                    )
                                )
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalStocks = baseStocks.mapNotNull { stockMap[it.symbol] }
                .filter { it.lastPrice > 0.0 }
            if (finalStocks.isEmpty()) {
                error("Unable to fetch live NSE data. Check your internet connection and retry.")
            }

            dao.upsertQuotes(finalStocks.map { it.toEntity(universe.name) })

            UniverseScanUpdate(
                stocks = finalStocks,
                scannedCount = total,
                totalCount = total,
                primarySource = resolvedSource,
                isComplete = true
            )
        }
    }

    suspend fun fetchSingleStockWithTimeframe(
        symbol: String,
        timeframe: Timeframe,
        forceRefresh: Boolean = false
    ): Result<NseStockItem> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = symbol.trim().uppercase()
            val cached = memoryStockCache[clean] ?: dao.getQuote(clean)?.toDomain()
            val liveQuote = nseClient.fetchSingleNseQuote(clean) ?: cached
                ?: NseStockItem(
                    symbol = clean,
                    companyName = clean,
                    sector = "NSE Equity",
                    lastPrice = 0.0,
                    change = 0.0,
                    changePercent = 0.0,
                    dayHigh = 0.0,
                    dayLow = 0.0,
                    volume = 0L,
                    yearHigh = 0.0,
                    yearLow = 0.0
                )

            val enriched = computeIndicatorForStock(liveQuote, timeframe, forceRefresh)
                ?: error("Could not load NSE data for $clean")

            if (enriched.lastPrice <= 0.0) {
                error("No live NSE price found for $clean")
            }

            memoryStockCache[clean] = enriched
            dao.upsertQuotes(listOf(enriched.toEntity("CUSTOM")))
            enriched
        }
    }

    suspend fun loadAllTimeframesForStock(
        symbol: String,
        forceRefresh: Boolean = false
    ): Result<NseStockItem> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = symbol.trim().uppercase()
            var base = memoryStockCache[clean]
                ?: dao.getQuote(clean)?.toDomain()
                ?: nseClient.fetchSingleNseQuote(clean)
                ?: error("Stock $clean not found on NSE")

            if (base.lastPrice <= 0.0) {
                base = nseClient.fetchSingleNseQuote(clean) ?: base
            }

            val snapshots = ConcurrentHashMap<Timeframe, IndicatorSnapshot>()
            snapshots.putAll(base.indicators)

            coroutineScope {
                Timeframe.entries.map { tf ->
                    async {
                        val existingEod = base.indicators[tf]?.takeIf { it.source == DataSource.EOD_JSON }
                        if (!forceRefresh && existingEod != null) {
                            snapshots[tf] = existingEod
                            return@async
                        }
                        val updated = computeIndicatorForStock(base, tf, forceRefresh)
                        if (updated != null) {
                            updated.indicators[tf]?.let { snapshots[tf] = it }
                            if (base.lastPrice <= 0.0 && updated.lastPrice > 0.0) {
                                base = updated
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalItem = base.copy(indicators = snapshots.toMap())
            memoryStockCache[clean] = finalItem
            finalItem
        }
    }

    suspend fun refreshWatchlistSymbols(
        symbols: Set<String>,
        timeframe: Timeframe
    ): List<NseStockItem> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyList()
        val semaphore = Semaphore(5)
        coroutineScope {
            symbols.map { sym ->
                async {
                    semaphore.withPermit {
                        fetchSingleStockWithTimeframe(sym, timeframe, forceRefresh = false)
                            .getOrNull()
                            ?.copy(isWatchlisted = true)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun computeIndicatorForStock(
        base: NseStockItem,
        timeframe: Timeframe,
        forceRefresh: Boolean
    ): NseStockItem? {
        val ttlMs = when (timeframe) {
            Timeframe.M15 -> 3 * 60_000L
            Timeframe.H1 -> 10 * 60_000L
            else -> 30 * 60_000L
        }

        if (!forceRefresh) {
            val cachedEntity = dao.getCachedCandles(base.symbol, timeframe.name)
            if (cachedEntity != null && (System.currentTimeMillis() - cachedEntity.updatedAt) < ttlMs) {
                val candles = decodeCandles(cachedEntity.candlesJson)
                if (candles.size >= 15 && base.lastPrice > 0.0) {
                    val snapshot = TechnicalIndicatorCalculator.buildSnapshot(
                        timeframe = timeframe,
                        candles = candles,
                        livePrice = base.lastPrice,
                        source = runCatching { DataSource.valueOf(cachedEntity.source) }
                            .getOrDefault(DataSource.LOCAL_CACHE)
                    )
                    val merged = base.indicators + (timeframe to snapshot)
                    return base.copy(indicators = merged)
                }
            }
        }

        val remoteResult: CandleSeriesResult? = nseClient.fetchCandlesForTimeframe(base.symbol, timeframe)
        if (remoteResult != null && remoteResult.candles.isNotEmpty()) {
            val patch = remoteResult.quotePatch
            val updatedBase = if (patch != null && (base.lastPrice <= 0.0 || base.companyName == base.symbol)) {
                base.copy(
                    companyName = if (base.companyName == base.symbol) patch.companyName else base.companyName,
                    lastPrice = if (base.lastPrice <= 0.0) patch.lastPrice else base.lastPrice,
                    change = if (base.lastPrice <= 0.0) patch.change else base.change,
                    changePercent = if (base.lastPrice <= 0.0) patch.changePercent else base.changePercent,
                    dayHigh = if (base.dayHigh <= 0.0) patch.dayHigh else base.dayHigh,
                    dayLow = if (base.dayLow <= 0.0) patch.dayLow else base.dayLow,
                    volume = if (base.volume <= 0L) patch.volume else base.volume,
                    yearHigh = if (base.yearHigh <= 0.0) patch.yearHigh else base.yearHigh,
                    yearLow = if (base.yearLow <= 0.0) patch.yearLow else base.yearLow
                )
            } else {
                base
            }

            dao.upsertCandles(
                CachedCandleSeriesEntity(
                    symbol = updatedBase.symbol,
                    timeframe = timeframe.name,
                    candlesJson = encodeCandles(remoteResult.candles),
                    source = remoteResult.source.name,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val snapshot = TechnicalIndicatorCalculator.buildSnapshot(
                timeframe = timeframe,
                candles = remoteResult.candles,
                livePrice = updatedBase.lastPrice.takeIf { it > 0.0 },
                source = remoteResult.source
            )
            return updatedBase.copy(indicators = updatedBase.indicators + (timeframe to snapshot))
        }

        val stale = loadCachedSnapshot(base.symbol, timeframe, base.lastPrice)
        if (stale != null) {
            return base.copy(indicators = base.indicators + (timeframe to stale.copy(source = DataSource.LOCAL_CACHE)))
        }
        return if (base.lastPrice > 0.0) base else null
    }

    private suspend fun loadCachedSnapshot(
        symbol: String,
        timeframe: Timeframe,
        livePrice: Double
    ): IndicatorSnapshot? {
        val entity = dao.getCachedCandles(symbol, timeframe.name) ?: return null
        val candles = decodeCandles(entity.candlesJson)
        if (candles.isEmpty()) return null
        return TechnicalIndicatorCalculator.buildSnapshot(
            timeframe = timeframe,
            candles = candles,
            livePrice = livePrice.takeIf { it > 0.0 },
            source = DataSource.LOCAL_CACHE
        )
    }

    private fun encodeCandles(candles: List<Candle>): String {
        val arr = JSONArray()
        for (c in candles) {
            val obj = JSONObject()
            obj.put("t", c.timestampMillis)
            obj.put("o", c.open)
            obj.put("h", c.high)
            obj.put("l", c.low)
            obj.put("c", c.close)
            obj.put("v", c.volume)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCandles(json: String): List<Candle> {
        return runCatching {
            val arr = JSONArray(json)
            val list = ArrayList<Candle>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Candle(
                        timestampMillis = obj.getLong("t"),
                        open = obj.getDouble("o"),
                        high = obj.getDouble("h"),
                        low = obj.getDouble("l"),
                        close = obj.getDouble("c"),
                        volume = obj.optLong("v", 0L)
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    private fun CachedQuoteEntity.toDomain() = NseStockItem(
        symbol = symbol,
        companyName = companyName,
        sector = sector,
        lastPrice = lastPrice,
        change = change,
        changePercent = changePercent,
        dayHigh = dayHigh,
        dayLow = dayLow,
        volume = volume,
        yearHigh = yearHigh,
        yearLow = yearLow
    )

    private fun NseStockItem.toEntity(indexTag: String) = CachedQuoteEntity(
        symbol = symbol,
        companyName = companyName,
        sector = sector,
        lastPrice = lastPrice,
        change = change,
        changePercent = changePercent,
        dayHigh = dayHigh,
        dayLow = dayLow,
        volume = volume,
        yearHigh = yearHigh,
        yearLow = yearLow,
        indexTag = indexTag,
        updatedAt = System.currentTimeMillis()
    )
}
