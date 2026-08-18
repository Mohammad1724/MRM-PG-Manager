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

/**
 * مدیریت ساخت/نمایش اعلان‌ها و دیپ‌لینک‌شان (کدام صفحه/کاربر با ضربه روی اعلان باز شود).
 *
 * با ضربه روی هر اعلان:
 *  - اعلان‌های کاربر-محور (محدود/منقضی/نزدیک‌به‌سقف/بدهکار/...) → اپ باز می‌شود، تب «کاربران» انتخاب
 *    می‌گردد و مستقیم جزئیات همان کاربر باز می‌شود.
 *  - اعلان‌های سیستمی (CPU/RAM/Disk/قطع پنل/نود/تمدید نشست) → تب «داشبورد» باز می‌شود.
 */
object NotificationHelper {
    const val CHANNEL_EVENTS = "mrm_user_events"
    const val CHANNEL_SYSTEM = "mrm_system_health"

    /** کلیدهای extra روی intent ضربه‌روی‌اعلان. */
    const val EXTRA_DEST = "mrm_dest"           // مقدارش: DEST_USERS یا DEST_DASHBOARD
    const val EXTRA_USERNAME = "mrm_username"   // برای اعلان‌های کاربر-محور: نام کاربری مقصد
    const val DEST_USERS = "users"
    const val DEST_DASHBOARD = "dashboard"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, context.getString(R.string.nc_events), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.nc_events_desc) })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SYSTEM, context.getString(R.string.nc_system), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.nc_system_desc) })
    }

    /**
     * ساخت و نمایش اعلان.
     *
     * @param targetUsername برای اعلان‌های مربوط به یک کاربر خاص، نام کاربری را می‌دهیم
     *                       تا با ضربه روی اعلان مستقیم جزئیات آن باز شود.
     *                       برای اعلان‌های سیستمی/عمومی `null` بدهید (→ داشبورد).
     */
    fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        message: String,
        targetUsername: String? = null,
        targetTab: String = if (targetUsername != null) DEST_USERS else DEST_DASHBOARD
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannels(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DEST, targetTab)
            if (!targetUsername.isNullOrBlank()) putExtra(EXTRA_USERNAME, targetUsername)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // requestCode منحصربه‌فرد بر مبنای id اعلان تا هر نوتیف PendingIntent جدا داشته باشد
        // و هنگام تبِ چند اعلان هم‌زمان، هر کدام داده درست خودش را برساند.
        val pendingIntent = PendingIntent.getActivity(context, 10000 + id, launchIntent, flags)

        val priority = if (channel == CHANNEL_SYSTEM) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority).setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** پاک کردن کلیدهای دیپ‌لینک از intent (پردازش یک‌بار پس از باز کردن). */
    fun consumeDeepLink(intent: Intent) {
        intent.removeExtra(EXTRA_DEST)
        intent.removeExtra(EXTRA_USERNAME)
    }
}
