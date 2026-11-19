package com.example.domain

import com.example.model.NseStockItem
import com.example.model.Timeframe
import kotlin.math.abs

enum class ScannerCategory(val label: String) {
    ALL("All Scans"),
    RSI("RSI Momentum"),
    MOVING_AVERAGE("Moving Averages"),
    VOLUME_BREAKOUT("Volume & Price"),
    TREND_52W("Trend & 52W")
}

enum class ScannerSentiment(val label: String) {
    BULLISH("Bullish"),
    BEARISH("Bearish"),
    REVERSAL("Reversal"),
    BREAKOUT("Breakout")
}

data class StockScannerDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ScannerCategory,
    val sentiment: ScannerSentiment,
    val predicate: (NseStockItem, Timeframe) -> Boolean
)

object StockScannerCatalog {

    val scanners: List<StockScannerDefinition> = listOf(
        // --- RSI MOMENTUM SCANNERS ---
        StockScannerDefinition(
            id = "rsi_oversold_30",
            title = "RSI Oversold (< 30)",
            subtitle = "Wilder RSI(14) below 30 indicating potential oversold bounce",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.REVERSAL
        ) { stock, tf ->
            val rsi = stock.indicators[tf]?.rsi14 ?: return@StockScannerDefinition false
            rsi < 30.0
        },
        StockScannerDefinition(
            id = "rsi_crossed_above_30",
            title = "RSI Rebounded Above 30",
            subtitle = "RSI(14) crossed back above 30 from oversold territory",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val curr = snap.rsi14 ?: return@StockScannerDefinition false
            val prev = snap.prevRsi14 ?: return@StockScannerDefinition false
            prev <= 30.0 && curr > 30.0
        },
        StockScannerDefinition(
            id = "rsi_crossed_above_50",
            title = "RSI Crossed Above 50",
            subtitle = "Fresh momentum shift as RSI(14) crosses bullish 50 midline",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val curr = snap.rsi14 ?: return@StockScannerDefinition false
            val prev = snap.prevRsi14 ?: return@StockScannerDefinition false
            prev <= 50.0 && curr > 50.0
        },
        StockScannerDefinition(
            id = "rsi_bullish_zone",
            title = "RSI Bullish Zone (55–70)",
            subtitle = "Sustained upward strength without extreme overbought reading",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val rsi = stock.indicators[tf]?.rsi14 ?: return@StockScannerDefinition false
            rsi in 55.0..70.0
        },
        StockScannerDefinition(
            id = "rsi_overbought_70",
            title = "RSI Overbought (> 70)",
            subtitle = "Extended bullish momentum above 70; watch for profit booking",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.BEARISH
        ) { stock, tf ->
            val rsi = stock.indicators[tf]?.rsi14 ?: return@StockScannerDefinition false
            rsi > 70.0
        },
        StockScannerDefinition(
            id = "rsi_weak_zone",
            title = "RSI Weak Zone (< 40)",
            subtitle = "Stocks losing momentum with RSI(14) trading below 40",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.BEARISH
        ) { stock, tf ->
            val rsi = stock.indicators[tf]?.rsi14 ?: return@StockScannerDefinition false
            rsi < 40.0
        },

        // --- MOVING AVERAGE SCANNERS ---
        StockScannerDefinition(
            id = "cross_above_ema20",
            title = "Crossed Above 20 EMA",
            subtitle = "Price closed above 20 EMA after trading below on prior bar",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BREAKOUT
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val ema = snap.ema20 ?: return@StockScannerDefinition false
            val prevEma = snap.prevEma20 ?: return@StockScannerDefinition false
            val prevClose = snap.prevClose ?: return@StockScannerDefinition false
            prevClose <= prevEma && stock.lastPrice > ema
        },
        StockScannerDefinition(
            id = "cross_above_sma50",
            title = "Crossed Above 50 SMA",
            subtitle = "Medium-term breakout above 50-period Simple Moving Average",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val sma = snap.sma50 ?: return@StockScannerDefinition false
            val prevSma = snap.prevSma50 ?: return@StockScannerDefinition false
            val prevClose = snap.prevClose ?: return@StockScannerDefinition false
            prevClose <= prevSma && stock.lastPrice > sma
        },
        StockScannerDefinition(
            id = "cross_above_sma200",
            title = "Crossed Above 200 SMA",
            subtitle = "Major long-term trend reversal above 200 SMA",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BREAKOUT
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val sma = snap.sma200 ?: return@StockScannerDefinition false
            val prevSma = snap.prevSma200 ?: return@StockScannerDefinition false
            val prevClose = snap.prevClose ?: return@StockScannerDefinition false
            prevClose <= prevSma && stock.lastPrice > sma
        },
        StockScannerDefinition(
            id = "above_all_mas",
            title = "Above All Major MAs (20/50/200)",
            subtitle = "Full bullish alignment above 20 EMA, 50 SMA, and 200 SMA",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val ema20 = snap.ema20 ?: return@StockScannerDefinition false
            val sma50 = snap.sma50 ?: return@StockScannerDefinition false
            val sma200 = snap.sma200 ?: return@StockScannerDefinition false
            stock.lastPrice > ema20 && stock.lastPrice > sma50 && stock.lastPrice > sma200
        },
        StockScannerDefinition(
            id = "below_all_mas",
            title = "Below All Major MAs (20/50/200)",
            subtitle = "Full bearish structure trading beneath 20 EMA, 50 SMA & 200 SMA",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BEARISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val ema20 = snap.ema20 ?: return@StockScannerDefinition false
            val sma50 = snap.sma50 ?: return@StockScannerDefinition false
            val sma200 = snap.sma200 ?: return@StockScannerDefinition false
            stock.lastPrice < ema20 && stock.lastPrice < sma50 && stock.lastPrice < sma200
        },
        StockScannerDefinition(
            id = "golden_cross_50_200",
            title = "Golden Cross (50 > 200 SMA)",
            subtitle = "50-period SMA holding above 200-period SMA",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val sma50 = snap.sma50 ?: return@StockScannerDefinition false
            val sma200 = snap.sma200 ?: return@StockScannerDefinition false
            sma50 > sma200
        },
        StockScannerDefinition(
            id = "death_cross_50_200",
            title = "Death Cross (50 < 200 SMA)",
            subtitle = "50-period SMA trading below 200-period SMA",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BEARISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: return@StockScannerDefinition false
            val sma50 = snap.sma50 ?: return@StockScannerDefinition false
            val sma200 = snap.sma200 ?: return@StockScannerDefinition false
            sma50 < sma200
        },
        StockScannerDefinition(
            id = "near_200_sma_support",
            title = "Near 200 SMA Support (±2%)",
            subtitle = "Price testing key long-term 200 SMA support/resistance zone",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.REVERSAL
        ) { stock, tf ->
            val sma200 = stock.indicators[tf]?.sma200 ?: return@StockScannerDefinition false
            sma200 > 0.0 && abs(stock.lastPrice - sma200) / sma200 <= 0.02
        },

        // --- VOLUME & PRICE BREAKOUT SCANNERS ---
        StockScannerDefinition(
            id = "high_volume_surge",
            title = "High Volume Surge (> 1.8x Avg)",
            subtitle = "Current bar volume exceeds 1.8x of the 20-bar average volume",
            category = ScannerCategory.VOLUME_BREAKOUT,
            sentiment = ScannerSentiment.BREAKOUT
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: stock.indicators[Timeframe.D1]
            val explicitRatio = stock.volRatio ?: snap?.volRatio
            if (explicitRatio != null) {
                explicitRatio >= 1.8
            } else {
                val avgVol = snap?.avgVolume20 ?: return@StockScannerDefinition false
                val latestVol = snap.candles.lastOrNull()?.volume?.takeIf { it > 0L } ?: stock.volume
                avgVol > 0.0 && latestVol >= avgVol * 1.8
            }
        },
        StockScannerDefinition(
            id = "bb_upper_breakout",
            title = "Bollinger Upper Band Breakout",
            subtitle = "Price closed above the 20-period upper Bollinger Band (2σ)",
            category = ScannerCategory.VOLUME_BREAKOUT,
            sentiment = ScannerSentiment.BREAKOUT
        ) { stock, tf ->
            val upper = (stock.indicators[tf] ?: stock.indicators[Timeframe.D1])?.bbUpper
                ?: return@StockScannerDefinition false
            stock.lastPrice > upper
        },
        StockScannerDefinition(
            id = "bb_lower_reversal",
            title = "Below Bollinger Lower Band",
            subtitle = "Price stretched below lower Bollinger Band (mean-reversion watch)",
            category = ScannerCategory.VOLUME_BREAKOUT,
            sentiment = ScannerSentiment.REVERSAL
        ) { stock, tf ->
            val lower = (stock.indicators[tf] ?: stock.indicators[Timeframe.D1])?.bbLower
                ?: return@StockScannerDefinition false
            stock.lastPrice < lower
        },
        StockScannerDefinition(
            id = "strong_day_gainers",
            title = "Strong Movers (> +2%)",
            subtitle = "NSE equities gaining more than 2% in the latest session",
            category = ScannerCategory.VOLUME_BREAKOUT,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, _ ->
            stock.changePercent >= 2.0
        },
        StockScannerDefinition(
            id = "sharp_day_losers",
            title = "Sharp Decliners (< -2%)",
            subtitle = "NSE equities down more than 2% in the latest session",
            category = ScannerCategory.VOLUME_BREAKOUT,
            sentiment = ScannerSentiment.BEARISH
        ) { stock, _ ->
            stock.changePercent <= -2.0
        },

        // --- TREND & 52-WEEK SCANNERS ---
        StockScannerDefinition(
            id = "rsi_above_ema45",
            title = "RSI(14) Above RSI EMA(45)",
            subtitle = "RSI(14) holding above its 45-period EMA trend baseline",
            category = ScannerCategory.RSI,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: stock.indicators[Timeframe.D1] ?: return@StockScannerDefinition false
            val r14 = snap.rsi14 ?: return@StockScannerDefinition false
            val rEma45 = snap.rsi14Ema45 ?: return@StockScannerDefinition false
            r14 > rEma45
        },
        StockScannerDefinition(
            id = "hm_ema3_above_wma21",
            title = "HM EMA(3) > WMA(21) Momentum",
            subtitle = "Fast 3-period EMA trading above 21-period Weighted Moving Average",
            category = ScannerCategory.MOVING_AVERAGE,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val snap = stock.indicators[tf] ?: stock.indicators[Timeframe.D1] ?: return@StockScannerDefinition false
            val e3 = snap.hmEma3 ?: return@StockScannerDefinition false
            val w21 = snap.hmWma21 ?: return@StockScannerDefinition false
            e3 > w21
        },
        StockScannerDefinition(
            id = "rs_rating_80_plus",
            title = "High RS Rating Leaders (≥ 80)",
            subtitle = "Top relative-strength NSE leaders outperforming the broader market",
            category = ScannerCategory.TREND_52W,
            sentiment = ScannerSentiment.BREAKOUT
        ) { stock, _ ->
            val rs = stock.rsRating ?: return@StockScannerDefinition false
            rs >= 80.0
        },
        StockScannerDefinition(
            id = "near_52w_high",
            title = "Near 52-Week High (Within 3%)",
            subtitle = "Market leaders trading within 3% of their 52-week NSE high",
            category = ScannerCategory.TREND_52W,
            sentiment = ScannerSentiment.BREAKOUT
        ) { stock, _ ->
            if (stock.distFrom52wPct != null) {
                abs(stock.distFrom52wPct) <= 3.0
            } else {
                stock.yearHigh > 0.0 && stock.lastPrice >= stock.yearHigh * 0.97
            }
        },
        StockScannerDefinition(
            id = "near_52w_low",
            title = "Near 52-Week Low (Within 5%)",
            subtitle = "Stocks hovering within 5% of their 52-week NSE low",
            category = ScannerCategory.TREND_52W,
            sentiment = ScannerSentiment.REVERSAL
        ) { stock, _ ->
            stock.yearLow > 0.0 && stock.lastPrice <= stock.yearLow * 1.05
        },
        StockScannerDefinition(
            id = "three_bar_uptrend",
            title = "3-Bar Higher Highs & Closes",
            subtitle = "Three consecutive rising closes with higher highs on active timeframe",
            category = ScannerCategory.TREND_52W,
            sentiment = ScannerSentiment.BULLISH
        ) { stock, tf ->
            val candles = stock.indicators[tf]?.candles ?: return@StockScannerDefinition false
            if (candles.size < 4) return@StockScannerDefinition false
            val c1 = candles[candles.size - 3]
            val c2 = candles[candles.size - 2]
            val c3 = candles[candles.size - 1]
            c2.close > c1.close && c3.close > c2.close && c2.high > c1.high && c3.high > c2.high
        }
    )

    fun findById(id: String): StockScannerDefinition? = scanners.firstOrNull { it.id == id }

    fun evaluateScanner(
        scanner: StockScannerDefinition,
        stocks: List<NseStockItem>,
        timeframe: Timeframe
    ): List<NseStockItem> {
        return stocks.filter { scanner.predicate(it, timeframe) }
    }

    fun evaluateCombinedScanners(
        scannerIds: List<String>,
        stocks: List<NseStockItem>,
        timeframe: Timeframe
    ): List<NseStockItem> {
        val defs = scannerIds.mapNotNull { findById(it) }
        if (defs.isEmpty()) return emptyList()
        return stocks.filter { stock -> defs.all { it.predicate(stock, timeframe) } }
    }
}
