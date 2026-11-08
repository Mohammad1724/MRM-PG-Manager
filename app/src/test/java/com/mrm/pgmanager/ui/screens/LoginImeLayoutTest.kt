package com.mrm.pgmanager.ui.screens

import android.content.ComponentName
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.MainActivity
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.theme.ThemeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class LoginImeLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `activity requests resize rather than default keyboard panning`() {
        val context = RuntimeEnvironment.getApplication()
        val activity = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            activity.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )
    }

    @Test fun `keyboard inset is applied once and the bottom of the form stays reachable`() {
        var keyboardHeight by mutableStateOf(0.dp)
        rule.setContent {
            Box(Modifier.size(width = 320.dp, height = 600.dp).testTag("login_test_host")) {
                LoginFormViewport(keyboardInsets = WindowInsets(bottom = keyboardHeight)) {
                    Text("Top of form")
                    Box(Modifier.fillMaxWidth().height(900.dp))
                    Text("End of form", modifier = Modifier.testTag("login_test_last"))
                }
            }
        }
        assertViewportGap(0.dp)
        rule.runOnIdle { keyboardHeight = 260.dp }
        assertViewportGap(260.dp)
        rule.onNodeWithTag("login_test_last").performScrollTo().assertIsDisplayed()
        assertViewportGap(260.dp)
        rule.runOnIdle { keyboardHeight = 0.dp }
        assertViewportGap(0.dp)
    }

    @Test fun `already consumed bottom insets are not added a second time`() {
        rule.setContent {
            Box(Modifier.size(width = 320.dp, height = 600.dp).testTag("login_test_host")) {
                Box(
                    Modifier.fillMaxSize()
                        .padding(bottom = 60.dp)
                        .consumeWindowInsets(PaddingValues(bottom = 60.dp))
                ) {
                    LoginFormViewport(keyboardInsets = WindowInsets(bottom = 240.dp)) {
                        Text("Form")
                    }
                }
            }
        }
        assertViewportGap(240.dp)
    }

    @Test fun `both login modes keep their input fields reachable in a short window`() {
        rule.setContent {
            Box(Modifier.height(360.dp)) {
                LoginScreen(onLoggedIn = {}, themeState = ThemeState(), sessionExpired = true)
            }
        }
        rule.onNodeWithTag("login_password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("login_username").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("login_url").performScrollTo().assertIsDisplayed()
        val apiMode = RuntimeEnvironment.getApplication().getString(R.string.login_mode_api_key)
        rule.onNodeWithText(apiMode).performScrollTo().performClick()
        rule.onNodeWithTag("login_api_key").performScrollTo().assertIsDisplayed()
    }

    private fun assertViewportGap(expected: Dp) {
        val host = rule.onNodeWithTag("login_test_host").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("login_form_viewport").fetchSemanticsNode().boundsInRoot
        val expectedPixels = host.height * expected.value / 600f
        assertEquals(expectedPixels, host.bottom - viewport.bottom, 1.5f)
    }
}
