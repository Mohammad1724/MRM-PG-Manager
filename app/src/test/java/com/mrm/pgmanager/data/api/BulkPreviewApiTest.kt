package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * قراردادِ «پیش‌نمایشِ عملیاتِ گروهی» (فاز ۴ — اعتماد و ایمنی).
 *
 * پنل از v5 فیلدِ `dry_run` را در `BulkUserFilter` دارد و در
 * `UserOperation.bulk_modify_expire/datalimit` وقتی true باشد فقط
 * `BulkOperationDryRunResponse{affected_users}` را برمی‌گرداند و **هیچ تغییری
 * اعمال نمی‌کند**. این تست‌ها سه چیز را قفل می‌کنند:
 *
 *  ۱. مسیر و بدنهٔ درست (`/api/users/bulk/expire` و `/api/users/bulk/data_limit`).
 *  ۲. `amount = 0` در درخواستِ پیش‌نمایش — یعنی حتی اگر پنلِ نسخه‌های آینده
 *     `dry_run` را نادیده بگیرد، عملیاتِ اعمال‌شده بی‌اثر است (صفر ثانیه/بایت).
 *  ۳. قراردادِ «نامعلوم»: اگر پاسخ `affected_users` نداشت، عددِ منفی برمی‌گردد و
 *     هرگز به‌عنوانِ «صفر کاربر» به کاربر نشان داده نمی‌شود.
 *
 * تستِ آخر (`bulkAddDays` با مقدارِ منفی) معنای «بازگرداندن» (Undo) را قفل
 * می‌کند: معکوسِ +۳۰ روز دقیقاً −۳۰ روز است، همان‌طور که کاربر انتظار دارد.
 */
class BulkPreviewApiTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `bulkPreview for days hits expire path with dry_run and zero amount`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"dry_run":true,"affected_users":3}"""))

        val affected = PanelApi.bulkPreview(session, setOf(1L, 2L, 5L), "days")

        val request = server.takeRequest()
        assertTrue("must use /api/users/bulk/expire", request.path!!.startsWith("/api/users/bulk/expire"))

        val body = JSONObject(request.body.readUtf8())
        assertTrue("dry_run must be true", body.getBoolean("dry_run"))
        // صفر بودنِ مقدار، شبکهٔ امنیتیِ دوم است: حتی اگر پنل dry_run را نادیده بگیرد،
        // «افزودن ۰ ثانیه» هیچ کاربری را تغییر نمی‌دهد.
        assertEquals(0, body.getInt("amount"))
        assertEquals(3, body.getJSONArray("users").length())

        assertEquals(3, affected)
    }

    @Test fun `bulkPreview for data hits data_limit path`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"dry_run":true,"affected_users":2}"""))

        val affected = PanelApi.bulkPreview(session, setOf(7L), "data")

        val path = server.takeRequest().path!!
        assertTrue("must use /api/users/bulk/data_limit", path.startsWith("/api/users/bulk/data_limit"))
        assertEquals(2, affected)
    }

    @Test fun `bulkPreview reports unknown as negative instead of zero`() = runBlocking {
        // پنلِ قدیمی‌تر یا پاسخِ ناقص: کلیدِ affected_users نیست.
        server.enqueue(MockResponse().setBody("""{"detail":"ok"}"""))

        val affected = PanelApi.bulkPreview(session, setOf(4L), "days")

        assertTrue("unknown must not be reported as 0 users", affected < 0)
    }

    @Test fun `bulkPreview without selection sends no request`() = runBlocking {
        val affected = PanelApi.bulkPreview(session, emptySet(), "days")

        assertEquals(0, affected)
        assertEquals("no request should be sent", 0, server.requestCount)
    }

    @Test fun `negative amount is the exact inverse of adding days`() = runBlocking {
        // Undo برای «افزودن ۳۰ روز» باید ۳۰×۸۶۴۰۰ ثانیه با علامتِ منفی بفرستد.
        server.enqueue(MockResponse().setBody("""{"detail":"ok"}"""))

        PanelApi.bulkAddDays(session, setOf(1L, 2L), -30)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(-30L * 86_400L, body.getLong("amount"))
        assertFalse("undo must not set dry_run", body.has("dry_run"))
    }
}
