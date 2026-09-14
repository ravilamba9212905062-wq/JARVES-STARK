package com.jarves.stark

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

object AiClient {
    private const val PREF = "jarves_ai"
    private const val KEY_URL = "backend_url"

    fun saveBackendUrl(context: Context, url: String) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, url).apply()

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_URL, "") ?: ""

    fun backendStatus(context: Context): String =
        if (getBackendUrl(context).isBlank()) "keyless web mode" else "connected URL set"

    fun ask(context: Context, message: String, callback: (String) -> Unit) {
        askWithWebFallback(context, message, callback)
    }

    fun askWithWebFallback(context: Context, message: String, callback: (String) -> Unit) {
        val clean = message.trim()

        if (clean.isBlank()) {
            callback("हाँ भाई, बोलो।")
            return
        }

        Thread {
            try {
                val answer = webAnswer(clean)

                if (answer.isNotBlank()) {
                    callback(answer.take(1800))
                } else {
                    callback("मुझे अभी इसका भरोसेमंद जवाब नहीं मिला।")
                }
            } catch (_: Exception) {
                callback("अभी इंटरनेट से जानकारी नहीं मिल पाई।")
            }
        }.start()
    }

    private fun webAnswer(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=0")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 12000
            setRequestProperty("User-Agent", "JARVES/15 Android")
        }

        return try {
            val stream = if (conn.responseCode in 200..299)
                conn.inputStream
            else
                conn.errorStream

            val text = BufferedReader(
                InputStreamReader(stream, StandardCharsets.UTF_8)
            ).use { it.readText() }

            val json = JSONObject(text)

            val abstractText = json.optString("AbstractText", "").trim()
            val heading = json.optString("Heading", "").trim()

            if (abstractText.isNotBlank()) {
                if (heading.isNotBlank()) {
                    "$heading। $abstractText"
                } else {
                    abstractText
                }
            } else {
                val topics = json.optJSONArray("RelatedTopics")
                var result = ""

                if (topics != null) {
                    for (i in 0 until topics.length()) {
                        val item = topics.optJSONObject(i) ?: continue
                        val textValue = item.optString("Text", "").trim()
                        if (textValue.isNotBlank()) {
                            result = textValue
                            break
                        }
                    }
                }

                result
            }
        } finally {
            conn.disconnect()
        }
    }

    fun analyzeMarket(
        context: Context,
        symbol: String,
        timeframe: String,
        callback: (String) -> Unit
    ) {
        callback("Market analysis JARVES के live market engine से की जाती है।")
    }
}
