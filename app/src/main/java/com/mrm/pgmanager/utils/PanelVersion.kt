package com.mrm.pgmanager.utils

/**
 * نسخه‌شناسیِ پنل — یک تابعِ **خالص** تا هم در UI و هم در تست‌های JVM قابلِ اتکا باشد.
 *
 * چرا لازم شد: اپ از نسخهٔ ۰٫۱۰ فقط **PasarGuard 5.5.1 به بالا** را پشتیبانی می‌کند
 * (قابلیت‌های تازه مثل پاک‌سازیِ کاربرانِ بی‌فعال، ترتیبِ نودها و `hwid_count` در
 * v5.5 اضافه شده‌اند). ولی نه می‌خواهیم با یک نسخهٔ قدیمی اپ را کامل قفل کنیم و نه
 * می‌خواهیم دکمه‌ای نشان بدهیم که پنل قدیمی با ۴۰۴/۴۲۲ جوابش بدهد؛ پس نسخه را
 * می‌خوانیم و در صورتِ قدیمی‌بودن فقط «هشدار» می‌دهیم و قابلیت‌های ۵.۵ را پنهان
 * می‌کنیم.
 *
 * قواعدِ پارس:
 *  - `5.5.1`، `v5.5.1`، `5.5.1-beta.2`، `5.5.1+meta`، `5.5` و `5` پذیرفته می‌شوند.
 *  - بخش‌های غایب صفر حساب می‌شوند (`5.5` = `5.5.0`).
 *  - هر چیزی که با رقم شروع نشود (مثلاً رشتهٔ خالی یا `dev`) → `null` یعنی «نامعلوم».
 *
 * سیاستِ «نامعلوم»: **مدارا** — اگر نسخه را ندانیم (مثلاً نقشِ محدود که مجوزِ
 * `system.read` ندارد و `/api/system` برایش ۴۰۳ می‌دهد) قابلیت‌ها پنهان نمی‌شوند و
 * هشدار هم نشان داده نمی‌شود؛ پنل خودش آخرین حرف را می‌زند.
 */
object PanelVersion {

    /** کمترین نسخهٔ پنلی که این نسخهٔ اپ رویش تست و پشتیبانی شده است. */
    const val MIN_SUPPORTED = "5.5.1"

    private val pattern = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

    /** `"v5.5.1"` → `[5, 5, 1]`؛ اگر قابلِ خواندن نباشد `null`. */
    fun parts(version: String?): List<Int>? {
        val raw = version?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = pattern.find(raw) ?: return null
        val groups = match.groupValues
        return listOf(
            groups.getOrNull(1)?.toIntOrNull() ?: return null,
            groups.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
            groups.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        )
    }

    /**
     * مقایسهٔ دو نسخه: منفی اگر [a] قدیمی‌تر، صفر اگر برابر، مثبت اگر تازه‌تر.
     * اگر هرکدام قابلِ خواندن نباشد `null` (یعنی «نمی‌دانم»، نه «قدیمی»).
     */
    fun compare(a: String?, b: String?): Int? {
        val pa = parts(a) ?: return null
        val pb = parts(b) ?: return null
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    /** آیا نسخه ≥ [minimum] است؟ `null` یعنی نامعلوم. */
    fun isAtLeast(version: String?, minimum: String = MIN_SUPPORTED): Boolean? =
        compare(version, minimum)?.let { it >= 0 }

    /**
     * «آیا می‌شود روی این پنل قابلیتِ [minimum] را نشان داد؟»
     * نامعلوم = بله (مدارا)، تا ادمینِ محدود قابلیتی را بی‌دلیل از دست ندهد.
     */
    fun supports(version: String?, minimum: String = MIN_SUPPORTED): Boolean =
        isAtLeast(version, minimum) != false

    /** نسخهٔ نمایشی برای UI: `"5.5.1"` (بدونِ `v`) یا `""` اگر نامعلوم است. */
    fun label(version: String?): String = parts(version)?.joinToString(".") ?: ""
}
