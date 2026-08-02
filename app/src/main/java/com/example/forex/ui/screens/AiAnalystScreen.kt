package com.example.forex.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.forex.ui.components.MarketSentimentCard
import com.example.forex.ui.viewmodel.ForexViewModel

@Composable
fun AiAnalystScreen(
    viewModel: ForexViewModel,
    modifier: Modifier = Modifier
) {
    val aiReport by viewModel.aiReport.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingAi.collectAsState()
    val selectedPair by viewModel.selectedPair.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val snapshotBitmap by viewModel.latestSnapshotBitmap.collectAsState()
    val pairs by viewModel.pairs.collectAsState()

    val candles = remember(selectedPair, selectedTimeframe, pairs) {
        viewModel.getCandlesForSelectedPair()
    }

    LaunchedEffect(selectedPair, selectedTimeframe) {
        if (aiReport == null) {
            viewModel.runAiTechnicalAnalysis(isSnapshot = snapshotBitmap != null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (snapshotBitmap != null) "Snapshot Confirmation Panel" else "AI Technical Breakdown",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { viewModel.runAiTechnicalAnalysis(isSnapshot = snapshotBitmap != null) },
                modifier = Modifier.testTag("refresh_ai_analysis")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Analysis", tint = Color(0xFF38BDF8))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Captured Snapshot Header Card if available
        if (snapshotBitmap != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Captured Chart Snapshot for Manual Confirmation",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSnapshot() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Snapshot",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Image(
                        bitmap = snapshotBitmap!!.asImageBitmap(),
                        contentDescription = "Chart Snapshot Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Symbol: ${selectedPair.symbol} (${selectedTimeframe.label})",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Manual Confirmation Mode Active",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            MarketSentimentCard(
                pair = selectedPair,
                candles = candles,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (snapshotBitmap != null) "Analyzing captured chart snapshot for manual confirmation..." else "Analyzing market structure & indicator confluence...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (aiReport != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = aiReport ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
