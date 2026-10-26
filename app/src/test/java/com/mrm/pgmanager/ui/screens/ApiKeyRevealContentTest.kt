package com.mrm.pgmanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    private fun render(onDismiss: () -> Unit = {}) {
        val fresh = CreatedApiKey(
            key = fullKey,
            item = PanelApiKey(id = 1, name = "Backup monitor", trimmed = maskedKey)
        )
        rule.setContent { ApiKeyRevealContent(fresh = fresh, onDismiss = onDismiss) }
    }

    @Test
    fun the_full_key_is_shown_with_its_own_label() {
        render()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(fullKey).assertIsDisplayed()
        rule.onNodeWithText("Backup monitor").assertIsDisplayed()
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
        rule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun the_done_button_is_what_closes_the_dialog() {
        var closed = 0
        render(onDismiss = { closed++ })
        rule.onNodeWithText(done).performClick()
        assertEquals("فقط دکمهٔ «تمام» دیالوگ را می‌بندد", 1, closed)
    }
}
