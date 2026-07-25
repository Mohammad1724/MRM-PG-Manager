package com.mrm.pgmanager.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrm.pgmanager.R

object NotificationHelper {
    const val CHANNEL_EVENTS = "mrm_user_events"
    const val CHANNEL_SYSTEM = "mrm_system_health"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, "رویدادهای کاربران", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "ساخت، حذف، محدودشدن و انقضای کاربران" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SYSTEM, "سلامت سیستم", NotificationManager.IMPORTANCE_HIGH).apply { description = "هشدار CPU، RAM، Disk و اتصال پنل" })
    }

    fun post(context: Context, id: Int, channel: String, title: String, message: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
