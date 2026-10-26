package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelSettings
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
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
 * تستِ رفتاریِ **صفحهٔ «تنظیماتِ پنل»**.
 *
 * `PanelSettingsApiTest` شکلِ درخواست/پاسخ را می‌سنجد؛ اینجا سه قراردادی قفل
 * می‌شود که فقط در خودِ صفحه معنا دارند:
 *  ۱. چهار تب واقعاً چهار بخشِ جدا را باز می‌کند و محتوای تبِ قبلی **جایش را
 *     می‌گیرد** (نه اینکه زیرِ تبِ تازه هم بماند)؛
 *  ۲. «ذخیره» تا چیزی عوض نشده غیرفعال است — هر `PUT` روی `SettingsSchema`
 *     سرویس‌های پنل را ری‌استارت می‌کند، پس یک ذخیرهٔ بی‌دلیل یعنی قطعیِ
 *     اختراک برای همهٔ کاربرها. این مهم‌ترین خطِ قرمزِ همین صفحه است؛
 *  ۳. نقشِ فقط‌خواندن هیچ دکمهٔ ذخیره‌ای نمی‌بیند (به‌جایش توضیحِ دلیلش).
 *
 * قاعدهٔ «صفحه با اسپینرِ بی‌پایان مونتاژ نشود» (شرحش در `ApiKeysScreenTest`)
 * اینجا هم رعایت شده: کش با دادهٔ **تازه** پر می‌شود، پس `loading` از همان
 * فریمِ اول false است و `setContent` روی idle گیر نمی‌کند. برای همین هیچ‌وقت
 * «ذخیره» لمس نمی‌شود: `saving=true` اسپینرِ بی‌پایان می‌آورد و تست می‌خوابد —
 * مسیرِ ذخیره در `PanelSettingsApiTest` روی لایهٔ داده قفل شده است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class PanelSettingsScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    private val settingsJson = """
        {"general":{
            "default_method":"chacha20-ietf-poly1305",
            "on_hold_timeout_action":"activate",
            "custom_variables":[{"key":"BRAND","value":"MRM"}]
         },
         "subscription":{
            "url_prefix":"sub","update_interval":12,"support_url":"https://t.me/support",
            "profile_title":"MRM","announce":"","announce_url":"","header_placement":"header",
            "response_headers":[],"rules":[],"applications":[],
            "allow_browser_config":true,"disable_sub_template":false,"randomize_order":false,
            "manual_sub_request":{"links":true,"links_base64":true,"xray":true,"wireguard":false,
                                  "sing_box":true,"clash":true,"clash_meta":true,"outline":false}
         },
         "hwid":{"enabled":true,"forced":false,"require_hwid_for_manual_sub":false,
                 "fallback_limit":2,"min_limit":1,"max_limit":5},
         "notification_settings":{"notify_telegram":false,"notify_discord":false}
        }
    """.trimIndent()

    private fun str(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        PanelApi.allowCleartextLoopbackForTests = true
        PanelCache.clear()
        AdminAccess.reset()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
        PanelCache.put(PanelCache.panelSettingsKey(session.baseUrl), PanelSettings.from(org.json.JSONObject(settingsJson)))
    }

    @After fun tearDown() {
        AdminAccess.reset()
        PanelCache.resetClockForTests()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    private fun installDispatcher(settingsResponse: MockResponse? = null, adminBody: String? = null) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return when {
                    path == "/api/admin" -> adminBody?.let { MockResponse().setBody(it) }
                        ?: MockResponse().setResponseCode(404)
                    path.startsWith("/api/settings") -> settingsResponse ?: MockResponse().setBody(settingsJson)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun render() = rule.setContent {
        PanelSettingsScreen(session = session, onBack = {})
    }

    private fun await(what: String, observed: () -> String, condition: () -> Boolean) {
        try {
            rule.waitUntil(15_000, condition)
        } catch (timeout: Throwable) {
            throw AssertionError("$what محقق نشد؛ دیده‌شده: ${observed()}", timeout)
        }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test fun four_tabs_render_once_with_a_min_touch_target() {
        installDispatcher()
        render()

        val labels = listOf(
            str(R.string.panel_settings_tab_general),
            str(R.string.panel_settings_tab_subscription),
            str(R.string.panel_settings_tab_hwid),
            str(R.string.panel_settings_tab_notifications)
        )
        await("تبِ اول", observed = { "تعداد: ${count(labels[0])}" }) { count(labels[0]) > 0 }
        labels.forEach { label ->
            assertEquals("تبِ «$label» باید یکتا باشد", 1, count(label))
            // متنِ تب داخلِ باکسِ کلیک‌پذیرِ merge‌شده است، پس ارتفاعِ همین
            // گره همان محدودهٔ لمسی است (هدفِ `heightIn(min = 40.dp)`).
            rule.onNodeWithText(label).assertHeightIsAtLeast(40.dp)
        }
        // با کشِ تازه نباید اصلاً درخواستی رفته باشد (قاعدهٔ «یک بار در ۲۰ ثانیه»).
        assertEquals("کشِ تازه نباید بازخوانی راه بیندازد", 0, server.requestCount)
    }

    @Test fun switching_tab_replaces_the_previous_section() {
        installDispatcher()
        render()

        val general = str(R.string.panel_settings_tab_general)
        val subscription = str(R.string.panel_settings_tab_subscription)
        await("تبِ عمومی", observed = { "تعداد: ${count(general)}" }) { count(general) > 0 }
        assertTrue(
            "تبِ عمومی باید فیلدِ رویهٔ Shadowsocks را نشان بدهد",
            count(str(R.string.ps_default_method)) > 0
        )

        rule.onNodeWithText(subscription).performClick()

        await("فیلدِ پیشوندِ URL", observed = { "تعداد: ${count(str(R.string.ps_url_prefix))}" }) {
            count(str(R.string.ps_url_prefix)) > 0
        }
        // بخشِ قبلی باید رفته باشد؛ ماندنش یعنی دو فرم روی هم و ذخیرهٔ اشتباه.
        assertEquals(0, count(str(R.string.ps_default_method)))
    }

    @Test fun save_stays_disabled_until_a_field_actually_changes() {
        installDispatcher()
        render()

        val subscription = str(R.string.panel_settings_tab_subscription)
        await("تبِ اشتراک", observed = { "تعداد: ${count(subscription)}" }) { count(subscription) > 0 }
        rule.onNodeWithText(subscription).performClick()

        val save = str(R.string.ps_save)
        await("دکمهٔ ذخیره", observed = { "تعداد: ${count(save)}" }) { count(save) > 0 }
        // قراردادِ خطِ قرمز: بدونِ تغییر، ذخیره فعال نیست (هر PUT سرویس‌ها را ری‌استارت می‌کند).
        rule.onNodeWithText(save).assertIsNotEnabled()

        val fields = rule.onAllNodes(hasSetTextAction())
        assertTrue("تبِ اشتراک باید فیلدِ متنی داشته باشد", fields.fetchSemanticsNodes().isNotEmpty())
        fields[0].performTextInput("x")

        // خودِ assertion کافی است: `assertIsEnabled` پیش از سنجیدن با فریمِ
        // تازه sync می‌شود و اینجا هیچ انیمیشنِ بی‌پایانی روی صفحه نیست.
        rule.onNodeWithText(save).assertIsEnabled()
    }

    @Test fun stale_cache_is_refetched_instead_of_shown_as_current() {
        // کش «تازه» است؛ ساعت را یک دقیقه جلو می‌بریم تا کهنه شود — همان کاری
        // که در گوشیِ واقعی بعد از DefaultFreshMs اتفاق می‌افتد.
        // `at` در کش با `elapsedRealtime` (در Robolectric عددی کوچک) ثبت شده؛
        // ساعت را به یک مقدارِ بزرگ می‌بریم تا سنِ داده از DefaultFreshMs رد شود.
        PanelCache.clock = { 1_000_000_000L }
        installDispatcher()
        render()

        await("درخواستِ بازخوانی", observed = { "درخواست‌ها: ${server.requestCount}" }) { server.requestCount > 0 }
        // داده نباید وسطِ کار غیب شود: تب‌ها همان اول هم بودند و بعد هم هستند.
        assertEquals(1, count(str(R.string.panel_settings_tab_general)))
    }

    @Test fun read_only_role_gets_no_save_button_only_an_explanation() {
        installDispatcher(
            adminBody = """{"id":1,"username":"ops","role":{"id":2,"name":"Viewer","is_owner":false,
                          "permissions":{"settings":{"read":true,"read_general":true}}}}"""
        )
        runBlocking { AdminAccess.refresh(session, force = true) }
        render()

        val general = str(R.string.panel_settings_tab_general)
        await("تبِ عمومی", observed = { "تعداد: ${count(general)}" }) { count(general) > 0 }
        assertTrue(
            "باید نوشته شود که نقشِ کاربر فقط خواندن است",
            count(str(R.string.ps_readonly)) > 0
        )
        assertEquals("دکمهٔ ذخیره برای نقشِ فقط‌خواندن نباید ساخته شود", 0, count(str(R.string.ps_save)))
        // و هیچ فیلدِ قابلِ ویرایشی هم نباید باشد (همه ردیفِ اطلاعات‌اند).
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }
}
