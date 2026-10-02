package com.mrm.pgmanager.ui.designsystem

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ریاضیاتِ کنتراستِ WCAG 2.1 — **بدونِ وابستگی به Compose**.
 *
 * چرا اینجا و به‌شکلِ توابعِ خالصِ `Int`؟ چون فاز ۷ باید در سه جا قابلِ اجرا باشد:
 *
 *  ۱. **تمِ اپ** برای محاسبهٔ «رنگِ متنیِ اکسنت» (هر چراغ/رنگِ سفارشیِ کاربر).
 *  ۲. **تستِ JVM** بدونِ شبیه‌ساز و بدونِ کلاس‌های Compose.
 *  ۳. **اسکریپتِ `tools/check-contrast.py`** که همان اعداد را از توکن‌های پالت
 *     می‌خواند و در CI قفل می‌کند (نسبت‌ها باید بین دو زبان یکی بماند).
 *
 * همهٔ ورودی‌ها `Int` به‌شکلِ ARGB (مثل `0xFF5B6472.toInt()`).
 */
object WcagContrast {

    /** آستانهٔ AA برای متنِ معمولی. متنِ بزرگ (≥۱۸pt/۱۴pt bold) ۳.۰ می‌گیرد. */
    const val AA_TEXT = 4.5

    /** آستانهٔ AA برای «اجزای غیرمتنی» (مرزِ کنترل، آیکونِ معنادار). */
    const val AA_NON_TEXT = 3.0

    /** کانالِ sRGB (0..255) → مقدارِ خطی‌شدهٔ WCAG. */
    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** روشناییِ نسبیِ رنگ — پایهٔ فرمولِ نسبتِ کنتراست. */
    fun relativeLuminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /** نسبتِ کنتراست (۱..۲۱). همیشه ≥۱؛ ترتیبِ ورودی‌ها مهم نیست. */
    fun ratio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = max(la, lb)
        val lo = min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * ترکیبِ رنگِ نیمه‌شفاف روی پس‌زمینه — همان کاری که Compose هنگامِ رنگ‌آمیزی
     * با alpha انجام می‌دهد. برای چک‌کردنِ مرزهای «سفیدِ ۱۴٪» لازم است.
     */
    fun mix(fg: Int, bg: Int, alpha: Double): Int {
        val a = alpha.coerceIn(0.0, 1.0)
        fun ch(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return (f * a + b * (1 - a)).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * رنگِ **متنِ** خوانا از یک فامِ مشخص روی پس‌زمینهٔ [bg].
     *
     * الگوریتم: فام و اشباع حفظ می‌شود و فقط روشنایی در HSL گام‌به‌گام کم (روی
     * پس‌زمینهٔ روشن) یا زیاد (روی پس‌زمینهٔ تیره) می‌شود تا آستانه رد شود.
     * نتیجه: هویتِ رنگیِ اکسنت می‌ماند ولی متن AA می‌شود — به‌جای هاردکدِ یک
     * مقدار برای «طلایی» که با رنگِ دلخواهِ کاربر بی‌معنا می‌شد.
     */
    fun forTextOn(fg: Int, bg: Int, minRatio: Double = AA_TEXT): Int {
        if (ratio(fg, bg) >= minRatio) return fg
        val darken = relativeLuminance(bg) > 0.5
        val (h, s, l0) = toHsl(fg)
        // گامِ ۰٫۰۴ کافی است و بیش از ۳۰ دور حلقه نمی‌زند؛ در بدترین حالت به
        // سیاه/سفید می‌رسد که همیشه آستانه را رد می‌کند.
        var l = l0
        repeat(31) {
            l = if (darken) l - 0.04 else l + 0.04
            if (l <= 0.04 || l >= 0.96) return@repeat
            val candidate = fromHsl(h, s, l, alphaOf(fg))
            if (ratio(candidate, bg) >= minRatio) return candidate
        }
        return if (darken) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** مثلِ [forTextOn] ولی با آستانهٔ «اجزای غیرمتنی» (۳:۱) برای مرزِ کنترل. */
    fun forBorderOn(fg: Int, bg: Int, minRatio: Double = AA_NON_TEXT): Int = forTextOn(fg, bg, minRatio)

    fun alphaOf(argb: Int): Double = ((argb ushr 24) and 0xFF) / 255.0

    // ── HSL (همان تعریفی که Theme.kt برای گرادیان‌ها دارد) ──────────────

    /** RGB → HSL؛ h درجه (۰..۳۶۰)، s و l در ۰..۱. */
    fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d < 1e-4f) return Triple(0f, 0f, l)
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = 60f * when (mx) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return Triple(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float, alpha: Double = 1.0): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val a = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        return (a shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
