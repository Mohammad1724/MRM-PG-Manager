package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.CreatedApiKey
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * دیالوگِ «یک‌بار نمایشِ کلید» — تنها جایی که متنِ کاملِ کلید دیده می‌شود.
 *
 * چرا اصلاً یک تستِ جدا: `Dialog` در Robolectric هرگز idle گزارش نمی‌کند
 * (`docs/CI.md` §۳)، پس محتوایش از قاب جدا شد (`ApiKeyRevealContent`) و همین
 * محتوا بدونِ پنجره رندر می‌شود. چیزی که قفل می‌شود قراردادِ پنل است، نه
 * پوستهٔ بصری: متنِ کامل باید همان‌جا باشد (پنل بعد از این پاسخ فقط
 * `api_key_trimed` را نگه می‌دارد) و تنها راهِ بستن، دکمهٔ «تمام» باشد.
 *
 * دو دام که همین فایل در CI خورد و هر دو در همین‌جا مستند می‌مانند:
 *  ۱. رشته‌ها باید با **همان** مکانیزمی خوانده شوند که کامپوزبل می‌خواند
 *     (`RuntimeEnvironment…getString`)؛ ساختنِ `Configuration` با `Locale("fa")`
 *     گره‌ای پیدا نمی‌کند، چون میزبانِ تست با locale پیش‌فرض رندر می‌کند.
 *  ۲. `assertIsDisplayed` در این میزبان اتکاپذیر نیست (ناحیهٔ دیدنی با کرانۀ
 *     پنجره برش می‌خورد)؛ جایش `boundsInRoot` را می‌آوریم — و چون `Column`
 *     فرزندی را که در فضایِ نمانده نمی‌گنجاند اصلاً اندازه نمی‌گیرد، والد
 *     ابعادِ صریح دارد.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ApiKeyRevealContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val fullKey = "pg_key_7f3c9a1b4d2e6f0a5c8b1d4e7f0a3c6b"
    private val maskedKey = "pg_key_7f3c9a1b…3c6b"

    private fun str(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private val title get() = str(R.string.ak_reveal_title)
    private val hint get() = str(R.string.ak_reveal_hint)
    private val done get() = str(R.string.ak_reveal_done)

    @Before
    fun setUp() {
        // «کاهش انیمیشن»: هیچ موجِ ورود/نشانگرِ چرخانی روی صحنه نمی‌ماند،
        // پس waitForIdle هرگز تا ۶۰ ثانیه کش نمی‌آید.
        MotionPrefs.reduceMotion = true
    }

    @After
    fun tearDown() {
        MotionPrefs.reduceMotion = false
    }

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

    /** ارتفاعِ واقعیِ گره؛ «صفر» یعنی چیده نشده، نه اینکه متن عوض شده باشد. */
    private fun heightOf(text: String): Float =
        rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.height

    @Test
    fun the_full_key_is_shown_with_its_own_label() {
        render()
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithText(fullKey).assertExists()
        rule.onNodeWithText("Backup monitor").assertExists()
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
