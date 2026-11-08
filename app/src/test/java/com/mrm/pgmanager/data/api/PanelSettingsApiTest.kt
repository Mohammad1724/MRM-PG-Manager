package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelCustomVariable
import com.mrm.pgmanager.data.model.PanelSettings
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

/** HTTP contract tests for fresh-read rebasing and 5.5/5.6 settings compatibility. */
class PanelSettingsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "test-token", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    private fun settingsJson(): String = """
        {
          "general": {"default_method":"aes-256-gcm", "on_hold_timeout_action":"disable", "custom_variables":null},
          "subscription": {
            "url_prefix":"sub", "update_interval":6, "support_url":"https://t.me/help",
            "profile_title":"MRM", "announce":"hi", "announce_url":"", "header_placement":"body_end",
            "allow_browser_config":false, "disable_sub_template":true, "randomize_order":true,
            "manual_sub_request":{"links":true,"xray":false}, "response_headers":{"X-A":"1","X-B":"2"},
            "rules":[{"pattern":"Clash","target":"clash"}],
            "applications":[{"name":"App","platform":"android","download_links":[]}],
            "custom_variables":[{"key":"BRAND","value":"MRM"}]
          },
          "hwid":{"enabled":true,"forced":true,"require_hwid_for_manual_sub":false,
                  "fallback_limit":null,"min_limit":1,"max_limit":5},
          "notification_settings":{"notify_telegram":true,"notify_discord":false,
            "telegram_api_token":"********","telegram_chat_id":-1001234567890,
            "telegram_topic_id":7,"max_retries":4,"channels":{"admin":{"telegram_chat_id":42}}}
        }
    """.trimIndent()

    private fun settings560(): JSONObject = JSONObject(settingsJson()).apply {
        getJSONObject("general").put("usage", JSONObject("""{
            "record_node_usage":true,"record_node_stats":false,"reset_usage_clean_chart_data":false,
            "record_outbound_usage":false,"future_usage":"keep"
        }""")).put("node_health", JSONObject("""{
            "fail_threshold":3,"recover_threshold":2,"probe_timeout":5,"max_backoff":60,"future_health":7
        }""")).put("stop_nodes_on_shutdown", true)
        getJSONObject("subscription").put("clients_limit", 10)
        put("cleanup", JSONObject("""{
            "expired_users_retention_days":null,"include_limited_users":false,"usage_history_retention_days":null,
            "node_stats_retention_days":null,"usage_rollup_hourly_after_days":null,
            "usage_rollup_half_day_after_days":null,"usage_rollup_daily_after_days":null,"future_cleanup":"keep"
        }"""))
    }

    private fun enqueue(body: String) = server.enqueue(MockResponse().setBody(body))

    @Test fun `settings parse legacy sections without inventing 5_6 fields`() = runBlocking {
        enqueue(settingsJson())
        val settings = PanelApi.panelSettings(session)
        assertEquals("/api/settings", server.takeRequest().path)
        assertEquals("aes-256-gcm", settings.general.defaultMethod)
        assertEquals("disable", settings.general.onHoldTimeoutAction)
        assertEquals(listOf(PanelCustomVariable("BRAND", "MRM")), settings.general.customVariables)
        assertEquals("sub", settings.subscription.urlPrefix)
        assertEquals(6, settings.subscription.updateInterval)
        assertEquals("body_end", settings.subscription.headerPlacement)
        assertFalse(settings.subscription.allowBrowserConfig)
        assertTrue(settings.subscription.randomizeOrder)
        assertFalse(settings.subscription.manualFormats["xray"] ?: true)
        assertTrue(settings.subscription.manualFormats["clash"] ?: false)
        assertEquals(1, settings.subscription.rulesCount)
        assertEquals(1, settings.subscription.applicationsCount)
        assertNull(settings.subscription.clientsLimit)
        assertNull(settings.runtime)
        assertNull(settings.cleanup)
        assertEquals(1, settings.hwid.minLimit)
        assertEquals(5, settings.hwid.maxLimit)
        assertNull(settings.hwid.fallbackLimit)
        assertTrue(settings.notifications.notifyTelegram)
        assertEquals("", settings.notifications.telegramApiToken)
        assertTrue(settings.notifications.tokenMasked)
        assertEquals(-1001234567890L, settings.notifications.telegramChatId)
        assertEquals(4, settings.notifications.maxRetries)
    }

    @Test fun `general legacy save rebases and does not rewrite unchanged subscription variables`() = runBlocking {
        enqueue(settingsJson())
        val base = PanelApi.panelSettings(session)
        val fresh = JSONObject(settingsJson()).apply {
            getJSONObject("general").put("default_method", "aes-128-gcm")
            getJSONObject("subscription").getJSONArray("custom_variables").getJSONObject(0).put("value", "new")
        }
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("general").put("on_hold_timeout_action", "delete")
        enqueue(response.toString())
        PanelApi.savePanelGeneral(session, base.general, base.general.copy(onHoldTimeoutAction = "delete"))
        server.takeRequest()
        assertEquals("GET", server.takeRequest().method)
        val put = server.takeRequest()
        val body = JSONObject(put.body.readUtf8())
        assertEquals("PUT", put.method)
        assertEquals(setOf("general"), body.keys().asSequence().toSet())
        val general = body.getJSONObject("general")
        assertEquals("delete", general.getString("on_hold_timeout_action"))
        assertEquals("aes-128-gcm", general.getString("default_method"))
        assertFalse(general.has("custom_variables"))
    }

    @Test fun `editing general variables explicitly sends the selected list`() = runBlocking {
        enqueue(settingsJson())
        val base = PanelApi.panelSettings(session)
        enqueue(settingsJson())
        val response = JSONObject(settingsJson())
        response.getJSONObject("general").put("custom_variables", org.json.JSONArray("""[{"key":"BRAND","value":"New brand"}]"""))
        enqueue(response.toString())
        val variables = listOf(PanelCustomVariable("BRAND", "New brand"))
        PanelApi.savePanelGeneral(session, base.general, base.general.copy(customVariables = variables))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(setOf("general"), body.keys().asSequence().toSet())
        val items = body.getJSONObject("general").getJSONArray("custom_variables")
        assertEquals("BRAND", items.getJSONObject(0).getString("key"))
        assertEquals("New brand", items.getJSONObject(0).getString("value"))
    }

    @Test fun `5_6 general save sends only changed fields`() = runBlocking {
        val body = settings560()
        enqueue(body.toString())
        val base = PanelApi.panelSettings(session)
        enqueue(body.toString())
        val response = JSONObject(body.toString())
        response.getJSONObject("general").put("default_method", "aes-128-gcm")
        enqueue(response.toString())
        PanelApi.savePanelGeneral(session, base.general, base.general.copy(defaultMethod = "aes-128-gcm"))
        server.takeRequest(); server.takeRequest()
        val general = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("general")
        assertEquals(setOf("default_method"), general.keys().asSequence().toSet())
        assertEquals("aes-128-gcm", general.getString("default_method"))
    }

    @Test fun `subscription save preserves fresh clients limit and unknown fields`() = runBlocking {
        val first = settings560()
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session)
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("subscription").put("clients_limit", 25)
            .put("future_option", "new")
        fresh.getJSONObject("subscription").getJSONObject("manual_sub_request").put("links", false)
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("subscription").put("profile_title", "New title")
        enqueue(response.toString())
        PanelApi.savePanelSubscription(session, base.subscription, base.subscription.copy(profileTitle = "New title"))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("subscription")
        assertEquals("New title", body.getString("profile_title"))
        assertEquals(25, body.getInt("clients_limit"))
        assertEquals("new", body.getString("future_option"))
        assertFalse(body.getJSONObject("manual_sub_request").getBoolean("links"))
        assertEquals(1, body.getJSONArray("rules").length())
        assertEquals(1, body.getJSONArray("applications").length())
        assertEquals("2", body.getJSONObject("response_headers").getString("X-B"))
        assertEquals("BRAND", body.getJSONArray("custom_variables").getJSONObject(0).getString("key"))
    }

    @Test fun `editing one manual format retains fresh sibling formats`() = runBlocking {
        val first = JSONObject(settingsJson())
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session)
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("subscription").getJSONObject("manual_sub_request")
            .put("links", false).put("future_format", false)
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("subscription").getJSONObject("manual_sub_request").put("clash", false)
        enqueue(response.toString())
        val formats = base.subscription.manualFormats + ("clash" to false)
        PanelApi.savePanelSubscription(session, base.subscription, base.subscription.copy(manualFormats = formats))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("subscription")
        val sent = body.getJSONObject("manual_sub_request")
        assertFalse(sent.getBoolean("clash"))
        assertFalse(sent.getBoolean("xray"))
        assertFalse(sent.getBoolean("links"))
        assertFalse(sent.getBoolean("future_format"))
    }

    @Test fun `subscription with an available raw body but no rules sends an empty rules array`() = runBlocking {
        val first = JSONObject(settingsJson())
        first.getJSONObject("subscription").remove("rules")
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session)
        enqueue(first.toString())
        val response = JSONObject(first.toString())
        response.getJSONObject("subscription").put("url_prefix", "new").put("rules", org.json.JSONArray())
        enqueue(response.toString())
        PanelApi.savePanelSubscription(session, base.subscription, base.subscription.copy(urlPrefix = "/new/"))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("subscription")
        assertEquals("new", body.getString("url_prefix"))
        assertEquals(0, body.getJSONArray("rules").length())
    }

    @Test fun `same subscription field changed on server is a conflict and causes no put`() = runBlocking {
        val first = settings560()
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session)
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("subscription").put("profile_title", "other")
        enqueue(fresh.toString())
        val error = runCatching {
            PanelApi.savePanelSubscription(session, base.subscription, base.subscription.copy(profileTitle = "mine"))
        }.exceptionOrNull()
        assertTrue(error is SettingsEditConflictException)
        assertEquals(2, server.requestCount)
    }

    @Test fun `hwid save sends explicit null while keeping new siblings`() = runBlocking {
        enqueue(settingsJson())
        val base = PanelApi.panelSettings(session)
        val fresh = JSONObject(settingsJson())
        fresh.getJSONObject("hwid").put("future_option", 19)
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("hwid").put("max_limit", JSONObject.NULL).put("min_limit", 2).put("forced", false)
        enqueue(response.toString())
        PanelApi.savePanelHwid(session, base.hwid, base.hwid.copy(maxLimit = null, minLimit = 2, forced = false))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("hwid")
        assertEquals(2, body.getInt("min_limit"))
        assertTrue(body.has("max_limit")); assertTrue(body.isNull("max_limit"))
        assertFalse(body.getBoolean("forced"))
        assertEquals(19, body.getInt("future_option"))
    }

    @Test fun `masked notification token blocks an unrelated full replacement`() = runBlocking {
        enqueue(settingsJson())
        val base = PanelApi.panelSettings(session)
        enqueue(settingsJson())
        val error = runCatching {
            PanelApi.savePanelNotifications(session, base.notifications, base.notifications.copy(maxRetries = 5))
        }.exceptionOrNull()
        assertTrue(error is SettingsMaskedSecretException)
        assertEquals(2, server.requestCount)
    }

    @Test fun `notification save preserves fresh channels when token is visible`() = runBlocking {
        val first = JSONObject(settingsJson())
        first.getJSONObject("notification_settings").put("telegram_api_token", "test-token")
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session)
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("notification_settings").getJSONObject("channels").getJSONObject("admin").put("telegram_chat_id", 55)
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("notification_settings").put("max_retries", 5)
        enqueue(response.toString())
        PanelApi.savePanelNotifications(session, base.notifications, base.notifications.copy(maxRetries = 5))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("notification_settings")
        assertEquals(5, body.getInt("max_retries"))
        assertEquals("test-token", body.getString("telegram_api_token"))
        assertEquals(55, body.getJSONObject("channels").getJSONObject("admin").getInt("telegram_chat_id"))
    }

    @Test fun `missing original section fails closed without a request`() = runBlocking {
        val missing = PanelSubscriptionSection()
        val error = runCatching { PanelApi.savePanelSubscription(session, missing, missing.copy(profileTitle = "new")) }.exceptionOrNull()
        assertTrue(error is SettingsUnavailableException)
        assertEquals(0, server.requestCount)
    }

    @Test fun `runtime save uses nested partial merge for general`() = runBlocking {
        val first = settings560()
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session).runtime!!
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("general").getJSONObject("node_health").put("probe_timeout", 12)
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("general").getJSONObject("usage").put("record_outbound_usage", true)
        enqueue(response.toString())
        PanelApi.savePanelRuntime(session, base, base.copy(recordOutboundUsage = true))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("general")
        assertEquals(setOf("usage"), body.keys().asSequence().toSet())
        assertEquals(setOf("record_outbound_usage"), body.getJSONObject("usage").keys().asSequence().toSet())
        assertTrue(body.getJSONObject("usage").getBoolean("record_outbound_usage"))
    }

    @Test fun `cleanup rebase preserves new values and explicitly clears a nullable setting`() = runBlocking {
        val first = settings560()
        first.getJSONObject("cleanup").put("usage_history_retention_days", 20)
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session).cleanup!!
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("cleanup").put("node_stats_retention_days", 30).put("future_cleanup", "new")
        enqueue(fresh.toString())
        val response = JSONObject(fresh.toString())
        response.getJSONObject("cleanup").put("usage_history_retention_days", JSONObject.NULL)
        enqueue(response.toString())
        PanelApi.savePanelCleanup(session, base, base.copy(usageHistoryRetentionDays = null))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("cleanup")
        assertTrue(body.has("usage_history_retention_days")); assertTrue(body.isNull("usage_history_retention_days"))
        assertEquals(30, body.getInt("node_stats_retention_days"))
        assertEquals("new", body.getString("future_cleanup"))
        assertFalse(body.getBoolean("include_limited_users"))
    }

    @Test fun `combined concurrent cleanup edits that violate rollup order are rejected`() = runBlocking {
        val first = settings560()
        first.getJSONObject("cleanup").put("usage_rollup_hourly_after_days", 5)
            .put("usage_rollup_half_day_after_days", 20)
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session).cleanup!!
        val fresh = JSONObject(first.toString())
        fresh.getJSONObject("cleanup").put("usage_rollup_half_day_after_days", 8)
        enqueue(fresh.toString())
        val error = runCatching { PanelApi.savePanelCleanup(session, base, base.copy(usageRollupHourlyAfterDays = 10)) }.exceptionOrNull()
        assertTrue(error is SettingsEditConflictException)
        assertEquals(2, server.requestCount)
    }

    @Test fun `settings reads do not forward credentials through redirects`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/capture")))
        val error = runCatching { PanelApi.panelSettings(session) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("302"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `successful response that ignores a runtime edit is unconfirmed`() = runBlocking {
        val first = settings560()
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session).runtime!!
        enqueue(first.toString()); enqueue(first.toString())
        val error = runCatching {
            PanelApi.savePanelRuntime(session, base, base.copy(recordOutboundUsage = true))
        }.exceptionOrNull()
        assertTrue(error is SettingsWriteUnconfirmedException)
        assertEquals(3, server.requestCount)
    }

    @Test fun `a server error does not replay settings put`() = runBlocking {
        val first = settings560()
        enqueue(first.toString())
        val base = PanelApi.panelSettings(session)
        enqueue(first.toString())
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0"))
        val error = runCatching {
            PanelApi.savePanelGeneral(session, base.general, base.general.copy(defaultMethod = "aes-128-gcm"))
        }.exceptionOrNull()
        assertTrue(error is SettingsWriteUnconfirmedException)
        assertEquals(3, server.requestCount)
    }
}
