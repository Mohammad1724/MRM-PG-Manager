package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های منطقِ متنیِ «کارتِ تصویریِ اشتراک».
 * اندازه‌گیریِ عرض با یک تابعِ ساختگی (هر نویسه = ۱۰ واحد) شبیه‌سازی می‌شود
 * تا نیازی به `Paint`ِ اندروید نباشد.
 */
class CardTextTest {

    /** اندازه‌گیریِ ساختگی: هر نویسه ۱۰ واحد عرض دارد. */
    private val measure: (String) -> Float = { it.length * 10f }

    // ── ارقامِ فارسی ──────────────────────────────────────────

    @Test fun `converts latin digits to persian`() {
        assertEquals("۱۴۰۴/۰۶/۲۱", CardText.toPersianDigits("1404/06/21"))
    }

    @Test fun `leaves non digits untouched`() {
        assertEquals("۱۲ روز", CardText.toPersianDigits("12 روز"))
        assertEquals("GB", CardText.toPersianDigits("GB"))
    }

    @Test fun `handles empty string`() {
        assertEquals("", CardText.toPersianDigits(""))
    }

    // ── کوتاه‌سازی ────────────────────────────────────────────

    @Test fun `short text is returned unchanged`() {
        assertEquals("abc", CardText.truncateToWidth("abc", 100f, measure))
    }

    @Test fun `text exactly at the limit is not truncated`() {
        // ۱۰ نویسه × ۱۰ = ۱۰۰ که دقیقاً برابرِ سقف است
        val text = "0123456789"
        assertEquals(text, CardText.truncateToWidth(text, 100f, measure))
    }

    @Test fun `long text is truncated and fits within the limit`() {
        val text = "https://example.com/sub/verylongtokenhere/moreandmore"
        val out = CardText.truncateToWidth(text, 100f, measure)

        assertTrue("must end with ellipsis", out.endsWith("…"))
        assertTrue("must actually fit", measure(out) <= 100f)
        assertTrue("must be shorter than input", out.length < text.length)
    }

    @Test fun `truncation keeps the longest prefix that fits`() {
        // سقف ۱۰۰ ⇒ حداکثر ۱۰ نویسه، که یکی‌اش «…» است ⇒ ۹ نویسه از متن
        val out = CardText.truncateToWidth("abcdefghijklmnop", 100f, measure)
        assertEquals("abcdefghi…", out)
        assertEquals(100f, measure(out), 0.01f)
    }

    @Test fun `returns ellipsis alone when nothing fits`() {
        assertEquals("…", CardText.truncateToWidth("abcdef", 5f, measure))
    }

    @Test fun `empty input stays empty`() {
        assertEquals("", CardText.truncateToWidth("", 100f, measure))
    }

    @Test fun `does not loop forever on zero width`() {
        // اگر منطق حلقهٔ بی‌پایان داشته باشد این تست هنگ می‌کند
        val out = CardText.truncateToWidth("abcdef", 0f, measure)
        assertEquals("…", out)
    }
}
