package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * رگرسیونِ «دکمه‌ها کار نمی‌کنند» (touch-1): `.clickable` به جلوی زنجیره رفته بود و `pressScale`
 * با detectTapGestures لمس را مصرف می‌کرد؛ clickable بیرونی هرگز فعال نمی‌شد.
 *
 * اینجا با رویدادهای «واقعیِ» pointer (performClick = down+up از مسیرِ کاملِ hit-test) می‌سنجیم:
 *  - هر دو ترتیبِ clickable/pressScale باید کلیک را برسانند؛
 *  - تستِ کنترل: همان الگوی قدیمی (مصرف‌کنندهٔ درونی) باید کلیک را «بلعد» — این ثابت می‌کند
 *    تست حساس است و تشخیصِ ریشهٔ باگ درست بوده.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PressScaleTapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun clickableOuter_pressScaleInner_delivers_click() {
        var clicks = 0
        rule.setContent {
            Box(Modifier.testTag("b").clickable { clicks++ }.size(48.dp).pressScale(0.9f))
        }
        rule.onNodeWithTag("b").performClick()
        rule.waitForIdle()
        assertEquals("clickable بیرونی + pressScale درونی باید کلیک بگیرد", 1, clicks)
    }

    @Test fun pressScaleOuter_clickableInner_delivers_click() {
        var clicks = 0
        rule.setContent {
            Box(Modifier.testTag("b").pressScale(0.9f).clickable { clicks++ }.size(48.dp))
        }
        rule.onNodeWithTag("b").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test fun repeatedTaps_all_delivered_with_pressScale_inner() {
        var clicks = 0
        rule.setContent {
            Box(Modifier.testTag("b").clickable { clicks++ }.size(48.dp).pressScale(0.9f))
        }
        repeat(3) { rule.onNodeWithTag("b").performClick(); rule.waitForIdle() }
        assertEquals(3, clicks)
    }

    /** کنترل: مصرف‌کنندهٔ درونیِ قدیمی (detectTapGestures) باید clickable بیرونی را خاموش کند. */
    @Test fun control_oldConsumingInnerModifier_blocks_outerClick() {
        var clicks = 0
        rule.setContent {
            Box(
                Modifier.testTag("b").clickable { clicks++ }.size(48.dp)
                    .pointerInput(Unit) { detectTapGestures(onPress = { tryAwaitRelease() }) }
            )
        }
        rule.onNodeWithTag("b").performClick()
        rule.waitForIdle()
        assertEquals("الگوی قدیمی باید کلیک را ببلعد (اگر ۱ شد، تشخیصِ ریشه غلط بوده)", 0, clicks)
    }
}
