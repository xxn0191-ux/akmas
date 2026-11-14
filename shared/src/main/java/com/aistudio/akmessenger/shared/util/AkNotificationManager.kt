package com.aistudio.akmessenger.shared.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AkNotificationManager {
    const val CHANNEL_MESSAGES = "messages_channel"
    const val CHANNEL_CALLS = "calls_channel"
    const val CHANNEL_ADMIN_NOTICES = "admin_notices_channel"
    const val CHANNEL_STORIES = "stories_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Real-time chat and group message notifications" },
                NotificationChannel(CHANNEL_CALLS, "Audio & Video Calls", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Incoming AK Messenger audio and video call alerts" },
                NotificationChannel(CHANNEL_ADMIN_NOTICES, "Admin Official Notices", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Platform announcements from AK Messenger Admin" },
                NotificationChannel(CHANNEL_STORIES, "Story Replies & Reactions", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Status and story updates" }
            )
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    fun showNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        smallIconRes: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannels(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
