package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontScaleTest {
    @Test fun `the default follows the device font size`() {
        assertEquals(AppFontScale.DEFAULT_PERCENT, UiPrefs().fontScalePercent)
        assertEquals(1.3f, AppFontScale.applyTo(1.3f, AppFontScale.DEFAULT_PERCENT), 0.0001f)
    }

    @Test fun `smaller and larger choices scale relative to the device`() {
        assertEquals(0.8f, AppFontScale.applyTo(1f, 80), 0.0001f)
        assertEquals(1.4f, AppFontScale.applyTo(1f, 140), 0.0001f)
        assertEquals(1.56f, AppFontScale.applyTo(1.3f, 120), 0.0001f)
    }

    @Test fun `out of range saved values are bounded`() {
        assertEquals(80, AppFontScale.normalize(Int.MIN_VALUE))
        assertEquals(140, AppFontScale.normalize(Int.MAX_VALUE))
        assertEquals(0.96f, AppFontScale.applyTo(1.2f, Int.MIN_VALUE), 0.0001f)
        assertEquals(1.68f, AppFontScale.applyTo(1.2f, Int.MAX_VALUE), 0.0001f)
    }
}
