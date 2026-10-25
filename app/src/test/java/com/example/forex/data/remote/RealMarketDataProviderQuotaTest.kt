package com.example.forex.data.remote

import com.example.forex.data.model.Timeframe
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sin

/**
 * F-10: the Alpha Vantage daily budget is process-wide. Previously every
 * [RealMarketDataProvider] instance (repository, worker, …) kept its own counter,
 * so the app could silently burn 2–3× the free-tier quota.
 */
class RealMarketDataProviderQuotaTest {

    private class FakeApi(private val csv: String) : AlphaVantageApi {
        var calls = 0
        override suspend fun query(params: Map<String, String>): Response<ResponseBody> {
            calls++
            return Response.success(csv.toResponseBody("text/csv".toMediaType()))
        }
    }

    @Before
    fun resetSharedQuota() {
        RealMarketDataProvider.sharedRequestsToday = 0
        RealMarketDataProvider.sharedRequestDayUtc = ""
        RealMarketDataProvider.sharedLastRequestAt = 0L
    }

    private fun dailyCsv(rows: Int = 60): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dayMs = 86_400_000L
        val start = 1_704_067_200_000L // 2024-01-01 UTC
        val sb = StringBuilder("timestamp,open,high,low,close,volume\n")
        for (i in 0 until rows) {
            val close = 1.0850 + sin(i * 0.35) * 0.004
            val open = close - 0.0004
            sb.append(fmt.format(Date(start + i * dayMs))).append(',')
                .append(String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f,1000", open, close + 0.0009, open - 0.0009, close))
                .append('\n')
        }
        return sb.toString()
    }

    @Test
    fun `daily quota is shared across provider instances`() = runBlocking {
        val csv = dailyCsv()
        val first = RealMarketDataProvider(
            apiKey = "key-one", dailyQuota = 1, minRequestIntervalMs = 0,
            api = FakeApi(csv), clock = { 1_710_000_000_000L }
        )
        val second = RealMarketDataProvider(
            apiKey = "key-two", dailyQuota = 1, minRequestIntervalMs = 0,
            api = FakeApi(csv), clock = { 1_710_000_000_000L }
        )

        val firstResult = first.fetchCandles("EUR/USD", Timeframe.D1)
        assertTrue("first fetch should succeed but was: ${firstResult.exceptionOrNull()}", firstResult.isSuccess)
        assertEquals("one network request should have been counted", 1, RealMarketDataProvider.sharedRequestsToday)

        val blocked = second.fetchCandles("GBP/USD", Timeframe.D1)
        assertTrue(
            "expected DailyQuotaExhausted, got $blocked",
            blocked.exceptionOrNull() is MarketDataException.DailyQuotaExhausted
        )
    }

    @Test
    fun `persisted quota is adopted for the current day`() = runBlocking {
        val csv = dailyCsv()
        val todayUtc = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(1_710_000_000_000L))
        val provider = RealMarketDataProvider(
            apiKey = "key",
            dailyQuota = 25,
            minRequestIntervalMs = 0,
            api = FakeApi(csv),
            clock = { 1_710_000_000_000L },
            quotaPersistLoad = { todayUtc to 24 },
            quotaPersistSave = { _, _ -> }
        )
        // 24 already used today (persisted) + this one = 25 = quota ceiling reached.
        val firstResult = provider.fetchCandles("EUR/USD", Timeframe.D1)
        assertTrue("fetch should succeed but was: ${firstResult.exceptionOrNull()}", firstResult.isSuccess)
        assertEquals(25, RealMarketDataProvider.sharedRequestsToday)

        val blocked = provider.fetchCandles("GBP/USD", Timeframe.D1, forceRefresh = true)
        assertTrue(blocked.exceptionOrNull() is MarketDataException.DailyQuotaExhausted)
    }
}
