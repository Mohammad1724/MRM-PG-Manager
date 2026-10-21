package com.mrm.pgmanager.utils

/**
 * تنها مرجعِ ID اعلان‌ها.
 *
 * چرا این شیء لازم شد: موتورِ پیش‌زمینه (داشبورد/صفحه‌ها) و موتورِ پس‌زمینه
 * ([com.mrm.pgmanager.work.MonitoringWorker]) هر دو برای یک رویداد اعلان می‌سازند و
 * قبلاً طرحِ ID جداگانه داشتند (مثلاً 3104 در برابر 5104، یا «near_expire» در برابر
 * «near_expiry»)؛ نتیجه‌اش دو اعلان برای یک رویداد در سایه بود. همه‌ی مسیرها حالا
 * از همین توابع استفاده می‌کنند تا ID یک رویداد، مستقل از موتور صادرکننده، یکی باشد
 * و NotificationManager خودش انتشار مجدد را جایگزین (نه انباشته) کند.
 *
 * عمداً بدون هیچ وابستگیِ اندرویدی است تا با تستِ JVMِ خالص سنجیده شود.
 */
object NotificationIds {

    // ── IDهای ثابتِ سلامت/چرخه‌حیات (هر کلید فقط یک اعلان هم‌زمان در سایه) ──
    const val CPU = 5101
    const val RAM = 5102
    const val DISK = 5103
    const val PANEL_OFFLINE = 5104
    const val SESSION_EXPIRED = 5105
    const val CAPACITY = 5106

    /**
     * رویدادِ یک نود (آنلاین/آفلاین).
     *
     * به‌جای باندِ عددی (مثلاً 6100+id) که برای idهای با فاصله‌ی ثابت می‌توانست
     * با باندِ دیگر برخورد کند (رفعِ D9 فقط فاصله را زیاد کرده بود)، هشِ رشته‌ی
     * نوع‌دار به کار می‌رود: عملاً بدون برخورد و مستقل از بزرگیِ id نود.
     */
    fun nodeOffline(nodeId: Int): Int = "node_offline_$nodeId".hashCode()
    fun nodeOnline(nodeId: Int): Int = "node_online_$nodeId".hashCode()

    /**
     * رویدادِ وضعیتِ یک کاربر (محدود/منقضی/نزدیک‌سقف/نزدیک‌انقضا).
     *
     * `kind` باید همان کلیدِ [NotificationHelper.MUTE_TARGETS] باشد تا اعلانِ
     * پیش‌زمینه و پس‌زمینه هم ID و هم دکمه‌ی بی‌صداکردنِ یکسان داشته باشند.
     * پایدار در (kind, user) تا انتشار مجدد، اعلان قبلی را به‌روزرسانی کند.
     */
    fun userEvent(kind: String, userId: Long): Int = (kind + userId.toString()).hashCode()

    /** نتیجه‌ی عملیاتِ کاربر روی کانالِ کم‌صدای actions — پایدار در متن اعلان. */
    fun userAction(title: String, message: String): Int = (title + message).hashCode()

    /** ثبتِ بدهکار جدید (رویداد یک‌باره، بدون اکشن بی‌صدا). */
    fun debtorRegistered(username: String): Int = "debtor_added_$username".hashCode()

    /** قطعِ خودکارِ بدهکارِ معوق — طرحِ یکسان برای اسکنِ پیش‌زمینه و ورکر. */
    fun debtorOverdue(username: String): Int = "debtor_overdue_$username".hashCode()
}
