package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelCustomVariable
import com.mrm.pgmanager.data.model.PanelHwidSection
import com.mrm.pgmanager.data.model.PanelNotificationSection
import com.mrm.pgmanager.data.model.PanelSubscriptionSection
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * قراردادِ «تنظیماتِ پنل» روی سرورِ ساختگی.
 *
 * چیزی که اینجا قفل می‌شود و در عمل گران است: **قاعدهٔ نوشتنِ بخش‌ها**.
 * `PUT /api/settings` بدنه را با `model_dump(exclude_none=True)` روی دیتابیس
 * می‌نویسد و هر بخشِ فرستاده‌شده کامل جایگزین می‌شود. اگر فرمِ ما بخش را ناقص
 * بفرستد، فیلدهای ناشناخته (`rules`، `applications`، `response_headers`،
 * `channels`، توکنِ ماسک‌شده) بی‌صدا به پیش‌فرضِ پنل برمی‌گردند — یعنی ادمین با
 * یک «ذخیره» بخشی از پیکربندی سرور را از دست می‌دهد.
 */
class PanelSettingsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(
            server.url("/").toString().trimEnd('/'),
            "pg_key_00000000-0000-4000-8000-000000000000",
            "admin"
        )
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    /** پاسخِ `GET /api/settings` با همهٔ بخش‌هایی که UI می‌خواند. */
    private fun settingsJson(): String = """
        {
          "general": {"default_method": "aes-256-gcm", "on_hold_timeout_action": "disable", "custom_variables": null},
          "subscription": {
            "url_prefix": "sub", "update_interval": 6, "support_url": "https://t.me/help",
            "profile_title": "MRM", "announce": "hi", "announce_url": "",
            "header_placement": "body_end", "allow_browser_config": false,
            "disable_sub_template": true, "randomize_order": true,
            "manual_sub_request": {"links": true, "xray": false},
            "response_headers": {"X-A": "1", "X-B": "2"},
            "rules": [{"pattern": "Clash", "target": "clash"}],
            "applications": [{"name": "App", "platform": "android", "download_links": []}],
            "custom_variables": [{"key": "BRAND", "value": "MRM"}]
          },
          "hwid": {"enabled": true, "forced": true, "require_hwid_for_manual_sub": false,
                   "fallback_limit": null, "min_limit": 1, "max_limit": 5},
          "notification_settings": {
            "notify_telegram": true, "notify_discord": false,
            "telegram_api_token": "********", "telegram_chat_id": -1001234567890,
            "telegram_topic_id": 7, "max_retries": 4,
            "channels": {"admin": {"telegram_chat_id": 42}}
          }
        }
    """.trimIndent()

    @Test fun `settings parse every section the screen needs`() = runBlocking {
        server.enqueue(MockResponse().setBody(settingsJson()))
        val settings = PanelApi.panelSettings(session)

        assertEquals("/api/settings", server.takeRequest().path)
        assertEquals("aes-256-gcm", settings.general.defaultMethod)
        assertEquals("disable", settings.general.onHoldTimeoutAction)
        // `general.custom_variables` در پاسخ null است؛ پنل آن را در subscription
        // نگه می‌دارد و ادغام‌کردنش کارِ خودِ `/api/settings/general` است. ویو باید
        // همان فهرست را ببیند، وگرنه تبِ «عمومی» همیشه خالی می‌ماند.
        assertEquals(listOf(PanelCustomVariable("BRAND", "MRM")), settings.general.customVariables)

        assertEquals("sub", settings.subscription.urlPrefix)
        assertEquals(6, settings.subscription.updateInterval)
        assertEquals("body_end", settings.subscription.headerPlacement)
        assertFalse(settings.subscription.allowBrowserConfig)
        assertTrue(settings.subscription.randomizeOrder)
        assertFalse(settings.subscription.manualFormats["xray"] ?: true)
        assertEquals(1, settings.subscription.rulesCount)
        assertEquals(1, settings.subscription.applicationsCount)

        assertEquals(1, settings.hwid.minLimit)
        assertEquals(5, settings.hwid.maxLimit)
        assertNull(settings.hwid.fallbackLimit)

        assertTrue(settings.notifications.notifyTelegram)
        // توکنِ ماسک‌شده نباید به‌عنوان «توکنِ تازه» در فرم بنشیند.
        assertEquals("", settings.notifications.telegramApiToken)
        assertTrue(settings.notifications.tokenMasked)
        assertEquals(-1001234567890L, settings.notifications.telegramChatId)
        assertEquals(4, settings.notifications.maxRetries)
    }

    @Test fun `general save sends only that section and keeps variables`() = runBlocking {
        server.enqueue(MockResponse().setBody(settingsJson()))
        val settings = PanelApi.panelSettings(session)

        server.enqueue(MockResponse().setBody(settingsJson()))
        PanelApi.savePanelGeneral(
            session,
            settings.general.copy(onHoldTimeoutAction = "delete")
        )
        server.takeRequest()                                // GET
        val put = server.takeRequest()
        val body = JSONObject(put.body.readUtf8())

        assertEquals("PUT", put.method)
        assertEquals("/api/settings", put.path)
        // فقط یک کلیدِ سطحِ بالا: بخش‌های دیگر نباید بازنویسی شوند.
        assertEquals(setOf("general"), body.keys().asSequence().toSet())
        val general = body.getJSONObject("general")
        assertEquals("delete", general.getString("on_hold_timeout_action"))
        assertEquals("aes-256-gcm", general.getString("default_method"))
        assertEquals("BRAND", general.getJSONArray("custom_variables").getJSONObject(0).getString("key"))
    }

    @Test fun `subscription save preserves fields the form does not know`() = runBlocking {
        server.enqueue(MockResponse().setBody(settingsJson()))
        val settings = PanelApi.panelSettings(session)

        server.enqueue(MockResponse().setBody(settingsJson()))
        PanelApi.savePanelSubscription(
            session,
            settings.subscription.copy(profileTitle = "MRM VPN", manualFormats = mapOf("xray" to false))
        )
        server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("subscription")

        assertEquals("MRM VPN", body.getString("profile_title"))
        // دست‌نخورده‌ها باید برگردند:
        assertEquals(1, body.getJSONArray("rules").length())
        assertEquals(1, body.getJSONArray("applications").length())
        assertEquals("2", body.getJSONObject("response_headers").getString("X-B"))
        assertEquals("BRAND", body.getJSONArray("custom_variables").getJSONObject(0).getString("key"))
        // فرمتِ دستی merge می‌شود، نه اینکه کلِ آبجکت را بازنویسی کند.
        val formats = body.getJSONObject("manual_sub_request")
        assertEquals(false, formats.getBoolean("xray"))
        assertEquals(true, formats.getBoolean("links"))
        // کلیدی که فرم نفرستاده به پیش‌فرضِ پنل (`true`) می‌رسد، نه به «حذف‌شده».
        assertEquals(true, formats.getBoolean("clash"))
    }

    @Test fun `hwid save writes null for an unlimited cap`() = runBlocking {
        server.enqueue(MockResponse().setBody(settingsJson()))
        PanelApi.savePanelHwid(
            session,
            PanelHwidSection(enabled = true, forced = false, maxLimit = null, minLimit = 2)
        )
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("hwid")

        assertEquals(2, body.getInt("min_limit"))
        assertTrue("کلیدِ سقف باید باشد ولی null", body.has("max_limit"))
        assertTrue(body.isNull("max_limit"))
        assertEquals(false, body.getBoolean("forced"))
    }

    @Test fun `masked telegram token is never wiped by an unrelated save`() = runBlocking {
        server.enqueue(MockResponse().setBody(settingsJson()))
        val settings = PanelApi.panelSettings(session)

        server.enqueue(MockResponse().setBody(settingsJson()))
        PanelApi.savePanelNotifications(
            session,
            settings.notifications.copy(maxRetries = 5)
        )
        server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("notification_settings")

        assertEquals(5, body.getInt("max_retries"))
        // توکنِ ماسک‌شده در فرم خالی است؛ نباید `null` بنویسیم وگرنه پنل ۴۲۲ می‌دهد
        // (`notify_telegram` روشن است ولی توکن ندارد). مقدارِ قبلی می‌ماند.
        assertEquals("********", body.getString("telegram_api_token"))
        assertEquals(-1001234567890L, body.getLong("telegram_chat_id"))
        // `channels` ماتریسِ کانال‌هاست و در فرم نیست؛ باید حفظ شود.
        assertEquals(42, body.getJSONObject("channels").getJSONObject("admin").getInt("telegram_chat_id"))
    }

    @Test fun `subscription with no raw body still sends the required rules array`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"subscription":{"url_prefix":"","rules":[]}}"""))
        val settings = PanelApi.panelSettings(session)

        server.enqueue(MockResponse().setBody(settingsJson()))
        PanelApi.savePanelSubscription(
            session,
            PanelSubscriptionSection(
                urlPrefix = "/sub/",
                profileTitle = "",
                manualFormats = mapOf("clash" to false)
            )
        )
        server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("subscription")

        // پنل `rules` را الزامی می‌خواهد (بدونِ پیش‌فرض)؛ نبودش ۴۲۲ می‌شد.
        assertTrue(body.has("rules"))
        // پنل خودش `/`های دو طرفِ url_prefix را می‌بُرد؛ ما هم همان کار را می‌کنیم.
        assertEquals("sub", body.getString("url_prefix"))
        assertEquals(false, body.getJSONObject("manual_sub_request").getBoolean("clash"))
    }
}
