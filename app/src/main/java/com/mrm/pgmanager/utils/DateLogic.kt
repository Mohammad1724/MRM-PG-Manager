package com.mrm.pgmanager.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * منطق خالص محاسبات تاریخ/انقضای اشتراک — بدون وابستگی به اندروید تا قابل تست واحد باشد.
 * تمام توابع از «امروز» به وقت دستگاه استفاده می‌کنند (مثل بقیهٔ برنامه).
 */
object DateLogic {

    /**
     * مقدار `expire` که به پنل فرستاده می‌شود.
     * - خالی / "null" / "0" → 0 (نامحدود طبق قرارداد پنل)
     * - تاریخ گذشته → ISO همان تاریخ به پایان روز
     * - تاریخ امروز → ۱ روز کامل از اکنون
     * - تاریخ آینده → `now + N روز` (ISO)
     */
    fun expireValue(date: String?): Any {
        if (date.isNullOrBlank() || date == "null" || date == "0") return 0
        val target = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()
            ?: return "${date}T23:59:59Z"
        val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
        if (days < 0L) return "${date}T23:59:59Z"
        if (days == 0L) return Instant.now().plusSeconds(86400L).toString()
        return Instant.now().plusSeconds(days * 86400L).toString()
    }

    /** تعداد روزهای باقی‌مانده تا انقضا؛ null برای «نامحدود/نامشخص»، صفر یا منفی برای «منقضی». */
    fun remainingDays(expire: String?): Long? {
        if (expire.isNullOrBlank() || expire == "0" || expire == "null") return null
        return runCatching {
            val end = try {
                Instant.parse(expire).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (_: Exception) {
                LocalDate.parse(expire.take(10))
            }
            ChronoUnit.DAYS.between(LocalDate.now(), end)
        }.getOrNull()
    }

    /** آیا اشتراک در آستانهٔ انقضا (کمتر یا مساوی N روز) قرار دارد؟ کاربرِ نامحدود/نامشخص → false. */
    fun isNearExpiry(expire: String?, nearDays: Int): Boolean {
        val days = remainingDays(expire) ?: return false
        return days in 0..nearDays.toLong()
    }

    /** متن فارسیِ روزهای باقی‌مانده (نامحدود / منقضی / امروز / N روز). */
    fun daysLeftText(expire: String?): String {
        if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
        val days = remainingDays(expire) ?: return "نامحدود"
        return when {
            days < 0L -> "منقضی"
            days == 0L -> "امروز"
            days == 1L -> "۱ روز"
            else -> "$days روز"
        }
    }
}
