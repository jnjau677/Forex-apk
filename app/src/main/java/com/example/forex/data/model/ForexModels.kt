package com.example.forex.data.model

enum class PairCategory(val label: String) {
    MAJOR("Major"), MINOR("Minor"), EXOTIC("Exotic"), METALS_CRYPTO("Metals & Crypto")
}

data class CurrencyPair(
    val symbol: String, // e.g., "EUR/USD"
    val name: String, // e.g., "Euro / US Dollar"
    val category: PairCategory,
    val baseCurrency: String,
    val quoteCurrency: String,
    val currentPrice: Double,
    val priceChange24h: Double, // in percentage, e.g. +0.45 or -0.32
    val high24h: Double,
    val low24h: Double,
    val pipSize: Double = 0.0001, // 0.01 for JPY pairs
    val isFavorite: Boolean = false
)

enum class Timeframe(val label: String, val minutes: Int) {
    M1("1M", 1),
    M5("5M", 5),
    M15("15M", 15),
    H1("1H", 60),
    H4("4H", 240),
    D1("1D", 1440)
}

enum class ChartStyle {
    CANDLESTICK, LINE, HEIKIN_ASHI
}

data class CandleStick(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isBullish: Boolean = close >= open
)

enum class PatternType(val title: String, val isBullish: Boolean, val description: String) {
    BULLISH_ENGULFING("Bullish Engulfing", true, "Strong buyers reversal pattern after downtrend"),
    BEARISH_ENGULFING("Bearish Engulfing", false, "Strong sellers reversal pattern after uptrend"),
    DOUBLE_BOTTOM("Double Bottom (W)", true, "Reversal pattern indicating strong floor support"),
    DOUBLE_TOP("Double Top (M)", false, "Reversal pattern indicating heavy ceiling resistance"),
    HEAD_AND_SHOULDERS("Head & Shoulders", false, "Classic bearish structural breakdown pattern"),
    INVERSE_HEAD_AND_SHOULDERS("Inv. Head & Shoulders", true, "Bullish market structure breakout pattern"),
    MORNING_STAR("Morning Star", true, "3-candle bullish reversal cluster"),
    EVENING_STAR("Evening Star", false, "3-candle bearish reversal cluster"),
    RESISTANCE_REJECTION("Resistance Rejection", false, "Price wick rejected upper supply zone"),
    SUPPORT_BOUNCE("Support Bounce", true, "Price wick bounced from key demand zone"),
    BREAKOUT_BULLISH("Bullish Breakout", true, "Price closed above key resistance zone with high volume"),
    BREAKOUT_BEARISH("Bearish Breakout", false, "Price closed below key support zone with high volume")
}

data class DetectedPattern(
    val patternType: PatternType,
    val candleIndex: Int,
    val priceLevel: Double,
    val confidence: Int // 0 - 100%
)

enum class SignalType(val title: String) {
    BUY("STRONG BUY"),
    SELL("STRONG SELL"),
    NEUTRAL("NEUTRAL / HOLD")
}

data class TradeSignal(
    val id: String,
    val pairSymbol: String,
    val type: SignalType,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val riskRewardRatio: Double,
    val confidenceScore: Int, // e.g. 88%
    val timeframe: Timeframe,
    val detectedPatterns: List<PatternType>,
    val rsiValue: Double,
    val macdStatus: String,
    val summaryRationale: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false
)

enum class AlertCondition(val title: String) {
    PRICE_ABOVE("Price Rises Above"),
    PRICE_BELOW("Price Drops Below"),
    RSI_OVERSOLD("RSI Oversold (< 30)"),
    RSI_OVERBOUGHT("RSI Overbought (> 70)"),
    MA_BULLISH_CROSS("EMA 20/50 Bullish Cross"),
    MA_BEARISH_CROSS("EMA 20/50 Bearish Cross")
}

data class CustomAlert(
    val id: String,
    val pairSymbol: String,
    val condition: AlertCondition,
    val targetValue: Double,
    val isTriggered: Boolean = false,
    val isActive: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class MacdData(
    val macdLine: List<Double?>,
    val signalLine: List<Double?>,
    val histogram: List<Double?>
)

data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis(),
    val accountTier: String = "Pro Trader"
)

data class IndicatorSettings(
    // Visibility Toggles
    val showSma20: Boolean = false,
    val showSma50: Boolean = false,
    val showEma20: Boolean = true,
    val showEma50: Boolean = true,
    val showEma200: Boolean = false,
    val showBollingerBands: Boolean = true,
    val showSupportResistance: Boolean = true,
    val showRsiSubchart: Boolean = true,
    val showMacdSubchart: Boolean = false,
    val showPatterns: Boolean = true,

    // Configurable Parameters for Moving Averages
    val smaPeriod1: Int = 20,
    val smaPeriod2: Int = 50,
    val emaPeriod1: Int = 20,
    val emaPeriod2: Int = 50,
    val emaPeriod3: Int = 200,

    // Configurable Parameters for RSI
    val rsiPeriod: Int = 14,
    val rsiOverbought: Double = 70.0,
    val rsiOversold: Double = 30.0,

    // Configurable Parameters for MACD
    val macdFastPeriod: Int = 12,
    val macdSlowPeriod: Int = 26,
    val macdSignalPeriod: Int = 9,

    // Configurable Parameters for Bollinger Bands
    val bollingerPeriod: Int = 20,
    val bollingerStdDev: Double = 2.0
)

enum class WebSocketStatus(val label: String) {
    CONNECTING("Connecting..."),
    CONNECTED("Live WS Connected"),
    DISCONNECTED("Disconnected"),
    RECONNECTING("Reconnecting..."),
    ERROR("Connection Error")
}

enum class PriceDirection {
    UP, DOWN, NEUTRAL
}

data class MarketTick(
    val symbol: String,
    val price: Double,
    val bid: Double,
    val ask: Double,
    val volume: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val priceChangeDirection: PriceDirection = PriceDirection.NEUTRAL
)

data class WebSocketStats(
    val status: WebSocketStatus,
    val latencyMs: Long,
    val totalTicksReceived: Long,
    val lastTickTime: Long,
    val activeStreamUrl: String,
    val messageRatePerSec: Double
)

enum class AiZoneType(val title: String) {
    DEMAND_SUPPORT("Demand / Key Support"),
    SUPPLY_RESISTANCE("Supply / Key Resistance"),
    BREAKOUT_ZONE("Breakout Threshold Zone")
}

data class AiZone(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AiZoneType,
    val priceTop: Double,
    val priceBottom: Double,
    val label: String,
    val strength: Double = 0.85,
    val confidence: Int = 90
)

enum class AiTrendlineType(val title: String) {
    BULLISH_SUPPORT("Bullish Dynamic Support"),
    BEARISH_RESISTANCE("Bearish Dynamic Resistance"),
    ASCENDING_CHANNEL("Ascending Channel Ray"),
    DESCENDING_CHANNEL("Descending Channel Ray"),
    BREAKOUT_VECTOR("Breakout Vector")
}

data class AiTrendline(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AiTrendlineType,
    val candleIndex1: Int,
    val price1: Double,
    val candleIndex2: Int,
    val price2: Double,
    val label: String,
    val isDashed: Boolean = false
)

data class AiTargetProjection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val riskRewardRatio: Double,
    val isBuy: Boolean,
    val rationale: String
)

data class AiFibonacciLevel(
    val ratio: Double,
    val percentage: String,
    val price: Double,
    val description: String
)

data class AiChartOverlayState(
    val pairSymbol: String,
    val timeframe: Timeframe,
    val zones: List<AiZone> = emptyList(),
    val trendlines: List<AiTrendline> = emptyList(),
    val targets: List<AiTargetProjection> = emptyList(),
    val fibonacciLevels: List<AiFibonacciLevel> = emptyList(),
    val fibonacciP1: Double = 0.0,
    val fibonacciP2: Double = 0.0,
    val detectedPatterns: List<DetectedPattern> = emptyList(),
    val analysisSummary: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRedrawing: Boolean = false,
    val redrawStep: String = ""
)

enum class AiChatSender {
    USER, AI, SYSTEM
}

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: AiChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAnalysisReport: Boolean = false,
    val chartOverlay: AiChartOverlayState? = null,
    val suggestedPrompts: List<String> = emptyList()
) {
    val isUser: Boolean get() = sender == AiChatSender.USER
    val hasChartRedraw: Boolean get() = chartOverlay != null || isAnalysisReport
    val actions: List<String> get() = suggestedPrompts
}


