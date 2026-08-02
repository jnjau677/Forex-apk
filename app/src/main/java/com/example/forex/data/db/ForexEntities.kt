package com.example.forex.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_signals")
data class SignalEntity(
    @PrimaryKey val id: String,
    val pairSymbol: String,
    val signalType: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val riskRewardRatio: Double,
    val confidenceScore: Int,
    val timeframeLabel: String,
    val summaryRationale: String,
    val timestamp: Long,
    val isBookmarked: Boolean = true
)

@Entity(tableName = "custom_alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val pairSymbol: String,
    val conditionType: String,
    val targetValue: Double,
    val isTriggered: Boolean,
    val isActive: Boolean,
    val note: String,
    val createdAt: Long
)

@Entity(tableName = "watchlist_items")
data class WatchlistEntity(
    @PrimaryKey val symbol: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "historical_candles",
    primaryKeys = ["symbol", "timeframe", "timestamp"]
)
data class CandleEntity(
    val symbol: String,
    val timeframe: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)
