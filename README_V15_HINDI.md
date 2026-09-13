# JARVES STARK V15 — Maximum JARVIS Polish

V15 is a polished Android source project based on V14.

## क्या नया है
- JARVES System Check / Diagnostics screen
- Startup पर immediate market/news watch check queue
- Recurring market/news watch remains battery-safe WorkManager (~15 min, inexact)
- Voice market analysis अब सीधे keyless MarketClient से 11 supported instruments पर
- Market API key field नहीं है
- Trading remains analysis/alerts only; no automatic order placement
- Persistent local memory and security remain enabled
- Hindi voice interaction remains enabled
- Safer status reporting for permissions, accessibility, AI backend and watch scheduling

## Supported markets
EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, NZD/USD,
Gold (Yahoo futures data source), Silver (Yahoo futures data source),
Bitcoin, Ethereum, Litecoin.

## Build on Android phone
1. AndroidIDE में project root import करें.
2. SDK/JDK setup होने दें; JDK 17 preferred.
3. Gradle sync करें.
4. `assembleDebug` / Build APK चलाएँ.
5. `app/build/outputs/apk/debug/app-debug.apk` install करें.

यह ZIP source project है, precompiled APK नहीं। Actual Android build device के SDK/Gradle environment में होगा.

## Important Android limitations
- Android background scheduling exact continuous execution की guarantee नहीं देता.
- Microphone, camera, notifications और accessibility permissions user को manually grant करनी होंगी.
- Screen recording के लिए Android system consent dialog जरूरी है.
- Market/news feeds external हैं; availability और timing बदल सकती है.
- JARVES किसी trade/order को अपने-आप place नहीं करता.

## ☁️ Codemagic Cloud Build

इस project की root में `codemagic.yaml` पहले से मौजूद है। Codemagic में repository connect करके `jarves-android-debug` workflow चलाएँ। इससे install करने योग्य debug APK artifact मिलेगा।

Codemagic की official native Android documentation के अनुसार `codemagic.yaml` repository root में होना चाहिए और Gradle build से APK artifacts collect किए जा सकते हैं। Release signing के लिए अलग Android keystore/credentials configure करनी होती हैं।
