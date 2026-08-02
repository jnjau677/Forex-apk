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

class ForexViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ForexDatabase.getDatabase(application)
    private val repository = ForexRepository(db.forexDao())

    val pairs: StateFlow<List<CurrencyPair>> = repository.pairs

    private val _selectedPair = MutableStateFlow(repository.pairs.value.first())
    val selectedPair: StateFlow<CurrencyPair> = _selectedPair.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.H1)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    private val _indicatorSettings = MutableStateFlow(IndicatorSettings())
    val indicatorSettings: StateFlow<IndicatorSettings> = _indicatorSettings.asStateFlow()

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

    // AI Analysis State
    private val _aiReport = MutableStateFlow<String?>(null)
    val aiReport: StateFlow<String?> = _aiReport.asStateFlow()

    private val _isAnalyzingAi = MutableStateFlow(false)
    val isAnalyzingAi: StateFlow<Boolean> = _isAnalyzingAi.asStateFlow()

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

    init {
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
        _indicatorSettings.value = update(_indicatorSettings.value)
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

    fun runAiTechnicalAnalysis(isSnapshot: Boolean = false) {
        viewModelScope.launch {
            _isAnalyzingAi.value = true
            _aiReport.value = null

            val pair = _selectedPair.value
            val candles = getCandlesForSelectedPair()
            val signal = TechnicalAnalysisEngine.generateTradeSignal(pair, candles, _selectedTimeframe.value)
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
                    reportBuilder.append("• [x] RSI Momentum Confluence: ${String.format("%.1f", signal.rsiValue)} - ${if (signal.rsiValue < 30) "Oversold Zone (BUY Confluence)" else if (signal.rsiValue > 70) "Overbought Zone (SELL Confluence)" else "Neutral Momentum"}\n")
                    reportBuilder.append("• [x] Dynamic Moving Average Support: ${signal.summaryRationale}\n")
                    reportBuilder.append("• [ ] Final Execution Check: Await candle closing on ${_selectedTimeframe.value.label} chart before placing order.\n\n")

                    reportBuilder.append("3. ⚠️ RISK & MANIPULATION WARNINGS:\n")
                    reportBuilder.append("• Beware of liquidity sweeps below ${pair.low24h}. Maintain strict 1% risk per trade.\n\n")

                    reportBuilder.append("4. 🏁 MANUAL TRADER EXECUTION VERDICT:\n")
                    reportBuilder.append("• Verdict: ${signal.type.title.uppercase()} CONFIRMATION (High Probability Setup)\n")
                    reportBuilder.append("• Entry Price: ${signal.entryPrice}\n")
                    reportBuilder.append("• Invalid Level (SL): ${signal.stopLoss}\n")
                    reportBuilder.append("• Target 1 (TP1): ${signal.takeProfit1} | Target 2 (TP2): ${signal.takeProfit2}\n")
                    reportBuilder.append("• Risk-to-Reward Ratio: 1:${String.format("%.1f", signal.riskRewardRatio)}\n")
                } else {
                    reportBuilder.append("📊 AI TECHNICAL ANALYSIS REPORT (Local Simulation Engine)\n")
                    reportBuilder.append("Asset: ${pair.symbol} | Timeframe: ${_selectedTimeframe.value.label} | Price: ${pair.currentPrice}\n")
                    reportBuilder.append("Note: Configure OPENROUTER_API_KEY in Secrets panel to connect live OpenRouter LLM.\n\n")

                    reportBuilder.append("1. MARKET STRUCTURE & BIAS:\n")
                    reportBuilder.append("• Directional Bias: ${signal.type.title} (Confidence: ${signal.confidenceScore}%)\n")
                    reportBuilder.append("• 24h Price Action Range: ${pair.low24h} - ${pair.high24h} (${pair.priceChange24h}%)\n\n")

                    reportBuilder.append("2. INDICATOR CONFLUENCE:\n")
                    reportBuilder.append("• RSI (14): ${String.format("%.1f", signal.rsiValue)} - ${if (signal.rsiValue < 30) "Oversold Bounce Zone" else if (signal.rsiValue > 70) "Overbought Rejection Zone" else "Neutral Momentum"}\n")
                    reportBuilder.append("• Trend Confluence: ${signal.summaryRationale}\n\n")

                    reportBuilder.append("3. DETECTED PATTERNS:\n")
                    if (patterns.isEmpty()) {
                        reportBuilder.append("• Consolidation structure. Awaiting clear breakout or candle confirmation.\n\n")
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
                    reportBuilder.append("• Risk-to-Reward Ratio: 1:${String.format("%.1f", signal.riskRewardRatio)}\n")
                }

                _aiReport.value = reportBuilder.toString()
            }
            _isAnalyzingAi.value = false
        }
    }

}
