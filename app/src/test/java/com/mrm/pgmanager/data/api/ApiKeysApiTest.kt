package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelApiKey
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
    }

    @Test fun `expired key is not active even with status active`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"api_keys":[${keyJson(9, "active", true)}],"total":1}"""))
        val keys = PanelApi.apiKeys(session)

        assertFalse("is_expired باید بر status مقدم باشد", keys[0].isActive)
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
        assertFalse(updated.isActive)
    }

    @Test fun `delete uses the singular path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteApiKey(session, keyId = 8)
        val request = server.takeRequest()

        assertEquals("DELETE", request.method)
        assertEquals("/api/api_key/8", request.path)
    }
}
