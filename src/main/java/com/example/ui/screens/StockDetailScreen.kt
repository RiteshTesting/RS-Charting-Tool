package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AppThemeMode
import com.example.model.AlertCondition
import com.example.model.IndicatorSnapshot
import com.example.model.Timeframe
import com.example.ui.components.ChartViewport
import com.example.ui.components.IndianFormatters
import com.example.ui.components.PriceMaCanvasChart
import com.example.ui.components.RsiOscillatorChart
import com.example.ui.components.SignalBadge
import com.example.ui.components.ThemeModeToggleChip
import com.example.ui.components.rsiColor
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.MonoPriceLarge
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.VioletMa
import com.example.ui.viewmodel.ScreenerViewModel
import com.example.ui.viewmodel.StockDetailUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StockDetailScreen(
    uiState: StockDetailUiState,
    showBackButton: Boolean,
    stockQueue: List<String> = emptyList(),
    onSwitchSymbol: (String) -> Unit = {},
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isDarkTheme: Boolean = true,
    onToggleDayNight: () -> Unit = {},
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    onBack: () -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleChartStyle: () -> Unit,
    onToggleEma9: () -> Unit = {},
    onToggleEma20: () -> Unit,
    onToggleEma45: () -> Unit = {},
    onToggleSma50: () -> Unit,
    onToggleSma200: () -> Unit,
    onToggleBollingerBands: () -> Unit = {},
    onToggleVolume: () -> Unit = {},
    onToggleStockInWatchlist: (Long) -> Unit,
    onSaveStockToWatchlistByName: (String) -> Unit,
    onCreatePriceAlert: (Double, AlertCondition) -> Unit,
    onTogglePriceAlertActive: (Long, Boolean) -> Unit,
    onDeletePriceAlert: (Long) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stock = uiState.stock
    var showWatchlistSheet by remember { mutableStateOf(false) }
    var showAlertSheet by remember { mutableStateOf(false) }
    var selectedCandleIndex by remember(uiState.selectedTimeframe, uiState.symbol) {
        mutableStateOf<Int?>(null)
    }

    val activeSnapshot = stock?.indicators?.get(uiState.selectedTimeframe)
    val totalCandles = activeSnapshot?.candles?.size ?: 0
    var viewport by remember(uiState.symbol, uiState.selectedTimeframe) {
        mutableStateOf(ChartViewport.defaultFor(totalCandles))
    }
    LaunchedEffect(totalCandles, uiState.symbol, uiState.selectedTimeframe) {
        viewport = if (viewport.totalCount == 0 && totalCandles > 0) {
            ChartViewport.defaultFor(totalCandles)
        } else {
            viewport.syncTotalCount(totalCandles)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.symbol) {
        runCatching { focusRequester.requestFocus() }
    }

    val canCycleStocks = stockQueue.size > 1
    val stepStock: (Int) -> Unit = { delta ->
        ScreenerViewModel.stepSymbolInQueue(stockQueue, uiState.symbol, delta)?.let(onSwitchSymbol)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && canCycleStocks && !showWatchlistSheet && !showAlertSheet) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            stepStock(-1)
                            true
                        }
                        Key.DirectionDown -> {
                            stepStock(1)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = uiState.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stock?.companyName ?: "NSE India Equity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            },
            actions = {
                ThemeModeToggleChip(
                    themeMode = themeMode,
                    isDarkActive = isDarkTheme,
                    onToggleDayNight = onToggleDayNight,
                    onSelectMode = onSelectThemeMode,
                    compact = true
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_refresh_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh stock")
                }
                IconButton(
                    onClick = { if (stock != null) showAlertSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_alert_button")
                ) {
                    val hasActiveAlert = uiState.alertsForStock.any { it.isActive }
                    Icon(
                        imageVector = if (hasActiveAlert) Icons.Rounded.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = "Set price alert",
                        tint = if (hasActiveAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { if (stock != null) showWatchlistSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_watchlist_button")
                ) {
                    val watched = stock?.isWatchlisted == true
                    Icon(
                        imageVector = if (watched) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (watched) "Manage watchlists" else "Save to watchlist",
                        tint = if (watched) AmberSignal else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Up / Down Stock Chart Switcher Bar
        if (canCycleStocks) {
            val currentIdx = stockQueue.indexOf(uiState.symbol).let { if (it < 0) 0 else it }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { stepStock(-1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("prev_stock_chart_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Previous stock chart (Up key)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${uiState.symbol} (${currentIdx + 1}/${stockQueue.size}) • Use ↑/↓ keys to switch",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { stepStock(1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("next_stock_chart_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Next stock chart (Down key)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (uiState.isLoading && stock == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading ${uiState.symbol} multi-timeframe NSE data…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        if (stock == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unable to load ${uiState.symbol} from NSE",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = onRefresh) {
                        Text("Retry")
                    }
                }
            }
            return@Column
        }

        val isUp = stock.changePercent >= 0.0
        val changeColor = if (isUp) BullishEmerald else BearishCoral

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Price Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = IndianFormatters.formatInr(stock.lastPrice),
                                style = MonoPriceLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${IndianFormatters.formatSignedPrice(stock.change)} (${IndianFormatters.formatSignedPercent(stock.changePercent)})",
                                    style = MonoMetricSmall,
                                    color = changeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• ${stock.sector}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        activeSnapshot?.let { SignalBadge(signal = it.signal) }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn("Day High", IndianFormatters.formatInr(stock.dayHigh))
                        MetricColumn("Day Low", IndianFormatters.formatInr(stock.dayLow))
                        MetricColumn("Volume", IndianFormatters.formatVolume(stock.volume))
                        MetricColumn("52W High", IndianFormatters.formatInr(stock.yearHigh))
                    }

                    val volRatio = stock.volRatio ?: activeSnapshot?.volRatio
                    val dist52w = stock.distFrom52wPct ?: if (stock.yearHigh > 0.0) {
                        ((stock.lastPrice - stock.yearHigh) / stock.yearHigh) * 100.0
                    } else null
                    if (stock.rsRating != null || volRatio != null || dist52w != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn(
                                "RS Rating",
                                stock.rsRating?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--"
                            )
                            MetricColumn(
                                "Vol Ratio",
                                volRatio?.let { String.format(java.util.Locale.US, "%.2fx", it) } ?: "--"
                            )
                            MetricColumn(
                                "From 52W High",
                                dist52w?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "--"
                            )
                        }
                    }
                }
            }

            // Timeframe Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val selected = uiState.selectedTimeframe == tf
                    Surface(
                        onClick = { onSelectTimeframe(tf) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("detail_timeframe_${tf.label.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tf.label,
                                style = MonoMetricSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Candlestick / Price + Moving Averages Interactive Chart Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Price & Moving Averages (${uiState.selectedTimeframe.displayName})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Drag chart for past • Pinch or +/- to zoom",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewport = viewport.zoomByFactor(0.74f) },
                                enabled = viewport.canZoomOut,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chart_zoom_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomOut,
                                    contentDescription = "Zoom out chart",
                                    tint = if (viewport.canZoomOut) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            IconButton(
                                onClick = { viewport = viewport.zoomByFactor(1.35f) },
                                enabled = viewport.canZoomIn,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chart_zoom_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomIn,
                                    contentDescription = "Zoom in chart",
                                    tint = if (viewport.canZoomIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            if (!viewport.isAtLiveEdge) {
                                IconButton(
                                    onClick = { viewport = viewport.resetToLatest() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("chart_reset_latest_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FastForward,
                                        contentDescription = "Jump to latest candle",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleChartStyle,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_chart_style_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.showCandlesticks) Icons.Rounded.ShowChart else Icons.Rounded.CandlestickChart,
                                    contentDescription = "Toggle candlestick or line chart",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Overlay Toggle Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OverlayToggleChip(
                            label = "EMA 9",
                            dotColor = BullishEmerald,
                            selected = uiState.showEma9,
                            onClick = onToggleEma9
                        )
                        OverlayToggleChip(
                            label = "EMA 20",
                            dotColor = ElectricCyan,
                            selected = uiState.showEma20,
                            onClick = onToggleEma20
                        )
                        OverlayToggleChip(
                            label = "EMA 45",
                            dotColor = BearishCoral,
                            selected = uiState.showEma45,
                            onClick = onToggleEma45
                        )
                        OverlayToggleChip(
                            label = "SMA 50",
                            dotColor = AmberSignal,
                            selected = uiState.showSma50,
                            onClick = onToggleSma50
                        )
                        OverlayToggleChip(
                            label = "SMA 200",
                            dotColor = VioletMa,
                            selected = uiState.showSma200,
                            onClick = onToggleSma200
                        )
                        OverlayToggleChip(
                            label = "BB (20,2)",
                            dotColor = com.example.ui.theme.IndigoAccent,
                            selected = uiState.showBollingerBands,
                            onClick = onToggleBollingerBands
                        )
                        OverlayToggleChip(
                            label = "Vol",
                            dotColor = BullishEmerald.copy(alpha = 0.7f),
                            selected = uiState.showVolume,
                            onClick = onToggleVolume
                        )
                    }

                    if (activeSnapshot != null && activeSnapshot.candles.size >= 2) {
                        val allCandles = activeSnapshot.candles
                        val startIdx = viewport.startIndex
                        val endIdx = viewport.safeEndIndex.coerceIn(startIdx, allCandles.lastIndex)
                        val inspectedIdx = (selectedCandleIndex ?: endIdx).coerceIn(0, allCandles.lastIndex)
                        val inspectedCandle = allCandles[inspectedIdx]
                        val inspectedRsi = activeSnapshot.rsiHistory.getOrNull(inspectedIdx)
                        val inspectedRsiEma45 = activeSnapshot.rsi14Ema45History.getOrNull(inspectedIdx)
                        val isIntraday = uiState.selectedTimeframe == Timeframe.M15 || uiState.selectedTimeframe == Timeframe.H1

                        // Crosshair inspection bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = IndianFormatters.formatCandleTime(inspectedCandle.timestampMillis, isIntraday),
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "O:${String.format("%.1f", inspectedCandle.open)}  H:${String.format("%.1f", inspectedCandle.high)}  L:${String.format("%.1f", inspectedCandle.low)}  C:${String.format("%.1f", inspectedCandle.close)}",
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        PriceMaCanvasChart(
                            candles = allCandles,
                            ema9History = activeSnapshot.ema9History,
                            ema20History = activeSnapshot.ema20History,
                            ema45History = activeSnapshot.ema45History,
                            sma50History = activeSnapshot.sma50History,
                            sma200History = activeSnapshot.sma200History,
                            bbUpperHistory = activeSnapshot.bbUpperHistory,
                            bbLowerHistory = activeSnapshot.bbLowerHistory,
                            viewport = viewport,
                            onViewportChange = { nextVp -> viewport = nextVp },
                            showCandlesticks = uiState.showCandlesticks,
                            showEma9 = uiState.showEma9,
                            showEma20 = uiState.showEma20,
                            showEma45 = uiState.showEma45,
                            showSma50 = uiState.showSma50,
                            showSma200 = uiState.showSma200,
                            showBollingerBands = uiState.showBollingerBands,
                            showVolume = uiState.showVolume,
                            selectedAbsoluteIndex = selectedCandleIndex,
                            onSelectAbsoluteIndex = { selectedCandleIndex = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(236.dp)
                                .testTag("price_ma_canvas_chart")
                        )

                        // Visible Date Range & Zoom Span Strip
                        val firstVis = allCandles[startIdx]
                        val lastVis = allCandles[endIdx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = IndianFormatters.formatCandleTime(firstVis.timestampMillis, isIntraday),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${endIdx - startIdx + 1}/${allCandles.size} bars" +
                                    if (!viewport.isAtLiveEdge) " (Past)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!viewport.isAtLiveEdge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = IndianFormatters.formatCandleTime(lastVis.timestampMillis, isIntraday),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RSI (14) & EMA(45)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "RSI: ${IndianFormatters.formatRsi(inspectedRsi ?: activeSnapshot.rsi14)}",
                                    style = MonoMetricSmall,
                                    color = rsiColor(inspectedRsi ?: activeSnapshot.rsi14),
                                    fontWeight = FontWeight.Bold
                                )
                                val rsiEmaVal = inspectedRsiEma45 ?: activeSnapshot.rsi14Ema45
                                if (rsiEmaVal != null) {
                                    Text(
                                        text = "EMA45: ${IndianFormatters.formatRsi(rsiEmaVal)}",
                                        style = MonoMetricSmall,
                                        color = AmberSignal,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        RsiOscillatorChart(
                            rsiHistory = activeSnapshot.rsiHistory,
                            rsi14Ema45History = activeSnapshot.rsi14Ema45History,
                            viewport = viewport,
                            selectedAbsoluteIndex = selectedCandleIndex
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading chart candles…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Multi-Timeframe Technical Matrix (All 5 Timeframes at Once)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Multi-Timeframe Technical Matrix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any timeframe row to inspect its chart & levels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TF", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("RSI(14)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("EMA 20", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("SMA 50", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Signal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Timeframe.entries.forEach { tf ->
                        val snap = stock.indicators[tf]
                        val isRowSelected = tf == uiState.selectedTimeframe
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isRowSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                                )
                                .clickable { onSelectTimeframe(tf) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tf.label,
                                style = MonoMetricSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                text = IndianFormatters.formatRsi(snap?.rsi14),
                                style = MonoMetricSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = rsiColor(snap?.rsi14),
                                modifier = Modifier.width(54.dp)
                            )
                            Text(
                                text = snap?.ema20?.let { String.format("%.1f", it) } ?: "--",
                                style = MonoMetricSmall,
                                color = if (snap?.ema20 != null && stock.lastPrice > snap.ema20) BullishEmerald else BearishCoral,
                                modifier = Modifier.width(72.dp)
                            )
                            Text(
                                text = snap?.sma50?.let { String.format("%.1f", it) } ?: "--",
                                style = MonoMetricSmall,
                                color = if (snap?.sma50 != null && stock.lastPrice > snap.sma50) BullishEmerald else BearishCoral,
                                modifier = Modifier.width(72.dp)
                            )
                            if (snap != null) {
                                SignalBadge(signal = snap.signal)
                            } else {
                                Text("--", style = MonoMetricSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Moving Averages & RSI Periods Breakdown
            if (activeSnapshot != null) {
                MovingAveragesLevelsCard(
                    price = stock.lastPrice,
                    snapshot = activeSnapshot
                )
            }

            if (uiState.alertsForStock.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Price Threshold Alerts (${uiState.symbol})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.alertsForStock.forEach { alert ->
                        PriceAlertRowCard(
                            alert = alert,
                            onOpenStock = {},
                            onToggleActive = { active -> onTogglePriceAlertActive(alert.id, active) },
                            onDelete = { onDeletePriceAlert(alert.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showWatchlistSheet && stock != null) {
        com.example.ui.components.WatchlistPickerSheet(
            stock = stock,
            watchlists = uiState.namedWatchlists,
            onToggleInWatchlist = onToggleStockInWatchlist,
            onCreateAndSave = onSaveStockToWatchlistByName,
            onDismiss = { showWatchlistSheet = false }
        )
    }

    if (showAlertSheet && stock != null) {
        com.example.ui.components.PriceAlertSheet(
            stock = stock,
            onCreateAlert = onCreatePriceAlert,
            onDismiss = { showAlertSheet = false }
        )
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoMetricSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OverlayToggleChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = dotColor.copy(alpha = 0.16f)
        )
    )
}

@Composable
private fun MovingAveragesLevelsCard(
    price: Double,
    snapshot: IndicatorSnapshot
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Moving Averages & RSI (${snapshot.timeframe.displayName})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("RSI (9)", IndianFormatters.formatRsi(snapshot.rsi9))
                MetricColumn("RSI (14)", IndianFormatters.formatRsi(snapshot.rsi14))
                MetricColumn("RSI EMA45", IndianFormatters.formatRsi(snapshot.rsi14Ema45))
                MetricColumn("RSI (21)", IndianFormatters.formatRsi(snapshot.rsi21))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            val rows = listOf(
                Triple("EMA 9", snapshot.ema9, "EMA 45" to snapshot.ema45),
                Triple("EMA 20", snapshot.ema20, "SMA 20" to snapshot.sma20),
                Triple("EMA 50", snapshot.ema50, "SMA 50" to snapshot.sma50),
                Triple("EMA 100", snapshot.ema100, "SMA 100" to snapshot.sma100),
                Triple("EMA 200", snapshot.ema200, "SMA 200" to snapshot.sma200),
                Triple("HM EMA 3", snapshot.hmEma3, "HM WMA 21" to snapshot.hmWma21),
                Triple("BB Upper", snapshot.bbUpper, "BB Lower" to snapshot.bbLower)
            )

            rows.forEach { (emaLabel, emaVal, smaPair) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MaLevelCell(label = emaLabel, value = emaVal, price = price, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    MaLevelCell(label = smaPair.first, value = smaPair.second, price = price, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MaLevelCell(
    label: String,
    value: Double?,
    price: Double,
    modifier: Modifier = Modifier
) {
    val isBull = value != null && price > value
    val color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else if (isBull) BullishEmerald else BearishCoral
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value?.let { String.format("%.1f", it) } ?: "--",
            style = MonoPriceMedium,
            color = color
        )
    }
}
