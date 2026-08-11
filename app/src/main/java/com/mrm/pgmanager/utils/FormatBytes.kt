package com.mrm.pgmanager.utils

fun formatBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val index = (kotlin.math.ln(value.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    val df = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
    return "${df.format(value / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
}

/** درصد با یک رقم اعشار و اعدادِ لاتین (مستقل از locale دستگاه). */
fun formatPercent(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)

/**
 * مدت زمان (ثانیه) به متن خوانا: «۲ روز و ۳ ساعت».
 * برای uptime سرور استفاده می‌شود.
 */
fun formatUptime(seconds: Long): String {
    if (seconds <= 0L) return "—"
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return when {
        days > 0L -> "$days روز و $hours ساعت"
        hours > 0L -> "$hours ساعت و $minutes دقیقه"
        minutes > 0L -> "$minutes دقیقه"
        else -> "$seconds ثانیه"
    }
}
