package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.NodeLogFilter
import com.mrm.pgmanager.data.model.NodeLogLine
import com.mrm.pgmanager.data.model.NodeLogLevel
import com.mrm.pgmanager.data.model.PanelNode
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

/**
 * تستِ رفتاریِ **بدنهٔ صفحهٔ «لاگِ نود»** — [NodeLogsContent].
 *
 * چرا خودِ `NodeLogsScreen` آزموده نمی‌شود: جریانش بی‌پایان است و `waitForIdle`
 * هیچ‌وقت برنمی‌گردد (قاعدهٔ §۳ `docs/CI.md`). همان چیزی که در تولید با جریانِ
 * زنده کار می‌کند، یک composableِ بی‌شبکه است که حالت را از پارامتر می‌گیرد؛
 * پس می‌شود با چند خطِ ثابت سنجیدش.
 *
 * چهار قراردادی که اینجا قفل می‌شود — همه‌شان رفتارهایِ «کاربر فکر می‌کند لاگ
 * نیست ولی هست» هستند:
 *  ۱. چیپِ شدتی روشن نیست یعنی **همه**، نه هیچ؛
 *  ۲. وقتی خط‌ها هستند ولی هیچ‌کدام با فیلتر نمی‌خوانند، پیامِ «فیلتر» می‌آید و
 *     چند خط پنهان شده — نه «هنوز خطی نرسیده»؛
 *  ۳. کپی همان فهرستِ دیدنی را می‌دهد (نه بافرِ خام)؛
 *  ۴. بدونِ `nodes.logs` صفحه توضیح می‌دهد و هیچ کنترلی نمی‌سازد.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class NodeLogsContentTest {

    @get:Rule val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    private val nodes = listOf(
        PanelNode(id = 1, name = "node-a", status = "connected"),
        PanelNode(id = 2, name = "node-b", status = "connected")
    )

    private fun logLine(id: Long, level: NodeLogLevel, message: String, stamp: String? = null) =
        NodeLogLine(id = id, level = level, message = message, timestampText = stamp, timestampMs = null)

    private val sample = listOf(
        logLine(0L, NodeLogLevel.Error, "panic: runtime error", "2025-09-27 13:26:58.279079"),
        logLine(1L, NodeLogLevel.Info, "accepted tcp:1.1.1.1:443"),
        logLine(2L, NodeLogLevel.Debug, "stats: memory 42MB")
    )

    @Before fun setUp() {
        // «کاهش انیمیشن»: listIntro و گذارها روی صفر می‌آیند تا waitForIdle گیر نکند.
        MotionPrefs.reduceMotion = true
    }

    @After fun tearDown() {
        MotionPrefs.reduceMotion = false
    }

    private fun render(
        displayNodes: List<PanelNode> = nodes,
        lines: List<NodeLogLine> = sample,
        total: Int = lines.size,
        filter: NodeLogFilter = NodeLogFilter(),
        stream: NodeLogStream = NodeLogStream.Streaming,
        streamError: String? = null,
        nodesLoading: Boolean = false,
        nodesError: String? = null,
        canLogs: Boolean = true,
        paused: Boolean = false,
        buffered: Int = 0,
        selectedNode: Int? = 1,
        onSelectNode: (Int) -> Unit = {},
        onTogglePause: () -> Unit = {},
        onReconnect: () -> Unit = {},
        onToggleLevel: (NodeLogLevel) -> Unit = {},
        onWindowChange: (String) -> Unit = {},
        onCopy: (List<NodeLogLine>) -> Unit = {}
    ) {
        rule.setContent {
            NodeLogsContent(
                nodes = displayNodes,
                selectedNode = selectedNode,
                stream = stream,
                streamError = streamError,
                nodesLoading = nodesLoading,
                nodesError = nodesError,
                canLogs = canLogs,
                total = total,
                visible = lines,
                hidden = total - lines.size,
                buffered = buffered,
                paused = paused,
                filter = filter,
                onBack = {},
                onDismissError = {},
                onRefresh = {},
                onSelectNode = onSelectNode,
                onTogglePause = onTogglePause,
                onClear = {},
                onReconnect = onReconnect,
                onQueryChange = {},
                onToggleLevel = onToggleLevel,
                onWindowChange = onWindowChange,
                onCopy = onCopy
            )
        }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test fun `streamed lines are shown with the node count in the header`() {
        render()
        sample.forEach { assertTrue("متنِ خط باید روی صفحه باشد", count(it.message) > 0) }
        // تاریخِ خط هم کنارش می‌آید (نه فقط در tooltip)
        assertEquals(1, count("2025-09-27 13:26:58.279079"))
        assertEquals(1, count("${sample.size}"))
    }

    @Test fun `filtered out lines say so instead of claiming nothing arrived`() {
        render(lines = emptyList(), total = 412)
        assertEquals(1, count(str(R.string.nl_filtered_empty)))
        assertEquals(1, count(str(R.string.nl_filtered_count, "412", "412")))
        // «هنوز خطی نرسیده» دروغ است وقتی ۴۱۲ خط در بافر است
        assertEquals(0, count(str(R.string.nl_empty)))
        assertEquals(0, count(str(R.string.nl_waiting)))
    }

    @Test fun `node list errors provide retry rather than claiming no nodes exist`() {
        render(displayNodes = emptyList(), lines = emptyList(), nodesError = "Access denied")
        assertEquals(1, count("Access denied"))
        assertEquals(1, count(str(R.string.load_failed)))
        assertEquals(1, count(str(R.string.retry)))
        assertEquals(0, count(str(R.string.nl_no_node)))
    }

    @Test fun `empty buffer while connecting waits for the first line`() {
        render(lines = emptyList(), total = 0, stream = NodeLogStream.Connecting)
        assertEquals(1, count(str(R.string.nl_waiting)))
        assertEquals(0, count(str(R.string.nl_filtered_empty)))
    }

    @Test fun `level chips are a toggle and the window chip reports its key`() {
        var levelTaps = listOf<NodeLogLevel>()
        var windowTaps = listOf<String>()
        render(
            filter = NodeLogFilter(levels = setOf(NodeLogLevel.Error)),
            onToggleLevel = { levelTaps = levelTaps + it },
            onWindowChange = { windowTaps = windowTaps + it }
        )
        rule.onNodeWithText(str(R.string.nl_level_warning)).performClick()
        rule.onNodeWithText(str(R.string.nl_time_1h)).performClick()
        assertEquals(listOf(NodeLogLevel.Warning), levelTaps)
        assertEquals(listOf("1h"), windowTaps)
    }

    @Test fun `copy hands over exactly the visible lines`() {
        var copied: List<NodeLogLine> = emptyList()
        render(
            lines = sample.take(2),
            total = sample.size,
            filter = NodeLogFilter(levels = setOf(NodeLogLevel.Error, NodeLogLevel.Info)),
            onCopy = { copied = it }
        )
        rule.onNodeWithContentDescription(str(R.string.nl_copy)).performClick()
        // «کپیِ خط‌های دیدنی» یعنی فیلتر هم روی کلیپ‌بورد اثر دارد، نه بافرِ خام
        assertEquals(listOf("panic: runtime error", "accepted tcp:1.1.1.1:443"), copied.map { it.message })
    }

    @Test fun `pause and resume buttons report which state the screen should flip`() {
        var taps = 0
        render(paused = true, buffered = 7, onTogglePause = { taps++ })
        // وقتی متوقف است، دکمه باید «ادامه» باشد (آیکون Play) و صف هم شمارش شود
        rule.onNodeWithContentDescription(str(R.string.nl_resume)).performClick()
        assertEquals(1, taps)
        assertEquals(1, count(str(R.string.nl_paused_buffered, "7")))
        assertEquals(0, count(str(R.string.nl_paused)))
    }

    @Test fun `a closed stream offers to resume`() {
        var reconnects = 0
        render(stream = NodeLogStream.Ended, onReconnect = { reconnects++ })
        rule.onNodeWithText(str(R.string.nl_reconnect)).performClick()
        assertEquals(1, reconnects)
    }

    /** جریانِ باز «ادامهٔ دریافت» نمی‌خواهد؛ دکمه یعنی جریانی بسته شده است. */
    @Test fun `an open stream hides the resume affordance`() {
        render(stream = NodeLogStream.Streaming)
        assertEquals(0, count(str(R.string.nl_reconnect)))
    }

    @Test fun `node chips pick the node by id`() {
        var picked = -1
        render(selectedNode = 1, onSelectNode = { picked = it })
        rule.onNodeWithText("node-b").performClick()
        assertEquals(2, picked)
    }

    @Test fun `without the logs permission the screen explains and hides every control`() {
        render(canLogs = false)
        assertEquals(1, count(str(R.string.nl_need_permission)))
        // نه چیپِ فیلدی، نه جست‌وجو، نه فهرستِ خط‌ها
        assertEquals(0, count(sample[0].message))
        assertEquals(0, count(str(R.string.nl_level_error)))
        assertEquals(0, count(str(R.string.nl_search)))
    }

    @Test fun `filter chips keep the minimum touch target`() {
        render()
        // همان قاعدهٔ تب‌های تنظیمات: چیپِ کوچکِ زیر ۴۰dp روی گوشی لمسِ از دست رفته است.
        rule.onNodeWithText(str(R.string.nl_level_error)).assertHeightIsAtLeast(40.dp)
        rule.onNodeWithText(str(R.string.nl_time_all)).assertHeightIsAtLeast(40.dp)
    }

    @Test fun `selected filters have accessible state and forty eight dp targets`() {
        render(filter = NodeLogFilter(levels = setOf(NodeLogLevel.Error), windowMs = 3_600_000L))
        rule.onNodeWithText(str(R.string.nl_level_error))
            .assertHeightIsAtLeast(48.dp).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithText(str(R.string.nl_time_1h))
            .assertHeightIsAtLeast(48.dp).assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test fun `log rows expose severity and expanded state without relying on color`() {
        render(lines = sample.take(1))
        rule.onNodeWithText(str(R.string.nl_row_error)).assertExists()
        val row = rule.onNodeWithText(sample[0].message)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.nl_line_collapsed)))
            .performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.nl_line_expanded)))
    }

    @Test fun `no connected node says why and still offers a refresh`() {
        var refreshed = 0
        rule.setContent {
            NodeLogsContent(
                nodes = emptyList(),
                selectedNode = null,
                stream = NodeLogStream.Idle,
                streamError = null,
                nodesLoading = false,
                nodesError = null,
                canLogs = true,
                total = 0,
                visible = emptyList(),
                hidden = 0,
                buffered = 0,
                paused = false,
                filter = NodeLogFilter(),
                onBack = {},
                onDismissError = {},
                onRefresh = { refreshed++ },
                onSelectNode = {},
                onTogglePause = {},
                onClear = {},
                onReconnect = {},
                onQueryChange = {},
                onToggleLevel = {},
                onWindowChange = {},
                onCopy = {}
            )
        }
        assertEquals(1, count(str(R.string.nl_no_node)))
        rule.onNodeWithText(str(R.string.refresh)).performClick()
        assertEquals(1, refreshed)
    }
}
