package com.jarves.stark

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray

/** Fetches a public weekly economic-calendar feed and schedules relevant alerts automatically. */
class NewsWatchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        private const val CALENDAR_URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"
        private val RELEVANT = setOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "NZD")
    }

    override suspend fun doWork(): Result {
        return try {
            val text = get(CALENDAR_URL)
            val arr = JSONArray(text)
            val scheduler = NewsAlertScheduler(applicationContext)
            val seen = applicationContext.getSharedPreferences("jarves_news_seen", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val currency = o.optString("country").uppercase()
                val impact = o.optString("impact").lowercase()
                if (currency !in RELEVANT) continue
                if (impact != "high" && impact != "medium") continue
                val date = runCatching { OffsetDateTime.parse(o.optString("date")).toInstant().toEpochMilli() }.getOrNull() ?: continue
                if (date <= now || date > now + 8L * 24 * 60 * 60 * 1000) continue
                val title = o.optString("title", "Economic news")
                val key = "$currency|$title|$date"
                if (seen.getBoolean(key, false)) continue
                val local = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(date), ZoneId.systemDefault())
                val timeText = local.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))
                scheduler.schedule(date, "JARVES ${impact.uppercase()} News — $currency", "$title. Scheduled time: $timeText. Impact: $impact.")
                seen.edit().putBoolean(key, true).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.requestMethod = "GET"
        c.setRequestProperty("User-Agent", "JARVES-STARK/13")
        return try {
            if (c.responseCode !in 200..299) error("News feed HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
