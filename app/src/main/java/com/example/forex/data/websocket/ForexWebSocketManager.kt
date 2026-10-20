package com.example.forex.data.websocket

import com.example.forex.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random

/**
 * High-performance WebSocket client for real-time Forex, Metals, and Crypto market ticks.
 * Manages live WebSocket connections, heartbeats, auto-reconnection, and price tick dispatching.
 */
class ForexWebSocketManager {

    private val wsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var isManualDisconnect = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var pulseJob: Job? = null

    // Real-time WebSocket Status & Performance Metrics
    private val _webSocketStatus = MutableStateFlow(WebSocketStatus.CONNECTING)
    val webSocketStatus: StateFlow<WebSocketStatus> = _webSocketStatus.asStateFlow()

    private val totalTicksCounter = AtomicLong(0)
    private var lastTickTimestamp = System.currentTimeMillis()
    private var lastPingTimestamp = 0L

    private val _webSocketStats = MutableStateFlow(
        WebSocketStats(
            status = WebSocketStatus.CONNECTING,
            latencyMs = 24,
            totalTicksReceived = 0,
            lastTickTime = System.currentTimeMillis(),
            activeStreamUrl = DEFAULT_WS_URL,
            messageRatePerSec = 0.0
        )
    )
    val webSocketStats: StateFlow<WebSocketStats> = _webSocketStats.asStateFlow()

    // Shared Flow for real-time market ticks
    private val _marketTicks = MutableSharedFlow<MarketTick>(
        extraBufferCapacity = 128,
        replay = 0
    )
    val marketTicks: SharedFlow<MarketTick> = _marketTicks.asSharedFlow()

    // Latest price cache for cross-rate generation
    private val latestPrices = mutableMapOf<String, Double>()
    private val previousPrices = mutableMapOf<String, Double>()

    companion object {
        /**
         * Targeted combined stream for exactly the symbols this app maps to pairs.
         * The previous default (`!miniTicker@arr`) pushed ~1,000 symbols/sec through
         * the parser for the sake of five — a needless battery/bandwidth cost (F-06).
         * Note these are Binance *crypto* tickers (BTC, tokenised gold PAXG, EUR/USD
         * stablecoin pairs) used as price references — not an interbank FX feed.
         */
        const val DEFAULT_WS_URL =
            "wss://stream.binance.com:9443/stream?streams=" +
                "btcusdt@miniTicker/paxgusdt@miniTicker/eurusdt@miniTicker/gbpusdt@miniTicker"
        const val COMBINED_TRADES_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@trade/ethusdt@trade/eurgbp@trade/xrpusdt@trade"
    }

    /** True only while the Binance socket is actually open (drives honest status labels). */
    @Volatile
    private var socketConnected = false

    /** Last real (socket) tick per app symbol; the simulation pulse must not fight real ticks. */
    private val lastRealTickAt = mutableMapOf<String, Long>()

    private var currentWsUrl = DEFAULT_WS_URL

    init {
        // Initialize base baseline prices
        latestPrices["EUR/USD"] = 1.0854
        latestPrices["GBP/USD"] = 1.2940
        latestPrices["USD/JPY"] = 154.25
        latestPrices["AUD/USD"] = 0.6580
        latestPrices["USD/CAD"] = 1.3780
        latestPrices["EUR/JPY"] = 167.42
        latestPrices["GBP/JPY"] = 199.60
        latestPrices["XAU/USD"] = 2385.50
        latestPrices["BTC/USD"] = 67450.00

        startWebSocketConnection()
        startLiveTickPulseEngine()
    }

    /**
     * Initiates the WebSocket handshake
     */
    fun startWebSocketConnection(url: String = currentWsUrl) {
        currentWsUrl = url
        isManualDisconnect = false
        reconnectJob?.cancel()

        _webSocketStatus.value = WebSocketStatus.CONNECTING
        updateStats(WebSocketStatus.CONNECTING)

        val request = Request.Builder()
            .url(url)
            .build()

        lastPingTimestamp = System.currentTimeMillis()

        // (Re)start the simulation pulse if a previous disconnect() stopped it.
        if (pulseJob?.isActive != true) {
            startLiveTickPulseEngine()
        }

        activeWebSocket?.cancel()
        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val latency = (System.currentTimeMillis() - lastPingTimestamp).coerceAtLeast(8)
                reconnectAttempt = 0
                socketConnected = true
                _webSocketStatus.value = WebSocketStatus.CONNECTED
                updateStats(WebSocketStatus.CONNECTED, latency = latency)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastTickTimestamp = System.currentTimeMillis()
                val latency = (System.currentTimeMillis() - lastPingTimestamp).coerceIn(12, 180)
                handleIncomingMessage(text, latency)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                socketConnected = false
                _webSocketStatus.value = WebSocketStatus.DISCONNECTED
                updateStats(WebSocketStatus.DISCONNECTED)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketConnected = false
                _webSocketStatus.value = WebSocketStatus.DISCONNECTED
                updateStats(WebSocketStatus.DISCONNECTED)
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketConnected = false
                _webSocketStatus.value = WebSocketStatus.ERROR
                updateStats(WebSocketStatus.ERROR)
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * Parses incoming WebSocket text payloads (MiniTicker array or Trade JSON)
     */
    private fun handleIncomingMessage(jsonText: String, latency: Long) {
        try {
            if (jsonText.startsWith("[")) {
                val array = JSONArray(jsonText)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawSymbol = obj.optString("s", "")
                    val closePrice = obj.optDouble("c", 0.0)
                    val vol = obj.optDouble("v", 0.0)

                    if (rawSymbol.isNotBlank() && closePrice > 0.0) {
                        processRawSymbolTick(rawSymbol, closePrice, vol, latency)
                    }
                }
            } else if (jsonText.startsWith("{")) {
                val root = JSONObject(jsonText)
                if (root.has("data")) {
                    // Combined-stream envelope: {"stream":"btcusdt@miniTicker","data":{…}}
                    // miniTicker payloads carry close in "c" (+ base volume "v");
                    // trade payloads carry price in "p" (+ quantity "q").
                    val data = root.getJSONObject("data")
                    val rawSymbol = data.optString("s", "")
                    val miniClose = data.optDouble("c", 0.0)
                    if (rawSymbol.isNotBlank() && miniClose > 0.0) {
                        processRawSymbolTick(rawSymbol, miniClose, data.optDouble("v", 0.0), latency)
                    } else {
                        val price = data.optDouble("p", 0.0)
                        val quantity = data.optDouble("q", 0.0)
                        if (rawSymbol.isNotBlank() && price > 0.0) {
                            processRawSymbolTick(rawSymbol, price, quantity, latency)
                        }
                    }
                } else if (root.has("s") && root.has("c")) {
                    val rawSymbol = root.optString("s", "")
                    val closePrice = root.optDouble("c", 0.0)
                    val vol = root.optDouble("v", 0.0)
                    if (rawSymbol.isNotBlank() && closePrice > 0.0) {
                        processRawSymbolTick(rawSymbol, closePrice, vol, latency)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Dispatches parsed raw symbol to active app pairs
     */
    private fun processRawSymbolTick(rawSymbol: String, rawPrice: Double, volume: Double, latency: Long) {
        when {
            rawSymbol.equals("BTCUSDT", ignoreCase = true) -> {
                dispatchTick("BTC/USD", rawPrice, volume, latency, isRealTick = true)
            }
            rawSymbol.equals("PAXGUSDT", ignoreCase = true) || rawSymbol.equals("XAUUSDT", ignoreCase = true) -> {
                dispatchTick("XAU/USD", rawPrice, volume, latency, isRealTick = true)
            }
            rawSymbol.equals("EURUSDT", ignoreCase = true) -> {
                dispatchTick("EUR/USD", rawPrice, volume, latency, isRealTick = true)
            }
            rawSymbol.equals("GBPUSDT", ignoreCase = true) -> {
                dispatchTick("GBP/USD", rawPrice, volume, latency, isRealTick = true)
            }
            rawSymbol.equals("EURGBP", ignoreCase = true) -> {
                // Derive GBP/USD from the EUR/USD reference and the EUR/GBP cross.
                val eurusd = latestPrices["EUR/USD"] ?: 1.0850
                val gbpEst = eurusd / rawPrice
                dispatchTick("GBP/USD", gbpEst, volume, latency, isRealTick = true)
            }
        }
    }

    /**
     * Dispatches a normalized market tick into the SharedFlow.
     *
     * [isRealTick] marks socket-sourced ticks. The reported status is honest:
     * CONNECTED only while the Binance socket is open; otherwise ticks (from the
     * simulation pulse) are labelled SIMULATED (F-06).
     */
    fun dispatchTick(symbol: String, price: Double, volume: Double, latency: Long = 20, isRealTick: Boolean = false) {
        if (isRealTick) {
            lastRealTickAt[symbol] = System.currentTimeMillis()
        }
        val prevPrice = latestPrices[symbol] ?: price
        val direction = when {
            price > prevPrice -> PriceDirection.UP
            price < prevPrice -> PriceDirection.DOWN
            else -> PriceDirection.NEUTRAL
        }

        previousPrices[symbol] = prevPrice
        latestPrices[symbol] = price

        // Calculate institutional spreads
        val spreadFactor = when (symbol) {
            "EUR/USD", "GBP/USD" -> 0.00012
            "USD/JPY", "EUR/JPY", "GBP/JPY" -> 0.018
            "AUD/USD", "USD/CAD" -> 0.00018
            "XAU/USD" -> 0.35
            "BTC/USD" -> 4.5
            else -> 0.0002
        }

        val halfSpread = spreadFactor / 2.0
        val bid = price - halfSpread
        val ask = price + halfSpread
        val tickCount = totalTicksCounter.incrementAndGet()

        val tick = MarketTick(
            symbol = symbol,
            price = price,
            bid = bid,
            ask = ask,
            volume = max(1.0, volume),
            timestamp = System.currentTimeMillis(),
            priceChangeDirection = direction
        )

        wsScope.launch {
            _marketTicks.emit(tick)
        }

        val effectiveStatus = if (socketConnected) WebSocketStatus.CONNECTED else WebSocketStatus.SIMULATED
        _webSocketStatus.value = effectiveStatus
        updateStats(
            status = effectiveStatus,
            latency = latency,
            totalTicks = tickCount
        )
    }

    /**
     * Simulation pulse: generates plausible micro-ticks for pairs the Binance
     * stream does not cover (JPY crosses, AUD/USD, USD/CAD) and as an offline
     * fallback. Ticks are labelled SIMULATED unless the socket is live, and the
     * pulse never overrides a symbol that received a real tick in the last 5s.
     */
    private fun startLiveTickPulseEngine() {
        pulseJob?.cancel()
        pulseJob = wsScope.launch {
            while (isActive) {
                delay(Random.nextLong(350, 750))

                val pairs = listOf(
                    "EUR/USD", "GBP/USD", "USD/JPY", "AUD/USD",
                    "USD/CAD", "EUR/JPY", "GBP/JPY", "XAU/USD", "BTC/USD"
                )
                // Pick 1-3 active pairs for high-frequency micro-ticks
                val activeBatch = pairs.shuffled().take(Random.nextInt(1, 4))

                for (symbol in activeBatch) {
                    // Never fight a real socket tick with a simulated one.
                    val lastReal = lastRealTickAt[symbol] ?: 0L
                    if (System.currentTimeMillis() - lastReal < 5_000L) continue
                    val current = latestPrices[symbol] ?: continue
                    val pipChange = when (symbol) {
                        "USD/JPY", "EUR/JPY", "GBP/JPY" -> Random.nextDouble(-0.035, 0.035)
                        "XAU/USD" -> Random.nextDouble(-0.85, 0.85)
                        "BTC/USD" -> Random.nextDouble(-18.0, 18.0)
                        else -> Random.nextDouble(-0.00015, 0.00015)
                    }

                    val rawNewPrice = current + pipChange
                    val formattedPrice = when {
                        symbol == "BTC/USD" -> round(rawNewPrice * 10) / 10.0
                        symbol == "XAU/USD" -> round(rawNewPrice * 100) / 100.0
                        symbol.contains("JPY") -> round(rawNewPrice * 100) / 100.0
                        else -> round(rawNewPrice * 100000) / 100000.0
                    }

                    val latency = (_webSocketStats.value.latencyMs + Random.nextLong(-3, 4)).coerceIn(10, 85)
                    dispatchTick(symbol, formattedPrice, Random.nextDouble(5.0, 45.0), latency)
                }
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = wsScope.launch {
            reconnectAttempt++
            _webSocketStatus.value = WebSocketStatus.RECONNECTING
            updateStats(WebSocketStatus.RECONNECTING)

            val backoffDelay = min(30000L, (1000L * (1 shl min(reconnectAttempt, 5))))
            delay(backoffDelay)

            if (!isManualDisconnect && isActive) {
                startWebSocketConnection(currentWsUrl)
            }
        }
    }

    private fun updateStats(
        status: WebSocketStatus,
        latency: Long = _webSocketStats.value.latencyMs,
        totalTicks: Long = totalTicksCounter.get()
    ) {
        val now = System.currentTimeMillis()
        val timeDiffSec = max(1.0, (now - lastTickTimestamp) / 1000.0)
        val rate = if (totalTicks > 0) 1.0 / timeDiffSec else 0.0

        _webSocketStats.value = WebSocketStats(
            status = status,
            latencyMs = latency,
            totalTicksReceived = totalTicks,
            lastTickTime = now,
            activeStreamUrl = currentWsUrl,
            messageRatePerSec = round(rate * 10) / 10.0
        )
    }

    /**
     * Manual Reconnect Trigger
     */
    fun reconnect() {
        isManualDisconnect = false
        reconnectAttempt = 0
        startWebSocketConnection(currentWsUrl)
    }

    /**
     * Manual Disconnect
     */
    fun disconnect() {
        isManualDisconnect = true
        socketConnected = false
        reconnectJob?.cancel()
        // Stop the simulation pulse too: after an explicit disconnect the price
        // board must freeze, not keep "ticking" on fabricated data (F-06).
        pulseJob?.cancel()
        pulseJob = null
        activeWebSocket?.close(1000, "User disconnected")
        activeWebSocket = null
        _webSocketStatus.value = WebSocketStatus.DISCONNECTED
        updateStats(WebSocketStatus.DISCONNECTED)
    }

    fun getCurrentPrice(symbol: String): Double {
        return latestPrices[symbol] ?: 1.0
    }
}
