package com.mrm.pgmanager.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * انتخابگرِ چیپ بعد از شکایتِ کاربر از «اسکرولِ افقیِ اصلاً جالب نبودنِ تگِ inbound».
 *
 * سه رفتار تثبیت می‌شود:
 *  • لیستِ بلند تا [ChipSelector] سقفش را رد کند یک چیپِ «+N دیگر» می‌گیرد و همان
 *    جای خودش باز/بسته می‌شود (نه دیالوگ تازه، نه پنجره‌ای که در تست idle نشود).
 *  • اگر انتخابِ فعلی بیرونِ سقف باشد، بازم نمایش داده می‌شود — وگرنه کاربر مقدارِ
 *    ذخیره‌شدۀ خودش را نمی‌بیند.
 *  • چیپ‌ها در عرضِ والد *می‌شکنند*: هیچ چیپی از لبه بیرون نمی‌زند (قبلاً نصفه
 *    بریده می‌شد و باید افقی کشیده می‌شد).
 *
 * (قاعده‌ها: [docs/CI.md] §۳ — رشته‌ها از پیکربندیِ پیش‌فرضِ میزبان، والدِ
 * اندازه‌دار، و `boundsInRoot` به‌جای `assertIsDisplayed()`.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class ChipSelectorFlowTest {
    @get:Rule val rule = createComposeRule()

    private val tags = (0 until 15).map { "T$it" }

    private fun moreLabel(hidden: Int) =
        RuntimeEnvironment.getApplication().getString(R.string.chip_more_fmt, hidden)

    private fun showLess() = RuntimeEnvironment.getApplication().getString(R.string.chip_show_less)

    private fun render(selected: String?) = rule.setContent {
        Box(Modifier.size(300.dp, 500.dp)) {
            ChipSelector(values = tags, labels = tags, selected = selected, onSelect = {})
        }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun longList_isCappedAndHasAMoreChip() {
        render("T0")
        assertTrue("ده چیپِ اول باید دیده شود", count("T0") == 1 && count("T10") == 1)
        assertTrue("چیپِ یازدهم باید پشت «+N دیگر» پنهان باشد", count("T11") == 0)
        rule.onNodeWithText(moreLabel(4)).assertExists()
    }

    @Test
    fun moreChip_expandsAndCollapsesInPlace() {
        render("T0")
        rule.onNodeWithText(moreLabel(4)).performClick()
        assertTrue("بعد از بازکردن باید همهٔ ۱۵ چیپ باشند", count("T14") == 1)
        rule.onNodeWithText(showLess()).assertExists()
        rule.onNodeWithText(showLess()).performClick()
        assertTrue("با بسته‌شدن دوباره باید جمع شود", count("T14") == 0)
    }

    @Test
    fun selectedBeyondTheLimit_staysVisible() {
        render("T13")
        assertTrue("انتخابِ بیرونِ سقف باید جای آخرین چیپِ قابل‌نمایش را بگیرد", count("T13") == 1)
        assertTrue("پس یکی از چیپ‌های اول جایش را می‌دهد", count("T10") == 0)
        rule.onNodeWithText(moreLabel(4)).assertExists()
    }

    @Test
    fun chips_wrapInsideTheHostWidth() {
        render("T0")
        val tops = mutableSetOf<Float>()
        for (i in 0..10) {
            val nodes = rule.onAllNodesWithText("T$i").fetchSemanticsNodes()
            if (nodes.isEmpty()) continue
            val bounds = nodes[0].boundsInRoot
            assertTrue("چیپ T$i از لبهٔ راستِ والد بیرون زده (${bounds.right})", bounds.right <= 300f + 0.5f)
            assertTrue("چیپ T$i از لبهٔ چپِ والد بیرون زده (${bounds.left})", bounds.left >= -0.5f)
            tops.add(bounds.top)
        }
        assertTrue("با ۱۱ چیپ در عرضِ ۳۰۰dp باید دستِ‌کم دو خط ساخته شود (خطوط: ${tops.size})", tops.size >= 2)
    }
}
