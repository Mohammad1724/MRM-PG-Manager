package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.PanelSupport
import com.mrm.pgmanager.data.model.OnHoldAction
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.SystemStats
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
 * قراردادِ **PasarGuard 5.5.1** — چیزهایی که در v5.5 اضافه یا سخت‌تر شدند و اپ
 * از این نسخه به بعد رویشان حساب می‌کند:
 *
 *  1. `hwid_count` در پاسخِ `GET /api/users` (بدونِ درخواستِ جداگانهٔ HWID)؛
 *  2. هدفِ `inactive` در `/api/users/expired` که `expired_before` را **الزامی** می‌کند؛
 *  3. `GET /api/users/simple` برای جست‌وجوی سبک؛
 *  4. `PUT /api/nodes/order` با بدنهٔ `{"ordered_ids": [...]}`؛
 *  5. `on_hold_timeout_action` در `GET /api/settings/general`؛
 *  6. سقفِ `MAX_ON_HOLD_EXPIRE_DURATION_SECONDS` روی `next_plan.expire`.
 *
 * همه روی MockWebServer اجرا می‌شوند تا شکلِ درخواست/پاسخ دقیقاً همان چیزی باشد که
 * پنل انتظار دارد (بدونِ نیاز به پنلِ واقعی).
 */
class Panel55CompatTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "pg_key_00000000-0000-4000-8000-000000000000", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
        PanelSupport.reset()
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        PanelSupport.reset()
        server.shutdown()
    }

    // ── ۱. hwid_count ─────────────────────────────────────────────────────

    @Test fun `users page parses hwid_count from v5_5 payload`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"users":[
                    {"username":"a","status":"active","used_traffic":0,"data_limit":0,"hwid_count":3},
                    {"username":"b","status":"active","used_traffic":0,"data_limit":0,"hwid_count":0},
                    {"username":"c","status":"active","used_traffic":0,"data_limit":0,"hwid_count":null}
                ],"total":3}"""
            )
        )
        val page = PanelApi.usersPage(
            session,
            com.mrm.pgmanager.data.model.UserQuery(offset = 0, limit = 3)
        ).users
        assertEquals(3, page.size)
        // عددِ واقعی نگه داشته می‌شود…
        assertEquals(3, page[0].hwidCount)
        // …صفر هم یک مقدارِ واقعی است (نه «نامعلوم»)…
        assertEquals(0, page[1].hwidCount)
        // …و null یعنی پنل عدد نداده (نقشِ بدونِ hwids.read یا پنلِ قدیمی).
        assertNull(page[2].hwidCount)
    }

    // ── ۲. پاک‌سازیِ inactive ─────────────────────────────────────────────

    @Test fun `inactive cleanup sends required expired_before`() = runBlocking {
        server.enqueue(MockResponse().setBody("""["ali","sara"]"""))
        val names = PanelApi.cleanupCandidates(session, PanelApi.CLEANUP_INACTIVE, "2026-09-01T00:00:00Z")
        val path = server.takeRequest().path!!

        assertTrue(path.startsWith("/api/users/expired?"))
        assertTrue(path.contains("target=inactive"))
        // پنل `OptionalAwareDatetime` می‌خواهد؛ «:» باید encode شود وگرنه 422.
        assertTrue(path.contains("expired_before=2026-09-01T00%3A00%3A00Z"))
        assertEquals(listOf("ali", "sara"), names)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inactive cleanup without cutoff fails locally instead of asking the panel`() = runBlocking {
        // پنل برای این هدف ۴۲۲ می‌داد؛ حالا پیش از شبکه روشن می‌شود و هیچ درخواستی نمی‌رود.
        PanelApi.cleanupCandidates(session, PanelApi.CLEANUP_INACTIVE, null)
    }

    @Test fun `expired cleanup keeps the old shape`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        PanelApi.cleanupCandidates(session)
        val path = server.takeRequest().path!!
        assertEquals("/api/users/expired?target=expired", path)
    }

    @Test fun `delete inactive cleanup carries cutoff too`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"users":[],"count":0}"""))
        PanelApi.deleteCleanupCandidates(session, PanelApi.CLEANUP_INACTIVE, "2026-09-01T00:00:00Z")
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertTrue(req.path!!.contains("target=inactive"))
        assertTrue(req.path!!.contains("expired_before="))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown cleanup target is rejected locally`() = runBlocking {
        PanelApi.cleanupCandidates(session, "everything", null)
    }

    // ── ۳. users/simple ──────────────────────────────────────────────────

    @Test fun `simple users hits the lightweight endpoint and parses ids`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"users":[{"id":7,"username":"ali"}],"total":1}"""))
        val refs = PanelApi.usersSimple(session, search = "al", limit = 5)
        val path = server.takeRequest().path!!

        assertTrue(path.startsWith("/api/users/simple?"))
        assertTrue(path.contains("limit=5"))
        assertTrue(path.contains("search=al"))
        assertEquals(1, refs.size)
        assertEquals(7L, refs[0].id)
        assertEquals("ali", refs[0].username)
    }

    // ── ۴. ترتیبِ نودها ──────────────────────────────────────────────────

    @Test fun `reorder nodes puts ordered_ids in the body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.reorderNodes(session, listOf(3, 1, 2))
        val req = server.takeRequest()

        assertEquals("PUT", req.method)
        assertEquals("/api/nodes/order", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals(3, body.getJSONArray("ordered_ids").length())
        assertEquals(3, body.getJSONArray("ordered_ids").getInt(0))
        assertEquals(2, body.getJSONArray("ordered_ids").getInt(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reorder with fewer than two nodes is rejected locally`() = runBlocking {
        PanelApi.reorderNodes(session, listOf(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reorder with duplicate ids is rejected locally`() = runBlocking {
        PanelApi.reorderNodes(session, listOf(1, 1))
    }

    // ── ۵. تنظیماتِ عمومی ────────────────────────────────────────────────

    @Test fun `general settings parses on hold timeout action`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"default_method":"chacha20-ietf-poly1305","on_hold_timeout_action":"disable"}"""))
        val general = PanelApi.generalSettings(session)
        val path = server.takeRequest().path!!

        assertEquals("/api/settings/general", path)
        assertEquals(OnHoldAction.DISABLE, general.onHoldTimeoutAction)
    }

    @Test fun `unknown on hold action falls back to activate`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"default_method":"aes-256-gcm","on_hold_timeout_action":"explode"}"""))
        assertEquals(OnHoldAction.ACTIVATE, PanelApi.generalSettings(session).onHoldTimeoutAction)
    }

    // ── ۶. سقفِ next_plan ────────────────────────────────────────────────

    @Test fun `next plan expire is clamped to the panel maximum`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        PanelApi.createUser(
            session = session,
            username = "clamped",
            limitGb = 1.0,
            expireIso = "",
            nextPlan = com.mrm.pgmanager.data.model.NextPlan(
                expireSeconds = PanelApi.MAX_PLAN_SECONDS + 999_999L
            )
        )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(
            PanelApi.MAX_PLAN_SECONDS,
            body.getJSONObject("next_plan").getLong("expire")
        )
    }

    // ── ۷. دروازهٔ نسخه ──────────────────────────────────────────────────

    @Test fun `panel support reads version from system stats`() {
        PanelSupport.updateFrom(SystemStats(version = "5.5.1"))
        assertEquals("5.5.1", PanelSupport.label)
        assertTrue(PanelSupport.supportsInactiveCleanup)
        assertTrue(PanelSupport.supportsNodeOrder)
        assertFalse(PanelSupport.isOutdated)
    }

    @Test fun `old panel is flagged as outdated and hides 5_5 features`() {
        PanelSupport.updateFrom(SystemStats(version = "5.4.1"))
        assertTrue(PanelSupport.isOutdated)
        assertFalse(PanelSupport.supportsInactiveCleanup)
        assertFalse(PanelSupport.supportsNodeOrder)
        assertFalse(PanelSupport.supportsOnHoldAction)
    }

    @Test fun `unknown version neither warns nor hides features`() {
        PanelSupport.updateFrom(SystemStats(version = ""))
        assertFalse(PanelSupport.isOutdated)
        assertTrue(PanelSupport.supportsInactiveCleanup)
    }
}
