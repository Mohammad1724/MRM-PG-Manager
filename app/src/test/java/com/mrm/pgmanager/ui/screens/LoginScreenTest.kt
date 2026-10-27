package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.theme.ThemeState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * تست‌های رفتاری صفحه ورود — رفع‌های L1/L2/B1/C1 دور دیباگ صفحه لاگین.
 *
 * L1: کلید API سالم از نظر فرمت اما ردشده (۴۰۱) باید پیام «نامعتبر/غیرفعال/منقضی»
 *     بگیرد، نه پیام خطای فرمت.
 * L2: http حتی برای localhost در اعتبارسنجی محلی رد می‌شود (اپ cleartext را کلاً بسته است).
 * B1: کلید Done کیبورد در فیلد رمز همان submit دکمه را اجرا می‌کند.
 * C1: فیلدهای نام کاربری/رمز contentType مخصوص Autofill دارند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class LoginScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private var loggedIn = 0

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun render() = rule.setContent {
        LoginScreen(
            onLoggedIn = { _: Session -> loggedIn++ },
            themeState = ThemeState()
        )
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        PanelApi.allowCleartextLoopbackForTests = true
        loggedIn = 0
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    @Test fun emptySubmit_showsInlineErrorsAndDoesNotLogin() {
        render()
        rule.onNodeWithText(str(R.string.sign_in)).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(str(R.string.login_err_url)).assertExists()
        // دو فیلد نام کاربری و رمز هر دو «الزامی» می‌شوند.
        rule.onAllNodesWithText(str(R.string.login_field_required)).assertCountEquals(2)
        assertEquals(0, loggedIn)
    }

    @Test fun imeDoneOnPasswordSubmitsTheForm_likeTheButton() {
        render()
        // بدون پرکردن چیزی، Done باید همان اعتبارسنجی دکمه را اجرا کند (B1).
        rule.onNodeWithTag("login_password").performImeAction()
        rule.waitForIdle()

        rule.onNodeWithText(str(R.string.login_err_url)).assertExists()
        rule.onAllNodesWithText(str(R.string.login_field_required)).assertCountEquals(2)
        assertEquals(0, loggedIn)
    }

    @Test fun loopbackHttp_isRejectedLocallyWithTheHttpsMessage() {
        // رفتار تولید: فلگ تست خاموش است و حتی loopback هم باید محلی رد شود (L2).
        PanelApi.allowCleartextLoopbackForTests = false
        render()
        rule.onNodeWithTag("login_url").performTextInput("http://127.0.0.1:8080")
        rule.onNodeWithTag("login_username").performTextInput("admin")
        rule.onNodeWithTag("login_password").performTextInput("secret")
        rule.onNodeWithText(str(R.string.sign_in)).performClick()
        rule.waitForIdle()

        // خطا بلافاصله و زیر فیلد آدرس، بدون هیچ درخواست شبکه.
        rule.onNodeWithText(str(R.string.login_err_https)).assertExists()
        assertEquals(0, server.requestCount)
        assertEquals(0, loggedIn)
    }

    /**
     * هر دو فیلدِ فرم موجودند و از طریقِ testTag قابلِ هدف‌گیری‌اند.
     *
     * این تست قبلاً سمتِ `SemanticsProperties.ContentType` را می‌سنجید و کامپایل
     * نمی‌شد: در Compose UI 1.7.x مسیرِ `semantics.contentType` (و
     * `androidx.compose.ui.autofill.ContentType`) **internal** است — همان دلیلی که
     * خودِ `LoginScreen` هم از `Modifier.autofill` استفاده می‌کند. اثرِ autofill
     * فقط با سرویسِ واقعیِ گوشی دیده می‌شود، نه در semanticsِ Robolectric؛ پس
     * تستِ صادقانه همین است: گره‌ها با تگِ درست وجود دارند (بقیهٔ تست‌های این فایل
     * هم روی همین تگ‌ها تکیه دارند).
     */
    @Test fun credentialFields_areTagged() {
        render()
        rule.onNodeWithTag("login_username").assertExists()
        rule.onNodeWithTag("login_password").assertExists()
    }

    @Test fun authenticationModesAndPasswordVisibilityAreAccessible() {
        render()
        rule.onNodeWithText(str(R.string.login_mode_password)).assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        rule.onNodeWithContentDescription(str(R.string.cc_show_password))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithContentDescription(str(R.string.cc_hide_password)).assertExists()
    }

    @Test fun rejectedWellFormedApiKey_showsInvalidKeyMessage_notFormatError() {
        // پنل کلید را با ۴۰۱ رد می‌کند؛ فرمت کلید درست است.
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Not authenticated"}"""))
        render()

        rule.onNodeWithText(str(R.string.login_mode_api_key)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("login_url").performTextInput(server.url("/").toString())
        rule.onNodeWithTag("login_api_key").performTextInput("pg_key_123e4567-e89b-42d3-a456-426614174000")
        rule.onNodeWithText(str(R.string.sign_in)).performClick()

        // L1: پیام «نامعتبر/غیرفعال/منقضی» ظاهر می‌شود و پیام خطای فرمت نباید بیاید.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(str(R.string.login_err_api_key)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(str(R.string.login_err_api_key)).assertExists()
        rule.onNodeWithText(str(R.string.login_err_api_key_format)).assertDoesNotExist()
        assertEquals(1, server.requestCount)
        assertEquals(0, loggedIn)
    }
}
