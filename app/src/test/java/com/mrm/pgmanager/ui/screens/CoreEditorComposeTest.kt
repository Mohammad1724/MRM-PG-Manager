package com.mrm.pgmanager.ui.screens

import android.content.Context
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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelCore
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoreEditorComposeTest {
    @get:Rule val rule = createComposeRule()
    private val choiceWindow = TestChoiceWindow()
    private val core = PanelCore(id = 3, name = "Main", type = "future-backend", excludeInboundTags = listOf("saved-unknown"))
    private var current: CoreEditorDraft? = null
    private var saved: CoreEditorDraft? = null
    private var dismissed = false
    private val saving = mutableStateOf(false)
    private val serverError = mutableStateOf<String?>(null)

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { it.getSharedPreferences("core_editor_compose_test", Context.MODE_PRIVATE) }
    }

    @After fun tearDown() {
        SessionStore.resetPrefsForTests()
        MotionPrefs.reduceMotion = false
    }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun render(
        editedCore: PanelCore = core,
        availableTags: List<String>? = listOf("local-one", "local-two"),
        tagsLoading: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        dark: Boolean = false
    ) {
        rule.setContent {
            val density = LocalDensity.current
            var draft by remember { mutableStateOf(CoreEditorDraft.from(editedCore)) }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
                LocalThemeState provides ThemeState(isDark = dark),
                LocalChoiceWindow provides choiceWindow
            ) {
                Box(Modifier.fillMaxSize()) {
                    CoreEditorDialog(
                        initial = editedCore, draft = draft, saving = saving.value, error = serverError.value,
                        availableTags = availableTags, tagsLoading = tagsLoading,
                        onDraftChange = { draft = it; current = it },
                        onDismiss = { dismissed = true }, onSave = { saved = draft }
                    )
                    choiceWindow.Overlay()
                }
            }
        }
    }

    @Test fun `tag search keeps unknown selections and exposes 48dp targets`() {
        render()
        rule.onNodeWithTag("core_editor_exclude").performScrollTo().performClick()
        rule.onNodeWithTag("core_editor_tag_option_saved-unknown").assertIsOn()
        val search = rule.onNodeWithContentDescription(str(R.string.core_search_tags))
        search.performTextInput("local-two")
        rule.onNodeWithTag("core_editor_tag_option_saved-unknown").assertDoesNotExist()
        rule.onNodeWithTag("core_editor_tag_option_local-two").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        search.performTextClearance()
        rule.onNodeWithTag("core_editor_tag_option_saved-unknown").assertIsOn()
        rule.onNodeWithTag("core_editor_tag_picker_close").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.runOnIdle { assertEquals(setOf("saved-unknown", "local-two"), saved?.exclude) }
    }

    @Test fun `unknown core type remains visible and default is not offered as a clear action`() {
        render()
        rule.onNodeWithTag("core_editor_type").performScrollTo().performClick()
        rule.onNodeWithTag("core_editor_type_picker_option_future-backend").assertIsDisplayed()
        rule.onNodeWithTag("core_editor_type_picker_option_").assertDoesNotExist()
        rule.runOnIdle { choiceWindow.dismiss() }
        rule.onNodeWithTag("core_editor_name").performTextClearance()
        rule.onNodeWithTag("core_editor_name").performTextInput("Renamed")
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.runOnIdle { assertEquals("future-backend", saved?.type) }
    }

    @Test fun `unstorable tags cannot be added and existing ones require removal`() {
        render(editedCore = core.copy(excludeInboundTags = listOf("")), availableTags = listOf("a,b", "local-one"))
        rule.onNodeWithTag("core_editor_name").performScrollTo().performTextClearance()
        rule.onNodeWithTag("core_editor_name").performTextInput("Renamed")
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.onNodeWithTag("core_editor_error").assertIsDisplayed()
        rule.runOnIdle { assertEquals(null, saved) }
        rule.onNodeWithTag("core_editor_exclude").performScrollTo().performClick()
        rule.onNodeWithTag("core_editor_tag_option_").assertIsOn().performClick()
        rule.onNodeWithTag("core_editor_tag_option_a,b").assertIsNotEnabled()
        rule.onNodeWithTag("core_editor_tag_picker_done").performClick()
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.runOnIdle { assertEquals(emptySet<String>(), saved?.exclude) }
    }

    @Test fun `wireguard requires tags to be removed deliberately rather than clearing them`() {
        render()
        rule.onNodeWithTag("core_editor_type").performScrollTo().performClick()
        rule.onNodeWithTag("core_editor_type_picker_option_wg").performClick()
        rule.runOnIdle {
            assertEquals("wg", current?.type)
            assertEquals(setOf("saved-unknown"), current?.exclude)
        }
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.onNodeWithTag("core_editor_error").assertIsDisplayed()
        rule.runOnIdle { assertEquals(null, saved) }
        rule.onNodeWithTag("core_editor_exclude").performScrollTo().performClick()
        rule.onNodeWithTag("core_editor_tag_option_saved-unknown").assertIsOn().performClick()
        rule.onNodeWithTag("core_editor_tag_picker_done").performClick()
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.runOnIdle {
            assertEquals("wg", saved?.type)
            assertEquals(emptySet<String>(), saved?.exclude)
        }
    }

    @Test fun `canceling discard retains the draft while saving blocks dismissal`() {
        render()
        rule.onNodeWithTag("core_editor_name").performTextClearance()
        rule.onNodeWithTag("core_editor_name").performTextInput("Edited")
        rule.onNodeWithTag("core_editor_cancel").performClick()
        val title = str(R.string.host_editor_discard_title)
        rule.onNodeWithText(title).assertIsDisplayed()
        val confirmation = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
        rule.onNode(hasText(str(R.string.cancel)) and hasAnyAncestor(confirmation)).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.runOnIdle { saving.value = true }
        rule.onNodeWithTag("core_editor_close").assertIsNotEnabled()
        rule.onNodeWithTag("core_editor_cancel").assertIsNotEnabled()
        rule.onNodeWithTag("core_editor_save").assertIsNotEnabled()
        rule.runOnIdle {
            assertFalse(dismissed)
            assertEquals("Edited", current?.name)
        }
    }

    @Test fun `tag loading is distinct from a failed or empty list`() {
        render(availableTags = null, tagsLoading = true)
        rule.onNodeWithTag("core_editor_tags_loading").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("core_editor_tags_error").assertDoesNotExist()
        rule.onNodeWithTag("core_editor_exclude").performScrollTo().assertIsNotEnabled()
    }

    @Test fun `unreadable tag options are visible without discarding the saved tags`() {
        render(availableTags = null)
        rule.onNodeWithTag("core_editor_tags_error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("core_editor_exclude").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("core_editor_tag_option_saved-unknown").assertIsOn().performClick()
        rule.onNodeWithTag("core_editor_tag_picker_done").performClick()
        rule.onNodeWithTag("core_editor_fallbacks").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("core_editor_save").performClick()
        rule.runOnIdle { assertEquals(emptySet<String>(), saved?.exclude) }
    }

    @Test fun `sheet keeps header and actions fixed while the body scrolls`() {
        render()
        rule.onNodeWithTag("core_editor_close").assertWidthIsEqualTo(48.dp).assertHeightIsAtLeast(48.dp)
        val beforeHeader = rule.onNodeWithTag("core_editor_header").fetchSemanticsNode().boundsInRoot
        val beforeFooter = rule.onNodeWithTag("core_editor_footer").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("core_editor_scope_hint").performScrollTo()
        val afterHeader = rule.onNodeWithTag("core_editor_header").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val afterFooter = rule.onNodeWithTag("core_editor_footer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(beforeHeader.top, afterHeader.top, 1f)
        assertEquals(beforeFooter.bottom, afterFooter.bottom, 1f)
        rule.onNodeWithTag("core_editor_cancel").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("core_editor_save").assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h480dp")
    fun `short rtl sheet keeps controls reachable with large text and long errors`() {
        serverError.value = "A long error. ".repeat(20)
        render(direction = LayoutDirection.Rtl, fontScale = 1.8f, dark = true)
        rule.onNodeWithTag("core_editor_close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("core_editor_error").assertIsDisplayed()
        val frame = rule.onNodeWithTag("core_editor_window").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        listOf("core_editor_cancel", "core_editor_save").forEach { tag ->
            val button = rule.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            assertTrue(button.top >= frame.top && button.bottom <= frame.bottom)
        }
    }
}
