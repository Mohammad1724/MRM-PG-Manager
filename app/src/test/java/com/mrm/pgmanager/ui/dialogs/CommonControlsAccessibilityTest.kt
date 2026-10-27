package com.mrm.pgmanager.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGTabBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class CommonControlsAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        RuntimeEnvironment.getApplication().getString(id, *args)

    @Test fun `page header exposes a heading and a labelled back action`() {
        rule.setContent { PGPageHeader("Page title", "Page description", onBack = {}) }

        rule.onNodeWithText("Page title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithContentDescription(str(R.string.cd_back))
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun `tabs wrap inside the host and announce selection`() {
        var selected = -1
        val labels = listOf("Appearance", "Notifications and monitoring", "App lock")
        rule.setContent {
            Box(Modifier.width(300.dp).testTag("tabs")) {
                PGTabBar(labels, selectedIndex = 0, onSelect = { selected = it })
            }
        }
        val host = rule.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot
        for (label in labels) {
            val node = rule.onNodeWithText(label)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Tab must remain inside the host", bounds.left >= host.left - 0.5f && bounds.right <= host.right + 0.5f)
            node.assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        rule.onNodeWithText("Appearance").assertIsSelected()
        rule.onNodeWithText("App lock").performClick()
        assertEquals(2, selected)
    }

    @Test fun `settings switch is a single labelled toggle`() {
        var enabled by mutableStateOf(true)
        rule.setContent {
            SettingsSwitchRow(title = "Automatic refresh", checked = enabled, onChange = { enabled = it })
        }
        val toggle = rule.onNodeWithText("Automatic refresh")
        toggle.assertHeightIsAtLeast(48.dp).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        toggle.performClick()
        assertEquals(false, enabled)
    }

    @Test fun `search exposes a labelled clear action`() {
        var query by mutableStateOf("example")
        rule.setContent { PGSearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search users") }
        rule.onNodeWithContentDescription(str(R.string.clear_search))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("", query)
    }

    @Test fun `stepper actions have labels and disable at the limits`() {
        var value by mutableIntStateOf(1)
        rule.setContent {
            SettingsStepper(label = "Interval", value = value, unit = "s", range = 1..2, onChange = { value = it })
        }
        val decrease = str(R.string.a11y_decrease_value, "Interval")
        val increase = str(R.string.a11y_increase_value, "Interval")
        rule.onNodeWithContentDescription(decrease).assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        rule.onNodeWithContentDescription(increase).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(2, value)
        rule.onNodeWithContentDescription(increase).assertIsNotEnabled()
    }

    @Test fun `expiry presets expose selection and full touch height`() {
        var days by mutableStateOf("30")
        rule.setContent { ExpiryPresetSelector(days) { days = it.toString() } }
        rule.onNodeWithText(str(R.string.re_days_value, 30)).assertIsSelected().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onNodeWithText(str(R.string.re_days_value, 7)).performClick().assertIsSelected()
        assertEquals("7", days)
    }

    @Test fun `segmented and checkbox controls expose their selected state`() {
        var checked by mutableStateOf(true)
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                SegmentedControl(options = listOf("First", "Second"), selectedIndex = 1, onSelect = {})
                CheckboxIcon(selected = checked, onToggle = { checked = !checked })
            }
        }
        rule.onNodeWithText("Second").assertIsSelected().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        rule.onNodeWithContentDescription(str(R.string.cd_unselect)).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .performClick()
        assertEquals(false, checked)
    }
}
