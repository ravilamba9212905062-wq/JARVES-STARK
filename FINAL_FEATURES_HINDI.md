# JARVES STARK FINAL – Feature Plan

## स्थायी रूप से हटाया गया
- मोबाइल ↔ लैपटॉप/PC remote-control feature नहीं है।

## Voice Assistant
- Wake phrase: Hey JARVES / हे जार्वेस / हे जार्विस
- Hindi voice interaction
- Long-term memory + searchable memory
- Secret passphrase fallback when voice recognition is difficult

## Phone controls
- Open installed apps by voice
- Open Google/YouTube/WhatsApp
- Open Settings, Wi-Fi settings and Bluetooth settings
- Calculator-style spoken arithmetic
- Home/leave current app where Android permits

## Camera / media
- Open camera by voice
- Take photo by voice
- Start/stop video recording by voice
- Start/stop voice recording by voice
- Screen recording through Android's official MediaProjection permission flow

## Media cleanup
- “पिछले दो दिन की फोटो डिलीट कर दो”
- “आज की voice recording डिलीट कर दो”
- Date-based media selection
- Bulk delete should ask for confirmation before destructive action

## AI / Web
- AI conversation backend
- Current-information web fallback through backend
- Market analysis backend
- News/calendar alerts backend

## Content creation
- AI script/video/caption/thumbnail workflow
- YouTube/Facebook/Instagram publishing through official OAuth/API backend
- Scheduled publishing
- User-provided video editing workflow

## App installation
- Open official Google Play listing by voice
- Installation remains subject to Android/Play Store user/system confirmation; no permission/security bypass.

## Security
- Camera/microphone permissions follow Android rules.
- Screen recording uses official Android consent.
- Bluetooth pairing may require system confirmation.
- Wi-Fi/Bluetooth direct toggling may be restricted on modern Android; JARVES opens the appropriate system panel when needed.
- No hidden recording and no Android security bypass.

## Build note
This folder is an Android Studio/Gradle source project. A final APK must be compiled with Android SDK + Gradle/Android Gradle Plugin in a build environment. The current chat runtime does not have the required Gradle/Android build toolchain available, so no APK is being falsely claimed as compiled here.


## Trading instruments (fixed)
JARVES में केवल ये instruments दिखेंगे: EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, NZD/USD, Gold (XAU/USD), Silver (XAG/USD), Bitcoin (BTC/USD), Ethereum (ETH/USD), Litecoin (LTC/USD). Indian stocks/NSE/BSE और arbitrary symbols UI से हटाए गए हैं। Market API-key field भी हटाया गया है।
