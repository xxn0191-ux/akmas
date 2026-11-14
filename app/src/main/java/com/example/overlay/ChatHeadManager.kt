package com.example.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object ChatHeadManager {
    const val ACTION_START = "com.example.overlay.START"
    const val ACTION_POP_CHAT = "com.example.overlay.POP_CHAT"
    const val ACTION_STOP = "com.example.overlay.STOP"
    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
    const val EXTRA_OPEN_CALL_ID = "extra_open_call_id"
    const val EXTRA_OPEN_PROFILE_UID = "extra_open_profile_uid"

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestOverlayPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun requestBatteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun startService(context: Context, popChatId: String? = null) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, ChatHeadOverlayService::class.java).apply {
            action = if (popChatId != null) ACTION_POP_CHAT else ACTION_START
            if (popChatId != null) putExtra(EXTRA_CHAT_ID, popChatId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, ChatHeadOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        context.stopService(intent)
    }
}
