package com.jarves.stark

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.math.abs

/** Background trading watch agent. It never places trades; it only observes and alerts. */
class MarketWatchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("jarves_watch", Context.MODE_PRIVATE)
        val client = MarketClient()
        var alerts = 0
        for (instrument in MarketClient.SUPPORTED_INSTRUMENTS) {
            try {
                val candles = client.candles(instrument.label, "15min", 80)
                val latest = candles.last().close
                val previous = prefs.getString("price:${instrument.label}", null)?.toDoubleOrNull()
                val oldBias = prefs.getString("bias:${instrument.label}", null)
                val analysis = TechnicalAnalyzer.analyze(instrument.label, "15min", candles)
                val move = if (previous != null && previous != 0.0) (latest - previous) / previous * 100.0 else 0.0
                val threshold = if (instrument.label.contains("Bitcoin") || instrument.label.contains("Ethereum") || instrument.label.contains("Litecoin")) 0.80 else 0.25
                val biasChanged = oldBias != null && oldBias != analysis.bias && analysis.bias != "RANGE / mixed"
                val lastAlert = prefs.getLong("alert:${instrument.label}", 0L)
                val cooldownOk = System.currentTimeMillis() - lastAlert >= 30 * 60 * 1000L
                if (cooldownOk && (abs(move) >= threshold || biasChanged)) {
                    val direction = if (move > 0) "तेज़ ऊपर" else if (move < 0) "नीचे" else "trend बदल रहा है"
                    val reason = if (biasChanged) "trend बदलकर ${analysis.bias} हुआ है" else "पिछली जाँच से ${"%.2f".format(move)}% movement"
                    notifyMarket(
                        "JARVES Market Alert — ${instrument.label}",
                        "${instrument.label} ${direction} है: ${"%.6f".format(latest)}. $reason. RSI ${"%.1f".format(analysis.indicators.rsi)}."
                    )
                    prefs.edit().putLong("alert:${instrument.label}", System.currentTimeMillis()).apply()
                    alerts++
                }
                prefs.edit().putString("price:${instrument.label}", latest.toString()).putString("bias:${instrument.label}", analysis.bias).apply()
            } catch (_: Exception) {
                // One instrument failing must not stop monitoring of the remaining instruments.
            }
        }
        return if (alerts >= 0) Result.success() else Result.retry()
    }

    private fun notifyMarket(title: String, message: String) {
        val channelId = "jarves_market"
        val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel(channelId, "JARVES Market Watch", android.app.NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        nm.notify((title.hashCode() xor System.currentTimeMillis().toInt()), notification)
    }
}
