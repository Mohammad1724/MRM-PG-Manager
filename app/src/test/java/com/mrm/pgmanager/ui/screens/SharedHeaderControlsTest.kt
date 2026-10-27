package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGBadge
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.PGScreenHeader
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
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
class SharedHeaderControlsTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    @Test fun `main screen headers use compact visuals and full independent touch targets`() {
        var refreshes = 0
        var settings = 0
        rule.setContent {
            PGScreenHeader(
                title = "Groups", subtitle = "Manage groups", refreshing = false,
                onRefresh = { refreshes++ }, onOpenSettings = { settings++ },
                refreshLabel = "Refresh", settingsLabel = "Settings", badge = { PGBadge("6") }
            )
        }
        listOf(AppIcon.Refresh, AppIcon.Settings).forEach { icon ->
            rule.onNodeWithTag("header_action_visual_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
            rule.onNodeWithTag("header_action_icon_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        }
        val refresh = rule.onNodeWithContentDescription("Refresh")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val settingsAction = rule.onNodeWithContentDescription("Settings")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertTrue(!overlaps(refresh.fetchSemanticsNode().boundsInRoot, settingsAction.fetchSemanticsNode().boundsInRoot))
        refresh.performTouchInput { click(Offset(1f, 1f)) }
        settingsAction.performTouchInput { click(Offset(1f, 1f)) }
        rule.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(1, settings)
        }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `main header actions fit beside a long title and badge in large RTL text`() {
        val title = "A long screen title for accessibility"
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                Box(Modifier.width(320.dp).testTag("header_container")) {
                    PGScreenHeader(
                        title = title, subtitle = "A descriptive subtitle", refreshing = false,
                        onRefresh = {}, onOpenSettings = {},
                        refreshLabel = "Refresh", settingsLabel = "Settings", badge = { PGBadge("123") }
                    )
                }
            }
        }
        val container = rule.onNodeWithTag("header_container").fetchSemanticsNode().boundsInRoot
        val titleBounds = rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val refresh = rule.onNodeWithContentDescription("Refresh")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithContentDescription("Settings")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).fetchSemanticsNode().boundsInRoot
        listOf(refresh, settings).forEach { action ->
            assertTrue(action.left >= container.left && action.right <= container.right)
            assertTrue(!overlaps(titleBounds, action))
        }
        assertTrue(!overlaps(refresh, settings))
    }

    @Test fun `detail refresh uses the same compact visual and keeps its accessible target`() {
        var refreshes = 0
        var backs = 0
        var enabled by mutableStateOf(true)
        rule.setContent {
            PGPageHeader(title = "Details", subtitle = "Settings", onBack = { backs++ }) {
                InfraIconButton(
                    icon = AppIcon.Refresh, description = "Refresh details", tint = Color.Blue,
                    enabled = enabled, onClick = { refreshes++ }
                )
            }
        }
        rule.onNodeWithTag("infra_refresh_visual", useUnmergedTree = true)
            .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
        rule.onNodeWithTag("infra_refresh_icon", useUnmergedTree = true)
            .assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        val refresh = rule.onNodeWithContentDescription("Refresh details")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        refresh.performTouchInput { click(Offset(1f, 1f)) }
        rule.onNodeWithContentDescription(str(R.string.cd_back))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performClick()
        rule.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(1, backs)
            enabled = false
        }
        refresh.assertIsNotEnabled().performTouchInput { click(Offset(1f, 1f)) }
        rule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun `other infrastructure actions retain their previous size`() {
        var additions = 0
        rule.setContent {
            InfraIconButton(icon = AppIcon.Add, description = "Add", tint = Color.Blue, onClick = { additions++ })
        }
        rule.onNodeWithTag("infra_refresh_visual", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Add")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performClick()
        rule.runOnIdle { assertEquals(1, additions) }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `infrastructure section header keeps refresh and add independent in RTL`() {
        var refreshes = 0
        var additions = 0
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(320.dp)) {
                    InfraSectionHeader(
                        title = "Infrastructure", subtitle = "Manage infrastructure", count = 6,
                        onRefresh = { refreshes++ }, onAdd = { additions++ }, addLabel = "Add server"
                    )
                }
            }
        }
        val refresh = rule.onNodeWithContentDescription(str(R.string.refresh))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val add = rule.onNodeWithContentDescription("Add server")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertTrue(!overlaps(refresh.fetchSemanticsNode().boundsInRoot, add.fetchSemanticsNode().boundsInRoot))
        rule.onNodeWithTag("infra_refresh_visual", useUnmergedTree = true).assertWidthIsEqualTo(36.dp)
        refresh.performTouchInput { click(Offset(1f, 1f)) }
        add.performClick()
        rule.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(1, additions)
        }
    }

    private fun overlaps(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
