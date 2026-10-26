package com.mrm.pgmanager.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.CreatedApiKey
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * دیالوگِ «یک‌بار نمایشِ کلید» — تنها جایی که متنِ کاملِ کلید دیده می‌شود.
 *
 * این تست محتوای دیالوگ را **بدونِ** `Dialog` رندر می‌کند (`ApiKeyRevealContent`)،
 * چون پنجرهٔ دیالوگ در Robolectric هرگز idle گزارش نمی‌کند (`docs/CI.md` §۳).
 * چیزی که قفل می‌شود قراردادِ پنل است، نه پوستهٔ بصری: متنِ کامل باید همان‌جا
 * باشد (پنل بعد از این پاسخ فقط `api_key_trimed` را نگه می‌دارد) و تنها راهِ
 * بستن، دکمهٔ «تمام» باشد.
 *
 * متن‌ها از منابعِ فارسی خوانده می‌شوند و به انگلیسیِ `values/strings.xml` برمی‌گردند؛
 * بدونِ این، تغییرِ یک واژه در رشته، تست را می‌شکست.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ApiKeyRevealContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val fullKey = "pg_key_7f3c9a1b4d2e6f0a5c8b1d4e7f0a3c6b"
    private val maskedKey = "pg_key_7f3c9a1b…3c6b"
    private lateinit var app: Application

    @Before
    fun setUp() {
        // بدونِ «کاهش انیمیشن»، موجِ ورود/اسپینرها روی صحنه می‌مانند و
        // `waitForIdle` برنمی‌گردد.
        MotionPrefs.reduceMotion = true
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        MotionPrefs.reduceMotion = false
    }

    private fun localized(en: String, id: Int): String {
        val config = android.content.res.Configuration(app.resources.configuration)
        config.setLocale(Locale("fa"))
        return runCatching { app.createConfigurationContext(config).resources.getString(id) }
            .getOrDefault(en)
    }

    private val title get() = localized("Copy the key now", R.string.ak_reveal_title)
    private val hint get() = localized("This is the only time the full key is shown.", R.string.ak_reveal_hint)
    private val done get() = localized("Done", R.string.ak_reveal_done)

    /**
     * والدِ اندازه‌دار عمدی است، نه سلیقه: محتوای دیالوگ `Column(Modifier.fillMaxWidth())`
     * است و ارتفاعش را از محتوا می‌گیرد. اگر میزبانِ تست فضای بلندی ندهد، `Column`
     * فرزندی را که در فضایِ باقی‌مانده نمی‌گنجاند **اندازه نمی‌گیرد** — یعنی نه
     * placed می‌شود نه گرهٔ semantics می‌سازد. نتیجهٔ واقعی‌اش در CI همین بود:
     * «گره هست ولی displayed نیست» برای دو `Text` اول و «هیچ گره‌ای پیدا نشد» برای
     * دکمهٔ چهارم. پس این‌جا یک `Box` با ابعادِ صریح می‌گذاریم تا چیدمان مثلِ
     * پنجرهٔ دیالوگِ واقعی باشد؛ `assertIsDisplayed` را هم به کار نمی‌بریم، چون
     * «ناحیهٔ دیدنی» با کرانهٔ *پنجره* بریده می‌شود و پنجرهٔ Robolectric در این
     * میزبان ارتفاعِ قابلِ اتکا ندارد — `boundsInRoot` همان حرف را با عدد می‌زند.
     */
    private fun render(onDismiss: () -> Unit = {}) {
        val fresh = CreatedApiKey(
            key = fullKey,
            item = PanelApiKey(id = 1, name = "Backup monitor", trimmed = maskedKey)
        )
        rule.setContent {
            Box(Modifier.size(width = 400.dp, height = 700.dp)) {
                ApiKeyRevealContent(fresh = fresh, onDismiss = onDismiss)
            }
        }
    }

    /** ارتفاعِ واقعیِ گره — همان چیزی که «صفر شدنِ چیدمان» را از «نبودِ متن» جدا می‌کند. */
    private fun heightOf(text: String): Float =
        rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.height

    @Test
    fun the_full_key_is_shown_with_its_own_label() {
        render()
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithText(fullKey).assertExists()
        rule.onNodeWithText("Backup monitor").assertExists()
        // ارتفاعِ صفر یعنی «چیدمان نشکسته، اصلاً چیده نشده» — همان دامِ CI.
        assertTrue("عنوانِ دیالوگ ارتفاع ندارد: ${heightOf(title)}", heightOf(title) > 0f)
    }

    /**
     * اگر روزی دیالوگ `fresh.item.trimmed` را نشان دهد، کاربر متنِ ماسک‌شده را کپی
     * می‌کند و کلیدش کار نمی‌کند — پس «نشان‌ندادنِ نسخهٔ ماسک‌شده» خودِ سناریو است.
     */
    @Test
    fun the_masked_form_is_not_shown_instead() {
        render()
        rule.onNodeWithText(fullKey).assertExists()
        rule.onNodeWithText(maskedKey).assertDoesNotExist()
    }

    @Test
    fun the_warning_that_this_is_the_only_chance_is_on_screen() {
        render()
        rule.onNodeWithText(hint).assertExists()
        assertTrue("هشدارِ «فقط همین یک‌بار» ارتفاع ندارد", heightOf(hint) > 0f)
    }

    @Test
    fun the_done_button_is_what_closes_the_dialog() {
        var closed = 0
        render(onDismiss = { closed++ })
        rule.onNodeWithText(done).performClick()
        assertEquals("فقط دکمهٔ «تمام» دیالوگ را می‌بندد", 1, closed)
    }
}
