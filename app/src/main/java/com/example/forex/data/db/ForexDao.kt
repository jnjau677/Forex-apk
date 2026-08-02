package com.example.forex.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ForexDao {
    // Saved Signals
    @Query("SELECT * FROM saved_signals ORDER BY timestamp DESC")
    fun getAllSavedSignals(): Flow<List<SignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalEntity)

    @Query("DELETE FROM saved_signals WHERE id = :signalId")
    suspend fun deleteSignal(signalId: String)

    // Custom Alerts
    @Query("SELECT * FROM custom_alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("UPDATE custom_alerts SET isTriggered = :triggered, isActive = :active WHERE id = :alertId")
    suspend fun updateAlertStatus(alertId: String, triggered: Boolean, active: Boolean)

    @Query("DELETE FROM custom_alerts WHERE id = :alertId")
    suspend fun deleteAlert(alertId: String)

    // Watchlist
    @Query("SELECT * FROM watchlist_items")
    fun getWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist_items WHERE symbol = :symbol")
    suspend fun removeFromWatchlist(symbol: String)

    // Historical Candles
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<CandleEntity>)

    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestamp ASC")
    suspend fun getCandles(symbol: String, timeframe: String): List<CandleEntity>
    
    @Query("DELETE FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun deleteCandles(symbol: String, timeframe: String)
}
