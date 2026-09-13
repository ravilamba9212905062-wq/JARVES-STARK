package com.jarves.stark

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MediaManager {
    fun isVoiceRecording(): Boolean = recorder != null
    private var recorder: MediaRecorder? = null
    private var recordingPath: String? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null

    fun startVoiceRecording(context: Context): String {
        if (recorder != null) return recordingPath ?: "Recording already running"
        val resolver = context.contentResolver
        val name = "JARVES_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.m4a"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/JARVES")
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create audio file")
        val r = MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        outputPfd = resolver.openFileDescriptor(uri, "w")
        r.setOutputFile(outputPfd!!.fileDescriptor)
        try {
            r.prepare(); r.start()
            recorder = r; recordingPath = uri.toString()
            return "Voice recording शुरू: $name"
        } catch (e: Exception) {
            r.release(); resolver.delete(uri, null, null); throw e
        }
    }

    fun stopVoiceRecording(): String {
        val r = recorder ?: return "कोई voice recording चालू नहीं है।"
        return try {
            r.stop(); r.release(); recorder = null; outputPfd?.close(); outputPfd = null
            "Voice recording save हो गई।"
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) {}
            recorder = null; outputPfd?.close(); outputPfd = null
            "Recording बहुत छोटी थी या save नहीं हो पाई।"
        } finally { recordingPath = null }
    }

    fun deletePhotosForDay(context: Context, year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis / 1000
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis / 1000
        val resolver = context.contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf(start.toString(), end.toString(), "Pictures/JARVES/%")
        } else {
            selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf(start.toString(), end.toString(), "JARVES_%")
        }
        var count = 0
        resolver.query(uri, projection, selection, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) {
                val item = ContentUris.withAppendedId(uri, c.getLong(id))
                if (resolver.delete(item, null, null) > 0) count++
            }
        }
        return count
    }

    fun deleteAudioForDay(context: Context, year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis / 1000
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis / 1000
        val resolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "${MediaStore.Audio.Media.DATE_ADDED} >= ? AND ${MediaStore.Audio.Media.DATE_ADDED} < ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf(start.toString(), end.toString(), "Music/JARVES/%")
        } else {
            selection = "${MediaStore.Audio.Media.DATE_ADDED} >= ? AND ${MediaStore.Audio.Media.DATE_ADDED} < ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf(start.toString(), end.toString(), "JARVES_%")
        }
        return resolver.delete(uri, selection, args)
    }
}
