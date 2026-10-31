package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Request contracts and safe updates for hosts, cores and client templates. */
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
        assertEquals(0, second.priority)
        assertTrue(second.isDisabled)
        assertEquals("inbound_default", second.security)
    }

    @Test fun `host priority preserves zero and negative values and distinguishes unknown values`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """[
                {"id":1,"remark":"zero","priority":0},
                {"id":2,"remark":"negative","priority":-2},
                {"id":3,"remark":"missing"},
                {"id":4,"remark":"null","priority":null},
                {"id":5,"remark":"numeric string","priority":"3"},
                {"id":6,"remark":"invalid","priority":"invalid"},
                {"id":7,"remark":"fractional","priority":1.5}
            ]"""
        ))
        val hosts = PanelApi.hosts(session)
        assertEquals(listOf(0, -2, null, null, 3, null, null), hosts.map { it.priority })
        assertTrue(JSONObject(hosts[0].raw).has("priority"))
        assertFalse(JSONObject(hosts[2].raw).has("priority"))
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

    // تابعِ تست باید `void` باشد: `= runBlocking { ... }` مقدارِ `setHostsDisabled` را
    // برمی‌گرداند و JUnit کلِ کلاس را رد می‌کرد (InvalidTestClassError) — یعنی حتی
    // تست‌های سالمِ همین فایل هم اجرا نمی‌شدند.
    @Test fun `bulk toggle without ids never reaches the network`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.setHostsDisabled(session, emptySet(), disabled = true) }
        }
        assertEquals(0, server.requestCount)
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
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":["a"],
                "fallbacks_inbound_tags":["b"],"config":{"inbounds":[{"tag":"vless-443"}],"routing":{"rules":[]}}}"""
        ))
        server.enqueue(
            MockResponse().setBody(
                """{"id":2,"name":"renamed","type":"xray","exclude_inbound_tags":[],
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
            name = "renamed", type = "xray",
            excludeInboundTags = emptyList(), fallbacksInboundTags = emptyList(),
            restartNodes = true
        )
        val read = server.takeRequest()
        assertEquals("GET", read.method)
        assertEquals("/api/core/2", read.path)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())

        assertEquals("PUT", req.method)
        assertEquals("/api/core/2?restart_nodes=true", req.path)
        assertEquals("renamed", body.getString("name"))
        assertEquals("xray", body.getString("type"))
        assertEquals(0, body.getJSONArray("exclude_inbound_tags").length())
        // routing که در فرم دیده نمی‌شود باید دست‌نخورده بماند.
        assertTrue(body.getJSONObject("config").has("routing"))
        assertEquals("renamed", updated.name)
    }

    @Test fun `existing core type cannot be cleared when the panel ignores null`() {
        val core = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, core, "main", null, emptyList(), emptyList(), false) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `an initially null core type remains null when unchanged`() = runBlocking {
        val coreResponse = """{"id":2,"name":"main","type":null,"exclude_inbound_tags":[],
            "fallbacks_inbound_tags":[],"config":{"a":1}}"""
        server.enqueue(MockResponse().setBody(coreResponse))
        server.enqueue(MockResponse().setBody(coreResponse))
        val core = PanelCore(id = 2, name = "main", type = null, config = """{"a":1}""")
        PanelApi.modifyCore(session, core, "renamed", null, emptyList(), emptyList(), false)
        assertEquals("/api/core/2", server.takeRequest().path)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.has("type"))
        assertTrue(body.isNull("type"))
    }

    @Test fun `core update uses the latest configuration and keeps unrelated server edits`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":["server"],
                "fallbacks_inbound_tags":[],"config":{"inbounds":[{"tag":"server"}],"routing":{"rules":[1]}}}"""
        ))
        server.enqueue(MockResponse().setBody("""{"id":2,"name":"renamed","type":"xray","exclude_inbound_tags":["server"],"fallbacks_inbound_tags":[],"config":{"a":1}}"""))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"old":true}""")
        PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false)
        assertEquals("/api/core/2", server.takeRequest().path)
        val put = server.takeRequest()
        val body = JSONObject(put.body.readUtf8())
        assertEquals("/api/core/2?restart_nodes=false", put.path)
        assertEquals("server", body.getJSONObject("config").getJSONArray("inbounds").getJSONObject(0).getString("tag"))
        assertEquals(1, body.getJSONObject("config").getJSONObject("routing").getJSONArray("rules").getInt(0))
        assertEquals("server", body.getJSONArray("exclude_inbound_tags").getString(0))
        assertFalse(body.getJSONObject("config").has("old"))
    }

    @Test fun `core put retains exact config numbers and escaped nested strings`() = runBlocking {
        val config = """{"large":9223372036854775807123456789,"precise":0.12345678901234567890123456789,
            "nested":[{"text":"braces } and ["}],"inbounds":[]}"""
        val response = """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],
            "fallbacks_inbound_tags":[],"config":$config}"""
        server.enqueue(MockResponse().setBody(response))
        server.enqueue(MockResponse().setBody(response))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = config)
        PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false)
        assertEquals("/api/core/2", server.takeRequest().path)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"config\":$config"))
    }

    @Test fun `ambiguous duplicate config response stops before put`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],
                "config":{"a":1},"config":{"a":2}}"""
        ))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun `editing tags does not normalize an untouched existing core name`() = runBlocking {
        val response = """{"id":2,"name":"  Main  ","type":"xray","exclude_inbound_tags":[],
            "fallbacks_inbound_tags":[],"config":{"inbounds":[{"tag":"a"}]}}"""
        server.enqueue(MockResponse().setBody(response))
        server.enqueue(MockResponse().setBody(response))
        val initial = PanelCore(id = 2, name = "  Main  ", type = "xray", config = """{"inbounds":[{"tag":"a"}]}""")
        PanelApi.modifyCore(session, initial, initial.name, initial.type, listOf("a"), emptyList(), false)
        assertEquals("/api/core/2", server.takeRequest().path)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("  Main  ", body.getString("name"))
    }

    @Test fun `unstorable tags are preserved until deliberately removed before put`() {
        val response = """{"id":2,"name":"main","type":"future-backend","exclude_inbound_tags":["null",""],
            "fallbacks_inbound_tags":[],"config":{"inbounds":[]}}"""
        server.enqueue(MockResponse().setBody("""{"cores":[$response]}"""))
        val initial = runBlocking { PanelApi.cores(session).single() }
        assertEquals("future-backend", initial.type)
        assertEquals(setOf("null", ""), initial.excludeInboundTags.toSet())
        server.enqueue(MockResponse().setBody(response))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "new name", initial.type, initial.excludeInboundTags, emptyList(), false) }
        }
        assertEquals(2, server.requestCount)

        server.enqueue(MockResponse().setBody(response))
        server.enqueue(MockResponse().setBody(response))
        runBlocking { PanelApi.modifyCore(session, initial, "new name", initial.type, listOf("null"), emptyList(), false) }
        assertEquals("/api/cores", server.takeRequest().path)
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals("/api/core/2", server.takeRequest().path)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("future-backend", body.getString("type"))
        val tags = body.getJSONArray("exclude_inbound_tags")
        assertEquals(listOf("null"), (0 until tags.length()).map { tags.getString(it) })
    }

    @Test fun `new tags containing commas cannot be silently split by storage`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],
                "config":{"inbounds":[{"tag":"a,b"}]}}"""
        ))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"inbounds":[{"tag":"a,b"}]}""")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "main", "xray", listOf("a,b"), emptyList(), false) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun `existing tags may be removed even when inbound options cannot be parsed`() = runBlocking {
        val response = """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":["old"],
            "fallbacks_inbound_tags":[],"config":{"inbounds":{}}}"""
        server.enqueue(MockResponse().setBody(response))
        server.enqueue(MockResponse().setBody(response))
        val initial = PanelCore(id = 2, name = "main", type = "xray", excludeInboundTags = listOf("old"),
            config = """{"inbounds":{}}""")
        PanelApi.modifyCore(session, initial, "main", "xray", emptyList(), emptyList(), false)
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(0, JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("exclude_inbound_tags").length())
    }

    @Test fun `conflicting core metadata stops before put`() {
        server.enqueue(MockResponse().setBody("""{"id":2,"name":"remote","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],"config":{"a":1}}"""))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        val error = assertThrows(CoreEditConflictException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "local", "xray", emptyList(), emptyList(), false) }
        }
        assertEquals(listOf("name"), error.fields)
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `changing inbound choices stops if the server changed available tags`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],
                "config":{"inbounds":[{"tag":"new"}]}}"""
        ))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"inbounds":[{"tag":"old"}]}""")
        val error = assertThrows(CoreEditConflictException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "main", "xray", listOf("old"), emptyList(), false) }
        }
        assertEquals(listOf("inbounds"), error.fields)
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `unavailable fresh core never sends cached configuration`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"example-sensitive-value"}"""))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false) }
        }
        assertFalse(error.message.orEmpty().contains("example-sensitive-value"))
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `an invalid update response is not reported as success`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],"config":{"a":1}}"""
        ))
        server.enqueue(MockResponse().setBody("""{"id":9}"""))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(CoreUpdateUnconfirmedException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false) }
        }
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals("/api/core/2?restart_nodes=false", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }

    @Test fun `a disconnected core put is not retried and its result remains unconfirmed`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],"config":{"a":1}}"""
        ))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(CoreUpdateUnconfirmedException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), true) }
        }
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals("/api/core/2?restart_nodes=true", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }

    @Test fun `an incomplete update response warns that the result is unconfirmed`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],"config":{"a":1}}"""
        ))
        server.enqueue(MockResponse().setBody("""{"id":2,"name":"renamed"}"""))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(CoreUpdateUnconfirmedException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false) }
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `wireguard update with excluded or fallback tags is rejected before put`() {
        server.enqueue(MockResponse().setBody(
            """{"id":2,"name":"main","type":"xray","exclude_inbound_tags":["old"],"fallbacks_inbound_tags":[],
                "config":{"inbounds":[{"tag":"old"}]}}"""
        ))
        val initial = PanelCore(id = 2, name = "main", type = "xray", excludeInboundTags = listOf("old"),
            config = """{"inbounds":[{"tag":"old"}]}""")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "main", "wg", listOf("old"), emptyList(), false) }
        }
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `mismatched core identity prevents put`() {
        server.enqueue(MockResponse().setBody(
            """{"id":9,"name":"main","type":"xray","exclude_inbound_tags":[],"fallbacks_inbound_tags":[],"config":{"a":1}}"""
        ))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false) }
        }
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `incomplete fresh core is rejected rather than overwriting unknown fields`() {
        server.enqueue(MockResponse().setBody("""{"id":2,"name":"main","type":"xray","config":{"a":1}}"""))
        val initial = PanelCore(id = 2, name = "main", type = "xray", config = """{"a":1}""")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PanelApi.modifyCore(session, initial, "renamed", "xray", emptyList(), emptyList(), false) }
        }
        assertEquals("/api/core/2", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `restart and delete core use their own routes`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.restartCore(session, 4)
        val restart = server.takeRequest()
        assertEquals("POST", restart.method)
        assertEquals("/api/core/4/restart", restart.path)

        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteCore(session, 4, restartNodes = true)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/core/4?restart_nodes=true", delete.path)
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
