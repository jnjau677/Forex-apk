package com.example.forex.data.repository

import com.example.forex.data.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Deterministic institutional rule engine.
 *
 * All computations are pure functions over candle series so they can run in the UI
 * process, inside a background [androidx.work.Worker], and in unit tests without
 * Android dependencies. Indicator math follows standard conventions (Wilder smoothing
 * for RSI/ATR/ADX, EMA seeded with SMA, population std-dev for Bollinger Bands).
 */
object TechnicalAnalysisEngine {

    /** Never place a structural stop tighter than this many pips. */
    const val MIN_STOP_PIPS = 6.0

    /** Cap ATR-derived stops (unless volatility demands a wider cushion). */
    const val MAX_STOP_PIPS = 150.0

    // ------------------------------------------------------------------
    // Moving averages & momentum
    // ------------------------------------------------------------------

    /**
     * Calculates Simple Moving Average (SMA) for a list of closes.
     */
    fun calculateSMA(closes: List<Double>, period: Int): List<Double?> {
        if (closes.size < period) return List(closes.size) { null }
        val result = ArrayList<Double?>(closes.size)
        for (i in 0 until period - 1) {
            result.add(null)
        }
        for (i in (period - 1) until closes.size) {
            val windowAvg = closes.subList(i - period + 1, i + 1).average()
            result.add(windowAvg)
        }
        return result
    }

    /**
     * Calculates Exponential Moving Average (EMA) for a list of closes.
     */
    fun calculateEMA(closes: List<Double>, period: Int): List<Double?> {
        if (closes.size < period) return List(closes.size) { null }
        val result = ArrayList<Double?>(closes.size)

        // Fill initial nulls
        for (i in 0 until period - 1) {
            result.add(null)
        }

        // Initial SMA as starting point
        val initialSma = closes.take(period).average()
        result.add(initialSma)

        val multiplier = 2.0 / (period + 1)
        var prevEma = initialSma

        for (i in period until closes.size) {
            val currentEma = (closes[i] - prevEma) * multiplier + prevEma
            result.add(currentEma)
            prevEma = currentEma
        }

        return result
    }

    /**
     * Calculates Relative Strength Index (RSI 14, Wilder smoothing).
     */
    fun calculateRSI(closes: List<Double>, period: Int = 14): List<Double?> {
        if (closes.size <= period) return List(closes.size) { null }
        val result = ArrayList<Double?>(closes.size)

        for (i in 0..period) {
            result.add(null)
        }

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) gains += change else losses += abs(change)
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
        val initialRsi = 100.0 - (100.0 / (1.0 + rs))
        result.add(initialRsi)

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val currentGain = if (change > 0) change else 0.0
            val currentLoss = if (change < 0) abs(change) else 0.0

            avgGain = (avgGain * (period - 1) + currentGain) / period
            avgLoss = (avgLoss * (period - 1) + currentLoss) / period

            val currentRs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
            val currentRsi = 100.0 - (100.0 / (1.0 + currentRs))
            result.add(currentRsi)
        }

        return result
    }

    /**
     * Calculates Bollinger Bands (SMA 20, 2 StdDev)
     */
    data class BollingerBand(val upper: Double, val middle: Double, val lower: Double) {
        /** Band width normalized by the midline — a common volatility proxy. */
        val widthPercent: Double get() = if (middle != 0.0) (upper - lower) / middle * 100.0 else 0.0
    }

    fun calculateBollingerBands(closes: List<Double>, period: Int = 20, stdDevMultiplier: Double = 2.0): List<BollingerBand?> {
        if (closes.size < period) return List(closes.size) { null }
        val result = ArrayList<BollingerBand?>(closes.size)

        for (i in 0 until period - 1) {
            result.add(null)
        }

        for (i in (period - 1) until closes.size) {
            val window = closes.subList(i - period + 1, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean).pow(2) } / period
            val stdDev = sqrt(variance)

            val upper = mean + (stdDevMultiplier * stdDev)
            val lower = mean - (stdDevMultiplier * stdDev)
            result.add(BollingerBand(upper, mean, lower))
        }

        return result
    }

    /**
     * Calculates Moving Average Convergence Divergence (MACD)
     * Fast EMA, Slow EMA, Signal Line, and Histogram
     */
    fun calculateMACD(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdData {
        val size = closes.size
        if (size < slowPeriod) {
            return MacdData(
                macdLine = List(size) { null },
                signalLine = List(size) { null },
                histogram = List(size) { null }
            )
        }

        val fastEma = calculateEMA(closes, fastPeriod)
        val slowEma = calculateEMA(closes, slowPeriod)

        val macdLine = ArrayList<Double?>(size)
        for (i in 0 until size) {
            val f = fastEma.getOrNull(i)
            val s = slowEma.getOrNull(i)
            if (f != null && s != null) {
                macdLine.add(f - s)
            } else {
                macdLine.add(null)
            }
        }

        // Calculate Signal Line (EMA of non-null MACD values)
        val signalLine = ArrayList<Double?>(size)
        val nonNullMacd = macdLine.filterNotNull()

        if (nonNullMacd.size >= signalPeriod) {
            val macdEma = calculateEMA(nonNullMacd, signalPeriod)
            var emaPtr = 0
            for (i in 0 until size) {
                if (macdLine[i] != null) {
                    signalLine.add(macdEma.getOrNull(emaPtr))
                    emaPtr++
                } else {
                    signalLine.add(null)
                }
            }
        } else {
            for (i in 0 until size) {
                signalLine.add(null)
            }
        }

        val histogram = ArrayList<Double?>(size)
        for (i in 0 until size) {
            val m = macdLine.getOrNull(i)
            val s = signalLine.getOrNull(i)
            if (m != null && s != null) {
                histogram.add(m - s)
            } else {
                histogram.add(null)
            }
        }

        return MacdData(
            macdLine = macdLine,
            signalLine = signalLine,
            histogram = histogram
        )
    }

    // ------------------------------------------------------------------
    // Volatility: ATR, ADX, regime detection, position sizing
    // ------------------------------------------------------------------

    /**
     * Average True Range (Wilder, standard 14). True range uses the classic
     * max(high-low, |high-prevClose|, |low-prevClose|) so overnight gaps are priced in.
     * The series is null-padded and index-aligned with the input candles.
     */
    fun calculateATR(candles: List<CandleStick>, period: Int = 14): List<Double?> {
        val size = candles.size
        if (size < period + 1) return List(size) { null }

        val trueRanges = DoubleArray(size)
        trueRanges[0] = candles[0].high - candles[0].low
        for (i in 1 until size) {
            val c = candles[i]
            val prevClose = candles[i - 1].close
            trueRanges[i] = max(
                c.high - c.low,
                max(abs(c.high - prevClose), abs(c.low - prevClose))
            )
        }

        val result = ArrayList<Double?>(size)
        for (i in 0 until period) result.add(null)

        // Seed with the simple average of the first `period` true ranges (indices 1..period).
        var atr = (1..period).sumOf { trueRanges[it] } / period
        result.add(atr)

        for (i in (period + 1) until size) {
            atr = (atr * (period - 1) + trueRanges[i]) / period
            result.add(atr)
        }
        return result
    }

    /** Convenience: latest ATR for the series, or null when data is insufficient. */
    fun latestATR(candles: List<CandleStick>, period: Int = 14): Double? =
        calculateATR(candles, period).lastOrNull { it != null }

    /** Latest ATR expressed in the pair's pip unit (0.0 when unavailable). */
    fun atrInPips(candles: List<CandleStick>, pipSize: Double, period: Int = 14): Double {
        if (pipSize <= 0) return 0.0
        return (latestATR(candles, period) ?: 0.0) / pipSize
    }

    /**
     * Average Directional Index (Wilder). Returns an index-aligned series; nulls while
     * the smoothing windows are still warming up. Used to distinguish trending from chop
     * so EMA/RSI signals are not over-trusted inside ranges.
     */
    fun calculateADX(candles: List<CandleStick>, period: Int = 14): List<Double?> {
        val size = candles.size
        if (size < period * 2 + 1) return List(size) { null }

        val tr = DoubleArray(size)
        val plusDm = DoubleArray(size)
        val minusDm = DoubleArray(size)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until size) {
            val upMove = candles[i].high - candles[i - 1].high
            val downMove = candles[i - 1].low - candles[i].low
            plusDm[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDm[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
            val c = candles[i]
            tr[i] = max(c.high - c.low, max(abs(c.high - candles[i - 1].close), abs(c.low - candles[i - 1].close)))
        }

        var smoothedTr = (1..period).sumOf { tr[it] }
        var smoothedPlus = (1..period).sumOf { plusDm[it] }
        var smoothedMinus = (1..period).sumOf { minusDm[it] }

        val dxValues = ArrayList<Double>(size)
        for (i in period until size) {
            val plusDi = if (smoothedTr > 0) 100.0 * smoothedPlus / smoothedTr else 0.0
            val minusDi = if (smoothedTr > 0) 100.0 * smoothedMinus / smoothedTr else 0.0
            val diSum = plusDi + minusDi
            val dx = if (diSum > 0) abs(plusDi - minusDi) / diSum * 100.0 else 0.0
            dxValues.add(dx)
            if (i + 1 < size) {
                smoothedTr = smoothedTr - smoothedTr / period + tr[i + 1]
                smoothedPlus = smoothedPlus - smoothedPlus / period + plusDm[i + 1]
                smoothedMinus = smoothedMinus - smoothedMinus / period + minusDm[i + 1]
            }
        }

        // ADX = Wilder-smoothed DX; first ADX = mean of the first `period` DX values.
        if (dxValues.size < period) return List(size) { null }
        val result = ArrayList<Double?>(size)
        val firstAdxIndex = period + period - 1 // index of the candle that yields the first ADX
        for (i in 0 until min(size, firstAdxIndex)) result.add(null)

        var adx = dxValues.take(period).average()
        if (result.size < size) result.add(adx)
        for (dxIdx in period until dxValues.size) {
            adx = (adx * (period - 1) + dxValues[dxIdx]) / period
            if (result.size < size) result.add(adx)
        }
        return result
    }

    fun latestADX(candles: List<CandleStick>, period: Int = 14): Double? =
        calculateADX(candles, period).lastOrNull { it != null }

    /**
     * Classifies the volatility regime from the ATR distribution: the latest ATR is
     * ranked against its own recent history (percentile) and against its median
     * (spike detection for news-driven shocks).
     */
    fun detectVolatilityRegime(candles: List<CandleStick>, period: Int = 14): VolatilityRegime {
        val atrSeries = calculateATR(candles, period).filterNotNull()
        if (atrSeries.size < 5) return VolatilityRegime.NORMAL
        val latest = atrSeries.last()
        val sorted = atrSeries.sorted()
        val median = sorted[sorted.size / 2]
        // A nearly flat ATR distribution cannot express a regime: treat as NORMAL.
        val dispersion = if (median > 0) (sorted.last() - sorted.first()) / median else 0.0
        if (!dispersion.isFinite() || dispersion < 0.12) return VolatilityRegime.NORMAL
        if (latest > 2.2 * median) return VolatilityRegime.EXTREME

        val rank = sorted.count { it <= latest }.toDouble() / sorted.size
        return when {
            rank >= 0.85 -> VolatilityRegime.ELEVATED
            rank <= 0.18 -> VolatilityRegime.COMPRESSED
            else -> VolatilityRegime.NORMAL
        }
    }

    /** Contract size per standard lot for position sizing math (units per lot). */
    fun contractSizeFor(symbol: String): Double {
        val (base, _) = PairCatalog.parseSymbol(symbol) ?: return 100_000.0
        return when {
            PairCatalog.isCryptoBase(base) -> 1.0
            base == "XAU" || base == "XAG" -> 100.0
            else -> 100_000.0
        }
    }

    /**
     * Volatility-based position sizing: the stop distance is an ATR multiple that widens
     * in turbulent regimes, and the lot count solves `risk = lots * stopDistance * contract`.
     *
     * Assumption: quote currency is priced 1:1 in the account currency (true for USD-quoted
     * instruments, which is the whole app's tradable universe); otherwise a conversion note
     * is attached to the result instead of silently skewing the size.
     */
    fun computeVolatilitySizing(
        pair: CurrencyPair,
        candles: List<CandleStick>,
        atrPeriod: Int = 14,
        balance: Double = 10_000.0,
        riskPercent: Double = 1.0
    ): PositionSizing {
        val pip = pair.pipSize.takeIf { it > 0 } ?: 0.0001
        val atr = latestATR(candles, atrPeriod) ?: (pair.currentPrice * 0.0015)
        val regime = detectVolatilityRegime(candles, atrPeriod)

        val rawStop = max(atr * regime.atrStopMultiplier, MIN_STOP_PIPS * pip)
        val stop = min(rawStop, max(MAX_STOP_PIPS * pip, atr * 4))

        val atrPips = if (pip > 0) atr / pip else 0.0
        val stopPips = if (pip > 0) stop / pip else 0.0
        val tp1Pips = stopPips * 1.8
        val tp2Pips = stopPips * 3.2

        val riskAmount = max(balance, 0.0) * (riskPercent.coerceIn(0.05, 5.0) / 100.0)
        val contract = contractSizeFor(pair.symbol)
        val perLotRisk = stop * contract
        val lots = if (perLotRisk > 0) floor((riskAmount / perLotRisk) * 100.0) / 100.0 else 0.0

        val note = if (pair.quoteCurrency != "USD" && pair.quoteCurrency.isNotEmpty()) {
            "Risk priced in ${pair.quoteCurrency}; convert to account currency for exact lots."
        } else {
            ""
        }

        return PositionSizing(
            atrValue = atr,
            atrPips = atrPips,
            regime = regime,
            stopDistance = stop,
            stopLossPips = stopPips,
            takeProfit1Pips = tp1Pips,
            takeProfit2Pips = tp2Pips,
            riskRewardRatio = if (stopPips > 0) tp1Pips / stopPips else 1.8,
            riskAmount = riskAmount,
            contractSize = contract,
            suggestedLots = lots.coerceAtLeast(if (riskAmount > 0) 0.01 else 0.0),
            note = note
        )
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /**
     * Detects support and resistance key price zones from swing highs and lows
     */
    fun detectSupportResistance(candles: List<CandleStick>): Pair<List<Double>, List<Double>> {
        if (candles.size < 10) return Pair(emptyList(), emptyList())
        val supports = mutableListOf<Double>()
        val resistances = mutableListOf<Double>()

        for (i in 2 until candles.size - 2) {
            val prev2 = candles[i - 2]
            val prev1 = candles[i - 1]
            val curr = candles[i]
            val next1 = candles[i + 1]
            val next2 = candles[i + 2]

            // Swing High -> Resistance
            if (curr.high > prev1.high && curr.high > prev2.high && curr.high > next1.high && curr.high > next2.high) {
                resistances.add(curr.high)
            }
            // Swing Low -> Support
            if (curr.low < prev1.low && curr.low < prev2.low && curr.low < next1.low && curr.low < next2.low) {
                supports.add(curr.low)
            }
        }

        return Pair(supports.takeLast(3), resistances.takeLast(3))
    }

    /**
     * Automated Pattern Recognition across candle history
     */
    fun detectPatterns(candles: List<CandleStick>): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        if (candles.size < 5) return patterns

        for (i in 2 until candles.size) {
            val cCurr = candles[i]
            val cPrev = candles[i - 1]
            val cPrev2 = candles[i - 2]

            // 1. Bullish Engulfing
            if (!cPrev.isBullish && cCurr.isBullish && cCurr.close > cPrev.open && cCurr.open < cPrev.close) {
                patterns.add(DetectedPattern(PatternType.BULLISH_ENGULFING, i, cCurr.close, 90))
            }

            // 2. Bearish Engulfing
            if (cPrev.isBullish && !cCurr.isBullish && cCurr.close < cPrev.open && cCurr.open > cPrev.close) {
                patterns.add(DetectedPattern(PatternType.BEARISH_ENGULFING, i, cCurr.close, 88))
            }

            // 3. Morning Star
            if (!cPrev2.isBullish && abs(cPrev.close - cPrev.open) < (cPrev2.high - cPrev2.low) * 0.3 && cCurr.isBullish && cCurr.close > (cPrev2.open + cPrev2.close) / 2) {
                patterns.add(DetectedPattern(PatternType.MORNING_STAR, i, cCurr.close, 85))
            }

            // 4. Support Bounce (long lower wick at recent low)
            val bodyLen = abs(cCurr.close - cCurr.open)
            val lowerWick = min(cCurr.open, cCurr.close) - cCurr.low
            if (lowerWick > bodyLen * 2.0 && cCurr.isBullish) {
                patterns.add(DetectedPattern(PatternType.SUPPORT_BOUNCE, i, cCurr.low, 82))
            }

            // 5. Resistance Rejection (long upper wick)
            val upperWick = cCurr.high - max(cCurr.open, cCurr.close)
            if (upperWick > bodyLen * 2.0 && !cCurr.isBullish) {
                patterns.add(DetectedPattern(PatternType.RESISTANCE_REJECTION, i, cCurr.high, 84))
            }
        }

        // 6. Double Top & Head and Shoulders (Requires larger window)
        for (i in 10 until candles.size) {
            val window = candles.subList(i - 10, i + 1)
            val highs = window.map { it.high }

            // Find local maxima in this window
            val peaks = mutableListOf<Pair<Int, Double>>()
            for (j in 1 until window.lastIndex) {
                if (window[j].high > window[j - 1].high && window[j].high > window[j + 1].high) {
                    peaks.add(Pair(i - 10 + j, window[j].high))
                }
            }

            if (peaks.size >= 2) {
                val lastPeak = peaks.last()
                val prevPeak = peaks[peaks.size - 2]

                // Double Top check
                val priceDiff = abs(lastPeak.second - prevPeak.second) / prevPeak.second
                val indexDiff = lastPeak.first - prevPeak.first
                if (priceDiff < 0.002 && indexDiff in 3..8) {
                    // Check if there is a dip between them
                    val dip = window.subList(prevPeak.first - (i - 10) + 1, lastPeak.first - (i - 10)).minOf { it.low }
                    if (prevPeak.second - dip > (prevPeak.second * 0.001)) { // Valid dip
                        // Don't add duplicate double tops for the same peak
                        if (patterns.none { it.patternType == PatternType.DOUBLE_TOP && it.candleIndex == lastPeak.first }) {
                            patterns.add(DetectedPattern(PatternType.DOUBLE_TOP, lastPeak.first, lastPeak.second, 80))
                        }
                    }
                }
            }

            if (peaks.size >= 3) {
                val rightShoulder = peaks.last()
                val head = peaks[peaks.size - 2]
                val leftShoulder = peaks[peaks.size - 3]

                // Head and Shoulders check
                if (head.second > leftShoulder.second && head.second > rightShoulder.second) {
                    val shoulderDiff = abs(leftShoulder.second - rightShoulder.second) / leftShoulder.second
                    if (shoulderDiff < 0.003) {
                        if (patterns.none { it.patternType == PatternType.HEAD_AND_SHOULDERS && it.candleIndex == rightShoulder.first }) {
                            patterns.add(DetectedPattern(PatternType.HEAD_AND_SHOULDERS, rightShoulder.first, rightShoulder.second, 85))
                        }
                    }
                }
            }
        }

        return patterns
    }

    // ------------------------------------------------------------------
    // Signal generation
    // ------------------------------------------------------------------

    /**
     * Generate dynamic trade signal from full technical confluence.
     *
     * Scoring is multi-factor (EMA stack, long-term trend filter, RSI, MACD momentum
     * slope, Bollinger position, ATR-validated structure and candlestick patterns),
     * weighted by trend strength (ADX). Stops/targets and the suggested lot size are
     * computed from ATR and the volatility regime — never fixed pip counts.
     */
    fun generateTradeSignal(
        pair: CurrencyPair,
        candles: List<CandleStick>,
        timeframe: Timeframe,
        settings: IndicatorSettings = IndicatorSettings(),
        balance: Double = 10_000.0,
        riskPercent: Double = 1.0,
        dataQualityScore: Int = 100,
        dataSource: String = "SIMULATION"
    ): TradeSignal {
        val closes = candles.map { it.close }
        val currentPrice = pair.currentPrice.takeIf { it > 0 && it.isFinite() }
            ?: (closes.lastOrNull() ?: 0.0)

        val rsiList = calculateRSI(closes, settings.rsiPeriod)
        val currentRsi = rsiList.lastOrNull() ?: 50.0

        val emaFastList = calculateEMA(closes, settings.emaPeriod1)
        val emaSlowList = calculateEMA(closes, settings.emaPeriod2)
        val emaTrendList = if (closes.size >= settings.emaPeriod3) calculateEMA(closes, settings.emaPeriod3) else emptyList()

        val currentEmaFast = emaFastList.lastOrNull() ?: currentPrice
        val currentEmaSlow = emaSlowList.lastOrNull() ?: currentPrice
        val currentEmaTrend = emaTrendList.lastOrNull()

        val macd = calculateMACD(
            closes,
            settings.macdFastPeriod,
            settings.macdSlowPeriod,
            settings.macdSignalPeriod
        )
        val hist = macd.histogram.filterNotNull()
        val adx = latestADX(candles)

        val sizing = computeVolatilitySizing(pair, candles, atrPeriod = 14, balance = balance, riskPercent = riskPercent)

        val patterns = detectPatterns(candles)

        var score = 0
        val rationaleList = mutableListOf<String>()

        // 1. EMA cross (primary trend engine)
        if (currentEmaFast > currentEmaSlow) {
            score += 2
            rationaleList.add("EMA ${settings.emaPeriod1}/${settings.emaPeriod2} Bullish Cross")
        } else {
            score -= 2
            rationaleList.add("EMA ${settings.emaPeriod1}/${settings.emaPeriod2} Bearish Cross")
        }

        // 2. Long-term trend filter (only when enough history exists)
        if (currentEmaTrend != null && currentPrice > 0) {
            if (currentPrice > currentEmaTrend) {
                score += 1
                rationaleList.add("Price above EMA ${settings.emaPeriod3}")
            } else {
                score -= 1
                rationaleList.add("Price below EMA ${settings.emaPeriod3}")
            }
        }

        // 3. RSI mean-reversion extremes
        if (currentRsi < settings.rsiOversold) {
            score += 3
            rationaleList.add("RSI Oversold (${currentRsi.toInt()})")
        } else if (currentRsi > settings.rsiOverbought) {
            score -= 3
            rationaleList.add("RSI Overbought (${currentRsi.toInt()})")
        } else {
            rationaleList.add("RSI Neutral (${currentRsi.toInt()})")
        }

        // 4. MACD momentum and its slope
        val macdStatus: String = when {
            hist.size >= 2 && hist.last() > 0 && hist.last() > hist[hist.size - 2] -> {
                score += 2; rationaleList.add("MACD Bullish & Accelerating"); "Bullish Momentum (accelerating)"
            }
            hist.size >= 2 && hist.last() < 0 && hist.last() < hist[hist.size - 2] -> {
                score -= 2; rationaleList.add("MACD Bearish & Accelerating"); "Bearish Pressure (accelerating)"
            }
            hist.isNotEmpty() && hist.last() > 0 -> { score += 1; "Bullish Momentum (easing)" }
            hist.isNotEmpty() && hist.last() < 0 -> { score -= 1; "Bearish Pressure (easing)" }
            else -> "MACD Neutral"
        }

        // 5. Bollinger Band position / squeeze
        val bands = calculateBollingerBands(closes, settings.bollingerPeriod, settings.bollingerStdDev).lastOrNull()
        if (bands != null && currentPrice > 0) {
            when {
                currentPrice <= bands.lower -> { score += 1; rationaleList.add("Price at lower Bollinger band") }
                currentPrice >= bands.upper -> { score -= 1; rationaleList.add("Price at upper Bollinger band") }
            }
        }

        // 6. Candlestick patterns — pattern score capped so a noisy history can't dominate
        var patternScore = 0
        for (pat in patterns) {
            patternScore += if (pat.patternType.isBullish) 3 else -3
        }
        patternScore = patternScore.coerceIn(-6, 6)
        if (patternScore != 0) {
            score += patternScore
            rationaleList.add("Patterns ${if (patternScore > 0) "+$patternScore" else patternScore}")
        }

        // 7. ADX trend-strength weighting (mute in chop, amplify in trend)
        if (adx != null) {
            when {
                adx >= 25.0 -> {
                    score = when {
                        score > 0 -> amplifyPositive(score)
                        score < 0 -> amplifyNegative(score)
                        else -> score
                    }
                    rationaleList.add("ADX ${adx.toInt()} confirms directional trend")
                }
                adx <= 18.0 -> rationaleList.add("ADX ${adx.toInt()} signals range-bound chop")
            }
        }

        val regime = sizing.regime

        // Volatility veto: never fire a marginal signal in news-grade turbulence.
        var signalType = when {
            score >= 3 -> SignalType.BUY
            score <= -3 -> SignalType.SELL
            else -> SignalType.NEUTRAL
        }
        if (regime == VolatilityRegime.EXTREME && abs(score) < 6 && signalType != SignalType.NEUTRAL) {
            signalType = SignalType.NEUTRAL
            rationaleList.add("Signal suppressed: extreme volatility regime")
        }

        // Structure-aware ATR stops. Entry anchors on live price; the stop is widened
        // past the nearest structural extreme when it lies inside the ATR cushion.
        val isBuy = signalType == SignalType.BUY
        val isSell = signalType == SignalType.SELL
        val (supports, resistances) = detectSupportResistance(candles)
        val stop = sizing.stopDistance

        val (sl, tp1, tp2) = when {
            isBuy || (!isSell && currentEmaFast > currentEmaSlow) -> {
                val structural = supports.lastOrNull()?.let { currentPrice - it } ?: 0.0
                val effStop = max(stop, min(structural + sizing.atrValue * 0.25, stop * 1.6))
                Triple(
                    currentPrice - effStop,
                    currentPrice + effStop * 1.8,
                    currentPrice + effStop * 3.2
                )
            }
            else -> {
                val structural = resistances.lastOrNull()?.let { it - currentPrice } ?: 0.0
                val effStop = max(stop, min(structural + sizing.atrValue * 0.25, stop * 1.6))
                Triple(
                    currentPrice + effStop,
                    currentPrice - effStop * 1.8,
                    currentPrice - effStop * 3.2
                )
            }
        }

        val slDistance = abs(currentPrice - sl).coerceAtLeast(1e-9)
        val rrr = abs(tp1 - currentPrice) / slDistance
        val stopPips = if (pair.pipSize > 0) slDistance / pair.pipSize else 0.0

        var confidence = when {
            signalType == SignalType.NEUTRAL -> 40 + abs(score) * 5
            else -> 52 + abs(score) * 6
        }
        if (adx != null && adx <= 18.0) confidence = min(confidence, 68)
        if (regime == VolatilityRegime.EXTREME) confidence = min(confidence, 82)
        if (dataQualityScore < 85) confidence -= (85 - dataQualityScore) / 5
        confidence = confidence.coerceIn(35, 97)

        rationaleList.add(
            "ATR ${fmt(sizing.atrPips, 1)}p (${regime.label}) — stop ${fmt(stopPips, 1)}p @1:${fmt(sizing.riskRewardRatio, 1)}R"
        )
        val summary = rationaleList.joinToString(" • ")

        return TradeSignal(
            id = "${pair.symbol}_${timeframe.name}_${System.currentTimeMillis() / 10000}",
            pairSymbol = pair.symbol,
            type = signalType,
            entryPrice = currentPrice,
            stopLoss = sl,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            riskRewardRatio = rrr,
            confidenceScore = confidence,
            timeframe = timeframe,
            detectedPatterns = patterns.map { it.patternType },
            rsiValue = currentRsi,
            macdStatus = macdStatus,
            summaryRationale = summary,
            atrValue = sizing.atrValue,
            stopLossPips = stopPips,
            volatilityRegime = regime,
            suggestedLotSize = sizing.suggestedLots,
            dataQualityScore = dataQualityScore,
            dataSource = dataSource
        )
    }

    // ------------------------------------------------------------------
    // Chart overlay generation
    // ------------------------------------------------------------------

    /**
     * Dynamically generates rich AI chart annotations and technical redraw overlays
     * (Supply/Demand zones, smart trendlines, Fibonacci retracements, and target vectors).
     */
    fun generateAiChartOverlay(
        pair: CurrencyPair,
        candles: List<CandleStick>,
        timeframe: Timeframe,
        focusType: String? = null
    ): AiChartOverlayState {
        if (candles.size < 10) {
            return AiChartOverlayState(pairSymbol = pair.symbol, timeframe = timeframe)
        }

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }

        val minPrice = lows.minOrNull() ?: pair.currentPrice * 0.99
        val maxPrice = highs.maxOrNull() ?: pair.currentPrice * 1.01
        val currentPrice = pair.currentPrice
        val pip = pair.pipSize

        // Find swing highs and swing lows (local extrema)
        val swingHighs = mutableListOf<Pair<Int, Double>>()
        val swingLows = mutableListOf<Pair<Int, Double>>()

        val window = 4
        for (i in window until candles.size - window) {
            val h = candles[i].high
            val l = candles[i].low
            val isHigh = (1..window).all { h >= candles[i - it].high && h >= candles[i + it].high }
            val isLow = (1..window).all { l <= candles[i - it].low && l <= candles[i + it].low }

            if (isHigh) swingHighs.add(i to h)
            if (isLow) swingLows.add(i to l)
        }

        // Zones calculation
        val zones = mutableListOf<AiZone>()
        val zoneBuffer = pip * 12.0

        // Major Demand / Support Zone near lowest swing low
        val primaryLow = swingLows.minByOrNull { it.second }?.second ?: (minPrice + pip * 5)
        zones.add(
            AiZone(
                type = AiZoneType.DEMAND_SUPPORT,
                priceTop = primaryLow + zoneBuffer * 0.8,
                priceBottom = primaryLow - zoneBuffer * 0.4,
                label = "Institutional Demand (${String.format(Locale.US, "%.4f", primaryLow)})",
                strength = 0.92,
                confidence = 94
            )
        )

        // Major Supply / Resistance Zone near highest swing high
        val primaryHigh = swingHighs.maxByOrNull { it.second }?.second ?: (maxPrice - pip * 5)
        zones.add(
            AiZone(
                type = AiZoneType.SUPPLY_RESISTANCE,
                priceTop = primaryHigh + zoneBuffer * 0.4,
                priceBottom = primaryHigh - zoneBuffer * 0.8,
                label = "Institutional Supply (${String.format(Locale.US, "%.4f", primaryHigh)})",
                strength = 0.88,
                confidence = 91
            )
        )

        // Mid-range Liquidity / Flip Zone if enough space
        if (abs(primaryHigh - primaryLow) > zoneBuffer * 4) {
            val midLevel = (primaryHigh + primaryLow) / 2.0
            zones.add(
                AiZone(
                    type = AiZoneType.BREAKOUT_ZONE,
                    priceTop = midLevel + zoneBuffer * 0.4,
                    priceBottom = midLevel - zoneBuffer * 0.4,
                    label = "Liquidity Pivot (${String.format(Locale.US, "%.4f", midLevel)})",
                    strength = 0.75,
                    confidence = 86
                )
            )
        }

        // Smart Trendlines calculation
        val trendlines = mutableListOf<AiTrendline>()

        if (swingLows.size >= 2) {
            val l1 = swingLows[swingLows.size - 2]
            val l2 = swingLows.last()
            trendlines.add(
                AiTrendline(
                    type = AiTrendlineType.BULLISH_SUPPORT,
                    candleIndex1 = l1.first,
                    price1 = l1.second,
                    candleIndex2 = l2.first,
                    price2 = l2.second,
                    label = "AI Ascending Support Vector"
                )
            )
        } else if (candles.size >= 25) {
            trendlines.add(
                AiTrendline(
                    type = AiTrendlineType.BULLISH_SUPPORT,
                    candleIndex1 = max(0, candles.size - 35),
                    price1 = lows.takeLast(35).minOrNull() ?: currentPrice,
                    candleIndex2 = candles.size - 1,
                    price2 = currentPrice - (pip * 15),
                    label = "AI Support Trendline"
                )
            )
        }

        if (swingHighs.size >= 2) {
            val h1 = swingHighs[swingHighs.size - 2]
            val h2 = swingHighs.last()
            trendlines.add(
                AiTrendline(
                    type = AiTrendlineType.BEARISH_RESISTANCE,
                    candleIndex1 = h1.first,
                    price1 = h1.second,
                    candleIndex2 = h2.first,
                    price2 = h2.second,
                    label = "AI Descending Resistance Vector",
                    isDashed = true
                )
            )
        }

        // Fibonacci calculation from swing low to swing high
        val fibLow = swingLows.minByOrNull { it.second }?.second ?: minPrice
        val fibHigh = swingHighs.maxByOrNull { it.second }?.second ?: maxPrice
        val fibDiff = fibHigh - fibLow

        val fibRatios = listOf(
            0.0 to Pair("0.0%", "Swing Base"),
            0.236 to Pair("23.6%", "Minor Retracement"),
            0.382 to Pair("38.2%", "Healthy Pullback"),
            0.500 to Pair("50.0%", "Equilibrium Level"),
            0.618 to Pair("61.8%", "Golden Pocket Zone"),
            0.786 to Pair("78.6%", "Deep Discount"),
            1.000 to Pair("100.0%", "Swing Peak")
        )

        val fibLevels = fibRatios.map { (ratio, info) ->
            AiFibonacciLevel(
                ratio = ratio,
                percentage = info.first,
                price = fibLow + fibDiff * ratio,
                description = info.second
            )
        }

        // Target projection & execution setup (ATR-sized via the shared signal generator)
        val signal = generateTradeSignal(pair, candles, timeframe)
        val isBuy = signal.type == SignalType.BUY || (signal.type == SignalType.NEUTRAL && currentPrice > (fibLow + fibHigh) / 2)
        val targets = listOf(
            AiTargetProjection(
                entryPrice = currentPrice,
                stopLoss = signal.stopLoss,
                takeProfit1 = signal.takeProfit1,
                takeProfit2 = signal.takeProfit2,
                riskRewardRatio = signal.riskRewardRatio,
                isBuy = isBuy,
                rationale = "AI Target Model: ${signal.volatilityRegime.label} ATR stop " +
                    "${String.format(Locale.US, "%.1f", signal.stopLossPips)} pips " +
                    "(${String.format(Locale.US, "%.4f", signal.stopLoss)}) | TP1 ${String.format(Locale.US, "%.4f", signal.takeProfit1)} " +
                    "(RR 1:${String.format(Locale.US, "%.1f", signal.riskRewardRatio)})"
            )
        )

        val detectedPatterns = detectPatterns(candles)

        return AiChartOverlayState(
            pairSymbol = pair.symbol,
            timeframe = timeframe,
            zones = zones,
            trendlines = trendlines,
            targets = targets,
            fibonacciLevels = fibLevels,
            fibonacciP1 = fibLow,
            fibonacciP2 = fibHigh,
            detectedPatterns = detectedPatterns,
            analysisSummary = "AI redrew chart structure: ${zones.size} Supply/Demand Zones, ${trendlines.size} Trendlines, Fibonacci Golden Ratio, and ${if (isBuy) "BUY" else "SELL"} Target Projections.",
            timestamp = System.currentTimeMillis(),
            isRedrawing = false
        )
    }

    // ------------------------------------------------------------------
    // Formatting helpers (shared with the AI response engine)
    // ------------------------------------------------------------------

    /** Decimal places appropriate for the pair (5 for majors, 3 for JPY pairs, 2 otherwise). */
    fun decimalsForPip(pipSize: Double): Int = when {
        pipSize <= 0.0002 -> 5
        pipSize <= 0.02 -> 3
        else -> 2
    }

    fun fmt(price: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", price)

    /** Formats a price with the pair-appropriate precision. */
    fun fmtPrice(price: Double, pipSize: Double): String = fmt(price, decimalsForPip(pipSize))

    // Tiny helpers for ADX-weighted score amplification (roughly +25%, sign-preserving).
    private fun amplifyPositive(score: Int): Int = score + max(1, score / 4)
    private fun amplifyNegative(score: Int): Int = score - max(1, -score / 4)
}
