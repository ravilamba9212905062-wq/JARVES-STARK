# JARVES STARK V14 — Stark Agent Final Source

यह build trading-only market watch को background agent की तरह चलाता है।

### Markets
EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, NZD/USD, Gold, Silver, Bitcoin, Ethereum, Litecoin.

### Automatic alerts
- Significant market movement / bias change
- Economic-calendar High/Medium impact alerts
- 5h, 30m, 15m, 5m pre-news notifications
- Boot के बाद monitoring फिर शुरू

### API key
Market API-key UI हटाया गया है। Market data और calendar के लिए fixed public sources use किए गए हैं।

### Safety
JARVES analysis/alerts देता है; यह अपने-आप trade/order place नहीं करता।

### Reality check
यह source build-ready है, लेकिन इस environment में Android SDK/Gradle से वास्तविक device APK build/run test नहीं किया गया है। External market/news feeds उपलब्ध न होने पर alerts नहीं आएँगे।

### APK
Android Studio में project खोलें → Gradle Sync → Build APK. Release distribution के लिए अपनी signing key लगाएँ।
