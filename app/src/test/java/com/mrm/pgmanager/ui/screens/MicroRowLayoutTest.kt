package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
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

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private val worst = PanelUser(
        id = 1, username = "AliReza-Roosta", status = "active",
        usedTraffic = (20.27 * 1024 * 1024 * 1024).toLong(),
        dataLimit = 20L * 1024 * 1024 * 1024,
        expire = Instant.now().plus(19, ChronoUnit.DAYS).toString(),
        createdAt = null
    )

    private fun render() = rule.setContent {
        Box(Modifier.width(360.dp)) { LuxuryMicroRow(worst, onClick = {}) }
    }

    // ردیف clickable است و زیرمجموعه‌ها را merge می‌کند؛ تگ‌ها فقط در درخت unmerged دیده می‌شوند.
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
    private fun bounds(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot

    @Test fun actionButtons_areCompact() {
        render()
        for (id in listOf(R.string.us_copy, R.string.us_show_qr)) {
            rule.onNodeWithContentDescription(str(id))
                .assertWidthIsEqualTo(MICRO_ACTION_SIZE).assertHeightIsEqualTo(MICRO_ACTION_SIZE)
        }
        assertTrue("دکمه‌ها باید از ۴۰dp قدیمی کوچک‌تر باشند", MICRO_ACTION_SIZE < 40.dp)
    }

    @Test fun usageBar_isNotSqueezed_andWiderThanName() {
        render()
        node("micro_bar").assertWidthIsAtLeast(90.dp)
        val bar = bounds("micro_bar"); val name = bounds("micro_name")
        assertTrue("نوار (${bar.width}px) باید از ستونِ نام (${name.width}px) عریض‌تر باشد", bar.width > name.width)
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
