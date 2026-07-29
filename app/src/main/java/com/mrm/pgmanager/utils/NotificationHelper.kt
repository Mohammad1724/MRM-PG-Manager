package com.mrm.pgmanager.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrm.pgmanager.MainActivity
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
        // بدون مجوز اعلان (ردشده در اندروید ۱۳+) هیچ اقدامی نمی‌کنیم تا برنامه کرش نکند.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannels(context)

        // ساخت intent برای باز کردن اپ با ضربه روی اعلان
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, id, launchIntent, flags)

        val priority = if (channel == CHANNEL_SYSTEM) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority).setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
