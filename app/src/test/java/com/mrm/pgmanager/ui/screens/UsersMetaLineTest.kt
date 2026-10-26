package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.utils.lastSeenShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * خطِ دومِ ردیف‌های کاربر: چیپِ تعدادِ دستگاه (HWID) + آنلاین/آخرین دیدار.
 *
 * ایرادِ گزارش‌شده با اسکرین‌شات: «نمایشِ دستگاه تو یه ردیف نیست؛ یا عقبه یا جلو».
 * علت: چیپ *بعد از* اسلاتِ «آخرین دیدار» می‌آمد و پهنای آن اسلات زبان‌به‌زبان و
 * وضعیت‌به‌وضعیت فرق می‌کرد (آیکونِ وای‌فای ~۱۲dp در برابرِ «۵ دقیقه» ~۵۲dp)، پس لبهٔ
 * چیپ از ردیفی به ردیف دیگر هل داده می‌شد؛ ضمناً جهتِ ردیف، ترتیبِ آیکون/عدد را هم
 * در فارسی برعکسِ انگلیسی می‌کرد.
 *
 * (قاعده‌ها: [docs/CI.md] §۳ — `boundsInRoot` به‌جای `assertIsDisplayed()`، رشته‌ها از
 * پیکربندیِ پیش‌فرضِ میزبان، و میزبانِ با ابعادِ مشخص تا ستون‌های جا‌شده حذف نشوند.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class UsersMetaLineTest {
    @get:Rule val rule = createComposeRule()

    /** `boundsInRoot` پیکسلی است؛ برای آستانه‌های dp به آن ضربِ `density` می‌کنیم. */
    private var density = 1f

    /** آخرین دیدارِ عمداً قدیمی، تا اسلاتِ اولِ ردیفِ دوم *متن* باشد نه آیکون. */
    private val seenAt = "2020-01-01T00:00:00"

    private fun user(id: Long, onlineAt: String?, isOnline: Boolean) = PanelUser(
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
        hwidCount = 3
    )

    private val online = user(1, null, true)
    private val seen = user(2, seenAt, false)

    private fun render(ltr: Boolean) = rule.setContent {
        density = LocalDensity.current.density
        CompositionLocalProvider(
            LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl
        ) {
            Box(Modifier.size(360.dp, 240.dp)) {
                Column {
                    UserMetaLine(online, fontSize = 11.sp, iconSize = 11.dp, badgeCompact = true, badgeIconOnly = true)
                    UserMetaLine(seen, fontSize = 11.sp, iconSize = 11.dp, badgeCompact = true, badgeIconOnly = true)
                }
            }
        }
    }

    /** چیپِ `iconOnly` توضیحِ عددیِ TalkBack را روی آیکون نگه می‌دارد و متنش فقط رقم است. */
    private val badgeIconDesc: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.us_devices_fmt, 3)

    private fun badgeIcons() =
        rule.onAllNodesWithContentDescription(badgeIconDesc).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun badgeNumbers() = rule.onAllNodesWithText("3").fetchSemanticsNodes().map { it.boundsInRoot }

    private fun lastSeenBounds() = rule.onAllNodesWithText(lastSeenShort(seenAt, false))
        .fetchSemanticsNodes().map { it.boundsInRoot }

    @Test
    fun badgeChips_areInOneColumn_inLtr() {
        render(true)
        val icons = badgeIcons()
        assertEquals("دو چیپ انتظار می‌رفت", 2, icons.size)
        assertEquals(
            "آیکونِ چیپ در دو ردیف هم‌راستا نیست (${icons[0].left} در برابرِ ${icons[1].left})",
            icons[0].left, icons[1].left, 0.5f
        )
        // قفلِ LTRِ داخلِ چیپ: آیکون چپِ عدد می‌ماند.
        val number = badgeNumbers().first()
        assertTrue("آیکون (چپ=${icons[0].left}) باید چپِ عدد (چپ=${number.left}) باشد", icons[0].left < number.left)
    }

    @Test
    fun badgeChips_areInOneColumn_inRtl() {
        render(false)
        val texts = badgeNumbers()
        assertEquals(2, texts.size)
        // در RTL لبهٔ لنگ سمت راست است (اولِ خط = راست)؛ چیپ به همان لبه می‌چسبد.
        assertEquals(
            "چیپ در فارسی از ردیفی به ردیف دیگر جابه‌جا شده (${texts[0].right} در برابرِ ${texts[1].right})",
            texts[0].right, texts[1].right, 0.5f
        )
        // همان قفلِ LTR در فارسی هم آیکون را چپِ عدد نگه می‌دارد (برعکسِ ترتیبِ طبیعیِ RTL).
        val icon = badgeIcons().first()
        assertTrue("در RTL هم آیکون باید چپِ عدد بماند", icon.left < texts[0].left)
    }

    /**
     * نه روی‌هم‌افتادگی، و مهم‌تر: اسلاتِ اول واقعاً پهنای متفاوت دارد — وگرنه
     * هم‌راستاییِ دو تستِ بالا چیزی را نمی‌سنجد.
     */
    @Test
    fun badgeAndLastSeenText_doNotOverlap() {
        render(false)
        val icons = badgeIcons()
        val times = lastSeenBounds()
        assertEquals("ردیفِ دوم باید متنِ «آخرین دیدار» داشته باشد", 1, times.size)
        assertTrue(
            "متنِ آخرین دیدار (${times[0].width}px) باید دستِ‌کم ۴dp از آیکونِ وای‌فای (${icons[0].width}px) پهن‌تر باشد",
            times[0].width > icons[0].width + 4f * density
        )
        // در RTL چیپ سمتِ راستِ متن است.
        assertTrue(
            "چیپ روی متن افتاده (چپِ چیپ=${icons[1].left}، راستِ متن=${times[0].right})",
            icons[1].left >= times[0].right - 0.5f
        )
    }

    /** نقشِ بدونِ `hwids.read`: عددی نیست پس چیپی هم نیست — خط نباید بشکند. */
    @Test
    fun lineStillCarriesTheTimestamp_whenPanelOmittedHwidCount() {
        rule.setContent {
            Box(Modifier.size(360.dp, 120.dp)) {
                Column { UserMetaLine(user(3, seenAt, false).copy(hwidCount = null)) }
            }
        }
        assertEquals("با hwidCount=null نباید چیپی دیده شود", 0, badgeIcons().size)
        val time = lastSeenBounds().firstOrNull()
        assertTrue("بدونِ چیپ هم باید زمانِ آخرین دیدار رندر شود", time != null && time.height > 0f)
    }
}
