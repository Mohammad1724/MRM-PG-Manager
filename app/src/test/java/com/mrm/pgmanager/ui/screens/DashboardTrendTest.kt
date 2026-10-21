package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.model.TrafficPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تستِ تابع خالصِ روندِ ترافیک — رفع D1.
 *
 * قدیم: مقایسهٔ سطل اول و آخر، با سطل آخرِ ناقص و تقسیمِ اجباری بر ۱ وقتی اولی صفر
 * بود (درصد نجومی). حالا میانگین دو نیمه (پس از حذف سطل ناقص آخر) ملاک است.
 */
class DashboardTrendTest {

    private fun pts(vararg values: Long) = values.mapIndexed { i, v ->
        TrafficPoint("2026-10-${(10 + i).toString().padStart(2, '0')}T00:00:00Z", v)
    }

    @Test fun `fewer than four points yields no trend`() {
        assertNull(trafficTrendPercent(emptyList()))
        assertNull(trafficTrendPercent(pts(1, 2)))
        assertNull(trafficTrendPercent(pts(1, 2, 3)))
    }

    @Test fun `flat traffic is zero percent`() {
        // سطل آخر کنار گذاشته می‌شود؛ سه سطل باقی‌مانده میانگین برابر دارند.
        assertEquals(0, trafficTrendPercent(pts(100, 100, 100, 50)))
    }

    @Test fun `rising second half yields positive percent`() {
        // سطل‌ها پس از حذف آخر: [100, 200, 200] → نیمه اول 100، نیمه دوم 200 → +100٪
        assertEquals(100, trafficTrendPercent(pts(100, 200, 200, 999)))
    }

    @Test fun `falling second half yields negative percent`() {
        // سطل‌ها: [200, 100, 100] → نیمه اول 200، نیمه دوم 100 → -50٪
        assertEquals(-50, trafficTrendPercent(pts(200, 100, 100, 0)))
    }

    @Test fun `zero baseline yields no trend instead of an astronomical percent`() {
        // رفتار قدیم با coerceAtLeast(1) اینجا عددی نجومی می‌ساخت.
        assertNull(trafficTrendPercent(pts(0, 0, 100, 200)))
    }

    @Test fun `last in-progress bucket is ignored in the comparison`() {
        // بدون حذف سطل آخر، سطل ناقصِ 1 روند را منفی کاذب می‌کرد.
        // سطل‌های موثر: [100, 200, 200] → +100٪ با وجود سطل آخرِ کوچک.
        assertEquals(100, trafficTrendPercent(pts(100, 200, 200, 1)))
    }

    @Test fun `five points split into two equal halves`() {
        // حذف آخر → 4 سطل، mid=2: [100,100] در برابر [200,200] → +100٪
        assertEquals(100, trafficTrendPercent(pts(100, 100, 200, 200, 7)))
    }
}
