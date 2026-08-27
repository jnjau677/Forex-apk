package com.example.forex.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.forex.data.model.SignalType
import com.example.forex.data.model.TradeSignal
import kotlin.random.Random

class SignalWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Mocking a signal generation for demonstration purposes
            val pairs = listOf("EUR/USD", "GBP/JPY", "XAU/USD", "BTC/USD")
            val randomPair = pairs.random()
            
            val isBuy = Random.nextBoolean()
            val signalType = if (isBuy) "BUY" else "SELL"
            
            val price = 100.0 + Random.nextDouble(1.0, 10.0)
            
            sendNotification(randomPair, signalType, price)
            
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private fun sendNotification(pair: String, type: String, price: Double) {
        val channelId = "trade_signals_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Trade Signals"
            val descriptionText = "Notifications for new trade signals"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New $type Signal: $pair")
            .setContentText("Pattern identified. Suggested Entry: ${String.format(java.util.Locale.US, "%.4f", price)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(Random.nextInt(), builder.build())
        }
    }
}
