package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * هندسهٔ ردیفِ micro روی گوشیِ ۳۶۰dp با بدترین دادهٔ ممکن: نامِ بلند، مصرفِ دو رقمیِ اعشاری، روزِ مانده.
 * ایرادِ گزارش‌شده: فضای خالیِ زیاد بین نام و نوار، دکمه‌های بزرگ، نوارِ له‌شده و متن‌های روی‌هم‌افتاده.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class MicroRowLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    private val worst = PanelUser(
        id = 1, username = "AliReza-Roosta", status = "active",
        usedTraffic = (20.27 * 1024 * 1024 * 1024).toLong(),
        dataLimit = 20L * 1024 * 1024 * 1024,
        expire = Instant.now().plus(19, ChronoUnit.DAYS).toString(),
        createdAt = null
    )

    private var density = 1f

    private fun render() = rule.setContent {
        density = androidx.compose.ui.platform.LocalDensity.current.density
        Box(Modifier.width(360.dp).testTag("row_box")) { LuxuryMicroRow(worst, onClick = {}) }
    }

    // ردیف clickable است و زیرمجموعه‌ها را merge می‌کند؛ تگ‌ها فقط در درخت unmerged دیده می‌شوند.
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
    private fun bounds(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot

    /** کادرِ عنصر از روی شرحِ آن (دکمه‌ها تگِ خودشان را ندارند). */
    private fun boundsByDesc(desc: String) =
        rule.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInRoot

    @Test fun shareButton_isCompactAndReplacesBothActions() {
        render()
        val description = str(R.string.us_share_subscription, worst.username)
        rule.onAllNodesWithContentDescription(description).assertCountEquals(1)
        rule.onNodeWithContentDescription(description)
            .assertWidthIsEqualTo(MICRO_ACTION_SIZE).assertHeightIsEqualTo(MICRO_ACTION_SIZE)
        rule.onAllNodesWithContentDescription(str(R.string.us_copy)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(str(R.string.us_show_qr)).assertCountEquals(0)
        assertTrue("The micro surface should remain compact", MICRO_ACTION_SIZE <= 26.dp)
        assertTrue("The micro surface should stay legible", MICRO_ACTION_SIZE >= 24.dp)
        assertTrue("The compact-list surface should stay compact", COMPACT_ACTION_SIZE <= 30.dp)
    }

    @Test fun oneShareAction_freesHorizontalSpace() {
        render()
        val share = boundsByDesc(str(R.string.us_share_subscription, worst.username))
        val oldActionWidth = 2f * MICRO_ACTION_SIZE.value + 6f
        val newActionWidth = share.width / density
        assertTrue("The share surface must save at least 30dp", oldActionWidth - newActionWidth >= 30f)
        val row = bounds("row_box")
        assertTrue("The share action must remain inside its row", share.left >= row.left && share.right <= row.right)
    }

    /**
     * گزارشِ کاربر: «این قسمت‌ها (ستونِ چک‌باکس + نام) فضای خالیِ زیاد دارند و فضا
     * زیاد اشغال می‌کنند».
     *
     * ریشه: `CheckboxIcon` ظرفِ `padding(11.dp) + size(18.dp)` داشت → ۴۰dp برای یک
     * مربعِ ۱۸dp. آن ۴۰dp بلندترین فرزندِ ردیف بود و ارتفاع را به ۶۴dp می‌برد
     * (۴۰ + ۲۴ حاشیهٔ کارت) و ۲۲dp هم عرض می‌خورد.
     */
    @Test fun checkboxFootprint_isCompact_nowRowsAreShorter() {
        render()
        val box = rule.onNodeWithContentDescription(str(R.string.cd_select)).fetchSemanticsNode().boundsInRoot
        val wDp = box.width / density; val hDp = box.height / density
        assertTrue("ظرفِ چک‌باکس ${wDp}dp عرض گرفت (سقفِ ۳۰dp، قبلاً ۴۰dp)", wDp <= 30.5f)
        assertTrue("ظرفِ چک‌باکس ${hDp}dp ارتفاع گرفت (سقفِ ۳۰dp، قبلاً ۴۰dp)", hDp <= 30.5f)

        // ۱) ردیف نباید از «بلندترین قطعه + ۲۴dp حاشیهٔ کارت» بلندتر باشد
        //    (اگر کسی دوباره جعبهٔ بزرگی داخلِ ردیف بگذارد، این می‌شکند).
        val nameH = bounds("micro_name").height / density
        val rowH = bounds("row_box").height / density
        val tallest = maxOf(hDp, nameH) + 24f
        assertTrue(
            "ارتفاعِ ردیف ${rowH}dp از «بلندترین قطعه (${maxOf(hDp, nameH)}dp) + حاشیه» بیشتر است",
            rowH <= tallest + 1.5f
        )
        // ۲) عددِ *مطلقِ* ارتفاع اینجا قابلِ اتکا نیست: Robolectric با این qualifier
        //    (mdpi) متریکِ فونتی متفاوت از رندرِ واقعی می‌دهد (اندازه‌گیری شد: ۹۶dp
        //    در برابر ۵۴dp در xhdpi با گرافیکِ NATIVE). قفلِ عددِ واقعی در
        //    MicroRowScreenshotTest است که با xhdpi و همان شرایطِ رندرِ گوشی اجرا می‌شود.
    }

    /** با آزادشدنِ ۱۰dp، ستونِ نام باید زودتر شروع شود (قبلاً ۵۸dp از لبهٔ کارت). */
    @Test fun nameColumn_startsEarlier_afterCheckboxSlimming() {
        render()
        val nameLeftDp = (bounds("micro_name").left - bounds("row_box").left) / density
        assertTrue("ستونِ نام از ${nameLeftDp}dp شروع شد (باید ≤ ۵۰dp باشد)", nameLeftDp <= 50f)
    }

    @Test fun usageBar_keepsItsWidth_andNameGainsSpace() {
        render()
        node("micro_bar").assertWidthIsAtLeast(90.dp)
        val bar = bounds("micro_bar"); val name = bounds("micro_name")
        assertEquals(MICRO_USAGE_WIDTH.value, bar.width / density, 1f)
        assertTrue("The name should benefit from the removed action", name.width / density >= 110f)
    }

    @Test fun usageText_isNeverEllipsized_forWorstCase() {
        render()
        for (tag in listOf("micro_traffic", "micro_remaining")) {
            val results = mutableListOf<TextLayoutResult>()
            node(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertTrue("$tag: نتیجهٔ layout متن در دسترس نیست", results.isNotEmpty())
            assertFalse("$tag بریده شده (…) — حدِ مصرف/روزِ مانده باید کامل دیده شود", results[0].isLineEllipsized(0))
        }
    }

    @Test fun trafficAndRemaining_neverOverlap() {
        render()
        val traffic = bounds("micro_traffic"); val remaining = bounds("micro_remaining")
        assertTrue(
            "متنِ مصرف (راست=${traffic.right}) نباید به «روزِ مانده» (چپ=${remaining.left}) برسد",
            traffic.right <= remaining.left + 0.5f
        )
    }
}
