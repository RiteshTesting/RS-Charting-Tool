package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppThemeMode
import com.example.model.DataSource
import com.example.model.MaCondition
import com.example.model.MaType
import com.example.model.NseIndexUniverse
import com.example.model.NseSearchSuggestion
import com.example.model.RsiFilterMode
import com.example.model.ScreenerFilterState
import com.example.model.TechnicalSignal
import com.example.model.Timeframe
import com.example.ui.components.IndianFormatters
import com.example.ui.components.StockScreenerCard
import com.example.ui.components.TechnicalFilterBottomSheet
import com.example.ui.components.ThemeModeToggleChip
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.viewmodel.ScreenerUiState

@Composable
fun ScreenerScreen(
    uiState: ScreenerUiState,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onToggleDayNight: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSelectSuggestion: (NseSearchSuggestion) -> Unit,
    onSelectUniverse: (NseIndexUniverse) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onUpdateFilter: ((ScreenerFilterState) -> ScreenerFilterState) -> Unit,
    onApplyQuickPreset: (RsiFilterMode, MaCondition, MaType, Int) -> Unit,
    onResetFilters: () -> Unit,
    onSavePreset: (String) -> Unit,
    onRefreshEod: () -> Unit,
    onUpdateEodFeedUrl: (String) -> Unit,
    onImportLocalEodJson: (String) -> Unit,
    onRefresh: () -> Unit,
    onStockClick: (String) -> Unit,
    onToggleWatchlist: (com.example.model.NseStockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFilterSheet by remember { mutableStateOf(false) }
    var showEodSourceDialog by remember { mutableStateOf(false) }
    val filter = uiState.filter

    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!content.isNullOrBlank()) {
                onImportLocalEodJson(content)
                showEodSourceDialog = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // Top Branded Header with EOD Timestamp Badge & Refresh EOD Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val isEod = uiState.dataSource == DataSource.EOD_JSON && !uiState.eodLastUpdated.isNullOrBlank()
                    val badgeColor = if (isEod) MaterialTheme.colorScheme.primary else BullishEmerald
                    Surface(
                        onClick = { showEodSourceDialog = true },
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.6.dp, badgeColor.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("eod_last_updated_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Text(
                                text = if (isEod) {
                                    "EOD: ${uiState.eodLastUpdated}"
                                } else {
                                    uiState.dataSource.label
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    text = if (!uiState.eodLastUpdated.isNullOrBlank()) {
                        "EOD Synced ${uiState.eodLastUpdated} • Tap badge for source"
                    } else {
                        "Updated ${IndianFormatters.formatLastUpdated(uiState.lastUpdatedMillis)} • NSE India"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = onRefreshEod,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_eod_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uiState.isRefreshingEod) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.action_refresh_eod),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.action_refresh_eod),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                ThemeModeToggleChip(
                    themeMode = themeMode,
                    isDarkActive = isDarkTheme,
                    onToggleDayNight = onToggleDayNight,
                    onSelectMode = onSelectThemeMode,
                    compact = true
                )

                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_filters_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (filter.activeFilterCount > 0) {
                                Badge { Text(filter.activeFilterCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.action_filters)
                        )
                    }
                }
            }
        }

        // Search NSE Stocks Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = onQueryChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_nse_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.isSearchingRemote) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (filter.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nse_search_input")
            )
        }

        // Remote NSE Autocomplete Suggestions Card
        AnimatedVisibility(visible = uiState.searchSuggestions.isNotEmpty() && filter.query.trim().length >= 2) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NSE EQUITIES MATCHING \"${filter.query.trim().uppercase()}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    uiState.searchSuggestions.take(5).forEachIndexed { idx, suggestion ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSuggestion(suggestion) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("search_suggestion_${suggestion.symbol}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = suggestion.symbol,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = suggestion.companyName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Analyze →",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Index Universe Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NseIndexUniverse.entries.forEach { universe ->
                val selected = filter.universe == universe
                FilterChip(
                    selected = selected,
                    onClick = { onSelectUniverse(universe) },
                    label = {
                        Text(
                            text = universe.displayName,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("universe_chip_${universe.name.lowercase()}")
                )
            }
        }

        // Timeframe Switcher Bar (15m, 1H, 1D, 1W, 1M)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Timeframe.entries.forEach { tf ->
                val selected = filter.timeframe == tf
                Surface(
                    onClick = { onSelectTimeframe(tf) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("timeframe_${tf.label.lowercase()}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tf.label,
                            style = MonoMetricSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        // Quick RSI & Moving Average Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.rsiFilterMode == RsiFilterMode.ANY && filter.maCondition == MaCondition.ANY,
                onClick = onResetFilters,
                label = { Text("All") },
                modifier = Modifier.testTag("quick_filter_all")
            )
            FilterChip(
                selected = filter.rsiFilterMode == RsiFilterMode.OVERSOLD_30,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.OVERSOLD_30, MaCondition.ANY, filter.maType, filter.maPeriod)
                },
                label = { Text("RSI < 30 Oversold") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BullishEmerald.copy(alpha = 0.2f),
                    selectedLabelColor = BullishEmerald
                ),
                modifier = Modifier.testTag("quick_filter_rsi_oversold")
            )
            FilterChip(
                selected = filter.rsiFilterMode == RsiFilterMode.OVERBOUGHT_70,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.OVERBOUGHT_70, MaCondition.ANY, filter.maType, filter.maPeriod)
                },
                label = { Text("RSI > 70 Overbought") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BearishCoral.copy(alpha = 0.2f),
                    selectedLabelColor = BearishCoral
                ),
                modifier = Modifier.testTag("quick_filter_rsi_overbought")
            )
            FilterChip(
                selected = filter.maCondition == MaCondition.GOLDEN_CROSS_50_200,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.ANY, MaCondition.GOLDEN_CROSS_50_200, MaType.SMA, 50)
                },
                label = { Text("Golden Cross 50>200") },
                modifier = Modifier.testTag("quick_filter_golden_cross")
            )
            FilterChip(
                selected = filter.maCondition == MaCondition.PRICE_ABOVE_MA && filter.maPeriod == 200,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.ANY, MaCondition.PRICE_ABOVE_MA, MaType.SMA, 200)
                },
                label = { Text("Price > 200 SMA") },
                modifier = Modifier.testTag("quick_filter_above_200")
            )
            FilterChip(
                selected = filter.maCondition == MaCondition.SHORT_UPTREND_20_50,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.ANY, MaCondition.SHORT_UPTREND_20_50, MaType.EMA, 20)
                },
                label = { Text("EMA 20 > 50") },
                modifier = Modifier.testTag("quick_filter_ema_20_50")
            )
            FilterChip(
                selected = filter.maCondition == MaCondition.RSI_ABOVE_EMA45,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.ANY, MaCondition.RSI_ABOVE_EMA45, filter.maType, filter.maPeriod)
                },
                label = { Text("RSI > EMA45") },
                modifier = Modifier.testTag("quick_filter_rsi_ema45")
            )
            FilterChip(
                selected = filter.maCondition == MaCondition.HM_EMA3_ABOVE_WMA21,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.ANY, MaCondition.HM_EMA3_ABOVE_WMA21, filter.maType, filter.maPeriod)
                },
                label = { Text("HM EMA3 > WMA21") },
                modifier = Modifier.testTag("quick_filter_hm_ema3_wma21")
            )
            FilterChip(
                selected = filter.maCondition == MaCondition.ABOVE_BB_UPPER,
                onClick = {
                    onApplyQuickPreset(RsiFilterMode.ANY, MaCondition.ABOVE_BB_UPPER, filter.maType, filter.maPeriod)
                },
                label = { Text("Above BB Upper") },
                modifier = Modifier.testTag("quick_filter_bb_upper")
            )
            FilterChip(
                selected = false,
                onClick = { showFilterSheet = true },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Custom RSI / MA") },
                modifier = Modifier.testTag("quick_filter_custom")
            )
        }

        // Scan Progress Bar
        if (uiState.isScanningIndicators && uiState.totalCount > 0) {
            val progress = (uiState.scannedCount.toFloat() / uiState.totalCount.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Computing ${filter.timeframe.displayName} RSI & Moving Averages (${uiState.scannedCount}/${uiState.totalCount})…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Market Breadth Summary Strip
        if (uiState.rawStocks.isNotEmpty()) {
            val matches = uiState.filteredStocks
            val rsis = matches.mapNotNull { it.indicators[filter.timeframe]?.rsiForPeriod(filter.rsiPeriod) }
            val avgRsi = if (rsis.isNotEmpty()) rsis.average() else null
            val bullishCount = matches.count {
                val sig = it.indicators[filter.timeframe]?.signal
                sig == TechnicalSignal.STRONG_BUY || sig == TechnicalSignal.BULLISH
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${matches.size} of ${uiState.rawStocks.size} NSE stocks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Bullish: $bullishCount",
                        style = MonoMetricSmall,
                        color = BullishEmerald
                    )
                    Text(
                        text = "Avg RSI: ${IndianFormatters.formatRsi(avgRsi)}",
                        style = MonoMetricSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Error Banner (if network fails and no data)
        if (uiState.errorMessage != null && uiState.rawStocks.isEmpty() && !uiState.isInitialLoading) {
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
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = BearishCoral,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.error_network_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("retry_nse_button")
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        } else if (uiState.isInitialLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Fetching live ${filter.universe.displayName} data from NSE…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (uiState.filteredStocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.empty_screener_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.empty_screener_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onResetFilters,
                        modifier = Modifier.testTag("empty_reset_filters_button")
                    ) {
                        Text("Reset Technical Filters")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("screener_stock_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = uiState.filteredStocks,
                    key = { it.symbol }
                ) { stock ->
                    StockScreenerCard(
                        stock = stock,
                        timeframe = filter.timeframe,
                        rsiPeriod = filter.rsiPeriod,
                        isSelected = uiState.selectedDetailSymbol == stock.symbol,
                        onClick = { onStockClick(stock.symbol) },
                        onToggleWatchlist = { onToggleWatchlist(stock) }
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        TechnicalFilterBottomSheet(
            filter = filter,
            onUpdateFilter = onUpdateFilter,
            onReset = onResetFilters,
            onSavePreset = onSavePreset,
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showEodSourceDialog) {
        var urlInput by remember(uiState.eodFeedUrl) { mutableStateOf(uiState.eodFeedUrl) }
        AlertDialog(
            onDismissRequest = { showEodSourceDialog = false },
            title = { Text(stringResource(R.string.eod_source_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Fetches /screener_data.json?t=<timestamp> on startup and Refresh EOD. Falls back to live NSE feed if unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("screener_data.json URL or Path") },
                        placeholder = { Text("https://host/screener_data.json") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eod_url_input")
                    )
                    OutlinedButton(
                        onClick = { jsonFilePickerLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_local_eod_json_button")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Load Local screener_data.json File")
                    }
                    uiState.eodStatusMessage?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateEodFeedUrl(urlInput)
                        showEodSourceDialog = false
                    },
                    modifier = Modifier.testTag("save_eod_url_button")
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fetch EOD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEodSourceDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
