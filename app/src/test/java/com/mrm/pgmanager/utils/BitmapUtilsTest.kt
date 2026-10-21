package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * تستِ تابع خالصِ محاسبه‌ی inSampleSize — رفع S1 (جلوگیری از OOM هنگام انتخاب
 * لوگوی فاکتور با تصویرِ بزرگ).
 */
class BitmapUtilsTest {

    @Test fun `zero or negative inputs fall back to no downsampling`() {
        assertEquals(1, BitmapUtils.calculateInSampleSize(0, 1000, 400, 400))
        assertEquals(1, BitmapUtils.calculateInSampleSize(1000, 0, 400, 400))
        assertEquals(1, BitmapUtils.calculateInSampleSize(1000, 1000, 0, 400))
        assertEquals(1, BitmapUtils.calculateInSampleSize(-1, -1, 400, 400))
    }

    @Test fun `image already smaller than target is not downsampled`() {
        assertEquals(1, BitmapUtils.calculateInSampleSize(200, 300, 400, 400))
        assertEquals(1, BitmapUtils.calculateInSampleSize(400, 400, 400, 400))
    }

    @Test fun `large camera photo gets a power-of-two sample covering the target`() {
        // 4000x3000 با هدف 400: نمونه‌ی 4 → 1000x750 (هنوز هدف را پوشش می‌دهد)،
        // نمونه‌ی 8 → 500x375 که ضلعِ کوتاهش از 400 کمتر می‌شد.
        assertEquals(4, BitmapUtils.calculateInSampleSize(4000, 3000, 400, 400))
    }

    @Test fun `sample is always a power of two`() {
        val sample = BitmapUtils.calculateInSampleSize(8000, 8000, 200, 200)
        assertEquals(0, sample and (sample - 1))
    }

    @Test fun `both dimensions must stay above the target`() {
        // تصویرِ بسیار کشیده: ضلعِ کوتاه تعیین‌کننده است و نباید بیش از حد کوچک شود.
        assertEquals(2, BitmapUtils.calculateInSampleSize(2000, 900, 400, 400))
    }
}
