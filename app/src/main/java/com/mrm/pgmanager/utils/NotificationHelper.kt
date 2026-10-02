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
 *  - اعلان‌های متریک و سلامت نود (CPU/RAM/Disk/ظرفیت/آنلاین-آفلاین نود) → تب «آمار» باز می‌شود؛
 *    نمودارها و کارت سلامت نود همان‌جا هستند.
 *  - اعلان‌های عمومی (قطع پنل/تمدید نشست) → تب «داشبورد» باز می‌شود.
 */
object NotificationHelper {
    /** طلاییِ برند (DsAccent.Gold) — برای رنگِ اعلان‌ها. */
    private val ACCENT_COLOR = 0xFFFACC15.toInt()

    const val CHANNEL_EVENTS = "mrm_user_events"
    const val CHANNEL_SYSTEM = "mrm_system_health"

    /**
     * کانالِ سوم (فاز ۸.۳): نتیجهٔ عملیاتی که **خودِ کاربر** اجرا کرده.
     *
     * چرا جدا شد: این‌ها خبرِ فوری نیستند — کاربر همین حالا کار را انجام داده و
     * اسنک و لمسِ هپتیک هم گرفته؛ اگر روی همین کانالِ هشدارها بمانند، هر «ریستِ
     * مصرفِ گروهی» صدا و سروصدا تولید می‌کند. `IMPORTANCE_LOW` یعنی بدونِ صدا و
     * بدونِ هد-آپ، ولی همچنان در سایهٔ اعلان‌ها قابلِ دیدن — و کاربر می‌تواند در
     * تنظیماتِ اندروید این کانال را مستقل خاموش کند.
     */
    const val CHANNEL_ACTIONS = "mrm_user_actions"

    /** کلیدهای extra اکشنِ «بی‌صدا کردن» روی اعلان. */
    const val EXTRA_NOTIF_ID = "mrm_notif_id"
    const val EXTRA_MUTE_KIND = "mrm_mute_kind"
    const val ACTION_MUTE = "com.mrm.pgmanager.action.MUTE_ALERT"

    /**
     * نگاشتِ «نوعِ هشدار» → تغییری که «بی‌صدا کردن» روی تنظیمات می‌گذارد.
     *
     * هر نوتیفِ هشدار با `kind` ساخته می‌شود؛ اگر نوعش اینجا باشد، اکشنِ
     * بی‌صداکردن هم به اعلان اضافه می‌شود و همان کلیدِ تنظیماتِ درونِ اپ را
     * خاموش می‌کند (پس در «تنظیمات ← اعلان‌ها» هم دیده می‌شود و برعکس).
     * نوع‌هایی که اینجا نیستند — مثلِ «نشستِ منقضی» — اکشنِ بی‌صدا نمی‌گیرند چون
     * یک رویدادِ یک‌باره‌اند و خاموش‌کردنی نیستند.
     */
    val MUTE_TARGETS: Map<String, (com.mrm.pgmanager.data.model.MonitoringSettings) -> com.mrm.pgmanager.data.model.MonitoringSettings> = mapOf(
        "limited" to { it.copy(notifyLimited = false) },
        "expired" to { it.copy(notifyExpired = false) },
        "near_limit" to { it.copy(notifyNearLimit = false) },
        "near_expiry" to { it.copy(notifyNearExpiry = false) },
        "node_offline" to { it.copy(notifyNodeOffline = false) },
        "panel_offline" to { it.copy(notifyPanelOffline = false) },
        "system_health" to { it.copy(notifySystemHealth = false) },
        "capacity" to { it.copy(notifyCapacity = false) },
        "debtor_overdue" to { it.copy(notifyDebtorOverdue = false) }
    )

    /** آیا این نوعِ هشدار قابلِ بی‌صداکردن است؟ */
    fun canMute(kind: String?): Boolean = kind != null && MUTE_TARGETS.containsKey(kind)

    /** کلیدهای extra روی intent ضربه‌روی‌اعلان. */
    const val EXTRA_DEST = "mrm_dest"           // مقدارش: DEST_USERS، DEST_STATISTICS یا DEST_DASHBOARD
    const val EXTRA_USERNAME = "mrm_username"   // برای اعلان‌های کاربر-محور: نام کاربری مقصد
    const val DEST_USERS = "users"
    const val DEST_STATISTICS = "statistics"    // تب «آمار» (ایندکس ۲) — مقصدِ اعلان‌های متریک/نود
    const val DEST_DASHBOARD = "dashboard"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, context.getString(R.string.nc_events), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.nc_events_desc) })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SYSTEM, context.getString(R.string.nc_system), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.nc_system_desc) })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ACTIONS, context.getString(R.string.nc_actions), NotificationManager.IMPORTANCE_LOW).apply { description = context.getString(R.string.nc_actions_desc) })
    }

    /**
     * ساخت و نمایش اعلان.
     *
     * @param targetUsername برای اعلان‌های مربوط به یک کاربر خاص، نام کاربری را می‌دهیم
     *                       تا با ضربه روی اعلان مستقیم جزئیات آن باز شود.
     *                       برای اعلان‌های متریک/نود `null` بدهید و `targetTab = DEST_STATISTICS`؛
     *                       برای اعلان‌های عمومی هر دو را `null` بگذارید (→ داشبورد).
     * @param kind نوعِ هشدار (فاز ۸.۳) — اگر در [MUTE_TARGETS] باشد، اعلان دو اکشن
     *   می‌گیرد: «مشاهده» (همان دیپ‌لینکِ بدنه) و «بی‌صدا کردن» (خاموش‌کردنِ همین
     *   نوع هشدار در تنظیمات). برای اعلان‌های نتیجهٔ عملیات `null` بگذارید.
     */
    fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        message: String,
        targetUsername: String? = null,
        targetTab: String = if (targetUsername != null) DEST_USERS else DEST_DASHBOARD,
        kind: String? = null
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannels(context)

        // نام کاربری یعنی مقصد «کاربران» (جزئیات همان کاربر باز شود)؛
        // وگرنه targetTab حاکم است (آمار یا داشبورد).
        val dest = if (!targetUsername.isNullOrBlank()) DEST_USERS else targetTab
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DEST, dest)
            if (dest == DEST_USERS) putExtra(EXTRA_USERNAME, targetUsername.orEmpty())
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // requestCode منحصربه‌فرد بر مبنای id اعلان تا هر نوتیف PendingIntent جدا داشته باشد
        // و هنگام تبِ چند اعلان هم‌زمان، هر کدام داده درست خودش را برساند.
        val pendingIntent = PendingIntent.getActivity(context, 10000 + id, launchIntent, flags)

        val priority = when (channel) {
            CHANNEL_SYSTEM -> NotificationCompat.PRIORITY_HIGH
            CHANNEL_ACTIONS -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            // رنگِ برندی که سیستم پشتِ آیکونِ کوچک در کشوی اعلان‌ها می‌گذارد؛
            // کمک می‌کند کاربر در یک نگاه بفهمد اعلان از کدام اپ است.
            .setColor(ACCENT_COLOR)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority).setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // فاز ۸.۳ — «هر اعلانِ هشدار، اکشن دارد»:
        //  • مشاهده: همان مقصدِ بدنهٔ اعلان (برای هشدارهای متنی که جزئیات دارند).
        //  • بی‌صدا کردن: بدونِ باز کردنِ اپ، همین نوع هشدار خاموش می‌شود.
        // اگر کاربر بی‌صدا کند، حالتِ درونِ اپ هم عوض شده است؛ پس دو منبعِ حقیقت نداریم.
        if (canMute(kind)) {
            val muteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_MUTE
                putExtra(EXTRA_NOTIF_ID, id)
                putExtra(EXTRA_MUTE_KIND, kind)
            }
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notif_action_open),
                pendingIntent
            )
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notif_action_mute),
                PendingIntent.getBroadcast(context, 20000 + id, muteIntent, flags)
            )
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** پاک کردن کلیدهای دیپ‌لینک از intent (پردازش یک‌بار پس از باز کردن). */
    fun consumeDeepLink(intent: Intent) {
        intent.removeExtra(EXTRA_DEST)
        intent.removeExtra(EXTRA_USERNAME)
    }
}
