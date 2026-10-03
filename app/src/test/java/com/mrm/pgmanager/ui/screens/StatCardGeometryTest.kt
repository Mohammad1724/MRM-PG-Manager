package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.LocalThemeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * قفلِ رگرسیونِ فاز ۹ — «کارت‌های آماریِ صفحهٔ کاربران تا ته صفحه کش می‌آمدند و عدد
 * دیده نمی‌شد».
 *
 * ریشه: در فاز ۷.۴ ارتفاعِ قطعیِ `.height(72.dp)` به `.heightIn(min = 72.dp)` تبدیل شد
 * (برای اینکه با fontScale بزرگ، برچسب/عدد بریده نشود). اما داخلِ کارت
 * `Column(Modifier.fillMaxSize(), …SpaceBetween)` بود؛ با ارتفاعِ قطعی بی‌اثر بود، ولی با
 * «حداقلِ ارتفاع» کارت سقفِ فضای والد را می‌گرفت — والد در سربرگِ جمع‌شوندهٔ صفحهٔ
 * کاربران فضای زیادی پیشنهاد می‌دهد — پس کارت کش می‌آمد و عدد با SpaceBetween به ته
 * می‌رفت و زیرِ نوارِ ناوبری گم می‌شد.
 *
 * این تست همان والدِ سخاوتمند را شبیه‌سازی می‌کند: اگر روزی کسی دوباره
 * `fillMaxSize()`/`SpaceBetween` را داخلِ کارت بگذارد، ارتفاع از سقفِ مجاز رد می‌شود و
 * تست می‌شکند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class StatCardGeometryTest {
    @get:Rule val rule = createComposeRule()

    /** سقفِ مجاز: کفِ ۷۲dp + حاشیهٔ متنِ بزرگ؛ هر چیزِ بیشتری یعنی «کش‌آمدن». */
    private val maxCardHeightDp = 110f

    // تراکمِ واقعیِ محیطِ تست؛ از داخلِ کامپوزیشن گرفته می‌شود تا به API تست وابسته نباشیم.
    private var density = 1f

    private fun render() = rule.setContent {
        val theme = LocalThemeState.current
        density = LocalDensity.current.density
        Box(Modifier.fillMaxSize()) {
            // همان‌طور که در سربرگِ UsersScreen است: یک Box تمام‌صفحه که به ستونِ
            // کارت‌ها فضای فراوان پیشنهاد می‌دهد.
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                        StatGlassCard(
                            icon = AppIcon.User, label = "کاربران آنلاین", value = "12",
                            accent = GlassGreen, modifier = Modifier.weight(1f).testTag("card_online")
                        )
                        StatGlassCard(
                            icon = AppIcon.Check, label = "کاربران فعال", value = "73",
                            accent = theme.accentPrimary, modifier = Modifier.weight(1f).testTag("card_active")
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                        StatGlassCard(
                            icon = AppIcon.User, label = "کاربران", value = "80",
                            accent = theme.accentPrimary, modifier = Modifier.weight(1f).testTag("card_total")
                        )
                    }
                }
            }
        }
    }

    private fun heightPx(tag: String): Float =
        rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height

    @Test fun statCards_stayCompact_whenParentOffersTallSpace() {
        render()
        val maxPx = maxCardHeightDp * density
        for (tag in listOf("card_online", "card_active", "card_total")) {
            val h = heightPx(tag)
            assertTrue(
                "$tag با ارتفاعِ ${h / density}dp کش آمده (سقفِ مجاز $maxCardHeightDp" +
                    "dp). یعنی دوباره fillMaxSize/SpaceBetween داخلِ کارت برگشته است.",
                h <= maxPx
            )
            assertTrue("$tag از کفِ ۷۲dp کوچک‌تر است: ${h / density}dp", h >= 72f * density - 1f)
        }
    }

    @Test fun statValues_areVisibleInsideTheirCard() {
        render()
        for ((tag, value) in listOf("card_online" to "12", "card_active" to "73", "card_total" to "80")) {
            val card = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            rule.onNodeWithText(value).assertIsDisplayed()
            val text = rule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "عددِ «$value» بیرونِ کارتِ $tag افتاده (متن: $text، کارت: $card)",
                text.top >= card.top && text.bottom <= card.bottom
            )
        }
    }

    /** برچسب‌ها هم باید دیده شوند — گزارشِ کاربر «هیچ عددی» بود، نه «هیچ متنی». */
    @Test fun statLabels_areVisible() {
        render()
        rule.onNodeWithText("کاربران آنلاین").assertIsDisplayed()
        rule.onNodeWithText("کاربران فعال").assertIsDisplayed()
    }
}
