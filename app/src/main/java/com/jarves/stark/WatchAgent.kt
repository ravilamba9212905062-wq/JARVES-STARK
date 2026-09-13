package com.jarves.stark

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * JARVES background watch coordinator.
 * Android WorkManager is intentionally used for battery-safe background work.
 * The first check is queued immediately; recurring checks are best-effort/inexact.
 */
object WatchAgent {
    private const val MARKET = "jarves_market_watch"
    private const val NEWS = "jarves_news_watch"
    private const val MARKET_NOW = "jarves_market_watch_now"
    private const val NEWS_NOW = "jarves_news_watch_now"

    fun start(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences("jarves_watch", Context.MODE_PRIVATE).edit().putLong("started_at", System.currentTimeMillis()).apply()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val wm = WorkManager.getInstance(app)

        wm.enqueueUniqueWork(
            MARKET_NOW,
            androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MarketWatchWorker>()
                .setConstraints(constraints)
                .build()
        )
        wm.enqueueUniqueWork(
            NEWS_NOW,
            androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NewsWatchWorker>()
                .setConstraints(constraints)
                .build()
        )

        val market = PeriodicWorkRequestBuilder<MarketWatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        val news = PeriodicWorkRequestBuilder<NewsWatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(MARKET, ExistingPeriodicWorkPolicy.UPDATE, market)
        wm.enqueueUniquePeriodicWork(NEWS, ExistingPeriodicWorkPolicy.UPDATE, news)
    }

    fun stop(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(MARKET)
        wm.cancelUniqueWork(NEWS)
        wm.cancelUniqueWork(MARKET_NOW)
        wm.cancelUniqueWork(NEWS_NOW)
    }
}
