package com.example.forex.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SignalWorker contract tests under Robolectric.
 *
 * The scan below pins a single symbol, disables network fetches and runs against an
 * empty Room history, so the deterministic outcome is exercised end-to-end: the
 * symbol is skipped (no 30 validated candles), nothing is notified and the worker
 * still completes successfully with a structured output note.
 */
@RunWith(RobolectricTestRunner::class)
class SignalWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `scan without usable history skips symbol and succeeds with structured output`() {
        val worker = TestListenableWorkerBuilder<SignalWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString(SignalWorker.KEY_SYMBOLS, "EUR/USD")
                    .putBoolean(SignalWorker.KEY_ALLOW_NETWORK, false)
                    .build()
            )
            .build()

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(1, output.getInt(SignalWorker.KEY_OUTPUT_SCANNED, -1))
        assertEquals(0, output.getInt(SignalWorker.KEY_OUTPUT_NOTIFIED, -1))
        val note = output.getString(SignalWorker.KEY_OUTPUT_NOTE).orEmpty()
        assertTrue(note.contains("0 actionable"))
    }
}
