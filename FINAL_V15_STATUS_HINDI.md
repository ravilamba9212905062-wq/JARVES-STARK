# JARVES STARK V15 — Final Status

स्थिति: source-polished / build-ready project

V15 का लक्ष्य “maximum JARVIS feel” है, न कि Android security restrictions को bypass करना.

### Core
- Voice foreground service
- Hindi speech recognition
- Wake phrase: JARVES / जार्वेस / जार्विस
- Local persistent memory
- Voice security
- Accessibility text entry
- Camera/photo/video
- Screen recording
- Voice recording
- Calculator
- App/Play Store/settings launching

### Watch Agent
- Immediate one-time market check on startup
- Immediate one-time news-calendar check on startup
- Periodic market check every ~15 min (WorkManager, inexact)
- Periodic news check every ~15 min (WorkManager, inexact)
- Market movement/bias notifications
- Economic news alerts at 5h/30m/15m/5m where schedule data permits

### Trading
Only these 11 instruments:
EUR/USD
GBP/USD
USD/JPY
USD/CHF
AUD/USD
NZD/USD
Gold
Silver
Bitcoin
Ethereum
Litecoin

No Indian stocks/NSE/BSE and no market API key UI.

### Safety
- No automatic order placement
- Delete actions require confirmation
- Secret/password is not saved in plain text
- External backend secrets are not embedded in the APK

### Build note
This package has not been compiled inside this environment because an Android SDK/Gradle build environment is not available here. Use AndroidIDE or another Android Gradle environment for the actual APK build.
