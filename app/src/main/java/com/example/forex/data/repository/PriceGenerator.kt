package com.example.forex.data.repository

import com.example.forex.data.model.*
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
    fun generateTradeSignal(pair: CurrencyPair, candles: List<CandleStick>, timeframe: Timeframe): TradeSignal {
        val closes = candles.map { it.close }
        val rsiList = calculateRSI(closes)
        val currentRsi = rsiList.lastOrNull() ?: 50.0
        
        val ema20List = calculateEMA(closes, 20)
        val ema50List = calculateEMA(closes, 50)
        
        val currentEma20 = ema20List.lastOrNull() ?: pair.currentPrice
        val currentEma50 = ema50List.lastOrNull() ?: pair.currentPrice
        
        val patterns = detectPatterns(candles)
        val currentPrice = pair.currentPrice
        
        var score = 0 // + for BUY, - for SELL
        val rationaleList = mutableListOf<String>()
        
        if (currentEma20 > currentEma50) {
            score += 2
            rationaleList.add("EMA 20/50 Bullish Crossover")
        } else {
            score -= 2
            rationaleList.add("EMA 20/50 Bearish Crossover")
        }
        
        if (currentRsi < 35) {
            score += 3
            rationaleList.add("RSI Oversold (${currentRsi.toInt()})")
        } else if (currentRsi > 65) {
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
}
