package com.example.model

enum class Timeframe(
    val label: String,
    val displayName: String,
    val yahooInterval: String,
    val yahooRange: String,
    val nseLookbackDays: Int
) {
    M15("15m", "15 Min", "15m", "1mo", 5),
    H1("1H", "1 Hour", "60m", "3mo", 30),
    D1("1D", "Daily", "1d", "2y", 420),
    W1("1W", "Weekly", "1wk", "5y", 1825),
    MN1("1M", "Monthly", "1mo", "max", 3650)
}

enum class NseIndexUniverse(
    val displayName: String,
    val nseQueryParam: String,
    val seedSymbols: List<String>
) {
    NIFTY_50(
        displayName = "NIFTY 50",
        nseQueryParam = "NIFTY 50",
        seedSymbols = listOf(
            "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "BHARTIARTL", "INFY", "SBIN",
            "LICI", "ITC", "HINDUNILVR", "LT", "BAJFINANCE", "HCLTECH", "MARUTI",
            "SUNPHARMA", "ADANIENT", "KOTAKBANK", "TITAN", "ONGC", "TATAMOTORS",
            "NTPC", "AXISBANK", "ADANIPORTS", "ULTRACEMCO", "POWERGRID", "ASIANPAINT",
            "COALINDIA", "BAJAJFINSV", "WIPRO", "NESTLEIND", "M&M", "JSWSTEEL",
            "TATASTEEL", "GRASIM", "TECHM", "HINDALCO", "BEL", "TRENT", "CIPLA",
            "DRREDDY", "EICHERMOT", "BPCL", "HEROMOTOCO", "APOLLOHOSP", "DIVISLAB",
            "TATACONSUM", "BRITANNIA", "INDUSINDBK", "SHRIRAMFIN", "HDFCLIFE"
        )
    ),
    NIFTY_BANK(
        displayName = "NIFTY BANK",
        nseQueryParam = "NIFTY BANK",
        seedSymbols = listOf(
            "HDFCBANK", "ICICIBANK", "SBIN", "KOTAKBANK", "AXISBANK", "INDUSINDBK",
            "BANKBARODA", "PNB", "AUBANK", "FEDERALBNK", "IDFCFIRSTB", "CANBK"
        )
    ),
    NIFTY_IT(
        displayName = "NIFTY IT",
        nseQueryParam = "NIFTY IT",
        seedSymbols = listOf(
            "TCS", "INFY", "HCLTECH", "WIPRO", "TECHM", "LTIM", "PERSISTENT",
            "COFORGE", "MPHASIS", "LTTS"
        )
    ),
    NIFTY_NEXT_50(
        displayName = "NIFTY NEXT 50",
        nseQueryParam = "NIFTY NEXT 50",
        seedSymbols = listOf(
            "HAL", "ZOMATO", "JIOFIN", "VBL", "DLF", "SIEMENS", "PIDILITIND",
            "IOC", "GAIL", "AMBUJACEM", "INDIGO", "ABB", "CHOLAFIN", "HAVELLS",
            "BANKBARODA", "PFC", "RECLTD", "TVSMOTOR", "VEDL", "DMART"
        )
    ),
    NIFTY_MIDCAP_50(
        displayName = "MIDCAP 50",
        nseQueryParam = "NIFTY MIDCAP 50",
        seedSymbols = listOf(
            "SUZLON", "IRCTC", "POLYCAB", "DIXON", "PERSISTENT", "COFORGE",
            "BHEL", "ASHOKLEY", "AUROPHARMA", "LUPIN", "FEDERALBNK", "IDFCFIRSTB",
            "VOLTAS", "CUMMINSIND", "MRF", "ASTRAL", "ALKEM", "OBEROIRLTY",
            "PIIND", "OFSS"
        )
    )
}

enum class DataSource(val label: String) {
    EOD_JSON("EOD JSON"),
    NSE_DIRECT("NSE Live"),
    NSE_FEED("NSE (.NS) Live"),
    LOCAL_CACHE("Cached NSE")
}

enum class TechnicalSignal(val label: String) {
    STRONG_BUY("Strong Bullish"),
    BULLISH("Bullish"),
    OVERSOLD("Oversold"),
    NEUTRAL("Neutral"),
    OVERBOUGHT("Overbought"),
    BEARISH("Bearish"),
    STRONG_SELL("Strong Bearish")
}

data class Candle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class IndicatorSnapshot(
    val timeframe: Timeframe,
    val rsi9: Double?,
    val rsi14: Double?,
    val rsi21: Double?,
    val rsi14Ema45: Double? = null,
    val prevRsi14: Double? = null,
    val hmEma3: Double? = null,
    val hmWma21: Double? = null,
    val sma20: Double?,
    val sma50: Double?,
    val sma100: Double?,
    val sma200: Double?,
    val ema9: Double? = null,
    val ema20: Double?,
    val ema45: Double? = null,
    val ema50: Double?,
    val ema100: Double?,
    val ema200: Double?,
    val bbUpper: Double? = null,
    val bbLower: Double? = null,
    val volRatio: Double? = null,
    val prevClose: Double? = null,
    val prevEma20: Double? = null,
    val prevSma50: Double? = null,
    val prevSma200: Double? = null,
    val avgVolume20: Double? = null,
    val rsiHistory: List<Double?>,
    val rsi14Ema45History: List<Double?> = emptyList(),
    val ema9History: List<Double?> = emptyList(),
    val ema20History: List<Double?>,
    val ema45History: List<Double?> = emptyList(),
    val sma50History: List<Double?>,
    val sma200History: List<Double?>,
    val bbUpperHistory: List<Double?> = emptyList(),
    val bbLowerHistory: List<Double?> = emptyList(),
    val candles: List<Candle>,
    val signal: TechnicalSignal,
    val source: DataSource
) {
    fun rsiForPeriod(period: Int): Double? = when (period) {
        9 -> rsi9
        21 -> rsi21
        else -> rsi14
    }

    fun maValue(type: MaType, period: Int): Double? = when (type) {
        MaType.EMA -> when (period) {
            9 -> ema9
            20 -> ema20
            45 -> ema45
            50 -> ema50
            100 -> ema100
            200 -> ema200
            else -> ema50
        }
        MaType.SMA -> when (period) {
            20 -> sma20
            50 -> sma50
            100 -> sma100
            200 -> sma200
            else -> sma50
        }
    }
}

data class NseStockItem(
    val symbol: String,
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
    val volRatio: Double? = null,
    val distFrom52wPct: Double? = null,
    val rsRating: Double? = null,
    val isWatchlisted: Boolean = false,
    val indicators: Map<Timeframe, IndicatorSnapshot> = emptyMap()
)

data class NseSearchSuggestion(
    val symbol: String,
    val companyName: String,
    val resultType: String
)

enum class RsiFilterMode(val label: String) {
    ANY("Any RSI"),
    OVERSOLD_30("Oversold (< 30)"),
    EXTREME_OVERSOLD_20("Extreme (< 20)"),
    BULLISH_ZONE_50_70("Momentum (50–70)"),
    OVERBOUGHT_70("Overbought (> 70)"),
    CUSTOM("Custom Range")
}

enum class MaType(val label: String) {
    EMA("EMA"),
    SMA("SMA")
}

enum class MaCondition(val label: String) {
    ANY("Any MA / EOD"),
    PRICE_ABOVE_MA("Price > MA"),
    PRICE_BELOW_MA("Price < MA"),
    GOLDEN_CROSS_50_200("Golden Cross (50 > 200)"),
    DEATH_CROSS_50_200("Death Cross (50 < 200)"),
    SHORT_UPTREND_20_50("EMA 20 > EMA 50"),
    NEAR_MA_SUPPORT("Near MA (±2%)"),
    RSI_ABOVE_EMA45("RSI(14) > RSI EMA(45)"),
    HM_EMA3_ABOVE_WMA21("HM EMA3 > WMA21"),
    ABOVE_BB_UPPER("Price > BB Upper"),
    BELOW_BB_LOWER("Price < BB Lower")
}

enum class SortOption(val label: String) {
    RSI_ASC("RSI: Low → High"),
    RSI_DESC("RSI: High → Low"),
    RS_RATING_DESC("RS Rating: High → Low"),
    VOL_RATIO_DESC("Vol Ratio: High → Low"),
    CHANGE_DESC("Top Gainers %"),
    CHANGE_ASC("Top Losers %"),
    PRICE_DESC("Price: High → Low"),
    SYMBOL_ASC("Symbol A → Z")
}

data class ScreenerFilterState(
    val query: String = "",
    val universe: NseIndexUniverse = NseIndexUniverse.NIFTY_50,
    val timeframe: Timeframe = Timeframe.D1,
    val rsiPeriod: Int = 14,
    val rsiFilterMode: RsiFilterMode = RsiFilterMode.ANY,
    val rsiMin: Float = 0f,
    val rsiMax: Float = 100f,
    val maType: MaType = MaType.EMA,
    val maPeriod: Int = 50,
    val maCondition: MaCondition = MaCondition.ANY,
    val minVolRatio: Float = 0f,
    val minRsRating: Float = 0f,
    val maxDistFrom52wPct: Float = 100f,
    val sortOption: SortOption = SortOption.RSI_ASC
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (rsiFilterMode != RsiFilterMode.ANY) count++
            if (maCondition != MaCondition.ANY) count++
            if (minVolRatio > 0f) count++
            if (minRsRating > 0f) count++
            if (maxDistFrom52wPct < 100f) count++
            return count
        }
}

data class SavedPreset(
    val id: Int,
    val name: String,
    val timeframe: Timeframe,
    val rsiPeriod: Int,
    val rsiFilterMode: RsiFilterMode,
    val rsiMin: Float,
    val rsiMax: Float,
    val maType: MaType,
    val maPeriod: Int,
    val maCondition: MaCondition
)

data class NamedWatchlist(
    val id: Long,
    val name: String,
    val symbols: Set<String>,
    val createdAt: Long
)

enum class AlertCondition(val label: String, val symbolText: String) {
    ABOVE("Price Above", "≥"),
    BELOW("Price Below", "≤")
}

data class PriceAlert(
    val id: Long,
    val symbol: String,
    val companyName: String,
    val targetPrice: Double,
    val condition: AlertCondition,
    val isActive: Boolean,
    val lastCheckedPrice: Double?,
    val triggeredPrice: Double?,
    val triggeredAt: Long?,
    val createdAt: Long
)
