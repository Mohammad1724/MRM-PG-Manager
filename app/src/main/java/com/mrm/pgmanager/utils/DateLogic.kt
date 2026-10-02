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
     * پنجرهٔ «آنلاین» — دقیقاً همان `_ONLINE_USERS_WINDOW = 2 min` پنل (`app/db/crud/user.py`).
     * قبلاً ۵ دقیقه بود و شمارندهٔ آنلاینِ اپ با «کاربران آنلاین» خودِ پنل نمی‌خواند.
     */
    const val ONLINE_WINDOW_MS: Long = 2L * 60L * 1000L

    /**
     * `online_at` پنل → epoch millis. پنل تاریخ‌ها را آگاه از timezone می‌دهد
     * (`2026-09-29T07:20:04Z` یا با offset مثل `+03:30`)؛ رشتهٔ بدونِ timezone را — مطابق
     * قراردادِ خودِ پنل (`fix_datetime_timezone`) — UTC فرض می‌کنیم، نه وقتِ محلیِ گوشی؛
     * وگرنه در ایران (UTC+3:30) هر کاربرِ آنلاینی ۳٫۵ ساعت «پیش» دیده می‌شد.
     * timestamp عددی (ثانیه یا میلی‌ثانیه) هم پذیرفته می‌شود.
     */
    fun parseOnlineAtMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(" ", "T")
        if (s == "null") return null
        runCatching { return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }
        runCatching { val ts = s.toLong(); return if (ts < 1_000_000_000_000L) ts * 1000 else ts }
        return null
    }

    /** آیا با توجه به `online_at` کاربر در لحظهٔ `nowMillis` آنلاین محسوب می‌شود؟ */
    fun isOnline(onlineAt: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val at = parseOnlineAtMillis(onlineAt) ?: return false
        return at > 0L && nowMillis - at < ONLINE_WINDOW_MS
    }

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
    /**
     * جابه‌جاییِ تاریخِ انقضا به‌اندازهٔ [days] روز (منفی = کم‌کردن) — فاز ۶.۱.
     *
     * برای «پیش‌نمایشِ خوش‌بینانه» لازم است: کاربر بعد از تأییدِ «+۳۰ روز»
     * باید **همان لحظه** تاریخِ تازه را روی ردیف ببیند، نه بعد از برگشتِ پاسخ.
     *
     * ورودی همان ISO-8601 پنل است (مثل `2026-11-01T20:29:59Z`). اگر مقدار
     * نامعتبر/خالی/`null` باشد، `null` برمی‌گردد — و همین درست است: پنل هم
     * کاربرِ بدونِ تاریخِ انقضا را هدفِ «افزودن روز» نمی‌شمارد.
     */
    fun shiftIsoDays(expire: String?, days: Long): String? {
        val raw = expire?.trim()
        if (raw.isNullOrBlank() || raw == "0" || raw == "null") return null
        val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
        return instant.plus(days, ChronoUnit.DAYS).toString()
    }

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
        val raw = expire.trim()
        // زمانِ کامل (Z / offset / naive=UTC / timestamp) → روزِ محلیِ دستگاه.
        // قبلاً فقط `Instant.parse` (یعنی فقط پسوندِ Z) امتحان می‌شد و برای
        // `+03:30` به «۱۰ کاراکترِ اول» می‌افتاد که تاریخِ همان offset بود نه روزِ محلی.
        if (raw.length > 10 || raw.all { it.isDigit() }) {
            parseOnlineAtMillis(raw)?.let { return Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        }
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
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
