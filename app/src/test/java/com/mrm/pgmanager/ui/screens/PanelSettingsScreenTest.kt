package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
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
 * تستِ رفتاریِ **صفحهٔ «تنظیماتِ پنل»**.
 *
 * تستِ قرارداد (`PanelSettingsApiTest`) فقط شکلِ درخواست/پاسخ را می‌سنجد؛ اینجا
 * سه قراردادِ UX قفل می‌شود که شکستنشان در عمل گران است:
 *  ۱. چهار تب واقعاً چهار بخشِ جدا را باز می‌کنند (نه یک فرمِ بلند)؛
 *  ۲. تا وقتی چیزی عوض نشده، «ذخیره» **غیرفعال** است — هر `PUT` سرویس‌های پنل
 *     را ری‌استارت می‌کند، پس ذخیرهٔ بی‌مورد یک خرابیِ واقعی است؛
 *  ۳. خطای شبکه «تلاشِ مجدد» دارد و بعد از آن صفحه پر می‌شود (نه صفحهٔ سفید).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class PanelSettingsScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    private fun str(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    // حداقلِ معتبرِ پاسخِ `GET /api/settings`: هر گروه فقط فیلدهایی را دارد که
    // صفحه نمایش می‌دهد؛ فیلدهای غایب باید به پیش‌فرض برسند، نه استثنا.
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
        PanelSettingsScreen(session = session, onBack = {})
    }

    private fun await(what: String, observed: () -> String, condition: () -> Boolean) {
        try {
            rule.waitUntil(15_000, condition)
        } catch (timeout: Throwable) {
            throw AssertionError("$what محقق نشد؛ دیده‌شده: ${observed()}", timeout)
        }
    }

    private fun awaitTab(label: String) = await(
        what = "تبِ «$label»",
        observed = { "متن‌های یافت‌شده: ${rule.onAllNodesWithText(label).fetchSemanticsNodes().size}" }
    ) { rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }

    @Test fun four_tabs_are_rendered_with_usable_touch_targets() {
        server.enqueue(MockResponse().setBody(settingsJson))
        render()

        val labels = listOf(
            str(R.string.panel_settings_tab_general),
            str(R.string.panel_settings_tab_subscription),
            str(R.string.panel_settings_tab_hwid),
            str(R.string.panel_settings_tab_notifications)
        )
        awaitTab(labels[0])
        labels.forEach { label ->
            assertEquals("تبِ «$label» باید یکتا باشد", 1, rule.onAllNodesWithText(label).fetchSemanticsNodes().size)
            // متن در نودِ کلیک‌پذیرِ merge‌شده است؛ پس ارتفاعِ سنجیده‌شده همان
            // محدودهٔ لمسیِ تب است (heightIn(min = 40dp)).
            rule.onNodeWithText(label).assertHeightIsAtLeast(40.dp)
        }
    }

    @Test fun switching_tabs_shows_that_section_only() {
        server.enqueue(MockResponse().setBody(settingsJson))
        render()

        val general = str(R.string.panel_settings_tab_general)
        val subscription = str(R.string.panel_settings_tab_subscription)
        awaitTab(general)
        // تبِ عمومی: فیلدِ رویهٔ Shadowsocks.
        assertTrue(
            "تبِ عمومی باید کارتِ پیش‌فرض‌های سرور را نشان بدهد",
            rule.onAllNodesWithText(str(R.string.ps_default_method)).fetchSemanticsNodes().isNotEmpty()
        )

        rule.onNodeWithText(subscription).performClick()
        rule.waitForIdle()

        await(
            what = "فیلدِ پیشوندِ URL در تبِ اشتراک",
            observed = { "تعداد: ${rule.onAllNodesWithText(str(R.string.ps_url_prefix)).fetchSemanticsNodes().size}" }
        ) { rule.onAllNodesWithText(str(R.string.ps_url_prefix)).fetchSemanticsNodes().isNotEmpty() }
        // بخشِ عمومی جایش را به اشتراک داده است، نه اینکه زیرش مانده باشد.
        assertEquals(
            0,
            rule.onAllNodesWithText(str(R.string.ps_default_method)).fetchSemanticsNodes().size
        )
    }

    @Test fun save_stays_disabled_until_a_field_changes() {
        server.enqueue(MockResponse().setBody(settingsJson))
        render()

        val subscription = str(R.string.panel_settings_tab_subscription)
        awaitTab(subscription)
        rule.onNodeWithText(subscription).performClick()
        rule.waitForIdle()

        val save = str(R.string.ps_save)
        await(
            what = "دکمهٔ ذخیره",
            observed = { "تعداد: ${rule.onAllNodesWithText(save).fetchSemanticsNodes().size}" }
        ) { rule.onAllNodesWithText(save).fetchSemanticsNodes().isNotEmpty() }

        // قرارداد: بدونِ تغییر، ذخیره فعال نیست (هر PUT سرویس‌ها را ری‌استارت می‌کند).
        rule.onNodeWithText(save).assertIsNotEnabled()

        // اولین فیلدِ متنیِ همین تب (پیشوندِ URL) را تغییر می‌دهیم.
        val fields = rule.onAllNodes(hasSetTextAction())
        assertTrue("تبِ اشتراک باید فیلدِ متنی داشته باشد", fields.fetchSemanticsNodes().isNotEmpty())
        fields[0].performTextInput("x")
        rule.waitForIdle()

        rule.onNodeWithText(save).assertIsEnabled()
    }

    @Test fun network_error_recovers_through_retry() {
        var calls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls++
                return if (calls == 1) MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}""")
                else MockResponse().setBody(settingsJson)
            }
        }
        render()

        val failed = str(R.string.load_failed)
        await(
            what = "کارتِ خطا",
            observed = { "تعداد: ${rule.onAllNodesWithText(failed).fetchSemanticsNodes().size}" }
        ) { rule.onAllNodesWithText(failed).fetchSemanticsNodes().isNotEmpty() }

        rule.onAllNodesWithText(str(R.string.retry))[0].performClick()
        await(
            what = "بازیابیِ تب‌ها پس از تلاشِ مجدد",
            observed = { "درخواست‌ها: $calls" }
        ) {
            rule.onAllNodesWithText(str(R.string.panel_settings_tab_general)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("باید حداقل دو درخواست رفته باشد", calls >= 2)
    }
}
