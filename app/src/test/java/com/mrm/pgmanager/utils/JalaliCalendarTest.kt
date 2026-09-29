package com.mrm.pgmanager.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * تقویمِ شمسی باید در هر locale ای خروجیِ **قابلِ پارس** بدهد.
 *
 * اپ locale پیش‌فرض را روی `fa` می‌گذارد؛ در این حالت `String.format` بدونِ Locale
 * صریح با `%d` ارقامِ فارسی می‌سازد و `LocalDate.parse` روی «۲۰۲۶-۱۱-۰۱» می‌شکند —
 * دقیقاً همان چیزی که انتخابِ تاریخ از تقویمِ ویرایشگر را در حالتِ فارسی خالی می‌کرد.
 */
class JalaliCalendarTest {

    private lateinit var saved: Locale

    @Before fun forcePersianLocale() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale("fa"))
    }

    @After fun restoreLocale() { Locale.setDefault(saved) }

    @Test fun `machine-readable strings stay ASCII under the fa locale`() {
        assertEquals("1405/08/10", JalaliCalendar.Date(1405, 8, 10).toString())
        assertEquals("2026-11-01", JalaliCalendar.jalaliToGregorian(1405, 8, 10))
        assertEquals("2026-11-01", JalaliCalendar.shamsiToIso("1405/08/10"))
        // ورودیِ با ارقامِ فارسی (همان چیزی که تقویم برمی‌گرداند) هم پذیرفته می‌شود.
        assertEquals("2026-11-01", JalaliCalendar.shamsiToIso("۱۴۰۵/۰۸/۱۰"))
        // مسیرِ «سالِ میلادی با اسلش» هم ASCII می‌ماند.
        assertEquals("2026-11-01", JalaliCalendar.shamsiToIso("2026/11/01"))
    }

    @Test fun `picker round trip parses in the fa locale`() {
        // همان کاری که ویرایشگر می‌کند: تقویم → shamsiToIso → LocalDate.parse
        val picked = JalaliCalendar.Date(1405, 8, 10).toString()
        val iso = JalaliCalendar.shamsiToIso(picked).take(10)
        assertEquals(LocalDate.of(2026, 11, 1), LocalDate.parse(iso))
        val today = JalaliCalendar.todayJalali().toString()
        assertEquals(LocalDate.now(), LocalDate.parse(JalaliCalendar.shamsiToIso(today).take(10)))
    }

    @Test fun `gregorian and jalali conversions are inverse`() {
        for (d in listOf(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 21), LocalDate.of(2024, 2, 29), LocalDate.of(2025, 12, 31))) {
            val j = JalaliCalendar.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
            assertEquals(d.toString(), JalaliCalendar.jalaliToGregorian(j.year, j.month, j.day))
        }
        assertEquals("1405/01/01", JalaliCalendar.gregorianToJalali(2026, 3, 21).toString())
    }

    @Test fun `isoToShamsi is display-only and follows the UI language`() {
        // فارسی → ارقامِ فارسی (همان ظاهرِ قبلی)
        assertEquals("۱۴۰۵/۰۸/۱۰", JalaliCalendar.isoToShamsi("2026-11-01"))
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("1405/08/10", JalaliCalendar.isoToShamsi("2026-11-01"))
        assertEquals("", JalaliCalendar.isoToShamsi(null))
        assertEquals("", JalaliCalendar.isoToShamsi("0"))
    }

    @Test fun `isoToShamsi converts full timestamps to the local day`() {
        Locale.setDefault(Locale.ENGLISH)
        val ts = "2026-10-31T21:30:00Z"
        val local = java.time.Instant.parse(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val expected = JalaliCalendar.gregorianToJalali(local.year, local.monthValue, local.dayOfMonth).toString()
        assertEquals(expected, JalaliCalendar.isoToShamsi(ts))
    }
}
