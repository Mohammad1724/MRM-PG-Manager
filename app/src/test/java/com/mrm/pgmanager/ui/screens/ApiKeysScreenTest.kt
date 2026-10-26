package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.Session
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * تستِ رفتاریِ **صفحهٔ «کلیدهای API»** — چیزی که تستِ قرارداد نمی‌بیند.
 *
 * قرارداد (`ApiKeysApiTest`) می‌گوید مسیرها و بدنه‌ها درست‌اند؛ اینجا سنجیده
 * می‌شود که صفحه همان چیزی را نشان بدهد که ادعا می‌کند:
 *  ۱. ردیفِ هر کلید **فقط** متنِ ماسک‌شده (`api_key_trimmed`) را نشان می‌دهد —
 *     متنِ کامل هیچ‌جا جز دیالوگِ «یک‌بار» نباید دیده شود؛
 *  ۲. حالتِ خالی راهِ ساخت را پیشنهاد می‌کند، و خطای شبکه به «تلاشِ مجدد» با
 *     بازیابیِ واقعی می‌رسد (نه یک صفحهٔ سفید)؛
 *  ۳. مسیرِ کاملِ ساخت → نمایشِ یک‌بارهٔ کلید کار می‌کند؛ اگر جایی بشکند
 *     ادمین کلید را می‌سازد و **نمی‌بیندش**، یعنی کلید از دست رفته است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class ApiKeysScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    private fun str(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    private fun keyJson(id: Int, name: String, trimmed: String, status: String = "active"): String =
        """{"id":$id,"admin_id":1,"name":"$name","note":null,"created_at":"2026-10-01T10:00:00Z",
            "api_key_trimmed":"$trimmed","status":"$status","is_expired":false,
            "expire_date":null,"inherit_permissions":true}"""

    private fun installDispatcher(vararg responses: Pair<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.path.orEmpty().substringBefore('?')}"
                val hit = responses.lastOrNull { it.first == key } ?: responses.firstOrNull { it.first == "*" }
                return hit?.second ?: MockResponse().setResponseCode(404)
            }
        }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        PanelApi.allowCleartextLoopbackForTests = true
        PanelCache.clear()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    private fun render() = rule.setContent {
        ApiKeysScreen(session = session, onBack = {})
    }

    /** `waitUntil`ِ خالی پیامِ بی‌فایده می‌دهد؛ این نسخه «چه دیدیم» را هم می‌آورد. */
    private fun await(what: String, observed: () -> String, condition: () -> Boolean) {
        try {
            rule.waitUntil(15_000, condition)
        } catch (timeout: Throwable) {
            throw AssertionError("$what محقق نشد؛ دیده‌شده: ${observed()}", timeout)
        }
    }

    @Test fun rows_show_masked_key_only() {
        installDispatcher(
            "GET /api/api_keys" to MockResponse().setBody(
                """{"api_keys":[${keyJson(3, "widget", "pg_key_…f7a2")},
                                ${keyJson(4, "ci", "pg_key_…11bc", status = "disabled")}],"total":2}"""
            )
        )
        render()

        val masked = "pg_key_…f7a2"
        await("ردیفِ کلیدِ ماسک‌شده", observed = { "متن‌های یافت‌شده: ${rule.onAllNodesWithText(masked).fetchSemanticsNodes().size}" }) {
            rule.onAllNodesWithText(masked).fetchSemanticsNodes().isNotEmpty()
        }
        // وضعیت‌ها از هم جدا می‌شوند: یکی فعال، یکی غیرفعال.
        assertTrue(rule.onAllNodesWithText(str(R.string.ak_status_active)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText(str(R.string.ak_status_disabled)).fetchSemanticsNodes().isNotEmpty())
        // بدونِ انقضا → «هرگز منقضی نمی‌شود» نه یک خطِ خالی.
        assertTrue(rule.onAllNodesWithText(str(R.string.ak_no_expiry)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun empty_state_offers_create_and_respects_touch_target() {
        installDispatcher("GET /api/api_keys" to MockResponse().setBody("""{"api_keys":[],"total":0}"""))
        render()

        val empty = str(R.string.ak_empty)
        await("حالتِ خالی", observed = { "تعداد: ${rule.onAllNodesWithText(empty).fetchSemanticsNodes().size}" }) {
            rule.onAllNodesWithText(empty).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(str(R.string.ak_empty_hint)).fetchSemanticsNodes().let {
            assertTrue("توضیحِ حالتِ خالی باید دیده شود", it.isNotEmpty())
        }
        // دکمهٔ ساختِ سربرگ باید ناحیهٔ لمسیِ استاندارد داشته باشد (۴۰dp + گسترشِ Compose).
        val addLabel = str(R.string.ak_create)
        rule.onNodeWithContentDescription(addLabel).assertHeightIsAtLeast(40.dp)
        assertEquals(1, rule.onAllNodesWithContentDescription(addLabel).fetchSemanticsNodes().size)
    }

    @Test fun network_error_shows_retry_that_recovers() {
        // اولین پاسخ ۵۰۰، بعدی موفق — «تلاشِ مجدد» باید واقعاً دوباره بکشد.
        var calls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls++
                return if (calls == 1) MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}""")
                else MockResponse().setBody("""{"api_keys":[${keyJson(9, "after-retry", "pg_key_…9d1f")}],"total":1}""")
            }
        }
        render()

        val failed = str(R.string.load_failed)
        await("حالتِ خطا", observed = { "تعداد: ${rule.onAllNodesWithText(failed).fetchSemanticsNodes().size}" }) {
            rule.onAllNodesWithText(failed).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(str(R.string.retry))[0].performClick()
        val masked = "pg_key_…9d1f"
        await("بازیابی پس از تلاشِ مجدد", observed = { "درخواست‌ها: $calls" }) {
            rule.onAllNodesWithText(masked).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("باید دو درخواست رفته باشد (خطا + تلاشِ مجدد)", calls >= 2)
    }

    @Test fun create_reveals_the_full_key_exactly_once() {
        installDispatcher(
            "GET /api/api_keys" to MockResponse().setBody("""{"api_keys":[],"total":0}"""),
            "POST /api/api_key" to MockResponse().setResponseCode(201)
                .setBody("""{"api_key":"pg_key_full_secret",${keyJson(11, "widget", "pg_key_…f7a2")}}""")
        )
        render()

        val addLabel = str(R.string.ak_create)
        await("دکمهٔ ساخت", observed = { "تعداد: ${rule.onAllNodesWithContentDescription(addLabel).fetchSemanticsNodes().size}" }) {
            rule.onAllNodesWithContentDescription(addLabel).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(addLabel).performClick()
        rule.waitForIdle()

        // نام در اولین فیلدِ متنیِ دیالوگ تایپ می‌شود.
        val fields = rule.onAllNodes(hasSetTextAction())
        assertTrue("دیالوگِ ساخت باید فیلدِ متنی داشته باشد", fields.fetchSemanticsNodes().isNotEmpty())
        fields[0].performTextInput("widget")
        rule.waitForIdle()

        // دکمهٔ تأیید برچسبِ جدا دارد («بساز»)، پس تکِ یکتاست — قبلاً عنوان و
        // دکمه هم‌نام بودند و تست نمی‌توانست مطمئن باشد کدام را می‌زند.
        rule.onNodeWithText(str(R.string.ak_create_action)).performClick()

        val secret = "pg_key_full_secret"
        await("نمایشِ یک‌بارهٔ متنِ کاملِ کلید", observed = { "تعداد: ${rule.onAllNodesWithText(secret).fetchSemanticsNodes().size}" }) {
            rule.onAllNodesWithText(secret).fetchSemanticsNodes().isNotEmpty()
        }
        // راهنمای «فقط همین یک‌بار» هم باید با خودِ کلید باشد، وگرنه کاربر
        // فکر می‌کند بعداً هم پیدایش می‌شود.
        assertTrue(rule.onAllNodesWithText(str(R.string.ak_reveal_hint)).fetchSemanticsNodes().isNotEmpty())
    }
}
