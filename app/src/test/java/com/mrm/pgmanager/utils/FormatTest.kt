package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FormatTest {

    /**
     * `formatPercent` جایگزینِ `String.format(...)`های درون‌خطی شد که در کامیت 4fb394d
     * با escapeِ اشتباهِ `\"` نوشته شده بودند و بیلد را می‌شکستند.
     */
    @Test fun `formatPercent keeps one decimal`() {
        assertEquals("0.0", formatPercent(0f))
        assertEquals("42.6", formatPercent(42.567f))
        assertEquals("100.0", formatPercent(100f))
    }

    /** خروجی باید همیشه لاتین باشد، حتی وقتی locale پیش‌فرضِ دستگاه فارسی است. */
    @Test fun `formatPercent is locale independent`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            val out = formatPercent(12.34f)
            assertEquals("12.3", out)
            assertTrue("expected latin digits, got $out", out.all { it.isDigit() || it == '.' })
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `uptimeOf picks the right unit`() {
        // مثلِ daysLeft، اینجا هم روی نوعِ ساخت‌یافته assert می‌کنیم؛ متنِ
        // نهایی در لایهٔ UI از منابع ساخته می‌شود.
        assertEquals(Uptime.None, uptimeOf(0L))
        assertEquals(Uptime.None, uptimeOf(-1L))
        assertEquals(Uptime.Seconds(30), uptimeOf(30L))
        assertEquals(Uptime.Minutes(2), uptimeOf(120L))
        assertEquals(Uptime.HoursMinutes(1, 1), uptimeOf(3_661L))
        assertEquals(Uptime.DaysHours(1, 1), uptimeOf(90_061L))
    }

    @Test fun `formatBytes handles zero negatives and units`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("0 B", formatBytes(-5L))
        assertEquals("1 KB", formatBytes(1_024L))
        assertEquals("1 MB", formatBytes(1_048_576L))
        assertEquals("1 GB", formatBytes(1_073_741_824L))
    }

    @Test fun `formatBytes is locale independent`() {
        val original = Locale.getDefault()
        try {
            // در locale آلمانی جداکنندهٔ اعشار «,» است؛ خروجی نباید تغییر کند.
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5 GB", formatBytes(1_610_612_736L))
        } finally {
            Locale.setDefault(original)
        }
    }

    /**
     * قفلِ رگرسیونِ گزارشِ کاربر: خطِ خلاصهٔ «IPهای آنلاین» عددِ خامِ اتصال‌ها را
     * می‌نوشت («1 IPs · 1791007477 connections») و خط را پر می‌کرد.
     */
    @Test fun `formatCompactCount compresses big counters`() {
        assertEquals("999", formatCompactCount(999))
        assertEquals("1K", formatCompactCount(1_000))
        assertEquals("1.5K", formatCompactCount(1_500))
        assertEquals("1.08K", formatCompactCount(1_080))
        assertEquals("12.3K", formatCompactCount(12_300))
        assertEquals("2M", formatCompactCount(2_000_000))
        assertEquals("1.79B", formatCompactCount(1_791_007_477))
    }

    @Test fun `formatCompactCount is locale independent`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)   // جداکنندهٔ اعشار «,» — خروجی نباید عوض شود
            assertEquals("1.79B", formatCompactCount(1_791_007_477))
            assertEquals("1.5K", formatCompactCount(1_500))
        } finally {
            Locale.setDefault(original)
        }
    }
}
