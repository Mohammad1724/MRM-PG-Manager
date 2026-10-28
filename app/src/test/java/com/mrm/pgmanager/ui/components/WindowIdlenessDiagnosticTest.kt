package com.mrm.pgmanager.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class WindowIdlenessDiagnosticTest {
    @get:Rule val rule = createComposeRule()

    private fun idleTimeout(seconds: Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies")
            .getMethod("setMasterPolicyTimeout", java.lang.Long.TYPE, TimeUnit::class.java)
            .invoke(null, seconds, TimeUnit.SECONDS)
    }

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { it.getSharedPreferences("window_idleness_diagnostic", Context.MODE_PRIVATE) }
        idleTimeout(12)
    }

    @After fun tearDown() {
        rule.mainClock.autoAdvance = true
        idleTimeout(60)
        SessionStore.resetPrefsForTests()
        MotionPrefs.reduceMotion = false
    }

    private fun startChoice() {
        var open by mutableStateOf(false)
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Text("Open choice", Modifier.clickable { open = true })
                if (open) {
                    PGSingleChoiceDialog(
                        title = "Choose", searchLabel = "Search", choices = listOf(PGChoiceOption("one", "One")),
                        selected = "one", onDismiss = { open = false }, onSelect = { open = false }, tag = "diagnostic_choice"
                    )
                }
            }
        }
        rule.onNodeWithText("Open choice").performClick()
    }

    @Test fun `choice window idles with automatic clock`() {
        startChoice()
        rule.onNodeWithTag("diagnostic_choice").assertIsDisplayed()
    }

    @Test fun `choice window idles with manually advanced clock`() {
        startChoice()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("diagnostic_choice").assertIsDisplayed()
    }
}
