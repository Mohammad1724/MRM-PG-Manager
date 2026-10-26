package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PermissionScope
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * قراردادِ «اپراتورها و نقش‌ها» روی سرورِ ساختگی.
 *
 * آنچه اینجا قفل می‌شود همان چیزی است که صفحهٔ نقش‌ها را از یک فهرستِ تزئینی
 * جدا می‌کند:
 *  ۱. نگاشتِ مجوزها `resource → action → scope` است و سه شکلِ مقدار دارد:
 *     `true` (دامنهٔ کامل)، `{"scope": 1}` (فقط کاربرانِ خودش) و `null`/غایب
 *     (ممنوع). ممنوع‌ها **نباید** در نگاشت بیایند، وگرنه صفحه نقشِ بی‌مجوز را
 *     پرنشان نشان می‌دهد.
 *  ۲. ادمین‌ها `role.name` و پرچم‌های `is_disabled`/`is_limited` را دارند؛
 *     `PanelAdmin` قبلاً هیچ‌کدام را نمی‌خواند و «نقشِ هر اپراتور» در دسترس نبود.
 */
class AdminsRolesApiTest {

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

    @Test fun `roles read the envelope and keep scope semantics`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"roles":[
                     {"id":1,"name":"Owner","is_owner":true,"created_at":"2026-01-01T00:00:00Z",
                      "permissions":{"users":{"create":true,"read":{"scope":2},"delete":null}},
                      "limits":{"max_users":null},"hwid":{"mode":"use_global"}},
                     {"id":2,"name":"Reseller","is_owner":false,"created_at":"2026-01-02T00:00:00Z",
                      "permissions":{"users":{"read":{"scope":1},"update":{"scope":1},
                                              "reset_usage":{"scope":1},"delete":null},
                                     "api_keys":{"create":true}},
                      "limits":{"max_users":25},"hwid":{"mode":"force_on"}}
                   ],"total":2}"""
            )
        )
        val list = PanelApi.adminRoles(session)

        assertEquals("/api/admin-roles?offset=0&limit=100", server.takeRequest().path)
        assertEquals(2, list.total)
        assertEquals(2, list.roles.size)

        val owner = list.roles[0]
        assertTrue(owner.isOwner)
        assertEquals(PermissionScope.ALL, owner.permissions["users"]?.get("read"))
        // اکشنِ null یعنی ممنوع — نباید کلید بسازد.
        assertFalse(owner.permissions["users"]?.containsKey("delete") ?: false)
        assertNull(owner.maxUsers)

        val reseller = list.roles[1]
        assertEquals("Reseller", reseller.name)
        assertEquals(PermissionScope.OWN, reseller.permissions["users"]?.get("read"))
        assertEquals(PermissionScope.OWN, reseller.permissions["users"]?.get("reset_usage"))
        assertEquals(25, reseller.maxUsers)
        assertEquals("force_on", reseller.hwidMode)
        // ۳ مجوز در users (منهای delete) + ۱ در api_keys
        assertEquals(4, reseller.allowedActionCount)
    }

    @Test fun `missing permissions object yields an empty map not a crash`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"roles":[{"id":7,"name":"Empty","is_owner":false}],"total":1}"""))
        val role = PanelApi.adminRoles(session).roles.single()

        assertTrue(role.permissions.isEmpty())
        assertEquals(0, role.allowedActionCount)
        assertNull(role.createdAt)
        assertNull(role.hwidMode)
    }

    @Test fun `admins carry their role name and status flags`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"admins":[
                     {"id":1,"username":"boss","status":"active","total_users":12,
                      "used_traffic":2048,"data_limit":null,"is_disabled":false,"is_limited":false,
                      "role":{"id":1,"name":"Owner","is_owner":true}},
                     {"id":2,"username":"ops","status":"active","total_users":3,
                      "used_traffic":0,"data_limit":1024,"is_disabled":true,"is_limited":false,
                      "role":{"id":2,"name":"Operator","is_owner":false}}
                   ],"total":2,"active":1,"disabled":1,"limited":0}"""
            )
        )
        val admins = PanelApi.admins(session)

        assertEquals(2, admins.size)
        assertEquals("Owner", admins[0].roleName)
        assertTrue(admins[0].isOwner)
        assertFalse(admins[0].isDisabled)
        assertEquals("Operator", admins[1].roleName)
        // `is_disabled` حتی وقتی `status` هنوز "active" است باید برداشته شود.
        assertTrue(admins[1].isDisabled)
        assertEquals(1024L, admins[1].dataLimit)
    }

    @Test fun `status string alone is enough to mark an admin disabled`() = runBlocking {
        // بعضی نسخه‌ها پرچمِ محاسبه‌شده را نمی‌فرستند؛ `status` باید کافی باشد.
        server.enqueue(
            MockResponse().setBody(
                """{"admins":[{"id":3,"username":"old","status":"disabled","total_users":0,
                     "used_traffic":0,"data_limit":null,"role":{"id":2,"name":"Operator","is_owner":false}}],
                   "total":1,"active":0,"disabled":1,"limited":0}"""
            )
        )
        assertTrue(PanelApi.admins(session).single().isDisabled)
    }
}
