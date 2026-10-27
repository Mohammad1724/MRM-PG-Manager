package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsersShareActionTest {
    @get:Rule val rule = createComposeRule()
    private val first = user(1, "alpha")
    private val second = user(2, "bravo")
    private val shared = mutableListOf<PanelUser>()
    private var rowClicks = 0
    private var selectionClicks = 0

    private fun user(id: Long, name: String) = PanelUser(
        id = id, username = name, status = "active", usedTraffic = 0L, dataLimit = 0L,
        expire = null, createdAt = null, subUrl = "https://example.test/sub/$name"
    )
    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)
    private fun description(user: PanelUser) = str(R.string.us_share_subscription, user.username)

    @Composable
    private fun UserTile(mode: ViewMode, user: PanelUser) {
        val onShare: (PanelUser) -> Unit = { shared.add(it) }
        val onRow: () -> Unit = { rowClicks++ }
        val onSelect: () -> Unit = { selectionClicks++ }
        when (mode) {
            ViewMode.GRID -> LuxuryGridCard(user, onClick = onRow, onShareSubscription = onShare, onSelectToggle = onSelect)
            ViewMode.COMPACT_LIST -> LuxuryCompactRow(user, onClick = onRow, onShareSubscription = onShare, onSelectToggle = onSelect)
            ViewMode.MICRO_LIST -> LuxuryMicroRow(user, onClick = onRow, onShareSubscription = onShare, onSelectToggle = onSelect)
        }
    }

    private fun render(mode: ViewMode, rtlLargeText: Boolean = false, twoUsers: Boolean = false) {
        rule.setContent {
            val density = LocalDensity.current
            val textDensity = if (rtlLargeText) Density(density.density, 2f) else density
            val direction = if (rtlLargeText) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalDensity provides textDensity, LocalLayoutDirection provides direction) {
                Column(Modifier.width(if (rtlLargeText) 320.dp else 360.dp)) {
                    Box(Modifier.testTag("first_row")) { UserTile(mode, first) }
                    if (twoUsers) Box(Modifier.testTag("second_row")) { UserTile(mode, second) }
                }
            }
        }
    }

    private fun assertAction(mode: ViewMode, visualSize: Dp, edgeInset: Dp) {
        render(mode)
        val label = description(first)
        rule.onAllNodesWithContentDescription(label).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.us_copy)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(str(R.string.us_copy_sub_link)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(str(R.string.us_show_qr)).assertCountEquals(0)
        val action = rule.onNodeWithContentDescription(label)
            .assertWidthIsEqualTo(visualSize).assertHeightIsEqualTo(visualSize)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        action.performTouchInput { click(center) }
        action.performTouchInput { click(Offset(-edgeInset.toPx(), center.y)) }
        action.performTouchInput { click(Offset(width + edgeInset.toPx(), center.y)) }
        rule.runOnIdle {
            assertEquals(listOf(first, first, first), shared)
            assertEquals("Share touches must not open the row", 0, rowClicks)
        }
        rule.onNodeWithTag("first_row").performTouchInput { click(Offset(90.dp.toPx(), center.y)) }
        rule.onNodeWithContentDescription(str(R.string.cd_select)).performTouchInput { click(center) }
        rule.runOnIdle {
            assertEquals(1, rowClicks)
            assertEquals(1, selectionClicks)
            assertEquals(3, shared.size)
        }
    }

    @Test fun `micro row exposes one compact share action with an expanded touch area`() {
        assertAction(ViewMode.MICRO_LIST, MICRO_ACTION_SIZE, 9.dp)
    }

    @Test fun `compact row exposes one share action with an expanded touch area`() {
        assertAction(ViewMode.COMPACT_LIST, COMPACT_ACTION_SIZE, 8.dp)
    }

    @Test fun `grid card exposes one share action with an expanded touch area`() {
        assertAction(ViewMode.GRID, 40.dp, 3.dp)
    }

    @Test fun `each share action targets its own user`() {
        render(ViewMode.MICRO_LIST, twoUsers = true)
        rule.onNodeWithContentDescription(description(second)).performTouchInput { click(center) }
        rule.runOnIdle {
            assertEquals(listOf(second), shared)
            assertEquals(0, rowClicks)
        }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp-xhdpi")
    fun `share remains reachable in RTL with large text`() {
        render(ViewMode.MICRO_LIST, rtlLargeText = true)
        val row = rule.onNodeWithTag("first_row").fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithContentDescription(description(first))
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue("Share should remain within its row", bounds.left >= row.left && bounds.right <= row.right)
        action.performTouchInput { click(center) }
        rule.runOnIdle {
            assertEquals(listOf(first), shared)
            assertEquals(0, rowClicks)
        }
    }
}
