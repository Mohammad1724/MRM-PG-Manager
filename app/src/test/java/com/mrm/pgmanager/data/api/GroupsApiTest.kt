package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.GroupDetail
import com.mrm.pgmanager.data.model.GroupValidation
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
 * قرارداد CRUD گروه‌ها با پنل.
 *
 * مهم‌ترین چیزی که این تست‌ها نگه می‌دارند، تفاوتِ مفرد/جمع در مسیرهاست:
 * پیشوندِ روتر «/api/group» است ولی فهرست روی «s» سوار می‌شود، پس
 * ساخت روی /api/group و فهرست روی /api/groups می‌نشیند. اگر کسی این را
 * یکدست کند، پنل 404 می‌دهد و این تست‌ها جلویش را می‌گیرند.
 */
class GroupsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() = server.shutdown()

    // ── خواندن ───────────────────────────────────────────────

    @Test fun `groupsDetailed hits plural path and parses all fields`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"groups":[
                    {"id":7,"name":"Premium","inbound_tags":["vless-tls","vmess-ws"],"is_disabled":false,"total_users":42},
                    {"id":9,"name":"Trial","inbound_tags":[],"is_disabled":true,"total_users":0}
                ],"total":2}"""
            )
        )
        val groups = PanelApi.groupsDetailed(session)
        val path = server.takeRequest().path!!

        assertTrue("must use plural /api/groups", path.startsWith("/api/groups"))
        assertEquals(2, groups.size)

        val premium = groups[0]
        assertEquals(7, premium.id)
        assertEquals("Premium", premium.name)
        assertEquals(listOf("vless-tls", "vmess-ws"), premium.inboundTags)
        assertFalse(premium.isDisabled)
        assertEquals(42, premium.totalUsers)

        val trial = groups[1]
        assertTrue(trial.inboundTags.isEmpty())
        assertTrue(trial.isDisabled)
    }

    @Test fun `groupsDetailed tolerates missing optional fields`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"groups":[{"id":3,"name":"Bare"}],"total":1}"""))
        val groups = PanelApi.groupsDetailed(session)
        assertEquals(1, groups.size)
        assertEquals(0, groups[0].totalUsers)
        assertFalse(groups[0].isDisabled)
        assertTrue(groups[0].inboundTags.isEmpty())
    }

    @Test fun `groupsDetailed accepts items key as fallback`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":1,"name":"Alpha"}]}"""))
        val groups = PanelApi.groupsDetailed(session)
        assertEquals(1, groups.size)
        assertEquals("Alpha", groups[0].name)
    }

    @Test(expected = IllegalStateException::class)
    fun `groupsDetailed throws on server error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        PanelApi.groupsDetailed(session)
        Unit
    }

    // ── inbound tags ─────────────────────────────────────────

    @Test fun `inboundTags parses a plain string array`() = runBlocking {
        server.enqueue(MockResponse().setBody("""["vless-tls","vmess-ws","trojan"]"""))
        val tags = PanelApi.inboundTags(session)
        val path = server.takeRequest().path!!
        assertEquals("/api/inbounds", path)
        assertEquals(listOf("vless-tls", "vmess-ws", "trojan"), tags)
    }

    @Test fun `inboundTags parses objects with tag field`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"tag":"a"},{"tag":"b"}]"""))
        assertEquals(listOf("a", "b"), PanelApi.inboundTags(session))
    }

    @Test fun `inboundTags returns empty instead of throwing on failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("nope"))
        // نبودِ دسترسی به inboundها نباید صفحهٔ گروه‌ها را از کار بیندازد.
        assertTrue(PanelApi.inboundTags(session).isEmpty())
    }

    @Test fun `inboundTags de-duplicates`() = runBlocking {
        server.enqueue(MockResponse().setBody("""["x","x","y"]"""))
        assertEquals(listOf("x", "y"), PanelApi.inboundTags(session))
    }

    // ── ساخت ─────────────────────────────────────────────────

    @Test fun `createGroup posts to singular path with correct body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":1,"name":"New"}"""))
        PanelApi.createGroup(session, "  New  ", listOf("vless-tls"), isDisabled = false)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("must use singular /api/group", "/api/group", req.path)

        val body = JSONObject(req.body.readUtf8())
        assertEquals("New", body.getString("name"))          // trim شده
        assertEquals(1, body.getJSONArray("inbound_tags").length())
        assertEquals("vless-tls", body.getJSONArray("inbound_tags").getString(0))
        assertFalse(body.getBoolean("is_disabled"))
    }

    @Test(expected = IllegalStateException::class)
    fun `createGroup surfaces server rejection`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"exists"}"""))
        PanelApi.createGroup(session, "Dup", listOf("t"))
        Unit
    }

    // ── ویرایش ───────────────────────────────────────────────

    @Test fun `modifyGroup puts to singular path with id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":5,"name":"Edited"}"""))
        PanelApi.modifyGroup(session, 5, "Edited", listOf("a", "b"), isDisabled = true)

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/group/5", req.path)

        val body = JSONObject(req.body.readUtf8())
        assertEquals("Edited", body.getString("name"))
        assertEquals(2, body.getJSONArray("inbound_tags").length())
        assertTrue(body.getBoolean("is_disabled"))
    }

    @Test fun `modifyGroup can send an empty tag list`() = runBlocking {
        // GroupModify اجازهٔ فهرست خالی می‌دهد (برخلاف GroupCreate).
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.modifyGroup(session, 2, "X", emptyList(), isDisabled = false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(0, body.getJSONArray("inbound_tags").length())
    }

    // ── حذف ──────────────────────────────────────────────────

    @Test fun `deleteGroup sends DELETE to singular path and accepts 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteGroup(session, 11)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/group/11", req.path)
    }

    @Test(expected = IllegalStateException::class)
    fun `deleteGroup throws on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
        PanelApi.deleteGroup(session, 99)
        Unit
    }

    @Test fun `mutations carry the auth header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteGroup(session, 1)
        val auth = server.takeRequest().getHeader("Authorization")
        assertEquals("Bearer tok", auth)
    }

    // ── parse مستقیم ─────────────────────────────────────────

    @Test fun `parseGroupDetail skips blank tags`() {
        val json = JSONObject("""{"id":1,"name":"N","inbound_tags":["a","","b"],"total_users":3}""")
        val g = PanelApi.parseGroupDetail(json)
        assertEquals(listOf("a", "b"), g.inboundTags)
        assertEquals(3, g.totalUsers)
    }

    @Test fun `parseGroupDetail handles null inbound_tags`() {
        val json = JSONObject("""{"id":1,"name":"N","inbound_tags":null}""")
        assertTrue(PanelApi.parseGroupDetail(json).inboundTags.isEmpty())
    }
}

/** اعتبارسنجیِ فرمِ گروه — قواعد از مدلِ پنل گرفته شده‌اند. */
class GroupValidationTest {

    @Test fun `name shorter than 3 is rejected`() {
        assertEquals(GroupValidation.ERR_NAME_SHORT, GroupValidation.validateName("ab"))
        assertEquals(GroupValidation.ERR_NAME_SHORT, GroupValidation.validateName(""))
    }

    @Test fun `whitespace does not count towards length`() {
        assertEquals(GroupValidation.ERR_NAME_SHORT, GroupValidation.validateName("  a  "))
    }

    @Test fun `name of exactly 3 and 64 is accepted`() {
        assertNull(GroupValidation.validateName("abc"))
        assertNull(GroupValidation.validateName("x".repeat(64)))
    }

    @Test fun `name longer than 64 is rejected`() {
        assertEquals(GroupValidation.ERR_NAME_LONG, GroupValidation.validateName("x".repeat(65)))
    }

    @Test fun `create requires at least one inbound`() {
        assertEquals(GroupValidation.ERR_NO_INBOUND, GroupValidation.validateInbounds(emptyList(), isCreate = true))
        assertNull(GroupValidation.validateInbounds(listOf("a"), isCreate = true))
    }

    @Test fun `edit allows an empty inbound list`() {
        assertNull(GroupValidation.validateInbounds(emptyList(), isCreate = false))
    }

    @Test fun `validate reports the name error before the inbound error`() {
        // هر دو خراب‌اند؛ کاربر باید اول خطای نام را ببیند.
        assertEquals(
            GroupValidation.ERR_NAME_SHORT,
            GroupValidation.validate("ab", emptyList(), isCreate = true)
        )
    }

    @Test fun `validate passes for a well formed create`() {
        assertNull(GroupValidation.validate("Premium", listOf("vless"), isCreate = true))
    }

    @Test fun `group detail exposes the panel name bounds`() {
        assertEquals(3, GroupDetail.NAME_MIN)
        assertEquals(64, GroupDetail.NAME_MAX)
    }
}
