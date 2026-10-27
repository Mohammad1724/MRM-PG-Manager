package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelSettings
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
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
 * قاعدهٔ «صفحه با اسپینرِ بی‌پایان مونتاژ نشود» و «در فیلدِ متنی تایپ نشود»
 * (شرحِ هر دو در `ApiKeysScreenTest`)
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

    /** بدنهٔ `PUT`هایی که UI فرستاد — برای سنجیدنِ «فقط بخشِ کثیف». */
    private val putBodies = java.util.concurrent.ConcurrentLinkedQueue<String>()

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
        putBodies.clear()
        // «کاهش انیمیشن» روشن: هیچ انیمیشنی (listIntro، pressScale، گذارِ تب)
        // روی صحنه نمی‌ماند، پس waitForIdle هرگز تا ۶۰ ثانیه کش نمی‌آید.
        MotionPrefs.reduceMotion = true
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
        PanelCache.put(PanelCache.panelSettingsKey(session.baseUrl), PanelSettings.from(org.json.JSONObject(settingsJson)))
    }

    @After fun tearDown() {
        AdminAccess.reset()
        MotionPrefs.reduceMotion = false
        PanelCache.resetClockForTests()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    private fun installDispatcher(
        settingsResponse: MockResponse? = null,
        adminBody: String? = null,
        putResponse: String? = null
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return when {
                    // `PUT /api/settings` را خودِ دیسپچر می‌گیرد تا بدنهٔ ارسالیِ UI
                    // سنجیده شود؛ پاسخ پیش‌فرض همان `settingsJson` است (پنل همیشه
                    // `SettingsSchema` کامل را برمی‌گرداند).
                    path == "/api/settings" && request.method == "PUT" -> {
                        putBodies += request.body.readUtf8()
                        MockResponse().setBody(putResponse ?: settingsJson)
                    }
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

    /**
     * شرط‌ها فقط شمارندهٔ سرور می‌خوانند؛ `fetchSemanticsNodes()` خودش
     * `runOnIdle` است و داخلِ `waitUntil` یعنی `waitForIdle` تودرتو (شرحش در
     * `ApiKeysScreenTest`). با کشِ تازه هیچ درخواستی در راه نیست، پس متن‌ها
     * بی‌واسطه سنجیده می‌شوند.
     */
    private fun awaitRequests(atLeast: Int) {
        rule.waitUntil(15_000) { server.requestCount >= atLeast }
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

        val subscription = str(R.string.panel_settings_tab_subscription)
        assertTrue(
            "تبِ عمومی باید فیلدِ رویهٔ Shadowsocks را نشان بدهد",
            count(str(R.string.ps_default_method)) > 0
        )
        assertEquals("تبِ اشتراک نباید از اول باز شده باشد", 0, count(str(R.string.ps_url_prefix)))

        rule.onNodeWithText(subscription).performClick()

        // بخشِ تازه آمده و بخشِ قبلی رفته است؛ ماندنِ قبلی یعنی دو فرم روی هم
        // و ذخیرهٔ اشتباهِ یک تب دیگر.
        assertTrue(count(str(R.string.ps_url_prefix)) > 0)
        assertEquals(0, count(str(R.string.ps_default_method)))
    }

    @Test fun save_stays_disabled_until_a_field_actually_changes() {
        installDispatcher()
        render()

        // همان تبِ اول (عمومی) می‌مانیم: چیپِ رویه‌ها بالای صفحه است و لمسش
        // قطعی است، برعکسِ کنترل‌های پایینِ فهرست که ممکن است بریده شوند.
        val save = str(R.string.ps_save)
        // قراردادِ خطِ قرمز: بدونِ تغییر، ذخیره فعال نیست — هر `PUT` روی
        // SettingsSchema سرویس‌های پنل را ری‌استارت می‌کند، پس «ذخیرهٔ بی‌دلیل»
        // یعنی قطعیِ اختراک برای همه.
        rule.onNodeWithText(save).assertIsNotEnabled()

        // «کثیف‌شدنِ» فرم با *چیپ* سنجیده می‌شود نه تایپ: `performTextInput`
        // فوکوس می‌آورد و مکانکِ چشمک‌زنِ BasicTextField دیگر هیچ‌وقت idle
        // نمی‌شود (شرحش در `ApiKeysScreenTest`).
        rule.onNodeWithText("aes-128-gcm").performClick()

        rule.onNodeWithText(save).assertIsEnabled()
        // «برگرداندن» هم همان لحظه معنا پیدا می‌کند.
        rule.onNodeWithText(str(R.string.ps_revert)).assertIsEnabled()
    }

    @Test fun stale_cache_is_refetched_instead_of_shown_as_current() {
        // کش «تازه» است؛ ساعت را یک دقیقه جلو می‌بریم تا کهنه شود — همان کاری
        // که در گوشیِ واقعی بعد از DefaultFreshMs اتفاق می‌افتد.
        // `at` در کش با `elapsedRealtime` (در Robolectric عددی کوچک) ثبت شده؛
        // ساعت را به یک مقدارِ بزرگ می‌بریم تا سنِ داده از DefaultFreshMs رد شود.
        PanelCache.clock = { 1_000_000_000L }
        installDispatcher()
        render()

        awaitRequests(1)
        // داده نباید وسطِ کار غیب شود: تب‌ها همان اول هم بودند و بعد هم هستند.
        assertEquals(1, count(str(R.string.panel_settings_tab_general)))
    }

    /**
     * نشانهٔ «پاسخِ سرور اعمال شد» کش است، نه گرهٔ semantics: خواندنِ کش در حلقه
     * مجاز است (قاعدهٔ ۲ فقط خواندنِ گره را از داخلِ شرطِ `waitUntil` منع می‌کند)
     * و مسابقهٔ نخ‌ها را حذف می‌کند — `putResponse` دقیقاً همان چیزی است که
     * `persist` بعد از موفقیت در کش می‌گذارد.
     */
    private fun awaitCached(method: String): Boolean {
        val key = PanelCache.panelSettingsKey(session.baseUrl)
        repeat(60) {
            if (PanelCache.get<PanelSettings>(key)?.general?.defaultMethod == method) return true
            rule.waitForIdle()
            Thread.sleep(10)
        }
        return PanelCache.get<PanelSettings>(key)?.general?.defaultMethod == method
    }

    /**
     * مسیرِ ذخیره — تا پیش از ۰٫۱۶٫۰ آزمودنی نبود، چون `saving=true` نشانگرِ
     * بی‌پایان می‌آورد و `waitForIdle` گیر می‌کرد. حالا که همهٔ نشانگرها از
     * `MrmBusyIndicator` می‌آیند و `setUp` «کاهش انیمیشن» را روشن می‌کند، همان
     * نشانگر ساکن است؛ پس این‌جا واقعاً «ذخیره» لمس می‌شود و بدنهٔ `PUT` سنجیده
     * می‌شود. سه چیز قفل می‌شود که هیچ‌کدام در لایهٔ داده معنا ندارد:
     *  ۱. یک `PUT /api/settings` با **فقط** بخشِ تبِ باز (`general`) — فرستادنِ
     *     `subscription` هم‌زمان یعنی بازنویسیِ چیزی که کاربر اصلاً ندیده؛
     *  ۲. فیلدهایی که کاربر دست نزده (`on_hold_timeout_action`،
     *     `custom_variables`) عیناً در بدنه می‌مانند — نبودنشان در پنل یعنی
     *     «برگرد به پیش‌فرض» و متغیرهای زیرساخت پاک می‌شود؛
     *  ۳. بعد از پاسخ، فرم از `SettingsSchema`ِ تازه سِت می‌شود و «ذخیره» دوباره
     *     غیرفعال است — موندنِ حالتِ کثیف یعنی یک `PUTِ` بی‌دلیلِ دیگر، و هر
     *     `PUT` سرویس‌های پنل را ری‌استارت می‌کند.
     */
    @Test fun save_sends_one_put_for_the_dirty_section_only() {
        // پنل تغییر را پذیرفته و `SettingsSchema` کاملِ تازه را برگردانده است.
        installDispatcher(
            putResponse = settingsJson.replace(
                "\"default_method\":\"chacha20-ietf-poly1305\"",
                "\"default_method\":\"aes-128-gcm\""
            )
        )
        render()

        val save = str(R.string.ps_save)
        rule.onNodeWithText(save).assertIsNotEnabled()
        rule.onNodeWithText("aes-128-gcm").performClick()
        rule.onNodeWithText(save).performClick()

        awaitRequests(1)
        val sent = putBodies.toList()
        assertEquals("دقیقاً یک ذخیرهٔ بخش", 1, sent.size)
        val body = org.json.JSONObject(sent.single())
        // `has`/`opt` (نه `keySet()`): پیاده‌سازیِ org.json در کلاسِ تست
        // ممکن است اندروید یا مرجعِ maven باشد، ولی این دو در هر دو یکی‌اند.
        assertTrue(
            "بدنهٔ `PUT` باید فقط بخشِ تبِ باز را داشته باشد: $body",
            body.has("general") && !body.has("subscription") && !body.has("hwid") &&
                !body.has("notification_settings")
        )
        val general = body.getJSONObject("general")
        assertEquals("aes-128-gcm", general.getString("default_method"))
        assertEquals(
            "فیلدِ دست‌نخورده باید عیناً بماند",
            "activate", general.getString("on_hold_timeout_action")
        )
        assertEquals(
            "متغیرهای سفارشی اگر در بدنه نباشند، در پنل پاک می‌شوند",
            "BRAND", general.getJSONArray("custom_variables").getJSONObject(0).getString("key")
        )
        // «ذخیرهٔ موفق» نباید با GETِ دنبالِ آن همراه باشد: پاسخِ `PUT` خودِ schema کامل است.
        assertEquals(1, server.requestCount)

        assertTrue("پاسخ باید به کش هم برسد (برگشتن به صفحه دادهٔ تازه می‌خواهد)", awaitCached("aes-128-gcm"))
        rule.waitForIdle()
        rule.onNodeWithText(save).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.ps_revert)).assertIsNotEnabled()
    }

    @Test fun read_only_role_gets_no_save_button_only_an_explanation() {
        installDispatcher(
            adminBody = """{"id":1,"username":"ops","role":{"id":2,"name":"Viewer","is_owner":false,
                          "permissions":{"settings":{"read":true,"read_general":true}}}}"""
        )
        runBlocking { AdminAccess.refresh(session, force = true) }
        render()

        assertTrue(
            "باید نوشته شود که نقشِ کاربر فقط خواندن است",
            count(str(R.string.ps_readonly)) > 0
        )
        assertEquals("دکمهٔ ذخیره برای نقشِ فقط‌خواندن نباید ساخته شود", 0, count(str(R.string.ps_save)))
        // و هیچ فیلدِ قابلِ ویرایشی هم نباید باشد (همه ردیفِ اطلاعات‌اند).
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }
}
