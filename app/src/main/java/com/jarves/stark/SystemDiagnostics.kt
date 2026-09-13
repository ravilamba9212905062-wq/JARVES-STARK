package com.jarves.stark

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object SystemDiagnostics {
    fun run(context: Context): String {
        val lines = mutableListOf<String>()
        val pm = context.packageManager
        val mic = pm.checkPermission(android.Manifest.permission.RECORD_AUDIO, context.packageName) == PackageManager.PERMISSION_GRANTED
        val cam = pm.checkPermission(android.Manifest.permission.CAMERA, context.packageName) == PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < 33 ||
            pm.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS, context.packageName) == PackageManager.PERMISSION_GRANTED
        val accessibility = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains(context.packageName, ignoreCase = true) == true
        }.getOrDefault(false)
        val power = context.getSystemService(PowerManager::class.java)
        val batteryOptimized = power?.isIgnoringBatteryOptimizations(context.packageName) != true

        lines += "JARVES V15 System Check"
        lines += "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        lines += "Microphone: ${if (mic) "OK" else "NEEDS PERMISSION"}"
        lines += "Camera: ${if (cam) "OK" else "NEEDS PERMISSION"}"
        lines += "Notifications: ${if (notif) "OK" else "NEEDS PERMISSION"}"
        lines += "Accessibility: ${if (accessibility) "ON" else "OFF"}"
        lines += "Battery optimization: ${if (batteryOptimized) "ON — background watch may be delayed" else "IGNORED"}"
        lines += "AI backend: ${AiClient.backendStatus(context)}"
        lines += "Market data: keyless Yahoo chart source"
        lines += "Markets: 11 fixed instruments only"
        val watchStarted = context.getSharedPreferences("jarves_watch", Context.MODE_PRIVATE).getLong("started_at", 0L) > 0L
        val memories = runCatching { MemoryStore(context).count() }.getOrDefault(0)
        lines += "Watch Agent: ${if (watchStarted) "SCHEDULED" else "NOT STARTED"}"
        lines += "Local memory: $memories items"
        lines += "Orders: DISABLED (analysis/alerts only)"
        return lines.joinToString("\n")
    }
}
