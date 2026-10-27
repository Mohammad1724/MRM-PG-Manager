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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
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
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGFAB
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
class UsersCompactControlsTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    @Test fun `header actions have smaller visuals and full touch targets`() {
        var refreshes = 0
        var settings = 0
        rule.setContent {
            TopBarHeader(onRefresh = { refreshes++ }, loading = false, onOpenSettings = { settings++ })
        }
        listOf(AppIcon.Refresh, AppIcon.Settings).forEach { icon ->
            rule.onNodeWithTag("header_action_visual_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
            rule.onNodeWithTag("header_action_icon_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        }
        rule.onNodeWithContentDescription(str(R.string.refresh))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.onNodeWithContentDescription(str(R.string.app_settings))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(1, settings)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `toolbar visuals are compact and all actions still work`() {
        var filter by mutableStateOf(UserFilter.ALL)
        var sort by mutableStateOf(UserSort.CREATED)
        var groupId by mutableStateOf<Int?>(null)
        var viewMode by mutableStateOf(ViewMode.GRID)
        rule.setContent {
            FilterAndControlBar(
                currentFilter = filter, onFilterChange = { filter = it },
                currentSort = sort, onSortChange = { sort = it },
                viewMode = viewMode, onViewModeChange = { viewMode = it },
                groups = listOf(Group(1, "VIP")), groupFilterId = groupId,
                onGroupFilterChange = { groupId = it }
            )
        }

        listOf(AppIcon.Filter, AppIcon.Sort).forEach { icon ->
            rule.onNodeWithTag("users_bar_visual_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
            rule.onNodeWithTag("users_bar_icon_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        }
        listOf(AppIcon.GridView, AppIcon.ListRows, AppIcon.DenseList).forEach { icon ->
            rule.onNodeWithTag("users_view_visual_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
            rule.onNodeWithTag("users_view_icon_${icon.name}", useUnmergedTree = true)
                .assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        }
        rule.onNodeWithTag("users_group_visual", useUnmergedTree = true).assertHeightIsEqualTo(36.dp)
        rule.onNodeWithTag("users_group_icon", useUnmergedTree = true).assertWidthIsEqualTo(14.dp)
        rule.onNodeWithTag("users_view_modes_background", useUnmergedTree = true)
            .assertWidthIsEqualTo(132.dp).assertHeightIsEqualTo(36.dp)

        val filterDescription = str(R.string.filter) + ": " + str(R.string.all)
        rule.onNodeWithContentDescription(filterDescription)
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.onNodeWithText(str(R.string.online)).performClick()
        rule.runOnIdle { assertEquals(UserFilter.ONLINE, filter) }

        val sortDescription = str(R.string.sort) + ": " + str(R.string.created)
        rule.onNodeWithContentDescription(sortDescription)
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.onNodeWithText(str(R.string.name)).performClick()
        rule.runOnIdle { assertEquals(UserSort.NAME, sort) }

        rule.onNodeWithText(str(R.string.us_group_filter))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.onNodeWithText("VIP").performClick()
        rule.runOnIdle { assertEquals(1, groupId ?: -1) }

        rule.onNodeWithContentDescription(str(R.string.us_view_grid)).assertIsSelected()
        rule.onNodeWithContentDescription(str(R.string.us_view_compact))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.runOnIdle { assertEquals(ViewMode.COMPACT_LIST, viewMode) }
        rule.onNodeWithContentDescription(str(R.string.us_view_compact)).assertIsSelected()
    }

    @Test fun `compact create button keeps a 48dp target and stops accepting taps when hidden`() {
        var clicks = 0
        var visible by mutableStateOf(true)
        rule.setContent {
            PGFAB(icon = AppIcon.UserAdd, contentDescription = "Create user", visible = visible, compact = true) { clicks++ }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("compact_fab_visual", useUnmergedTree = true)
            .assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        rule.onNodeWithTag("compact_fab_icon", useUnmergedTree = true)
            .assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
        rule.onNodeWithContentDescription("Create user")
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .performTouchInput { click(Offset(1f, 1f)) }
        rule.runOnIdle {
            assertEquals(1, clicks)
            visible = false
        }
        rule.onNodeWithContentDescription("Create user").assertIsNotEnabled()
    }

    @Test fun `other screens retain the regular create button size`() {
        rule.setContent { PGFAB(icon = AppIcon.Add, contentDescription = "Add") {} }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        rule.onNodeWithTag("compact_fab_visual", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `narrow RTL toolbar retains non-overlapping targets at large system font scale`() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                Box(Modifier.width(320.dp).testTag("toolbar_container")) {
                    FilterAndControlBar(
                        currentFilter = UserFilter.ALL, onFilterChange = {},
                        currentSort = UserSort.CREATED, onSortChange = {},
                        viewMode = ViewMode.GRID, onViewModeChange = {},
                        groups = listOf(Group(1, "A long group name for accessibility")), groupFilterId = 1
                    )
                }
            }
        }
        val descriptions = listOf(
            str(R.string.filter) + ": " + str(R.string.all),
            str(R.string.sort) + ": " + str(R.string.created),
            str(R.string.us_view_grid), str(R.string.us_view_compact), str(R.string.us_view_micro)
        )
        val bounds = descriptions.map { description ->
            rule.onNodeWithContentDescription(description)
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .fetchSemanticsNode().boundsInRoot
        } + rule.onNodeWithText("A long group name for accessibility")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertTrue("Touch targets overlap: $first and $second", !overlaps(first, second))
            }
        }
    }

    private fun overlaps(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
