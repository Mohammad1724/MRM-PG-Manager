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
     * - در غیر این صورت: **پایانِ همان روزِ انتخاب‌شده** به وقتِ محلیِ دستگاه،
     *   که به UTC تبدیل و به‌صورت ISO-8601 فرستاده می‌شود.
     *
     * نکته: پنل `expire` را به‌صورت datetime آگاه از timezone می‌پذیرد
     * (`AwareDatetime`)، بنابراین ارسالِ لحظهٔ دقیق درست‌تر از «now + N روز» است.
     * با این کار، انتخابِ «امروز» یعنی «تا آخرِ امشب» — نه ۲۴ ساعت از این لحظه.
     */
    fun expireValue(date: String?): Any {
        if (date.isNullOrBlank() || date == "null" || date == "0") return 0
        val target = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return 0
        return endOfDayUtcIso(target)
    }

    /** پایانِ روزِ داده‌شده (23:59:59 محلی) به‌صورت ISO-8601 در UTC. */
    fun endOfDayUtcIso(date: LocalDate): String =
        date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toString()

    /**
     * تاریخِ انقضا به‌صورت [LocalDate] محلی؛ `null` برای «نامحدود/نامشخص/نامعتبر».
     * هم ISO-8601 کاملِ پنل را می‌پذیرد و هم رشتهٔ سادهٔ `yyyy-MM-dd` را.
     */
    fun expiryDate(expire: String?): LocalDate? {
        if (expire.isNullOrBlank() || expire == "0" || expire == "null") return null
        return runCatching {
            try {
                Instant.parse(expire).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (_: Exception) {
                LocalDate.parse(expire.take(10))
            }
        }.getOrNull()
    }

    /**
     * تعداد روزهای باقی‌مانده تا انقضا؛ null برای «نامحدود/نامشخص»، صفر یا منفی برای «منقضی».
     *
     * [today] فقط برای تست تزریق می‌شود؛ در اپ همان تاریخِ امروزِ دستگاه است.
     */
    fun remainingDays(expire: String?, today: LocalDate = LocalDate.now()): Long? {
        val end = expiryDate(expire) ?: return null
        return ChronoUnit.DAYS.between(today, end)
    }

    /** آیا اشتراک در آستانهٔ انقضا (کمتر یا مساوی N روز) قرار دارد؟ کاربرِ نامحدود/نامشخص → false. */
    fun isNearExpiry(expire: String?, nearDays: Int): Boolean {
        val days = remainingDays(expire) ?: return false
        return days in 0..nearDays.toLong()
    }

    /**
     * وضعیتِ زمانِ باقی‌مانده — **بدون متن**.
     *
     * قبلاً همین‌جا رشتهٔ فارسی ساخته می‌شد و در حالتِ انگلیسی هم فارسی
     * نمایش داده می‌شد. حالا این لایه فقط «معنا» را برمی‌گرداند و ترجمه‌اش
     * در لایهٔ UI انجام می‌شود (`daysLeftText` در utils/UiText.kt).
     */
    sealed interface DaysLeft {
        data object Unlimited : DaysLeft
        data object Expired : DaysLeft
        data object Today : DaysLeft
        data class Days(val count: Int) : DaysLeft
    }

    fun daysLeft(expire: String?): DaysLeft {
        if (expire.isNullOrBlank() || expire == "0" || expire == "null") return DaysLeft.Unlimited
        val days = remainingDays(expire) ?: return DaysLeft.Unlimited
        return when {
            days < 0L -> DaysLeft.Expired
            days == 0L -> DaysLeft.Today
            else -> DaysLeft.Days(days.toInt())
        }
    }
}
