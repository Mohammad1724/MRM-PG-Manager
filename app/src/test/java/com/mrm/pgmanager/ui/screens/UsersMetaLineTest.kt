package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.utils.lastSeenShort
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * خطِ دومِ ردیف‌های کاربر: چیپِ تعدادِ دستگاه (HWID) + آنلاین/آخرین دیدار.
 *
 * ایرادِ گزارش‌شده با اسکرین‌شات: «نمایشِ دستگاه تو یه ردیف نیست؛ یا عقبه یا جلو».
 * علت: چیپ *بعد از* اسلاتِ «آخرین دیدار» می‌آمد و پهنای آن اسلات زبان‌به‌زبان و
 * وضعیت‌به‌وضعیت فرق می‌کرد (آیکونِ وای‌فای ~۱۲dp در برابرِ «۵ دقیقه» ~۵۲dp و «5m»
 * ~۱۶dp)، پس لبهٔ چیپ از ردیفی به ردیف دیگر هل داده می‌شد.
 *
 * ⚠️ جهتِ چیدمان اینجا دست‌کاری نمی‌شود: پوشیدنِ `LocalLayoutDirection` روی ریشۀ
 * `setContent` باعث شد Compose هیچ‌وقت idle نشود و `AppNotIdleException` به کلاس‌های
 * بعدیِ همان JVM هم مسری شود (CI: ۱۱ تستِ سبزِ دیگر شکست — جدولِ قاعده‌ها در
 * [docs/CI.md] §۳). پس آنچه سنجیده می‌شود **لبهٔ ثابتِ چیپ نسبت به اولِ خط** و
 * **ترتیبِ چیپ/زمان** است؛ قفلِ LTRِ *داخلِ* چیپ (آیکون یک‌طرفِ عدد نماند) در همان
 * `DeviceBadge` انجام می‌شود و از روی میزبانِ JVM قابلِ بازبینی نیست.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class UsersMetaLineTest {
    @get:Rule val rule = createComposeRule()

    /** آخرین دیدارِ عمداً قدیمی، تا اسلاتِ دوم در ردیفِ پایین *متن* باشد نه آیکون. */
    private val seenAt = "2020-01-01T00:00:00"

    private fun user(id: Long, onlineAt: String?, isOnline: Boolean, hwidCount: Int? = 3) = PanelUser(
        id = id,
        username = "u$id",
        status = "active",
        usedTraffic = 0L,
        dataLimit = 0L,
        expire = null,
        createdAt = null,
        onlineAt = onlineAt,
        isOnline = isOnline,
        // هر دو ردیف یک عدد می‌گیرند تا مقایسهٔ لبه‌ها فقط به *محلِ* چیپ حساس باشد،
        // نه به پهنای رقم‌ها.
        hwidCount = hwidCount
    )

    private fun render() = rule.setContent {
        Box(Modifier.width(360.dp).testTag("meta_box")) {
            Column {
                UserMetaLine(user(1, null, true), fontSize = 11.sp, iconSize = 11.dp, badgeCompact = true, badgeIconOnly = true)
                UserMetaLine(user(2, seenAt, false), fontSize = 11.sp, iconSize = 11.dp, badgeCompact = true, badgeIconOnly = true)
            }
        }
    }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    /** چیپِ `iconOnly` توضیحِ TalkBack را روی آیکون نگه می‌دارد؛ متنش فقط رقم است. */
    private fun chipDesc(count: Int) = RuntimeEnvironment.getApplication().getString(R.string.us_devices_fmt, count)

    // boundsInRoot پیکسلی است (نه Dp)؛ آستانه‌ها همین‌جا px‌اند (میزبان mdpi → ۱dp = ۱px).
    private fun chipIcons() =
        rule.onAllNodesWithContentDescription(chipDesc(3)).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun chipNumbers() = rule.onAllNodesWithText("3").fetchSemanticsNodes().map { it.boundsInRoot }

    private fun lastSeen() = rule.onAllNodesWithText(lastSeenShort(seenAt, false)).fetchSemanticsNodes().map { it.boundsInRoot }

    @Test
    fun deviceChips_lineUpInOneColumn() {
        render()
        val icons = chipIcons()
        val numbers = chipNumbers()
        assertTrue("دو چیپ انتظار می‌رفت، ${icons.size} تا دیده شد", icons.size == 2 && numbers.size == 2)
        // قبل از این اصلاح لبهٔ چپِ چیپ به پهنای متنِ «آخرین دیدار» بند بود و این دو
        // عدد چند dp با هم فرق می‌کردند.
        assertTrue(
            "آیکونِ چیپ در دو ردیف هم‌راستا نیست (${icons[0].left} در برابرِ ${icons[1].left})",
            abs(icons[0].left - icons[1].left) <= 0.5f
        )
        assertTrue(
            "عددِ چیپ در دو ردیف هم‌راستا نیست (${numbers[0].left} در برابرِ ${numbers[1].left})",
            abs(numbers[0].left - numbers[1].left) <= 0.5f
        )
        assertTrue(
            "چیپ‌ها روی یک خطِ افقی نیستند (${icons[0].top} در برابرِ ${icons[1].top})",
            abs(icons[0].top - icons[1].top) <= 0.5f
        )
    }

    /**
     * چیپ **اولِ** خط است و متنِ آخرین دیدار بعد از آن — همین ترتیب، لبهٔ چیپ را از
     * پهنای متن بی‌نیاز می‌کند. اگر کسی ترتیب را برگرداند، این تست می‌شکند.
     */
    @Test
    fun deviceChip_precedesTheLastSeenText() {
        render()
        val time = lastSeen()
        assertTrue("ردیفِ دوم باید متنِ «آخرین دیدار» داشته باشد", time.size == 1)
        val number = chipNumbers()[1]
        assertTrue(
            "چیپ بعد از متنِ آخرین دیدار افتاده (راستِ چیپ=${number.right}، چپِ متن=${time[0].left})",
            number.right <= time[0].left + 0.5f
        )
    }

    /**
     * بی‌معنا نبودنِ تستِ هم‌راستایی: اسلاتِ اولِ دو ردیف واقعاً پهنای متفاوت دارد —
     * (در انگلیسی «6y» ممکن است از آیکونِ وای‌فای پهن‌تر نباشد، پس تفاوت را می‌سنجیم
     * نه این‌که کدام بلندتر است؛ همین برای این‌که ترتیبِ قبلیِ چیپ/زمان شکست را
     * تولید کند کافی است).
     */
    @Test
    fun firstSlotWidths_differ_soTheAlignmentCheckIsNotVacuous() {
        render()
        val time = lastSeen()
        assertTrue("ردیفِ دوم باید متنِ آخرین دیدار داشته باشد", time.size == 1)
        val wifi = rule.onAllNodesWithContentDescription(str(R.string.online))
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("ردیفِ اول باید آیکونِ وای‌فای داشته باشد، ${wifi.size} تا دیده شد", wifi.size == 1)
        assertTrue(
            "دو اسلات هم‌عرض‌اند (wifi=${wifi[0].width}px، متن=${time[0].width}px) — تستِ هم‌راستایی چیزی را نمی‌سنجد",
            abs(wifi[0].width - time[0].width) > 0.5f
        )
    }

    /** نقشِ بدونِ `hwids.read`: عددی نیست پس چیپی هم نیست — خط نباید بشکند. */
    @Test
    fun lineStillCarriesTheTimestamp_whenPanelOmittedHwidCount() {
        rule.setContent {
            Box(Modifier.width(360.dp)) {
                Column { UserMetaLine(user(3, seenAt, false, hwidCount = null)) }
            }
        }
        val chips = rule.onAllNodesWithContentDescription(chipDesc(3)).fetchSemanticsNodes()
        assertTrue("با hwidCount=null نباید چیپی دیده شود (${chips.size} تا هست)", chips.isEmpty())
        val time = lastSeen()
        assertTrue("بدونِ چیپ هم باید زمانِ آخرین دیدار رندر شود", time.isNotEmpty() && time[0].height > 0f)
    }
}
