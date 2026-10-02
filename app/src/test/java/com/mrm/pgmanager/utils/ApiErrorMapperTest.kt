package com.mrm.pgmanager.utils

import com.mrm.pgmanager.data.api.SessionExpiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * قراردادِ نگاشتِ خطا (فاز ۵.۱) — بدونِ Context، پس روی JVM خالص تست می‌شود.
 *
 * این تست‌ها همان چیزی را قفل می‌کنند که کاربر در نهایت *نمی‌بیند*: متنِ خامِ
 * استثنا. اگر کسی دسته‌بندی را بشکند، اول اینجا سرخ می‌شود، نه روی دستگاه.
 */
class ApiErrorMapperTest {

    // ── تشخیصِ دسته (خالص) ──────────────────────────────────────────────

    @Test fun `dns failure is offline`() {
        assertEquals(ApiErrorKind.OFFLINE, ApiErrorMapper.kindOf(UnknownHostException("panel.example.com")))
    }

    @Test fun `socket timeout is timeout`() {
        assertEquals(ApiErrorKind.TIMEOUT, ApiErrorMapper.kindOf(SocketTimeoutException("timeout")))
    }

    @Test fun `tls handshake failure is tls`() {
        assertEquals(ApiErrorKind.TLS, ApiErrorMapper.kindOf(SSLHandshakeException("Cert path not trusted")))
    }

    @Test fun `generic io exception is offline not server`() {
        // OkHttp وقتی هیچ نودی پاسخ ندهد «retry exhausted» می‌دهد؛ کاربر باید
        // «دوباره تلاش کن» ببیند، نه «خطای سرور».
        assertEquals(ApiErrorKind.OFFLINE, ApiErrorMapper.kindOf(IOException("retry exhausted")))
    }

    @Test fun `session expiry wins over everything`() {
        assertEquals(ApiErrorKind.UNAUTHORIZED, ApiErrorMapper.kindOf(SessionExpiredException()))
    }

    @Test fun `panel 403 maps to forbidden and is not transient`() {
        val e = IllegalStateException("""Request failed: 403 {"detail":"Not enough permissions"}""")
        val kind = ApiErrorMapper.kindOf(e)

        assertEquals(ApiErrorKind.FORBIDDEN, kind)
        assertFalse("403 با تلاش دوباره درست نمی‌شود", ApiErrorMapper.isTransient(kind))
    }

    @Test fun `panel 404 maps to not found`() {
        assertEquals(ApiErrorKind.NOT_FOUND, ApiErrorMapper.kindOf(IllegalStateException("User fetch failed: 404")))
    }

    @Test fun `panel 4xx maps to validation and keeps detail`() {
        val e = IllegalStateException("""Request failed: 422 {"detail":"data_limit must be a number"}""")

        assertEquals(ApiErrorKind.VALIDATION, ApiErrorMapper.kindOf(e))
        assertEquals("data_limit must be a number", ApiErrorMapper.panelDetail(e))
    }

    @Test fun `panel 5xx maps to server and is transient`() {
        val e = IllegalStateException("Request failed: 502 Bad Gateway")

        assertEquals(ApiErrorKind.SERVER, ApiErrorMapper.kindOf(e))
        assertTrue(ApiErrorMapper.isTransient(ApiErrorKind.SERVER))
    }

    @Test fun `http code parsing ignores non http numbers`() {
        // «Invalid URL» یک شمارهٔ تصادفی نیست؛ نباید به‌عنوانِ کدِ HTTP خوانده شود.
        assertNull(ApiErrorMapper.httpCodeOf(IllegalStateException("Invalid URL")))
        assertNull(ApiErrorMapper.httpCodeOf(IllegalStateException("Login failed: 200 ok")))
        assertEquals(429, ApiErrorMapper.httpCodeOf(IllegalStateException("Request failed: 429 too many")))
    }

    // ── متنِ فنی و پیامِ پنل ────────────────────────────────────────────

    @Test fun `technical text carries type and message for the expandable detail`() {
        val detail = ApiErrorMapper.technical(UnknownHostException("Failed to connect to /10.0.0.5:8000"))

        assertTrue(detail!!.startsWith("UnknownHostException: "))
        assertTrue(detail.contains("10.0.0.5"))
    }

    @Test fun `technical is null when there is nothing to show`() {
        assertNull(ApiErrorMapper.technical(IOException()))
        assertNull(ApiErrorMapper.technical(null))
    }

    @Test fun `panel detail is null when the panel did not send one`() {
        assertNull(ApiErrorMapper.panelDetail(IllegalStateException("Delete failed: 500")))
        assertNull(ApiErrorMapper.panelDetail(null))
    }

    // ── تفکیکِ منشأ: خطای فایل نباید «پنل در دسترس نیست» بشود ───────────

    @Test fun `local io failure is storage not network`() {
        assertEquals(
            ApiErrorKind.LOCAL_STORAGE,
            ApiErrorMapper.kindOf(FileNotFoundException("backup.mrm"), ErrorOrigin.LOCAL)
        )
        // همان استثنا در مسیرِ شبکه یعنی «پنل در دسترس نیست».
        assertEquals(
            ApiErrorKind.OFFLINE,
            ApiErrorMapper.kindOf(FileNotFoundException("backup.mrm"), ErrorOrigin.NETWORK)
        )
    }

    @Test fun `revoked SAF permission is reported as permission`() {
        assertEquals(
            ApiErrorKind.LOCAL_PERMISSION,
            ApiErrorMapper.kindOf(SecurityException("Permission Denial"), ErrorOrigin.LOCAL)
        )
    }
}
