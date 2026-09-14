package com.jarves.stark

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import java.util.regex.Pattern

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
        if (getBackendUrl(context).isBlank()) "free web research mode" else "connected URL set"

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
                val answer = intelligentAnswer(clean)
                callback(
                    if (answer.isNotBlank())
                        answer.take(1800)
                    else
                        "भाई, मुझे अभी इसका भरोसेमंद जवाब नहीं मिला।"
                )
            } catch (_: Exception) {
                callback("भाई, अभी इंटरनेट से जानकारी नहीं मिल पाई।")
            }
        }.start()
    }

    private fun intelligentAnswer(query: String): String {
        val ddg = duckAnswer(query)
        if (ddg.isNotBlank()) return ddg

        val wiki = wikipediaAnswer(query)
        if (wiki.isNotBlank()) return wiki

        val search = webSearch(query)
        if (search.isNotBlank()) return search

        return ""
    }

    private fun duckAnswer(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = URL(
                "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=0"
            )

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("User-Agent", "JARVES/15 Android")
            }

            try {
                if (conn.responseCode !in 200..299) return ""

                val text = BufferedReader(
                    InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
                ).use { it.readText() }

                val json = JSONObject(text)
                val abstractText = json.optString("AbstractText", "").trim()
                val heading = json.optString("Heading", "").trim()

                if (abstractText.isNotBlank()) {
                    if (heading.isNotBlank()) "$heading। $abstractText"
                    else abstractText
                } else {
                    ""
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun wikipediaAnswer(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = URL(
                "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            )

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("User-Agent", "JARVES/15 Android")
            }

            try {
                if (conn.responseCode !in 200..299) return ""

                val text = BufferedReader(
                    InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
                ).use { it.readText() }

                val json = JSONObject(text)
                val extract = json.optString("extract", "").trim()
                val title = json.optString("title", "").trim()

                if (extract.isNotBlank()) {
                    if (title.isNotBlank()) "$title। $extract"
                    else extract
                } else {
                    ""
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun webSearch(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = URL(
                "https://html.duckduckgo.com/html/?q=$encoded"
            )

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android) JARVES/15"
                )
            }

            try {
                if (conn.responseCode !in 200..299) return ""

                val html = BufferedReader(
                    InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
                ).use { it.readText() }

                val pattern = Pattern.compile(
                    "<a[^>]*class=\"result__a\"[^>]*>(.*?)</a>|" +
                    "<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )

                val matcher = pattern.matcher(html)
                val results = StringBuilder()

                var count = 0
                while (matcher.find() && count < 3) {
                    val raw = matcher.group(1) ?: matcher.group(2) ?: ""
                    val clean = stripHtml(raw)

                    if (clean.isNotBlank()) {
                        if (results.isNotEmpty()) results.append(" ")
                        results.append(clean)
                        results.append("।")
                        count++
                    }
                }

                results.toString().trim()
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun stripHtml(value: String): String {
        return value
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun analyzeMarket(
        context: Context,
        symbol: String,
        timeframe: String,
        callback: (String) -> Unit
    ) {
        callback("मार्केट विश्लेषण JARVES के लाइव मार्केट इंजन से की जाती है।")
    }
}
