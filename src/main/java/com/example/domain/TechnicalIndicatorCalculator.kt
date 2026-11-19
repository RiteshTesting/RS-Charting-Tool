package com.example.domain

import com.example.model.Candle
import com.example.model.DataSource
import com.example.model.IndicatorSnapshot
import com.example.model.MaCondition
import com.example.model.MaType
import com.example.model.NseStockItem
import com.example.model.RsiFilterMode
import com.example.model.ScreenerFilterState
import com.example.model.SortOption
import com.example.model.TechnicalSignal
import com.example.model.Timeframe
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.sqrt

object TechnicalIndicatorCalculator {

    private val istZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun calculateSma(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.isEmpty()) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size < period) return result

        var rollingSum = 0.0
        for (i in closes.indices) {
            rollingSum += closes[i]
            if (i >= period) {
                rollingSum -= closes[i - period]
            }
            if (i >= period - 1) {
                result[i] = rollingSum / period
            }
        }
        return result
    }

    fun calculateEma(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.isEmpty()) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size < period) return result

        var initialSum = 0.0
        for (i in 0 until period) {
            initialSum += closes[i]
        }
        var prevEma = initialSum / period
        result[period - 1] = prevEma

        val multiplier = 2.0 / (period + 1.0)
        for (i in period until closes.size) {
            val currentEma = (closes[i] - prevEma) * multiplier + prevEma
            result[i] = currentEma
            prevEma = currentEma
        }
        return result
    }

    fun calculateWma(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.isEmpty()) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size < period) return result

        val weightSum = (period * (period + 1)) / 2.0
        for (i in (period - 1) until closes.size) {
            var weighted = 0.0
            for (k in 0 until period) {
                weighted += closes[i - period + 1 + k] * (k + 1)
            }
            result[i] = weighted / weightSum
        }
        return result
    }

    fun calculateEmaOnNullable(values: List<Double?>, period: Int): List<Double?> {
        if (period <= 0 || values.isEmpty()) return List(values.size) { null }
        val result = MutableList<Double?>(values.size) { null }
        val nonNullIndices = mutableListOf<Int>()
        val nonNullVals = mutableListOf<Double>()
        values.forEachIndexed { idx, v ->
            if (v != null && !v.isNaN()) {
                nonNullIndices.add(idx)
                nonNullVals.add(v)
            }
        }
        if (nonNullVals.size < period) return result
        val emaSeries = calculateEma(nonNullVals, period)
        emaSeries.forEachIndexed { i, emaVal ->
            if (emaVal != null) {
                result[nonNullIndices[i]] = emaVal
            }
        }
        return result
    }

    fun calculateBollingerBands(
        closes: List<Double>,
        period: Int = 20,
        stdDevMultiplier: Double = 2.0
    ): Pair<List<Double?>, List<Double?>> {
        val upper = MutableList<Double?>(closes.size) { null }
        val lower = MutableList<Double?>(closes.size) { null }
        if (period <= 1 || closes.size < period) return upper to lower

        for (i in (period - 1) until closes.size) {
            var sum = 0.0
            for (j in (i - period + 1)..i) sum += closes[j]
            val mean = sum / period
            var varianceSum = 0.0
            for (j in (i - period + 1)..i) {
                val diff = closes[j] - mean
                varianceSum += diff * diff
            }
            val stdDev = sqrt(varianceSum / period)
            upper[i] = mean + stdDevMultiplier * stdDev
            lower[i] = mean - stdDevMultiplier * stdDev
        }
        return upper to lower
    }

    fun calculateWilderRsi(closes: List<Double>, period: Int = 14): List<Double?> {
        if (period <= 0 || closes.size <= period) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) gainSum += diff else lossSum += abs(diff)
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        result[period] = computeRsiValue(avgGain, avgLoss)

        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) abs(diff) else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            result[i] = computeRsiValue(avgGain, avgLoss)
        }
        return result
    }

    private fun computeRsiValue(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0 && avgGain == 0.0) return 50.0
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun buildSnapshot(
        timeframe: Timeframe,
        candles: List<Candle>,
        livePrice: Double?,
        source: DataSource
    ): IndicatorSnapshot {
        val sorted = candles.sortedBy { it.timestampMillis }
        val closes = sorted.map { it.close }.toMutableList()
        if (livePrice != null && livePrice > 0.0 && closes.isNotEmpty()) {
            closes[closes.lastIndex] = livePrice
        }

        val rsi9List = calculateWilderRsi(closes, 9)
        val rsi14List = calculateWilderRsi(closes, 14)
        val rsi21List = calculateWilderRsi(closes, 21)
        val rsi14Ema45List = calculateEmaOnNullable(rsi14List, 45)

        val hmEma3List = calculateEma(closes, 3)
        val hmWma21List = calculateWma(closes, 21)

        val sma20List = calculateSma(closes, 20)
        val sma50List = calculateSma(closes, 50)
        val sma100List = calculateSma(closes, 100)
        val sma200List = calculateSma(closes, 200)

        val ema9List = calculateEma(closes, 9)
        val ema20List = calculateEma(closes, 20)
        val ema45List = calculateEma(closes, 45)
        val ema50List = calculateEma(closes, 50)
        val ema100List = calculateEma(closes, 100)
        val ema200List = calculateEma(closes, 200)

        val (bbUpperList, bbLowerList) = calculateBollingerBands(closes, 20, 2.0)

        val currentPrice = closes.lastOrNull() ?: livePrice ?: 0.0
        val prevClose = if (closes.size >= 2) closes[closes.size - 2] else null
        val latestRsi14 = rsi14List.lastOrNull()
        val prevRsi14 = if (rsi14List.size >= 2) rsi14List[rsi14List.size - 2] else null
        val latestEma20 = ema20List.lastOrNull()
        val prevEma20 = if (ema20List.size >= 2) ema20List[ema20List.size - 2] else null
        val latestSma50 = sma50List.lastOrNull()
        val prevSma50 = if (sma50List.size >= 2) sma50List[sma50List.size - 2] else null
        val latestSma200 = sma200List.lastOrNull()
        val prevSma200 = if (sma200List.size >= 2) sma200List[sma200List.size - 2] else null

        val nonZeroVolumes = sorted.takeLast(21).dropLast(1).map { it.volume }.filter { it > 0L }
        val avgVolume20 = if (nonZeroVolumes.isNotEmpty()) nonZeroVolumes.average() else null
        val latestVolume = sorted.lastOrNull()?.volume ?: 0L
        val computedVolRatio = if (avgVolume20 != null && avgVolume20 > 0.0 && latestVolume > 0L) {
            latestVolume / avgVolume20
        } else {
            null
        }

        val signal = evaluateSignal(
            price = currentPrice,
            rsi14 = latestRsi14,
            ema20 = latestEma20,
            sma50 = latestSma50,
            sma200 = latestSma200
        )

        return IndicatorSnapshot(
            timeframe = timeframe,
            rsi9 = rsi9List.lastOrNull(),
            rsi14 = latestRsi14,
            rsi21 = rsi21List.lastOrNull(),
            rsi14Ema45 = rsi14Ema45List.lastOrNull(),
            prevRsi14 = prevRsi14,
            hmEma3 = hmEma3List.lastOrNull(),
            hmWma21 = hmWma21List.lastOrNull(),
            sma20 = sma20List.lastOrNull(),
            sma50 = latestSma50,
            sma100 = sma100List.lastOrNull(),
            sma200 = latestSma200,
            ema9 = ema9List.lastOrNull(),
            ema20 = latestEma20,
            ema45 = ema45List.lastOrNull(),
            ema50 = ema50List.lastOrNull(),
            ema100 = ema100List.lastOrNull(),
            ema200 = ema200List.lastOrNull(),
            bbUpper = bbUpperList.lastOrNull(),
            bbLower = bbLowerList.lastOrNull(),
            volRatio = computedVolRatio,
            prevClose = prevClose,
            prevEma20 = prevEma20,
            prevSma50 = prevSma50,
            prevSma200 = prevSma200,
            avgVolume20 = avgVolume20,
            rsiHistory = rsi14List,
            rsi14Ema45History = rsi14Ema45List,
            ema9History = ema9List,
            ema20History = ema20List,
            ema45History = ema45List,
            sma50History = sma50List,
            sma200History = sma200List,
            bbUpperHistory = bbUpperList,
            bbLowerHistory = bbLowerList,
            candles = sorted,
            signal = signal,
            source = source
        )
    }

    fun evaluateSignal(
        price: Double,
        rsi14: Double?,
        ema20: Double?,
        sma50: Double?,
        sma200: Double?
    ): TechnicalSignal {
        if (rsi14 != null) {
            if (rsi14 < 25.0) return TechnicalSignal.OVERSOLD
            if (rsi14 > 75.0) return TechnicalSignal.OVERBOUGHT
        }
        var score = 0
        if (ema20 != null) {
            score += if (price > ema20) 1 else -1
        }
        if (sma50 != null) {
            score += if (price > sma50) 1 else -1
        }
        if (sma200 != null) {
            score += if (price > sma200) 2 else -2
        }
        if (sma50 != null && sma200 != null) {
            score += if (sma50 > sma200) 1 else -1
        }
        if (rsi14 != null) {
            when {
                rsi14 in 52.0..70.0 -> score += 1
                rsi14 < 30.0 -> return TechnicalSignal.OVERSOLD
                rsi14 > 70.0 -> return TechnicalSignal.OVERBOUGHT
                rsi14 in 30.0..45.0 -> score -= 1
            }
        }
        return when {
            score >= 4 -> TechnicalSignal.STRONG_BUY
            score >= 2 -> TechnicalSignal.BULLISH
            score <= -4 -> TechnicalSignal.STRONG_SELL
            score <= -2 -> TechnicalSignal.BEARISH
            else -> TechnicalSignal.NEUTRAL
        }
    }

    fun resampleDailyCandles(dailyCandles: List<Candle>, target: Timeframe): List<Candle> {
        val sorted = dailyCandles.sortedBy { it.timestampMillis }
        if (target == Timeframe.D1 || sorted.isEmpty()) return sorted
        if (target != Timeframe.W1 && target != Timeframe.MN1) return sorted

        val cal = Calendar.getInstance(istZone)
        val grouped = LinkedHashMap<String, MutableList<Candle>>()
        for (candle in sorted) {
            cal.timeInMillis = candle.timestampMillis
            val key = if (target == Timeframe.W1) {
                val weekYear = cal.get(Calendar.YEAR)
                val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
                "$weekYear-W$weekOfYear"
            } else {
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                "$year-M$month"
            }
            grouped.getOrPut(key) { mutableListOf() }.add(candle)
        }

        return grouped.values.map { bucket ->
            Candle(
                timestampMillis = bucket.last().timestampMillis,
                open = bucket.first().open,
                high = bucket.maxOf { it.high },
                low = bucket.minOf { it.low },
                close = bucket.last().close,
                volume = bucket.sumOf { it.volume }
            )
        }
    }

    fun resampleIntradayTicks(ticks: List<Pair<Long, Double>>, intervalMinutes: Int): List<Candle> {
        if (ticks.isEmpty()) return emptyList()
        val intervalMillis = intervalMinutes * 60_000L
        val grouped = LinkedHashMap<Long, MutableList<Pair<Long, Double>>>()
        for (tick in ticks.sortedBy { it.first }) {
            val bucket = (tick.first / intervalMillis) * intervalMillis
            grouped.getOrPut(bucket) { mutableListOf() }.add(tick)
        }
        return grouped.entries.map { (bucketStart, points) ->
            Candle(
                timestampMillis = bucketStart,
                open = points.first().second,
                high = points.maxOf { it.second },
                low = points.minOf { it.second },
                close = points.last().second,
                volume = 0L
            )
        }
    }

    fun matchesFilter(stock: NseStockItem, filter: ScreenerFilterState): Boolean {
        val q = filter.query.trim()
        if (q.isNotEmpty()) {
            val matchesText = stock.symbol.contains(q, ignoreCase = true) ||
                stock.companyName.contains(q, ignoreCase = true) ||
                stock.sector.contains(q, ignoreCase = true)
            if (!matchesText) return false
        }

        val snapshot = stock.indicators[filter.timeframe]
            ?: stock.indicators[Timeframe.D1]

        // EOD Stock-level filters: Volume Ratio, RS Rating, Distance from 52W High
        if (filter.minVolRatio > 0f) {
            val ratio = stock.volRatio ?: snapshot?.volRatio
            if (ratio == null || ratio < filter.minVolRatio) return false
        }
        if (filter.minRsRating > 0f) {
            val rs = stock.rsRating
            if (rs == null || rs < filter.minRsRating) return false
        }
        if (filter.maxDistFrom52wPct < 100f) {
            val dist = stock.distFrom52wPct ?: run {
                if (stock.yearHigh > 0.0) {
                    ((stock.yearHigh - stock.lastPrice) / stock.yearHigh) * 100.0
                } else {
                    null
                }
            }
            if (dist == null || abs(dist) > filter.maxDistFrom52wPct) return false
        }

        val hasIndicatorFilters = filter.rsiFilterMode != RsiFilterMode.ANY ||
            filter.maCondition != MaCondition.ANY

        if (snapshot == null) {
            return !hasIndicatorFilters
        }

        // RSI filter
        val rsi = snapshot.rsiForPeriod(filter.rsiPeriod)
        val rsiMatches = when (filter.rsiFilterMode) {
            RsiFilterMode.ANY -> true
            RsiFilterMode.OVERSOLD_30 -> rsi != null && rsi < 30.0
            RsiFilterMode.EXTREME_OVERSOLD_20 -> rsi != null && rsi < 20.0
            RsiFilterMode.BULLISH_ZONE_50_70 -> rsi != null && rsi in 50.0..70.0
            RsiFilterMode.OVERBOUGHT_70 -> rsi != null && rsi > 70.0
            RsiFilterMode.CUSTOM -> rsi != null && rsi >= filter.rsiMin && rsi <= filter.rsiMax
        }
        if (!rsiMatches) return false

        // Moving Average & EOD Indicator Condition filter
        val price = stock.lastPrice
        val selectedMa = snapshot.maValue(filter.maType, filter.maPeriod)
        val ma50 = snapshot.maValue(filter.maType, 50)
        val ma200 = snapshot.maValue(filter.maType, 200)
        val ema20 = snapshot.ema20
        val ema50 = snapshot.ema50

        val maMatches = when (filter.maCondition) {
            MaCondition.ANY -> true
            MaCondition.PRICE_ABOVE_MA -> selectedMa != null && price > selectedMa
            MaCondition.PRICE_BELOW_MA -> selectedMa != null && price < selectedMa
            MaCondition.GOLDEN_CROSS_50_200 -> ma50 != null && ma200 != null && ma50 > ma200
            MaCondition.DEATH_CROSS_50_200 -> ma50 != null && ma200 != null && ma50 < ma200
            MaCondition.SHORT_UPTREND_20_50 -> ema20 != null && ema50 != null && ema20 > ema50
            MaCondition.NEAR_MA_SUPPORT -> selectedMa != null && selectedMa > 0.0 &&
                abs(price - selectedMa) / selectedMa <= 0.02
            MaCondition.RSI_ABOVE_EMA45 -> {
                val r14 = snapshot.rsi14
                val rEma45 = snapshot.rsi14Ema45
                r14 != null && rEma45 != null && r14 > rEma45
            }
            MaCondition.HM_EMA3_ABOVE_WMA21 -> {
                val e3 = snapshot.hmEma3
                val w21 = snapshot.hmWma21
                e3 != null && w21 != null && e3 > w21
            }
            MaCondition.ABOVE_BB_UPPER -> {
                val upper = snapshot.bbUpper
                upper != null && price > upper
            }
            MaCondition.BELOW_BB_LOWER -> {
                val lower = snapshot.bbLower
                lower != null && price < lower
            }
        }

        return maMatches
    }

    fun sortStocks(stocks: List<NseStockItem>, filter: ScreenerFilterState): List<NseStockItem> {
        return when (filter.sortOption) {
            SortOption.RSI_ASC -> stocks.sortedWith(
                compareBy(nullsLast()) {
                    (it.indicators[filter.timeframe] ?: it.indicators[Timeframe.D1])
                        ?.rsiForPeriod(filter.rsiPeriod)
                }
            )
            SortOption.RSI_DESC -> stocks.sortedWith(
                compareByDescending(nullsLast()) {
                    (it.indicators[filter.timeframe] ?: it.indicators[Timeframe.D1])
                        ?.rsiForPeriod(filter.rsiPeriod)
                }
            )
            SortOption.RS_RATING_DESC -> stocks.sortedWith(
                compareByDescending(nullsLast()) { it.rsRating }
            )
            SortOption.VOL_RATIO_DESC -> stocks.sortedWith(
                compareByDescending(nullsLast()) {
                    it.volRatio ?: (it.indicators[filter.timeframe] ?: it.indicators[Timeframe.D1])?.volRatio
                }
            )
            SortOption.CHANGE_DESC -> stocks.sortedByDescending { it.changePercent }
            SortOption.CHANGE_ASC -> stocks.sortedBy { it.changePercent }
            SortOption.PRICE_DESC -> stocks.sortedByDescending { it.lastPrice }
            SortOption.SYMBOL_ASC -> stocks.sortedBy { it.symbol }
        }
    }
}
