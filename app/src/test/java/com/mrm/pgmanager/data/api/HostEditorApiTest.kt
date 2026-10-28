package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.screens.HostDraft
import com.mrm.pgmanager.ui.screens.HostHttpHeader
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
}
