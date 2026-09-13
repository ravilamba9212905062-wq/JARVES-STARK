# JARVES V15 — Codemagic Setup (Hindi)

1. इस project को GitHub/GitLab/Bitbucket repository में push करें।
2. Codemagic में repository connect करें।
3. Root में मौजूद `codemagic.yaml` को detect होने दें।
4. Workflow `jarves-android-debug` चुनें।
5. Build शुरू करें।
6. Build सफल होने पर Artifacts में `app-debug.apk` डाउनलोड करें।

## Signed release
`jarves-android-release-unsigned` workflow release APK बनाता है लेकिन वह signed release नहीं है। Google Play या production distribution के लिए Codemagic में Android keystore upload करके Gradle release signing configure करना आवश्यक है।

## Important
- Market API key की जरूरत नहीं है।
- Debug workflow में कोई signing secret नहीं चाहिए।
- APK build होने की गारंटी केवल तब होगी जब Codemagic build logs में Gradle/Android SDK compatibility सफल हो।
