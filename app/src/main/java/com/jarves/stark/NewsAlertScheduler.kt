package com.jarves.stark

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat

class NewsAlertScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun schedule(eventTimeMs: Long, title: String, message: String) {
        val offsets = longArrayOf(5 * 60 * 60 * 1000L, 30 * 60 * 1000L, 15 * 60 * 1000L, 5 * 60 * 1000L)
        offsets.forEachIndexed { i, off ->
            val whenMs = eventTimeMs - off
            if (whenMs <= System.currentTimeMillis()) return@forEachIndexed
            val id = (eventTimeMs % 1000000).toInt() + i
            val intent = Intent(context, NewsAlertReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("message", message)
                putExtra("id", id)
            }
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
        }
    }
}

class NewsAlertReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val ch = "jarves_news"
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(ch, "JARVES News Alerts", NotificationManager.IMPORTANCE_HIGH))
        val message = i.getStringExtra("message") ?: "Market news alert"
        val n = NotificationCompat.Builder(c, ch)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(i.getStringExtra("title") ?: "JARVES Alert")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        nm.notify(i.getIntExtra("id", 1), n)
    }
}
