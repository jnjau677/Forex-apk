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

data class IndicatorSettings(
    val showSma20: Boolean = false,
    val showSma50: Boolean = false,
    val showEma20: Boolean = true,
    val showEma50: Boolean = true,
    val showEma200: Boolean = false,
    val showBollingerBands: Boolean = true,
    val showSupportResistance: Boolean = true,
    val showRsiSubchart: Boolean = true,
    val showMacdSubchart: Boolean = false,
    val showPatterns: Boolean = true
)
