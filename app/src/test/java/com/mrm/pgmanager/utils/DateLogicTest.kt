package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * تست‌های منطقِ تاریخ/انقضا.
 *
 * مهم‌ترین رفتاری که اینجا قفل می‌شود: `expireValue` باید **پایانِ روزِ انتخاب‌شده**
 * را بفرستد، نه «اکنون + N روز». پیاده‌سازیِ قبلی برای «امروز» ۲۴ ساعت اضافه می‌کرد
 * که باعث می‌شد اشتراک تا فردا معتبر بماند.
 */
class DateLogicTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun today(): LocalDate = LocalDate.now()
    private fun endOfDay(d: LocalDate): Instant =
        d.atTime(23, 59, 59).atZone(zone).toInstant()

    // ── نامحدود ───────────────────────────────────────────────

    @Test fun `null or blank means unlimited`() {
        assertEquals(0, DateLogic.expireValue(null))
        assertEquals(0, DateLogic.expireValue(""))
        assertEquals(0, DateLogic.expireValue("   "))
    }

    @Test fun `literal zero and null strings mean unlimited`() {
        assertEquals(0, DateLogic.expireValue("0"))
        assertEquals(0, DateLogic.expireValue("null"))
    }

    @Test fun `unparsable date falls back to unlimited instead of sending garbage`() {
        assertEquals(0, DateLogic.expireValue("not-a-date"))
        assertEquals(0, DateLogic.expireValue("2026-13-45"))
    }

    // ── هستهٔ رفعِ باگ ────────────────────────────────────────

    @Test fun `today expires at end of today, not 24h from now`() {
        val sent = DateLogic.expireValue(today().toString()) as String
        assertEquals(endOfDay(today()), Instant.parse(sent))
    }

    @Test fun `today is strictly less than 24 hours away`() {
        val sent = DateLogic.expireValue(today().toString()) as String
        val hours = Duration.between(Instant.now(), Instant.parse(sent)).toHours()
        assertTrue("expected < 24h, was $hours", hours < 24)
    }

    @Test fun `future date keeps its own calendar day after timezone conversion`() {
        val target = today().plusDays(30)
        val sent = DateLogic.expireValue(target.toString()) as String
        val back = Instant.parse(sent).atZone(zone).toLocalDate()
        assertEquals(target, back)
    }

    @Test fun `past date is preserved and reads as expired`() {
        val target = today().minusDays(5)
        val sent = DateLogic.expireValue(target.toString()) as String
        assertEquals(endOfDay(target), Instant.parse(sent))
        assertTrue((DateLogic.remainingDays(sent) ?: 0L) < 0L)
    }

    /** آنچه می‌فرستیم باید دقیقاً همان چیزی باشد که بعداً می‌خوانیم. */
    @Test fun `expireValue round-trips through remainingDays`() {
        listOf(0L, 1L, 7L, 30L, 90L, 365L).forEach { days ->
            val sent = DateLogic.expireValue(today().plusDays(days).toString()) as String
            assertEquals("roundtrip failed for +${days}d", days, DateLogic.remainingDays(sent))
        }
    }

    // ── remainingDays ────────────────────────────────────────

    @Test fun `remainingDays is null for unlimited`() {
        assertNull(DateLogic.remainingDays(null))
        assertNull(DateLogic.remainingDays(""))
        assertNull(DateLogic.remainingDays("0"))
        assertNull(DateLogic.remainingDays("null"))
    }

    @Test fun `remainingDays accepts plain yyyy-MM-dd as well as ISO instant`() {
        val target = today().plusDays(10)
        assertEquals(10L, DateLogic.remainingDays(target.toString()))
    }

    // ── isNearExpiry ─────────────────────────────────────────

    @Test fun `isNearExpiry respects the window`() {
        val inThree = DateLogic.expireValue(today().plusDays(3).toString()) as String
        assertTrue(DateLogic.isNearExpiry(inThree, 7))
        assertTrue(!DateLogic.isNearExpiry(inThree, 1))
    }

    @Test fun `unlimited users are never near expiry`() {
        assertTrue(!DateLogic.isNearExpiry(null, 7))
        assertTrue(!DateLogic.isNearExpiry("0", 30))
    }

    // ── daysLeftText ─────────────────────────────────────────

    @Test fun `daysLeft covers unlimited, expired and today`() {
        // روی نوعِ ساخت‌یافته assert می‌کنیم نه رشته: متنِ نمایشی در لایهٔ UI
        // ترجمه می‌شود و تغییرِ واژه نباید تست را بشکند.
        assertEquals(DateLogic.DaysLeft.Unlimited, DateLogic.daysLeft(null))
        assertEquals(DateLogic.DaysLeft.Unlimited, DateLogic.daysLeft("0"))
        assertEquals(DateLogic.DaysLeft.Today, DateLogic.daysLeft(DateLogic.expireValue(today().toString()) as String))
        assertEquals(DateLogic.DaysLeft.Expired, DateLogic.daysLeft(DateLogic.expireValue(today().minusDays(2).toString()) as String))
    }

    // ── online_at / پنجرهٔ آنلاین ─────────────────────────────

    @Test fun `online window equals the panel's two minutes`() {
        assertEquals(120_000L, DateLogic.ONLINE_WINDOW_MS)
    }

    @Test fun `parseOnlineAtMillis understands Z, offsets, naive-as-UTC and timestamps`() {
        val expected = Instant.parse("2026-09-29T07:20:04Z").toEpochMilli()
        assertEquals(expected, DateLogic.parseOnlineAtMillis("2026-09-29T07:20:04Z"))
        assertEquals(expected, DateLogic.parseOnlineAtMillis("2026-09-29T07:20:04.000Z"))
        assertEquals(expected, DateLogic.parseOnlineAtMillis("2026-09-29T07:20:04+00:00"))
        // offset تهران: همان لحظه، نوشته‌شده به وقتِ محلی
        assertEquals(expected, DateLogic.parseOnlineAtMillis("2026-09-29T10:50:04+03:30"))
        // بدونِ timezone = UTC (قراردادِ پنل)، مستقل از timezone گوشی
        assertEquals(expected, DateLogic.parseOnlineAtMillis("2026-09-29T07:20:04"))
        assertEquals(expected, DateLogic.parseOnlineAtMillis("2026-09-29 07:20:04"))
        // timestamp ثانیه و میلی‌ثانیه
        assertEquals(expected, DateLogic.parseOnlineAtMillis((expected / 1000).toString()))
        assertEquals(expected, DateLogic.parseOnlineAtMillis(expected.toString()))
        assertNull(DateLogic.parseOnlineAtMillis(null))
        assertNull(DateLogic.parseOnlineAtMillis(""))
        assertNull(DateLogic.parseOnlineAtMillis("null"))
        assertNull(DateLogic.parseOnlineAtMillis("garbage"))
    }

    @Test fun `isOnline is true inside the window and false right after it`() {
        val now = Instant.parse("2026-09-29T07:22:00Z").toEpochMilli()
        assertTrue(DateLogic.isOnline("2026-09-29T07:20:01Z", now))   // ۱:۵۹ پیش
        assertFalse(DateLogic.isOnline("2026-09-29T07:20:00Z", now))  // دقیقاً ۲ دقیقه → آفلاین
        assertFalse(DateLogic.isOnline("2026-09-29T07:00:00Z", now))
        assertFalse(DateLogic.isOnline(null, now))
    }

    // ── expiryDate: همهٔ قالب‌های پنل → روزِ محلی ────────────────

    @Test fun `expiryDate converts full timestamps to the local date of the device`() {
        val z = "2026-10-01T20:29:59Z"
        assertEquals(Instant.parse(z).atZone(zone).toLocalDate(), DateLogic.expiryDate(z))
        // offset غیر از Z — قبلاً به «۱۰ کاراکترِ اول» می‌افتاد که روزِ همان offset بود.
        val off = "2026-10-01T23:30:00+03:30"
        assertEquals(java.time.OffsetDateTime.parse(off).toInstant().atZone(zone).toLocalDate(), DateLogic.expiryDate(off))
        // naive = UTC (قراردادِ پنل)
        assertEquals(Instant.parse("2026-10-01T20:29:59Z").atZone(zone).toLocalDate(), DateLogic.expiryDate("2026-10-01 20:29:59"))
        // timestamp عددی (ثانیه)
        assertEquals(Instant.ofEpochSecond(1_767_225_599L).atZone(zone).toLocalDate(), DateLogic.expiryDate("1767225599"))
    }

    @Test fun `expiryDate keeps plain dates and rejects garbage`() {
        assertEquals(LocalDate.of(2026, 11, 1), DateLogic.expiryDate("2026-11-01"))
        assertNull(DateLogic.expiryDate("0"))
        assertNull(DateLogic.expiryDate("null"))
        assertNull(DateLogic.expiryDate("not-a-date"))
    }
}
