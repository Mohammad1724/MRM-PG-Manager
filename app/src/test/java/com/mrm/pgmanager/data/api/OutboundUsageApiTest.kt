package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.OutboundUsage
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OutboundUsageApiTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "test-token", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    @Test fun `outbound endpoint uses selected node and range and aggregates real points`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{
          "period":"hour","start":"2026-01-01T00:00:00Z","end":"2026-01-02T00:00:00Z",
          "stats":{
            "direct":[{"period_start":"2026-01-01T02:00:00Z","uplink":10,"downlink":20},
                      {"period_start":"2026-01-01T01:00:00Z","uplink":1,"downlink":2}],
            "proxy":[{"period_start":"2026-01-01T01:00:00Z","uplink":3,"downlink":4}]
          }
        }"""))
        val result = PanelApi.outboundUsage(session, StatsRange.LAST_24H, nodeId = 7)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path.orEmpty().startsWith("/api/node/usage/outbounds?period=hour&start="))
        assertTrue(request.path.orEmpty().contains("&node_id=7"))
        assertEquals(listOf("direct", "proxy"), result.map { it.tag })
        assertEquals(11L, result[0].uplink)
        assertEquals(22L, result[0].downlink)
        assertEquals(listOf(10L, 30L), OutboundUsage.merge(result).map { it.totalTraffic })
    }

    @Test fun `all nodes omits node filter and empty response remains empty`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stats":{}}"""))
        assertTrue(PanelApi.outboundUsage(session, StatsRange.LAST_1H).isEmpty())
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.contains("period=minute"))
        assertTrue(!path.contains("node_id="))
    }

    @Test fun `unsupported server reports 404 rather than empty data`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val error = runCatching { PanelApi.outboundUsage(session) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("404"))
    }

    @Test fun `canceling a blocked request closes the call promptly`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async { PanelApi.outboundUsage(session) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        withTimeout(2_000) { request.cancelAndJoin() }
        assertTrue(request.isCancelled)
    }

    @Test fun `unauthorized response remains a typed session failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { PanelApi.outboundUsage(session) }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
    }

    @Test fun `outbound reads do not forward credentials through redirects`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/capture")))
        val error = runCatching { PanelApi.outboundUsage(session) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("302"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `fractional negative and overflowing traffic are rejected instead of displayed as zero`() {
        val invalid = listOf(
            """{"stats":{"x":[{"period_start":"a","uplink":1.5,"downlink":0}]}}""",
            """{"stats":{"x":[{"period_start":"a","uplink":-1,"downlink":0}]}}""",
            """{"stats":{"x":[{"period_start":"a","uplink":9223372036854775807,"downlink":1}]}}""",
            """{"stats":{"x":[{"period_start":"a","uplink":9223372036854775807,"downlink":0}],
                       "y":[{"period_start":"a","uplink":1,"downlink":0}]}}"""
        )
        invalid.forEach { body ->
            assertThrows(IllegalArgumentException::class.java) { OutboundUsage.fromResponse(JSONObject(body)) }
        }
    }

    @Test fun `malformed response does not fabricate zero traffic`() {
        assertThrows(IllegalArgumentException::class.java) { OutboundUsage.fromResponse(JSONObject("{}")) }
        assertThrows(IllegalArgumentException::class.java) {
            OutboundUsage.fromResponse(JSONObject("""{"stats":{"direct":[{"period_start":"2026-01-01T00:00:00Z"}]}}"""))
        }
    }
}
