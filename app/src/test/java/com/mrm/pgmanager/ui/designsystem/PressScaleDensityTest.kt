package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], qualifiers = "w360dp-h800dp")
class PressScaleDensityTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    @Test fun `mounted press feedback survives font and device density changes`() {
        var percent by mutableIntStateOf(100)
        var deviceFontScale by mutableFloatStateOf(1f)
        var displayDensity by mutableFloatStateOf(1f)
        var observedFontScale = 0f
        var outerClicks = 0
        var innerClicks = 0

        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(displayDensity, deviceFontScale)) {
                ProvideAppFontScale(percent) {
                    val currentScale = LocalDensity.current.fontScale
                    SideEffect { observedFontScale = currentScale }
                    Column {
                        Box(
                            Modifier.testTag("outer")
                                .clickable { outerClicks++ }
                                .size(48.dp)
                                .pressScale(0.9f)
                        )
                        Box(
                            Modifier.testTag("inner")
                                .pressScale(0.9f)
                                .clickable { innerClicks++ }
                                .size(48.dp)
                        )
                    }
                }
            }
        }

        fun tapBoth(expectedClicks: Int) {
            rule.onNodeWithTag("outer").performTouchInput { click() }
            rule.onNodeWithTag("inner").performTouchInput { click() }
            rule.runOnIdle {
                assertEquals(expectedClicks, outerClicks)
                assertEquals(expectedClicks, innerClicks)
            }
        }

        tapBoth(1)
        listOf(90, 80, 100, 140, 100).forEachIndexed { index, nextPercent ->
            rule.runOnIdle { percent = nextPercent }
            rule.runOnIdle { assertEquals(nextPercent / 100f, observedFontScale, 0.001f) }
            tapBoth(index + 2)
        }

        rule.runOnIdle {
            deviceFontScale = 1.3f
            displayDensity = 1.5f
        }
        rule.runOnIdle { assertEquals(1.3f, observedFontScale, 0.001f) }
        tapBoth(7)
    }
}
