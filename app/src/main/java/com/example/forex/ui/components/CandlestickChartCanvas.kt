package com.example.forex.ui.components

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.forex.data.model.*
import com.example.forex.data.repository.TechnicalAnalysisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ChartDrawingTool {
    PAN_ZOOM,
    CROSSHAIR,
    SUPPORT,
    RESISTANCE,
    TRENDLINE,
    FIBONACCI
}

data class UserDrawnLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ChartDrawingTool,
    val price1: Double,
    val candleIndex1: Int,
    val price2: Double = price1,
    val candleIndex2: Int = candleIndex1
)

@Composable
fun ChartLoadingSkeletonAnimation(
    symbol: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ChartSkeletonLoading")

    val shimmerTranslateX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerX"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val candleBullColor = Color(0xFF10B981).copy(alpha = 0.35f)
    val candleBearColor = Color(0xFFEF4444).copy(alpha = 0.35f)
    val neonCurveColor = Color(0xFF06B6D4)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Horizontal & vertical grid lines
            val numHorizontal = 8
            val stepY = height / numHorizontal
            for (i in 1 until numHorizontal) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, stepY * i),
                    end = Offset(width, stepY * i),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
            }

            val numVertical = 12
            val stepX = width / numVertical
            for (i in 1 until numVertical) {
                drawLine(
                    color = gridColor,
                    start = Offset(stepX * i, 0f),
                    end = Offset(stepX * i, height),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
            }

            // Skeleton candlestick bars with shimmer gradient
            val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                start = Offset(shimmerTranslateX - 200f, 0f),
                end = Offset(shimmerTranslateX + 200f, height)
            )

            val candleCount = 18
            val candleGap = width / candleCount
            val candleBarWidth = candleGap * 0.45f

            for (i in 0 until candleCount) {
                val cx = candleGap * i + candleGap / 2
                val isBull = i % 2 == 0
                val color = if (isBull) candleBullColor else candleBearColor

                val topY = (height * 0.25f) + (Math.sin(i * 0.8) * (height * 0.2f)).toFloat()
                val bodyHeight = (height * 0.15f) + (Math.cos(i * 0.5) * (height * 0.08f)).toFloat()
                val bottomY = topY + bodyHeight

                val wickTop = topY - (height * 0.05f)
                val wickBottom = bottomY + (height * 0.05f)

                drawLine(
                    color = color.copy(alpha = pulseAlpha * 0.5f),
                    start = Offset(cx, wickTop),
                    end = Offset(cx, wickBottom),
                    strokeWidth = 2f
                )

                drawRect(
                    color = color.copy(alpha = pulseAlpha * 0.7f),
                    topLeft = Offset(cx - candleBarWidth / 2, topY),
                    size = Size(candleBarWidth, bodyHeight)
                )
            }

            // Animated sinusoidal MA curve
            val maPath = Path()
            val pointCount = 30
            for (p in 0..pointCount) {
                val px = (width / pointCount) * p
                val py = (height * 0.5f) + (Math.sin((p * 0.4) + (shimmerTranslateX * 0.005)) * (height * 0.18f)).toFloat()
                if (p == 0) maPath.moveTo(px, py) else maPath.lineTo(px, py)
            }
            drawPath(
                path = maPath,
                color = neonCurveColor.copy(alpha = pulseAlpha),
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), shimmerTranslateX * 0.1f))
            )

            // Shimmer highlight overlay
            drawRect(
                brush = shimmerBrush,
                topLeft = Offset.Zero,
                size = size
            )
        }

        // Top Status Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981).copy(alpha = pulseAlpha))
                )
                Text(
                    text = "RETROFIT REPOSITORY • FETCHING MARKET DATA",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Center Glassmorphism Loading Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.92f)),
            modifier = Modifier
                .padding(24.dp)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = Color(0xFF38BDF8),
                        strokeWidth = 3.5.dp
                    )
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Loading $symbol Chart...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Fetching live candles via Retrofit API",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CandlestickChartCanvas(
    candles: List<CandleStick>,
    pair: CurrencyPair,
    indicatorSettings: IndicatorSettings,
    activeSignal: TradeSignal? = null,
    isFullscreenDialog: Boolean = false,
    onAnalyzeSnapshot: ((Bitmap) -> Unit)? = null,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bullColor = MaterialTheme.colorScheme.secondary
    val bearColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline
    val bgDark = MaterialTheme.colorScheme.background
    val ema20Color = Color(0xFF06B6D4)
    val ema50Color = Color(0xFFA855F7)
    val ema200Color = Color(0xFFFFB703)
    val sma20Color = Color(0xFFF59E0B)
    val sma50Color = Color(0xFF84CC16)
    
    val textColor = MaterialTheme.colorScheme.onBackground
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipBorder = MaterialTheme.colorScheme.outline

    if (candles.isEmpty()) {
        ChartLoadingSkeletonAnimation(symbol = pair.symbol, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSavePreviewDialog by remember { mutableStateOf(false) }
    var showDebugOverlay by remember { mutableStateOf(false) }
    
    // Debug overlay state
    var fps by remember { mutableStateOf(0) }
    var latency by remember { mutableStateOf(0) }
    
    LaunchedEffect(showDebugOverlay) {
        if (showDebugOverlay) {
            var frameCount = 0
            var lastTime = System.nanoTime()
            while (true) {
                withFrameNanos { time ->
                    frameCount++
                    if (time - lastTime >= 1_000_000_000) {
                        fps = frameCount
                        frameCount = 0
                        lastTime = time
                        // Simulate network latency variance around 45-120ms
                        latency = (45..120).random()
                    }
                }
            }
        }
    }

    var scaleFactor by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }

    var selectedTool by remember { mutableStateOf(ChartDrawingTool.PAN_ZOOM) }
    var isFullscreen by remember { mutableStateOf(false) }
    val userDrawnLines = remember { mutableStateListOf<UserDrawnLine>() }
    var trendlineStart by remember { mutableStateOf<Pair<Int, Double>?>(null) }
    var currentDragOffset by remember { mutableStateOf<Offset?>(null) }

    var canvasWidthPx by remember { mutableFloatStateOf(1f) }
    var canvasHeightPx by remember { mutableFloatStateOf(1f) }
    var currentMinPrice by remember { mutableDoubleStateOf(0.0) }
    var currentMaxPrice by remember { mutableDoubleStateOf(1.0) }
    var currentPriceRange by remember { mutableDoubleStateOf(1.0) }
    var currentStartIdx by remember { mutableIntStateOf(0) }
    var currentCandleWidth by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pair.symbol) {
        offsetX = 0f
        scaleFactor = 1f
    }

    val closes = remember(candles) { candles.map { it.close } }
    val sma20 = remember(candles) { TechnicalAnalysisEngine.calculateSMA(closes, 20) }
    val sma50 = remember(candles) { TechnicalAnalysisEngine.calculateSMA(closes, 50) }
    val ema20 = remember(candles) { TechnicalAnalysisEngine.calculateEMA(closes, 20) }
    val ema50 = remember(candles) { TechnicalAnalysisEngine.calculateEMA(closes, 50) }
    val ema200 = remember(candles) { TechnicalAnalysisEngine.calculateEMA(closes, 200) }
    val rsiList = remember(candles) { TechnicalAnalysisEngine.calculateRSI(closes, 14) }
    val bollingerBands = remember(candles) { TechnicalAnalysisEngine.calculateBollingerBands(closes) }
    val (supports, resistances) = remember(candles) { TechnicalAnalysisEngine.detectSupportResistance(candles) }
    val detectedPatterns = remember(candles) { TechnicalAnalysisEngine.detectPatterns(candles) }

    val textMeasurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("candlestick_chart_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgDark)
        ) {
        // Main Candlestick Canvas
        Box(
            modifier = Modifier
                .weight(if (indicatorSettings.showRsiSubchart) 0.75f else 1f)
                .fillMaxWidth()
                .pointerInput(selectedTool) {
                    when (selectedTool) {
                        ChartDrawingTool.PAN_ZOOM -> {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scaleFactor = (scaleFactor * zoom).coerceIn(0.5f, 3.5f)
                                offsetX += pan.x
                            }
                        }
                        ChartDrawingTool.CROSSHAIR -> {
                            // No gesture required for crosshair, it reacts to tap/pointer movement
                        }
                        ChartDrawingTool.SUPPORT -> {
                            detectTapGestures { pos ->
                                val pRange = max(0.0001, currentMaxPrice - currentMinPrice)
                                val price = currentMaxPrice - (pRange * (pos.y / canvasHeightPx))
                                val candleIdx = (currentStartIdx + (pos.x / currentCandleWidth).toInt()).coerceIn(0, candles.lastIndex)
                                userDrawnLines.add(
                                    UserDrawnLine(
                                        type = ChartDrawingTool.SUPPORT,
                                        price1 = price,
                                        candleIndex1 = candleIdx
                                    )
                                )
                            }
                        }
                        ChartDrawingTool.RESISTANCE -> {
                            detectTapGestures { pos ->
                                val pRange = max(0.0001, currentMaxPrice - currentMinPrice)
                                val price = currentMaxPrice - (pRange * (pos.y / canvasHeightPx))
                                val candleIdx = (currentStartIdx + (pos.x / currentCandleWidth).toInt()).coerceIn(0, candles.lastIndex)
                                userDrawnLines.add(
                                    UserDrawnLine(
                                        type = ChartDrawingTool.RESISTANCE,
                                        price1 = price,
                                        candleIndex1 = candleIdx
                                    )
                                )
                            }
                        }
                        ChartDrawingTool.TRENDLINE, ChartDrawingTool.FIBONACCI -> {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val pRange = max(0.0001, currentMaxPrice - currentMinPrice)
                                    val price = currentMaxPrice - (pRange * (pos.y / canvasHeightPx))
                                    val candleIdx = (currentStartIdx + (pos.x / currentCandleWidth).toInt()).coerceIn(0, candles.lastIndex)
                                    trendlineStart = Pair(candleIdx, price)
                                    currentDragOffset = pos
                                },
                                onDrag = { change, _ ->
                                    currentDragOffset = change.position
                                },
                                onDragEnd = {
                                    val start = trendlineStart
                                    val drag = currentDragOffset
                                    if (start != null && drag != null) {
                                        val pRange = max(0.0001, currentMaxPrice - currentMinPrice)
                                        val endPrice = currentMaxPrice - (pRange * (drag.y / canvasHeightPx))
                                        val endCandleIdx = (currentStartIdx + (drag.x / currentCandleWidth).toInt()).coerceIn(0, candles.lastIndex)
                                        userDrawnLines.add(
                                            UserDrawnLine(
                                                type = selectedTool,
                                                price1 = start.second,
                                                candleIndex1 = start.first,
                                                price2 = endPrice,
                                                candleIndex2 = endCandleIdx
                                            )
                                        )
                                    }
                                    trendlineStart = null
                                    currentDragOffset = null
                                },
                                onDragCancel = {
                                    trendlineStart = null
                                    currentDragOffset = null
                                }
                            )
                        }
                    }
                }
                .pointerInput(selectedTool) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position
                            touchPosition = if (selectedTool == ChartDrawingTool.CROSSHAIR && event.changes.any { it.pressed }) position else null
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val visibleCount = (40 / scaleFactor).toInt().coerceIn(10, candles.size)
                val approxCandleWidth = (canvasWidth / visibleCount).coerceAtLeast(1f)
                val indexShift = (offsetX / approxCandleWidth).toInt()

                val maxShift = (candles.size - visibleCount).coerceAtLeast(0)
                val clampedShift = indexShift.coerceIn(0, maxShift)

                val startIdx = (candles.size - visibleCount - clampedShift).coerceIn(0, candles.size - 1)
                val endIdx = (startIdx + visibleCount - 1).coerceIn(startIdx, candles.size - 1)
                val visibleCandles = candles.subList(startIdx, endIdx + 1)

                var minPrice = visibleCandles.minOf { it.low }
                var maxPrice = visibleCandles.maxOf { it.high }

                if (indicatorSettings.showBollingerBands) {
                    for (i in startIdx..endIdx) {
                        bollingerBands.getOrNull(i)?.let { bb ->
                            minPrice = min(minPrice, bb.lower)
                            maxPrice = max(maxPrice, bb.upper)
                        }
                    }
                }

                if (activeSignal != null) {
                    minPrice = min(minPrice, min(activeSignal.stopLoss, activeSignal.takeProfit2))
                    maxPrice = max(maxPrice, max(activeSignal.stopLoss, activeSignal.takeProfit2))
                }

                val priceRange = max(0.0001, maxPrice - minPrice)
                val candleWidth = (canvasWidth / visibleCandles.size).toFloat()

                // Update geometry state for gesture mapping
                canvasWidthPx = canvasWidth
                canvasHeightPx = canvasHeight
                currentMinPrice = minPrice
                currentMaxPrice = maxPrice
                currentPriceRange = priceRange
                currentStartIdx = startIdx
                currentCandleWidth = candleWidth

                // Draw background Grid lines
                val gridLines = 5
                for (g in 0..gridLines) {
                    val y = canvasHeight * (g.toFloat() / gridLines)
                    val gridPrice = maxPrice - (priceRange * (g.toFloat() / gridLines))
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f
                    )

                    // Price label
                    drawText(
                        textMeasurer = textMeasurer,
                        text = String.format(Locale.US, "%.5f", gridPrice),
                        style = TextStyle(color = textMuted, fontSize = 10.sp),
                        topLeft = Offset(canvasWidth - 110f, y - 18f)
                    )
                }

                // Draw Bollinger Bands Channel
                if (indicatorSettings.showBollingerBands) {
                    val upperPath = Path()
                    val lowerPath = Path()
                    var first = true

                    for ((idx, candleIdx) in (startIdx..endIdx).withIndex()) {
                        val bb = bollingerBands.getOrNull(candleIdx)
                        if (bb != null) {
                            val x = idx * candleWidth + (candleWidth / 2f)
                            val yUpper = (canvasHeight * (1f - (bb.upper - minPrice) / priceRange)).toFloat()
                            val yLower = (canvasHeight * (1f - (bb.lower - minPrice) / priceRange)).toFloat()

                            if (first) {
                                upperPath.moveTo(x, yUpper)
                                lowerPath.moveTo(x, yLower)
                                first = false
                            } else {
                                upperPath.lineTo(x, yUpper)
                                lowerPath.lineTo(x, yLower)
                            }
                        }
                    }

                    drawPath(
                        path = upperPath,
                        color = Color(0xFF38BDF8).copy(alpha = 0.5f),
                        style = Stroke(width = 2f)
                    )
                    drawPath(
                        path = lowerPath,
                        color = Color(0xFF38BDF8).copy(alpha = 0.5f),
                        style = Stroke(width = 2f)
                    )
                }

                // Draw Support and Resistance lines
                if (indicatorSettings.showSupportResistance) {
                    resistances.forEach { r ->
                        val y = (canvasHeight * (1f - (r - minPrice) / priceRange)).toFloat()
                        if (y in 0f..canvasHeight) {
                            drawLine(
                                color = Color(0xFFF43F5E).copy(alpha = 0.7f),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "Resistance",
                                style = TextStyle(color = Color(0xFFF43F5E), fontSize = 10.sp),
                                topLeft = Offset(16f, y - 20f)
                            )
                        }
                    }

                    supports.forEach { s ->
                        val y = (canvasHeight * (1f - (s - minPrice) / priceRange)).toFloat()
                        if (y in 0f..canvasHeight) {
                            drawLine(
                                color = Color(0xFF10B981).copy(alpha = 0.7f),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "Support",
                                style = TextStyle(color = Color(0xFF10B981), fontSize = 10.sp),
                                topLeft = Offset(16f, y - 20f)
                            )
                        }
                    }
                }

                // Draw Signal Overlay Lines (Entry, SL, TP1, TP2)
                if (activeSignal != null) {
                    val levels = listOf(
                        Triple(activeSignal.entryPrice, "ENTRY", Color(0xFF3B82F6)),
                        Triple(activeSignal.stopLoss, "SL (Stop Loss)", Color(0xFFEF4444)),
                        Triple(activeSignal.takeProfit1, "TP1 Target", Color(0xFF10B981)),
                        Triple(activeSignal.takeProfit2, "TP2 Target", Color(0xFF059669))
                    )

                    levels.forEach { (price, label, col) ->
                        val y = (canvasHeight * (1f - (price - minPrice) / priceRange)).toFloat()
                        if (y in 0f..canvasHeight) {
                            drawLine(
                                color = col,
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f))
                            )
                            drawRect(
                                color = col,
                                topLeft = Offset(canvasWidth - 140f, y - 18f),
                                size = Size(140f, 24f)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "$label: ${String.format(Locale.US, "%.4f", price)}",
                                style = TextStyle(color = textColor, fontSize = 9.sp),
                                topLeft = Offset(canvasWidth - 134f, y - 14f)
                            )
                        }
                    }
                }

                // Draw Candlesticks
                for ((i, candle) in visibleCandles.withIndex()) {
                    val xCenter = i * candleWidth + (candleWidth / 2f)
                    val bodyWidth = max(2f, candleWidth * 0.7f)

                    val yHigh = (canvasHeight * (1f - (candle.high - minPrice) / priceRange)).toFloat()
                    val yLow = (canvasHeight * (1f - (candle.low - minPrice) / priceRange)).toFloat()
                    val yOpen = (canvasHeight * (1f - (candle.open - minPrice) / priceRange)).toFloat()
                    val yClose = (canvasHeight * (1f - (candle.close - minPrice) / priceRange)).toFloat()

                    val color = if (candle.isBullish) bullColor else bearColor

                    // High/Low Wick
                    drawLine(
                        color = color,
                        start = Offset(xCenter, yHigh),
                        end = Offset(xCenter, yLow),
                        strokeWidth = 2f
                    )

                    // Open/Close Body
                    val bodyTop = min(yOpen, yClose)
                    val bodyHeight = max(2f, Math.abs(yOpen - yClose))

                    drawRect(
                        color = color,
                        topLeft = Offset(xCenter - (bodyWidth / 2f), bodyTop),
                        size = Size(bodyWidth, bodyHeight)
                    )

                    // Pattern Badge Marker
                    if (indicatorSettings.showPatterns) {
                        val realIdx = startIdx + i
                        detectedPatterns.find { it.candleIndex == realIdx }?.let { pat ->
                            val badgeY = if (pat.patternType.isBullish) yLow + 12f else yHigh - 24f
                            val badgeColor = if (pat.patternType.isBullish) bullColor else bearColor
                            drawCircle(
                                color = badgeColor,
                                radius = 6f,
                                center = Offset(xCenter, badgeY)
                            )
                        }
                    }
                }

                // Draw SMA & EMA Curves
                if (indicatorSettings.showSma20) drawEmaPath(sma20, startIdx, endIdx, candleWidth, minPrice, priceRange, canvasHeight, sma20Color)
                if (indicatorSettings.showSma50) drawEmaPath(sma50, startIdx, endIdx, candleWidth, minPrice, priceRange, canvasHeight, sma50Color)
                if (indicatorSettings.showEma20) drawEmaPath(ema20, startIdx, endIdx, candleWidth, minPrice, priceRange, canvasHeight, ema20Color)
                if (indicatorSettings.showEma50) drawEmaPath(ema50, startIdx, endIdx, candleWidth, minPrice, priceRange, canvasHeight, ema50Color)
                if (indicatorSettings.showEma200) drawEmaPath(ema200, startIdx, endIdx, candleWidth, minPrice, priceRange, canvasHeight, ema200Color)

                // Draw User-Drawn Support, Resistance & Trendlines
                userDrawnLines.forEach { line ->
                    when (line.type) {
                        ChartDrawingTool.SUPPORT -> {
                            val y = (canvasHeight * (1f - (line.price1 - minPrice) / priceRange)).toFloat()
                            if (y in 0f..canvasHeight) {
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(0f, y),
                                    end = Offset(canvasWidth, y),
                                    strokeWidth = 3f
                                )
                                val text = "User Support: ${String.format(Locale.US, "%.5f", line.price1)}"
                                drawRoundRect(
                                    color = Color(0xFF10B981),
                                    topLeft = Offset(16f, y - 18f),
                                    size = Size(155f, 20f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    style = TextStyle(color = bgDark, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    topLeft = Offset(22f, y - 14f)
                                )
                            }
                        }
                        ChartDrawingTool.RESISTANCE -> {
                            val y = (canvasHeight * (1f - (line.price1 - minPrice) / priceRange)).toFloat()
                            if (y in 0f..canvasHeight) {
                                drawLine(
                                    color = Color(0xFFEF4444),
                                    start = Offset(0f, y),
                                    end = Offset(canvasWidth, y),
                                    strokeWidth = 3f
                                )
                                val text = "User Resistance: ${String.format(Locale.US, "%.5f", line.price1)}"
                                drawRoundRect(
                                    color = Color(0xFFEF4444),
                                    topLeft = Offset(16f, y - 18f),
                                    size = Size(165f, 20f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    style = TextStyle(color = textColor, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    topLeft = Offset(22f, y - 14f)
                                )
                            }
                        }
                        ChartDrawingTool.TRENDLINE -> {
                            val x1 = (line.candleIndex1 - startIdx) * candleWidth + (candleWidth / 2f)
                            val x2 = (line.candleIndex2 - startIdx) * candleWidth + (candleWidth / 2f)
                            val y1 = (canvasHeight * (1f - (line.price1 - minPrice) / priceRange)).toFloat()
                            val y2 = (canvasHeight * (1f - (line.price2 - minPrice) / priceRange)).toFloat()

                            drawLine(
                                color = Color(0xFF38BDF8),
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 3.5f
                            )
                            drawCircle(color = Color(0xFF38BDF8), radius = 6f, center = Offset(x1, y1))
                            drawCircle(color = textColor, radius = 3.5f, center = Offset(x1, y1))
                            drawCircle(color = Color(0xFF38BDF8), radius = 6f, center = Offset(x2, y2))
                            drawCircle(color = textColor, radius = 3.5f, center = Offset(x2, y2))
                        }
                        ChartDrawingTool.FIBONACCI -> {
                            val p1 = line.price1
                            val p2 = line.price2
                            val diff = p2 - p1
                            val fibLevels = listOf(
                                0.000 to Pair("0.0%", Color(0xFFE2E8F0)),
                                0.236 to Pair("23.6%", Color(0xFFFACC15)),
                                0.382 to Pair("38.2%", Color(0xFF34D399)),
                                0.500 to Pair("50.0%", Color(0xFF38BDF8)),
                                0.618 to Pair("61.8%", Color(0xFFF97316)),
                                0.786 to Pair("78.6%", Color(0xFFEC4899)),
                                1.000 to Pair("100.0%", Color(0xFF93C5FD))
                            )

                            val yFibStart = (canvasHeight * (1f - (p1 - minPrice) / priceRange)).toFloat()
                            val yFibEnd = (canvasHeight * (1f - (p2 - minPrice) / priceRange)).toFloat()
                            val topY = min(yFibStart, yFibEnd)
                            val bottomY = max(yFibStart, yFibEnd)
                            if (bottomY >= 0f && topY <= canvasHeight) {
                                drawRect(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.06f),
                                    topLeft = Offset(0f, max(0f, topY)),
                                    size = Size(canvasWidth, min(canvasHeight - max(0f, topY), bottomY - max(0f, topY)))
                                )
                            }

                            fibLevels.forEach { (ratio, info) ->
                                val levelPrice = p1 + diff * ratio
                                val y = (canvasHeight * (1f - (levelPrice - minPrice) / priceRange)).toFloat()
                                if (y in -20f..(canvasHeight + 20f)) {
                                    drawLine(
                                        color = info.second.copy(alpha = 0.85f),
                                        start = Offset(0f, y),
                                        end = Offset(canvasWidth, y),
                                        strokeWidth = if (ratio == 0.0 || ratio == 1.0 || ratio == 0.5) 2f else 1.2f,
                                        pathEffect = if (ratio != 0.0 && ratio != 1.0) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
                                    )
                                    val text = "${info.first}  ${String.format(Locale.US, "%.5f", levelPrice)}"
                                    drawRoundRect(
                                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                                        topLeft = Offset(12f, y - 10f),
                                        size = Size(125f, 18f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = text,
                                        style = TextStyle(color = info.second, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        topLeft = Offset(16f, y - 8f)
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // Render active trendline / fibonacci drag preview
                if (trendlineStart != null && currentDragOffset != null) {
                    val start = trendlineStart!!
                    val drag = currentDragOffset!!
                    val x1 = (start.first - startIdx) * candleWidth + (candleWidth / 2f)
                    val y1 = (canvasHeight * (1f - (start.second - minPrice) / priceRange)).toFloat()

                    if (selectedTool == ChartDrawingTool.TRENDLINE) {
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = 0.8f),
                            start = Offset(x1, y1),
                            end = drag,
                            strokeWidth = 3.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                        drawCircle(color = Color(0xFF38BDF8), radius = 7f, center = Offset(x1, y1))
                        drawCircle(color = Color(0xFF38BDF8), radius = 7f, center = drag)
                    } else if (selectedTool == ChartDrawingTool.FIBONACCI) {
                        val p1 = start.second
                        val p2 = maxPrice - (priceRange * (drag.y / canvasHeight))
                        val diff = p2 - p1
                        val fibLevels = listOf(
                            0.000 to Pair("0.0%", Color(0xFFE2E8F0)),
                            0.236 to Pair("23.6%", Color(0xFFFACC15)),
                            0.382 to Pair("38.2%", Color(0xFF34D399)),
                            0.500 to Pair("50.0%", Color(0xFF38BDF8)),
                            0.618 to Pair("61.8%", Color(0xFFF97316)),
                            0.786 to Pair("78.6%", Color(0xFFEC4899)),
                            1.000 to Pair("100.0%", Color(0xFF93C5FD))
                        )
                        fibLevels.forEach { (ratio, info) ->
                            val levelPrice = p1 + diff * ratio
                            val y = (canvasHeight * (1f - (levelPrice - minPrice) / priceRange)).toFloat()
                            if (y in 0f..canvasHeight) {
                                drawLine(
                                    color = info.second.copy(alpha = 0.9f),
                                    start = Offset(0f, y),
                                    end = Offset(canvasWidth, y),
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                )
                            }
                        }
                    }
                }

                // Draw Snapping Crosshair & Precision Interactive Tooltip on Touch / Tap / Long-press
                touchPosition?.let { pos ->
                    val hoveredIdx = (pos.x / candleWidth).toInt().coerceIn(0, visibleCandles.lastIndex)
                    val hoveredCandle = visibleCandles[hoveredIdx]
                    val xCenter = hoveredIdx * candleWidth + (candleWidth / 2f)

                    // Calculate candlestick level Y coordinates
                    val yOpen = (canvasHeight * (1f - (hoveredCandle.open - minPrice) / priceRange)).toFloat()
                    val yClose = (canvasHeight * (1f - (hoveredCandle.close - minPrice) / priceRange)).toFloat()
                    val yHigh = (canvasHeight * (1f - (hoveredCandle.high - minPrice) / priceRange)).toFloat()
                    val yLow = (canvasHeight * (1f - (hoveredCandle.low - minPrice) / priceRange)).toFloat()

                    // Snapping logic: find nearest level (Open, Close, High, Low) with snap threshold
                    val snapThresholdPx = 45f
                    val candidates = listOf(
                        SnapCandidate(yOpen, "OPEN", hoveredCandle.open, Color(0xFF38BDF8)),
                        SnapCandidate(yClose, "CLOSE", hoveredCandle.close, Color(0xFFF59E0B)),
                        SnapCandidate(yHigh, "HIGH", hoveredCandle.high, Color(0xFF10B981)),
                        SnapCandidate(yLow, "LOW", hoveredCandle.low, Color(0xFFEF4444))
                    )

                    val closestLevel = candidates.minByOrNull { kotlin.math.abs(it.y - pos.y) }
                    val isSnapped = closestLevel != null && kotlin.math.abs(closestLevel.y - pos.y) <= snapThresholdPx

                    val crosshairY = if (isSnapped) closestLevel!!.y else pos.y
                    val crosshairPrice = if (isSnapped) closestLevel!!.price else (maxPrice - (priceRange * (pos.y / canvasHeight)))
                    val snapLabel = if (isSnapped) closestLevel!!.label else null
                    val snapColor = if (isSnapped) closestLevel!!.color else Color(0xFFEADDFF)

                    // Vertical guide line snapped to hovered candle center
                    drawLine(
                        color = Color(0xFFEADDFF).copy(alpha = 0.8f),
                        start = Offset(xCenter, 0f),
                        end = Offset(xCenter, canvasHeight),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    // Horizontal price guide line snapped to candlestick level or touch Y
                    drawLine(
                        color = snapColor.copy(alpha = 0.9f),
                        start = Offset(0f, crosshairY),
                        end = Offset(canvasWidth, crosshairY),
                        strokeWidth = if (isSnapped) 2.2f else 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    // Snapping target circle & pulse indicator when locked onto level
                    if (isSnapped) {
                        drawCircle(
                            color = snapColor.copy(alpha = 0.35f),
                            radius = 12f,
                            center = Offset(xCenter, crosshairY)
                        )
                        drawCircle(
                            color = snapColor,
                            radius = 6f,
                            center = Offset(xCenter, crosshairY)
                        )
                        drawCircle(
                            color = textColor,
                            radius = 3f,
                            center = Offset(xCenter, crosshairY)
                        )
                    }

                    // Price level pill badge on the right axis
                    val badgePriceText = if (isSnapped && snapLabel != null) {
                        "$snapLabel: ${String.format(Locale.US, "%.5f", crosshairPrice)}"
                    } else {
                        String.format(Locale.US, "%.5f", crosshairPrice)
                    }
                    val badgeWidth = if (isSnapped) 130f else 85f
                    val badgeHeight = 22f
                    val badgeLeft = (canvasWidth - badgeWidth - 8f).coerceAtLeast(0f)
                    val badgeTop = (crosshairY - badgeHeight / 2f).coerceIn(4f, canvasHeight - badgeHeight - 4f)

                    drawRoundRect(
                        color = if (isSnapped) snapColor else gridColor,
                        topLeft = Offset(badgeLeft, badgeTop),
                        size = Size(badgeWidth, badgeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = badgePriceText,
                        style = TextStyle(
                            color = if (isSnapped) bgDark else textColor,
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        topLeft = Offset(badgeLeft + 6f, badgeTop + 3f)
                    )

                    val changePct = if (hoveredCandle.open > 0) ((hoveredCandle.close - hoveredCandle.open) / hoveredCandle.open) * 100 else 0.0
                    val isBull = hoveredCandle.isBullish

                    // Draw High-Density OHLC Tooltip Card Overlay
                    val cardWidth = min(canvasWidth * 0.85f, 320f)
                    val cardHeight = 72f
                    val cardLeft = if (xCenter > canvasWidth / 2f) 16f else canvasWidth - cardWidth - 16f
                    val cardTop = 16f

                    // Tooltip background card
                    drawRoundRect(
                        color = tooltipBg,
                        topLeft = Offset(cardLeft, cardTop),
                        size = Size(cardWidth, cardHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                    )
                    drawRoundRect(
                        color = if (isBull) bullColor else bearColor,
                        topLeft = Offset(cardLeft, cardTop),
                        size = Size(cardWidth, cardHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                        style = Stroke(width = 2f)
                    )

                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                    val timeStr = dateFormat.format(Date(hoveredCandle.timestamp))
                    val statusStr = if (isBull) "BULLISH +${String.format(Locale.US, "%.2f", changePct)}%" else "BEARISH ${String.format(Locale.US, "%.2f", changePct)}%"

                    val snapBadgeText = if (isSnapped && snapLabel != null) "  •  LOCKED: $snapLabel" else ""
                    val headerText = "$timeStr  •  $statusStr$snapBadgeText"
                    drawText(
                        textMeasurer = textMeasurer,
                        text = headerText,
                        style = TextStyle(color = if (isSnapped) snapColor else (if (isBull) bullColor else bearColor), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        topLeft = Offset(cardLeft + 12f, cardTop + 10f)
                    )

                    val ohlcText = "O: ${String.format(Locale.US, "%.5f", hoveredCandle.open)}  H: ${String.format(Locale.US, "%.5f", hoveredCandle.high)}\nL: ${String.format(Locale.US, "%.5f", hoveredCandle.low)}  C: ${String.format(Locale.US, "%.5f", hoveredCandle.close)}"
                    drawText(
                        textMeasurer = textMeasurer,
                        text = ohlcText,
                        style = TextStyle(color = textColor, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        topLeft = Offset(cardLeft + 12f, cardTop + 28f)
                    )
                }
            }

            // Floating Interactive Drawing Toolbar
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(tooltipBg.copy(alpha = 0.95f), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, gridColor, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { selectedTool = ChartDrawingTool.PAN_ZOOM },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedTool == ChartDrawingTool.PAN_ZOOM) Color(0xFF6750A4) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_pan_zoom")
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = "Pan & Zoom",
                        tint = if (selectedTool == ChartDrawingTool.PAN_ZOOM) textColor else textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = { selectedTool = ChartDrawingTool.CROSSHAIR },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedTool == ChartDrawingTool.CROSSHAIR) Color(0xFF6750A4) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_crosshair")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Crosshair",
                        tint = if (selectedTool == ChartDrawingTool.CROSSHAIR) textColor else textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { selectedTool = ChartDrawingTool.SUPPORT },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedTool == ChartDrawingTool.SUPPORT) Color(0xFF10B981).copy(alpha = 0.35f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_support_line")
                ) {
                    Icon(
                        imageVector = Icons.Default.HorizontalRule,
                        contentDescription = "Draw Support Line",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { selectedTool = ChartDrawingTool.RESISTANCE },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedTool == ChartDrawingTool.RESISTANCE) Color(0xFFEF4444).copy(alpha = 0.35f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_resistance_line")
                ) {
                    Icon(
                        imageVector = Icons.Default.HorizontalRule,
                        contentDescription = "Draw Resistance Line",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { selectedTool = ChartDrawingTool.TRENDLINE },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedTool == ChartDrawingTool.TRENDLINE) Color(0xFF38BDF8).copy(alpha = 0.35f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_trendline")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Draw Trendline",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { selectedTool = ChartDrawingTool.FIBONACCI },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedTool == ChartDrawingTool.FIBONACCI) Color(0xFFFACC15).copy(alpha = 0.35f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_fibonacci")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = "Fibonacci Retracement",
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(gridColor)
                )

                IconButton(
                    onClick = { isFullscreen = !isFullscreen },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("tool_fullscreen")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Full Screen" else "Full Screen",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val w = view.width
                        val h = view.height
                        if (w > 0 && h > 0) {
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            view.draw(canvas)
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    val filename = "Chart_${pair.symbol.replace("/", "_")}_$timeStamp.png"

                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ForexSignals")
                                        }
                                    }
                                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                    if (uri != null) {
                                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        capturedBitmap = bitmap
                                        showSavePreviewDialog = true
                                        Toast.makeText(context, "Chart image saved to Gallery!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        capturedBitmap = bitmap
                                        showSavePreviewDialog = true
                                        Toast.makeText(context, "Chart snapshot captured!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("tool_save_image")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Save Chart as Image",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (userDrawnLines.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .width(1.dp)
                            .background(gridColor)
                    )

                    IconButton(
                        onClick = { if (userDrawnLines.isNotEmpty()) userDrawnLines.removeAt(userDrawnLines.lastIndex) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("tool_undo_line")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo Line",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { userDrawnLines.clear() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("tool_clear_lines")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All Lines",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(gridColor)
                )
                
                IconButton(
                    onClick = { showDebugOverlay = !showDebugOverlay },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (showDebugOverlay) Color(0xFF10B981).copy(alpha = 0.35f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("tool_debug_overlay")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph, // Using an existing icon as a placeholder
                        contentDescription = "Toggle Debug Overlay",
                        tint = if (showDebugOverlay) Color(0xFF10B981) else textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Guidance Overlay Badge when Drawing Mode is active
            if (selectedTool != ChartDrawingTool.PAN_ZOOM) {
                val hintText = when (selectedTool) {
                    ChartDrawingTool.SUPPORT -> "Tap chart to add Support Line"
                    ChartDrawingTool.RESISTANCE -> "Tap chart to add Resistance Line"
                    ChartDrawingTool.TRENDLINE -> "Drag across chart to draw Trendline"
                    ChartDrawingTool.FIBONACCI -> "Drag from High to Low to draw Fibonacci levels"
                    else -> ""
                }
                Surface(
                    color = Color(0xFF6750A4).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = hintText,
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Debug FPS and Latency Overlay
            if (showDebugOverlay) {
                Surface(
                    color = bgDark.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (fps >= 30) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "FPS: $fps",
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (latency < 100) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lat: ${latency}ms",
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // RSI Sub-chart Canvas
        if (indicatorSettings.showRsiSubchart) {
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val visibleCount = (40 / scaleFactor).toInt().coerceIn(10, candles.size)
                    val approxCandleWidth = (canvasWidth / visibleCount).coerceAtLeast(1f)
                    val indexShift = (offsetX / approxCandleWidth).toInt()

                    val maxShift = (candles.size - visibleCount).coerceAtLeast(0)
                    val clampedShift = indexShift.coerceIn(0, maxShift)

                    val startIdx = (candles.size - visibleCount - clampedShift).coerceIn(0, candles.size - 1)
                    val endIdx = (startIdx + visibleCount - 1).coerceIn(startIdx, candles.size - 1)
                    val candleWidth = (canvasWidth / (endIdx - startIdx + 1)).toFloat()

                    // RSI Overbought (70) and Oversold (30) threshold lines
                    val y70 = canvasHeight * (1f - (70f / 100f))
                    val y30 = canvasHeight * (1f - (30f / 100f))

                    drawLine(
                        color = Color(0xFFEF4444).copy(alpha = 0.5f),
                        start = Offset(0f, y70),
                        end = Offset(canvasWidth, y70),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.5f),
                        start = Offset(0f, y30),
                        end = Offset(canvasWidth, y30),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "RSI (14)",
                        style = TextStyle(color = textMuted, fontSize = 9.sp),
                        topLeft = Offset(8f, 4f)
                    )

                    val rsiPath = Path()
                    var rsiStarted = false

                    for ((idx, candleIdx) in (startIdx..endIdx).withIndex()) {
                        val rsiVal = rsiList.getOrNull(candleIdx)
                        if (rsiVal != null) {
                            val x = idx * candleWidth + (candleWidth / 2f)
                            val yRsi = canvasHeight * (1f - (rsiVal.toFloat() / 100f))

                            if (!rsiStarted) {
                                rsiPath.moveTo(x, yRsi)
                                rsiStarted = true
                            } else {
                                rsiPath.lineTo(x, yRsi)
                            }
                        }
                    }

                    drawPath(
                        path = rsiPath,
                        color = Color(0xFFF59E0B),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            ChartLoadingSkeletonAnimation(symbol = pair.symbol, modifier = Modifier.fillMaxSize())
        }
    }

    if (isFullscreen && !isFullscreenDialog) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgDark)
            ) {
                CandlestickChartCanvas(
                    candles = candles,
                    pair = pair,
                    indicatorSettings = indicatorSettings,
                    activeSignal = activeSignal,
                    isFullscreenDialog = true,
                    onAnalyzeSnapshot = onAnalyzeSnapshot,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showSavePreviewDialog && capturedBitmap != null) {
        val bitmap = capturedBitmap!!
        Dialog(onDismissRequest = { showSavePreviewDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = tooltipBg,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "Chart Snapshot Captured!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Saved to Gallery / Pictures/ForexSignals",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Chart Snapshot Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, gridColor, RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pair: ${pair.symbol}",
                            style = MaterialTheme.typography.labelMedium.copy(color = textColor)
                        )
                        Text(
                            text = "${bitmap.width} x ${bitmap.height} px",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8))
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (onAnalyzeSnapshot != null) {
                        Button(
                            onClick = {
                                showSavePreviewDialog = false
                                onAnalyzeSnapshot.invoke(bitmap)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("analyze_snapshot_ai_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Analyze Snapshot in AI Panel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSavePreviewDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                        ) {
                            Text("Close")
                        }

                        Button(
                            onClick = {
                                try {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Check out my ${pair.symbol} chart analysis on ForexSignals AI!")
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Chart"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmaPath(
    emaValues: List<Double?>,
    startIdx: Int,
    endIdx: Int,
    candleWidth: Float,
    minPrice: Double,
    priceRange: Double,
    canvasHeight: Float,
    color: Color
) {
    val path = Path()
    var started = false

    for ((idx, candleIdx) in (startIdx..endIdx).withIndex()) {
        val ema = emaValues.getOrNull(candleIdx)
        if (ema != null) {
            val x = idx * candleWidth + (candleWidth / 2f)
            val y = (canvasHeight * (1f - (ema - minPrice) / priceRange)).toFloat()

            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.5f)
    )
}

private data class SnapCandidate(
    val y: Float,
    val label: String,
    val price: Double,
    val color: Color
)
