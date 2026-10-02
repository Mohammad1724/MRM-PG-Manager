package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * کاشیِ آمار در گوشیِ ۳۶۰dp: هر کاشی نیمِ عرضِ کارت (≈۱۵۰dp) است. گزارشِ دستگاه:
 * «970.87 M…» و «7.02 GB/4…» و «↓ 826.72 GB ↑…» بریده می‌شدند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class RingStatCardTest {
    @get:Rule val rule = createComposeRule()

    private fun layouts(tag: String): List<TextLayoutResult> =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
            val out = mutableListOf<TextLayoutResult>()
            n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
            out.first()
        }

    @Test fun worstCaseTiles_neverEllipsize() {
        rule.setContent {
            // ۳۶۰ − ۲×۱۴ (حاشیهٔ صفحه) − ۲×۱۲ (padding کارت) = ۳۰۸dp؛ دو کاشی با فاصلهٔ ۸dp.
            Column(Modifier.width(308.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PGRingStatCard(
                        label = "RAM Usage", value = "970.87 MB", icon = AppIcon.Memory, modifier = Modifier.weight(1f),
                        fraction = .25f, percent = 25, sub = "of 3.65 GB\n2.78 GB free"
                    )
                    PGRingStatCard(
                        label = "Total Traffic", value = "13.62 TB", icon = AppIcon.Storage, modifier = Modifier.weight(1f),
                        segments = listOf(RingSegment(.05f, Color.Red), RingSegment(.95f, Color.Blue)),
                        centerIcon = AppIcon.Storage, sub = "↓ 826.72 GB\n↑ 12.81 TB"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PGRingStatCard(
                        label = "Disk Usage", value = "107.59 GB", icon = AppIcon.Storage, modifier = Modifier.weight(1f),
                        fraction = .14f, percent = 100, sub = "of 489.12 GB\n381.53 GB free"
                    )
                    PGRingStatCard(
                        label = "CPU Usage", value = "5.2%", icon = AppIcon.Gauge, modifier = Modifier.weight(1f),
                        fraction = .05f, percent = 5, sub = "2 cores"
                    )
                }
            }
        }
        rule.waitForIdle()
        val values = layouts("ring_value"); val subs = layouts("ring_sub")
        assertEquals(4, values.size); assertEquals(4, subs.size)
        values.forEachIndexed { i, l -> assertFalse("مقدارِ کاشی #$i بریده شده (…)", l.isLineEllipsized(0)) }
        subs.forEachIndexed { i, l ->
            for (line in 0 until l.lineCount)
                assertFalse("زیرخطِ کاشی #$i خطِ $line بریده شده (…)", l.isLineEllipsized(line))
            assertTrue("زیرخط بیش از ۲ خط شده", l.lineCount <= 2)
        }
    }
}
