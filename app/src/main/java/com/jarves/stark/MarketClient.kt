package com.jarves.stark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Keyless market-data client for the instruments supported by JARVES.
 * It uses Yahoo Finance's public chart endpoint as a no-key data source.
 * This is intentionally limited to the user's selected FX, metals and crypto instruments.
 */
class MarketClient {
    companion object {
        data class Instrument(val label: String, val yahooSymbol: String)

        val SUPPORTED_INSTRUMENTS = listOf(
            Instrument("EUR/USD", "EURUSD=X"),
            Instrument("GBP/USD", "GBPUSD=X"),
            Instrument("USD/JPY", "JPY=X"),
            Instrument("USD/CHF", "CHF=X"),
            Instrument("AUD/USD", "AUDUSD=X"),
            Instrument("NZD/USD", "NZDUSD=X"),
            Instrument("Gold (XAU/USD)", "GC=F"),
            Instrument("Silver (XAG/USD)", "SI=F"),
            Instrument("Bitcoin (BTC/USD)", "BTC-USD"),
            Instrument("Ethereum (ETH/USD)", "ETH-USD"),
            Instrument("Litecoin (LTC/USD)", "LTC-USD")
        )

        fun labelFromCommand(command: String): String? {
            val s = command.lowercase(java.util.Locale.getDefault())
            val aliases = linkedMapOf(
                "EUR/USD" to listOf("eur/usd", "eur usd", "euro dollar", "यूरो डॉलर", "यूरो"),
                "GBP/USD" to listOf("gbp/usd", "gbp usd", "pound dollar", "पाउंड डॉलर", "पाउंड"),
                "USD/JPY" to listOf("usd/jpy", "usd jpy", "dollar yen", "डॉलर येन", "येन"),
                "USD/CHF" to listOf("usd/chf", "usd chf", "dollar franc", "डॉलर फ्रैंक", "फ्रैंक"),
                "AUD/USD" to listOf("aud/usd", "aud usd", "aussie dollar", "ऑस्ट्रेलियन डॉलर"),
                "NZD/USD" to listOf("nzd/usd", "nzd usd", "kiwi dollar", "न्यूजीलैंड डॉलर"),
                "Gold (XAU/USD)" to listOf("gold", "सोना", "गोल्ड", "xauusd", "xau/usd"),
                "Silver (XAG/USD)" to listOf("silver", "चांदी", "चाँदी", "सिल्वर", "xagusd", "xag/usd"),
                "Bitcoin (BTC/USD)" to listOf("bitcoin", "btc", "बिटकॉइन"),
                "Ethereum (ETH/USD)" to listOf("ethereum", "eth", "एथेरियम"),
                "Litecoin (LTC/USD)" to listOf("litecoin", "ltc", "लाइटकॉइन")
            )
            return aliases.entries.firstOrNull { (_, names) -> names.any { s.contains(it) } }?.key
        }

        fun yahooSymbol(label: String): String =
            SUPPORTED_INSTRUMENTS.firstOrNull { it.label == label }?.yahooSymbol
                ?: error("यह instrument JARVES में supported नहीं है")

        fun intervalFor(interval: String): String = when (interval) {
            "1min" -> "1m"
            "5min" -> "5m"
            "15min" -> "15m"
            "30min" -> "30m"
            "1h" -> "60m"
            else -> "5m"
        }
    }

    private fun get(url: String): String = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 12000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "JARVES-STARK/11")
        try {
            if (responseCode !in 200..299) error("Market data HTTP $responseCode")
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }

    suspend fun candles(label: String, interval: String, outputsize: Int = 240): List<Candle> = withContext(Dispatchers.IO) {
        val ticker = yahooSymbol(label)
        val yahooInterval = intervalFor(interval)
        val range = when (yahooInterval) {
            "1m" -> "1d"
            "5m", "15m", "30m" -> "5d"
            else -> "30d"
        }
        val encoded = URLEncoder.encode(ticker, "UTF-8")
        val u = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=$yahooInterval&range=$range&includePrePost=true&events=div%2Csplits"
        val root = JSONObject(get(u)).getJSONObject("chart")
        val result = root.optJSONArray("result") ?: error("Market data नहीं मिला")
        if (result.length() == 0) error("Market data नहीं मिला: $label")
        val r = result.getJSONObject(0)
        val timestamps = r.optJSONArray("timestamp") ?: error("Candle timestamps नहीं मिले")
        val quote = r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val opens = quote.optJSONArray("open")
        val highs = quote.optJSONArray("high")
        val lows = quote.optJSONArray("low")
        val closes = quote.optJSONArray("close")
        if (opens == null || highs == null || lows == null || closes == null) error("Candle data अधूरा है")

        val out = ArrayList<Candle>(minOf(outputsize, timestamps.length()))
        for (i in 0 until timestamps.length()) {
            val o = opens.optDouble(i, Double.NaN)
            val h = highs.optDouble(i, Double.NaN)
            val l = lows.optDouble(i, Double.NaN)
            val c = closes.optDouble(i, Double.NaN)
            if (o.isFinite() && h.isFinite() && l.isFinite() && c.isFinite()) {
                out.add(Candle(o, h, l, c, timestamps.optLong(i).toString()))
            }
        }
        if (out.size < 60) error("$label के लिए पर्याप्त live candles नहीं मिले")
        out.takeLast(outputsize)
    }
}

data class Candle(val open: Double, val high: Double, val low: Double, val close: Double, val time: String)

data class IndicatorPack(val rsi: Double, val macd: Double, val signal: Double, val ema20: Double, val ema50: Double, val atr: Double, val support: Double, val resistance: Double)

data class Analysis(val symbol: String, val timeframe: String, val bias: String, val confidence: Int, val indicators: IndicatorPack, val note: String)

object TechnicalAnalyzer {
    private fun emaSeries(x: List<Double>, n: Int): List<Double> {
        if (x.isEmpty()) return emptyList()
        val k = 2.0 / (n + 1)
        var e = x.first()
        val out = ArrayList<Double>(x.size)
        for (v in x) {
            e = v * k + e * (1 - k)
            out.add(e)
        }
        return out
    }

    private fun ema(x: List<Double>, n: Int): Double = emaSeries(x, n).lastOrNull() ?: 0.0

    private fun rsi(x: List<Double>, n: Int = 14): Double {
        if (x.size < n + 1) return 50.0
        var gain = 0.0
        var loss = 0.0
        for (i in x.size - n until x.size) {
            val d = x[i] - x[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        if (loss == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + gain / loss)
    }

    private fun atr(c: List<Candle>, n: Int = 14): Double {
        if (c.size < n + 1) return 0.0
        val trs = (1 until c.size).map { i ->
            maxOf(c[i].high - c[i].low, abs(c[i].high - c[i - 1].close), abs(c[i].low - c[i - 1].close))
        }
        return trs.takeLast(n).average()
    }

    fun analyze(symbol: String, timeframe: String, c: List<Candle>): Analysis {
        val x = c.map { it.close }
        if (x.size < 60) error("कम-से-कम 60 candles चाहिए")

        val e20 = ema(x, 20)
        val e50 = ema(x, 50)
        val ema12 = emaSeries(x, 12)
        val ema26 = emaSeries(x, 26)
        val macdSeries = ema12.indices.map { ema12[it] - ema26[it] }
        val macd = macdSeries.last()
        val signal = ema(macdSeries, 9)
        val rr = rsi(x)
        val aa = atr(c)
        val recent = c.takeLast(40)
        val sup = recent.minOf { it.low }
        val res = recent.maxOf { it.high }

        var score = 0
        if (e20 > e50) score += 2 else score -= 2
        if (macd > signal) score += 2 else score -= 2
        if (rr > 50) score++ else score--
        if (x.last() > e20) score++ else score--
        if (x.last() > sup + (res - sup) * .55) score++ else score--

        val bias = when {
            score >= 4 -> "UP bias"
            score <= -4 -> "DOWN bias"
            else -> "RANGE / mixed"
        }
        val conf = (50 + abs(score) * 10).coerceAtMost(90)
        val note = "यह probabilistic analysis है, prediction/guarantee नहीं। News और volatility के समय risk बढ़ सकता है।"
        return Analysis(symbol, timeframe, bias, conf, IndicatorPack(rr, macd, signal, e20, e50, aa, sup, res), note)
    }
}

object NewsCalendarClient {
    suspend fun upcoming(backendUrl: String): List<NewsEvent> = withContext(Dispatchers.IO) {
        if (backendUrl.isBlank()) return@withContext emptyList()
        val conn = URL(backendUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val text = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
        val arr = org.json.JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(NewsEvent(o.optString("time"), o.optString("currency"), o.optString("title"), o.optString("impact")))
            }
        }
    }
}

data class NewsEvent(val time: String, val currency: String, val title: String, val impact: String)
