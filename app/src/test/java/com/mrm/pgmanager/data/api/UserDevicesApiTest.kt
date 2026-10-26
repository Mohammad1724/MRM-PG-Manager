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
 * قراردادِ «دستگاه‌های کاربر» (`/api/user/{id}/hwids*`) روی سرورِ ساختگی.
 *
 * سه چیز اینجا قفل می‌شود که خطایشان در عمل گران است:
 *  ۱. پاکتِ پاسخ `{"hwids": [...], "count": N}` است، نه آرایهٔ خالص؛
 *  ۲. `hwid` یک شناسهٔ مبهم است و **باید** URL-encode شود — هویِدهایی که
 *     `+`، `/` یا `=` دارند با مسیرِ خام ۴۰۴ می‌گیرند (`+` در مسیر به فاصله
 *     تبدیل نمی‌شود ولی در بعضی پروکسی‌ها می‌شود، و `/` مسیر را می‌شکند)؛
 *  ۳. «حذفِ همه» یک `POST …/hwids/reset` است، نه چند `DELETE`.
 */
class UserDevicesApiTest {

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

    @Test fun `device list reads the envelope and keeps optional fields`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"hwids":[
                     {"id":5,"hwid":"a1b2c3","device_os":"Android","os_version":"14",
                      "device_model":"Pixel 7","created_at":"2026-09-01T10:00:00Z",
                      "last_used_at":"2026-10-20T08:30:00Z"},
                     {"id":6,"hwid":"d4e5f6","device_os":null,"os_version":null,
                      "device_model":null,"created_at":"2026-09-02T10:00:00Z",
                      "last_used_at":null}
                   ],"count":2}"""
            )
        )
        val devices = PanelApi.userDevices(session, userId = 42)

        assertEquals("/api/user/42/hwids", server.takeRequest().path)
        assertEquals(2, devices.size)
        assertEquals("Pixel 7", devices[0].deviceModel)
        assertEquals("2026-10-20T08:30:00Z", devices[0].lastUsedAt)
        // `null` نباید به رشتهٔ "null" تبدیل شود — دیالوگ آن را نمایش می‌داد.
        assertNull(devices[1].deviceOs)
        assertNull(devices[1].lastUsedAt)
    }

    @Test fun `forget encodes the hwid so odd characters keep the path intact`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        PanelApi.deleteUserDevice(session, userId = 42, hwid = "a+b/c=d")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        // به‌جای مقایسهٔ رشتهٔ درصدی (که به سلیقهٔ کدگذار وابسته است)، خودِ
        // بخشِ مسیر را می‌سنجیم: اگر `hwid` encode نشود، `/` آن را به دو
        // segment می‌شکند و آخرین segment دیگر کلِ شناسه نیست (→ ۴۰۴).
        val url = requireNotNull(request.requestUrl)
        assertEquals("a+b/c=d", url.pathSegments.last())
        assertEquals("/api/user/42/hwids/", "/" + url.pathSegments.dropLast(1).joinToString("/") + "/")
        assertTrue("مسیر نباید اسلشِ خام داشته باشد", url.encodedPath.contains("hwids/"))
    }

    @Test fun `reset all is a single POST on the reset route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"count":3}"""))
        PanelApi.resetUserDevices(session, userId = 42)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/user/42/hwids/reset", request.path)
    }
}
