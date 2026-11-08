package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.data.model.ApiKeyQuota
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
 * قراردادِ «کلیدهای API» (`/api/api_key*`) روی سرورِ ساختگی.
 *
 * سه چیز اینجا قفل می‌شود که خطایشان در عمل گران است:
 *  ۱. متنِ کاملِ کلید فقط در پاسخِ **ساخت** و **چرخش** می‌آید؛ بقیهٔ مسیرها فقط
 *     `api_key_trimmed` می‌دهند و اپ نباید چیزی از خودش بسازد؛
 *  ۲. «revoke» در پنل یعنی **چرخش** (همان id با متنِ تازه) — نه حذف؛ و
 *     «غیرفعال‌کردن» یک `PATCH status` جداگانه است؛
 *  ۳. بدنهٔ ساخت باید `expire_date` را یا ISO بفرستد یا صریحاً `null` کند؛
 *     مقدارِ رشتهٔ خالی/`0` را پنل ۴۲۲ می‌کند.
 */
class ApiKeysApiTest {

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

    private fun keyFields(id: Int = 3, status: String = "active", expired: Boolean = false): String =
        """"id":$id,"admin_id":1,"name":"widget","note":"phone","created_at":"2026-10-01T10:00:00Z",
           "api_key_trimmed":"pg_key_…f7a2","status":"$status","is_expired":$expired,
           "expire_date":null,"inherit_permissions":true""".trimIndent()

    private fun keyJson(id: Int = 3, status: String = "active", expired: Boolean = false): String =
        "{${keyFields(id, status, expired)}}"

    @Test fun `list reads the envelope and maps status flags`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"api_keys":[${keyJson(3)},${keyJson(4, "disabled")}],"total":2}"""
            )
        )
        val keys = PanelApi.apiKeys(session)

        assertEquals("/api/api_keys?offset=0&limit=200", server.takeRequest().path)
        assertEquals(2, keys.size)
        assertEquals("widget", keys[0].name)
        assertEquals("pg_key_…f7a2", keys[0].trimmed)
        assertTrue(keys[0].isActive)
        assertFalse(keys[1].isActive)
        assertNull(keys[0].expireDate)
        assertNull(keys[0].maxRequests)
        assertNull(keys[0].requestCount)
        assertNull(keys[0].deleteOnLimit)
    }

    @Test fun `request limits and usage are read without confusing status with usability`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"api_keys":[
                {${keyFields(id = 11)},"max_requests":3,"request_count":3,"delete_on_limit":false},
                {${keyFields(id = 12)},"max_requests":3000000000,"request_count":2999999999,"delete_on_limit":true},
                {${keyFields(id = 13)},"max_requests":null,"request_count":42,"delete_on_limit":false},
                {${keyFields(id = 14, status = "disabled")},"max_requests":3,"request_count":3,"delete_on_limit":false},
                {${keyFields(id = 15)},"max_requests":3,"request_count":null,"delete_on_limit":false}
            ],"total":5}""".trimIndent()
        ))

        val keys = PanelApi.apiKeys(session)
        val reached = keys[0]
        assertEquals(3L, reached.maxRequests)
        assertEquals(3L, reached.requestCount)
        assertEquals(false, reached.deleteOnLimit)
        assertTrue(reached.isEnabled)
        assertTrue(reached.isRequestLimitReached)
        assertFalse(reached.isActive)

        assertEquals(3_000_000_000L, keys[1].maxRequests)
        assertEquals(2_999_999_999L, keys[1].requestCount)
        assertEquals(true, keys[1].deleteOnLimit)
        assertTrue(keys[1].isActive)

        assertNull(keys[2].maxRequests)
        assertEquals(42L, keys[2].requestCount)
        assertTrue(keys[2].isActive)
        assertFalse(keys[2].isRequestLimitReached)

        assertFalse(keys[3].isEnabled)
        assertTrue(keys[3].isRequestLimitReached)
        assertFalse(keys[3].isActive)

        assertTrue(keys[4].isEnabled)
        assertTrue(keys[4].isRequestCountUnknown)
        assertFalse(keys[4].isActive)
    }

    @Test fun `key list reads do not forward credentials through redirects`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/capture")))
        val error = runCatching { PanelApi.apiKeys(session) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("302"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `malformed quota fields never prove capability or fabricate an unlimited key`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[
            {${keyFields(id = 21)},"max_requests":"invalid","request_count":2,"delete_on_limit":false},
            {${keyFields(id = 22)},"max_requests":10,"request_count":2,"delete_on_limit":"invalid"},
            {${keyFields(id = 23)},"max_requests":10,"request_count":1.5,"delete_on_limit":false},
            {${keyFields(id = 24)},"max_requests":0,"request_count":0,"delete_on_limit":false}
        ]}"""))
        val keys = PanelApi.apiKeys(session)
        assertTrue(keys[0].requestLimitUnknown)
        assertFalse(keys[0].isActive)
        assertTrue(keys[3].requestLimitUnknown)
        assertFalse(keys[3].isActive)
        assertTrue(keys.none { it.supportsRequestLimits })
        assertNull(keys[1].deleteOnLimit)
        assertNull(keys[2].requestCount)
        assertTrue(keys[2].isRequestCountUnknown)
    }

    @Test fun `expired key is not active even with status active`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${keyJson(9, "active", true)}],"total":1}"""))
        val keys = PanelApi.apiKeys(session)

        assertFalse("is_expired باید بر status مقدم باشد", keys[0].isActive)
        assertTrue(keys[0].isEnabled)
    }

    @Test fun `create posts the name and a null expiry when unlimited`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"api_key":"pg_key_full_secret",${keyFields()}}""")
        )
        val created = PanelApi.createApiKey(session, name = "  widget  ", note = "")
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertEquals("POST", request.method)
        assertEquals("/api/api_key", request.path)
        assertEquals("widget", body.getString("name"))
        assertTrue("یادداشتِ خالی باید null باشد", body.isNull("note"))
        assertTrue("بی‌انقضا باید null بفرستد، نه \"0\"", body.isNull("expire_date"))
        assertEquals("pg_key_full_secret", created.key)
        assertEquals("widget", created.item.name)
    }

    @Test fun `create with an expiry sends an ISO instant`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"api_key":"pg_key_x",${keyFields()}}""")
        )
        PanelApi.createApiKey(session, name = "ci", expireDays = 30)
        val body = JSONObject(server.takeRequest().body.readUtf8())

        val expire = body.getString("expire_date")
        // پنل `datetime` می‌خواهد؛ رشتهٔ تاریخِ ساده یا "" را ۴۲۲ می‌کند.
        assertTrue(expire.endsWith("Z"))
        assertTrue(java.time.Instant.parse(expire).isAfter(java.time.Instant.now().plusSeconds(29L * 24 * 3600)))
    }

    @Test fun `rotate hits the revoke route and returns the new secret`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"api_key":"pg_key_rotated",${keyFields()}}""")
        )
        val rotated = PanelApi.rotateApiKey(session, keyId = 3)
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/api/api_key/3/revoke", request.path)
        assertEquals("pg_key_rotated", rotated.key)
        assertEquals(3, rotated.item.id)
    }

    @Test fun `disable is a PATCH with the status field`() = runBlocking {
        server.enqueue(MockResponse().setBody(keyJson(3, "disabled")))
        val updated = PanelApi.setApiKeyDisabled(session, keyId = 3, disabled = true)
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertEquals("PATCH", request.method)
        assertEquals("/api/api_key/3", request.path)
        assertEquals(PanelApiKey.DISABLED, body.getString("status"))
        assertFalse(body.has("max_requests"))
        assertFalse(body.has("delete_on_limit"))
        assertFalse(updated.isActive)
    }

    @Test fun `delete uses the singular path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteApiKey(session, keyId = 8)
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/api/api_key/8", request.path)
    }

    private fun limitedKey(id: Int = 3, maximum: String = "10", count: Int = 2, delete: Boolean = false): String =
        """{${keyFields(id = id)},"max_requests":$maximum,"request_count":$count,"delete_on_limit":$delete}"""

    @Test fun `limited creation verifies capabilities and sends explicit quota`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey()}],"total":1}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"api_key":"test-secret",${keyFields()},"max_requests":25,"request_count":0,"delete_on_limit":true}"""
        ))
        val result = PanelApi.createApiKey(session, "client", quota = ApiKeyQuota(25, true))
        assertEquals("GET", server.takeRequest().method)
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("POST", request.method)
        assertEquals(25L, body.getLong("max_requests"))
        assertTrue(body.getBoolean("delete_on_limit"))
        assertTrue(result.requestLimitConfirmed)
        assertEquals("test-secret", result.key)
    }

    @Test fun `older server or empty list cannot silently create an unlimited key when a quota was requested`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${keyJson()}],"total":1}"""))
        val error = runCatching { PanelApi.createApiKey(session, "client", quota = ApiKeyQuota(10, false)) }.exceptionOrNull()
        assertTrue(error is ApiKeyQuotaUnsupportedException)
        assertEquals(1, server.requestCount)
    }

    @Test fun `created secret remains available when server ignores the requested quota`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey()}],"total":1}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"api_key":"test-secret",${keyFields()}}"""))
        val created = PanelApi.createApiKey(session, "client", quota = ApiKeyQuota(10, false))
        assertEquals("test-secret", created.key)
        assertFalse(created.requestLimitConfirmed)
    }

    @Test fun `quota update writes only the changed field after a fresh read`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey()}],"total":1}"""))
        val base = PanelApi.apiKeys(session).single()
        server.enqueue(MockResponse().setBody(limitedKey(count = 3)))
        server.enqueue(MockResponse().setBody(limitedKey(maximum = "20", count = 3)))
        val updated = PanelApi.updateApiKeyQuota(session, base, ApiKeyQuota(20, false))
        server.takeRequest()
        val read = server.takeRequest()
        val patch = server.takeRequest()
        assertEquals("/api/api_key/3", read.path)
        assertEquals("GET", read.method)
        assertEquals("PATCH", patch.method)
        val body = JSONObject(patch.body.readUtf8())
        assertEquals(setOf("max_requests"), body.keys().asSequence().toSet())
        assertEquals(20L, body.getLong("max_requests"))
        assertEquals(3L, updated.requestCount)
    }

    @Test fun `clearing quota sends null without resetting the counter`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey(delete = true)}],"total":1}"""))
        val base = PanelApi.apiKeys(session).single()
        server.enqueue(MockResponse().setBody(limitedKey(count = 4, delete = true)))
        server.enqueue(MockResponse().setBody(limitedKey(maximum = "null", count = 4, delete = true)))
        val updated = PanelApi.updateApiKeyQuota(session, base, ApiKeyQuota(null, true))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.has("max_requests")); assertTrue(body.isNull("max_requests"))
        assertFalse(body.has("request_count")); assertFalse(body.has("delete_on_limit"))
        assertNull(updated.maxRequests)
        assertEquals(4L, updated.requestCount)
    }

    @Test fun `conflicting quota update does not send patch`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey()}],"total":1}"""))
        val base = PanelApi.apiKeys(session).single()
        server.enqueue(MockResponse().setBody(limitedKey(maximum = "15")))
        val error = runCatching { PanelApi.updateApiKeyQuota(session, base, ApiKeyQuota(20, false)) }.exceptionOrNull()
        assertTrue(error is ApiKeyQuotaConflictException)
        assertEquals(2, server.requestCount)
    }

    @Test fun `invalid quota is rejected before any request`() = runBlocking {
        val error = runCatching { PanelApi.createApiKey(session, "client", quota = ApiKeyQuota(0, false)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test fun `new quota above the portable SQL integer limit is rejected without requests`() = runBlocking {
        val tooLarge = ApiKeyQuota.MAX_PORTABLE_REQUESTS + 1
        val base = PanelApiKey(id = 3, name = "key", maxRequests = 10, requestCount = 2,
            deleteOnLimit = false, requestLimitReported = true)
        assertTrue(runCatching { PanelApi.createApiKey(session, "client", quota = ApiKeyQuota(tooLarge, false)) }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { PanelApi.updateApiKeyQuota(session, base, ApiKeyQuota(tooLarge, false)) }
            .exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test fun `an existing large quota is retained when only delete on limit is changed`() = runBlocking {
        val large = "3000000000"
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey(maximum = large)}],"total":1}"""))
        val base = PanelApi.apiKeys(session).single()
        server.enqueue(MockResponse().setBody(limitedKey(maximum = large, count = 3)))
        server.enqueue(MockResponse().setBody(limitedKey(maximum = large, count = 3, delete = true)))
        val updated = PanelApi.updateApiKeyQuota(session, base, ApiKeyQuota(3_000_000_000L, true))
        server.takeRequest(); server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(setOf("delete_on_limit"), body.keys().asSequence().toSet())
        assertTrue(body.getBoolean("delete_on_limit"))
        assertEquals(3_000_000_000L, updated.maxRequests)
    }

    @Test fun `quota patch is never replayed on retry after zero`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${limitedKey()}],"total":1}"""))
        val base = PanelApi.apiKeys(session).single()
        server.enqueue(MockResponse().setBody(limitedKey()))
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0"))
        val error = runCatching { PanelApi.updateApiKeyQuota(session, base, ApiKeyQuota(20, false)) }.exceptionOrNull()
        assertTrue(error is ApiKeyWriteUnconfirmedException)
        assertEquals(3, server.requestCount)
    }

}
