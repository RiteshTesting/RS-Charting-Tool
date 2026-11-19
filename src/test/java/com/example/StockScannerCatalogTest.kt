package com.example

import com.example.domain.StockScannerCatalog
import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.Candle
import com.example.model.DataSource
import com.example.model.NseStockItem
import com.example.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockScannerCatalogTest {

    private fun buildTrendStock(
        symbol: String,
        closes: List<Double>,
        volumes: List<Long> = List(closes.size) { 100_000L },
        changePct: Double = 1.5,
        yearHigh: Double = closes.max() * 1.1,
        yearLow: Double = closes.min() * 0.9
    ): NseStockItem {
        val candles = closes.mapIndexed { idx, close ->
            Candle(
                timestampMillis = 1_700_000_000_000L + idx * 86_400_000L,
                open = close - 1.0,
                high = close + 2.0,
                low = close - 2.0,
                close = close,
                volume = volumes[idx]
            )
        }
        val snapshot = TechnicalIndicatorCalculator.buildSnapshot(
            timeframe = Timeframe.D1,
            candles = candles,
            livePrice = closes.last(),
            source = DataSource.NSE_DIRECT
        )
        return NseStockItem(
            symbol = symbol,
            companyName = "$symbol Ltd",
            sector = "NSE",
            lastPrice = closes.last(),
            change = 10.0,
            changePercent = changePct,
            dayHigh = closes.last() + 5.0,
            dayLow = closes.last() - 5.0,
            volume = volumes.last(),
            yearHigh = yearHigh,
            yearLow = yearLow,
            indicators = mapOf(Timeframe.D1 to snapshot)
        )
    }

    @Test
    fun `evaluateCombinedScanners matches stocks passing all selected scanners`() {
        val strongVolumes = List(219) { 100_000L } + listOf(300_000L)
        val risingCloses = (1..220).map { 1000.0 + it * 2.5 }
        val bullishVolumeStock = buildTrendStock(
            symbol = "HAL",
            closes = risingCloses,
            volumes = strongVolumes,
            changePct = 3.2,
            yearHigh = risingCloses.last()
        )

        val fallingCloses = (1..220).map { 2000.0 - it * 3.0 }
        val bearishStock = buildTrendStock(
            symbol = "WEAKCO",
            closes = fallingCloses,
            changePct = -3.1
        )

        val universe = listOf(bullishVolumeStock, bearishStock)

        val combined = StockScannerCatalog.evaluateCombinedScanners(
            scannerIds = listOf("above_all_mas", "high_volume_surge", "near_52w_high"),
            stocks = universe,
            timeframe = Timeframe.D1
        )
        assertEquals(1, combined.size)
        assertEquals("HAL", combined.first().symbol)

        val oversold = StockScannerCatalog.evaluateScanner(
            scanner = StockScannerCatalog.findById("rsi_oversold_30")!!,
            stocks = universe,
            timeframe = Timeframe.D1
        )
        assertTrue(oversold.any { it.symbol == "WEAKCO" })
        assertTrue(oversold.none { it.symbol == "HAL" })
    }
}
