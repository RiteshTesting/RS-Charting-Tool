package com.example.ui.components

import kotlin.math.min
import kotlin.math.roundToInt

data class ChartViewport(
    val totalCount: Int,
    val visibleCount: Int,
    val endIndex: Int
) {
    val safeVisibleCount: Int
        get() {
            if (totalCount <= 0) return 0
            val minCount = min(MIN_VISIBLE_CANDLES, totalCount)
            return visibleCount.coerceIn(minCount, totalCount)
        }

    val safeEndIndex: Int
        get() {
            if (totalCount <= 0) return 0
            return endIndex.coerceIn(safeVisibleCount - 1, totalCount - 1)
        }

    val startIndex: Int
        get() {
            if (totalCount <= 0) return 0
            return (safeEndIndex - safeVisibleCount + 1).coerceAtLeast(0)
        }

    val isAtLiveEdge: Boolean
        get() = totalCount <= 0 || safeEndIndex >= totalCount - 1

    val canZoomIn: Boolean
        get() = totalCount > MIN_VISIBLE_CANDLES && safeVisibleCount > min(MIN_VISIBLE_CANDLES, totalCount)

    val canZoomOut: Boolean
        get() = safeVisibleCount < totalCount

    /**
     * Shifts the visible window by [deltaCandles].
     * Negative values pan into past/older candles; positive values pan toward latest candles.
     */
    fun panByCandles(deltaCandles: Int): ChartViewport {
        if (totalCount <= 0 || deltaCandles == 0) return this
        val vis = safeVisibleCount
        val nextEnd = (safeEndIndex + deltaCandles).coerceIn(vis - 1, totalCount - 1)
        return ChartViewport(totalCount = totalCount, visibleCount = vis, endIndex = nextEnd)
    }

    /**
     * Zooms in (> 1f) or out (< 1f) around [anchorFraction] (0 = left edge, 1 = right edge).
     */
    fun zoomByFactor(zoomFactor: Float, anchorFraction: Float = 0.5f): ChartViewport {
        if (totalCount <= 0) return this
        val oldCount = safeVisibleCount
        val minCount = min(MIN_VISIBLE_CANDLES, totalCount)
        val clampedZoom = zoomFactor.coerceIn(0.25f, 4f)
        var newCount = (oldCount / clampedZoom).roundToInt().coerceIn(minCount, totalCount)
        // Ensure explicit zoom buttons always move at least 1 candle when possible
        if (newCount == oldCount && clampedZoom > 1.05f && oldCount > minCount) {
            newCount = (oldCount - 1).coerceAtLeast(minCount)
        } else if (newCount == oldCount && clampedZoom < 0.95f && oldCount < totalCount) {
            newCount = (oldCount + 1).coerceAtMost(totalCount)
        }
        if (newCount == oldCount) return this

        val oldStart = startIndex
        val clampedAnchor = anchorFraction.coerceIn(0f, 1f)
        val anchorCandle = oldStart + clampedAnchor * (oldCount - 1).coerceAtLeast(1)
        val newStart = (anchorCandle - clampedAnchor * (newCount - 1).coerceAtLeast(1)).roundToInt()
        val newEnd = (newStart + newCount - 1).coerceIn(newCount - 1, totalCount - 1)
        return ChartViewport(totalCount = totalCount, visibleCount = newCount, endIndex = newEnd)
    }

    fun syncTotalCount(newTotalCount: Int): ChartViewport {
        if (newTotalCount <= 0) return ChartViewport(0, 0, 0)
        if (totalCount == 0) return defaultFor(newTotalCount)
        val wasAtEnd = isAtLiveEdge
        val vis = safeVisibleCount.coerceIn(min(MIN_VISIBLE_CANDLES, newTotalCount), newTotalCount)
        val nextEnd = if (wasAtEnd) {
            newTotalCount - 1
        } else {
            safeEndIndex.coerceIn(vis - 1, newTotalCount - 1)
        }
        return ChartViewport(totalCount = newTotalCount, visibleCount = vis, endIndex = nextEnd)
    }

    fun resetToLatest(defaultVisible: Int = DEFAULT_VISIBLE_CANDLES): ChartViewport {
        if (totalCount <= 0) return ChartViewport(0, 0, 0)
        val vis = min(defaultVisible, totalCount)
        return ChartViewport(totalCount = totalCount, visibleCount = vis, endIndex = totalCount - 1)
    }

    companion object {
        const val MIN_VISIBLE_CANDLES = 15
        const val DEFAULT_VISIBLE_CANDLES = 75

        fun defaultFor(totalCount: Int): ChartViewport {
            if (totalCount <= 0) return ChartViewport(0, 0, 0)
            val vis = min(DEFAULT_VISIBLE_CANDLES, totalCount)
            return ChartViewport(
                totalCount = totalCount,
                visibleCount = vis,
                endIndex = totalCount - 1
            )
        }
    }
}
