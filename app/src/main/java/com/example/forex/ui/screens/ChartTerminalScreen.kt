package com.example.forex.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import com.example.forex.ui.components.ChartIndicatorConfigDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.forex.data.model.CurrencyPair
import com.example.forex.data.model.IndicatorSettings
import com.example.forex.data.model.Timeframe
import com.example.forex.data.model.TradeSignal
import com.example.forex.ui.components.CandlestickChartCanvas
import com.example.forex.ui.components.LiveTickerHeader
import com.example.forex.ui.components.MarketSentimentCard
import com.example.forex.ui.components.PairSelectorBar
import com.example.forex.ui.viewmodel.ForexViewModel

@Composable
fun ChartTerminalScreen(
    viewModel: ForexViewModel,
    onNavigateToAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairs by viewModel.pairs.collectAsState()
    val selectedPair by viewModel.selectedPair.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val indicatorSettings by viewModel.indicatorSettings.collectAsState()
    val inspectedSignal by viewModel.inspectedSignal.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isChartLoading by viewModel.isChartLoading.collectAsState()
    var showSentimentGauge by remember { mutableStateOf(false) }
    var showIndicatorConfigDialog by remember { mutableStateOf(false) }

    val candles = remember(selectedPair, selectedTimeframe, pairs) {
        viewModel.getCandlesForSelectedPair()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Theme Toggle and Pair Selection
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PairSelectorBar(
                    pairs = pairs,
                    selectedPair = selectedPair,
                    onPairSelected = { viewModel.selectPair(it) }
                )
            }
            IconButton(onClick = { viewModel.refreshChartData() }, modifier = Modifier.testTag("refresh_chart_data_btn")) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Market Data",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { viewModel.toggleTheme() }, modifier = Modifier.testTag("theme_toggle_btn")) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Live Header
        LiveTickerHeader(
            pair = selectedPair,
            selectedTimeframe = selectedTimeframe,
            onTimeframeSelected = { viewModel.selectTimeframe(it) }
        )

        // Indicator Toggle Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Button(
                    onClick = { showIndicatorConfigDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("open_indicator_config_panel")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Overlays Config",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showSma20,
                    onClick = { viewModel.toggleIndicator { it.copy(showSma20 = !it.showSma20) } },
                    label = { Text("SMA 20", fontSize = 11.sp, color = if (indicatorSettings.showSma20) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFF59E0B)),
                    modifier = Modifier.testTag("toggle_sma20")
                )
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showSma50,
                    onClick = { viewModel.toggleIndicator { it.copy(showSma50 = !it.showSma50) } },
                    label = { Text("SMA 50", fontSize = 11.sp, color = if (indicatorSettings.showSma50) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF84CC16)),
                    modifier = Modifier.testTag("toggle_sma50")
                )
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showEma20,
                    onClick = { viewModel.toggleIndicator { it.copy(showEma20 = !it.showEma20) } },
                    label = { Text("EMA 20", fontSize = 11.sp, color = if (indicatorSettings.showEma20) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF06B6D4)),
                    modifier = Modifier.testTag("toggle_ema20")
                )
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showEma50,
                    onClick = { viewModel.toggleIndicator { it.copy(showEma50 = !it.showEma50) } },
                    label = { Text("EMA 50", fontSize = 11.sp, color = if (indicatorSettings.showEma50) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFA855F7)),
                    modifier = Modifier.testTag("toggle_ema50")
                )
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showBollingerBands,
                    onClick = { viewModel.toggleIndicator { it.copy(showBollingerBands = !it.showBollingerBands) } },
                    label = { Text("Bollinger Bands", fontSize = 11.sp, color = if (indicatorSettings.showBollingerBands) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("toggle_bollinger")
                )
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showSupportResistance,
                    onClick = { viewModel.toggleIndicator { it.copy(showSupportResistance = !it.showSupportResistance) } },
                    label = { Text("Support/Resistance", fontSize = 11.sp, color = if (indicatorSettings.showSupportResistance) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.testTag("toggle_sr")
                )
            }
            item {
                FilterChip(
                    selected = indicatorSettings.showRsiSubchart,
                    onClick = { viewModel.toggleIndicator { it.copy(showRsiSubchart = !it.showRsiSubchart) } },
                    label = { Text("RSI (14)", fontSize = 11.sp, color = if (indicatorSettings.showRsiSubchart) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.testTag("toggle_rsi")
                )
            }
            item {
                FilterChip(
                    selected = showSentimentGauge,
                    onClick = { showSentimentGauge = !showSentimentGauge },
                    label = { Text("Sentiment Gauge", fontSize = 11.sp, color = if (showSentimentGauge) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (showSentimentGauge) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("toggle_sentiment_gauge")
                )
            }
        }

        if (showIndicatorConfigDialog) {
            ChartIndicatorConfigDialog(
                indicatorSettings = indicatorSettings,
                onUpdateIndicatorSettings = { newSettings ->
                    viewModel.toggleIndicator { newSettings }
                },
                onDismiss = { showIndicatorConfigDialog = false }
            )
        }

        // Market Sentiment Gauge Card overlay
        AnimatedVisibility(visible = showSentimentGauge) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MarketSentimentCard(pair = selectedPair, candles = candles)
            }
        }

        // Inspected Signal Banner Alert
        if (inspectedSignal != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Inspecting ${inspectedSignal?.pairSymbol} Target Lines",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "SL: ${inspectedSignal?.stopLoss} | TP1: ${inspectedSignal?.takeProfit1}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = { viewModel.setInspectedSignal(null) }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            }
        }

        // Main Chart Canvas View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CandlestickChartCanvas(
                candles = candles,
                pair = selectedPair,
                indicatorSettings = indicatorSettings,
                activeSignal = inspectedSignal,
                onAnalyzeSnapshot = { bitmap ->
                    viewModel.analyzeSnapshotWithAi(bitmap)
                    onNavigateToAi()
                },
                isLoading = isChartLoading,
                modifier = Modifier.fillMaxSize()
            )

            // AI Analysis Floating Action Chip
            FloatingActionButton(
                onClick = {
                    viewModel.runAiTechnicalAnalysis()
                    onNavigateToAi()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("floating_ai_analyst_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Analysis", tint = MaterialTheme.colorScheme.onPrimary)
                    Text("AI Breakdown", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
