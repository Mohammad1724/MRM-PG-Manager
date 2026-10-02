package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * قفلِ این ادعا: ترتیبِ `.clip(shape).clickable{}` ناحیهٔ لمس را کوچک نمی‌کند، چون Compose برای هر
 * pointer-input حوزهٔ لمس را خودکار تا minimumTouchTargetSize (۴۸dp) گسترش می‌دهد.
 * (tools/kt-modifier-order.py ۶۳ جا را به همین ترتیب برد؛ این تست پشتوانهٔ آن است.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class HitAreaTest {
    @get:Rule val rule = createComposeRule()

    private var clicks = 0

    private fun render() = rule.setContent {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable { clicks++ }.testTag("t"))
        }
    }

    @Test fun touchJustOutsideA24dpTarget_stillClicks() {
        render()
        // ۲۴dp → گسترش تا ۴۸dp = ۱۲dp از هر طرف؛ ۱۰dp بیرون هنوز داخل است.
        rule.onNodeWithTag("t").performTouchInput { click(Offset(-10.dp.toPx(), 12.dp.toPx())) }
        rule.waitForIdle()
        assertEquals("لمسِ ۱۰dp بیرونِ هدفِ ۲۴dp باید کلیک شود", 1, clicks)
    }

    @Test fun touchFarOutside_doesNotClick() {
        render()
        rule.onNodeWithTag("t").performTouchInput { click(Offset(-30.dp.toPx(), 12.dp.toPx())) }
        rule.waitForIdle()
        assertEquals("لمسِ ۳۰dp بیرون نباید کلیک شود (شاهدِ اینکه آزمونِ بالا بی‌معنی نیست)", 0, clicks)
    }
}
