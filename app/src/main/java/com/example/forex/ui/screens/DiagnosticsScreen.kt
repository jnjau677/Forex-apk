package com.example.forex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.forex.ui.viewmodel.ForexViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics screen: surfaces runtime observability for the market data pipeline
 * (source, quota, rate limiting, last errors) and the outcome of the most recent
 * background signal scan, so issues can be triaged without a debugger attached.
 */
@Composable
fun DiagnosticsScreen(viewModel: ForexViewModel) {
    val health by viewModel.marketDataHealth.collectAsState()
    val webSocketStatus by viewModel.webSocketStatus.collectAsState()
    var scanStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Diagnostics",
            fontSize = 22.sp,
            style = MaterialTheme.typography.headlineSmall
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("diagnostics_market_data_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Market Data Pipeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Source: " + health.source.name)
                Text(
                    if (health.isConfigured) "Live API configured"
                    else "Live API not configured (simulation mode)"
                )
                Text("Requests today: " + health.requestsToday + " / " + health.dailyQuota)
                Text(
                    if (health.isRateLimited) "Rate limited — cooling down"
                    else "Within rate limits"
                )
                Text("Last success: " + formatTime(health.lastSuccessAt))
                Text("Last error: " + formatTime(health.lastErrorAt))
                health.lastErrorMessage?.let { Text("Error detail: " + it) }
                Text("WebSocket: " + webSocketStatus.toString())
            }
        }

        Button(
            onClick = { scope.launch { scanStatus = viewModel.fetchLastSignalScan() } },
            modifier = Modifier.testTag("diagnostics_refresh_scan_button")
        ) {
            Text("Check last signal scan")
        }

        scanStatus?.let { status ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_scan_status_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Background Signal Scan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(status)
                }
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    if (epochMillis <= 0) "never"
    else SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(epochMillis))
