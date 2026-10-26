package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نسخه‌شناسیِ پنل — قراردادِ دروازهٔ نسخه.
 *
 * اپ از این نسخه فقط PasarGuard **5.5.1+** را پشتیبانی می‌کند؛ این تست‌ها همان
 * چیزهایی را قفل می‌کنند که در عمل باعث اشتباه می‌شدند: پیشوندِ `v`، نسخه‌های
 * ناقص (`5.5`)، پسوندهای prerelease (`5.5.1-rc.1`) و مقدارِ خالی/نامعلوم.
 */
class PanelVersionTest {

    @Test fun `parses plain and v prefixed versions`() {
        assertEquals(listOf(5, 5, 1), PanelVersion.parts("5.5.1"))
        assertEquals(listOf(5, 5, 1), PanelVersion.parts("v5.5.1"))
        assertEquals(listOf(5, 5, 1), PanelVersion.parts("  V5.5.1  "))
    }

    @Test fun `missing parts count as zero`() {
        assertEquals(listOf(5, 5, 0), PanelVersion.parts("5.5"))
        assertEquals(listOf(5, 0, 0), PanelVersion.parts("5"))
    }

    @Test fun `prerelease and build suffixes do not change the number`() {
        // پنل نسخه را از `app/version.py` می‌دهد، ولی proxy/فورک‌ها گاهی پسوند دارند.
        assertEquals(listOf(5, 5, 1), PanelVersion.parts("5.5.1-rc.1"))
        assertEquals(listOf(5, 5, 1), PanelVersion.parts("5.5.1+build.9"))
    }

    @Test fun `unparseable versions are unknown not zero`() {
        assertNull(PanelVersion.parts(null))
        assertNull(PanelVersion.parts(""))
        assertNull(PanelVersion.parts("dev"))
        // مهم: «نامعلوم» نباید «قدیمی» فهمیده شود.
        assertNull(PanelVersion.isAtLeast("dev"))
        assertTrue(PanelVersion.supports("dev"))
        assertTrue(PanelVersion.supports(null))
    }

    @Test fun `compares numerically not lexicographically`() {
        assertTrue(PanelVersion.compare("5.10.0", "5.9.9")!! > 0)
        assertTrue(PanelVersion.compare("5.5.0", "5.5.1")!! < 0)
        assertEquals(0, PanelVersion.compare("v5.5.1", "5.5.1"))
    }

    @Test fun `minimum supported is 5_5_1`() {
        assertEquals("5.5.1", PanelVersion.MIN_SUPPORTED)
        assertTrue(PanelVersion.isAtLeast("5.5.1")!!)
        assertTrue(PanelVersion.isAtLeast("5.6.0")!!)
        assertFalse(PanelVersion.isAtLeast("5.5.0")!!)
        assertFalse(PanelVersion.isAtLeast("5.4.1")!!)
        assertFalse(PanelVersion.supports("5.4.1"))
    }

    @Test fun `label normalizes for display`() {
        assertEquals("5.5.1", PanelVersion.label("v5.5.1-beta"))
        assertEquals("5.5.0", PanelVersion.label("5.5"))
        assertEquals("", PanelVersion.label(null))
    }
}
