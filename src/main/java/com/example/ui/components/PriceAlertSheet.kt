package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AlertCondition
import com.example.model.NseStockItem
import com.example.notifications.PriceAlertNotificationHelper
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.MonoPriceMedium
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertSheet(
    stock: NseStockItem,
    onCreateAlert: (targetPrice: Double, condition: AlertCondition) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var condition by remember { mutableStateOf(AlertCondition.ABOVE) }
    var targetPriceText by remember(stock.symbol) {
        val suggested = stock.lastPrice * 1.02
        mutableStateOf(String.format(Locale.US, "%.2f", suggested))
    }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val parsedPrice = targetPriceText.toDoubleOrNull()
    val isValidPrice = parsedPrice != null && parsedPrice > 0.0

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (parsedPrice != null && parsedPrice > 0.0) {
            onCreateAlert(parsedPrice, condition)
            if (granted) {
                onDismiss()
            } else {
                permissionDeniedNote = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Price Threshold Alert",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stock.symbol} • Live LTP ${IndianFormatters.formatInr(stock.lastPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Text(
                text = "Alert Condition",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlertCondition.entries.forEach { cond ->
                    val selected = condition == cond
                    val tint = if (cond == AlertCondition.ABOVE) BullishEmerald else BearishCoral
                    FilterChip(
                        selected = selected,
                        onClick = {
                            condition = cond
                            val pct = if (cond == AlertCondition.ABOVE) 1.02 else 0.98
                            targetPriceText = String.format(Locale.US, "%.2f", stock.lastPrice * pct)
                        },
                        label = {
                            Text(
                                text = "${cond.label} (${cond.symbolText})",
                                color = if (selected) tint else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alert_condition_${cond.name.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = targetPriceText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        targetPriceText = input
                    }
                },
                label = { Text("Target NSE Price (₹)") },
                prefix = { Text("₹ ", style = MonoPriceMedium) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_target_price_input")
            )

            // Quick Percentage Preset Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-5, -2, 2, 5).forEach { pct ->
                    OutlinedButton(
                        onClick = {
                            val newTarget = stock.lastPrice * (1.0 + pct / 100.0)
                            targetPriceText = String.format(Locale.US, "%.2f", newTarget)
                            condition = if (pct > 0) AlertCondition.ABOVE else AlertCondition.BELOW
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text("${if (pct > 0) "+" else ""}$pct%")
                    }
                }
            }

            if (permissionDeniedNote) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Alert saved! Enable Notifications in Android Settings so WorkManager can display background alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val target = parsedPrice ?: return@Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !PriceAlertNotificationHelper.hasNotificationPermission(context)
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onCreateAlert(target, condition)
                        onDismiss()
                    }
                },
                enabled = isValidPrice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_price_alert_button")
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Set WorkManager Price Alert")
            }
        }
    }
}
