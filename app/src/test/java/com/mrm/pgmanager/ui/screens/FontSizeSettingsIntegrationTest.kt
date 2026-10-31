package com.mrm.pgmanager.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.UiPrefs
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import com.mrm.pgmanager.ui.designsystem.ProvideAppFontScale
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.ThemeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class FontSizeSettingsIntegrationTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var prefs: SharedPreferences
    private lateinit var store: SessionStore

    private fun str(id: Int, vararg args: Any) =
        RuntimeEnvironment.getApplication().getString(id, *args)

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        val context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("font-size-integration-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        store = SessionStore(context)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        SessionStore.resetPrefsForTests()
        MotionPrefs.reduceMotion = false
    }

    @Test fun `settings screen remains interactive as text size is changed and saved`() {
        exerciseTextSizeChange()
    }

    @Test
    @Config(qualifiers = "fa-rIR-w360dp-h800dp")
    fun `text size can be changed in Persian settings`() {
        exerciseTextSizeChange()
    }

    @Test fun `legacy 80 percent appears as the new app default`() {
        exerciseTextSizeChange(legacyEightyPercent = true)
    }

    private fun exerciseTextSizeChange(legacyEightyPercent: Boolean = false) {
        val initial = UiPrefs(hapticsEnabled = false, reduceMotion = true, secureScreen = true)
        if (legacyEightyPercent) {
            prefs.edit().putInt("ui_font_scale_percent", 80)
                .putBoolean("ui_haptics", false)
                .putBoolean("ui_reduce_motion", true)
                .putBoolean("ui_secure_screen", true)
                .commit()
        } else {
            store.saveUiPrefs(initial)
        }
        var uiPrefs by mutableStateOf(store.readUiPrefs())
        var backClicks = 0
        var deviceFontScale = 0f
        var observedFontScale = 0f
        val theme = ThemeState()
        rule.setContent {
            val deviceScale = LocalDensity.current.fontScale
            ProvideAppFontScale(uiPrefs.fontScalePercent) {
                val appScale = LocalDensity.current.fontScale
                SideEffect {
                    deviceFontScale = deviceScale
                    observedFontScale = appScale
                }
                LiquidGlassTheme(themeState = theme) {
                    SettingsScreen(
                        themeState = theme,
                        onThemeChange = {},
                        onBack = { backClicks++ },
                        uiPrefs = uiPrefs,
                        onUiPrefsChange = {
                            uiPrefs = it
                            store.saveUiPrefs(it)
                        }
                    )
                }
            }
        }

        val label = str(R.string.set_font_size_value)
        val smaller = str(R.string.a11y_decrease_value, label)
        val larger = str(R.string.a11y_increase_value, label)
        fun tap(description: String) {
            rule.onNodeWithContentDescription(description).performScrollTo().performTouchInput { click() }
        }
        fun assertSaved(percent: Int) = rule.runOnIdle {
            val expected = initial.copy(fontScalePercent = percent)
            assertEquals(expected, uiPrefs)
            assertEquals(expected, store.readUiPrefs())
            assertEquals(deviceFontScale * 0.8f * percent / 100f, observedFontScale, 0.001f)
        }

        assertSaved(100)
        tap(smaller)
        assertSaved(90)
        tap(smaller)
        assertSaved(80)
        rule.onNodeWithContentDescription(smaller).assertIsNotEnabled()
        tap(larger)
        assertSaved(90)
        rule.onNodeWithText(str(R.string.set_font_size_reset)).performScrollTo().performTouchInput { click() }
        assertSaved(100)
        tap(larger)
        assertSaved(110)
        tap(smaller)
        assertSaved(100)

        rule.onNodeWithContentDescription(str(R.string.cd_back)).performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, backClicks) }
    }
}
