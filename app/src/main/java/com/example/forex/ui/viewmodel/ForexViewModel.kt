package com.example.forex.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.forex.data.ai.DynamicAiResponseEngine
import com.example.forex.data.db.ForexDatabase
import com.example.forex.data.model.*
import com.example.forex.data.remote.MarketDataHealth
import com.example.forex.data.repository.ForexRepository
import com.example.forex.data.repository.TechnicalAnalysisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.forex.data.auth.AuthResult
import com.example.forex.data.auth.ForexAuthManager

class ForexViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ForexDatabase.getDatabase(application)
    private val repository = ForexRepository(application.applicationContext, db.forexDao())

    // --- Authentication: Firebase Auth is the only auth path (see ForexAuthManager) ---
    private val authManager = ForexAuthManager(application.applicationContext)

    /** Signed-in Firebase user (null when signed out or Firebase is not configured). */
    val currentUser: StateFlow<UserProfile?> = authManager.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    /** True when this build carries a real google-services.json and Firebase Auth can run. */
    val isAuthConfigured: Boolean = authManager.isConfigured

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(
        if (authManager.isConfigured) null else ForexAuthManager.NOT_CONFIGURED_MESSAGE
    )
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    /** Non-error auth notices, e.g. "password reset email sent". */
    private val _authInfoMessage = MutableStateFlow<String?>(null)
    val authInfoMessage: StateFlow<String?> = _authInfoMessage.asStateFlow()

    fun clearAuthError() {
        _authErrorMessage.value = null
        _authInfoMessage.value = null
    }

    private fun runAuth(block: suspend () -> AuthResult, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            _authInfoMessage.value = null
            try {
                val result = block()
                if (result.success) {
                    onResult(true)
                } else {
                    _authErrorMessage.value = result.errorMessage
                    onResult(false)
                }
            } catch (e: Exception) {
                _authErrorMessage.value = e.message ?: "Authentication failed. Please try again."
                onResult(false)
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String, onResult: (Boolean) -> Unit = {}) {
        runAuth({ authManager.signUpWithEmail(email, password, displayName) }, onResult)
    }

    fun signInWithEmail(email: String, password: String, onResult: (Boolean) -> Unit = {}) {
        runAuth({ authManager.signInWithEmail(email, password) }, onResult)
    }

    /**
     * Google Sign-In needs an Activity [context] for the Credential Manager
     * picker — callers in Compose pass `LocalContext.current`.
     */
    fun signInWithGoogle(context: Context, onResult: (Boolean) -> Unit = {}) {
        runAuth({ authManager.signInWithGoogle(context) }, onResult)
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            _authInfoMessage.value = null
            try {
                val result = authManager.sendPasswordResetEmail(email)
                if (result.success) {
                    _authInfoMessage.value =
                        "Password reset email sent. Check your inbox for the Firebase reset link."
                } else {
                    _authErrorMessage.value = result.errorMessage
                }
            } finally {
                _isAuthLoading.value = false
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
        generateAllSignals(pairs.value, force = true)
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

    /** Health of the real REST market data chain (Alpha Vantage -> FCS -> simulation). */
    val marketDataHealth: StateFlow<MarketDataHealth> = repository.marketDataHealth

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
                // Throttled inside (max once / 5s): full 9-pair signal regeneration
                // on every sub-second tick was pure battery burn (F-10).
                generateAllSignals(updatedPairs)
            }
        }

        // F-07: post a notification the moment an alert triggers.
        viewModelScope.launch {
            repository.alertTriggers.collect { alert ->
                postAlertNotification(alert)
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


    @Volatile
    private var lastSignalGenerationAt = 0L

    /**
     * @param force bypass the 5s throttle — used by explicit user actions
     * (refresh, settings change); the tick stream calls are throttled (F-10).
     */
    private fun generateAllSignals(currentPairs: List<CurrencyPair>, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSignalGenerationAt < 5_000L) return
        lastSignalGenerationAt = now
        val signals = currentPairs.map { pair ->
            val candles = repository.getCandles(pair.symbol, _selectedTimeframe.value)
            TechnicalAnalysisEngine.generateTradeSignal(pair, candles, _selectedTimeframe.value, _indicatorSettings.value)
        }
        _liveSignals.value = signals
    }

    /** F-07: system notification for a triggered alert (permission-checked, channel-created). */
    private fun postAlertNotification(alert: CustomAlert) {
        val context = getApplication<Application>().applicationContext
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return // Evaluator already persisted the trigger; just don't notify without permission.
        }
        val channelId = "price_alerts"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(channelId, "Price Alerts", android.app.NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val conditionText = when (alert.condition) {
            AlertCondition.PRICE_ABOVE -> "rose above ${alert.targetValue}"
            AlertCondition.PRICE_BELOW -> "fell below ${alert.targetValue}"
            AlertCondition.RSI_OVERBOUGHT -> "RSI reached overbought (${alert.targetValue})"
            AlertCondition.RSI_OVERSOLD -> "RSI reached oversold (${alert.targetValue})"
            AlertCondition.MA_BULLISH_CROSS -> "bullish MA crossover"
            AlertCondition.MA_BEARISH_CROSS -> "bearish MA crossover"
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${alert.pairSymbol} alert triggered")
            .setContentText("${alert.pairSymbol} $conditionText")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(alert.id.hashCode(), notification)
    }

    /**
     * Pull fresh market data for the selected symbol: validated candle history via the
     * Alpha Vantage -> FCS chain (force refresh) plus a realtime spot quote, then rebuilds
     * signals across all pairs. Falls back silently to cached Room history offline.
     */
    fun refreshMarketData() {
        viewModelScope.launch(Dispatchers.Default) {
            triggerChartLoading(650)
            repository.refreshMarketSnapshot(_selectedPair.value.symbol, _selectedTimeframe.value)
            repository.refreshLatestQuoteFor(_selectedPair.value.symbol)
            generateAllSignals(pairs.value, force = true)
        }
    }

    private fun parentTrendAlignment(currentTf: Timeframe): List<Pair<String, String>> {
        val all = Timeframe.values().toList()
        val idx = all.indexOf(currentTf)
        if (idx < 0) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (higher in all.drop(idx + 1).take(2)) {
            val candles = repository.getCandles(_selectedPair.value.symbol, higher)
            if (candles.size >= 55) {
                val closes = candles.map { it.close }
                val fast = TechnicalAnalysisEngine.calculateEMA(closes, _indicatorSettings.value.emaPeriod1).lastOrNull { it != null }
                val slow = TechnicalAnalysisEngine.calculateEMA(closes, _indicatorSettings.value.emaPeriod2).lastOrNull { it != null }
                if (fast != null && slow != null) {
                    out += higher.label to if (fast > slow) "Bullish" else "Bearish"
                }
            }
        }
        return out
    }

    private fun activeOverlayNames(): String {
        val settings = _indicatorSettings.value
        val overlays = mutableListOf<String>()
        if (settings.showSma20) overlays.add("SMA ${settings.smaPeriod1}")
        if (settings.showSma50) overlays.add("SMA ${settings.smaPeriod2}")
        if (settings.showEma20) overlays.add("EMA ${settings.emaPeriod1}")
        if (settings.showEma50) overlays.add("EMA ${settings.emaPeriod2}")
        if (settings.showEma200) overlays.add("EMA ${settings.emaPeriod3}")
        if (settings.showBollingerBands) overlays.add("Bollinger Bands")
        if (settings.showSupportResistance) overlays.add("Support/Resistance")
        if (settings.showRsiSubchart) overlays.add("RSI (${settings.rsiPeriod})")
        if (settings.showMacdSubchart) overlays.add("MACD")
        if (settings.showPatterns) overlays.add("Pattern Markers")
        return overlays.joinToString(", ").ifEmpty { "Clean Candlesticks" }
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
        generateAllSignals(pairs.value, force = true)
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

    /**
     * Chat entry point for the AI analyst.
     *
     * The reply is never a canned paragraph: every turn re-reads the live candles for the
     * selected pair/timeframe, recomputes the full indicator stack (EMA/RSI/MACD/ADX/Bollinger/
     * ATR + volatility sizing) into a [DynamicAiResponseEngine.MarketAnalysisContext], detects
     * the user's intent, optionally redraws the chart overlay from the same candles, then asks
     * OpenRouter to answer the question grounded in the deterministic fact sheet. If the LLM
     * is not configured (or fails), the dynamic local engine composes the answer from the same
     * numbers — so the response always reflects current data, in either mode.
     */
    fun sendAiChatMessage(userText: String, requestedRedrawType: String? = null) {
        if (userText.isBlank()) return

        val userMsg = AiChatMessage(
            sender = AiChatSender.USER,
            text = userText.trim()
        )
        _aiChatMessages.value = _aiChatMessages.value + userMsg

        viewModelScope.launch {
            _isAnalyzingAi.value = true
            try {
                val pair = _selectedPair.value
                val tf = _selectedTimeframe.value
                val settings = _indicatorSettings.value
                val candles = getCandlesForSelectedPair()
                val ctx = DynamicAiResponseEngine.buildContext(
                    pair = pair,
                    candles = candles,
                    timeframe = tf,
                    settings = settings,
                    parentTrends = parentTrendAlignment(tf)
                )
                val intent = DynamicAiResponseEngine.detectIntent(userText, requestedRedrawType)

                var overlayResult = _aiChartOverlay.value
                    ?.takeIf { it.pairSymbol == pair.symbol && it.timeframe == tf }

                if (DynamicAiResponseEngine.shouldRedrawChart(userText, _aiAutoRedrawEnabled.value, intent)) {
                    _aiRedrawStep.value = "AI analyzing ${pair.symbol} structure..."
                    _aiChartOverlay.value = (overlayResult ?: AiChartOverlayState(pair.symbol, tf)).copy(
                        isRedrawing = true,
                        redrawStep = "Recomputing pivots & ATR bands on ${tf.label}..."
                    )
                    delay(220)
                    overlayResult = TechnicalAnalysisEngine.generateAiChartOverlay(pair, candles, tf, requestedRedrawType)
                    _aiChartOverlay.value = overlayResult.copy(
                        isRedrawing = false,
                        redrawStep = "Chart redrawn from ${candles.size} validated candles"
                    )
                    _showAiDrawingsOnChart.value = true
                    _aiRedrawStep.value = ""
                }

                val patternNames = ctx.patterns.joinToString(", ") { it.patternType.title }
                    .ifEmpty { "Consolidation" }

                val llmAnswer = repository.fetchOpenRouterAnalysis(
                    symbol = pair.symbol,
                    timeframe = tf.label,
                    currentPrice = pair.currentPrice,
                    signalType = ctx.signal.type.title,
                    rsiValue = ctx.signal.rsiValue,
                    patterns = patternNames,
                    isSnapshotAnalysis = false,
                    activeOverlays = activeOverlayNames(),
                    userQuestion = userText,
                    quantitativeContext = DynamicAiResponseEngine.quantitativeFactSheet(ctx)
                )

                val answer = llmAnswer ?: DynamicAiResponseEngine.compose(ctx, intent, userText)

                // Perception: pacing scales with content length instead of fixed fake waits.
                delay((answer.length / 40L).coerceIn(80L, 550L))

                val aiResponse = AiChatMessage(
                    sender = AiChatSender.AI,
                    text = answer,
                    chartOverlay = overlayResult,
                    suggestedPrompts = DynamicAiResponseEngine.followUpPrompts(intent)
                )
                _aiChatMessages.value = _aiChatMessages.value + aiResponse
                _aiReport.value = answer
            } finally {
                _isAnalyzingAi.value = false
            }
        }
    }

    /**
     * Full-chart analysis (report panel + optional auto-redraw). Same data-driven path as
     * the chat, without the intent routing: overview composition locally, LLM when configured.
     */
    fun runAiTechnicalAnalysis(isSnapshot: Boolean = false) {
        viewModelScope.launch {
            _isAnalyzingAi.value = true
            _aiReport.value = null
            try {

            val pair = _selectedPair.value
            val tf = _selectedTimeframe.value
            val settings = _indicatorSettings.value
            val candles = getCandlesForSelectedPair()
            val ctx = DynamicAiResponseEngine.buildContext(
                pair = pair,
                candles = candles,
                timeframe = tf,
                settings = settings,
                parentTrends = parentTrendAlignment(tf)
            )
            val activeOverlaysStr = activeOverlayNames()

            // Trigger AI chart redraw from the same candles if enabled
            if (_aiAutoRedrawEnabled.value) {
                _aiRedrawStep.value = "AI analyzing ${pair.symbol} structure..."
                _aiChartOverlay.value = (_aiChartOverlay.value ?: AiChartOverlayState(pair.symbol, tf)).copy(
                    isRedrawing = true,
                    redrawStep = "Scanning chart extremes & ATR volatility envelope..."
                )
                delay(250)
                val newOverlay = TechnicalAnalysisEngine.generateAiChartOverlay(pair, candles, tf)
                _aiChartOverlay.value = newOverlay.copy(isRedrawing = false)
                _showAiDrawingsOnChart.value = true
                _aiRedrawStep.value = ""
            }

            val patternNames = ctx.patterns.joinToString(", ") { it.patternType.title }
                .ifEmpty { "Consolidation" }

            val openRouterResult = repository.fetchOpenRouterAnalysis(
                symbol = pair.symbol,
                timeframe = tf.label,
                currentPrice = pair.currentPrice,
                signalType = ctx.signal.type.title,
                rsiValue = ctx.signal.rsiValue,
                patterns = patternNames,
                isSnapshotAnalysis = isSnapshot,
                activeOverlays = activeOverlaysStr,
                quantitativeContext = DynamicAiResponseEngine.quantitativeFactSheet(ctx)
            )

            val report = if (openRouterResult != null) {
                openRouterResult
            } else {
                val overview = DynamicAiResponseEngine.compose(
                    ctx,
                    DynamicAiResponseEngine.AiIntent.MARKET_OVERVIEW,
                    userText = if (isSnapshot) "Confirm the captured chart snapshot before I execute." else null
                )
                val sourceLine = when (marketDataHealth.value.source) {
                    com.example.forex.data.remote.MarketDataSource.ALPHA_VANTAGE ->
                        "Inputs: live Alpha Vantage candles (${candles.size} bars, quality ${ctx.qualityScore}/100)."
                    com.example.forex.data.remote.MarketDataSource.FCS_API ->
                        "Inputs: FCS API candles (${candles.size} bars, quality ${ctx.qualityScore}/100)."
                    else ->
                        "Inputs: local/simulated candle history (${candles.size} bars). Configure ALPHAVANTAGE_API_KEY or FCS_API_KEY in the Secrets panel for live market data."
                }
                if (isSnapshot) {
                    "📸 SNAPSHOT CONFIRMATION REPORT — MANUAL TRADER CHECKLIST\n" +
                        "Asset: ${pair.symbol} | Timeframe: ${tf.label} | Price at capture: ${pair.currentPrice}\n" +
                        "Active Overlays: $activeOverlaysStr\n\n$overview\n\n" +
                        "⚠️ Risk & manipulation warnings:\n" +
                        "• Beware of liquidity sweeps below ${TechnicalAnalysisEngine.fmtPrice(ctx.supports.lastOrNull() ?: pair.low24h, pair.pipSize)} / above ${TechnicalAnalysisEngine.fmtPrice(ctx.resistances.lastOrNull() ?: pair.high24h, pair.pipSize)}.\n" +
                        "• Await a ${tf.label} candle close before executing; keep account risk at 1% (${TechnicalAnalysisEngine.fmt(ctx.sizing.suggestedLots, 2)} lots suggested).\n" +
                        "• ${sourceLine}"
                } else {
                    "📊 AI TECHNICAL ANALYSIS REPORT (Dynamic Rule Engine v2 — ATR-aware)\n" +
                        "$sourceLine\n\n$overview"
                }
            }

            _aiReport.value = report
            } finally {
                _isAnalyzingAi.value = false
            }
        }
    }

}
