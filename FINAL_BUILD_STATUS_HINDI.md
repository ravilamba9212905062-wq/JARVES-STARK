# JARVES STARK — Final Build Status (V13 Trading Watch Agent)

## Included
- Fixed trading universe only: EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, NZD/USD, Gold, Silver, Bitcoin, Ethereum, Litecoin.
- No Market API-key field.
- Keyless public market-data client for chart/technical analysis.
- RSI, EMA20/50, ATR, support/resistance and proper MACD signal series.
- Automatic background Market Watch using Android WorkManager every 15 minutes.
- Movement alerts with per-instrument thresholds and cooldown to avoid notification spam.
- Automatic economic-calendar watcher using the public Fair Economy / Forex Factory weekly JSON feed.
- Relevant USD/EUR/GBP/JPY/CHF/AUD/NZD High/Medium-impact events are scheduled automatically.
- News alerts: 5 hours, 30 minutes, 15 minutes and 5 minutes before event time.
- Boot/package-replacement receiver restarts background watch scheduling.
- Notification permission request for Android 13+.
- Exact-alarm permission with graceful inexact fallback when exact alarms are unavailable.
- No automatic trade/order execution.

## Important limitation
The public market feed and public calendar are external services. Their availability, rate limits, symbols and timestamps can change. Forex Factory explicitly notes that calendar times are approximate and can change. The app therefore treats the information as alerts/analysis, not guaranteed execution-grade quotes or guaranteed news timing.

## Build requirement
This archive is Android Studio/Gradle source. A signed release APK still needs an Android SDK/Gradle build environment and a signing key. This environment does not contain the Android SDK, so an APK cannot honestly be claimed as compiled/tested here.
