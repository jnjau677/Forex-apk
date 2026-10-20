package com.example

import com.example.forex.data.model.CandleStick
import com.example.forex.data.model.PairCatalog
import com.example.forex.data.model.Timeframe
import com.example.forex.data.repository.TechnicalAnalysisEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for the analysis entry points the UI depends on.
 * Deeper indicator/signal invariants live in TechnicalAnalysisEngineTest.
 */
class ExampleUnitTest {

    private val baseTime = 1_700_000_000_000L

    /** Trending OHLC series with a fixed per-bar range, starting at [start]. */
    private fun trendCandles(count: Int, start: Double, step: Double): List<CandleStick> =
        (0 until count).map { i ->
            val close = start + step * i
            CandleStick(baseTime + i * 3_600_000L, close - step, close + 0.0008, close - 0.0008, close, 500.0)
        }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testAiChartOverlayGeneration() {
        val candles = trendCandles(count = 100, start = 1.0850, step = 0.0003)
        val pair = PairCatalog.pairFor(
            "EUR/USD",
            candles.last().close,
            candles.maxOf { it.high },
            candles.minOf { it.low }
        )

        val overlay = TechnicalAnalysisEngine.generateAiChartOverlay(pair, candles, Timeframe.H1)

        assertNotNull(overlay)
        assertEquals("EUR/USD", overlay.pairSymbol)
        assertTrue("Should generate AI demand/supply zones", overlay.zones.isNotEmpty())
        assertTrue("Should generate AI Fibonacci levels", overlay.fibonacciLevels.isNotEmpty())
        assertTrue("Should compute Fibonacci levels with valid prices", overlay.fibonacciLevels.all { it.price > 0 })
        assertTrue("Fibonacci levels count should cover standard ratios", overlay.fibonacciLevels.size >= 5)
    }

    @Test
    fun testTradeSignalGeneration() {
        val candles = trendCandles(count = 100, start = 1.2700, step = 0.0003)
        val pair = PairCatalog.pairFor(
            "GBP/USD",
            candles.last().close,
            candles.maxOf { it.high },
            candles.minOf { it.low }
        )

        val signal = TechnicalAnalysisEngine.generateTradeSignal(pair, candles, Timeframe.H1)
        assertNotNull(signal)
        assertTrue("Signal confidence should be between 0 and 100", signal.confidenceScore in 0..100)
        assertTrue("Stop loss should be positive", signal.stopLoss > 0)
        assertTrue("Take profit should be positive", signal.takeProfit1 > 0)
    }
}
