package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.PanelHost
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
 * قراردادِ «زیرساختِ پنل» روی سرورِ ساختگی: هاست‌ها، کورها و قالب‌های کلاینت.
 *
 * سه چیز اینجا قفل می‌شود که خطایشان در عمل گران است:
 *  ۱. مسیر و بدنهٔ هر درخواست (پنل برای `PUT /api/cores/{id}` کوئریِ الزامیِ
 *     `restart_nodes` می‌خواهد و برای هاست، بدنهٔ کاملِ `CreateHost`)؛
 *  ۲. **حفظِ فیلدهای ناشناخته** در ویرایشِ هاست (merge روی بدنهٔ خام) — وگرنه
 *     هاستی که در پنلِ وب با transport/ECH ساخته شده با یک ذخیره خراب می‌شود؛
 *  ۳. شکلِ متفاوتِ پاسخ‌ها: `/api/hosts` آرایهٔ خالص است ولی
 *     `/api/client_templates` پوشیده در `{"count", "templates"}`.
 */
class PanelInfraApiTest {

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

    // ── هاست‌ها ──────────────────────────────────────────────────────────

    @Test fun `hosts list parses the raw array and keeps the payload`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[
                    {"id":3,"remark":"de","address":["de.example.com","de2.example.com"],
                     "port":443,"inbound_tag":"vless-443","security":"tls",
                     "sni":["sni.example.com"],"alpn":["h2"],"fingerprint":"chrome",
                     "is_disabled":false,"priority":2,"status":["active"],
                     "use_sni_as_host":true,"transport_settings":{"xhttp_settings":{"path":"/x"}}},
                    {"id":4,"remark":"backup","address":["b.example.com"],"port":null,
                     "security":"inbound_default","priority":0,"is_disabled":true}
                ]"""
            )
        )
        val hosts = PanelApi.hosts(session)
        val req = server.takeRequest()

        assertEquals("/api/hosts?limit=0&offset=0", req.path)
        assertEquals(2, hosts.size)

        val first = hosts[0]
        assertEquals(3, first.id)
        assertEquals("de", first.remark)
        assertEquals(listOf("de.example.com", "de2.example.com"), first.addresses)
        assertEquals("vless-443", first.inboundTag)
        assertEquals(443, first.port)
        assertEquals("tls", first.security)
        assertEquals(listOf("sni.example.com"), first.sni)
        assertEquals(2, first.priority)
        assertTrue(first.useSniAsHost)
        // بدنهٔ خام باید فیلدهای پیشرفته را داشته باشد؛ merge روی همین انجام می‌شود.
        assertTrue(JSONObject(first.raw).has("transport_settings"))

        val second = hosts[1]
        assertNull("پورتِ null نباید 0 شود", second.port)
        assertTrue(second.isDisabled)
        assertEquals("inbound_default", second.security)
    }

    @Test fun `create host posts the minimal CreateHost body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":9,"remark":"new","address":["a.com"],"priority":0}"""))
        val created = PanelApi.createHost(
            session,
            remark = "  new  ",
            addresses = listOf(" a.com ", ""),
            inboundTag = "vless-443",
            port = 8443,
            security = "tls"
        )
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())

        assertEquals("POST", req.method)
        // مسیرِ ساخت در پنل `/api/host/` با اسلشِ پایانی است.
        assertEquals("/api/host/", req.path)
        assertEquals("new", body.getString("remark"))
        assertEquals(1, body.getJSONArray("address").length())
        assertEquals("a.com", body.getJSONArray("address").getString(0))
        assertEquals("vless-443", body.getString("inbound_tag"))
        assertEquals(8443, body.getInt("port"))
        assertEquals("tls", body.getString("security"))
        assertFalse(body.getBoolean("is_disabled"))
        // priority در مدلِ پنل فیلدِ الزامی است؛ نبودش 422 می‌دهد.
        assertEquals(0, body.getInt("priority"))
        assertEquals(9, created.id)
    }

    @Test fun `modify host merges into the raw body and keeps unknown fields`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":5,"remark":"edited","address":["a.com"],"priority":2,
                    "transport_settings":{"xhttp_settings":{"path":"/x"}}}"""
            )
        )
        val existing = PanelHost(
            id = 5,
            remark = "old",
            addresses = listOf("a.com"),
            port = 443,
            priority = 2,
            raw = """{"id":5,"remark":"old","address":["a.com"],"port":443,"priority":2,
                       "transport_settings":{"xhttp_settings":{"path":"/x"}},
                       "mux_settings":{"xray":{"enabled":true}}}"""
        )
        val updated = PanelApi.modifyHost(
            session, existing,
            mapOf(
                "remark" to "edited",
                "port" to JSONObject.NULL,
                "security" to "tls",
                "inbound_tag" to JSONObject.NULL
            )
        )
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())

        assertEquals("PUT", req.method)
        assertEquals("/api/host/5", req.path)
        assertEquals("edited", body.getString("remark"))
        // `null` یعنی «کلید را بردار» — پنل نبودِ port را «پیش‌فرضِ inbound» می‌فهمد.
        assertFalse(body.has("port"))
        assertFalse(body.has("inbound_tag"))
        assertEquals("tls", body.getString("security"))
        // مهم‌ترین بخش: چیزی که فرمِ موبایل نمی‌شناسد نباید گم شود.
        assertTrue(body.has("transport_settings"))
        assertTrue(body.has("mux_settings"))
        assertEquals(2, body.getInt("priority"))
        assertEquals("edited", updated.remark)
    }

    @Test fun `delete host uses the singular path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteHost(session, 7)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/host/7", req.path)
    }

    @Test fun `bulk disable posts ids and returns the panel count`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"hosts":["a","b"],"count":2}"""))
        val count = PanelApi.setHostsDisabled(session, setOf(1, 2), disabled = true)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())

        assertEquals("POST", req.method)
        assertEquals("/api/hosts/bulk/disable", req.path)
        assertEquals(2, body.getJSONArray("ids").length())
        assertEquals(2, count)
    }

    @Test fun `bulk enable hits the enable route`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"hosts":["a"],"count":1}"""))
        PanelApi.setHostsDisabled(session, setOf(1), disabled = false)
        assertEquals("/api/hosts/bulk/enable", server.takeRequest().path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bulk toggle without ids never reaches the network`() = runBlocking {
        PanelApi.setHostsDisabled(session, emptySet(), disabled = true)
    }

    // ── کورها ────────────────────────────────────────────────────────────

    @Test fun `cores parse the envelope and keep the config verbatim`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"count":1,"cores":[{"id":2,"name":"main","type":"xray",
                    "exclude_inbound_tags":["a"],"fallbacks_inbound_tags":["b","c"],
                    "sort_order":1,"created_at":"2026-08-01T00:00:00Z",
                    "config":{"inbounds":[{"tag":"vless-443","port":443}],"log":{"loglevel":"warning"}}}]}"""
            )
        )
        val cores = PanelApi.cores(session)
        assertEquals("/api/cores", server.takeRequest().path)
        assertEquals(1, cores.size)
        val core = cores[0]
        assertEquals("main", core.name)
        assertEquals("xray", core.type)
        assertEquals(listOf("a"), core.excludeInboundTags)
        assertEquals(listOf("b", "c"), core.fallbacksInboundTags)
        assertEquals(1, core.sortOrder)
        // کانفیگ خام است تا PUT بعدی عیناً همان را برگرداند.
        assertEquals(443, JSONObject(core.config).getJSONArray("inbounds").getJSONObject(0).getInt("port"))
    }

    @Test fun `modify core always sends restart_nodes and the full config`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":2,"name":"renamed","type":"wg","exclude_inbound_tags":[],
                    "fallbacks_inbound_tags":[],"sort_order":1,
                    "config":{"inbounds":[{"tag":"vless-443"}]}}"""
            )
        )
        val core = PanelCore(
            id = 2, name = "main", type = "xray",
            excludeInboundTags = listOf("a"), fallbacksInboundTags = listOf("b"),
            config = """{"inbounds":[{"tag":"vless-443"}],"routing":{"rules":[]}}"""
        )
        val updated = PanelApi.modifyCore(
            session, core,
            name = "renamed", type = "wg",
            excludeInboundTags = emptyList(), fallbacksInboundTags = emptyList(),
            restartNodes = true
        )
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())

        assertEquals("PUT", req.method)
        // بدونِ این کوئری، پنل 422 می‌دهد (پارامتر الزامی است).
        assertEquals("/api/cores/2?restart_nodes=true", req.path)
        assertEquals("renamed", body.getString("name"))
        assertEquals("wg", body.getString("type"))
        assertEquals(0, body.getJSONArray("exclude_inbound_tags").length())
        // routing که در فرم دیده نمی‌شود باید دست‌نخورده بماند.
        assertTrue(body.getJSONObject("config").has("routing"))
        assertEquals("renamed", updated.name)
    }

    @Test fun `core type can be cleared back to auto`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":2,"name":"main","type":null,"config":{"a":1}}"""))
        PanelApi.modifyCore(
            session, PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}"""),
            name = "main", type = null,
            excludeInboundTags = emptyList(), fallbacksInboundTags = emptyList(),
            restartNodes = false
        )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue("کلیدِ type باید صریحاً null باشد", body.isNull("type"))
    }

    @Test fun `restart and delete core use their own routes`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.restartCore(session, 4)
        val restart = server.takeRequest()
        assertEquals("POST", restart.method)
        assertEquals("/api/cores/4/restart", restart.path)

        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteCore(session, 4, restartNodes = true)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/cores/4?restart_nodes=true", delete.path)
    }

    // ── قالب‌های کلاینت ──────────────────────────────────────────────────

    @Test fun `client templates parse the wrapped response`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"count":2,"templates":[
                    {"id":1,"name":"clash-default","template_type":"clash_subscription",
                     "content":"proxy-groups: []","is_default":true,"is_system":true},
                    {"id":2,"name":"my-xray","template_type":"xray_subscription",
                     "content":"{}","is_default":false,"is_system":false}
                ]}"""
            )
        )
        val templates = PanelApi.clientTemplates(session)
        assertEquals("/api/client_templates", server.takeRequest().path)
        assertEquals(2, templates.size)
        assertTrue(templates[0].isSystem)
        assertTrue(templates[0].isDefault)
        assertEquals("xray_subscription", templates[1].templateType)
        assertFalse(templates[1].isSystem)
    }

    @Test fun `create client template posts all four fields`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":8,"name":"mine","template_type":"singbox_subscription",
                    "content":"{}","is_default":false,"is_system":false}"""
            )
        )
        val created = PanelApi.createClientTemplate(
            session, " mine ", "singbox_subscription", "{ \"outbounds\": [] }", isDefault = false
        )
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("POST", req.method)
        assertEquals("/api/client_templates", req.path)
        assertEquals("mine", body.getString("name"))
        assertEquals("singbox_subscription", body.getString("template_type"))
        assertEquals("{ \"outbounds\": [] }", body.getString("content"))
        assertFalse(body.getBoolean("is_default"))
        assertEquals(8, created.id)
    }

    @Test fun `modify client template sends only what changed`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":8,"name":"mine","template_type":"singbox_subscription",
                    "content":"{}","is_default":true,"is_system":false}"""
            )
        )
        PanelApi.modifyClientTemplate(session, 8, isDefault = true)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("PUT", req.method)
        assertEquals("/api/client_templates/8", req.path)
        // نوع عمداً فرستاده نمی‌شود: پنل در PUT آن را نمی‌پذیرد.
        assertFalse(body.has("template_type"))
        assertFalse(body.has("name"))
        assertTrue(body.getBoolean("is_default"))
    }

    @Test fun `delete client template hits the item route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteClientTemplate(session, 8)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/client_templates/8", req.path)
    }

    /**
     * مقادیرِ `template_type` باید عیناً همان چیزی باشند که پنل در
     * `ClientTemplateType` تعریف کرده؛ یک حرف اختلاف = ۴۲۲ در ساخت.
     */
    @Test fun `client template type values match the panel enum`() {
        assertEquals(
            listOf(
                "clash_subscription",
                "xray_subscription",
                "singbox_subscription",
                "user_agent",
                "grpc_user_agent"
            ),
            com.mrm.pgmanager.data.model.ClientTemplateType.ALL
        )
    }
}
