package com.example

import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.Candle
import com.example.model.DataSource
import com.example.model.MaCondition
import com.example.model.MaType
import com.example.model.NseStockItem
import com.example.model.RsiFilterMode
import com.example.model.ScreenerFilterState
import com.example.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorCalculatorTest {

    @Test
    fun `calculateSma computes exact rolling average`() {
        val closes = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        val sma3 = TechnicalIndicatorCalculator.calculateSma(closes, 3)
        assertEquals(null, sma3[0])
        assertEquals(null, sma3[1])
        assertEquals(20.0, sma3[2]!!, 0.001)
        assertEquals(30.0, sma3[3]!!, 0.001)
        assertEquals(40.0, sma3[4]!!, 0.001)
    }

    @Test
    fun `calculateEma weights recent prices higher than sma`() {
        val closes = listOf(100.0, 102.0, 101.0, 105.0, 110.0, 115.0)
        val ema3 = TechnicalIndicatorCalculator.calculateEma(closes, 3)
        val sma3 = TechnicalIndicatorCalculator.calculateSma(closes, 3)
        assertNotNull(ema3.last())
        assertTrue(ema3.last()!! > sma3.last()!!)
    }

    @Test
    fun `calculateWilderRsi detects oversold and overbought trends`() {
        val falling = (100 downTo 70).map { it.toDouble() }
        val rsiFalling = TechnicalIndicatorCalculator.calculateWilderRsi(falling, 14)
        assertTrue(rsiFalling.last()!! < 10.0)

        val rising = (70..100).map { it.toDouble() }
        val rsiRising = TechnicalIndicatorCalculator.calculateWilderRsi(rising, 14)
        assertTrue(rsiRising.last()!! > 90.0)
    }

    @Test
    fun `matchesFilter filters by RSI and Golden Cross correctly`() {
        val candles = (1..220).map { i ->
            val close = 1000.0 + i * 2.0
            Candle(
                timestampMillis = 1_700_000_000_000L + i * 86_400_000L,
                open = close - 1.0,
                high = close + 2.0,
                low = close - 2.0,
                close = close,
                volume = 100_000L
            )
        }
        val snapshot = TechnicalIndicatorCalculator.buildSnapshot(
            timeframe = Timeframe.D1,
            candles = candles,
            livePrice = 1442.0,
            source = DataSource.NSE_DIRECT
        )
        val stock = NseStockItem(
            symbol = "TCS",
            companyName = "Tata Consultancy Services Limited",
            sector = "IT",
            lastPrice = 1442.0,
            change = 12.0,
            changePercent = 0.84,
            dayHigh = 1445.0,
            dayLow = 1420.0,
            volume = 2_500_000L,
            yearHigh = 1500.0,
            yearLow = 1000.0,
            indicators = mapOf(Timeframe.D1 to snapshot)
        )

        val goldenFilter = ScreenerFilterState(
            timeframe = Timeframe.D1,
            maType = MaType.SMA,
            maCondition = MaCondition.GOLDEN_CROSS_50_200
        )
        assertTrue(TechnicalIndicatorCalculator.matchesFilter(stock, goldenFilter))

        val oversoldFilter = ScreenerFilterState(
            timeframe = Timeframe.D1,
            rsiFilterMode = RsiFilterMode.OVERSOLD_30
        )
        assertFalse(TechnicalIndicatorCalculator.matchesFilter(stock, oversoldFilter))
    }
}
