package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.AppFontScale
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import com.mrm.pgmanager.ui.designsystem.ProvideAppFontScale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class FontSizeSettingsTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        RuntimeEnvironment.getApplication().getString(id, *args)

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    @Test fun `controls change the size and can restore the app default`() {
        var percent by mutableIntStateOf(AppFontScale.DEFAULT_PERCENT)
        rule.setContent { FontSizeSettings(percent) { percent = it } }
        val label = str(R.string.set_font_size_value)
        val smaller = str(R.string.a11y_decrease_value, label)
        val larger = str(R.string.a11y_increase_value, label)
        val reset = str(R.string.set_font_size_reset)
        val unit = RuntimeEnvironment.getApplication().getString(R.string.set_unit_percent).replace("%%", "%")

        rule.onNodeWithText("100 $unit").assertExists()
        rule.onNodeWithText(reset).assertIsNotEnabled()
        rule.onNodeWithContentDescription(smaller)
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(90, percent)
        rule.onNodeWithContentDescription(larger).performClick().performClick()
        assertEquals(110, percent)
        rule.onNodeWithText(reset).performClick()
        assertEquals(AppFontScale.DEFAULT_PERCENT, percent)
        rule.onNodeWithText(reset).assertIsNotEnabled()
    }

    @Test fun `controls stop at both size limits`() {
        var percent by mutableIntStateOf(AppFontScale.MIN_PERCENT)
        rule.setContent { FontSizeSettings(percent) { percent = it } }
        val label = str(R.string.set_font_size_value)
        rule.onNodeWithContentDescription(str(R.string.a11y_decrease_value, label)).assertIsNotEnabled()
        rule.runOnIdle { percent = AppFontScale.MAX_PERCENT }
        rule.onNodeWithContentDescription(str(R.string.a11y_increase_value, label)).assertIsNotEnabled()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `text responds immediately without changing display density`() {
        var percent by mutableIntStateOf(AppFontScale.DEFAULT_PERCENT)
        val deviceDensity = Density(density = 2f, fontScale = 1.25f)
        var observed: Density? = null
        var layoutScale = Float.NaN
        var layoutTextPx = Float.NaN
        var layoutHeight = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides deviceDensity) {
                ProvideAppFontScale(percent) {
                    val current = LocalDensity.current
                    SideEffect { observed = current }
                    // The root fills the viewport; Box lets the text measure at its natural height.
                    Box {
                        Text(
                            "Sample text",
                            fontSize = 16.sp,
                            modifier = Modifier.testTag("sample"),
                            onTextLayout = { result ->
                                layoutScale = result.layoutInput.density.fontScale
                                layoutTextPx = with(result.layoutInput.density) { 16.sp.toPx() }
                                layoutHeight = result.size.height
                            }
                        )
                    }
                }
            }
        }
        fun metrics() = "providerPx=${with(observed!!) { 16.sp.toPx() }}, " +
            "layoutScale=$layoutScale, layoutPx=$layoutTextPx, layoutHeight=$layoutHeight"

        val normalHeight = rule.onNodeWithTag("sample").fetchSemanticsNode().boundsInRoot.height
        val normalMetrics = rule.runOnIdle {
            assertEquals(2f, observed!!.density, 0.001f)
            assertEquals(1f, observed!!.fontScale, 0.001f)
            assertEquals(1f, layoutScale, 0.001f)
            metrics()
        }

        rule.runOnIdle { percent = 80 }
        val smallHeight = rule.onNodeWithTag("sample").fetchSemanticsNode().boundsInRoot.height
        val smallMetrics = rule.runOnIdle {
            assertEquals(2f, observed!!.density, 0.001f)
            assertEquals(0.8f, observed!!.fontScale, 0.001f)
            assertEquals(0.8f, layoutScale, 0.001f)
            metrics()
        }

        rule.runOnIdle { percent = 180 }
        val largeHeight = rule.onNodeWithTag("sample").fetchSemanticsNode().boundsInRoot.height
        val largeMetrics = rule.runOnIdle {
            assertEquals(2f, observed!!.density, 0.001f)
            assertEquals(1.8f, observed!!.fontScale, 0.001f)
            assertEquals(1.8f, layoutScale, 0.001f)
            metrics()
        }
        val details = "small=$smallHeight [$smallMetrics], normal=$normalHeight [$normalMetrics], large=$largeHeight [$largeMetrics]"
        assertTrue("Expected smaller text to use less height: $details", smallHeight < normalHeight)
        assertTrue("Expected larger text to use more height: $details", largeHeight > normalHeight)

        rule.runOnIdle { percent = AppFontScale.SYSTEM_PERCENT }
        rule.runOnIdle { assertSame(deviceDensity, observed) }
        rule.runOnIdle { percent = AppFontScale.DEFAULT_PERCENT }
        rule.runOnIdle { assertEquals(1f, observed!!.fontScale, 0.001f) }
    }
}
