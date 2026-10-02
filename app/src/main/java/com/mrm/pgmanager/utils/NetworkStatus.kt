package com.mrm.pgmanager.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

/**
 * وضعیتِ اتصال برای «آفلاینِ هوشمند» (فاز ۵.۳).
 *
 * پیش از این، اپ در آفلاین هم دکمه‌ها را فعال می‌گذاشت و کاربر بعد از
 * ۱۵ ثانیه انتظار، خطای شبکه می‌گرفت. حالا عملیاتِ نوشتاری **پیش از تلاش**
 * متوقف می‌شود و هنگام بازگشتِ اتصال خودش اجرا می‌شود.
 *
 * پیاده‌سازی عمداً بدونِ StateFlow/callback است: یک `isOnline` سادهٔ
 * همگام + انتظارِ کوتاه، وابستگیِ اضافه و چرخهٔ عمرِ سنگین ندارد.
 * فاصلهٔ ۱ ثانیه‌ای یعنی بازگشتِ خودکار در بدترین حالت ~۱ ثانیه بعد از
 * وصل‌شدن است (معیارِ پذیرش: <۳ ثانیه).
 */
object NetworkStatus {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // نکتهٔ مهم: پنلِ self-hosted می‌تواند روی شبکهٔ محلیِ **بدونِ اینترنت** باشد
        // (مثلاً Wi-Fi داخلیِ سرور). پس «داشتنِ هر رابطِ شبکه» کافی است و اگر
        // فقط NET_CAPABILITY_INTERNET را شرط می‌گذاشتیم، برای چنین پنل‌هایی
        // نوشتن‌ها را اشتباهاً بلوکه می‌کردیم. تنها حالتی که «آفلاین» می‌گوییم
        // این است که هیچ شبکه‌ای فعال نباشد (حالتِ پرواز/قطعِ کامل).
        val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || hasTransport
    }

    /** تا [timeoutMs] صبر می‌کند تا اتصال برگردد؛ `true` یعنی شد. */
    suspend fun awaitOnline(context: Context, timeoutMs: Long = 120_000L): Boolean {
        if (isOnline(context)) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            if (isOnline(context)) return true
        }
        return isOnline(context)
    }

    private const val POLL_MS = 1_000L
}
