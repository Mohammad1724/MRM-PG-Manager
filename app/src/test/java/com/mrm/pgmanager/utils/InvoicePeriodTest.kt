package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * تستِ خالصِ [computeInvoicePeriod] — رفع باگ فاکتور: پس از تمدید، تاریخ شروع
 * باید روز تمدید باشد نه روز ساخت حساب (created_at در پنل با تمدید عوض نمی‌شود).
 */
class InvoicePeriodTest {

    private val today = LocalDate.of(2026, 10, 21)

    @Test fun `renewed user uses the renewal date, not the original creation date`() {
        // ساخت حساب ۳ ماه پیش، امروز تمدید ۳۰ روزه: شروع فاکتور باید امروز و مدت ۳۰ روز باشد.
        val period = computeInvoicePeriod(
            createdAtIso = "2026-07-23",
            expireIso = "2026-11-20",
            renewalIso = "2026-10-21",
            today = today
        )
        assertEquals(LocalDate.of(2026, 10, 21), period.start)
        assertEquals(LocalDate.of(2026, 11, 20), period.end)
        assertEquals(30L, period.durationDays)
    }

    @Test fun `fresh user without renewal falls back to created_at`() {
        val period = computeInvoicePeriod(
            createdAtIso = "2026-10-01",
            expireIso = "2026-10-31",
            renewalIso = null,
            today = today
        )
        assertEquals(LocalDate.of(2026, 10, 1), period.start)
        assertEquals(30L, period.durationDays)
    }

    @Test fun `missing both dates falls back to today`() {
        val period = computeInvoicePeriod(
            createdAtIso = null,
            expireIso = "2026-11-20",
            renewalIso = null,
            today = today
        )
        assertEquals(today, period.start)
        assertEquals(30L, period.durationDays)
    }

    @Test fun `unlimited expiry reports zero duration and null end`() {
        val period = computeInvoicePeriod(
            createdAtIso = "2026-10-01",
            expireIso = null,
            renewalIso = "2026-10-21",
            today = today
        )
        assertNull(period.end)
        assertEquals(0L, period.durationDays)
        assertEquals(LocalDate.of(2026, 10, 21), period.start)
    }

    @Test fun `blank or zero expiry strings are treated as unlimited`() {
        val period = computeInvoicePeriod(
            createdAtIso = "2026-10-01",
            expireIso = "0",
            renewalIso = null,
            today = today
        )
        assertNull(period.end)
        assertEquals(0L, period.durationDays)
    }

    @Test fun `duration never goes negative when dates overlap`() {
        val period = computeInvoicePeriod(
            createdAtIso = "2026-01-01",
            expireIso = "2026-01-10",
            renewalIso = "2026-09-01",
            today = today
        )
        assertEquals(0L, period.durationDays)
    }

    @Test fun `full timestamp strings are reduced to their local calendar day`() {
        val period = computeInvoicePeriod(
            createdAtIso = "2026-07-23T10:00:00Z",
            expireIso = "2026-11-20T23:59:59Z",
            renewalIso = "2026-10-21T08:30:00Z",
            today = today
        )
        assertEquals(LocalDate.of(2026, 10, 21), period.start)
    }
}
