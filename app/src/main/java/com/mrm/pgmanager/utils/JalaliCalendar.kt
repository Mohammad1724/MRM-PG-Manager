package com.mrm.pgmanager.utils

import java.time.LocalDate

object JalaliCalendar {
    data class Date(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String = "%04d/%02d/%02d".format(year, month, day)
        /**
         * نامِ ماه در لایهٔ UI از منابع خوانده می‌شود (`jalali_months`)، چون
         * در انگلیسی باید «Farvardin» نوشته شود نه «فروردین».
         */
        fun monthIndex(): Int = (month - 1).coerceIn(0, 11)
    }
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Date {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        val gy2 = gy - 1600; val gm2 = gm - 1; val gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd2
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053; jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) { jy += (jDayNo - 1) / 365; jDayNo = (jDayNo - 1) % 365 }
        var i = 0; while (i < 11 && jDayNo >= jDaysInMonth[i]) { jDayNo -= jDaysInMonth[i]; i++ }
        return Date(jy, i + 1, jDayNo + 1)
    }
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): String {
        val safeJm = jm.coerceIn(1, 12)
        val safeJd = jd.coerceIn(1, 31)
        val jy2 = jy - 979; val jm2 = safeJm - 1; val jd2 = safeJd - 1
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
        for (i in 0 until jm2) jDayNo += jDaysInMonth[i]
        jDayNo += jd2
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097); gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--; gy += 100 * (gDayNo / 36524); gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }
        gy += 4 * (gDayNo / 1461); gDayNo %= 1461
        if (gDayNo >= 366) { leap = false; gDayNo--; gy += gDayNo / 365; gDayNo %= 365 }
        val gDaysInMonth = intArrayOf(31, if (leap && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var i = 0; while (i < 12 && gDayNo >= gDaysInMonth[i]) { gDayNo -= gDaysInMonth[i]; i++ }
        return "%04d-%02d-%02d".format(gy, i + 1, gDayNo + 1)
    }
    fun isoToShamsi(iso: String?): String {
        if (iso.isNullOrBlank() || iso == "0" || iso == "null") return ""
        val parts = iso.take(10).split("-")
        if (parts.size != 3) return iso.take(10)
        val gy = parts[0].toIntOrNull() ?: return iso.take(10)
        val gm = parts[1].toIntOrNull() ?: return iso.take(10)
        val gd = parts[2].toIntOrNull() ?: return iso.take(10)
        return gregorianToJalali(gy, gm, gd).toString()
    }
    fun shamsiToIso(shamsi: String): String {
        if (shamsi.isBlank()) return ""
        val clean = shamsi.replace("-", "/").split("/")
        if (clean.size != 3) return shamsi
        val jy = clean[0].toIntOrNull() ?: return shamsi
        val jm = clean[1].toIntOrNull() ?: return shamsi
        val jd = clean[2].toIntOrNull() ?: return shamsi
        return jalaliToGregorian(jy, jm, jd)
    }
    fun todayJalali(): Date {
        val today = LocalDate.now()
        return gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
    }
    fun addDaysToIso(iso: String?, daysToAdd: Int): String {
        val baseDate = if (iso.isNullOrBlank() || iso == "0" || iso == "null") LocalDate.now()
        else runCatching { LocalDate.parse(iso.take(10)) }.getOrDefault(LocalDate.now())
        return baseDate.plusDays(daysToAdd.toLong()).toString()
    }
}

private fun faNum(n: Long): String = n.toString().map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

/**
 * ماژول‌محور (نه private) تا SessionStore هم بتواند همین منطقِ پارس را برای بازسازیِ isOnline از کش استفاده کند.
 *
 * هم‌راستا با fallbackِ `PanelApi.parseUser`: علاوه‌بر ISO-instant (با Z) و
 * timestamp، حالتِ naive datetime (بدونِ timezone، مثلِ `"2024-01-15 10:30:00"`)
 * را هم با `LocalDateTime` + zoneِ دستگاه پوشش می‌دهد — قبلاً این تابع فقط
 * حالتِ اول و سوم را می‌فهمید و برای naive همیشه null برمی‌گرداند.
 */
internal fun parseOnlineMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val s = raw.replace(" ", "T")
    runCatching { return java.time.Instant.parse(s).toEpochMilli() }
    runCatching { return java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    runCatching { val ts = raw.trim().toLong(); return if (ts < 1_000_000_000_000L) ts * 1000 else ts }
    return null
}

/** متنِ «آخرین آنلاین» به فارسی: آنلاین / X دقیقه پیش / X ساعت پیش / X روز پیش / ... */
fun lastSeenText(onlineAt: String?, isOnline: Boolean): String {
    val isFa = java.util.Locale.getDefault().language == "fa"
    if (isOnline) return if (isFa) "آنلاین" else "Online"
    val millis = parseOnlineMillis(onlineAt) ?: return if (isFa) "آخرین آنلاین: نامشخص" else "Last seen: Unknown"
    val diffMin = (System.currentTimeMillis() - millis) / 60000L
    if (diffMin <= 0L) return if (isFa) "آنلاین" else "Online"
    return if (isFa) {
        when {
            diffMin < 1L -> "هم‌اکنون"
            diffMin < 60L -> "${faNum(diffMin)} دقیقه پیش"
            diffMin < 1440L -> "${faNum(diffMin / 60L)} ساعت پیش"
            diffMin < 1440L * 30L -> "${faNum(diffMin / 1440L)} روز پیش"
            diffMin < 1440L * 365L -> "${faNum(diffMin / (1440L * 30L))} ماه پیش"
            else -> "${faNum(diffMin / (1440L * 365L))} سال پیش"
        }
    } else {
        when {
            diffMin < 1L -> "Just now"
            diffMin < 60L -> "$diffMin minutes ago"
            diffMin < 1440L -> "${diffMin / 60L} hours ago"
            diffMin < 1440L * 30L -> "${diffMin / 1440L} days ago"
            diffMin < 1440L * 365L -> "${diffMin / (1440L * 30L)} months ago"
            else -> "${diffMin / (1440L * 365L)} years ago"
        }
    }
}

/** فرمتِ کوتاهِ «آخرین آنلاین»: آنلاین / 4m / 4h / 4d / 4w / 3mo / 1y */
fun lastSeenShort(onlineAt: String?, isOnline: Boolean): String {
    val isFa = java.util.Locale.getDefault().language == "fa"
    if (isOnline) return if (isFa) "آنلاین" else "Online"
    val millis = parseOnlineMillis(onlineAt) ?: return ""
    val diffMin = (System.currentTimeMillis() - millis) / 60000L
    if (diffMin <= 0L) return if (isFa) "آنلاین" else "Online"
    return when {
        diffMin < 60L -> "${diffMin}m"
        diffMin < 1440L -> "${diffMin / 60L}h"
        diffMin < 10080L -> "${diffMin / 1440L}d"
        diffMin < 43200L -> "${diffMin / 10080L}w"
        diffMin < 525600L -> "${diffMin / 43200L}mo"
        else -> "${diffMin / 525600L}y"
    }
}