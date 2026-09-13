package com.jarves.stark

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

object AiClient {
    private const val PREF = "jarves_ai"
    private const val KEY_URL = "backend_url"

    fun saveBackendUrl(context: Context, url: String) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply()

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_URL, "") ?: ""

    fun backendStatus(context: Context): String =
        if (getBackendUrl(context).isBlank()) "not configured" else "connected URL set"

    fun ask(context: Context, message: String, callback: (String) -> Unit) {
        askWithWebFallback(context, message, callback)
    }

    fun askWithWebFallback(context: Context, message: String, callback: (String) -> Unit) {
        val memory = MemoryStore(context).contextFor(message)
        val endpoint = getBackendUrl(context)
        if (endpoint.isBlank()) {
            callback("AI backend अभी सेट नहीं है। JARVES में AI backend URL सेट करें।")
            return
        }
        Thread {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject()
                    .put("task", "answer_with_web_fallback")
                    .put("message", message)
                    .put("memory_context", memory)
                    .put("memory_mode", "permanent_local_memory")
                    .put("instructions", "JARVES has permanent local memory. Treat memory_context as prior conversation and user facts. Use it to continue naturally; do not ask the user to repeat information already present. If memories conflict, prefer the newest dated item and explicitly mention uncertainty. Answer from knowledge first. If uncertain, missing, current, price, school syllabus, news or time-sensitive information, automatically use live web search and return a concise Hindi answer with source names and current date/time. Never invent current facts.")
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                val reply = try {
                    val j = JSONObject(text)
                    j.optString("reply", j.optString("message", text))
                } catch (_: Exception) { text }
                callback(if (reply.isBlank()) "AI ने खाली जवाब दिया।" else reply.take(1800))
                conn.disconnect()
            } catch (e: Exception) {
                callback("AI connection नहीं हो पाई: ${e.message ?: "network error"}")
            }
        }.start()
    }

    fun analyzeMarket(context: Context, symbol: String, timeframe: String, callback: (String) -> Unit) {
        val endpoint = getBackendUrl(context)
        if (endpoint.isBlank()) {
            callback("Market analysis के लिए सुरक्षित AI/market-data backend जोड़ना बाकी है।")
            return
        }
        Thread {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject()
                    .put("task", "market_analysis")
                    .put("symbol", symbol)
                    .put("timeframe", timeframe)
                    .put("instructions", "Use current market data supplied by the backend. Give scenarios, trend evidence, confidence and risks. Never claim certainty or guarantee up/down.")
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                val reply = try {
                    val j = JSONObject(text)
                    j.optString("reply", j.optString("analysis", text))
                } catch (_: Exception) { text }
                callback(if (reply.isBlank()) "Analysis नहीं मिली।" else reply.take(2000))
                conn.disconnect()
            } catch (e: Exception) {
                callback("Market analysis connection नहीं हो पाई: ${e.message ?: "network error"}")
            }
        }.start()
    }
}
