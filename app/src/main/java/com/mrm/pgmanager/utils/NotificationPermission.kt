package com.mrm.pgmanager.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * وضعیتِ مجوزِ اعلان — فاز ۸.۳.
 *
 * چرا این فایل هست: قبلاً `MainActivity.onCreate` **پیش از هر کاری** مجوز را
 * می‌خواست؛ کاربر تازه‌وارد، حتی قبل از دیدنِ صفحهٔ ورود، با یک دیالوگِ سیستمیِ
 * بی‌زمینه روبه‌رو می‌شد. طبقِ تصمیمِ فاز ۸.۳، درخواست حالا **زمینه‌دار** است:
 * بعد از ورود و با یک توضیحِ درون‌برنامه‌ای، و برای همیشه در «تنظیمات ← اعلان‌ها»
 * یک کارتِ وضعیت هست.
 *
 * سه حالتِ ممکن (و تفاوتشان مهم است):
 *  • مجاز        → `granted == true`
 *  • رد شد ولی می‌شود دوباره پرسید → `canAskAgain == true` (دیالوگِ سیستمی می‌آید)
 *  • ردِ قطعی    → فقط «تنظیماتِ اعلان‌ها» کار می‌کند؛ پرسیدنِ دوباره بی‌اثر است
 */
object NotificationPermission {

    /** روی ۱۳+ مجوزِ زمانِ اجرا لازم است؛ پایین‌تر از آن هم وضعیتِ اعلان‌ها معنا دارد. */
    fun granted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** اگر false باشد، درخواستِ دوباره دیالوگی نشان نمی‌دهد و باید به تنظیمات بفرستیم. */
    fun canAskAgain(activity: Activity?): Boolean = when {
        Build.VERSION.SDK_INT < 33 -> false          // روی ۱۳- مجوزی برای گرفتن نیست
        activity == null -> false
        else -> ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /**
     * باز کردنِ تنظیماتِ اعلانِ همین اپ در اندروید.
     * اگر صفحهٔ اختصاصیِ اعلان‌ها در دسترس نبود، به «جزئیاتِ برنامه» می‌افتیم
     * (بعضی رام‌ها صفحهٔ اول را نمی‌شناسند).
     */
    fun openSettings(context: Context) {
        val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(direct) }.isSuccess
        if (ok) return
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}
