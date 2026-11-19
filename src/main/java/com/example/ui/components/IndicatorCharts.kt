package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.example.model.Candle
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.VioletMa
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MiniSparkline(
    candles: List<Candle>,
    ema20History: List<Double?>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) BullishEmerald else BearishCoral
    val maLineColor = MaterialTheme.colorScheme.primary
    val tailCount = min(36, candles.size)

    Canvas(modifier = modifier) {
        if (tailCount < 2) return@Canvas
        val sliceCandles = candles.takeLast(tailCount)
        val sliceEma = ema20History.takeLast(tailCount)

        val allValues = sliceCandles.map { it.close } + sliceEma.filterNotNull()
        val minVal = allValues.minOrNull() ?: return@Canvas
        val maxVal = allValues.maxOrNull() ?: return@Canvas
        val range = max(maxVal - minVal, 0.001)

        val w = size.width
        val h = size.height
        val padY = 4f
        val usableH = (h - padY * 2f).coerceAtLeast(1f)
        val stepX = w / (sliceCandles.size - 1).coerceAtLeast(1)

        fun toY(v: Double): Float {
            return padY + usableH - (((v - minVal) / range).toFloat() * usableH)
        }

        val pricePath = Path()
        val fillPath = Path()
        sliceCandles.forEachIndexed { idx, c ->
            val x = idx * stepX
            val y = toY(c.close)
            if (idx == 0) {
                pricePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                pricePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent)
            )
        )
        drawPath(
            path = pricePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val emaPath = Path()
        var started = false
        sliceEma.forEachIndexed { idx, ema ->
            if (ema != null) {
                val x = idx * stepX
                val y = toY(ema)
                if (!started) {
                    emaPath.moveTo(x, y)
                    started = true
                } else {
                    emaPath.lineTo(x, y)
                }
            }
        }
        if (started) {
            drawPath(
                path = emaPath,
                color = maLineColor.copy(alpha = 0.75f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun RsiSpectrumBar(
    rsi: Double?,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val midBullColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.height(6.dp)) {
        val w = size.width
        val h = size.height
        val radius = h / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        drawRoundRect(
            color = BullishEmerald.copy(alpha = 0.35f),
            topLeft = Offset.Zero,
            size = Size(w * 0.30f, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        drawRoundRect(
            color = BearishCoral.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.70f, 0f),
            size = Size(w * 0.30f, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        if (rsi != null) {
            val clamped = (rsi / 100.0).coerceIn(0.0, 1.0).toFloat()
            val markerColor = when {
                rsi < 30.0 -> BullishEmerald
                rsi > 70.0 -> BearishCoral
                rsi in 50.0..70.0 -> midBullColor
                else -> AmberSignal
            }
            val cx = (clamped * w).coerceIn(h / 2f, w - h / 2f)
            drawCircle(
                color = markerColor,
                radius = h * 0.75f,
                center = Offset(cx, h / 2f)
            )
        }
    }
}

@Composable
fun PriceMaCanvasChart(
    candles: List<Candle>,
    ema9History: List<Double?> = emptyList(),
    ema20History: List<Double?>,
    ema45History: List<Double?> = emptyList(),
    sma50History: List<Double?>,
    sma200History: List<Double?>,
    bbUpperHistory: List<Double?> = emptyList(),
    bbLowerHistory: List<Double?> = emptyList(),
    viewport: ChartViewport,
    onViewportChange: (ChartViewport) -> Unit,
    showCandlesticks: Boolean,
    showEma9: Boolean = false,
    showEma20: Boolean,
    showEma45: Boolean = false,
    showSma50: Boolean,
    showSma200: Boolean,
    showBollingerBands: Boolean = false,
    showVolume: Boolean = true,
    selectedAbsoluteIndex: Int?,
    onSelectAbsoluteIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val markerAccent = MaterialTheme.colorScheme.primary

    val currentViewport by rememberUpdatedState(viewport)
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)
    val currentOnSelectIndex by rememberUpdatedState(onSelectAbsoluteIndex)

    val startIdx = viewport.startIndex
    val endIdx = viewport.safeEndIndex
    val visibleCount = if (candles.isEmpty()) 0 else (endIdx - startIdx + 1).coerceAtLeast(0)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(candles.size) {
                    detectTapGestures { offset ->
                        val vp = currentViewport
                        val count = vp.safeVisibleCount
                        if (count > 1 && size.width > 0) {
                            val localIdx = ((offset.x / size.width) * (count - 1))
                                .roundToInt()
                                .coerceIn(0, count - 1)
                            currentOnSelectIndex(vp.startIndex + localIdx)
                        }
                    }
                }
                .pointerInput(candles.size) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        var isHorizontalPan = false
                        var isPinching = false
                        var accumulatedPanPx = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.isEmpty()) break

                            val vp = currentViewport
                            val visCount = vp.safeVisibleCount.coerceAtLeast(2)
                            val candleStepPx = (size.width.toFloat() / (visCount - 1)).coerceAtLeast(3f)

                            if (pressedChanges.size >= 2) {
                                isPinching = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroidX = event.calculateCentroid(useCurrent = true).x
                                val anchorFraction = if (size.width > 0) {
                                    (centroidX / size.width).coerceIn(0f, 1f)
                                } else {
                                    0.5f
                                }

                                var nextVp = vp
                                if (abs(zoom - 1f) > 0.008f) {
                                    nextVp = nextVp.zoomByFactor(zoom, anchorFraction)
                                }
                                accumulatedPanPx += pan.x
                                if (abs(accumulatedPanPx) >= candleStepPx) {
                                    val candleDelta = -(accumulatedPanPx / candleStepPx).toInt()
                                    accumulatedPanPx %= candleStepPx
                                    if (candleDelta != 0) {
                                        nextVp = nextVp.panByCandles(candleDelta)
                                    }
                                }
                                if (nextVp != vp) {
                                    currentOnViewportChange(nextVp)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (!isPinching) {
                                val change = pressedChanges.first()
                                val delta = change.positionChange()
                                totalDx += delta.x
                                totalDy += delta.y

                                if (!isHorizontalPan) {
                                    if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.15f) {
                                        break
                                    }
                                    if (abs(totalDx) > touchSlop && abs(totalDx) >= abs(totalDy)) {
                                        isHorizontalPan = true
                                    }
                                }

                                if (isHorizontalPan) {
                                    change.consume()
                                    accumulatedPanPx += delta.x
                                    if (abs(accumulatedPanPx) >= candleStepPx) {
                                        val candleDelta = -(accumulatedPanPx / candleStepPx).toInt()
                                        accumulatedPanPx %= candleStepPx
                                        if (candleDelta != 0) {
                                            currentOnViewportChange(vp.panByCandles(candleDelta))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (visibleCount < 2 || endIdx >= candles.size) return@Canvas
            val sliceCandles = candles.subList(startIdx, endIdx + 1)
            val sliceEma9 = if (ema9History.size > endIdx) ema9History.subList(startIdx, endIdx + 1) else emptyList()
            val sliceEma20 = if (ema20History.size > endIdx) ema20History.subList(startIdx, endIdx + 1) else emptyList()
            val sliceEma45 = if (ema45History.size > endIdx) ema45History.subList(startIdx, endIdx + 1) else emptyList()
            val sliceSma50 = if (sma50History.size > endIdx) sma50History.subList(startIdx, endIdx + 1) else emptyList()
            val sliceSma200 = if (sma200History.size > endIdx) sma200History.subList(startIdx, endIdx + 1) else emptyList()
            val sliceBbUpper = if (bbUpperHistory.size > endIdx) bbUpperHistory.subList(startIdx, endIdx + 1) else emptyList()
            val sliceBbLower = if (bbLowerHistory.size > endIdx) bbLowerHistory.subList(startIdx, endIdx + 1) else emptyList()

            val boundsValues = buildList {
                sliceCandles.forEach {
                    add(it.high)
                    add(it.low)
                }
                if (showEma9) sliceEma9.filterNotNull().forEach { add(it) }
                if (showEma20) sliceEma20.filterNotNull().forEach { add(it) }
                if (showEma45) sliceEma45.filterNotNull().forEach { add(it) }
                if (showSma50) sliceSma50.filterNotNull().forEach { add(it) }
                if (showSma200) sliceSma200.filterNotNull().forEach { add(it) }
                if (showBollingerBands) {
                    sliceBbUpper.filterNotNull().forEach { add(it) }
                    sliceBbLower.filterNotNull().forEach { add(it) }
                }
            }

            val rawMin = boundsValues.minOrNull() ?: return@Canvas
            val rawMax = boundsValues.maxOrNull() ?: return@Canvas
            val pad = (rawMax - rawMin) * 0.06
            val minPrice = rawMin - pad
            val maxPrice = rawMax + pad
            val range = max(maxPrice - minPrice, 0.01)

            val w = size.width
            val totalH = size.height
            val hasVolumeData = showVolume && sliceCandles.any { it.volume > 0L }
            val priceH = if (hasVolumeData) totalH * 0.78f else totalH
            val volTop = if (hasVolumeData) priceH + 4.dp.toPx() else totalH
            val volH = (totalH - volTop).coerceAtLeast(0f)
            val stepX = w / (sliceCandles.size - 1).coerceAtLeast(1)

            fun priceToY(price: Double): Float {
                return priceH - (((price - minPrice) / range).toFloat() * priceH)
            }

            // Horizontal dashed gridlines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (g in 1..3) {
                val y = priceH * (g / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            // Volume histogram pane at bottom (TradingView lightweight-charts style)
            if (hasVolumeData && volH > 6f) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, priceH),
                    end = Offset(w, priceH),
                    strokeWidth = 0.8.dp.toPx()
                )
                val maxVol = sliceCandles.maxOf { it.volume }.coerceAtLeast(1L)
                val barW = (stepX * 0.62f).coerceIn(2f, 20f)
                sliceCandles.forEachIndexed { idx, c ->
                    if (c.volume > 0L) {
                        val x = idx * stepX
                        val barH = ((c.volume.toFloat() / maxVol.toFloat()) * volH).coerceAtLeast(2f)
                        val barColor = if (c.close >= c.open) {
                            BullishEmerald.copy(alpha = 0.42f)
                        } else {
                            BearishCoral.copy(alpha = 0.42f)
                        }
                        drawRect(
                            color = barColor,
                            topLeft = Offset(x - barW / 2f, totalH - barH),
                            size = Size(barW, barH)
                        )
                    }
                }
            }

            // Bollinger Bands shaded channel
            if (showBollingerBands && sliceBbUpper.size == sliceCandles.size && sliceBbLower.size == sliceCandles.size) {
                val bandPoints = mutableListOf<Triple<Float, Float, Float>>()
                for (idx in sliceCandles.indices) {
                    val u = sliceBbUpper[idx]
                    val l = sliceBbLower[idx]
                    if (u != null && l != null) {
                        bandPoints.add(Triple(idx * stepX, priceToY(u), priceToY(l)))
                    }
                }
                if (bandPoints.size >= 2) {
                    val fillPath = Path().apply {
                        moveTo(bandPoints.first().first, bandPoints.first().second)
                        for (i in 1 until bandPoints.size) {
                            lineTo(bandPoints[i].first, bandPoints[i].second)
                        }
                        for (i in bandPoints.lastIndex downTo 0) {
                            lineTo(bandPoints[i].first, bandPoints[i].third)
                        }
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        color = IndigoAccent.copy(alpha = 0.10f)
                    )
                }
            }

            if (showCandlesticks) {
                val candleWidth = (stepX * 0.62f).coerceIn(2f, 22f)
                sliceCandles.forEachIndexed { idx, c ->
                    val x = idx * stepX
                    val isBull = c.close >= c.open
                    val color = if (isBull) BullishEmerald else BearishCoral
                    val highY = priceToY(c.high)
                    val lowY = priceToY(c.low)
                    val openY = priceToY(c.open)
                    val closeY = priceToY(c.close)

                    drawLine(
                        color = color,
                        start = Offset(x, highY),
                        end = Offset(x, lowY),
                        strokeWidth = 1.4.dp.toPx()
                    )

                    val bodyTop = min(openY, closeY)
                    val bodyHeight = max(abs(closeY - openY), 2f)
                    drawRect(
                        color = color,
                        topLeft = Offset(x - candleWidth / 2f, bodyTop),
                        size = Size(candleWidth, bodyHeight)
                    )
                }
            } else {
                val lineColor = if (sliceCandles.last().close >= sliceCandles.first().close) {
                    BullishEmerald
                } else {
                    BearishCoral
                }
                val linePath = Path()
                val areaPath = Path()
                sliceCandles.forEachIndexed { idx, c ->
                    val x = idx * stepX
                    val y = priceToY(c.close)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, priceH)
                        areaPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }
                areaPath.lineTo(w, priceH)
                areaPath.close()

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = priceH
                    )
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            fun drawOverlayLine(values: List<Double?>, color: Color, strokeDp: Float = 1.8f, dashed: Boolean = false) {
                val path = Path()
                var started = false
                values.forEachIndexed { idx, v ->
                    if (v != null) {
                        val x = idx * stepX
                        val y = priceToY(v)
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }
                if (started) {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokeDp.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f) else null
                        )
                    )
                }
            }

            if (showBollingerBands) {
                drawOverlayLine(sliceBbUpper, IndigoAccent.copy(alpha = 0.72f), strokeDp = 1.2f, dashed = true)
                drawOverlayLine(sliceBbLower, IndigoAccent.copy(alpha = 0.72f), strokeDp = 1.2f, dashed = true)
            }
            if (showSma200) drawOverlayLine(sliceSma200, VioletMa)
            if (showSma50) drawOverlayLine(sliceSma50, AmberSignal)
            if (showEma45) drawOverlayLine(sliceEma45, BearishCoral.copy(alpha = 0.85f), strokeDp = 1.5f)
            if (showEma20) drawOverlayLine(sliceEma20, ElectricCyan)
            if (showEma9) drawOverlayLine(sliceEma9, BullishEmerald, strokeDp = 1.4f)

            // Crosshair aligned to absolute index across price + volume panes
            if (selectedAbsoluteIndex != null && selectedAbsoluteIndex in startIdx..endIdx) {
                val localIdx = selectedAbsoluteIndex - startIdx
                val cx = localIdx * stepX
                val cy = priceToY(sliceCandles[localIdx].close)
                drawLine(
                    color = crosshairColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, totalH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = markerAccent,
                    radius = 4.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

@Composable
fun RsiOscillatorChart(
    rsiHistory: List<Double?>,
    rsi14Ema45History: List<Double?> = emptyList(),
    viewport: ChartViewport,
    selectedAbsoluteIndex: Int?,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val lineColor = MaterialTheme.colorScheme.primary

    val startIdx = viewport.startIndex
    val endIdx = viewport.safeEndIndex
    val visibleCount = if (rsiHistory.isEmpty()) 0 else (endIdx - startIdx + 1).coerceAtLeast(0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        if (visibleCount < 2 || endIdx >= rsiHistory.size) return@Canvas
        val slice = rsiHistory.subList(startIdx, endIdx + 1)
        val sliceRsiEma = if (rsi14Ema45History.size > endIdx) {
            rsi14Ema45History.subList(startIdx, endIdx + 1)
        } else {
            emptyList()
        }
        val w = size.width
        val h = size.height
        val stepX = w / (slice.size - 1).coerceAtLeast(1)

        fun rsiToY(v: Double): Float {
            return h - ((v.coerceIn(0.0, 100.0) / 100.0).toFloat() * h)
        }

        val y70 = rsiToY(70.0)
        val y50 = rsiToY(50.0)
        val y30 = rsiToY(30.0)

        drawRect(
            color = BearishCoral.copy(alpha = 0.10f),
            topLeft = Offset.Zero,
            size = Size(w, y70)
        )
        drawRect(
            color = BullishEmerald.copy(alpha = 0.10f),
            topLeft = Offset(0f, y30),
            size = Size(w, h - y30)
        )

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        drawLine(BearishCoral.copy(alpha = 0.55f), Offset(0f, y70), Offset(w, y70), 1.dp.toPx(), pathEffect = dashEffect)
        drawLine(gridColor, Offset(0f, y50), Offset(w, y50), 0.8.dp.toPx(), pathEffect = dashEffect)
        drawLine(BullishEmerald.copy(alpha = 0.55f), Offset(0f, y30), Offset(w, y30), 1.dp.toPx(), pathEffect = dashEffect)

        // RSI(14) EMA(45) trend baseline
        if (sliceRsiEma.isNotEmpty()) {
            val emaPath = Path()
            var emaStarted = false
            sliceRsiEma.forEachIndexed { idx, v ->
                if (v != null) {
                    val x = idx * stepX
                    val y = rsiToY(v)
                    if (!emaStarted) {
                        emaPath.moveTo(x, y)
                        emaStarted = true
                    } else {
                        emaPath.lineTo(x, y)
                    }
                }
            }
            if (emaStarted) {
                drawPath(
                    path = emaPath,
                    color = AmberSignal,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        val path = Path()
        var started = false
        slice.forEachIndexed { idx, rsi ->
            if (rsi != null) {
                val x = idx * stepX
                val y = rsiToY(rsi)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        if (selectedAbsoluteIndex != null && selectedAbsoluteIndex in startIdx..endIdx) {
            val localIdx = selectedAbsoluteIndex - startIdx
            val cx = localIdx * stepX
            drawLine(
                color = crosshairColor,
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            slice[localIdx]?.let { selRsi ->
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(cx, rsiToY(selRsi))
                )
            }
        }
    }
}
