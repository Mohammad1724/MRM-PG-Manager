package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.onNodeWithTag
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

    /**
     * قفلِ رگرسیونِ گزارشِ کاربر: «کارتِ RAM از کارتِ CPU بلندتر بود و خطِ
     * «1.27 GB free» ته کارت آویزان می‌شد».
     *
     * دلیلِ ساختاری: در `Row` هر کارت ارتفاع را از محتوای خودش می‌گیرد؛ کارتِ
     * یک‌خطیِ CPU کوتاه‌تر از کارتِ دوخطیِ RAM می‌ماند. راهِ درست،
     * [PGRingStatRow] است (IntrinsicSize.Min) + `fillMaxHeight()` روی کارت‌ها:
     * هر دو به قدِ بلندترین محتوا می‌رسند، نه بیشتر.
     */
    @Test fun rowSiblings_getEqualHeight_viaPGRingStatRow() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Column(Modifier.width(308.dp)) {
                PGRingStatRow(spacing = 8.dp) {
                    PGRingStatCard(
                        label = "CPU Usage", value = "9.3%", icon = AppIcon.Gauge,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("card_cpu"),
                        fraction = .09f, percent = 9, sub = "1 cores"
                    )
                    PGRingStatCard(
                        label = "RAM Usage", value = "614.95 MB", icon = AppIcon.Memory,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("card_ram"),
                        fraction = .32f, percent = 32, sub = "of 1.87 GB\n1.27 GB free"
                    )
                }
            }
        }
        rule.waitForIdle()
        val hCpu = rule.onNodeWithTag("card_cpu").fetchSemanticsNode().boundsInRoot.height
        val hRam = rule.onNodeWithTag("card_ram").fetchSemanticsNode().boundsInRoot.height
        assertEquals("کارت‌های هم‌ردیف باید هم‌قد باشند (CPU=${hCpu / density}dp، RAM=${hRam / density}dp)", hCpu, hRam, 0.5f)
        // کفِ ۸۸dp برای متن + سقفِ ۱۵۰dp: اگر کسی کارت را کشیده باشد، این می‌شکند.
        assertTrue("کارتِ بلندتر از حدِ محتوا شده: ${hRam / density}dp", hRam <= 150f * density)
        assertTrue("کارت از کفِ ۸۸dp کوچک‌تر است: ${hRam / density}dp", hRam >= 88f * density - 1f)
    }

    /** بی [PGRingStatRow] (یعنی همان اشتباهِ قبلی) ارتفاع‌ها *باید* فرق کند — سندِ علتِ باگ. */
    @Test fun withoutRowHelper_siblings_driftApart() {
        rule.setContent {
            Column(Modifier.width(308.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PGRingStatCard(
                        label = "CPU Usage", value = "9.3%", icon = AppIcon.Gauge,
                        modifier = Modifier.weight(1f).testTag("plain_cpu"),
                        fraction = .09f, percent = 9, sub = "1 cores"
                    )
                    PGRingStatCard(
                        label = "RAM Usage", value = "614.95 MB", icon = AppIcon.Memory,
                        modifier = Modifier.weight(1f).testTag("plain_ram"),
                        fraction = .32f, percent = 32, sub = "of 1.87 GB\n1.27 GB free"
                    )
                }
            }
        }
        rule.waitForIdle()
        val a = rule.onNodeWithTag("plain_cpu").fetchSemanticsNode().boundsInRoot.height
        val b = rule.onNodeWithTag("plain_ram").fetchSemanticsNode().boundsInRoot.height
        assertTrue("بدونِ هلپر انتظارِ ناهم‌قدی داریم (cpu=$a ram=$b)", b > a + 1f)
    }
}
