package com.mrm.pgmanager.utils

/**
 * تبدیل ارقام فارسی/عربی به انگلیسی برای پارس عددی.
 * کاربر فارسی ممکن است با کیبورد فارسی عدد تایپ کند (۰۱۲۳) که toIntOrNull آن را null برمی‌گرداند و باعث باگ «نامحدود شدن» می‌شود.
 */
fun normalizePersianDigits(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val normalized = when (ch) {
            '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
            '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            else -> ch
        }
        sb.append(normalized)
    }
    return sb.toString()
}

// `DecimalFormat` نه ارزان ساخته می‌شود نه thread-safe است؛ در فهرستِ کاربران
// برای هر کارت دو بار صدا زده می‌شود، پس یک نمونه به‌ازای هر نخ نگه می‌داریم.
private val bytesFormat = object : ThreadLocal<java.text.DecimalFormat>() {
    override fun initialValue() = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
}

/**
 * شمارشِ بزرگ را فشرده می‌کند: `1791007477` → «1.79B».
 *
 * در شیتِ جزئیاتِ کاربر، خطِ خلاصهٔ «IPهای آنلاین» عددِ خامِ اتصال‌ها را می‌نوشت و
 * عددهای میلیاردی (که پنل در شمارشِ تجمعی می‌دهد) خط را پر می‌کرد. فشرده‌سازی همان
 * الگوی `formatBytes` است: دو رقمِ اعشار، بدونِ صفرِ اضافی، با اعدادِ لاتین.
 */
fun formatCompactCount(value: Long): String {
    if (value < 1_000L) return value.toString()
    val units = arrayOf("K", "M", "B", "T")
    var v = value.toDouble()
    var index = 0
    while (v >= 1_000.0 && index < units.size - 1) { v /= 1_000.0; index++ }
    return "${bytesFormat.get()!!.format(v)}${units[index - 1]}"
}

fun formatBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val index = (kotlin.math.ln(value.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return "${bytesFormat.get()!!.format(value / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
}

/** درصد با یک رقم اعشار و اعدادِ لاتین (مستقل از locale دستگاه). */
fun formatPercent(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)

/**
 * مدت زمان (ثانیه) به متن خوانا: «۲ روز و ۳ ساعت».
 * برای uptime سرور استفاده می‌شود.
 */
/**
 * مدتِ روشن‌بودن — **بدون متن**؛ ترجمه در لایهٔ UI انجام می‌شود
 * (`uptimeText` در utils/UiText.kt). پیش از این رشتهٔ فارسی همین‌جا ساخته
 * می‌شد و در زبانِ انگلیسی هم فارسی می‌ماند.
 */
sealed interface Uptime {
    data object None : Uptime
    data class DaysHours(val days: Int, val hours: Int) : Uptime
    data class HoursMinutes(val hours: Int, val minutes: Int) : Uptime
    data class Minutes(val minutes: Int) : Uptime
    data class Seconds(val seconds: Int) : Uptime
}

fun uptimeOf(seconds: Long): Uptime {
    if (seconds <= 0L) return Uptime.None
    val days = (seconds / 86_400L).toInt()
    val hours = ((seconds % 86_400L) / 3_600L).toInt()
    val minutes = ((seconds % 3_600L) / 60L).toInt()
    return when {
        days > 0 -> Uptime.DaysHours(days, hours)
        hours > 0 -> Uptime.HoursMinutes(hours, minutes)
        minutes > 0 -> Uptime.Minutes(minutes)
        else -> Uptime.Seconds(seconds.toInt())
    }
}
