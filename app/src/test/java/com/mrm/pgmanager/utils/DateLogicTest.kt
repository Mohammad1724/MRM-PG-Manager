package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `daysLeftText covers unlimited, expired and today`() {
        assertEquals("نامحدود", DateLogic.daysLeftText(null))
        assertEquals("نامحدود", DateLogic.daysLeftText("0"))
        assertEquals("امروز", DateLogic.daysLeftText(DateLogic.expireValue(today().toString()) as String))
        assertEquals("منقضی", DateLogic.daysLeftText(DateLogic.expireValue(today().minusDays(2).toString()) as String))
    }
}
