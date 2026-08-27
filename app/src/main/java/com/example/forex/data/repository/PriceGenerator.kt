package com.example.forex.data.repository

import com.example.forex.data.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

object TechnicalAnalysisEngine {

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
     * Calculates Relative Strength Index (RSI 14)
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
    data class BollingerBand(val upper: Double, val middle: Double, val lower: Double)

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
                if (window[j].high > window[j-1].high && window[j].high > window[j+1].high) {
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

    /**
     * Generate dynamic trade signal based on full technical confluence
     */
    fun generateTradeSignal(
        pair: CurrencyPair,
        candles: List<CandleStick>,
        timeframe: Timeframe,
        settings: IndicatorSettings = IndicatorSettings()
    ): TradeSignal {
        val closes = candles.map { it.close }
        val rsiList = calculateRSI(closes, settings.rsiPeriod)
        val currentRsi = rsiList.lastOrNull() ?: 50.0
        
        val emaFastList = calculateEMA(closes, settings.emaPeriod1)
        val emaSlowList = calculateEMA(closes, settings.emaPeriod2)
        
        val currentEmaFast = emaFastList.lastOrNull() ?: pair.currentPrice
        val currentEmaSlow = emaSlowList.lastOrNull() ?: pair.currentPrice
        
        val patterns = detectPatterns(candles)
        val currentPrice = pair.currentPrice
        
        var score = 0 // + for BUY, - for SELL
        val rationaleList = mutableListOf<String>()
        
        if (currentEmaFast > currentEmaSlow) {
            score += 2
            rationaleList.add("EMA ${settings.emaPeriod1}/${settings.emaPeriod2} Bullish Cross")
        } else {
            score -= 2
            rationaleList.add("EMA ${settings.emaPeriod1}/${settings.emaPeriod2} Bearish Cross")
        }
        
        if (currentRsi < settings.rsiOversold) {
            score += 3
            rationaleList.add("RSI Oversold (${currentRsi.toInt()})")
        } else if (currentRsi > settings.rsiOverbought) {
            score -= 3
            rationaleList.add("RSI Overbought (${currentRsi.toInt()})")
        } else {
            rationaleList.add("RSI Neutral (${currentRsi.toInt()})")
        }
        
        for (pat in patterns) {
            if (pat.patternType.isBullish) {
                score += 3
                rationaleList.add("Pattern: ${pat.patternType.title}")
            } else {
                score -= 3
                rationaleList.add("Pattern: ${pat.patternType.title}")
            }
        }
        
        val signalType = when {
            score >= 3 -> SignalType.BUY
            score <= -3 -> SignalType.SELL
            else -> SignalType.NEUTRAL
        }
        
        val pipMultiplier = pair.pipSize
        val stopLossPips = 25.0
        val tp1Pips = 45.0
        val tp2Pips = 90.0
        
        val (sl, tp1, tp2) = if (signalType == SignalType.BUY) {
            Triple(
                currentPrice - (stopLossPips * pipMultiplier),
                currentPrice + (tp1Pips * pipMultiplier),
                currentPrice + (tp2Pips * pipMultiplier)
            )
        } else {
            Triple(
                currentPrice + (stopLossPips * pipMultiplier),
                currentPrice - (tp1Pips * pipMultiplier),
                currentPrice - (tp2Pips * pipMultiplier)
            )
        }
        
        val rrr = tp1Pips / stopLossPips
        val confidence = min(96, max(60, 70 + (abs(score) * 5)))
        
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
            macdStatus = if (score > 0) "Bullish Momentum" else "Bearish Pressure",
            summaryRationale = summary
        )
    }

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

        // Target projection & execution setup
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
                rationale = "AI Target Model: SL at ${String.format(Locale.US, "%.4f", signal.stopLoss)} | TP1 ${String.format(Locale.US, "%.4f", signal.takeProfit1)} (RR 1:${String.format(Locale.US, "%.1f", signal.riskRewardRatio)})"
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
}

