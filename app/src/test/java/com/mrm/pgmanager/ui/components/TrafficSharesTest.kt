package com.mrm.pgmanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * تستِ تابع خالصِ [trafficShares] — رفع N4 (حلقه‌ی ترافیکِ صفر قبلاً «۱۰۰٪ آپلود»
 * می‌نمایاند چون 1f - downShare با downShare صفر می‌شد ۱).
 *
 * تستِ JVMِ خالص بدون چارچوب UI؛ تابع top-level هم‌پکیج است و import نمی‌خواهد.
 */
class TrafficSharesTest {

    private val delta = 1e-6f

    @Test fun `no traffic means both shares are zero, not 100 percent upload`() {
        val (down, up) = trafficShares(0L, 0L)
        assertEquals(0f, down, delta)
        assertEquals(0f, up, delta)
    }

    @Test fun `download-only traffic is fully the down share`() {
        val (down, up) = trafficShares(9_000L, 0L)
        assertEquals(1f, down, delta)
        assertEquals(0f, up, delta)
    }

    @Test fun `upload-only traffic is fully the up share`() {
        val (down, up) = trafficShares(0L, 9_000L)
        assertEquals(0f, down, delta)
        assertEquals(1f, up, delta)
    }

    @Test fun `mixed traffic is split proportionally`() {
        val (down, up) = trafficShares(3_000L, 1_000L)
        assertEquals(0.75f, down, delta)
        assertEquals(0.25f, up, delta)
    }

    @Test fun `shares always sum to one when traffic exists`() {
        val (down, up) = trafficShares(123_456_789L, 987_654_321L)
        assertEquals(1f, down + up, delta)
    }
}
