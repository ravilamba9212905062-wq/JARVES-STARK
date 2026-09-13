package com.jarves.stark

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * App Factory bridge. The Android app sends a natural-language app specification
 * to YOUR backend. The backend should call the chosen builder's official API,
 * create the project, start the build, and return a build/status URL.
 *
 * Do not put builder/API secrets in the APK.
 */
object AppFactoryClient {
    private const val PREF = "jarves_factory"
    private const val KEY_URL = "factory_url"

    fun saveUrl(ctx: Context, url: String) = ctx.getSharedPreferences(PREF, 0).edit().putString(KEY_URL, url).apply()
    fun getUrl(ctx: Context) = ctx.getSharedPreferences(PREF, 0).getString(KEY_URL, "") ?: ""

    fun createAsync(ctx: Context, spec: String, callback: (String) -> Unit) {
        val endpoint = getUrl(ctx)
        if (endpoint.isBlank()) { callback("FACTORY_NOT_CONFIGURED"); return }
        Thread {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("spec", spec).put("platform", "android").put("output", "apk").toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val result = json?.optString("status_url").takeUnless { it.isNullOrBlank() }
                    ?: json?.optString("message").takeUnless { it.isNullOrBlank() }
                    ?: text.take(500)
                callback(result ?: "Factory response received")
            } catch (e: Exception) { callback("FACTORY_ERROR: ${e.message}") }
        }.start()
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
