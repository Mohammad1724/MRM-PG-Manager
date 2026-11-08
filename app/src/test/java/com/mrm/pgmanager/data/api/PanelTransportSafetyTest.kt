package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.model.AdminSelf
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PanelTransportSafetyTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session
    private val user = PanelUser(42, "example", "active", 0, 0, null, null)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "pg_key_test-credential", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    @Test fun `validation details containing 401 do not expire the session`() {
        assertFalse(PanelApi.isUnauthorized(IllegalStateException("Request failed: 400 invalid value: 401")))
        assertFalse(PanelApi.isUnauthorized(IllegalStateException("Request failed: 422 invalid user 1401")))
        assertTrue(PanelApi.isUnauthorized(IllegalStateException("Login failed: 401 invalid password")))
        assertTrue(PanelApi.isUnauthorized(SessionExpiredException()))
    }

    @Test fun `group name lookup is isolated between accounts on the same panel`() = runBlocking {
        val users = """{"users":[{"id":1,"username":"sample","status":"active","group_ids":[7]}],"total":1}"""
        server.enqueue(MockResponse().setBody(users))
        server.enqueue(MockResponse().setBody("""{"groups":[{"id":7,"name":"First account group"}]}"""))
        val first = PanelApi.users(session)

        val second = session.copy(username = "bob", token = "pg_key_other-credential")
        server.enqueue(MockResponse().setBody(users))
        server.enqueue(MockResponse().setBody("""{"groups":[{"id":7,"name":"Second account group"}]}"""))
        val other = PanelApi.users(second)

        assertEquals(listOf("First account group"), first.single().groupNames)
        assertEquals(listOf("Second account group"), other.single().groupNames)
        assertEquals(4, server.requestCount)

        // A change by one account must invalidate other accounts' labels on this panel.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        PanelApi.modifyGroup(session, 7, "renamed", emptyList(), false)
        server.enqueue(MockResponse().setBody(users))
        server.enqueue(MockResponse().setBody("""{"groups":[{"id":7,"name":"Renamed"}]}"""))
        assertEquals(listOf("Renamed"), PanelApi.users(second).single().groupNames)
        assertEquals(7, server.requestCount)
    }

    @Test fun `unauthorized group lookup is not swallowed by the fallback`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"users":[{"id":1,"username":"sample","status":"active","group_ids":[7]}]}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { PanelApi.users(session) }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
        assertEquals(2, server.requestCount)
    }

    @Test fun `permission refresh invalidates in flight group names`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"users":[{"id":1,"username":"sample","status":"active","group_ids":[7]}]}"""))
        server.enqueue(MockResponse().setBody("""{"groups":[{"id":7,"name":"Old group"}]}""").setHeadersDelay(1, TimeUnit.SECONDS))
        val page = async(Dispatchers.IO) { PanelApi.users(session) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        try {
            AdminAccess.refreshWith(session, force = true) { AdminSelf(id = 1, username = "alice") }
            assertEquals(emptyList<String>(), withTimeout(5_000) { page.await() }.single().groupNames)
        } finally {
            AdminAccess.reset()
        }
    }

    @Test fun `authenticated reads never follow a cross origin redirect`() = runBlocking {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/capture")))
            other.enqueue(MockResponse().setBody("{}"))

            val error = runCatching { PanelApi.systemStats(session) }.exceptionOrNull()

            assertNotNull(error)
            assertEquals(session.token, server.takeRequest().getHeader("X-Api-Key"))
            assertEquals(1, server.requestCount)
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test fun `same origin reads can follow redirects`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/redirected"))
        server.enqueue(MockResponse().setBody("{}"))

        PanelApi.systemStats(session)

        assertEquals("/api/system", server.takeRequest().path)
        assertEquals("/redirected", server.takeRequest().path)
    }

    @Test fun `a disconnected put is never repeated after an uncertain write`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = runCatching { PanelApi.setDisabled(session, user, true) }.exceptionOrNull()

        assertTrue(error is PanelWriteUnconfirmedException)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a put is not replayed on an immediate server retry hint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = runCatching { PanelApi.setDisabled(session, user, true) }.exceptionOrNull()

        assertTrue(error is PanelWriteUnconfirmedException)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a delete is not replayed on an immediate server retry hint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = runCatching { PanelApi.deleteUser(session, user) }.exceptionOrNull()

        assertTrue(error is PanelWriteUnconfirmedException)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a post is not replayed on an immediate server retry hint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = runCatching { PanelApi.resetUsage(session, user) }.exceptionOrNull()

        assertTrue(error is PanelWriteUnconfirmedException)
        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a write redirect is not followed even on the same origin`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/another-write"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = runCatching { PanelApi.resetUsage(session, user) }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a dry run reports a server error without an uncertain mutation warning`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = runCatching { PanelApi.bulkPreview(session, setOf(42), "days") }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error !is PanelWriteUnconfirmedException)
        assertTrue(error?.message.orEmpty().contains("503"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `cancelling a blocked system read closes the call promptly`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async(Dispatchers.IO) { PanelApi.systemStats(session) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        withTimeout(3_000) { request.cancelAndJoin() }

        assertTrue(request.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test fun `transient read failures still receive a retry`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("{}"))

        PanelApi.systemStats(session)

        assertEquals("GET", server.takeRequest().method)
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertTrue(server.requestCount >= 2)
    }
}
