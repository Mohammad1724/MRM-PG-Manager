package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.screens.HostDraft
import com.mrm.pgmanager.ui.screens.HostHttpHeader
import com.mrm.pgmanager.ui.screens.HostNoiseItem
import com.mrm.pgmanager.ui.screens.HostTcpHeader
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HostEditorApiTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        session = Session(server.url("/").toString().trimEnd('/'), "test-token", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    private fun existing(raw: String) = PanelHost(id = 5, remark = "old", addresses = listOf("a"), raw = raw)

    @Test fun `guarded update uses the latest body and merges nested edits`() = runBlocking {
        val original = """{"id":5,"remark":"old","address":["a"],"priority":1,
            "status":["active","on_hold"],"subscription_templates":{"xray":2,"custom":"keep"},
            "transport_settings":{"newer_field":"before"}}"""
        val latest = """{"id":5,"remark":"old","address":["a"],"priority":7,
            "status":["on_hold","active"],"subscription_templates":{"xray":2,"custom":"new"},
            "transport_settings":{"newer_field":"after"},
            "mux_settings":{"xray":{"xudpConcurrency":9,"xudpProxyUDP443":"allow"}},"server_added":true}"""
        server.enqueue(MockResponse().setBody(latest))
        server.enqueue(MockResponse().setBody("""{"id":5,"remark":"edited","address":["a"],"priority":7}"""))
        PanelApi.modifyHostSafely(session, existing(original), listOf(
            HostFieldChange("remark", "edited"),
            HostFieldChange("status", JSONArray(listOf("active"))),
            HostFieldChange(listOf("subscription_templates", "xray"), 4)
        ))
        val get = server.takeRequest()
        val put = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("/api/host/5", get.path)
        assertEquals("PUT", put.method)
        assertEquals("/api/host/5", put.path)
        val body = JSONObject(put.body.readUtf8())
        assertEquals("edited", body.getString("remark"))
        assertEquals(7, body.getInt("priority"))
        assertEquals("after", body.getJSONObject("transport_settings").getString("newer_field"))
        assertEquals("new", body.getJSONObject("subscription_templates").getString("custom"))
        assertEquals(4, body.getJSONObject("subscription_templates").getInt("xray"))
        val xray = body.getJSONObject("mux_settings").getJSONObject("xray")
        assertEquals(9, xray.getInt("xudp_concurrency"))
        assertEquals("allow", xray.getString("xudp_proxy_udp_443"))
        assertTrue(body.getBoolean("server_added"))
        assertEquals("active", body.getJSONArray("status").getString(0))
    }

    @Test fun `a conflicting change prevents the put and keeps user edits local`() = runBlocking {
        val original = """{"id":5,"remark":"old","address":["a"],"priority":1}"""
        server.enqueue(MockResponse().setBody("""{"id":5,"remark":"other","address":["a"],"priority":1}"""))
        val result = runCatching {
            PanelApi.modifyHostSafely(session, existing(original), listOf(HostFieldChange("remark", "mine")))
        }
        assertTrue(result.exceptionOrNull() is HostEditConflictException)
        assertEquals(listOf("remark"), (result.exceptionOrNull() as HostEditConflictException).fields)
        assertEquals("GET", server.takeRequest().method)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test fun `http header draft goes through real guarded update and preserves unrelated fields`() = runBlocking {
        val raw = """{"id":5,"remark":"old","address":["a"],"http_headers":{"X-Future":"keep","X-Remove":"old"}}"""
        val host = existing(raw)
        val draft = HostDraft.from(host).withHttpHeaders(listOf(HostHttpHeader("X-Future", "keep"), HostHttpHeader("X-New", "added")))
        server.enqueue(MockResponse().setBody(JSONObject(raw).put("server_added", "preserved").toString()))
        server.enqueue(MockResponse().setBody(raw))
        PanelApi.modifyHostSafely(session, host, draft.changes(host))
        assertEquals("GET", server.takeRequest().method)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        val body = JSONObject(put.body.readUtf8())
        val headers = body.getJSONObject("http_headers")
        assertEquals("keep", headers.getString("X-Future"))
        assertEquals("added", headers.getString("X-New"))
        assertFalse(headers.has("X-Remove"))
        assertEquals("preserved", body.getString("server_added"))
    }

    @Test fun `concurrent server header changes stop the guarded put`() = runBlocking {
        val raw = """{"id":5,"remark":"old","address":["a"],"http_headers":{"X-A":"one"}}"""
        val latest = JSONObject(raw).apply { getJSONObject("http_headers").put("X-Server", "new") }
        val host = existing(raw)
        val draft = HostDraft.from(host).withHttpHeaders(listOf(HostHttpHeader("X-A", "mine")))
        server.enqueue(MockResponse().setBody(latest.toString()))
        val result = runCatching { PanelApi.modifyHostSafely(session, host, draft.changes(host)) }
        assertTrue(result.exceptionOrNull() is HostEditConflictException)
        assertEquals("GET", server.takeRequest().method)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test fun `unavailable latest host never falls back to cached data`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Forbidden"}"""))
        val original = """{"id":5,"remark":"old","address":["a"],"priority":1}"""
        assertTrue(runCatching {
            PanelApi.modifyHostSafely(session, existing(original), listOf(HostFieldChange("remark", "mine")))
        }.isFailure)
        assertEquals("GET", server.takeRequest().method)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test fun `create sends actual status and template choices`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":6,"remark":"new","address":["a"],"priority":0}"""))
        PanelApi.createHostWithChanges(session, "new", listOf("a"), "inbound", null, "inbound_default", listOf(
            HostFieldChange("status", JSONArray(listOf("disabled", "limited"))),
            HostFieldChange(listOf("subscription_templates", "xray"), 12)
        ))
        val request = server.takeRequest()
        assertEquals("/api/host/", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("disabled", body.getJSONArray("status").getString(0))
        assertEquals("limited", body.getJSONArray("status").getString(1))
        assertEquals(12, body.getJSONObject("subscription_templates").getInt("xray"))
        assertFalse(body.getBoolean("is_disabled"))
    }

    @Test fun `simple template list uses least privilege and filters default and non xray options`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"templates":[
            {"id":1,"name":"Default","template_type":"xray_subscription","is_default":true},
            {"id":2,"name":"Custom","template_type":"xray_subscription","is_default":false},
            {"id":3,"name":"Other","template_type":"clash_subscription","is_default":false}],"total":3}"""))
        val templates = PanelApi.hostXrayTemplates(session)
        val request = server.takeRequest()
        assertEquals("/api/client_templates/simple?template_type=xray_subscription&all=true", request.path)
        assertEquals(listOf(HostXrayTemplate(2, "Custom")), templates)
    }

    @Test fun `inbound entries are validated instead of silently presented as an empty list`() = runBlocking {
        server.enqueue(MockResponse().setBody("""["tag-1",{"tag":"tag-2"},"tag-1"]"""))
        assertEquals(listOf("tag-1", "tag-2"), PanelApi.inboundTagsStrict(session))
        server.enqueue(MockResponse().setBody("""[{"tag":null}]"""))
        assertTrue(runCatching { PanelApi.inboundTagsStrict(session) }.isFailure)
        server.enqueue(MockResponse().setBody("[42]"))
        assertTrue(runCatching { PanelApi.inboundTagsStrict(session) }.isFailure)
    }

    @Test fun `inbound errors are distinct from an empty list`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        assertEquals(emptyList<String>(), PanelApi.inboundTagsStrict(session))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Forbidden"}"""))
        assertTrue(runCatching { PanelApi.inboundTagsStrict(session) }.isFailure)
        assertEquals("/api/inbounds", server.takeRequest().path)
        assertEquals("/api/inbounds", server.takeRequest().path)
    }

    @Test fun `noise xhttp and xudp changes use guarded real request bodies`() = runBlocking {
        val raw = """{"id":5,"remark":"old","address":["a"],"priority":1,
            "mux_settings":{"xray":{"enabled":true,"xudpConcurrency":5,"xudpProxyUDP443":"reject"}},
            "noise_settings":{"xray":[{"type":"str","packet":"old","delay":4,"apply_to":"ip","future":"keep"}]}}"""
        val host = existing(raw)
        val initial = HostDraft.from(host)
        val draft = initial.withNoise(listOf(initial.noise.single().copy(packet = "new"), HostNoiseItem(packet = "15", delay = "1-5")))
            .withField("transport_settings.xhttp_settings.no_grpc_header", "true")
            .withField("transport_settings.xhttp_settings.sc_max_each_post_bytes", "100-200")
            .withField("mux_settings.xray.xudp_concurrency", "12")
            .withField("mux_settings.xray.xudp_proxy_udp_443", "allow")
        assertNull(draft.validation(host))
        server.enqueue(MockResponse().setBody(JSONObject(raw).put("server_added", "keep").toString()))
        server.enqueue(MockResponse().setBody(raw))
        PanelApi.modifyHostSafely(session, host, draft.changes(host))
        assertEquals("GET", server.takeRequest().method)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/host/5", request.path)
        val body = JSONObject(request.body.readUtf8())
        val noise = body.getJSONObject("noise_settings").getJSONArray("xray")
        assertEquals("new", noise.getJSONObject(0).getString("packet"))
        assertEquals("keep", noise.getJSONObject(0).getString("future"))
        assertEquals("1-5", noise.getJSONObject(1).getString("delay"))
        val xhttp = body.getJSONObject("transport_settings").getJSONObject("xhttp_settings")
        assertTrue(xhttp.getBoolean("no_grpc_header"))
        assertEquals("100-200", xhttp.getString("sc_max_each_post_bytes"))
        val mux = body.getJSONObject("mux_settings").getJSONObject("xray")
        assertEquals(12, mux.getInt("xudp_concurrency"))
        assertEquals("allow", mux.getString("xudp_proxy_udp_443"))
        assertEquals("keep", body.getString("server_added"))
    }

    @Test fun `concurrent noise updates prevent an overwriting request`() = runBlocking {
        val raw = """{"id":5,"remark":"old","address":["a"],"priority":1,
            "noise_settings":{"xray":[{"type":"rand","packet":"5","apply_to":"ip"}]}}"""
        val host = existing(raw)
        val draft = HostDraft.from(host).withNoise(emptyList())
        val latest = JSONObject(raw)
        latest.getJSONObject("noise_settings").getJSONArray("xray").put(JSONObject().put("type", "str").put("packet", "server").put("apply_to", "ip"))
        server.enqueue(MockResponse().setBody(latest.toString()))
        val result = runCatching { PanelApi.modifyHostSafely(session, host, draft.changes(host)) }
        assertTrue(result.exceptionOrNull() is HostEditConflictException)
        assertEquals("GET", server.takeRequest().method)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }


    @Test fun `host custom variables use settings general permission and surface errors`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"custom_variables":[{"key":"REGION","value":"west"},{"key":"EMPTY","value":""}]}"""))
        val values = PanelApi.hostCustomVariables(session)
        assertEquals(listOf(HostCustomVariable("REGION", "west"), HostCustomVariable("EMPTY", "")), values)
        assertFalse(values.toString().contains("west"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("/api/settings/general", request.path)
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Forbidden"}"""))
        assertTrue(runCatching { PanelApi.hostCustomVariables(session) }.isFailure)
        assertEquals("/api/settings/general", server.takeRequest().path)
        server.enqueue(MockResponse().setBody("""{"custom_variables":{"REGION":"west"}}"""))
        assertTrue(runCatching { PanelApi.hostCustomVariables(session) }.isFailure)
    }


    @Test fun `TCP headers use real guarded update with arrays and preserve server additions`() = runBlocking {
        val raw = """{"id":5,"remark":"old","address":["a"],"priority":1,
            "transport_settings":{"tcp_settings":{"header":"http","request":{"headers":{"Accept":["one"]}}}}}"""
        val host = existing(raw)
        val draft = HostDraft.from(host)
            .withField("transport_settings.tcp_settings.request.method", "POST")
            .withTcpHeaders("request", listOf(HostTcpHeader("Accept", listOf("text/html,application/json", "other"))))
        val latest = JSONObject(raw)
        latest.getJSONObject("transport_settings").getJSONObject("tcp_settings").put("future", "keep")
        server.enqueue(MockResponse().setBody(latest.toString()))
        server.enqueue(MockResponse().setBody(raw))
        PanelApi.modifyHostSafely(session, host, draft.changes(host))
        assertEquals("GET", server.takeRequest().method)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        val tcp = JSONObject(put.body.readUtf8()).getJSONObject("transport_settings").getJSONObject("tcp_settings")
        assertEquals("keep", tcp.getString("future"))
        val request = tcp.getJSONObject("request")
        assertEquals("POST", request.getString("method"))
        val values = request.getJSONObject("headers").getJSONArray("Accept")
        assertEquals("text/html,application/json", values.getString(0))
        assertEquals("other", values.getString(1))
    }

    @Test fun `concurrent TCP header change blocks the put`() = runBlocking {
        val raw = """{"id":5,"remark":"old","address":["a"],"priority":1,
            "transport_settings":{"tcp_settings":{"request":{"headers":{"X":["one"]}}}}}"""
        val host = existing(raw)
        val draft = HostDraft.from(host).withTcpHeaders("request", emptyList())
        val latest = JSONObject(raw)
        latest.getJSONObject("transport_settings").getJSONObject("tcp_settings").getJSONObject("request")
            .getJSONObject("headers").put("Y", JSONArray(listOf("server")))
        server.enqueue(MockResponse().setBody(latest.toString()))
        val result = runCatching { PanelApi.modifyHostSafely(session, host, draft.changes(host)) }
        assertTrue(result.exceptionOrNull() is HostEditConflictException)
        assertEquals("GET", server.takeRequest().method)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test fun `custom variable nulls and invalid values are not invented`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"custom_variables":null}"""))
        assertTrue(PanelApi.hostCustomVariables(session).isEmpty())
        server.enqueue(MockResponse().setBody("""{"custom_variables":[{"key":"A","value":null}]}"""))
        assertEquals(listOf(HostCustomVariable("A", "")), PanelApi.hostCustomVariables(session))
        server.enqueue(MockResponse().setBody("""{"custom_variables":[{"key":"A","value":123}]}"""))
        assertTrue(runCatching { PanelApi.hostCustomVariables(session) }.isFailure)
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(runCatching { PanelApi.hostCustomVariables(session) }.isFailure)
        repeat(4) { assertEquals("/api/settings/general", server.takeRequest().path) }
    }

}
