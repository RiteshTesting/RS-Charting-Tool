package com.example

import com.example.data.remote.EodScreenerDataClient
import com.example.domain.StockScannerCatalog
import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.DataSource
import com.example.model.MaCondition
import com.example.model.ScreenerFilterState
import com.example.model.SortOption
import com.example.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EodScreenerDataTest {

    @Test
    fun appendCacheBustQuery_handlesPlainAndQueryUrls() {
        val ts = 1712345678901L
        assertEquals(
            "http://10.0.2.2:5173/screener_data.json?t=$ts",
            EodScreenerDataClient.appendCacheBustQuery("http://10.0.2.2:5173/screener_data.json", ts)
        )
        assertEquals(
            "https://example.com/screener_data.json?env=prod&t=$ts",
            EodScreenerDataClient.appendCacheBustQuery("https://example.com/screener_data.json?env=prod", ts)
        )
    }

    @Test
    fun parseScreenerJson_mapsAllEodFieldsCandlesAndResampledTimeframes() {
        val candlesJson = buildString {
            append("[")
            var price = 2500.0
            for (day in 0 until 65) {
                val month = 1 + (day / 28)
                val dom = 1 + (day % 28)
                val dateStr = String.format("2026-%02d-%02d", month, dom)
                val open = price
                val close = price + 6.5
                val high = close + 4.0
                val low = open - 3.0
                val vol = 1_500_000L + (day * 25_000L)
                if (day > 0) append(",")
                append(
                    """{"time":"$dateStr","open":$open,"high":$high,"low":$low,"close":$close,"volume":$vol}"""
                )
                price = close
            }
            append("]")
        }

        val json = """
            {
              "lastUpdated": "2026-03-09 15:30 IST",
              "stocks": [
                {
                  "symbol": "RELIANCE",
                  "companyName": "Reliance Industries Ltd",
                  "sector": "Energy",
                  "price": 2950.5,
                  "changePct": 2.45,
                  "volume": 8450000,
                  "volRatio": 2.35,
                  "high52w": 3020.0,
                  "distFrom52wPct": -2.3,
                  "rsi14": 64.2,
                  "rsi14Ema45": 56.8,
                  "rsi9": 68.4,
                  "hmEma3": 66.1,
                  "hmWma21": 58.9,
                  "sma20": 2840.0,
                  "sma50": 2760.0,
                  "sma200": 2610.0,
                  "ema9": 2910.0,
                  "ema20": 2860.0,
                  "ema45": 2795.0,
                  "bbUpper": 2940.0,
                  "bbLower": 2740.0,
                  "rsRating": 88.5,
                  "candles": $candlesJson
                },
                {
                  "symbol": "INFY",
                  "price": 1480.0,
                  "changePct": -1.2,
                  "volume": 3200000,
                  "volRatio": 0.9,
                  "high52w": 1950.0,
                  "distFrom52wPct": -24.1,
                  "rsi14": 34.0,
                  "rsi14Ema45": 48.0,
                  "rsi9": 29.0,
                  "hmEma3": 36.0,
                  "hmWma21": 45.0,
                  "sma20": 1560.0,
                  "sma50": 1620.0,
                  "sma200": 1680.0,
                  "ema9": 1510.0,
                  "ema20": 1555.0,
                  "ema45": 1605.0,
                  "bbUpper": 1640.0,
                  "bbLower": 1495.0,
                  "rsRating": 32.0,
                  "candles": [
                    {"time": 1741305600, "open": 1500.0, "high": 1512.0, "low": 1475.0, "close": 1480.0, "volume": 3200000}
                  ]
                }
              ]
            }
        """.trimIndent()

        val payload = EodScreenerDataClient.parseScreenerJson(json, originLabel = "EOD Test")
        assertNotNull(payload)
        assertEquals("2026-03-09 15:30 IST", payload!!.lastUpdated)
        assertEquals(2, payload.stocks.size)

        val rel = payload.stocks[0]
        assertEquals("RELIANCE", rel.symbol)
        assertEquals(2950.5, rel.lastPrice, 0.001)
        assertEquals(2.45, rel.changePercent, 0.001)
        assertEquals(8450000L, rel.volume)
        assertEquals(2.35, rel.volRatio!!, 0.001)
        assertEquals(3020.0, rel.yearHigh, 0.001)
        assertEquals(-2.3, rel.distFrom52wPct!!, 0.001)
        assertEquals(88.5, rel.rsRating!!, 0.001)

        val d1 = rel.indicators[Timeframe.D1]
        assertNotNull(d1)
        assertEquals(DataSource.EOD_JSON, d1!!.source)
        assertEquals(64.2, d1.rsi14!!, 0.001)
        assertEquals(56.8, d1.rsi14Ema45!!, 0.001)
        assertEquals(68.4, d1.rsi9!!, 0.001)
        assertEquals(66.1, d1.hmEma3!!, 0.001)
        assertEquals(58.9, d1.hmWma21!!, 0.001)
        assertEquals(2840.0, d1.sma20!!, 0.001)
        assertEquals(2760.0, d1.sma50!!, 0.001)
        assertEquals(2610.0, d1.sma200!!, 0.001)
        assertEquals(2910.0, d1.ema9!!, 0.001)
        assertEquals(2860.0, d1.ema20!!, 0.001)
        assertEquals(2795.0, d1.ema45!!, 0.001)
        assertEquals(2940.0, d1.bbUpper!!, 0.001)
        assertEquals(2740.0, d1.bbLower!!, 0.001)
        assertEquals(65, d1.candles.size)
        assertEquals(65, d1.ema9History.size)
        assertEquals(65, d1.bbUpperHistory.size)
        assertEquals(65, d1.rsi14Ema45History.size)

        // Weekly resampled timeframe should also be populated from daily EOD candles
        assertNotNull(rel.indicators[Timeframe.W1])

        val infy = payload.stocks[1]
        assertEquals(1, infy.indicators[Timeframe.D1]?.candles?.size)
        assertEquals(1741305600_000L, infy.indicators[Timeframe.D1]?.candles?.first()?.timestampMillis)

        // Verify EOD Screener filters
        val bullishFilter = ScreenerFilterState(
            timeframe = Timeframe.D1,
            maCondition = MaCondition.RSI_ABOVE_EMA45,
            minVolRatio = 1.5f,
            minRsRating = 80f,
            maxDistFrom52wPct = 5f
        )
        assertTrue(TechnicalIndicatorCalculator.matchesFilter(rel, bullishFilter))
        assertFalse(TechnicalIndicatorCalculator.matchesFilter(infy, bullishFilter))

        val hmFilter = ScreenerFilterState(
            timeframe = Timeframe.D1,
            maCondition = MaCondition.HM_EMA3_ABOVE_WMA21
        )
        assertTrue(TechnicalIndicatorCalculator.matchesFilter(rel, hmFilter))
        assertFalse(TechnicalIndicatorCalculator.matchesFilter(infy, hmFilter))

        val bbUpperFilter = ScreenerFilterState(
            timeframe = Timeframe.D1,
            maCondition = MaCondition.ABOVE_BB_UPPER
        )
        assertTrue(TechnicalIndicatorCalculator.matchesFilter(rel, bbUpperFilter))
        assertFalse(TechnicalIndicatorCalculator.matchesFilter(infy, bbUpperFilter))

        val bbLowerFilter = ScreenerFilterState(
            timeframe = Timeframe.D1,
            maCondition = MaCondition.BELOW_BB_LOWER
        )
        assertFalse(TechnicalIndicatorCalculator.matchesFilter(rel, bbLowerFilter))
        assertTrue(TechnicalIndicatorCalculator.matchesFilter(infy, bbLowerFilter))

        // Verify EOD Sort options
        val byRs = TechnicalIndicatorCalculator.sortStocks(
            listOf(infy, rel),
            ScreenerFilterState(sortOption = SortOption.RS_RATING_DESC)
        )
        assertEquals("RELIANCE", byRs.first().symbol)

        val byVol = TechnicalIndicatorCalculator.sortStocks(
            listOf(infy, rel),
            ScreenerFilterState(sortOption = SortOption.VOL_RATIO_DESC)
        )
        assertEquals("RELIANCE", byVol.first().symbol)

        // Verify EOD StockScannerCatalog entries
        val rsScanner = StockScannerCatalog.findById("rs_rating_80_plus")!!
        val rsiEma45Scanner = StockScannerCatalog.findById("rsi_above_ema45")!!
        val bbBreakoutScanner = StockScannerCatalog.findById("bb_upper_breakout")!!
        val volScanner = StockScannerCatalog.findById("high_volume_surge")!!
        assertTrue(rsScanner.predicate(rel, Timeframe.D1))
        assertFalse(rsScanner.predicate(infy, Timeframe.D1))
        assertTrue(rsiEma45Scanner.predicate(rel, Timeframe.D1))
        assertTrue(bbBreakoutScanner.predicate(rel, Timeframe.D1))
        assertTrue(volScanner.predicate(rel, Timeframe.D1))
    }
}
