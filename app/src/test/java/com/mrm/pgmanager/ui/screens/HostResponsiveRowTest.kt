package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.HostSecurity
import com.mrm.pgmanager.data.model.PanelHost
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
class HostResponsiveRowTest {
    @get:Rule val rule = createComposeRule()

    private val host = PanelHost(
        id = 1,
        remark = "Host A",
        addresses = listOf("cfcliclip2.iranshop21.ir"),
        port = 443,
        inboundTag = "VLESS-WS-CDN-2053",
        security = HostSecurity.INBOUND_DEFAULT,
        priority = 6
    )
    private var edits = 0
    private var density = 1f

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun render(
        value: PanelHost = host,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        editable: Boolean = true,
        busy: Boolean = false,
        theme: ThemeState = ThemeState()
    ) {
        rule.setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
                LocalLayoutDirection provides direction,
                LocalThemeState provides theme
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        HostRow(
                            host = value,
                            readable = true,
                            busy = busy,
                            onEdit = if (editable) ({ edits++ }) else null,
                            onToggle = if (editable) ({}) else null,
                            onDelete = if (editable) ({}) else null
                        )
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertValueFits(tag: String) {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        assertTrue("Text layout is available for $tag", results.isNotEmpty())
        val layout = results[0]
        assertFalse(
            "Representative text must not be truncated: $tag; text=${layout.layoutInput.text}; " +
                "size=${layout.size}; constraints=${layout.layoutInput.constraints}; lines=${layout.lineCount}; " +
                "widthOverflow=${layout.didOverflowWidth}; heightOverflow=${layout.didOverflowHeight}; " +
                "ellipsized=${(0 until layout.lineCount).any { layout.isLineEllipsized(it) }}",
            layout.hasVisualOverflow
        )
    }

    private fun firstBaseline(tag: String): Float {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        assertTrue("Text layout is available for $tag", results.isNotEmpty())
        return node.boundsInRoot.top + results[0].firstBaseline
    }

    private fun assertLabelBaselinesAligned() {
        listOf("host_readable_address", "host_readable_inbound", "host_readable_priority").forEach { tag ->
            assertEquals("Label and value should share a baseline for $tag", firstBaseline("${tag}_label"), firstBaseline(tag), 1f)
        }
    }

    private fun assertSeparated(direction: LayoutDirection) {
        val row = bounds("host_readable_row")
        val name = bounds("host_readable_name")
        val address = bounds("host_readable_address")
        val inbound = bounds("host_readable_inbound")
        val priority = bounds("host_readable_priority")
        val more = rule.onNodeWithContentDescription(str(R.string.act_more)).fetchSemanticsNode().boundsInRoot
        assertTrue("Name precedes address", name.bottom <= address.top + 1f)
        assertTrue("Address and inbound must not overlap", address.bottom <= inbound.top + 1f)
        assertTrue("Inbound and priority must not overlap", inbound.bottom <= priority.top + 1f)
        assertTrue("Technical values get a readable width", address.width >= 130.dp.value * density)
        assertTrue("Values stay inside the row", address.left >= row.left && address.right <= row.right)
        if (direction == LayoutDirection.Ltr) {
            assertTrue("Menu does not cover the name", name.right <= more.left + 1f)
        } else {
            assertTrue("Menu does not cover the name in RTL", more.right <= name.left + 1f)
        }
    }

    @Test fun `phone row separates host fields and keeps a full size menu target`() {
        render()
        rule.onNodeWithTag("host_readable_address", useUnmergedTree = true)
            .assertTextEquals("cfcliclip2.iranshop21.ir:443")
        rule.onNodeWithTag("host_readable_inbound", useUnmergedTree = true).assertTextEquals("VLESS-WS-CDN-2053")
        rule.onNodeWithTag("host_readable_priority", useUnmergedTree = true).assertTextEquals("6")
        rule.onNodeWithContentDescription(str(R.string.active), useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription(str(R.string.act_more))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertSeparated(LayoutDirection.Ltr)
        assertLabelBaselinesAligned()
        assertValueFits("host_readable_address")
        assertValueFits("host_readable_inbound")
        assertTrue("A representative host card should fit within 120 dp", bounds("host_readable_row").height <= 120f * density)
        rule.onNodeWithTag("host_readable_name", useUnmergedTree = true).performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, edits) }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `RTL and 200 percent text keep representative values legible and separated`() {
        render(direction = LayoutDirection.Rtl, fontScale = 2f)
        assertSeparated(LayoutDirection.Rtl)
        assertLabelBaselinesAligned()
        listOf("host_readable_address_label", "host_readable_inbound_label", "host_readable_priority_label")
            .forEach(::assertValueFits)
        assertValueFits("host_readable_name")
        assertValueFits("host_readable_address")
        assertValueFits("host_readable_inbound")
        assertValueFits("host_readable_priority")
        rule.onNodeWithContentDescription(str(R.string.act_more))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test
    @Config(qualifiers = "w240dp-h800dp")
    fun `very narrow screens still wrap values inside the row`() {
        render(fontScale = 1.8f)
        val row = bounds("host_readable_row")
        val address = bounds("host_readable_address")
        val inbound = bounds("host_readable_inbound")
        val priority = bounds("host_readable_priority")
        assertTrue(address.width > 40f * density)
        assertTrue(address.left >= row.left && address.right <= row.right)
        assertTrue(address.bottom <= inbound.top + 1f && inbound.bottom <= priority.top + 1f)
        assertValueFits("host_readable_address")
        assertValueFits("host_readable_inbound")
        rule.onNodeWithContentDescription(str(R.string.act_more))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test fun `long values retain their full accessible text without overlapping actions`() {
        val longHost = host.copy(
            remark = "An exceptionally long host name for accessibility and truncation",
            addresses = listOf("very-long-subdomain.example-host-name.example.com", "192.0.2.1"),
            inboundTag = "VLESS-VERY-LONG-INBOUND-TAG-2053-TLS"
        )
        render(value = longHost)
        rule.onNodeWithTag("host_readable_name", useUnmergedTree = true).assertTextEquals(longHost.remark)
        rule.onNodeWithTag("host_readable_address", useUnmergedTree = true)
            .assertTextEquals("very-long-subdomain.example-host-name.example.com, 192.0.2.1:443")
        rule.onNodeWithTag("host_readable_inbound", useUnmergedTree = true).assertTextEquals(longHost.inboundTag!!)
        assertSeparated(LayoutDirection.Ltr)
        assertValueFits("host_readable_name")
        assertValueFits("host_readable_address")
        assertValueFits("host_readable_inbound")
    }

    @Test fun `inbound retains its explicit security label`() {
        render(value = host.copy(security = HostSecurity.TLS))
        rule.onNodeWithTag("host_readable_inbound", useUnmergedTree = true)
            .assertTextEquals("VLESS-WS-CDN-2053 · ${str(R.string.host_security_tls)}")
        assertValueFits("host_readable_inbound")
    }

    @Test fun `an inbound tag containing status is displayed unchanged`() {
        val tag = "TROJAN TCP TLS 2097 status"
        render(value = host.copy(inboundTag = tag))
        rule.onNodeWithTag("host_readable_inbound", useUnmergedTree = true).assertTextEquals(tag)
        assertValueFits("host_readable_inbound")
    }

    @Test fun `zero priority remains visible in the readable row`() {
        render(value = host.copy(priority = 0))
        rule.onNodeWithTag("host_readable_priority", useUnmergedTree = true).assertTextEquals("0")
        assertLabelBaselinesAligned()
    }

    @Test fun `negative priority remains visible in the readable row`() {
        render(value = host.copy(priority = -2))
        rule.onNodeWithTag("host_readable_priority", useUnmergedTree = true).assertTextEquals("-2")
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp")
    fun `zero priority remains visible in the wide table`() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(800.dp)) {
                    HostRow(host.copy(priority = 0), readable = false, busy = false, onEdit = null, onToggle = null, onDelete = null)
                }
            }
        }
        rule.onNodeWithText("0", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `dark rows keep the same readable geometry and actions`() {
        render(theme = ThemeState(isDark = true, amoledDark = true))
        assertSeparated(LayoutDirection.Ltr)
        assertValueFits("host_readable_address")
        rule.onNodeWithContentDescription(str(R.string.act_more))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test fun `read-only rows do not expose the action menu`() {
        render(value = host.copy(isDisabled = true, addresses = emptyList(), port = null, inboundTag = null, priority = null), editable = false)
        rule.onNodeWithContentDescription(str(R.string.host_disabled_badge), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("host_readable_row").assertHasNoClickAction()
        rule.onNodeWithContentDescription(str(R.string.act_more)).assertDoesNotExist()
        rule.onNodeWithTag("host_readable_address", useUnmergedTree = true).assertTextEquals("—")
        rule.onNodeWithTag("host_readable_inbound", useUnmergedTree = true).assertTextEquals("—")
        rule.onNodeWithTag("host_readable_priority", useUnmergedTree = true).assertTextEquals("—")
    }

    @Test fun `busy row retains its content but replaces the action menu`() {
        render(busy = true)
        rule.onNodeWithTag("host_readable_name", useUnmergedTree = true).assertTextEquals("Host A")
        rule.onNodeWithContentDescription(str(R.string.act_more)).assertDoesNotExist()
    }

    @Test fun `menu actions preserve labels callbacks and permission filtering`() {
        var toggles = 0
        var deletions = 0
        val actions = hostRowActions(host, { edits++ }, { toggles++ }, { deletions++ })
        assertEquals(listOf(R.string.qa_edit, R.string.host_toggle_disable, R.string.delete_host), actions.map { it.labelRes })
        assertTrue(actions.last().danger)
        actions.forEach { it.run() }
        assertEquals(1, edits)
        assertEquals(1, toggles)
        assertEquals(1, deletions)
        assertEquals(R.string.host_toggle_enable, hostRowActions(host.copy(isDisabled = true), null, {}, null).single().labelRes)
        assertTrue(hostRowActions(host, null, null, null).isEmpty())
    }
}
