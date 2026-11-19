package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AlertCondition
import com.example.model.NseStockItem
import com.example.model.PriceAlert
import com.example.model.SavedPreset
import com.example.data.local.AppThemeMode
import com.example.ui.components.IndianFormatters
import com.example.ui.components.StockScreenerCard
import com.example.ui.components.ThemeModeToggleChip
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.viewmodel.ScreenerUiState

@Composable
fun WatchlistScreen(
    uiState: ScreenerUiState,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onToggleDayNight: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    isCheckingAlerts: Boolean,
    onStockClick: (String) -> Unit,
    onOpenWatchlistPicker: (NseStockItem) -> Unit,
    onOpenPriceAlertSheet: (NseStockItem) -> Unit,
    onSelectWatchlist: (Long) -> Unit,
    onCreateWatchlist: (String) -> Unit,
    onRenameWatchlist: (Long, String) -> Unit,
    onDeleteWatchlist: (Long) -> Unit,
    onCheckAlertsNow: () -> Unit,
    onToggleAlertActive: (Long, Boolean) -> Unit,
    onDeleteAlert: (Long) -> Unit,
    onApplyPreset: (SavedPreset) -> Unit,
    onDeletePreset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val activeWatchlist = uiState.activeWatchlist
    val activeStocks = uiState.activeWatchlistStocks
    val activeAlertSymbols = remember(uiState.priceAlerts) {
        uiState.priceAlerts.filter { it.isActive }.map { it.symbol }.toSet()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personal Watchlists",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Room Watchlists & WorkManager Price Alerts • ${uiState.filter.timeframe.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeModeToggleChip(
                    themeMode = themeMode,
                    isDarkActive = isDarkTheme,
                    onToggleDayNight = onToggleDayNight,
                    onSelectMode = onSelectThemeMode,
                    compact = true
                )
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_watchlist_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New List")
                }
            }
        }

        // Named Watchlist Selector Chips
        if (uiState.namedWatchlists.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.namedWatchlists.forEach { wl ->
                    val selected = wl.id == activeWatchlist?.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectWatchlist(wl.id) },
                        label = {
                            Text(
                                text = "${wl.name} (${wl.symbols.size})",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("watchlist_tab_${wl.id}")
                    )
                }
            }
        }

        // Active Watchlist Management Bar (Rename / Delete)
        if (activeWatchlist != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${activeWatchlist.name} • ${activeWatchlist.symbols.size} stocks",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rename_watchlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Rename watchlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.namedWatchlists.size > 1) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_watchlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete watchlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("watchlist_stock_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Price Threshold Alerts Section
            if (uiState.priceAlerts.isNotEmpty()) {
                item(key = "alerts_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Price Alerts (${uiState.priceAlerts.count { it.isActive }} active)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = onCheckAlertsNow,
                            enabled = !isCheckingAlerts,
                            modifier = Modifier.testTag("check_alerts_now_button")
                        ) {
                            if (isCheckingAlerts) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Checking…")
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Check Now")
                            }
                        }
                    }
                }

                items(uiState.priceAlerts, key = { "alert_${it.id}" }) { alert ->
                    PriceAlertRowCard(
                        alert = alert,
                        onOpenStock = { onStockClick(alert.symbol) },
                        onToggleActive = { active -> onToggleAlertActive(alert.id, active) },
                        onDelete = { onDeleteAlert(alert.id) }
                    )
                }
            }

            // Saved Screener Scans Section
            if (uiState.savedPresets.isNotEmpty()) {
                item(key = "presets_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Saved Screener Scans",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.savedPresets, key = { it.id }) { preset ->
                                Card(
                                    modifier = Modifier
                                        .clickable { onApplyPreset(preset) }
                                        .testTag("saved_preset_card_${preset.id}"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = preset.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${preset.timeframe.label} • ${preset.rsiFilterMode.label} • ${preset.maCondition.label}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeletePreset(preset.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete preset",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (activeStocks.isEmpty()) {
                item(key = "empty_watchlist") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.StarOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (activeWatchlist != null) {
                                    "\"${activeWatchlist.name}\" has no stocks yet"
                                } else {
                                    stringResource(R.string.empty_watchlist_title)
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.empty_watchlist_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = activeStocks,
                    key = { it.symbol }
                ) { stock ->
                    StockScreenerCard(
                        stock = stock,
                        timeframe = uiState.filter.timeframe,
                        rsiPeriod = uiState.filter.rsiPeriod,
                        hasActiveAlert = stock.symbol in activeAlertSymbols,
                        onAlertClick = { onOpenPriceAlertSheet(stock) },
                        onClick = { onStockClick(stock.symbol) },
                        onToggleWatchlist = { onOpenWatchlistPicker(stock) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Named Watchlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Watchlist Name") },
                    placeholder = { Text("e.g. Swing Picks, Oversold RSI") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_create_watchlist_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateWatchlist(nameInput)
                        showCreateDialog = false
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog && activeWatchlist != null) {
        var renameInput by remember(activeWatchlist.id) { mutableStateOf(activeWatchlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Watchlist") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Watchlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameWatchlist(activeWatchlist.id, renameInput)
                        showRenameDialog = false
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm && activeWatchlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${activeWatchlist.name}\"?") },
            text = { Text("This removes the watchlist and its saved stock list from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWatchlist(activeWatchlist.id)
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PriceAlertRowCard(
    alert: PriceAlert,
    onOpenStock: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val condColor = if (alert.condition == AlertCondition.ABOVE) BullishEmerald else BearishCoral
    val statusText = when {
        alert.triggeredPrice != null && !alert.isActive ->
            "Triggered at ${IndianFormatters.formatInr(alert.triggeredPrice)}"
        alert.isActive && alert.lastCheckedPrice != null ->
            "Monitoring • Last ${IndianFormatters.formatInr(alert.lastCheckedPrice)}"
        alert.isActive -> "Monitoring via WorkManager"
        else -> "Paused"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenStock)
            .testTag("price_alert_card_${alert.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (alert.triggeredPrice != null && !alert.isActive) {
                BullishEmerald.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = alert.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = condColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${alert.condition.symbolText} ${IndianFormatters.formatInr(alert.targetPrice)}",
                            style = MonoMetricSmall,
                            color = condColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (alert.triggeredPrice != null && !alert.isActive) BullishEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alert.isActive,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.testTag("toggle_alert_${alert.id}")
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_alert_${alert.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete price alert",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
