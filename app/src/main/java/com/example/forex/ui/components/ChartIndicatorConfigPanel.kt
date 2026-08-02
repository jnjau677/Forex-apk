package com.example.forex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.forex.data.model.IndicatorSettings

@Composable
fun ChartIndicatorConfigDialog(
    indicatorSettings: IndicatorSettings,
    onUpdateIndicatorSettings: (IndicatorSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("indicator_config_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Chart Overlays & Indicators",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Configure technical overlays on live candles",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_config_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Presets Row
                Text(
                    text = "QUICK PRESETS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            onUpdateIndicatorSettings(
                                IndicatorSettings(
                                    showSma20 = false,
                                    showSma50 = false,
                                    showEma20 = true,
                                    showEma50 = true,
                                    showBollingerBands = true,
                                    showSupportResistance = true,
                                    showRsiSubchart = true,
                                    showPatterns = true
                                )
                            )
                        },
                        label = { Text("Default", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_default")
                    )
                    SuggestionChip(
                        onClick = {
                            onUpdateIndicatorSettings(
                                indicatorSettings.copy(
                                    showSma20 = true,
                                    showSma50 = true,
                                    showEma20 = true,
                                    showEma50 = true,
                                    showEma200 = true
                                )
                            )
                        },
                        label = { Text("Moving Avgs", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_ma")
                    )
                    SuggestionChip(
                        onClick = {
                            onUpdateIndicatorSettings(
                                IndicatorSettings(
                                    showSma20 = false, showSma50 = false,
                                    showEma20 = false, showEma50 = false, showEma200 = false,
                                    showBollingerBands = false, showSupportResistance = false,
                                    showRsiSubchart = false, showPatterns = false
                                )
                            )
                        },
                        label = { Text("Clean", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_clean")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Indicator Options
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Category: Simple Moving Averages (SMA)
                    CategoryHeader("SIMPLE MOVING AVERAGES (SMA)")

                    IndicatorRowItem(
                        title = "SMA (20)",
                        description = "20-period Simple Moving Average line",
                        badgeColor = Color(0xFFF59E0B),
                        checked = indicatorSettings.showSma20,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showSma20 = checked))
                        },
                        testTag = "switch_sma20"
                    )

                    IndicatorRowItem(
                        title = "SMA (50)",
                        description = "50-period Simple Moving Average line",
                        badgeColor = Color(0xFF84CC16),
                        checked = indicatorSettings.showSma50,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showSma50 = checked))
                        },
                        testTag = "switch_sma50"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category: Exponential Moving Averages (EMA)
                    CategoryHeader("EXPONENTIAL MOVING AVERAGES (EMA)")

                    IndicatorRowItem(
                        title = "EMA (20)",
                        description = "Fast 20-period Exponential Trend line",
                        badgeColor = Color(0xFF06B6D4),
                        checked = indicatorSettings.showEma20,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showEma20 = checked))
                        },
                        testTag = "switch_ema20"
                    )

                    IndicatorRowItem(
                        title = "EMA (50)",
                        description = "Medium 50-period Exponential Trend line",
                        badgeColor = Color(0xFFA855F7),
                        checked = indicatorSettings.showEma50,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showEma50 = checked))
                        },
                        testTag = "switch_ema50"
                    )

                    IndicatorRowItem(
                        title = "EMA (200)",
                        description = "Long-term 200-period Major Trend line",
                        badgeColor = Color(0xFFFFB703),
                        checked = indicatorSettings.showEma200,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showEma200 = checked))
                        },
                        testTag = "switch_ema200"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category: Volatility & Price Action
                    CategoryHeader("VOLATILITY & MARKET STRUCTURE")

                    IndicatorRowItem(
                        title = "Bollinger Bands",
                        description = "20-period SMA with ±2 Standard Deviation bands",
                        badgeColor = Color(0xFF38BDF8),
                        checked = indicatorSettings.showBollingerBands,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showBollingerBands = checked))
                        },
                        testTag = "switch_bollinger"
                    )

                    IndicatorRowItem(
                        title = "Support & Resistance",
                        description = "Automated swing key floor & ceiling levels",
                        badgeColor = Color(0xFF10B981),
                        checked = indicatorSettings.showSupportResistance,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showSupportResistance = checked))
                        },
                        testTag = "switch_sr"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category: Oscillators & Badges
                    CategoryHeader("SUB-CHARTS & SIGNAL MARKERS")

                    IndicatorRowItem(
                        title = "RSI (14) Oscillator",
                        description = "Relative Strength Index subchart with 70/30 zones",
                        badgeColor = Color(0xFFF97316),
                        checked = indicatorSettings.showRsiSubchart,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showRsiSubchart = checked))
                        },
                        testTag = "switch_rsi"
                    )

                    IndicatorRowItem(
                        title = "Pattern Recognition",
                        description = "Candlestick pattern markers (Engulfing, Stars, etc.)",
                        badgeColor = MaterialTheme.colorScheme.tertiary,
                        checked = indicatorSettings.showPatterns,
                        onCheckedChange = { checked ->
                            onUpdateIndicatorSettings(indicatorSettings.copy(showPatterns = checked))
                        },
                        testTag = "switch_patterns"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("apply_indicator_config_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply & Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun IndicatorRowItem(
    title: String,
    description: String,
    badgeColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .scaleSmall()
                .testTag(testTag)
        )
    }
}

private fun Modifier.scaleSmall(): Modifier = this.padding(start = 4.dp)
