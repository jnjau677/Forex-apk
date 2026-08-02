package com.example.forex.data.repository

import com.example.forex.data.db.*
import com.example.forex.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.random.Random

class ForexRepository(private val forexDao: ForexDao) {

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _pairs = MutableStateFlow<List<CurrencyPair>>(createInitialPairs())
    val pairs: StateFlow<List<CurrencyPair>> = _pairs.asStateFlow()

    private val candleMap = mutableMapOf<Pair<String, Timeframe>, MutableList<CandleStick>>()

    init {
        // Initialize candle history
        repoScope.launch {
            _pairs.value.forEach { pair ->
                Timeframe.values().forEach { tf ->
                    val key = Pair(pair.symbol, tf)
                    val dbCandles = forexDao.getCandles(pair.symbol, tf.name)
                    if (dbCandles.isNotEmpty()) {
                        candleMap[key] = dbCandles.map {
                            CandleStick(it.timestamp, it.open, it.high, it.low, it.close, it.volume)
                        }.toMutableList()
                    } else {
                        val generated = generateInitialCandles(pair.currentPrice, tf)
                        candleMap[key] = generated
                        forexDao.insertCandles(generated.map {
                            CandleEntity(pair.symbol, tf.name, it.timestamp, it.open, it.high, it.low, it.close, it.volume)
                        })
                    }
                }
            }
        }
    }

    private fun createInitialPairs(): List<CurrencyPair> {
        return listOf(
            CurrencyPair("EUR/USD", "Euro / US Dollar", PairCategory.MAJOR, "EUR", "USD", 1.0854, +0.32, 1.0885, 1.0820, 0.0001, true),
            CurrencyPair("GBP/USD", "British Pound / US Dollar", PairCategory.MAJOR, "GBP", "USD", 1.2940, +0.54, 1.2980, 1.2890, 0.0001, true),
            CurrencyPair("USD/JPY", "US Dollar / Japanese Yen", PairCategory.MAJOR, "USD", "JPY", 154.25, -0.41, 155.10, 153.80, 0.01, true),
            CurrencyPair("AUD/USD", "Australian Dollar / US Dollar", PairCategory.MAJOR, "AUD", "USD", 0.6580, +0.18, 0.6610, 0.6550, 0.0001, false),
            CurrencyPair("USD/CAD", "US Dollar / Canadian Dollar", PairCategory.MAJOR, "USD", "CAD", 1.3780, -0.12, 1.3820, 1.3750, 0.0001, false),
            CurrencyPair("EUR/JPY", "Euro / Japanese Yen", PairCategory.MINOR, "EUR", "JPY", 167.42, -0.15, 168.10, 166.90, 0.01, false),
            CurrencyPair("GBP/JPY", "British Pound / Japanese Yen", PairCategory.MINOR, "GBP", "JPY", 199.60, +0.28, 200.20, 198.90, 0.01, false),
            CurrencyPair("XAU/USD", "Gold / US Dollar", PairCategory.METALS_CRYPTO, "XAU", "USD", 2385.50, +1.15, 2398.00, 2362.00, 0.01, true),
            CurrencyPair("BTC/USD", "Bitcoin / US Dollar", PairCategory.METALS_CRYPTO, "BTC", "USD", 67450.00, +2.45, 68200.00, 65800.00, 1.0, true)
        )
    }

    private fun generateInitialCandles(basePrice: Double, tf: Timeframe): MutableList<CandleStick> {
        val list = mutableListOf<CandleStick>()
        var current = basePrice * 0.985
        val now = System.currentTimeMillis()
        val tfMillis = tf.minutes * 60 * 1000L
        val count = 60

        for (i in count downTo 0) {
            val time = now - (i * tfMillis)
            val volatility = basePrice * 0.003
            val change = Random.nextDouble(-volatility, volatility)
            val open = current
            val close = open + change
            val high = maxOf(open, close) + Random.nextDouble(0.0, volatility * 0.5)
            val low = minOf(open, close) - Random.nextDouble(0.0, volatility * 0.5)
            val volume = Random.nextDouble(500.0, 5000.0)

            list.add(CandleStick(time, open, high, low, close, volume))
            current = close
        }
        return list
    }

    fun getCandles(symbol: String, timeframe: Timeframe): List<CandleStick> {
        val key = Pair(symbol, timeframe)
        if (!candleMap.containsKey(key)) {
            val pair = _pairs.value.find { it.symbol == symbol }
            val price = pair?.currentPrice ?: 1.0
            val generated = generateInitialCandles(price, timeframe)
            candleMap[key] = generated
            repoScope.launch {
                forexDao.insertCandles(generated.map {
                    CandleEntity(symbol, timeframe.name, it.timestamp, it.open, it.high, it.low, it.close, it.volume)
                })
            }
        }
        return candleMap[key] ?: emptyList()
    }

    private val marketDataService = ForexMarketRepositoryService()

    private val openRouterRetrofit = retrofit2.Retrofit.Builder()
        .baseUrl("https://openrouter.ai/")
        .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create())
        .build()

    private val openRouterApi = openRouterRetrofit.create(OpenRouterApi::class.java)

    suspend fun fetchOpenRouterAnalysis(
        symbol: String,
        timeframe: String,
        currentPrice: Double,
        signalType: String,
        rsiValue: Double,
        patterns: String,
        isSnapshotAnalysis: Boolean = false,
        activeOverlays: String = ""
    ): String? {
        val apiKey = com.example.BuildConfig.OPENROUTER_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_OPENROUTER_API_KEY") {
            return null
        }

        return try {
            val prompt = if (isSnapshotAnalysis) {
                """
                You are a senior institutional Forex & Crypto technical analyst performing a MANUAL MARKET CONFIRMATION on a captured chart snapshot.
                Analyze the captured snapshot chart state for $symbol:
                - Timeframe: $timeframe
                - Current Price at Snapshot: $currentPrice
                - Technical Signal Bias: $signalType
                - 14-period RSI: $rsiValue
                - Active Chart Overlays & Technical Indicators: $activeOverlays
                - Chart Patterns Detected: $patterns

                Please generate a structured, rigorous MANUAL MARKET CONFIRMATION REPORT with the following sections:
                1. 📸 CAPTURED SNAPSHOT STRUCTURE & KEY LEVEL CONFIRMATION
                2. 🔍 MANUAL CHECKLIST (Candle Rejection, RSI Confluence, Moving Average Dynamic Levels)
                3. ⚠️ RISK & MANIPULATION WARNINGS (Liquidity Sweeps, News Spikes)
                4. 🏁 MANUAL TRADER VERDICT (Strong Buy, Wait for Confirmation, Invalid Setup, or Sell)
                """.trimIndent()
            } else {
                """
                You are a senior professional Forex & Crypto technical analyst.
                Analyze the following live market data for $symbol:
                - Timeframe: $timeframe
                - Current Price: $currentPrice
                - Technical Signal Bias: $signalType
                - 14-period RSI: $rsiValue
                - Chart Patterns Detected: $patterns
                
                Please generate a structured, concise report with 4 sections:
                1. MARKET STRUCTURE & BIAS
                2. INDICATOR CONFLUENCE
                3. DETECTED PATTERNS & PRICE ACTION
                4. STRATEGY EXECUTION PLAN (Entry, Stop Loss, Take Profit 1 & 2, Risk/Reward)
                """.trimIndent()
            }

            val request = OpenRouterRequest(
                model = "google/gemini-2.5-flash",
                messages = listOf(OpenRouterMessage(role = "user", content = prompt))
            )

            val response = openRouterApi.generateCompletion(
                authHeader = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (!content.isNullOrBlank()) {
                    "🌐 LIVE OPENROUTER AI REPORT (${request.model})\n\n$content"
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    /**
     * Real-time tick stream: Updates currency prices & current candle
     */
    fun startLivePriceStream() = flow {
        val useRealApi = marketDataService.isConfigured()

        while (true) {
            delay(5000) // Update every 5 seconds to avoid rate limiting
            
            val updatedPairs = if (useRealApi) {
                val symbols = _pairs.value.map { it.symbol }
                val result = marketDataService.fetchLatestPrices(symbols)
                
                if (result.isSuccess) {
                    val prices = result.getOrDefault(emptyList())
                    _pairs.value.map { pair ->
                        val latestInfo = prices.find { it.s == pair.symbol || it.s == pair.symbol.replace("/", "") }
                        if (latestInfo != null) {
                            val newPrice = latestInfo.c.toDoubleOrNull() ?: pair.currentPrice
                            val newHigh = maxOf(pair.high24h, newPrice)
                            val newLow = minOf(pair.low24h, newPrice)
                            
                            // Update latest candle for all timeframes
                            Timeframe.values().forEach { tf ->
                                val key = Pair(pair.symbol, tf)
                                val candles = candleMap[key]
                                if (candles != null && candles.isNotEmpty()) {
                                    val lastCandle = candles.last()
                                    val updatedLast = lastCandle.copy(
                                        close = newPrice,
                                        high = maxOf(lastCandle.high, newPrice),
                                        low = minOf(lastCandle.low, newPrice),
                                        volume = lastCandle.volume + Random.nextDouble(1.0, 10.0)
                                    )
                                    candles[candles.lastIndex] = updatedLast
                                }
                            }
                            
                            pair.copy(
                                currentPrice = newPrice,
                                high24h = newHigh,
                                low24h = newLow
                            )
                        } else {
                            pair
                        }
                    }
                } else {
                    _pairs.value
                }
            } else {
                // Simulated data fallback
                _pairs.value.map { pair ->
                    val pipChange = if (pair.symbol.contains("JPY")) {
                        Random.nextDouble(-0.08, 0.08)
                    } else if (pair.symbol == "XAU/USD") {
                        Random.nextDouble(-1.2, 1.2)
                    } else if (pair.symbol == "BTC/USD") {
                        Random.nextDouble(-45.0, 45.0)
                    } else {
                        Random.nextDouble(-0.0003, 0.0003)
                    }

                    val newPrice = (pair.currentPrice + pipChange).let {
                        if (pair.pipSize == 0.0001) round(it * 10000) / 10000.0
                        else if (pair.pipSize == 0.01) round(it * 100) / 100.0
                        else round(it * 10) / 10.0
                    }

                    val newHigh = maxOf(pair.high24h, newPrice)
                    val newLow = minOf(pair.low24h, newPrice)

                    // Update latest candle for all timeframes
                    Timeframe.values().forEach { tf ->
                        val key = Pair(pair.symbol, tf)
                        val candles = candleMap[key]
                        if (candles != null && candles.isNotEmpty()) {
                            val lastCandle = candles.last()
                            val updatedLast = lastCandle.copy(
                                close = newPrice,
                                high = maxOf(lastCandle.high, newPrice),
                                low = minOf(lastCandle.low, newPrice),
                                volume = lastCandle.volume + Random.nextDouble(5.0, 25.0)
                            )
                            candles[candles.lastIndex] = updatedLast
                        }
                    }

                    pair.copy(
                        currentPrice = newPrice,
                        high24h = newHigh,
                        low24h = newLow
                    )
                }
            }

            _pairs.value = updatedPairs
            emit(updatedPairs)
        }
    }

    // Room DB methods
    val savedSignals: Flow<List<SignalEntity>> = forexDao.getAllSavedSignals()
    val customAlerts: Flow<List<AlertEntity>> = forexDao.getAllAlerts()
    val watchlist: Flow<List<WatchlistEntity>> = forexDao.getWatchlist()

    suspend fun saveSignal(signal: TradeSignal) {
        forexDao.insertSignal(
            SignalEntity(
                id = signal.id,
                pairSymbol = signal.pairSymbol,
                signalType = signal.type.name,
                entryPrice = signal.entryPrice,
                stopLoss = signal.stopLoss,
                takeProfit1 = signal.takeProfit1,
                takeProfit2 = signal.takeProfit2,
                riskRewardRatio = signal.riskRewardRatio,
                confidenceScore = signal.confidenceScore,
                timeframeLabel = signal.timeframe.label,
                summaryRationale = signal.summaryRationale,
                timestamp = signal.timestamp
            )
        )
    }

    suspend fun deleteSavedSignal(id: String) {
        forexDao.deleteSignal(id)
    }

    suspend fun createAlert(alert: CustomAlert) {
        forexDao.insertAlert(
            AlertEntity(
                id = alert.id,
                pairSymbol = alert.pairSymbol,
                conditionType = alert.condition.name,
                targetValue = alert.targetValue,
                isTriggered = alert.isTriggered,
                isActive = alert.isActive,
                note = alert.note,
                createdAt = alert.createdAt
            )
        )
    }

    suspend fun deleteAlert(id: String) {
        forexDao.deleteAlert(id)
    }

    suspend fun toggleAlert(id: String, active: Boolean) {
        forexDao.updateAlertStatus(id, triggered = false, active = active)
    }

    suspend fun addToWatchlist(symbol: String) {
        forexDao.addToWatchlist(WatchlistEntity(symbol = symbol))
    }

    suspend fun removeFromWatchlist(symbol: String) {
        forexDao.removeFromWatchlist(symbol)
    }
}
