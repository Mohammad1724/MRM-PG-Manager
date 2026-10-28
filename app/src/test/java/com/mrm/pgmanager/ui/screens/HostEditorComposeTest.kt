package com.mrm.pgmanager.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.HostXrayTemplate
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.LocalChoiceWindow
import com.mrm.pgmanager.ui.components.TestChoiceWindow
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HostEditorComposeTest {
    @get:Rule val rule = createComposeRule()
    private val choiceWindow = TestChoiceWindow()

    private val host = PanelHost(
        id = 3, remark = "Example", addresses = listOf("example.com"),
        raw = """{"id":3,"remark":"Example","address":["example.com"],"allowinsecure":null}"""
    )
    private var current: HostDraft? = null
    private var saved: HostDraft? = null
    private var dismissed = false
    private val saving = mutableStateOf(false)
    private val serverError = mutableStateOf<String?>(null)

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { it.getSharedPreferences("host_editor_compose_test", Context.MODE_PRIVATE) }
    }

    @After fun tearDown() {
        SessionStore.resetPrefsForTests()
        MotionPrefs.reduceMotion = false
    }
    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun render(
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        dark: Boolean = false,
        inbounds: HostOptions<String> = HostOptions.Ready(listOf("VLESS-TLS")),
        initial: PanelHost = host
    ) {
        rule.setContent {
            val density = LocalDensity.current
            var draft by remember { mutableStateOf(HostDraft.from(initial)) }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
                LocalThemeState provides ThemeState(isDark = dark),
                LocalChoiceWindow provides choiceWindow
            ) {
                Box(Modifier.fillMaxSize()) {
                    HostEditorContent(
                        initial = initial,
                        draft = draft,
                        saving = saving.value,
                        error = serverError.value,
                        onDraftChange = { draft = it; current = it },
                        inbounds = inbounds,
                        templates = HostOptions.Ready<HostXrayTemplate>(emptyList()),
                        onRetryInbounds = {},
                        onRetryTemplates = {},
                        onDismiss = { dismissed = true },
                        onSave = { saved = draft }
                    )
                    choiceWindow.Overlay()
                }
            }
        }
    }

    @Test fun `status filter saves independently of host enabled state`() {
        render()
        rule.onNodeWithTag("host_editor_status_dropdown").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_status_clear").assertIsNotEnabled()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(2)
        rule.onNodeWithTag("host_editor_status_limited").performClick()
        rule.onNodeWithTag("host_editor_status_done").performClick()
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle {
            assertEquals(listOf("limited"), saved?.status)
            assertEquals(false, saved?.disabled)
        }
    }

    @Test fun `status dropdown supports multiple choices unknown values and clear all`() {
        val initial = host.copy(
            status = listOf("active", "future_status"),
            raw = """{"id":3,"remark":"Example","address":["example.com"],"status":["active","future_status"]}"""
        )
        render(initial = initial)
        rule.onNodeWithTag("host_editor_status_dropdown").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(0)
        rule.onNodeWithTag("host_editor_status_active").assertIsOn()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(5)
        rule.onNodeWithTag("host_editor_status_future_status").assertIsOn()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(2)
        rule.onNodeWithTag("host_editor_status_limited").performClick()
        rule.runOnIdle { assertEquals(listOf("active", "future_status", "limited"), current?.status) }
        rule.onNodeWithTag("host_editor_status_clear").performClick()
        rule.runOnIdle {
            assertEquals(emptyList<String>(), current?.status)
            assertEquals(false, current?.disabled)
        }
        rule.onNodeWithTag("host_editor_status_done").performClick()
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), saved?.status) }
    }

    @Test fun `status search filters choices without discarding selections`() {
        val initial = host.copy(
            status = listOf("active", "future_status"),
            raw = """{"id":3,"remark":"Example","address":["example.com"],"status":["active","future_status"]}"""
        )
        render(initial = initial)
        rule.onNodeWithTag("host_editor_status_dropdown").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_status_dialog").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, str(R.string.host_editor_user_status)))
        val search = rule.onNodeWithContentDescription(str(R.string.host_editor_search_statuses))
        search.performTextInput(str(R.string.limited))
        rule.onNodeWithTag("host_editor_status_active").assertDoesNotExist()
        rule.onNodeWithTag("host_editor_status_limited").performClick()
        rule.runOnIdle { assertEquals(listOf("active", "future_status", "limited"), current?.status) }
        search.performTextClearance()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(0)
        rule.onNodeWithTag("host_editor_status_active").assertIsOn()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(5)
        rule.onNodeWithTag("host_editor_status_future_status").assertIsOn()
        rule.onNodeWithTag("host_editor_status_done").performClick()
        rule.onNodeWithTag("host_editor_status_dialog").assertDoesNotExist()
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle { assertEquals(listOf("active", "future_status", "limited"), saved?.status) }
    }

    @Test fun `dismiss request closes status dialog without undoing changes already made`() {
        render()
        rule.onNodeWithTag("host_editor_status_dropdown").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(2)
        rule.onNodeWithTag("host_editor_status_limited").performClick()
        rule.runOnIdle { choiceWindow.dismiss() }
        rule.onNodeWithTag("host_editor_status_dialog").assertDoesNotExist()
        rule.onNodeWithTag("host_editor_window").assertIsDisplayed()
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle { assertEquals(listOf("limited"), saved?.status) }
    }

    @Test fun `host inputs expose labels preserve editing and disable during saving`() {
        render()
        val remark = rule.onNodeWithTag("host_editor_remark")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(str(R.string.host_remark))))
        remark.performTextClearance()
        remark.performTextInput("Updated remark")
        rule.onNodeWithTag("host_editor_addresses").performScrollTo().assertHeightIsAtLeast(48.dp)
            .performTextInput(", second.example")
        rule.runOnIdle {
            assertEquals("Updated remark", current?.remark)
            assertTrue(current?.addressesText?.contains("second.example") == true)
            saving.value = true
        }
        remark.assertIsNotEnabled()
        rule.onNodeWithTag("host_editor_status_dropdown").performScrollTo().assertIsNotEnabled()
    }

    @Test fun `http header rows add edit save and delete actual draft values`() {
        render()
        rule.onNodeWithTag("host_editor_section_network").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_field_host").performScrollTo().assertIsDisplayed()
        saveSheetScreenshot("host_editor_network_en")
        rule.onNodeWithTag("host_editor_http_header_add").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_http_header_name_0").performScrollTo().performTextInput("X-Client")
        rule.onNodeWithTag("host_editor_http_header_value_0").performScrollTo().performTextInput("my-client")
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle { assertEquals(listOf(HostHttpHeader("X-Client", "my-client")), saved?.httpHeaders) }
        rule.onNodeWithTag("host_editor_http_header_remove_0").performScrollTo().performClick()
        rule.onNodeWithTag("host_editor_save").assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(emptyList<HostHttpHeader>(), current?.httpHeaders)
            assertTrue(requireNotNull(current).changes(host).isEmpty())
        }
    }

    @Test fun `unsupported saved http headers cannot be edited`() {
        val initial = host.copy(raw = """{"id":3,"remark":"Example","address":["example.com"],"http_headers":{"X-Future":42}}""")
        render(initial = initial)
        rule.onNodeWithTag("host_editor_section_network").performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.host_editor_http_headers_unsupported)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("host_editor_http_header_add").performScrollTo().assertIsNotEnabled()
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

    private fun saveSheetScreenshot(name: String) {
        rule.runOnIdle {
            val window = requireNotNull(ShadowDialog.getLatestDialog()?.window)
            val view = window.decorView
            require(view.width > 0 && view.height > 0) { "The sheet window has no layout" }
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val visibleColors = mutableSetOf<Int>()
            for (pixel in pixels) {
                if (pixel ushr 24 != 0) visibleColors.add(pixel)
                if (visibleColors.size >= 4) break
            }
            check(visibleColors.size >= 4) { "The sheet screenshot is blank" }
            val directory = File("build/test-screens").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    private fun assertActionsWithinEditor() {
        val window = rule.onNodeWithTag("host_editor_window").fetchSemanticsNode().boundsInRoot
        val actionsNode = rule.onNodeWithTag("host_editor_actions").assertIsDisplayed().fetchSemanticsNode()
        val actions = actionsNode.boundsInRoot
        assertEquals(actionsNode.size.width.toFloat(), actions.width, 1f)
        assertEquals(actionsNode.size.height.toFloat(), actions.height, 1f)
        assertTrue(actions.left >= window.left && actions.right <= window.right)
        assertTrue(actions.top >= window.top && actions.bottom <= window.bottom)
        listOf("host_editor_cancel", "host_editor_save").forEach { tag ->
            val node = rule.onNodeWithTag(tag).assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
            val button = node.boundsInRoot
            assertEquals(node.size.width.toFloat(), button.width, 1f)
            assertEquals(node.size.height.toFloat(), button.height, 1f)
            assertTrue(button.left >= actions.left && button.right <= actions.right)
            assertTrue(button.top >= actions.top && button.bottom <= actions.bottom)
        }
    }

    @Test fun `sheet keeps compact close touch target and fixed actions while content scrolls`() {
        render()
        rule.onNodeWithTag("host_editor_drag_handle", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("host_editor_close").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("host_editor_close_visual", useUnmergedTree = true)
            .assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        rule.onNodeWithTag("host_editor_close_icon", useUnmergedTree = true)
            .assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
        assertActionsWithinEditor()
        saveSheetScreenshot("host_editor_sheet_en")
        val before = rule.onNodeWithTag("host_editor_actions").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("host_editor_section_wireguard").performScrollTo()
        assertActionsWithinEditor()
        val after = rule.onNodeWithTag("host_editor_actions").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        rule.onNodeWithTag("host_editor_cancel").performClick()
        rule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h480dp")
    fun `short rtl sheet keeps actions reachable with large text and a long error`() {
        serverError.value = "A long server error. ".repeat(30)
        render(direction = LayoutDirection.Rtl, fontScale = 1.8f, dark = true)
        rule.onNodeWithTag("host_editor_close").assertIsDisplayed()
        rule.onNodeWithTag("host_editor_close_icon", useUnmergedTree = true)
            .assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
        rule.onNodeWithTag("host_editor_error").assertIsDisplayed()
        assertActionsWithinEditor()
        saveSheetScreenshot("host_editor_sheet_rtl_short")
        rule.onNodeWithTag("host_editor_section_wireguard").performScrollTo()
        assertActionsWithinEditor()
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h480dp")
    fun `status dialog keeps fixed actions accessible on a short RTL screen`() {
        val initial = host.copy(
            status = listOf("active", "future_status"),
            raw = """{"id":3,"remark":"Example","address":["example.com"],"status":["active","future_status"]}"""
        )
        render(direction = LayoutDirection.Rtl, fontScale = 1.8f, dark = true, initial = initial)
        rule.onNodeWithTag("host_editor_status_dropdown").performScrollTo().performClick()
        val frame = rule.onNodeWithTag("host_editor_status_dialog").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val clear = rule.onNodeWithTag("host_editor_status_clear").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        val done = rule.onNodeWithTag("host_editor_status_done").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        assertTrue(clear.top >= frame.top && clear.bottom <= frame.bottom)
        assertTrue(done.top >= frame.top && done.bottom <= frame.bottom)
        assertTrue(clear.right <= done.left || done.right <= clear.left)
        // This host test renders the dialog content without a native window.
        rule.onNodeWithTag("host_editor_status_list").performScrollToIndex(5)
        rule.onNodeWithTag("host_editor_status_future_status").assertIsOn()
        val after = rule.onNodeWithTag("host_editor_status_done").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(done.top, after.top, 1f)
        assertEquals(done.bottom, after.bottom, 1f)
        rule.onNodeWithTag("host_editor_status_done").performClick()
        assertActionsWithinEditor()
        rule.runOnIdle { assertEquals(null, current) }
    }

    @Test fun `dragging a dirty sheet requests confirmation without discarding the draft`() {
        render()
        rule.onNodeWithTag("host_editor_remark").performTextClearance()
        rule.onNodeWithTag("host_editor_remark").performTextInput("Edited")
        rule.onNodeWithTag("host_editor_drag_handle", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = 8.dp.toPx(), endY = 400.dp.toPx(), durationMillis = 250)
        }
        rule.onNodeWithText(str(R.string.host_editor_discard_title)).assertIsDisplayed()
        rule.runOnIdle {
            assertFalse(dismissed)
            assertEquals("Edited", current?.remark)
        }
        rule.onNodeWithText(str(R.string.host_editor_discard)).performClick()
        rule.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun `canceling discard keeps the draft and allows saving`() {
        render()
        rule.onNodeWithTag("host_editor_remark").performTextClearance()
        rule.onNodeWithTag("host_editor_remark").performTextInput("Edited")
        rule.onNodeWithTag("host_editor_cancel").performClick()
        val title = str(R.string.host_editor_discard_title)
        rule.onNodeWithText(title).assertIsDisplayed()
        val confirmation = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
        rule.onNode(hasText(str(R.string.cancel)) and hasAnyAncestor(confirmation)).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("host_editor_remark").assertIsDisplayed()
        rule.onNodeWithTag("host_editor_save").performClick()
        rule.runOnIdle {
            assertFalse(dismissed)
            assertEquals("Edited", saved?.remark)
        }
    }

    @Test fun `saving prevents all dismissal actions and sheet gestures`() {
        render()
        rule.runOnIdle { saving.value = true }
        rule.onNodeWithTag("host_editor_close").assertIsNotEnabled()
        rule.onNodeWithTag("host_editor_cancel").assertIsNotEnabled()
        rule.onNodeWithTag("host_editor_save").assertIsNotEnabled()
        rule.onNodeWithTag("host_editor_drag_handle", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = 8.dp.toPx(), endY = 400.dp.toPx(), durationMillis = 250)
        }
        rule.onNodeWithTag("host_editor_window").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.host_editor_discard_title)).assertDoesNotExist()
        rule.runOnIdle { assertFalse(dismissed) }
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
