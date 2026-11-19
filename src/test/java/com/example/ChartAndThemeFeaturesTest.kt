package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppThemeMode
import com.example.data.local.ThemePreferences
import com.example.ui.components.ChartViewport
import com.example.ui.viewmodel.ScreenerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChartAndThemeFeaturesTest {

    @Test
    fun `ChartViewport pans into past history and clamps at boundaries`() {
        val initial = ChartViewport.defaultFor(totalCount = 200)
        assertEquals(75, initial.safeVisibleCount)
        assertEquals(125, initial.startIndex)
        assertEquals(199, initial.safeEndIndex)
        assertTrue(initial.isAtLiveEdge)

        // Drag backward into past candles
        val pannedPast = initial.panByCandles(-30)
        assertEquals(95, pannedPast.startIndex)
        assertEquals(169, pannedPast.safeEndIndex)
        assertFalse(pannedPast.isAtLiveEdge)

        // Clamps at earliest historical candle (0..74)
        val pannedFarPast = pannedPast.panByCandles(-500)
        assertEquals(0, pannedFarPast.startIndex)
        assertEquals(74, pannedFarPast.safeEndIndex)
        assertFalse(pannedFarPast.isAtLiveEdge)

        // Reset back to live edge
        val reset = pannedFarPast.resetToLatest()
        assertEquals(125, reset.startIndex)
        assertEquals(199, reset.safeEndIndex)
        assertTrue(reset.isAtLiveEdge)
    }

    @Test
    fun `ChartViewport zooms in and out within min and max candle bounds`() {
        val initial = ChartViewport.defaultFor(totalCount = 150)

        val zoomedIn = initial.zoomByFactor(1.5f)
        assertTrue(zoomedIn.safeVisibleCount < initial.safeVisibleCount)
        assertTrue(zoomedIn.canZoomOut)

        // Repeated zoom-in clamps at MIN_VISIBLE_CANDLES
        var deeplyZoomedIn = zoomedIn
        repeat(10) { deeplyZoomedIn = deeplyZoomedIn.zoomByFactor(1.5f) }
        assertEquals(ChartViewport.MIN_VISIBLE_CANDLES, deeplyZoomedIn.safeVisibleCount)
        assertFalse(deeplyZoomedIn.canZoomIn)

        // Repeated zoom-out clamps at totalCount
        var zoomedOut = deeplyZoomedIn
        repeat(15) { zoomedOut = zoomedOut.zoomByFactor(0.7f) }
        assertEquals(150, zoomedOut.safeVisibleCount)
        assertEquals(0, zoomedOut.startIndex)
        assertEquals(149, zoomedOut.safeEndIndex)
        assertFalse(zoomedOut.canZoomOut)
    }

    @Test
    fun `stepSymbolInQueue cycles Up and Down across stock list`() {
        val queue = listOf("RELIANCE", "TCS", "HDFCBANK", "INFY")

        assertEquals("TCS", ScreenerViewModel.stepSymbolInQueue(queue, "RELIANCE", +1))
        assertEquals("RELIANCE", ScreenerViewModel.stepSymbolInQueue(queue, "TCS", -1))

        // Wrap around at boundaries
        assertEquals("INFY", ScreenerViewModel.stepSymbolInQueue(queue, "RELIANCE", -1))
        assertEquals("RELIANCE", ScreenerViewModel.stepSymbolInQueue(queue, "INFY", +1))
    }

    @Test
    fun `ThemePreferences persists Day and Night theme selection`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ThemePreferences(context)

        assertEquals(AppThemeMode.SYSTEM, prefs.themeMode.first())

        prefs.setThemeMode(AppThemeMode.DAY)
        assertEquals(AppThemeMode.DAY, prefs.themeMode.first())

        prefs.setThemeMode(AppThemeMode.NIGHT)
        assertEquals(AppThemeMode.NIGHT, prefs.themeMode.first())
    }
}
