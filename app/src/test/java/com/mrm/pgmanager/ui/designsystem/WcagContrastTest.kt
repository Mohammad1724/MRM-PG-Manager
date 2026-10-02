package com.mrm.pgmanager.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * ریاضیاتِ کنتراستِ فاز ۷.۱ — روی JVM، بدونِ Compose و بدونِ دستگاه.
 *
 * چرا مهم است؟ چون «رنگِ متنیِ اکسنت» در زمانِ اجرا محاسبه می‌شود (برای هر
 * چراغ و رنگِ سفارشیِ کاربر). اگر این تابع اشتباه کند، متن روی سطحِ روشن
 * ناخوانا می‌شود و هیچ تستِ دیگری آن را نمی‌گیرد.
 */
class WcagContrastTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private val white = argb(255, 255, 255)
    private val black = argb(0, 0, 0)

    // ── پایه ────────────────────────────────────────────────────────────

    @Test fun `black on white is the maximum ratio`() {
        assertEquals(21.0, WcagContrast.ratio(black, white), 0.01)
    }

    @Test fun `identical colors are always ratio one`() {
        assertEquals(1.0, WcagContrast.ratio(argb(120, 130, 140), argb(120, 130, 140)), 0.001)
    }

    @Test fun `ratio is symmetric`() {
        val a = argb(0x5B, 0x64, 0x72)
        val b = argb(0xF8, 0xF9, 0xFA)
        assertEquals(WcagContrast.ratio(a, b), WcagContrast.ratio(b, a), 0.0001)
    }

    @Test fun `known token ratios match the palette table`() {
        // همان اعدادی که در §۳ سند اندازه‌گیری شده بود.
        assertEquals(5.98, WcagContrast.ratio(argb(0x5B, 0x64, 0x72), white), 0.05)   // Tertiary
        assertEquals(4.83, WcagContrast.ratio(argb(0x6B, 0x72, 0x80), white), 0.05)   // Muted
        assertEquals(2.54, WcagContrast.ratio(argb(0x9C, 0xA3, 0xAF), white), 0.05)   // MutedLight (غیرفعال)
        assertEquals(5.02, WcagContrast.ratio(argb(0x15, 0x80, 0x3D), white), 0.05)   // SuccessText
        assertEquals(5.02, WcagContrast.ratio(argb(0xB4, 0x53, 0x09), white), 0.05)   // WarningText
    }

    // ── ترکیبِ alpha (مرزهای نیمه‌شفاف) ──────────────────────────────────

    @Test fun `mixing with alpha one returns the foreground`() {
        assertEquals(argb(0x70, 0x71, 0x74), WcagContrast.mix(argb(0x70, 0x71, 0x74), white, 1.0))
    }

    @Test fun `mixing with alpha zero returns the background`() {
        assertEquals(white, WcagContrast.mix(black, white, 0.0))
    }

    @Test fun `legacy white fourteen percent border fails contrast on dark`() {
        // مرزِ «سفیدِ ۱۴٪» روی سطحِ تیره مضحک‌الوصف کم‌کنتراست بود؛ اینجا فقط
        // ثابت می‌کنیم ترکیبِ alpha همان چیزی را می‌دهد که فکر می‌کنیم.
        val darkSurface = argb(0x19, 0x1A, 0x1E)
        val blended = WcagContrast.mix(white, darkSurface, 0.14)
        assertTrue("۱۴٪ سفید نباید AA مرز را رد کند", WcagContrast.ratio(blended, darkSurface) < WcagContrast.AA_NON_TEXT)
    }

    // ── forTextOn: قلبِ کار ─────────────────────────────────────────────

    @Test fun `gold accent becomes readable on white without losing its hue`() {
        val gold = argb(0xFA, 0xCC, 0x15)
        val text = WcagContrast.forTextOn(gold, white)

        assertTrue(
            "طلاییِ متنی باید AA شود: ${WcagContrast.ratio(text, white)}",
            WcagContrast.ratio(text, white) >= WcagContrast.AA_TEXT
        )
        // لمسِ هنری: فام باید در خانوادهٔ گرمِ طلایی بماند (نه خاکستری/آبی).
        val (hue, sat, _) = WcagContrast.toHsl(text)
        assertTrue("فام باید در بازهٔ ۳۰..۷۰ باشد ولی $hue است", hue in 30f..70f)
        assertTrue("اشباعِ رنگ نباید صفر شود", sat > 0.5f)
    }

    @Test fun `already accessible colors are returned untouched`() {
        val ink = argb(0x1A, 0x1F, 0x2E)
        assertEquals(ink, WcagContrast.forTextOn(ink, white))
    }

    @Test fun `dark theme direction is inverted`() {
        // روی سطحِ تیره باید روشن‌تر شود، نه تیره‌تر.
        val emerald = argb(0x20, 0xA3, 0x6B)
        val darkSurface = argb(0x0E, 0x0F, 0x12)
        val text = WcagContrast.forTextOn(emerald, darkSurface)

        assertTrue(WcagContrast.ratio(text, darkSurface) >= WcagContrast.AA_TEXT)
        assertTrue(
            "نسخهٔ تیره باید روشن‌تر از اصل باشد",
            WcagContrast.relativeLuminance(text) >= WcagContrast.relativeLuminance(emerald)
        )
    }

    @Test fun `every lamp accent reaches AA as text on its theme background`() {
        // همان ۶ چراغِ اپ + یک رنگِ سفارشیِ نمونه — همه باید عبور کنند.
        val lamps = mapOf(
            "GOLD" to argb(0xFA, 0xCC, 0x15),
            "MAGENTA" to argb(0xD6, 0x4D, 0x8C),
            "TURQUOISE" to argb(0x16, 0xA9, 0x9A),
            "SKY_BLUE" to argb(0x3B, 0x82, 0xF6),
            "VIOLET" to argb(0x8B, 0x5C, 0xF6),
            "EMERALD" to argb(0x20, 0xA3, 0x6B)
        )
        for ((name, accent) in lamps) {
            val onLight = WcagContrast.forTextOn(accent, argb(0xF8, 0xF9, 0xFA))
            val onDark = WcagContrast.forTextOn(accent, argb(0x0E, 0x0F, 0x12))
            assertTrue(
                "$name روی سطحِ روشن AA نشد (${WcagContrast.ratio(onLight, argb(0xF8, 0xF9, 0xFA))})",
                WcagContrast.ratio(onLight, argb(0xF8, 0xF9, 0xFA)) >= WcagContrast.AA_TEXT
            )
            assertTrue(
                "$name روی سطحِ تیره AA نشد (${WcagContrast.ratio(onDark, argb(0x0E, 0x0F, 0x12))})",
                WcagContrast.ratio(onDark, argb(0x0E, 0x0F, 0x12)) >= WcagContrast.AA_TEXT
            )
        }
    }

    @Test fun `border helper uses the non-text threshold`() {
        // مرزها ۳:۱ می‌خواهند نه ۴٫۵ — همان توکنی که در پالت تصحیح شد.
        val border = argb(0x85, 0x8D, 0x9B)
        val page = argb(0xF8, 0xF9, 0xFA)
        assertTrue(WcagContrast.ratio(border, page) >= WcagContrast.AA_NON_TEXT)
        assertTrue(
            "مرز نباید به‌جای AA مرز، آستانهٔ متن را رد کند (بی‌دلیل تیره می‌شد)",
            WcagContrast.ratio(border, page) < WcagContrast.AA_TEXT
        )
    }

    @Test fun `hsl round trip keeps the color`() {
        val c = argb(0x8B, 0x5C, 0xF6)
        val (h, s, l) = WcagContrast.toHsl(c)
        val back = WcagContrast.fromHsl(h, s, l)
        assertTrue(abs(((back shr 16) and 0xFF) - 0x8B) <= 2)
        assertTrue(abs(((back shr 8) and 0xFF) - 0x5C) <= 2)
        assertTrue(abs((back and 0xFF) - 0xF6) <= 2)
    }
}
