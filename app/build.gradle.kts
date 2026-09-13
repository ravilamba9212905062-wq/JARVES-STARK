plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.jarves.stark"; compileSdk = 35
    defaultConfig { applicationId = "com.jarves.stark"; minSdk = 26; targetSdk = 35; versionCode = 15; versionName = "15.0" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}
