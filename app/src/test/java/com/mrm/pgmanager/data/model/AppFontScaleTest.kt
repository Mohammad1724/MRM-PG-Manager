package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontScaleTest {
    @Test fun `the new default matches the previous 80 percent at different device scales`() {
        assertEquals(AppFontScale.DEFAULT_PERCENT, UiPrefs().fontScalePercent)
        listOf(1f, 1.3f, 2f).forEach { deviceScale ->
            val previousEightyPercent = deviceScale * (80 / 100f)
            assertEquals(previousEightyPercent, AppFontScale.applyTo(deviceScale, 100), 0.0001f)
        }
    }

    @Test fun `smaller and larger choices scale relative to the app default`() {
        assertEquals(0.64f, AppFontScale.applyTo(1f, 80), 0.0001f)
        assertEquals(0.8f, AppFontScale.applyTo(1f, 100), 0.0001f)
        assertEquals(1f, AppFontScale.applyTo(1f, 125), 0.0001f)
        assertEquals(1.44f, AppFontScale.applyTo(1f, 180), 0.0001f)
        assertEquals(1.248f, AppFontScale.applyTo(1.3f, 120), 0.0001f)
    }

    @Test fun `out of range saved values are bounded`() {
        assertEquals(80, AppFontScale.normalize(Int.MIN_VALUE))
        assertEquals(180, AppFontScale.normalize(Int.MAX_VALUE))
        assertEquals(0.768f, AppFontScale.applyTo(1.2f, Int.MIN_VALUE), 0.0001f)
        assertEquals(1.728f, AppFontScale.applyTo(1.2f, Int.MAX_VALUE), 0.0001f)
    }

    @Test fun `legacy preferences preserve the previous effective size as closely as integer percentages allow`() {
        val conversions = mapOf(80 to 100, 90 to 113, 100 to 125, 110 to 138, 120 to 150, 130 to 163, 140 to 175)
        conversions.forEach { (legacy, current) ->
            assertEquals(current, AppFontScale.fromLegacyPercent(legacy))
            assertEquals(legacy / 100f, AppFontScale.applyTo(1f, current), 0.0041f)
            assertEquals(legacy, AppFontScale.toLegacyPercent(current))
        }
        assertEquals(100, AppFontScale.fromLegacyPercent(Int.MIN_VALUE))
        assertEquals(175, AppFontScale.fromLegacyPercent(Int.MAX_VALUE))
    }

    @Test fun `legacy export represents the new baseline for older readers`() {
        assertEquals(64, AppFontScale.toLegacyPercent(80))
        assertEquals(80, AppFontScale.toLegacyPercent(100))
        assertEquals(100, AppFontScale.toLegacyPercent(125))
        assertEquals(144, AppFontScale.toLegacyPercent(180))
    }
}
