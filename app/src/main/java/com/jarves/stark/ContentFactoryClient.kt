package com.jarves.stark

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ContentFactoryClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarves", Context.MODE_PRIVATE)
    private fun base() = prefs.getString("content_backend_url", "") ?: ""

    suspend fun createVideo(topic: String, style: String = "shorts", language: String = "hi-IN"): String = withContext(Dispatchers.IO) {
        post(JSONObject().apply {
            put("action", "create_video"); put("topic", topic); put("style", style); put("language", language)
            put("platforms", JSONArray())
            put("request_original_script", true); put("request_captions", true); put("request_thumbnail", true)
            put("generate_tags", true); put("generate_title", true); put("generate_description", true)
        })
    }

    suspend fun editVideo(uri: Uri, instruction: String): String = withContext(Dispatchers.IO) {
        val endpoint = base(); if (endpoint.isBlank()) return@withContext "Content backend URL सेट करें।"
        val boundary = "----JARVES${System.currentTimeMillis()}"
        val c = URL(endpoint).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(c.outputStream).use { out ->
            fun field(name: String, value: String) { out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n") }
            field("action", "edit_video"); field("instruction", instruction)
            val name = "video.mp4"; out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"video\"; filename=\"$name\"\r\nContent-Type: video/mp4\r\n\r\n")
            context.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(out) } ?: return@withContext "वीडियो पढ़ नहीं पाया।"
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        stream.bufferedReader().use { it.readText() }
    }

    suspend fun schedule(assetId: String, title: String, description: String, publishAt: String, platforms: List<String>): String = withContext(Dispatchers.IO) {
        post(JSONObject().apply {
            put("action", "schedule_publish"); put("asset_id", assetId); put("title", title); put("description", description)
            put("publish_at", publishAt); put("platforms", JSONArray(platforms)); put("require_user_approval", true)
        })
    }

    suspend fun publish(assetId: String, title: String, description: String, platforms: List<String>): String = withContext(Dispatchers.IO) {
        post(JSONObject().apply {
            put("action", "publish"); put("asset_id", assetId); put("title", title); put("description", description)
            put("platforms", JSONArray(platforms)); put("require_user_approval", true)
        })
    }

    private fun post(body: JSONObject): String {
        val endpoint = base(); if (endpoint.isBlank()) return "Content/Publishing backend URL सेट करें।"
        val c = URL(endpoint).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return stream.bufferedReader().use { it.readText() }
    }
}
