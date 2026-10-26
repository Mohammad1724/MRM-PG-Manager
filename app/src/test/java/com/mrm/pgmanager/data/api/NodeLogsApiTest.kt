package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * قراردادِ «جریانِ لاگِ نود» (`GET /api/node/{id}/logs`) روی سرورِ ساختگی.
 *
 * این endpoint تنها مسیرِ اپ است که JSON برنمی‌گرداند، پس چیزی که اینجا قفل
 * می‌شود در هیچ تستِ دیگری سنجیده نمی‌شود:
 *  ۱. هدرها (`Accept: text/event-stream` و `Cache-Control: no-cache`) — بدونِ آن‌ها
 *     بعضی واسطه‌ها بدنه را buffer می‌کنند و «لاگِ زنده» به لاگِ انباشته تبدیل می‌شود؛
 *  ۲. احرازِ هویت در دو حالت (JWT با `Bearer`، کلید API با `X-Api-Key`) — کلیدِ API
 *     تنها راهی است که مانیتورینگِ پس‌زمینه بعد از ۲۴ ساعت نمی‌میرد؛
 *  ۳. قاب‌های SSE همان‌طور که sse-starlette می‌فرستد خوانده می‌شوند (چند `data:` در
 *     یک رویداد = یک رویداد، خطِ خالی = پایانِ رویداد، `:` = keep-alive)؛
 *  ۴. `detail`ِ پنل در پیامِ خطا می‌ماند و ۴۰۱ به [SessionExpiredException]
 *     تبدیل می‌شود — وگرنه روی صفحه فقط «خطای شبکه» نوشته می‌شد.
 */
class NodeLogsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        server.shutdown()
    }

    /** جریان را تا پایانِ پاسخ می‌خواند؛ همان کاری که حلقۀ صفحه می‌کند. */
    private fun drain(connection: NodeLogConnection): List<String> {
        val out = ArrayList<String>()
        while (true) {
            val event = connection.nextEvent() ?: break
            out += event
        }
        connection.close()
        return out
    }

    @Test fun `stream asks for the log route with the sse contract`() = runBlocking {
        server.enqueue(MockResponse().setBody("data: one\n\ndata: two\n\n"))
        val events = drain(PanelApi.openNodeLogs(session, 7))

        assertEquals(listOf("one", "two"), events)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/node/7/logs", request.path)
        assertEquals("text/event-stream", request.getHeader("Accept"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
        assertEquals("Bearer tok", request.getHeader("Authorization"))
    }

    @Test fun `api key session authenticates the stream without a bearer token`() = runBlocking {
        val keySession = Session(
            server.url("/").toString().trimEnd('/'),
            "pg_key_11111111-1111-4111-8111-111111111111",
            "ci"
        )
        server.enqueue(MockResponse().setBody("data: x\n\n"))
        assertEquals(listOf("x"), drain(PanelApi.openNodeLogs(keySession, 3)))

        val request = server.takeRequest()
        assertEquals(keySession.token, request.getHeader("X-Api-Key"))
        assertNull("کلیدِ API نباید در Bearer برود", request.getHeader("Authorization"))
    }

    @Test fun `keep-alive comments and unused fields never become log lines`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(": ping - 2026-10-26 10:00:00 UTC\n\ndata: a\ndata: b\n\nevent: log\ndata: c\n\n")
        )
        // دو رویدادِ اول: اولین رویداد خالی است (فقط کامنت) و نباید خطِ ساختگی بسازد.
        assertEquals(listOf("a\nb", "c"), drain(PanelApi.openNodeLogs(session, 1)))
    }

    @Test fun `panel detail survives on failure so the banner can show why`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"detail":"missing nodes.logs permission"}""")
        )
        val error = runCatching { PanelApi.openNodeLogs(session, 2) }.exceptionOrNull()

        assertTrue("خطای نوعِ پاسخ‌بدنه باید بماند: $error", error is IllegalStateException)
        val message = error?.message.orEmpty()
        assertTrue("کدِ پاسخ باید در پیام باشد: $message", message.contains("403"))
        assertTrue("detail پنل باید در پیام باشد: $message", message.contains("nodes.logs"))
    }

    @Test fun `expired session on the stream is a session expiry not a generic failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { PanelApi.openNodeLogs(session, 2) }.exceptionOrNull()
        // صفحه با همین تشخیص «نشست منقضی» را از «پنل در دسترس نیست» جدا می‌کند.
        assertTrue("۴۰۱ باید به SessionExpiredException تبدیل شود: $error", PanelApi.isUnauthorized(error))
    }

    @Test fun `closing the stream ends the reader instead of throwing`() = runBlocking {
        // این همان لحظه‌ای است که کاربر صفحه را می‌بندد: خواننده باید `null`
        // بگیرد، نه استثنای okio («closed»)، وگرنه هر خروج از صفحه یک کرش بود.
        server.enqueue(MockResponse().setBody("data: one\n\n"))
        val connection = PanelApi.openNodeLogs(session, 9)
        assertEquals("one", connection.nextEvent())

        connection.close()
        connection.close() // onDispose + finallyِ صفحه — بستنِ دوباره بی‌خطر است
        assertTrue(connection.closed)
        assertNull(connection.nextEvent())
    }
}
