package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.MaCondition
import com.example.model.MaType
import com.example.model.RsiFilterMode
import com.example.model.ScreenerFilterState
import com.example.model.SortOption
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TechnicalFilterBottomSheet(
    filter: ScreenerFilterState,
    onUpdateFilter: ((ScreenerFilterState) -> ScreenerFilterState) -> Unit,
    onReset: () -> Unit,
    onSavePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSaveDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Technical Indicator Filters",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Timeframe: ${filter.timeframe.displayName} • ${filter.universe.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("reset_filters_button")
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // RSI Section
            Text(
                text = "RSI (Relative Strength Index)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Period:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(9, 14, 21).forEach { period ->
                    FilterChip(
                        selected = filter.rsiPeriod == period,
                        onClick = { onUpdateFilter { it.copy(rsiPeriod = period) } },
                        label = { Text("RSI $period") },
                        modifier = Modifier.testTag("rsi_period_$period")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RsiFilterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = filter.rsiFilterMode == mode,
                        onClick = { onUpdateFilter { it.copy(rsiFilterMode = mode) } },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("rsi_mode_${mode.name.lowercase()}")
                    )
                }
            }

            if (filter.rsiFilterMode == RsiFilterMode.CUSTOM) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Custom RSI Range", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${filter.rsiMin.roundToInt()} – ${filter.rsiMax.roundToInt()}",
                            style = MonoMetricSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RangeSlider(
                        value = filter.rsiMin..filter.rsiMax,
                        onValueChange = { range ->
                            onUpdateFilter {
                                it.copy(
                                    rsiMin = range.start.roundToInt().toFloat(),
                                    rsiMax = range.endInclusive.roundToInt().toFloat()
                                )
                            }
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("rsi_custom_slider")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Moving Average Section
            Text(
                text = "Moving Averages (EMA / SMA)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MaType.entries.forEach { type ->
                    FilterChip(
                        selected = filter.maType == type,
                        onClick = { onUpdateFilter { it.copy(maType = type) } },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag("ma_type_${type.name.lowercase()}")
                    )
                }
                val periods = if (filter.maType == MaType.EMA) {
                    listOf(9, 20, 45, 50, 100, 200)
                } else {
                    listOf(20, 50, 100, 200)
                }
                periods.forEach { period ->
                    FilterChip(
                        selected = filter.maPeriod == period,
                        onClick = { onUpdateFilter { it.copy(maPeriod = period) } },
                        label = { Text("$period") },
                        modifier = Modifier.testTag("ma_period_$period")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MaCondition.entries.forEach { cond ->
                    FilterChip(
                        selected = filter.maCondition == cond,
                        onClick = { onUpdateFilter { it.copy(maCondition = cond) } },
                        label = { Text(cond.label) },
                        modifier = Modifier.testTag("ma_cond_${cond.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // EOD Strength, Volume Ratio & 52W High Proximity Filters
            Text(
                text = "EOD Strength, Volume & 52W High",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Min Volume Ratio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0f to "Any", 1.2f to "≥ 1.2x", 1.5f to "≥ 1.5x", 2.0f to "≥ 2.0x").forEach { (valMin, lbl) ->
                    FilterChip(
                        selected = filter.minVolRatio == valMin,
                        onClick = { onUpdateFilter { it.copy(minVolRatio = valMin) } },
                        label = { Text(lbl) },
                        modifier = Modifier.testTag("vol_ratio_${valMin}")
                    )
                }
            }

            Text(
                text = "Min RS Rating",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0f to "Any", 70f to "RS ≥ 70", 80f to "RS ≥ 80", 90f to "RS ≥ 90").forEach { (rsMin, lbl) ->
                    FilterChip(
                        selected = filter.minRsRating == rsMin,
                        onClick = { onUpdateFilter { it.copy(minRsRating = rsMin) } },
                        label = { Text(lbl) },
                        modifier = Modifier.testTag("rs_rating_${rsMin.roundToInt()}")
                    )
                }
            }

            Text(
                text = "Distance From 52W High",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(100f to "Any", 5f to "Within 5%", 10f to "Within 10%", 20f to "Within 20%").forEach { (maxDist, lbl) ->
                    FilterChip(
                        selected = filter.maxDistFrom52wPct == maxDist,
                        onClick = { onUpdateFilter { it.copy(maxDistFrom52wPct = maxDist) } },
                        label = { Text(lbl) },
                        modifier = Modifier.testTag("dist_52w_${maxDist.roundToInt()}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Sort Section
            Text(
                text = "Sort Results",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SortOption.entries.forEach { sort ->
                    FilterChip(
                        selected = filter.sortOption == sort,
                        onClick = { onUpdateFilter { it.copy(sortOption = sort) } },
                        label = { Text(sort.label) },
                        modifier = Modifier.testTag("sort_${sort.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_preset_open_button")
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save Scan")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("apply_filters_button")
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }

    if (showSaveDialog) {
        var presetName by remember {
            val defaultLabel = buildString {
                append(filter.timeframe.label)
                append(" ")
                if (filter.rsiFilterMode != RsiFilterMode.ANY) {
                    append(filter.rsiFilterMode.label)
                } else {
                    append("RSI(${filter.rsiPeriod})")
                }
                if (filter.maCondition != MaCondition.ANY) {
                    append(" • ${filter.maType.label}${filter.maPeriod}")
                }
            }
            mutableStateOf(defaultLabel)
        }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Screener Scan") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Scan Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePreset(presetName)
                        showSaveDialog = false
                    },
                    enabled = presetName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
