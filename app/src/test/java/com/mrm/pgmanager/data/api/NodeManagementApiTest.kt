package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.Session
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodeManagementApiTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session
    private val fakeKey = "11111111-1111-4111-8111-111111111111"
    private val certificate by lazy { requireNotNull(javaClass.getResource("/node-test-cert.pem")).readText() }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "pg_key_fake-test-key", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    private fun draft() = NodeDraft(
        name = "Alpha", address = "node.example.test", certificate = certificate, apiKey = fakeKey
    )

    private fun node(id: Int = 7): JSONObject = JSONObject()
        .put("id", id).put("name", "Alpha").put("address", "node.example.test")
        .put("port", 62050).put("api_port", 63000).put("usage_coefficient", 1.0)
        .put("connection_type", "grpc").put("server_ca", certificate).put("keep_alive", 60)
        .put("core_config_id", 2).put("api_key", fakeKey).put("data_limit", 0)
        .put("data_limit_reset_strategy", "no_reset").put("reset_time", -1)
        .put("default_timeout", 10).put("internal_timeout", 15)
        .put("proxy_url", JSONObject.NULL).put("status", "connected")

    private fun snapshot(): NodeSnapshot = parseNodeSnapshot(node().toString(), 7)

    @Test fun `strict node list keeps server order and sends the api key`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"nodes":[{"id":1,"name":"B","address":"b.example.test","status":"connected","sort_order":2},
            {"id":2,"name":"A","address":"a.example.test","status":"error","sort_order":1}],"total":2}"""
        ))
        val nodes = PanelApi.nodesStrict(session)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(listOf(2, 1), nodes.map { it.id })
        assertEquals("/api/nodes", request.path)
        assertEquals(session.token, request.getHeader("X-Api-Key"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun `strict node list distinguishes permission and malformed responses from empty`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"forbidden"}"""))
        val forbidden = runCatching { PanelApi.nodesStrict(session) }.exceptionOrNull()
        assertTrue(forbidden?.message.orEmpty().contains("403"))
        server.enqueue(MockResponse().setBody("""{"not_nodes":[]}"""))
        assertTrue(runCatching { PanelApi.nodesStrict(session) }.exceptionOrNull() is IllegalArgumentException)
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(PanelApi.nodesStrict(session).isEmpty())
    }

    @Test fun `strict list rejects missing invalid and duplicate node ids`() = runBlocking {
        val malformed = listOf(
            """[{"name":"A","address":"a","status":"connected"}]""",
            """[{"id":0,"name":"A","address":"a","status":"connected"}]""",
            """[{"id":1,"name":"A","address":"a","status":"connected"},{"id":1,"name":"B","address":"b","status":"error"}]"""
        )
        for (body in malformed) {
            server.enqueue(MockResponse().setBody(body))
            assertTrue(runCatching { PanelApi.nodesStrict(session) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun `read only endpoints use real routes and keep ipv6 counts exact`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"tag":"in-1","protocol":"vless","network":null}]"""))
        val inbound = PanelApi.inboundDetails(session).single()
        assertEquals(InboundDetail("in-1", "vless", null), inbound)
        assertEquals("/api/inbounds/details", server.takeRequest().path)

        server.enqueue(MockResponse().setBody(
            """[{"subnet":"2001:db8::/64","interface_tags":["wg0"],"capacity":18446744073709551614,"used":3,"free":18446744073709551611,"free_ips":["2001:db8::4"]}]"""
        ))
        val subnet = PanelApi.wireGuardSubnets(session).single()
        assertEquals(BigInteger("18446744073709551614"), subnet.capacity)
        assertEquals(BigInteger("18446744073709551611"), subnet.free)
        assertEquals(listOf("wg0"), subnet.interfaceTags)
        val request = server.takeRequest()
        assertEquals("/api/wireguard/subnets", request.path)
        assertEquals(session.token, request.getHeader("X-Api-Key"))
    }

    @Test fun `read only endpoints report forbidden and invalid bodies`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(runCatching { PanelApi.inboundDetails(session) }.exceptionOrNull()?.message.orEmpty().contains("403"))
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(runCatching { PanelApi.wireGuardSubnets(session) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun `create sends a validated node payload without status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":7}"""))
        PanelApi.createNode(session, draft())
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("POST", request.method)
        assertEquals("/api/node", request.path)
        assertEquals("Alpha", body.getString("name"))
        assertEquals(62051, body.getInt("api_port"))
        assertEquals(certificate.trim(), body.getString("server_ca"))
        assertEquals(fakeKey, body.getString("api_key"))
        assertFalse(body.has("status"))
        assertFalse(body.has("proxy_url"))
    }

    @Test fun `modify fetches fresh state and preserves concurrent untouched values`() = runBlocking {
        val initial = snapshot()
        server.enqueue(MockResponse().setBody(node().put("api_port", 64000).put("address", "new.example.test").toString()))
        server.enqueue(MockResponse().setBody("""{"id":7}"""))
        PanelApi.modifyNode(session, initial, NodeDraft.from(initial).copy(name = "Edited"))
        val get = server.takeRequest()
        val put = server.takeRequest()
        val body = JSONObject(put.body.readUtf8())
        assertEquals("GET", get.method)
        assertEquals("/api/node/7", get.path)
        assertEquals("PUT", put.method)
        assertEquals("/api/node/7", put.path)
        assertEquals("Edited", body.getString("name"))
        assertEquals(64000, body.getInt("api_port"))
        assertFalse(body.has("address"))
        assertFalse(body.has("server_ca"))
        assertFalse(body.has("api_key"))
        assertFalse(body.has("proxy_url"))
    }

    @Test fun `an already satisfied edit does not restart the node`() = runBlocking {
        val initial = snapshot()
        server.enqueue(MockResponse().setBody(node().put("name", "Edited").toString()))
        PanelApi.modifyNode(session, initial, NodeDraft.from(initial).copy(name = "Edited"))
        assertEquals("GET", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `concurrent node edit is rejected before a put`() {
        val initial = snapshot()
        server.enqueue(MockResponse().setBody(node().put("name", "Remote").toString()))
        assertThrows(NodeEditConflictException::class.java) {
            runBlocking { PanelApi.modifyNode(session, initial, NodeDraft.from(initial).copy(name = "Local")) }
        }
        assertEquals("GET", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `validation errors never echo server supplied credentials`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":"$fakeKey"}"""))
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { PanelApi.createNode(session, draft()) } }
        assertTrue(error.message.orEmpty().contains("422"))
        assertFalse(error.message.orEmpty().contains(fakeKey))
    }

    @Test fun `a malformed successful create has an unconfirmed outcome`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"7"}"""))
        assertThrows(NodeWriteUnconfirmedException::class.java) { runBlocking { PanelApi.createNode(session, draft()) } }
        assertEquals(1, server.requestCount)
    }

    @Test fun `disconnected modify is never replayed`() {
        val initial = snapshot()
        server.enqueue(MockResponse().setBody(node().toString()))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThrows(NodeWriteUnconfirmedException::class.java) {
            runBlocking { PanelApi.modifyNode(session, initial, NodeDraft.from(initial).copy(name = "Edited")) }
        }
        assertEquals("GET", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals(2, server.requestCount)
    }

    @Test fun `503 with immediate retry hint never replays a node delete`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(204))
        assertThrows(NodeWriteUnconfirmedException::class.java) { runBlocking { PanelApi.deleteNode(session, 7) } }
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `503 with immediate retry hint never replays a node create`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":7}"""))
        assertThrows(NodeWriteUnconfirmedException::class.java) { runBlocking { PanelApi.createNode(session, draft()) } }
        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `503 with immediate retry hint never replays a user sync`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("""{"id":7}"""))
        assertThrows(NodeWriteUnconfirmedException::class.java) { runBlocking { PanelApi.syncNodeUsers(session, 7) } }
        assertEquals("PUT", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `503 with immediate retry hint never replays a reconnect`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("{}"))
        assertThrows(NodeWriteUnconfirmedException::class.java) { runBlocking { PanelApi.reconnectNode(session, 7) } }
        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `sync uses the user snapshot endpoint with explicit flush`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":7}"""))
        PanelApi.syncNodeUsers(session, 7)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/node/7/sync?flush_users=true", request.path)
        assertEquals(0L, request.body.size)
    }

    @Test fun `delete and reconnect use their own endpoints`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteNode(session, 7)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/node/7", delete.path)
        server.enqueue(MockResponse().setBody("{}"))
        PanelApi.reconnectNode(session, 7)
        val reconnect = server.takeRequest()
        assertEquals("POST", reconnect.method)
        assertEquals("/api/node/7/reconnect", reconnect.path)
    }

    @Test fun `authenticated infrastructure reads do not follow redirects to another server`() {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/stolen").toString()))
            assertThrows(IllegalStateException::class.java) { runBlocking { PanelApi.nodeSnapshot(session, 7) } }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertNull(other.takeRequest(250, TimeUnit.MILLISECONDS))
        } finally {
            other.shutdown()
        }
    }
}
