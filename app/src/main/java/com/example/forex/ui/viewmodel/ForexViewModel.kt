package com.example.forex.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.forex.data.db.ForexDatabase
import com.example.forex.data.model.*
import com.example.forex.data.repository.ForexRepository
import com.example.forex.data.repository.TechnicalAnalysisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.round
import java.util.Locale
import com.example.forex.data.auth.ForexAuthManager
import com.example.forex.data.auth.GoogleAuthClient
import com.google.firebase.auth.FirebaseUser

class ForexViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ForexDatabase.getDatabase(application)
    private val repository = ForexRepository(db.forexDao())
    private val authManager = ForexAuthManager(application.applicationContext, db.forexDao())
    private val googleAuthClient = GoogleAuthClient(application.applicationContext)

    // User Authentication States
    val currentUser: StateFlow<UserProfile?> = authManager.activeUserProfileFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    fun signUpWithEmail(email: String, password: String, displayName: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.signUpWithEmail(email, password, displayName)
            _isAuthLoading.value = false
            if (result.success) {
                onResult(true)
            } else {
                _authErrorMessage.value = result.errorMessage
                onResult(false)
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.signInWithEmail(email, password)
            _isAuthLoading.value = false
            if (result.success) {
                onResult(true)
            } else {
                _authErrorMessage.value = result.errorMessage
                onResult(false)
            }
        }
    }

    fun signInWithGoogle(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.signInWithGoogle()
            _isAuthLoading.value = false
            if (result.success) {
                onResult(true)
            } else {
                _authErrorMessage.value = result.errorMessage
                onResult(false)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    val pairs: StateFlow<List<CurrencyPair>> = repository.pairs

    private val _selectedPair = MutableStateFlow(repository.pairs.value.first())
    val selectedPair: StateFlow<CurrencyPair> = _selectedPair.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.H1)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    private val _indicatorSettings = MutableStateFlow(IndicatorSettings())
    val indicatorSettings: StateFlow<IndicatorSettings> = _indicatorSettings.asStateFlow()

    fun updateIndicatorSettings(newSettings: IndicatorSettings) {
        _indicatorSettings.value = newSettings
        viewModelScope.launch {
            repository.saveIndicatorSettings(newSettings)
        }
        generateAllSignals(pairs.value)
    }

    private val _inspectedSignal = MutableStateFlow<TradeSignal?>(null)
    val inspectedSignal: StateFlow<TradeSignal?> = _inspectedSignal.asStateFlow()

    private val _liveSignals = MutableStateFlow<List<TradeSignal>>(emptyList())
    val liveSignals: StateFlow<List<TradeSignal>> = _liveSignals.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    // Room DB persistent flows
    val savedSignals = repository.savedSignals.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val customAlerts = repository.customAlerts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val watchlist = repository.watchlist.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun toggleWatchlist(symbol: String) {
        viewModelScope.launch {
            val current = watchlist.value
            if (current.any { it.symbol == symbol }) {
                repository.removeFromWatchlist(symbol)
            } else {
                repository.addToWatchlist(symbol)
            }
        }
    }

    // AI Analysis & Redraw State
    private val _aiReport = MutableStateFlow<String?>(null)
    val aiReport: StateFlow<String?> = _aiReport.asStateFlow()

    private val _isAnalyzingAi = MutableStateFlow(false)
    val isAnalyzingAi: StateFlow<Boolean> = _isAnalyzingAi.asStateFlow()

    private val _aiAutoRedrawEnabled = MutableStateFlow(true)
    val aiAutoRedrawEnabled: StateFlow<Boolean> = _aiAutoRedrawEnabled.asStateFlow()

    private val _showAiDrawingsOnChart = MutableStateFlow(true)
    val showAiDrawingsOnChart: StateFlow<Boolean> = _showAiDrawingsOnChart.asStateFlow()

    private val _aiChartOverlay = MutableStateFlow<AiChartOverlayState?>(null)
    val aiChartOverlay: StateFlow<AiChartOverlayState?> = _aiChartOverlay.asStateFlow()

    private val _aiRedrawStep = MutableStateFlow("")
    val aiRedrawStep: StateFlow<String> = _aiRedrawStep.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<AiChatMessage>>(
        listOf(
            AiChatMessage(
                sender = AiChatSender.AI,
                text = "👋 Hello Trader! I'm your institutional AI Market Analyst. I can perform in-depth technical analysis and dynamically **redraw key support/resistance zones, trendlines, and Fibonacci levels** directly onto your chart in real-time.",
                suggestedPrompts = listOf(
                    "🪄 Redraw Key Support & Resistance",
                    "📈 Draw Dynamic Trendlines",
                    "📐 Plot Fibonacci Retracement",
                    "🎯 Breakout & Target Levels (TP/SL)",
                    "🔍 Full Technical Breakdown"
                )
            )
        )
    )
    val aiChatMessages: StateFlow<List<AiChatMessage>> = _aiChatMessages.asStateFlow()

    fun toggleAiAutoRedraw(enabled: Boolean? = null) {
        _aiAutoRedrawEnabled.value = enabled ?: !_aiAutoRedrawEnabled.value
    }

    fun toggleShowAiDrawingsOnChart() {
        _showAiDrawingsOnChart.value = !_showAiDrawingsOnChart.value
    }

    fun clearAiChartDrawings() {
        _aiChartOverlay.value = null
    }

    private val _isChartLoading = MutableStateFlow(false)
    val isChartLoading: StateFlow<Boolean> = _isChartLoading.asStateFlow()

    private val _latestSnapshotBitmap = MutableStateFlow<Bitmap?>(null)
    val latestSnapshotBitmap: StateFlow<Bitmap?> = _latestSnapshotBitmap.asStateFlow()

    fun analyzeSnapshotWithAi(bitmap: Bitmap) {
        _latestSnapshotBitmap.value = bitmap
        runAiTechnicalAnalysis(isSnapshot = true)
    }

    fun clearSnapshot() {
        _latestSnapshotBitmap.value = null
    }

    // Real-time WebSocket Stream & Market Ticks
    val webSocketStats: StateFlow<WebSocketStats> = repository.webSocketStats
    val webSocketStatus: StateFlow<WebSocketStatus> = repository.webSocketStatus
    val marketTicks: SharedFlow<MarketTick> = repository.marketTicks

    private val _candleVersion = MutableStateFlow(0L)
    val candleVersion: StateFlow<Long> = _candleVersion.asStateFlow()

    private val _lastTick = MutableStateFlow<MarketTick?>(null)
    val lastTick: StateFlow<MarketTick?> = _lastTick.asStateFlow()

    init {
        // Load persisted indicator settings if previously customized
        viewModelScope.launch {
            repository.savedIndicatorSettings.collect { saved ->
                if (saved != null) {
                    _indicatorSettings.value = saved
                }
            }
        }

        // Pre-populate default favorites if watchlist is empty
        viewModelScope.launch {
            val initial = repository.watchlist.first()
            if (initial.isEmpty()) {
                repository.addToWatchlist("EUR/USD")
                repository.addToWatchlist("GBP/USD")
                repository.addToWatchlist("XAU/USD")
                repository.addToWatchlist("BTC/USD")
            }
        }

        // Collect real-time tick updates
        viewModelScope.launch(Dispatchers.Default) {
            repository.startLivePriceStream().collect { updatedPairs ->
                // Update selected pair if changed
                updatedPairs.find { it.symbol == _selectedPair.value.symbol }?.let {
                    _selectedPair.value = it
                }
                // Generate live signals across active pairs
                generateAllSignals(updatedPairs)
            }
        }

        // Observe market ticks to notify chart of real-time candlestick shape changes
        viewModelScope.launch(Dispatchers.Default) {
            repository.marketTicks.collect { tick ->
                if (tick.symbol == _selectedPair.value.symbol) {
                    _lastTick.value = tick
                    _candleVersion.value = System.currentTimeMillis()
                }
            }
        }
    }

    fun reconnectWebSocket() {
        repository.reconnectWebSocket()
    }

    fun disconnectWebSocket() {
        repository.disconnectWebSocket()
    }


    private fun generateAllSignals(currentPairs: List<CurrencyPair>) {
        val signals = currentPairs.map { pair ->
            val candles = repository.getCandles(pair.symbol, _selectedTimeframe.value)
            TechnicalAnalysisEngine.generateTradeSignal(pair, candles, _selectedTimeframe.value)
        }
        _liveSignals.value = signals
    }

    fun triggerChartLoading(durationMs: Long = 450) {
        viewModelScope.launch {
            _isChartLoading.value = true
            delay(durationMs)
            _isChartLoading.value = false
        }
    }

    fun selectPair(pair: CurrencyPair) {
        if (_selectedPair.value.symbol != pair.symbol) {
            _selectedPair.value = pair
            _inspectedSignal.value = null
            triggerChartLoading(400)
        }
    }

    fun selectTimeframe(tf: Timeframe) {
        if (_selectedTimeframe.value != tf) {
            _selectedTimeframe.value = tf
            triggerChartLoading(400)
        }
    }

    fun refreshChartData() {
        triggerChartLoading(600)
    }

    fun getCandlesForSelectedPair(): List<CandleStick> {
        return repository.getCandles(_selectedPair.value.symbol, _selectedTimeframe.value)
    }

    fun toggleIndicator(update: (IndicatorSettings) -> IndicatorSettings) {
        val updated = update(_indicatorSettings.value)
        _indicatorSettings.value = updated
        viewModelScope.launch {
            repository.saveIndicatorSettings(updated)
        }
        generateAllSignals(pairs.value)
    }

    fun setInspectedSignal(signal: TradeSignal?) {
        _inspectedSignal.value = signal
        if (signal != null) {
            val p = pairs.value.find { it.symbol == signal.pairSymbol }
            if (p != null) _selectedPair.value = p
            _selectedTimeframe.value = signal.timeframe
        }
    }

    fun saveSignal(signal: TradeSignal) {
        viewModelScope.launch {
            repository.saveSignal(signal)
        }
    }

    fun deleteSavedSignal(id: String) {
        viewModelScope.launch {
            repository.deleteSavedSignal(id)
        }
    }

    fun createAlert(pairSymbol: String, condition: AlertCondition, targetValue: Double, note: String) {
        viewModelScope.launch {
            val alert = CustomAlert(
                id = "alert_${System.currentTimeMillis()}",
                pairSymbol = pairSymbol,
                condition = condition,
                targetValue = targetValue,
                note = note
            )
            repository.createAlert(alert)
        }
    }

    fun toggleAlert(id: String, active: Boolean) {
        viewModelScope.launch {
            repository.toggleAlert(id, active)
        }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch {
            repository.deleteAlert(id)
        }
    }

    fun redrawChartWithAi(focusType: String? = null) {
        viewModelScope.launch {
            val pair = _selectedPair.value
            val tf = _selectedTimeframe.value
            val candles = getCandlesForSelectedPair()
            
            _aiRedrawStep.value = "Scanning historical high/low pivots..."
            _aiChartOverlay.value = (_aiChartOverlay.value ?: AiChartOverlayState(pair.symbol, tf)).copy(
                isRedrawing = true,
                redrawStep = "Scanning historical pivots..."
            )
            delay(280)

            _aiRedrawStep.value = "Calculating Demand/Supply zones & Fibonacci levels..."
            _aiChartOverlay.value = _aiChartOverlay.value?.copy(
                isRedrawing = true,
                redrawStep = "Calculating Fibonacci ratios & key zones..."
            )
            delay(280)

            _aiRedrawStep.value = "Plotting AI trendlines & target vectors on chart..."
            val newOverlay = TechnicalAnalysisEngine.generateAiChartOverlay(pair, candles, tf, focusType)
            _aiChartOverlay.value = newOverlay.copy(
                isRedrawing = false,
                redrawStep = "Chart redrawn successfully"
            )
            _showAiDrawingsOnChart.value = true
            _aiRedrawStep.value = ""
        }
    }

    fun sendAiChatMessage(userText: String, requestedRedrawType: String? = null) {
        if (userText.isBlank()) return
        
        val userMsg = AiChatMessage(
            sender = AiChatSender.USER,
            text = userText.trim()
        )
        _aiChatMessages.value = _aiChatMessages.value + userMsg

        viewModelScope.launch {
            _isAnalyzingAi.value = true
            val pair = _selectedPair.value
            val tf = _selectedTimeframe.value
            val candles = getCandlesForSelectedPair()
            val signal = TechnicalAnalysisEngine.generateTradeSignal(pair, candles, tf)
            val patterns = TechnicalAnalysisEngine.detectPatterns(candles)
            val patternNames = patterns.joinToString(", ") { it.patternType.title }.ifEmpty { "Consolidation" }

            var overlayResult: AiChartOverlayState? = null

            // If auto-redraw is enabled or prompt requests chart redraw
            val shouldRedraw = _aiAutoRedrawEnabled.value || 
                userText.contains("redraw", ignoreCase = true) || 
                userText.contains("draw", ignoreCase = true) || 
                userText.contains("zone", ignoreCase = true) || 
                userText.contains("trend", ignoreCase = true) || 
                userText.contains("fib", ignoreCase = true) || 
                userText.contains("target", ignoreCase = true)

            if (shouldRedraw) {
                _aiRedrawStep.value = "AI analyzing ${pair.symbol} structure..."
                _aiChartOverlay.value = (_aiChartOverlay.value ?: AiChartOverlayState(pair.symbol, tf)).copy(
                    isRedrawing = true,
                    redrawStep = "Scanning extremes on ${tf.label}..."
                )
                delay(300)

                _aiRedrawStep.value = "Generating AI zones & vectors..."
                delay(300)

                overlayResult = TechnicalAnalysisEngine.generateAiChartOverlay(pair, candles, tf, requestedRedrawType)
                _aiChartOverlay.value = overlayResult.copy(isRedrawing = false)
                _showAiDrawingsOnChart.value = true
                _aiRedrawStep.value = ""
            }

            // Generate AI text response
            val responseTextBuilder = StringBuilder()
            val lower = userText.lowercase()

            if (lower.contains("fib") || requestedRedrawType == "FIBONACCI") {
                val fib = overlayResult?.fibonacciLevels ?: emptyList()
                responseTextBuilder.append("📐 **AI FIBONACCI RETRACEMENT REDRAW (${pair.symbol} - ${tf.label})**\n\n")
                responseTextBuilder.append("I have identified the primary impulse wave and redrawn key Golden Ratio levels on your chart:\n\n")
                fib.forEach { f ->
                    responseTextBuilder.append("• **${f.percentage}** (${f.description}): `${String.format(Locale.US, "%.5f", f.price)}`\n")
                }
                responseTextBuilder.append("\n💡 **Tactical Recommendation:** Watch for buying reactions at the **61.8% Golden Pocket Zone** with bullish candle confirmations.")
            } else if (lower.contains("support") || lower.contains("resistance") || lower.contains("zone") || requestedRedrawType == "ZONES") {
                val zones = overlayResult?.zones ?: emptyList()
                responseTextBuilder.append("🪄 **AI SUPPLY & DEMAND ZONES REDRAW (${pair.symbol} - ${tf.label})**\n\n")
                responseTextBuilder.append("I scanned historical liquidity clusters and redrawn institutional zones directly on the canvas:\n\n")
                zones.forEach { z ->
                    val colorTag = if (z.type == AiZoneType.DEMAND_SUPPORT) "🟢" else if (z.type == AiZoneType.SUPPLY_RESISTANCE) "🔴" else "🟡"
                    responseTextBuilder.append("$colorTag **${z.label}**\n   Range: `${String.format(Locale.US, "%.5f", z.priceBottom)} - ${String.format(Locale.US, "%.5f", z.priceTop)}` (Confidence: ${z.confidence}%)\n\n")
                }
                responseTextBuilder.append("💡 **Order Flow Bias:** Current price is trading at `${pair.currentPrice}`, respecting the nearest institutional zone.")
            } else if (lower.contains("trend") || lower.contains("channel") || requestedRedrawType == "TRENDLINES") {
                val lines = overlayResult?.trendlines ?: emptyList()
                responseTextBuilder.append("📈 **AI DYNAMIC TRENDLINES & CHANNEL REDRAW**\n\n")
                responseTextBuilder.append("I analyzed multi-candle pivot structures and redrawn dynamic trend vectors on your chart:\n\n")
                if (lines.isNotEmpty()) {
                    lines.forEach { l ->
                        responseTextBuilder.append("• 🔹 **${l.label}** connecting pivot at `${String.format(Locale.US, "%.5f", l.price1)}` to `${String.format(Locale.US, "%.5f", l.price2)}`\n")
                    }
                } else {
                    responseTextBuilder.append("• Dynamic trendline vectors active across current session range.\n")
                }
                responseTextBuilder.append("\n💡 **Confluence:** Trend structure aligns with ${signal.summaryRationale}.")
            } else if (lower.contains("target") || lower.contains("sl") || lower.contains("tp") || requestedRedrawType == "TARGETS") {
                responseTextBuilder.append("🎯 **AI RISK & TARGET EXECUTION PROJECTION (${pair.symbol})**\n\n")
                responseTextBuilder.append("I calculated optimal risk-to-reward parameters and redrawn execution targets on your chart:\n\n")
                responseTextBuilder.append("• **Market Bias:** ${signal.type.title} (${signal.confidenceScore}% Confidence)\n")
                responseTextBuilder.append("• **Optimal Entry:** `${signal.entryPrice}`\n")
                responseTextBuilder.append("• **Invalidation Level (SL):** `${signal.stopLoss}`\n")
                responseTextBuilder.append("• **Primary Target (TP1):** `${signal.takeProfit1}`\n")
                responseTextBuilder.append("• **Extended Target (TP2):** `${signal.takeProfit2}`\n")
                responseTextBuilder.append("• **Risk-to-Reward Ratio:** `1:${String.format(Locale.US, "%.1f", signal.riskRewardRatio)}`\n\n")
                responseTextBuilder.append("⚠️ Maintain disciplined position sizing of 1-2% per setup.")
            } else {
                // General Full Analysis & Redraw
                responseTextBuilder.append("🤖 **AI TECHNICAL ANALYSIS & REAL-TIME CHART REDRAW**\n\n")
                responseTextBuilder.append("Asset: **${pair.symbol}** | Timeframe: **${tf.label}** | Live Price: `${pair.currentPrice}`\n\n")
                responseTextBuilder.append("1. **Market Structure & Redrawn Levels:**\n")
                responseTextBuilder.append("• Directional Bias: **${signal.type.title}** (Confidence: ${signal.confidenceScore}%)\n")
                responseTextBuilder.append("• 24h Session Bounds: Low `${pair.low24h}` — High `${pair.high24h}`\n")
                responseTextBuilder.append("• Candlestick Patterns: ${if (patterns.isEmpty()) "Consolidation / Wick Exhaustion" else patterns.joinToString { it.patternType.title }}\n\n")
                responseTextBuilder.append("2. **Indicator Confluence:**\n")
                responseTextBuilder.append("• RSI (14): `${String.format(Locale.US, "%.1f", signal.rsiValue)}` (${if (signal.rsiValue < 30) "Oversold Buy Zone" else if (signal.rsiValue > 70) "Overbought Rejection" else "Neutral Momentum"})\n")
                responseTextBuilder.append("• Trend Confluence: ${signal.summaryRationale}\n\n")
                responseTextBuilder.append("3. **Execution Plan:**\n")
                responseTextBuilder.append("• Entry: `${signal.entryPrice}` | SL: `${signal.stopLoss}` | TP1: `${signal.takeProfit1}`\n")
                responseTextBuilder.append("• Risk-to-Reward: `1:${String.format(Locale.US, "%.1f", signal.riskRewardRatio)}`\n")
            }

            val followUpPrompts = listOf(
                "🪄 Redraw Key Support & Resistance",
                "📈 Redraw Trendlines & Channels",
                "📐 Plot Fibonacci Retracement",
                "🎯 Show Breakout Targets (TP/SL)",
                "🔄 Full Re-Analysis & Redraw"
            )

            val aiResponse = AiChatMessage(
                sender = AiChatSender.AI,
                text = responseTextBuilder.toString(),
                chartOverlay = overlayResult ?: _aiChartOverlay.value,
                suggestedPrompts = followUpPrompts
            )

            _aiChatMessages.value = _aiChatMessages.value + aiResponse
            _aiReport.value = responseTextBuilder.toString()
            _isAnalyzingAi.value = false
        }
    }

    fun runAiTechnicalAnalysis(isSnapshot: Boolean = false) {
        viewModelScope.launch {
            _isAnalyzingAi.value = true
            _aiReport.value = null

            val pair = _selectedPair.value
            val tf = _selectedTimeframe.value
            val candles = getCandlesForSelectedPair()
            val signal = TechnicalAnalysisEngine.generateTradeSignal(pair, candles, tf)
            val patterns = TechnicalAnalysisEngine.detectPatterns(candles)
            val patternNames = patterns.joinToString(", ") { it.patternType.title }.ifEmpty { "Consolidation" }

            val settings = _indicatorSettings.value
            val activeOverlaysList = mutableListOf<String>()
            if (settings.showSma20) activeOverlaysList.add("SMA 20")
            if (settings.showSma50) activeOverlaysList.add("SMA 50")
            if (settings.showEma20) activeOverlaysList.add("EMA 20")
            if (settings.showEma50) activeOverlaysList.add("EMA 50")
            if (settings.showEma200) activeOverlaysList.add("EMA 200")
            if (settings.showBollingerBands) activeOverlaysList.add("Bollinger Bands")
            if (settings.showSupportResistance) activeOverlaysList.add("Support/Resistance")
            if (settings.showRsiSubchart) activeOverlaysList.add("RSI (14)")
            if (settings.showPatterns) activeOverlaysList.add("Pattern Markers")
            val activeOverlaysStr = activeOverlaysList.joinToString(", ").ifEmpty { "Clean Candlesticks" }

            // Trigger AI chart redraw if enabled
            if (_aiAutoRedrawEnabled.value) {
                _aiRedrawStep.value = "AI analyzing ${pair.symbol} structure..."
                _aiChartOverlay.value = (_aiChartOverlay.value ?: AiChartOverlayState(pair.symbol, tf)).copy(
                    isRedrawing = true,
                    redrawStep = "Scanning chart extremes..."
                )
                delay(250)
                val newOverlay = TechnicalAnalysisEngine.generateAiChartOverlay(pair, candles, tf)
                _aiChartOverlay.value = newOverlay.copy(isRedrawing = false)
                _showAiDrawingsOnChart.value = true
                _aiRedrawStep.value = ""
            }

            // Try OpenRouter API first
            val openRouterResult = repository.fetchOpenRouterAnalysis(
                symbol = pair.symbol,
                timeframe = _selectedTimeframe.value.label,
                currentPrice = pair.currentPrice,
                signalType = signal.type.title,
                rsiValue = signal.rsiValue,
                patterns = patternNames,
                isSnapshotAnalysis = isSnapshot,
                activeOverlays = activeOverlaysStr
            )

            if (openRouterResult != null) {
                _aiReport.value = openRouterResult
            } else {
                // Fallback to local rule engine simulation
                val reportBuilder = StringBuilder()
                if (isSnapshot) {
                    reportBuilder.append("📸 AI SNAPSHOT MARKET CONFIRMATION REPORT\n")
                    reportBuilder.append("Asset: ${pair.symbol} | Timeframe: ${_selectedTimeframe.value.label} | Price at Snapshot: ${pair.currentPrice}\n")
                    reportBuilder.append("Active Overlays: $activeOverlaysStr\n")
                    reportBuilder.append("Note: Configure OPENROUTER_API_KEY in Secrets panel to connect live Gemini/OpenRouter LLM.\n\n")

                    reportBuilder.append("1. 📸 CAPTURED SNAPSHOT STRUCTURE & KEY LEVELS:\n")
                    reportBuilder.append("• Asset & Timeframe: ${pair.symbol} (${_selectedTimeframe.value.label})\n")
                    reportBuilder.append("• Market Bias: ${signal.type.title} (Confidence: ${signal.confidenceScore}%)\n")
                    reportBuilder.append("• Price Bounds: 24h Low ${pair.low24h} | 24h High ${pair.high24h}\n\n")

                    reportBuilder.append("2. 🔍 MANUAL TRADER CONFIRMATION CHECKLIST:\n")
                    reportBuilder.append("• [x] Candle Rejection: ${if (patterns.isEmpty()) "Consolidation / Wick Rejection identified" else patterns.joinToString { it.patternType.title }}\n")
                    reportBuilder.append("• [x] RSI Momentum Confluence: ${String.format(Locale.US, "%.1f", signal.rsiValue)} - ${if (signal.rsiValue < 30) "Oversold Zone (BUY Confluence)" else if (signal.rsiValue > 70) "Overbought Zone (SELL Confluence)" else "Neutral Momentum"}\n")
                    reportBuilder.append("• [x] Dynamic Moving Average Support: ${signal.summaryRationale}\n")
                    reportBuilder.append("• [ ] Final Execution Check: Await candle closing on ${_selectedTimeframe.value.label} chart before placing order.\n\n")

                    reportBuilder.append("3. ⚠️ RISK & MANIPULATION WARNINGS:\n")
                    reportBuilder.append("• Beware of liquidity sweeps below ${pair.low24h}. Maintain strict 1% risk per trade.\n\n")

                    reportBuilder.append("4. 🏁 MANUAL TRADER EXECUTION VERDICT:\n")
                    reportBuilder.append("• Verdict: ${signal.type.title.uppercase()} CONFIRMATION (High Probability Setup)\n")
                    reportBuilder.append("• Entry Price: ${signal.entryPrice}\n")
                    reportBuilder.append("• Invalid Level (SL): ${signal.stopLoss}\n")
                    reportBuilder.append("• Target 1 (TP1): ${signal.takeProfit1} | Target 2 (TP2): ${signal.takeProfit2}\n")
                    reportBuilder.append("• Risk-to-Reward Ratio: 1:${String.format(Locale.US, "%.1f", signal.riskRewardRatio)}\n")
                } else {
                    reportBuilder.append("📊 AI TECHNICAL ANALYSIS REPORT (Institutional Rule Engine)\n")
                    reportBuilder.append("Asset: ${pair.symbol} | Timeframe: ${_selectedTimeframe.value.label} | Price: ${pair.currentPrice}\n")
                    reportBuilder.append("Note: Configure OPENROUTER_API_KEY in Secrets panel to connect live OpenRouter LLM.\n\n")

                    reportBuilder.append("1. MARKET STRUCTURE & BIAS:\n")
                    reportBuilder.append("• Directional Bias: ${signal.type.title} (Confidence: ${signal.confidenceScore}%)\n")
                    reportBuilder.append("• 24h Price Action Range: ${pair.low24h} - ${pair.high24h} (${pair.priceChange24h}%)\n\n")

                    reportBuilder.append("2. INDICATOR CONFLUENCE:\n")
                    reportBuilder.append("• RSI (14): ${String.format(Locale.US, "%.1f", signal.rsiValue)} - ${if (signal.rsiValue < 30) "Oversold Bounce Zone" else if (signal.rsiValue > 70) "Overbought Rejection Zone" else "Neutral Momentum"}\n")
                    reportBuilder.append("• Trend Confluence: ${signal.summaryRationale}\n\n")

                    reportBuilder.append("3. DETECTED PATTERNS & AI REDRAW:\n")
                    if (patterns.isEmpty()) {
                        reportBuilder.append("• Consolidation structure. Support/Resistance & Fibonacci retracement lines redrawn onto chart.\n\n")
                    } else {
                        patterns.forEach { p ->
                            reportBuilder.append("• ${p.patternType.title}: ${p.patternType.description}\n")
                        }
                        reportBuilder.append("\n")
                    }

                    reportBuilder.append("4. STRATEGY EXECUTION PLAN:\n")
                    reportBuilder.append("• Recommended Entry: ${signal.entryPrice}\n")
                    reportBuilder.append("• Invalid Level (SL): ${signal.stopLoss} (${signal.timeframe.label} candle close below)\n")
                    reportBuilder.append("• Target 1 (TP1): ${signal.takeProfit1}\n")
                    reportBuilder.append("• Target 2 (TP2): ${signal.takeProfit2}\n")
                    reportBuilder.append("• Risk-to-Reward Ratio: 1:${String.format(Locale.US, "%.1f", signal.riskRewardRatio)}\n")
                }

                _aiReport.value = reportBuilder.toString()
            }
            _isAnalyzingAi.value = false
        }
    }

}
