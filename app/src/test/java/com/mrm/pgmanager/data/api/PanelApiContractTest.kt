package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.CountMetric
import com.mrm.pgmanager.data.model.NextPlan
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
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
        // مسیرهای ورود از baseUrl() می‌گذرند که http را رد می‌کند؛ فقط برای localhostِ تست باز می‌شود.
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    private fun user(id: Long, username: String) = PanelUser(
        id = id, username = username, status = "active", usedTraffic = 0L, dataLimit = 0L, expire = null, createdAt = null
    )

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

    // ── usage یک کاربرِ مشخص ─────────────────────────────────

    @Test fun `userTrafficUsage hits by-id user route with encoded start`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{"42":[{"period_start":"2026-08-10T00:00:00Z","total_traffic":500}]}}"""))
        val points = PanelApi.userTrafficUsage(session, user(42, "ali"), StatsRange.LAST_7D)
        val path = server.takeRequest().path!!

        // مسیرهای username در پنل deprecated هستند؛ باید by-id برویم: /api/user/by-id/{id}/usage
        assertTrue("must use /api/user/by-id/{id}", path.startsWith("/api/user/by-id/42/usage"))
        assertTrue(path.contains("period=day"))
        // «:» موجود در ISO باید encode شده باشد
        assertFalse(path.substringAfter("start=").contains(":"))
        assertEquals(1, points.size)
        assertEquals(500L, points[0].totalTraffic)
    }

    @Test fun `userTrafficUsage falls back to encoded username when id is missing`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{}}"""))
        PanelApi.userTrafficUsage(session, user(0, "a b+c"), StatsRange.LAST_24H)
        val path = server.takeRequest().path!!

        assertFalse("raw space must not reach the panel", path.contains("a b+c"))
        assertTrue(path.startsWith("/api/user/"))
        assertFalse(path.contains("/by-id/"))
    }

    // ── node usage ───────────────────────────────────────────

    @Test fun `nodeUsage groups by node and parses uplink downlink`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"period":"hour","stats":{"1":[{"period_start":"2026-08-10T01:00:00Z","uplink":10,"downlink":90},{"period_start":"2026-08-10T00:00:00Z","uplink":5,"downlink":5}],"2":[{"period_start":"2026-08-10T00:00:00Z","uplink":1000,"downlink":2000}]}}"""))
        val usage = PanelApi.nodeUsage(session, StatsRange.LAST_24H, nodeId = null)
        val path = server.takeRequest().path!!

        assertTrue(path, path.startsWith("/api/node/usage?period=hour&start="))
        assertTrue(path, path.contains("group_by_node=true"))
        assertFalse(path, path.contains("node_id="))
        // پرمصرف‌ترین نود اول
        assertEquals(listOf(2, 1), usage.map { it.nodeId })
        assertEquals(3000L, usage[0].total)
        assertEquals(15L, usage[1].uplink)
        assertEquals(95L, usage[1].downlink)
        // نقاط بر اساس زمان مرتب می‌شوند
        assertEquals(listOf("2026-08-10T00:00:00Z", "2026-08-10T01:00:00Z"), usage[1].points.map { it.timestamp })
        val merged = com.mrm.pgmanager.data.model.NodeUsage.merge(usage)
        assertEquals(listOf(3010L, 100L), merged.map { it.totalTraffic })
    }

    @Test fun `nodeUsage forwards node_id when a node is selected`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{}}"""))
        val usage = PanelApi.nodeUsage(session, StatsRange.LAST_7D, nodeId = 4)
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("period=day") && path.contains("node_id=4"))
        assertTrue(usage.isEmpty())
    }

    // ── online IPs / sub_update ──────────────────────────────

    @Test fun `userOnlineIps flattens the per-node map and skips unreachable nodes`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"nodes":{"1":{"ips":{"10.0.0.2":1,"10.0.0.9":4}},"2":null,"3":{"ips":{}}}}"""))
        val ips = PanelApi.userOnlineIps(session, 42L)
        val req = server.takeRequest()

        assertEquals("/api/node/online_stats/42/ip", req.path)
        assertEquals(listOf("10.0.0.9" to 4, "10.0.0.2" to 1), ips.map { it.ip to it.connections })
        assertTrue(ips.all { it.nodeId == 1 })
    }

    @Test fun `userSubUpdates uses the by-id route and keeps the total count`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"updates":[{"created_at":"2026-09-28T10:00:00Z","user_agent":"v2rayNG/1.9.5","ip":"5.6.7.8","hwid":null},{"created_at":"2026-09-27T10:00:00Z","user_agent":"Happ 3.1","ip":null}],"count":17}"""))
        val list = PanelApi.userSubUpdates(session, 42L, limit = 5, offset = 0)
        val req = server.takeRequest()

        assertEquals("/api/user/by-id/42/sub_update?offset=0&limit=5", req.path)
        assertEquals(17, list.count)
        assertEquals(2, list.updates.size)
        assertEquals("v2rayNG", list.updates[0].client)
        assertEquals("5.6.7.8", list.updates[0].ip)
        assertNull(list.updates[0].hwid)
        assertEquals("Happ", list.updates[1].client)
        assertNull(list.updates[1].ip)
    }

    // ── فهرستِ کاربران: فیلترِ آنلاین سمتِ سرور ──────────────

    @Test fun `usersPage passes online=true only for the online filter`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"users":[],"total":0}"""))
        server.enqueue(MockResponse().setBody("""{"users":[],"total":0}"""))
        PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery(online = true))
        PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery(status = "active"))

        val onlinePath = server.takeRequest().path!!
        val statusPath = server.takeRequest().path!!
        assertTrue(onlinePath, onlinePath.contains("online=true"))
        assertFalse(onlinePath.contains("status="))
        assertFalse(statusPath, statusPath.contains("online="))
        assertTrue(statusPath.contains("status=active"))
    }

    @Test fun `usersPage maps advanced filters to UserListQuery params`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("""{"users":[],"total":0}""")) }
        PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery(expireAfter = "2026-09-29T10:00:00Z", expireBefore = "2026-10-06T10:00:00Z", admin = "reseller one"))
        PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery(noDataLimit = true, noExpire = true, noGroup = true))
        PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery(noDataLimit = false, noExpire = false, noGroup = false, sort = "-online_at"))

        val expiring = server.takeRequest().path!!
        val flags = server.takeRequest().path!!
        val plain = server.takeRequest().path!!
        assertTrue(expiring, expiring.contains("expire_after=2026-09-29T10%3A00%3A00Z"))
        assertTrue(expiring, expiring.contains("expire_before=2026-10-06T10%3A00%3A00Z"))
        assertTrue(expiring, expiring.contains("admin=reseller+one"))
        assertTrue(flags, flags.contains("no_data_limit=true") && flags.contains("no_expire=true") && flags.contains("no_group=true"))
        assertFalse(plain, plain.contains("no_data_limit") || plain.contains("no_expire") || plain.contains("no_group") || plain.contains("expire_after"))
        assertTrue(plain, plain.contains("sort=-online_at"))
    }

    @Test fun `user is online only within the panel's two-minute window`() = runBlocking {
        val fresh = java.time.Instant.now().minusSeconds(60).toString()
        val stale = java.time.Instant.now().minusSeconds(200).toString()
        server.enqueue(MockResponse().setBody(
            """{"users":[{"id":1,"username":"a","status":"active","online_at":"$fresh"},""" +
            """{"id":2,"username":"b","status":"active","online_at":"$stale"},""" +
            """{"id":3,"username":"c","status":"active","online_at":null}],"total":3}"""
        ))
        val page = PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery())
        server.takeRequest()

        assertEquals(listOf(true, false, false), page.users.map { it.isOnline })
    }

    // ── مسیرهای by-id برای عملیاتِ تک‌کاربره ─────────────────

    @Test fun `setDisabled uses by-id route with disabled body`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.setDisabled(session, user(7, "ali"), true)
        val req = server.takeRequest()

        assertEquals("PUT", req.method)
        assertEquals("/api/user/by-id/7/disabled", req.path)
        assertTrue(JSONObject(req.body.readUtf8()).getBoolean("disabled"))
    }

    @Test fun `deleteUser and resetUsage use by-id routes`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.deleteUser(session, user(7, "ali"))
        PanelApi.resetUsage(session, user(7, "ali"))

        val del = server.takeRequest(); val reset = server.takeRequest()
        assertEquals("DELETE", del.method); assertEquals("/api/user/by-id/7", del.path)
        assertEquals("POST", reset.method); assertEquals("/api/user/by-id/7/reset", reset.path)
    }

    // ── on_hold ──────────────────────────────────────────────
    // قاعدهٔ پنل (UserValidator.validate_status): on_hold فقط با on_hold_expire_duration > 0 و بدون expire.

    @Test fun `createUser on_hold sends duration and no expire`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.createUser(session, "ali", 10.0, "2026-12-01", status = "on_hold", onHoldExpireSeconds = 30L * 86_400L, onHoldTimeoutSeconds = 7L * 86_400L)
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("on_hold", body.getString("status"))
        assertEquals(2_592_000L, body.getLong("on_hold_expire_duration"))
        assertEquals(604_800L, body.getLong("on_hold_timeout"))
        assertFalse(body.has("expire"))
    }

    @Test fun `createUser without on_hold keeps the classic active body`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.createUser(session, "ali", 10.0, "2026-12-01")
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("active", body.getString("status"))
        assertTrue(body.has("expire"))
        assertFalse(body.has("on_hold_expire_duration"))
        assertFalse(body.has("on_hold_timeout"))
    }

    @Test fun `createUser ignores on_hold without a positive duration`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.createUser(session, "ali", 10.0, "2026-12-01", status = "on_hold", onHoldExpireSeconds = 0L)
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("active", body.getString("status"))
        assertTrue(body.has("expire"))
        assertFalse(body.has("on_hold_expire_duration"))
    }

    @Test fun `modifyUser to on_hold drops expire and can clear the timeout`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, user(7, "ali"), 5.0, "2026-12-01", status = "on_hold", onHoldExpireSeconds = 86_400L, onHoldTimeoutSeconds = 0L)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())

        assertEquals("PUT", req.method)
        assertEquals("/api/user/by-id/7", req.path)
        assertEquals("on_hold", body.getString("status"))
        assertEquals(86_400L, body.getLong("on_hold_expire_duration"))
        assertEquals(0L, body.getLong("on_hold_timeout"))
        assertFalse(body.has("expire"))
    }

    @Test fun `modifyUser back to active sends status and expire`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, user(7, "ali"), 5.0, "2026-12-01", status = "active")
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("active", body.getString("status"))
        assertTrue(body.has("expire"))
        assertFalse(body.has("on_hold_expire_duration"))
    }

    @Test fun `modifyUser without status never touches the panel status`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, user(7, "ali"), 5.0, "2026-12-01")
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertFalse(body.has("status"))
        assertTrue(body.has("expire"))
    }

    // ── قواعدِ «دست‌نزدن» در ویرایش (crud.modify_user پنل) ─────────────

    @Test fun `modifyUser with null expire leaves the panel expiry untouched`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, user(7, "ali"), 5.0, expireIso = null)
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertFalse(body.has("expire"))
        assertFalse(body.has("status"))
        assertTrue(body.has("data_limit"))
    }

    @Test fun `modifyUser always sends note so an emptied note is cleared`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, user(7, "ali"), 5.0, "2026-12-01", note = "  ")
        val body = JSONObject(server.takeRequest().body.readUtf8())

        assertTrue(body.has("note"))
        assertEquals("", body.getString("note"))
    }

    @Test fun `modifyUser re-sends the existing next plan when the caller passes none`() = runBlocking {
        // پنل اگر next_plan در بدنه نباشد پلنِ بعدیِ موجود را حذف می‌کند.
        val withPlan = user(7, "ali").copy(nextPlan = NextPlan(templateId = 3, dataLimit = 1_000L, expireSeconds = 86_400L, addRemainingTraffic = true))
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, withPlan, 5.0, "2026-12-01")
        val body = JSONObject(server.takeRequest().body.readUtf8())

        val plan = body.getJSONObject("next_plan")
        assertEquals(3, plan.getInt("user_template_id"))
        assertEquals(1_000L, plan.getLong("data_limit"))
        assertEquals(86_400L, plan.getLong("expire"))
        assertTrue(plan.getBoolean("add_remaining_traffic"))

        // ولی پلنِ خالیِ صریح یعنی «پاکش کن».
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, withPlan, 5.0, "2026-12-01", nextPlan = NextPlan())
        val cleared = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(cleared.isNull("next_plan"))

        // کاربرِ بدونِ پلن و بدونِ درخواست → کلید اصلاً فرستاده نمی‌شود.
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUser(session, user(7, "ali"), 5.0, "2026-12-01")
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("next_plan"))
    }

    @Test fun `parseUser reads reset strategy and auto delete`() {
        val u = PanelApi.parseUser(JSONObject("""{"id":5,"username":"m","status":"active","used_traffic":0,"data_limit":0,"expire":null,"data_limit_reset_strategy":"month","auto_delete_in_days":14}"""))
        assertEquals("month", u.dataLimitResetStrategy)
        assertEquals(14, u.autoDeleteDays)

        val bare = PanelApi.parseUser(JSONObject("""{"id":6,"username":"n","status":"active","used_traffic":0,"data_limit":0,"expire":null,"data_limit_reset_strategy":null,"auto_delete_in_days":null}"""))
        assertNull(bare.dataLimitResetStrategy)
        assertNull(bare.autoDeleteDays)
    }

    // ── لینکِ اشتراکِ نسبی (url_prefix خالی) ─────────────────────

    @Test fun `relative subscription urls are resolved against the panel address`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"users":[{"id":1,"username":"a","status":"active","subscription_url":"/sub/tok1"},""" +
            """{"id":2,"username":"b","status":"active","subscription_url":"https://sub.example.com/sub/tok2"}],"total":2}"""
        ))
        val page = PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery())
        server.takeRequest()

        assertEquals("${session.baseUrl}/sub/tok1", page.users[0].subUrl)
        assertEquals("https://sub.example.com/sub/tok2", page.users[1].subUrl)
    }

    @Test fun `absoluteSubUrl handles the edge cases`() {
        assertEquals("https://p.example.com/sub/x", PanelApi.absoluteSubUrl("/sub/x", "https://p.example.com"))
        assertEquals("https://p.example.com/sub/x", PanelApi.absoluteSubUrl("/sub/x", "https://p.example.com/"))
        assertEquals("https://cdn.example.com/sub/x", PanelApi.absoluteSubUrl("//cdn.example.com/sub/x", "https://p.example.com"))
        assertEquals("", PanelApi.absoluteSubUrl("", "https://p.example.com"))
        assertEquals("/sub/x", PanelApi.absoluteSubUrl("/sub/x", null))
    }

    @Test fun `parseUser reads on_hold fields and derives days`() {
        val onHold = PanelApi.parseUser(JSONObject("""{"id":3,"username":"hold","status":"on_hold","used_traffic":0,"data_limit":0,"expire":null,"on_hold_expire_duration":2592000,"on_hold_timeout":"2026-10-15T00:00:00Z"}"""))
        assertEquals(2_592_000L, onHold.onHoldExpireDuration)
        assertEquals("2026-10-15T00:00:00Z", onHold.onHoldTimeout)
        assertEquals(30, onHold.onHoldDays)

        val active = PanelApi.parseUser(JSONObject("""{"id":4,"username":"act","status":"active","used_traffic":0,"data_limit":0,"expire":null,"on_hold_expire_duration":null,"on_hold_timeout":null}"""))
        assertNull(active.onHoldExpireDuration)
        assertNull(active.onHoldTimeout)
        assertNull(active.onHoldDays)
    }

    @Test fun `modifyUserFromTemplate uses by-id route`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyUserFromTemplate(session, user(7, "ali"), templateId = 3)
        val req = server.takeRequest()

        assertEquals("PUT", req.method)
        assertEquals("/api/user/from_template/by-id/7", req.path)
        assertEquals(3, JSONObject(req.body.readUtf8()).getInt("user_template_id"))
    }

    @Test fun `userTrafficUsage merges multiple series into one`() = runBlocking {
        // اگر پنل به تفکیکِ نود پاسخ دهد، نقاطِ هم‌زمان باید جمع شوند نه اینکه یکی گم شود.
        server.enqueue(MockResponse().setBody(
            """{"stats":{"1":[{"period_start":"2026-08-10T00:00:00Z","total_traffic":100}],""" +
            """"2":[{"period_start":"2026-08-10T00:00:00Z","total_traffic":50}]}}"""
        ))
        val points = PanelApi.userTrafficUsage(session, user(42, "ali"))

        assertEquals(1, points.size)
        assertEquals(150L, points[0].totalTraffic)
    }

    @Test fun `userTrafficUsage returns points sorted by time`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"stats":{"1":[{"period_start":"2026-08-12T00:00:00Z","total_traffic":3},""" +
            """{"period_start":"2026-08-10T00:00:00Z","total_traffic":1},""" +
            """{"period_start":"2026-08-11T00:00:00Z","total_traffic":2}]}}"""
        ))
        val points = PanelApi.userTrafficUsage(session, user(42, "ali"))

        assertEquals(listOf(1L, 2L, 3L), points.map { it.totalTraffic })
    }

    @Test fun `userTrafficUsage returns empty list when no stats`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"period":"day","start":"x","end":"y"}"""))
        val points = PanelApi.userTrafficUsage(session, user(42, "ali"))
        assertTrue(points.isEmpty())
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

    @Test fun `login with wrong password is a plain 401 error, not a session expiry`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Incorrect username or password"}"""))
        val error = runCatching { runBlocking { PanelApi.login(server.url("/").toString(), "a", "b") } }.exceptionOrNull()

        assertTrue(error != null)
        assertFalse("login request carries no auth header, so it must not be treated as expiry", error is SessionExpiredException)
        assertTrue(PanelApi.isUnauthorized(error))
        assertTrue("panel detail should be surfaced: ${error!!.message}", error.message!!.contains("Incorrect username"))
    }

    // ── احراز هویت: کلید API و انقضای نشست ───────────────────

    @Test fun `pg_key token is sent as X-Api-Key instead of Bearer`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"version":"5.4.1"}"""))
        val keySession = Session(session.baseUrl, "pg_key_123e4567-e89b-42d3-a456-426614174000", "admin")
        assertTrue(keySession.isApiKey)
        PanelApi.systemStats(keySession)
        val req = server.takeRequest()

        assertEquals("pg_key_123e4567-e89b-42d3-a456-426614174000", req.getHeader("X-Api-Key"))
        assertNull(req.getHeader("Authorization"))
    }

    @Test fun `jwt token is sent as Bearer`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"version":"5.4.1"}"""))
        assertFalse(session.isApiKey)
        PanelApi.systemStats(session)
        val req = server.takeRequest()

        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertNull(req.getHeader("X-Api-Key"))
    }

    @Test fun `401 on an authenticated request raises SessionExpiredException`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Not authenticated"}"""))
        val error = runCatching { runBlocking { PanelApi.systemStats(session) } }.exceptionOrNull()

        assertTrue("was: $error", error is SessionExpiredException)
        assertTrue(PanelApi.isUnauthorized(error))
        // سازگاری با کدهای قدیمی که فقط پیام را نگاه می‌کنند
        assertTrue(error!!.message!!.contains("401"))
    }

    @Test fun `loginWithApiKey validates through GET api-admin and keeps the key as token`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":3,"username":"reseller","role":{"name":"Reseller","is_owner":false,"permissions":{"users":{"read":{"scope":1},"create":true}}}}"""))
        val result = PanelApi.loginWithApiKey(server.url("/").toString(), " pg_key_123e4567-e89b-42d3-a456-426614174000 ")
        val req = server.takeRequest()

        assertEquals("/api/admin", req.path)
        assertEquals("pg_key_123e4567-e89b-42d3-a456-426614174000", req.getHeader("X-Api-Key"))
        assertEquals("reseller", result.username)
        assertEquals("pg_key_123e4567-e89b-42d3-a456-426614174000", result.token)
        assertTrue(result.isApiKey)
    }

    @Test fun `loginWithApiKey rejects malformed key without a network call`() {
        val error = runCatching { runBlocking { PanelApi.loginWithApiKey(server.url("/").toString(), "not-a-key") } }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("Invalid API key"))
        assertEquals(0, server.requestCount)
    }

    @Test fun `loginWithApiKey maps a rejected key to a 401 login failure`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Not authenticated"}"""))
        val error = runCatching { runBlocking { PanelApi.loginWithApiKey(server.url("/").toString(), "pg_key_bad") } }.exceptionOrNull()
        assertTrue(error != null)
        assertTrue(PanelApi.isUnauthorized(error))
    }

    @Test fun `currentAdmin parses owner flag, scoped and boolean permissions`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"id":1,"username":"boss","total_users":12,"role":{"name":"Manager","is_owner":false,""" +
            """"permissions":{"users":{"read":{"scope":2},"update":{"scope":1},"delete":null,"create":true},"groups":{"read":true},"admins":{"read":false}},""" +
            """"limits":{"max_users":50},"features":{"can_use_next_plan":false},"access":{"require_template":true,"allowed_template_ids":[1,2],"allowed_group_ids":null}}}"""
        ))
        val admin = PanelApi.currentAdmin(session)

        assertEquals("/api/admin", server.takeRequest().path)
        assertEquals("boss", admin.username)
        assertFalse(admin.isOwner)
        assertEquals("Manager", admin.roleName)
        assertEquals(2, admin.scope("users", "read"))
        assertEquals(1, admin.scope("users", "update"))
        assertEquals(2, admin.scope("users", "create"))
        assertFalse("null action = denied", admin.can("users", "delete"))
        assertFalse("false action = denied", admin.can("admins", "read"))
        assertTrue(admin.can("groups", "read"))
        assertFalse("missing resource = denied", admin.can("nodes", "read"))
        assertEquals(50, admin.maxUsers)
        assertEquals(12, admin.totalUsers)
        assertTrue(admin.requireTemplate)
        assertEquals(listOf(1, 2), admin.allowedTemplateIds)
        assertNull(admin.allowedGroupIds)
        assertFalse(admin.canUseNextPlan)
        assertTrue(admin.canUseResetStrategy)
    }

    @Test fun `owner bypasses every permission check`() {
        val owner = PanelApi.parseAdminSelf(JSONObject("""{"id":1,"username":"root","role":{"is_owner":true,"permissions":{}}}"""))
        assertTrue(owner.isOwner)
        assertTrue(owner.can("users", "delete"))
        assertEquals(2, owner.scope("nodes", "reconnect"))
    }

    @Test fun `errorDetail surfaces panel detail strings and 422 field errors`() {
        assertEquals("User limit reached", PanelApi.errorDetail("""{"detail":"User limit reached"}"""))
        assertEquals(
            "username: String should have at most 128 characters",
            PanelApi.errorDetail("""{"detail":[{"loc":["body","username"],"msg":"String should have at most 128 characters","type":"string_too_long"}]}""")
        )
        assertEquals("", PanelApi.errorDetail(null))
        assertEquals("plain text", PanelApi.errorDetail("plain text"))
    }

    @Test fun `failed mutation includes readable detail in the message`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Maximum users limit reached"}"""))
        val error = runCatching { runBlocking { PanelApi.resetUsage(session, user(7, "ali")) } }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("400"))
        assertTrue(error.message!!.contains("Maximum users limit reached"))
        assertFalse(error.message!!.contains("{"))
    }
}
