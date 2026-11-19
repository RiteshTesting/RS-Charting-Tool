package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ScannerCategory
import com.example.domain.ScannerSentiment
import com.example.domain.StockScannerCatalog
import com.example.domain.StockScannerDefinition
import com.example.model.NseIndexUniverse
import com.example.model.NseStockItem
import com.example.model.Timeframe
import com.example.data.local.AppThemeMode
import com.example.ui.components.StockScreenerCard
import com.example.ui.components.ThemeModeToggleChip
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.viewmodel.ScreenerUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannersScreen(
    uiState: ScreenerUiState,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onToggleDayNight: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectUniverse: (NseIndexUniverse) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onSelectCategory: (ScannerCategory) -> Unit,
    onScannerQueryChanged: (String) -> Unit,
    onToggleMultiScanMode: () -> Unit,
    onToggleScannerSelection: (String) -> Unit,
    onClearSelectedScanners: () -> Unit,
    onOpenScannerResults: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeframe = uiState.filter.timeframe
    val stocks = uiState.rawStocks

    val visibleScanners = remember(uiState.scannerCategory, uiState.scannerQuery) {
        val q = uiState.scannerQuery.trim()
        StockScannerCatalog.scanners.filter { def ->
            val catMatch = uiState.scannerCategory == ScannerCategory.ALL ||
                def.category == uiState.scannerCategory
            val queryMatch = q.isEmpty() ||
                def.title.contains(q, ignoreCase = true) ||
                def.subtitle.contains(q, ignoreCase = true)
            catMatch && queryMatch
        }
    }

    val scannerMatchesMap = remember(stocks, timeframe, visibleScanners) {
        visibleScanners.associate { def ->
            def.id to StockScannerCatalog.evaluateScanner(def, stocks, timeframe)
        }
    }

    val combinedMatches = remember(stocks, timeframe, uiState.selectedScannerIds) {
        if (uiState.selectedScannerIds.isEmpty()) {
            emptyList()
        } else {
            StockScannerCatalog.evaluateCombinedScanners(
                scannerIds = uiState.selectedScannerIds.toList(),
                stocks = stocks,
                timeframe = timeframe
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NSE Stock Scanners",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${StockScannerCatalog.scanners.size} technical scans • ${uiState.filter.universe.displayName} (${timeframe.displayName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeModeToggleChip(
                        themeMode = themeMode,
                        isDarkActive = isDarkTheme,
                        onToggleDayNight = onToggleDayNight,
                        onSelectMode = onSelectThemeMode,
                        compact = true
                    )

                    FilterChip(
                        selected = uiState.isMultiScanMode,
                        onClick = onToggleMultiScanMode,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (uiState.isMultiScanMode) "Multi ON" else "Combine",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier.testTag("toggle_multi_scan_button")
                    )

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanners_refresh_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh NSE data")
                    }
                }
            }

            // Search Scanners Input
            OutlinedTextField(
                value = uiState.scannerQuery,
                onValueChange = onScannerQueryChanged,
                placeholder = { Text("Search scanners (e.g. RSI, Golden Cross, Volume, 200 SMA)…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.scannerQuery.isNotEmpty()) {
                        IconButton(onClick = { onScannerQueryChanged("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
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
                    .padding(horizontal = 16.dp)
                    .testTag("scanner_search_input")
            )

            Spacer(Modifier.height(8.dp))

            // Index Universe Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NseIndexUniverse.entries.forEach { universe ->
                    FilterChip(
                        selected = uiState.filter.universe == universe,
                        onClick = { onSelectUniverse(universe) },
                        label = { Text(universe.displayName) }
                    )
                }
            }

            // Timeframe Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val selected = timeframe == tf
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
                            .height(36.dp)
                            .testTag("scanners_tf_${tf.label.lowercase()}")
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

            // Scanner Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScannerCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = uiState.scannerCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("scanner_cat_${cat.name.lowercase()}")
                    )
                }
            }

            if (uiState.isScanningIndicators && uiState.totalCount > 0) {
                val progress = (uiState.scannedCount.toFloat() / uiState.totalCount.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("scanners_catalog_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = if (uiState.isMultiScanMode && uiState.selectedScannerIds.isNotEmpty()) 96.dp else 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = visibleScanners,
                    key = { it.id }
                ) { scanner ->
                    val matches = scannerMatchesMap[scanner.id].orEmpty()
                    val isChecked = scanner.id in uiState.selectedScannerIds

                    ScannerCatalogCard(
                        scanner = scanner,
                        matches = matches,
                        isMultiScanMode = uiState.isMultiScanMode,
                        isChecked = isChecked,
                        onClick = {
                            if (uiState.isMultiScanMode) {
                                onToggleScannerSelection(scanner.id)
                            } else {
                                onOpenScannerResults(listOf(scanner.id))
                            }
                        }
                    )
                }
            }
        }

        // Sticky Multi-Scan Combo Action Bar
        AnimatedVisibility(
            visible = uiState.isMultiScanMode && uiState.selectedScannerIds.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.selectedScannerIds.size} Scanners Combined (AND)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${combinedMatches.size} NSE stocks pass all selected scans",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onClearSelectedScanners) {
                        Text("Clear")
                    }
                    Button(
                        onClick = { onOpenScannerResults(uiState.selectedScannerIds.toList()) },
                        modifier = Modifier.testTag("run_combined_scan_button")
                    ) {
                        Text("View (${combinedMatches.size})")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScannerCatalogCard(
    scanner: StockScannerDefinition,
    matches: List<NseStockItem>,
    isMultiScanMode: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val sentimentColor = sentimentColor(scanner.sentiment)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("scanner_card_${scanner.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isChecked) 1.5.dp else 1.dp,
            color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(sentimentColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scanner.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = scanner.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = sentimentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = scanner.sentiment.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = sentimentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = if (matches.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${matches.size}",
                            style = MonoMetricSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (matches.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (isMultiScanMode) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onClick() }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Open scanner results",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = scanner.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (matches.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    matches.take(5).forEach { stock ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stock.symbol,
                                style = MonoMetricSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (matches.size > 5) {
                        Text(
                            text = "+${matches.size - 5} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerResultsScreen(
    scannerIds: List<String>,
    uiState: ScreenerUiState,
    onBack: () -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onStockClick: (String, List<String>) -> Unit,
    onOpenWatchlistPicker: (NseStockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeframe = uiState.filter.timeframe
    val definitions = remember(scannerIds) {
        scannerIds.mapNotNull { StockScannerCatalog.findById(it) }
    }
    var sortByChangeDesc by remember { mutableStateOf(true) }

    val matchingStocks = remember(uiState.rawStocks, timeframe, scannerIds, sortByChangeDesc) {
        val raw = StockScannerCatalog.evaluateCombinedScanners(
            scannerIds = scannerIds,
            stocks = uiState.rawStocks,
            timeframe = timeframe
        )
        if (sortByChangeDesc) {
            raw.sortedByDescending { it.changePercent }
        } else {
            raw.sortedWith(compareBy(nullsLast()) { it.indicators[timeframe]?.rsi14 })
        }
    }

    val title = when {
        definitions.size == 1 -> definitions.first().title
        definitions.size > 1 -> "Combined Scan (${definitions.size} Rules)"
        else -> "Scanner Results"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${matchingStocks.size} NSE matches • ${uiState.filter.universe.displayName} (${timeframe.displayName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scanner_results_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                OutlinedButton(
                    onClick = { sortByChangeDesc = !sortByChangeDesc },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(if (sortByChangeDesc) "Sort: % Chg" else "Sort: RSI")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Timeframe Switcher right inside results
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Timeframe.entries.forEach { tf ->
                val selected = timeframe == tf
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
                        .height(36.dp)
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

        if (definitions.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                definitions.forEach { def ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = def.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (matchingStocks.isEmpty()) {
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
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No stocks match this scan on ${timeframe.displayName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Try switching to another timeframe (15m, 1H, 1D, 1W, 1M) or broadening your scan combination.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("scanner_results_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = matchingStocks,
                    key = { it.symbol }
                ) { stock ->
                    StockScreenerCard(
                        stock = stock,
                        timeframe = timeframe,
                        rsiPeriod = uiState.filter.rsiPeriod,
                        onClick = {
                            onStockClick(stock.symbol, matchingStocks.map { it.symbol })
                        },
                        onToggleWatchlist = { onOpenWatchlistPicker(stock) }
                    )
                }
            }
        }
    }
}

private fun sentimentColor(sentiment: ScannerSentiment): Color = when (sentiment) {
    ScannerSentiment.BULLISH -> BullishEmerald
    ScannerSentiment.BEARISH -> BearishCoral
    ScannerSentiment.REVERSAL -> AmberSignal
    ScannerSentiment.BREAKOUT -> ElectricCyan
}
