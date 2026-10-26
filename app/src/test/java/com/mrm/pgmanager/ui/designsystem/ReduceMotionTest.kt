package com.mrm.pgmanager.ui.designsystem

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قراردادِ «کاهش انیمیشن» — و دلیلِ اینکه تست‌های Compose می‌توانند به idle برسند.
 *
 * `MotionPrefs.reduceMotion` پرچمِ سراسریِ اپ است (از تنظیماتِ اپ و تنظیمِ سیستم
 * روشن می‌شود). دو محل از آن سرِ باز درمی‌آمدند: پلکانِ ورودِ ردیف‌ها
 * ([ListIntro]) و چرخشِ بی‌پایانِ دکمهٔ رفرش. اولی حالا کلاً ساخته نمی‌شود؛
 * این تست همان را قفل می‌کند، چون اگر روزی برگردد، تست‌هایِ صفحه‌ای (مثلِ
 * `ApiKeysScreenTest`) روی `waitForIdle` می‌خوابند و کلِ اجرا سرخ می‌شود.
 */
class ReduceMotionTest {

    @After fun tearDown() {
        MotionPrefs.reduceMotion = false
    }

    @Test fun `list intro staggers when motion is allowed`() {
        MotionPrefs.reduceMotion = false
        val intro = ListIntro(System.nanoTime())
        assertTrue("با حرکتِ عادی ردیف‌های اول باید انیمیت شوند", intro.shouldAnimate(0))
    }

    @Test fun `list intro is fully skipped under reduce motion`() {
        MotionPrefs.reduceMotion = true
        val intro = ListIntro(System.nanoTime())
        // فقط «مدتِ صفر» کافی نیست: خودِ delay و Animatable هم نباید ساخته شوند،
        // وگرنه یک کارِ معلق روی Composition می‌ماند.
        (0 until ListIntro.MAX_ITEMS + 3).forEach { index ->
            assertFalse("ایندهکسِ $index هم نباید انیمیت شود", intro.shouldAnimate(index))
        }
    }

    @Test fun `effective duration collapses to zero under reduce motion`() {
        MotionPrefs.reduceMotion = false
        assertTrue(MotionPrefs.ms(DsDuration.Normal) > 0)
        MotionPrefs.reduceMotion = true
        assertTrue(MotionPrefs.ms(DsDuration.Normal) == 0)
    }
}
