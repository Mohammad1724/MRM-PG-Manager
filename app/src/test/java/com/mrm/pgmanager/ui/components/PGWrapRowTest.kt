package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * دو چیزِ همین‌جا تثبیت می‌شود؛ هر دو از گزارشِ «چینشِ هاست‌ها» آمده‌اند:
 *
 *  • `PGWrapRow` واقعاً می‌شکند (چیپِ سوم به خطِ دوم می‌رود) و هیچ بچّهای از عرضِ
 *    والد بیرون نمی‌زند — جای اسکرولِ افقی که چیپِ آخر را نصفه می‌گذاشت.
 *  • `PGBadge` قدِ یک خط دارد، حتی وقتی پنل مقدار را چندخطی فرستاده باشد
 *    (inbound tag هایی با `\n`)؛ وگرنه یک برچسبِ سه‌خطی کل کارت را می‌بلند کرد.
 *
 * (قاعده‌ها: [docs/CI.md] §۳ — `boundsInRoot` به‌جای `assertIsDisplayed()` و
 * والدِ اندازه‌دار، چون `Column` فرزندِ جانشده را نه اندازه می‌گیرد نه جای‌دهی.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class PGWrapRowTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun chip(tag: String, width: Int) =
        Box(Modifier.testTag(tag).size(width.dp, 24.dp))

    @Test
    fun wrapRow_flowsIntoLines_insteadOfOverflowing() {
        rule.setContent {
            Box(Modifier.size(300.dp, 200.dp)) {
                PGWrapRow(horizontalGap = 10.dp, verticalGap = 6.dp) {
                    chip("c0", 120)
                    chip("c1", 120)
                    chip("c2", 120)
                }
            }
        }
        val a = rule.onNodeWithTag("c0").fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithTag("c1").fetchSemanticsNode().boundsInRoot
        val c = rule.onNodeWithTag("c2").fetchSemanticsNode().boundsInRoot
        // دو چیپ اول در خطِ اول (هم‌خط، چیپِ دوم بعدی)، سومی روی خطِ جدید و هم‌ترازِ اولی.
        assertEquals("c0/c1 باید هم‌خط باشند", a.top, b.top, 0.5f)
        assertTrue("c1 باید بعد از c0 بیاید (با فاصله)", b.left > a.right)
        assertTrue("c2 باید به خطِ دوم افتاده باشد", c.top > a.top + 6f)
        assertEquals("c2 باید از همان لبهٔ شروعِ خطِ دوم بیاید", a.left, c.left, 0.5f)
        // هیچ چیپی نباید از باکسِ ۳۰dp بیرون بزند — همان «نصفه‌در‌نیمه» بودنِ قبلی.
        for ((i, node) in listOf(a, b, c).withIndex()) {
            assertTrue("چیپِ $i بیرونِ والد است (راست=${node.right})", node.right <= 300f + 0.5f)
        }
    }

    @Test
    fun distributedRow_fillsTheWidthAndCentersASingleWrappedItem() {
        rule.setContent {
            Box(Modifier.size(300.dp, 200.dp).testTag("container")) {
                PGWrapRow(horizontalGap = 10.dp, verticalGap = 6.dp, distributeHorizontally = true) {
                    repeat(4) { chip("d$it", 90) }
                }
            }
        }
        val container = rule.onNodeWithTag("container").fetchSemanticsNode().boundsInRoot
        val items = (0..3).map { rule.onNodeWithTag("d$it").fetchSemanticsNode().boundsInRoot }
        assertEquals(container.left, items[0].left, 0.5f)
        assertEquals(container.right, items[2].right, 0.5f)
        assertEquals(items[1].left - items[0].right, items[2].left - items[1].right, 1f)
        assertTrue(items[3].top > items[0].bottom)
        assertEquals(container.center.x, items[3].center.x, 1f)
        items.forEach { item ->
            assertTrue(item.left >= container.left && item.right <= container.right)
        }
    }

    @Test
    fun distributedRow_balancesEveryWrappedLineIndependently() {
        rule.setContent {
            Box(Modifier.size(220.dp, 200.dp).testTag("container")) {
                PGWrapRow(horizontalGap = 10.dp, verticalGap = 6.dp, distributeHorizontally = true) {
                    repeat(4) { chip("w$it", 90) }
                }
            }
        }
        val container = rule.onNodeWithTag("container").fetchSemanticsNode().boundsInRoot
        val items = (0..3).map { rule.onNodeWithTag("w$it").fetchSemanticsNode().boundsInRoot }
        assertEquals(items[0].top, items[1].top, 0.5f)
        assertEquals(items[2].top, items[3].top, 0.5f)
        assertTrue(items[2].top > items[0].bottom)
        listOf(0 to 1, 2 to 3).forEach { (first, last) ->
            assertEquals(container.left, items[first].left, 0.5f)
            assertEquals(container.right, items[last].right, 0.5f)
        }
    }

    @Test
    fun distributedRow_mirrorsPositionsInRtl() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(300.dp, 100.dp).testTag("container")) {
                    PGWrapRow(horizontalGap = 10.dp, distributeHorizontally = true) {
                        repeat(3) { chip("r$it", 60) }
                    }
                }
            }
        }
        val container = rule.onNodeWithTag("container").fetchSemanticsNode().boundsInRoot
        val items = (0..2).map { rule.onNodeWithTag("r$it").fetchSemanticsNode().boundsInRoot }
        assertEquals(container.right, items[0].right, 0.5f)
        assertEquals(container.left, items[2].left, 0.5f)
        assertEquals(items[0].left - items[1].right, items[1].left - items[2].right, 1f)
    }

    @Test
    fun badgeLabel_collapsesPanelLineBreaks() {
        // تابعِ خالصِ همان‌جا که استفاده می‌شود: خط‌شکستنِ پنل به « · ».
        assertEquals("A · B", badgeLabel("A\nB"))
        assertEquals("A · B", badgeLabel("A \r\n  B"))
        assertEquals("A · B · C", badgeLabel("  A\nB\nC  "))
        assertEquals("TLS", badgeLabel("TLS"))
    }

    @Test
    fun multilineBadgeValue_rendersExactlyOneLine() {
        val oneLine = "TROJAN TCP · TLS 2097 · status"
        rule.setContent {
            Box(Modifier.size(360.dp, 220.dp)) {
                Column {
                    PGBadge("TROJAN TCP\nTLS 2097\nstatus")
                    PGBadge(oneLine)
                }
            }
        }
        // هر دو برچسب باید به یک رشتهٔ یک‌خطی برسند؛ پس دو نودِ هم‌متن داریم.
        val nodes = rule.onAllNodesWithText(oneLine, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("برچسبِ چندخطی باید به متنِ یک‌خطیِ همان مقدار برسد", 2, nodes.size)
        val first = nodes[0]
        val results = mutableListOf<TextLayoutResult>()
        first.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        assertTrue("نتیجهٔ layout در دسترس نیست", results.isNotEmpty())
        assertEquals("برچسب باید یک خط باشد", 1, results[0].lineCount)
        val a = first.boundsInRoot
        val b = nodes[1].boundsInRoot
        assertEquals("قدِ دو برچسبِ هم‌متن باید برابر باشد", a.height, b.height, 0.5f)
    }
}
