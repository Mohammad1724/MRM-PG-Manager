package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.CountMetric
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * تست‌های قرارداد با API پنل PasarGuard (v5.2.1) روی یک سرورِ محلیِ ساختگی.
 * هدف: مطمئن شویم مسیرها، پارامترهای query و بدنهٔ JSON دقیقاً همان چیزی است
 * که پنل انتظار دارد — بدون نیاز به پنل واقعی.
 */
class PanelApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() = server.shutdown()

    // ── usage ────────────────────────────────────────────────

    @Test fun `trafficUsage sends valid period and encoded start`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{"1":[{"period_start":"2026-08-10T00:00:00Z","total_traffic":100}]}}"""))
        val points = PanelApi.trafficUsage(session)
        val path = server.takeRequest().path!!

        assertTrue(path.startsWith("/api/users/usage"))
        assertTrue("period must be a panel enum", path.contains("period=hour"))
        assertTrue(path.contains("start="))
        // اگر encode نشود، «:» موجود در ISO باعث خطای پنل می‌شود.
        assertFalse(path.substringAfter("start=").contains(":"))
        assertEquals(1, points.size)
        assertEquals(100L, points[0].totalTraffic)
    }

    @Test fun `trafficUsage passes node filter through`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{}}"""))
        PanelApi.trafficUsage(session, StatsRange.LAST_30D, nodeId = 7)
        val path = server.takeRequest().path!!

        assertTrue(path.contains("period=day"))
        assertTrue(path.contains("node_id=7"))
    }

    @Test fun `trafficUsage omits node_id when unset`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{}}"""))
        PanelApi.trafficUsage(session)
        assertFalse(server.takeRequest().path!!.contains("node_id"))
    }

    @Test fun `authorization header is attached`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{}}"""))
        PanelApi.trafficUsage(session)
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    // ── user counts ──────────────────────────────────────────

    @Test fun `userCountMetric targets the counts endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{"1":[{"period_start":"2026-08-10T00:00:00Z","count":66}]}}"""))
        val points = PanelApi.userCountMetric(session, CountMetric.ONLINE, StatsRange.LAST_1H)
        val path = server.takeRequest().path!!

        assertTrue(path.startsWith("/api/users/counts/online"))
        assertTrue("1h window should use minute buckets", path.contains("period=minute"))
        assertEquals(66L, points.single().totalTraffic)
    }

    // ── bulk create ──────────────────────────────────────────

    @Test fun `bulk create with sequence strategy sends username and start_number`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"created":5,"subscription_urls":["a","b","c","d","e"]}"""))
        val result = PanelApi.bulkCreateUsersFromTemplate(
            session, templateId = 3, count = 5, sequential = true, username = "user", startNumber = 10
        )
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertEquals("/api/users/bulk/from_template", request.path)
        assertEquals("POST", request.method)
        assertEquals("sequence", body.getString("strategy"))
        assertEquals("user", body.getString("username"))
        assertEquals(10, body.getInt("start_number"))
        assertEquals(3, body.getInt("user_template_id"))
        assertEquals(5, result.created)
        assertEquals(5, result.subscriptionUrls.size)
    }

    /** پنل صراحتاً می‌گوید در حالتِ random باید username تهی باشد، وگرنه 422. */
    @Test fun `bulk create with random strategy sends null username`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"created":2,"subscription_urls":[]}"""))
        PanelApi.bulkCreateUsersFromTemplate(session, templateId = 1, count = 2, sequential = false)
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("random", body.getString("strategy"))
        assertTrue(body.isNull("username"))
        assertFalse(body.has("start_number"))
    }

    @Test fun `bulk create rejects counts outside panel limits`() {
        listOf(0, -1, 501, 1000).forEach { n ->
            val failed = runCatching {
                runBlocking { PanelApi.bulkCreateUsersFromTemplate(session, 1, n, false) }
            }.isFailure
            assertTrue("count=$n should be rejected client-side", failed)
        }
    }

    // ── templates ────────────────────────────────────────────

    @Test fun `userTemplates makes exactly one request`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":1,"name":"T","data_limit":10,"expire_duration":60}]"""))
        val templates = PanelApi.userTemplates(session)

        assertEquals("/api/user_templates", server.takeRequest().path)
        assertEquals("should not also call /simple", 1, server.requestCount)
        assertEquals(10L, templates.single().dataLimit)
        assertEquals(60L, templates.single().expireDuration)
    }

    // ── login guards ─────────────────────────────────────────

    @Test fun `login rejects cleartext http with an actionable message`() {
        val error = runCatching {
            runBlocking { PanelApi.login("http://panel.example.com", "a", "b") }
        }.exceptionOrNull()

        assertTrue(error != null)
        assertTrue("message should mention https, was: ${error?.message}",
            error!!.message!!.contains("https"))
        assertEquals("no network call should be made", 0, server.requestCount)
    }

    @Test fun `login rejects blank address`() {
        val failed = runCatching { runBlocking { PanelApi.login("   ", "a", "b") } }.isFailure
        assertTrue(failed)
    }
}
