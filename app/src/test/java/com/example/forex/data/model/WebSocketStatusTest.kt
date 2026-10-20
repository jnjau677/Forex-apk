package com.example.forex.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-06 honesty contract: simulated prices must never be labelled as live.
 */
class WebSocketStatusTest {

    @Test
    fun `simulated feed is never labelled live or connected`() {
        val label = WebSocketStatus.SIMULATED.label
        assertFalse(label.contains("Live", ignoreCase = true))
        assertFalse(label.contains("Connected", ignoreCase = true))
        assertTrue(label.contains("Simulat", ignoreCase = true))
    }

    @Test
    fun `connected label reflects the Binance reference feed`() {
        assertTrue(WebSocketStatus.CONNECTED.label.contains("Live"))
    }
}
