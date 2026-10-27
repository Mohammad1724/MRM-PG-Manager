package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.mrm.pgmanager.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * جدولِ فشردهٔ «زیرساختِ پنل» (هاست/کور/قالب).
 *
 * سه چیزی که این تست قفل می‌کند، همان سه ایرادی است که کارتِ قبلی داشت:
 *  • لبهٔ چپِ هر ستون باید **از سرتیتر** بیاید، نه از جهتِ خودِ متن — یک
 *    remarkِ فارسی نباید ستونِ بعدی را جابه‌جا کند (دقیقاً همان «بی‌چینشی»).
 *  • سطر باید یک‌خطی و ~۴۴dp بماند، نه ~۱۳۰dp با سه چیپِ روی‌هم‌افتاده.
 *  • اگر فراخوان ستونِ کم بگذارد، «—» می‌بینیم، نه crash.
 *
 * منوی ⋮ عمداً باز نمی‌شود: پنجرهٔ popup در JVM هرگز idle گزارش نمی‌شود
 * (قاعده‌های [docs/CI.md] §۳). خودِ `DropdownMenu` با `expanded = false`
 * هیچ پنجره‌ای نمی‌سازد، پس رندرِ ردیف با actions امن است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class InfraTableAlignmentTest {
    @get:Rule val rule = createComposeRule()

    private val columns = listOf(
        InfraColumn(R.string.col_name, 1f),
        InfraColumn(R.string.col_address, 0.78f),
        InfraColumn(R.string.col_inbound, 0.72f),
        InfraColumn(R.string.col_priority, 0.30f)
    )

    private fun left(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left

    @Test
    fun columnsLineUpWithTheHeader_evenWhenANamesRightToLeft() {
        rule.setContent {
            Column {
                InfraTableHeader(columns)
                InfraTableRow(columns, name = "CF1", cells = listOf("a1.ir:443", "2053", "6"))
                InfraTableRow(
                    columns,
                    name = "📍 اظطزاری 📍 CF2",
                    cells = listOf("45.85.118.78:2053", "—", "7")
                )
            }
        }
        // سرتیتر ← هر دو ردیف؛ اگر روزی کسی وزن‌ها را در دو جا تعریف کند، اینجا می‌شکند.
        val addressEdge = left("Address")
        assertEquals("لبهٔ چپِ ستونِ آدرس (ردیفِ لاتین)", addressEdge, left("a1.ir:443"), 0.6f)
        assertEquals("لبهٔ چپِ ستونِ آدرس (ردیفِ فارسی)", addressEdge, left("45.85.118.78:2053"), 0.6f)
        assertEquals("لبهٔ چپِ ستونِ نام", left("Name"), left("CF1"), 0.6f)
        assertEquals("لبهٔ چپِ ستونِ اولویت", left("Priority"), left("6"), 0.6f)
    }

    @Test
    fun rowStaysOneLineDense_insteadOfThe130dpCard() {
        rule.setContent {
            Box(Modifier.testTag("row")) {
                InfraTableRow(
                    columns,
                    name = "📍 اظطزاری 📍 CF1 ینباندِ پیش‌فرض",
                    cells = listOf("cfcliclip2.iranshop21.ir:443", "VLESS-WS-CDN-2053 · TLS", "6"),
                    dot = Color.Red,
                    actions = listOf(InfraRowAction(R.string.qa_edit) {})
                )
            }
        }
        val h = rule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "ارتفاعِ ردیف $h.dp نباید از ۵۲dp بگذرد (کارتِ قبلی ~۱۳۰dp بود)",
            h <= 52f + 0.5f
        )
    }

    @Test
    fun missingCellsBecomeDashes_notCrashes() {
        rule.setContent { InfraTableRow(columns, name = "solo", cells = listOf("only-one")) }
        rule.onNodeWithText("only-one").assertExists()
        assertEquals(
            "دو ستونِ بی‌مقدار باید «—» بگیرند",
            2,
            rule.onAllNodesWithText("—").fetchSemanticsNodes().size
        )
    }
}
