package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RenewalLogicTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 10)

    /** ساختِ یک مقدارِ expire که دقیقاً N روز با [today] فاصله دارد. */
    private fun expireIn(days: Long): String =
        DateLogic.endOfDayUtcIso(today.plusDays(days))

    // ---------- newExpiryDate: حالتِ EXTEND ----------

    @Test
    fun extend_addsToRemainingTime() {
        // ۱۰ روز باقی + ۳۰ روز تمدید = ۴۰ روز از امروز
        val result = RenewalLogic.newExpiryDate(expireIn(10), 30, today = today)
        assertEquals(today.plusDays(40), result)
    }

    @Test
    fun extend_expiredUserStartsFromToday() {
        // ۵ روز از انقضا گذشته → نباید ۲۵ روز بدهد، باید ۳۰ روزِ کامل بدهد
        val result = RenewalLogic.newExpiryDate(expireIn(-5), 30, today = today)
        assertEquals(today.plusDays(30), result)
    }

    @Test
    fun extend_expiringTodayGivesFullPeriod() {
        val result = RenewalLogic.newExpiryDate(expireIn(0), 30, today = today)
        assertEquals(today.plusDays(30), result)
    }

    @Test
    fun extend_unlimitedUserStartsFromToday() {
        assertEquals(today.plusDays(30), RenewalLogic.newExpiryDate(null, 30, today = today))
        assertEquals(today.plusDays(30), RenewalLogic.newExpiryDate("0", 30, today = today))
        assertEquals(today.plusDays(30), RenewalLogic.newExpiryDate("", 30, today = today))
    }

    @Test
    fun extend_plainDateStringIsAccepted() {
        val result = RenewalLogic.newExpiryDate("2026-03-20", 10, today = today)
        assertEquals(today.plusDays(20), result)
    }

    // ---------- newExpiryDate: حالتِ FROM_TODAY ----------

    @Test
    fun fromToday_ignoresRemainingTime() {
        val result = RenewalLogic.newExpiryDate(
            expireIn(100), 30, RenewalLogic.Mode.FROM_TODAY, today
        )
        assertEquals(today.plusDays(30), result)
    }

    @Test
    fun fromToday_matchesLegacyResetBehaviour() {
        // رفتارِ دیالوگِ «ریستِ زمان» که از قبل در اپ بود
        for (d in intArrayOf(7, 30, 60, 90)) {
            assertEquals(
                today.plusDays(d.toLong()),
                RenewalLogic.newExpiryDate(expireIn(45), d, RenewalLogic.Mode.FROM_TODAY, today)
            )
        }
    }

    // ---------- ورودیِ نامعتبر ----------

    @Test
    fun nonPositiveDaysIsRejected() {
        assertNull(RenewalLogic.newExpiryDate(expireIn(10), 0, today = today))
        assertNull(RenewalLogic.newExpiryDate(expireIn(10), -5, today = today))
        assertNull(RenewalLogic.newExpiryDateString(expireIn(10), 0, today = today))
    }

    // ---------- خروجیِ رشته‌ای ----------

    @Test
    fun dateStringIsPlainIsoDate() {
        val s = RenewalLogic.newExpiryDateString(expireIn(10), 30, today = today)!!
        assertEquals("2026-04-19", s)
    }

    /**
     * خروجی باید از مسیرِ واقعیِ اپ (PanelApi → DateLogic.expireValue) سالم رد شود
     * و همان روز را بدهد — این تستِ همان باگِ take(10) روی ISO‌ی UTC است.
     */
    @Test
    fun dateStringSurvivesExpireValueRoundTrip() {
        val s = RenewalLogic.newExpiryDateString(expireIn(10), 30, today = today)!!
        val sent = DateLogic.expireValue(s) as String
        val back = java.time.Instant.parse(sent).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(today.plusDays(40), back)
    }

    @Test
    fun dateStringRoundTripsThroughRemainingDays() {
        val s = RenewalLogic.newExpiryDateString(null, 30, today = LocalDate.now())!!
        assertEquals(30L, DateLogic.remainingDays(s))
    }

    // ---------- bucket ----------

    @Test
    fun bucket_expiredByDate() {
        assertEquals(
            RenewalLogic.Bucket.EXPIRED,
            RenewalLogic.bucket(expireIn(-1), 0L, 0L, 7, today)
        )
    }

    @Test
    fun bucket_expiredByTraffic() {
        // تاریخ خوب است ولی حجم تمام شده
        assertEquals(
            RenewalLogic.Bucket.EXPIRED,
            RenewalLogic.bucket(expireIn(100), 50L, 50L, 7, today)
        )
        assertEquals(
            RenewalLogic.Bucket.EXPIRED,
            RenewalLogic.bucket(expireIn(100), 60L, 50L, 7, today)
        )
    }

    @Test
    fun bucket_unlimitedTrafficIsNotExpired() {
        // dataLimit == 0 یعنی نامحدود، پس مصرفِ زیاد مهم نیست
        assertEquals(
            RenewalLogic.Bucket.OK,
            RenewalLogic.bucket(expireIn(100), 999L, 0L, 7, today)
        )
    }

    @Test
    fun bucket_soonWindowIsInclusive() {
        assertEquals(RenewalLogic.Bucket.SOON, RenewalLogic.bucket(expireIn(0), 0L, 0L, 7, today))
        assertEquals(RenewalLogic.Bucket.SOON, RenewalLogic.bucket(expireIn(7), 0L, 0L, 7, today))
        assertEquals(RenewalLogic.Bucket.OK, RenewalLogic.bucket(expireIn(8), 0L, 0L, 7, today))
    }

    @Test
    fun bucket_unlimitedExpiryIsOk() {
        assertEquals(RenewalLogic.Bucket.OK, RenewalLogic.bucket(null, 0L, 0L, 7, today))
        assertEquals(RenewalLogic.Bucket.OK, RenewalLogic.bucket("0", 0L, 0L, 7, today))
    }

    // ---------- urgencyKey ----------

    @Test
    fun urgencyKey_sortsMostUrgentFirst() {
        val input = listOf(expireIn(30), null, expireIn(-10), expireIn(3))
        val sorted = input.sortedBy { RenewalLogic.urgencyKey(it, today) }
        assertEquals(listOf(expireIn(-10), expireIn(3), expireIn(30), null), sorted)
    }
}
