package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.NseStockItem
import com.example.model.TechnicalSignal
import com.example.model.Timeframe
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BearishCoral
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.MonoPriceMedium

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockScreenerCard(
    stock: NseStockItem,
    timeframe: Timeframe,
    rsiPeriod: Int,
    isSelected: Boolean = false,
    hasActiveAlert: Boolean = false,
    onAlertClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = stock.indicators[timeframe]
    val rsiValue = snapshot?.rsiForPeriod(rsiPeriod)
    val isUp = stock.changePercent >= 0.0
    val changeColor = if (isUp) BullishEmerald else BearishCoral

    val borderStroke = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("stock_card_${stock.symbol}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Symbol + Signal + Price + Watchlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stock.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (snapshot != null) {
                            SignalBadge(signal = snapshot.signal)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stock.companyName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Mini Sparkline
                if (snapshot != null && snapshot.candles.size >= 4) {
                    MiniSparkline(
                        candles = snapshot.candles,
                        ema20History = snapshot.ema20History,
                        isPositive = isUp,
                        modifier = Modifier
                            .width(68.dp)
                            .height(34.dp)
                            .padding(horizontal = 4.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = IndianFormatters.formatInr(stock.lastPrice),
                        style = MonoPriceMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(changeColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = IndianFormatters.formatSignedPercent(stock.changePercent),
                            style = MonoMetricSmall,
                            color = changeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (onAlertClick != null) {
                    IconButton(
                        onClick = onAlertClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("alert_button_${stock.symbol}")
                    ) {
                        Icon(
                            imageVector = if (hasActiveAlert) Icons.Rounded.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "Set price alert for ${stock.symbol}",
                            tint = if (hasActiveAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("watchlist_toggle_${stock.symbol}")
                ) {
                    Icon(
                        imageVector = if (stock.isWatchlisted) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (stock.isWatchlisted) {
                            "Remove ${stock.symbol} from watchlist"
                        } else {
                            "Add ${stock.symbol} to watchlist"
                        },
                        tint = if (stock.isWatchlisted) AmberSignal else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom Row: RSI Spectrum & Moving Average Alignment Tags
            if (snapshot != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.width(116.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RSI($rsiPeriod)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = IndianFormatters.formatRsi(rsiValue),
                                style = MonoMetricSmall,
                                fontWeight = FontWeight.Bold,
                                color = rsiColor(rsiValue)
                            )
                        }
                        RsiSpectrumBar(
                            rsi = rsiValue,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stock.rsRating?.let { rs ->
                            MaStatusChip(
                                label = "RS ${rs.toInt()}",
                                isBullish = rs >= 70.0
                            )
                        }
                        (stock.volRatio ?: snapshot.volRatio)?.takeIf { it >= 1.2 }?.let { vr ->
                            MaStatusChip(
                                label = String.format(java.util.Locale.US, "Vol %.1fx", vr),
                                isBullish = true
                            )
                        }
                        snapshot.ema20?.let { ema20 ->
                            val above = stock.lastPrice > ema20
                            MaStatusChip(
                                label = "${if (above) "▲" else "▼"} 20E",
                                isBullish = above
                            )
                        }
                        snapshot.sma50?.let { sma50 ->
                            val above = stock.lastPrice > sma50
                            MaStatusChip(
                                label = "${if (above) "▲" else "▼"} 50S",
                                isBullish = above
                            )
                        }
                        snapshot.sma200?.let { sma200 ->
                            val above = stock.lastPrice > sma200
                            MaStatusChip(
                                label = "${if (above) "▲" else "▼"} 200S",
                                isBullish = above
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignalBadge(signal: TechnicalSignal) {
    val (bg, fg) = when (signal) {
        TechnicalSignal.STRONG_BUY -> BullishEmerald.copy(alpha = 0.2f) to BullishEmerald
        TechnicalSignal.BULLISH -> BullishEmerald.copy(alpha = 0.14f) to BullishEmerald
        TechnicalSignal.OVERSOLD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) to MaterialTheme.colorScheme.primary
        TechnicalSignal.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        TechnicalSignal.OVERBOUGHT -> AmberSignal.copy(alpha = 0.18f) to AmberSignal
        TechnicalSignal.BEARISH -> BearishCoral.copy(alpha = 0.14f) to BearishCoral
        TechnicalSignal.STRONG_SELL -> BearishCoral.copy(alpha = 0.22f) to BearishCoral
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = signal.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MaStatusChip(label: String, isBullish: Boolean) {
    val color = if (isBullish) BullishEmerald else BearishCoral
    Surface(
        color = color.copy(alpha = 0.11f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun rsiColor(rsi: Double?): Color {
    return when {
        rsi == null -> MaterialTheme.colorScheme.onSurfaceVariant
        rsi < 30.0 -> BullishEmerald
        rsi > 70.0 -> BearishCoral
        rsi in 50.0..70.0 -> MaterialTheme.colorScheme.primary
        else -> AmberSignal
    }
}
