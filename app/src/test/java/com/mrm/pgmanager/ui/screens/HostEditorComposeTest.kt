package com.mrm.pgmanager.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.HostXrayTemplate
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HostEditorComposeTest {
    @get:Rule val rule = createComposeRule()

    private val host = PanelHost(
        id = 3, remark = "Example", addresses = listOf("example.com"),
        raw = """{"id":3,"remark":"Example","address":["example.com"],"allowinsecure":null}"""
    )
    private var current: HostDraft? = null
    private var saved: HostDraft? = null
    private var dismissed = false

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }
    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun render(
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        dark: Boolean = false,
        inbounds: HostOptions<String> = HostOptions.Ready(listOf("VLESS-TLS"))
    ) {
        rule.setContent {
            val density = LocalDensity.current
            var draft by remember { mutableStateOf(HostDraft.from(host)) }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
                LocalThemeState provides ThemeState(isDark = dark)
            ) {
                HostEditorContent(
                    initial = host,
                    draft = draft,
                    saving = false,
                    error = null,
                    onDraftChange = { draft = it; current = it },
                    inbounds = inbounds,
                    templates = HostOptions.Ready<HostXrayTemplate>(emptyList()),
                    onRetryInbounds = {},
                    onRetryTemplates = {},
                    onDismiss = { dismissed = true },
                    onSave = { saved = draft }
                )
            }
        }
    }

    @Test fun `status filter saves independently of host enabled state`() {
        render()
        rule.onNodeWithTag("host_editor_status_limited").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle {
            assertEquals(listOf("limited"), saved?.status)
            assertEquals(false, saved?.disabled)
        }
    }

    @Test fun `inbound can be entered manually when its options cannot be loaded`() {
        render(inbounds = HostOptions.Failed("Forbidden"))
        rule.onNodeWithTag("host_editor_inbound_manual").performScrollTo().performTextInput("manual-tag")
        rule.runOnIdle { assertEquals("manual-tag", current?.inboundTag) }
    }

    @Test fun `certificate verification offers an explicit override of inheritance`() {
        render()
        rule.onNodeWithTag("host_editor_section_security").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_tls_verification").performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.host_editor_tls_verify)).performClick()
        rule.runOnIdle {
            assertEquals("false", current?.field("allowinsecure"))
            assertEquals(false, current?.changes(host)?.single()?.value)
        }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `dirty editor asks before discarding in rtl with large dark text`() {
        render(direction = LayoutDirection.Rtl, fontScale = 1.8f, dark = true)
        rule.onNodeWithTag("host_editor_remark").performTextClearance()
        rule.onNodeWithTag("host_editor_remark").performTextInput("Changed")
        rule.onNodeWithContentDescription(str(R.string.cd_close)).performClick()
        rule.onNodeWithText(str(R.string.host_editor_discard_title)).assertIsDisplayed()
        rule.runOnIdle { assertFalse(dismissed) }
        rule.onNodeWithText(str(R.string.host_editor_discard)).performClick()
        rule.runOnIdle { assertEquals(true, dismissed) }
    }
}
