package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.TemplateOptions
import com.mrm.pgmanager.data.model.TemplateValidation
import com.mrm.pgmanager.data.model.UserTemplateItem
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
 * قرارداد CRUD تمپلت‌های کاربر با پنل.
 *
 * دو تلهٔ اصلی که این تست‌ها نگه می‌دارند:
 *
 *  ۱. مفرد/جمع — پیشوندِ روتر «/api/user_template» است ولی فهرست روی «s»
 *     سوار می‌شود. ساخت روی مفرد، فهرست روی جمع. یکدست‌کردن ⇒ 404.
 *
 *  ۲. شکلِ پاسخِ فهرست — برخلافِ /api/groups که {groups,total} می‌دهد،
 *     /api/user_templates یک **آرایهٔ خام** برمی‌گرداند. اگر کسی
 *     JSONObject از آن بسازد، استثنا می‌گیرد.
 */
class TemplatesApiTest {

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() = server.shutdown()

    // ── خواندن ───────────────────────────────────────────────

    @Test fun `userTemplates hits plural path and parses a bare array`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[
                  {"id":3,"name":"Gold","data_limit":107374182400,"expire_duration":2592000,
                   "hwid_limit":2,"username_prefix":"g-","username_suffix":"-vip",
                   "group_ids":[1,4],"status":"active","data_limit_reset_strategy":"month",
                   "on_hold_timeout":null,"reset_usages":true,"is_disabled":false,
                   "extra_settings":{"method":"aes-256-gcm"}},
                  {"id":5,"name":"Trial","data_limit":null,"expire_duration":null,
                   "group_ids":[],"data_limit_reset_strategy":"no_reset"}
                ]"""
            )
        )
        val tpls = PanelApi.userTemplates(session)
        val path = server.takeRequest().path!!

        assertTrue("must use plural /api/user_templates", path.startsWith("/api/user_templates"))
        assertEquals(2, tpls.size)

        val gold = tpls[0]
        assertEquals(3, gold.id)
        assertEquals("Gold", gold.name)
        assertEquals(107374182400L, gold.dataLimit)
        assertEquals(2592000L, gold.expireDuration)
        assertEquals(2, gold.hwidLimit)
        assertEquals("g-", gold.usernamePrefix)
        assertEquals("-vip", gold.usernameSuffix)
        assertEquals(listOf(1, 4), gold.groupIds)
        assertEquals("active", gold.status)
        assertEquals("month", gold.dataLimitResetStrategy)
        assertNull(gold.onHoldTimeout)
        assertEquals(true, gold.resetUsages)
        assertEquals(false, gold.isDisabled)
        assertEquals("aes-256-gcm", gold.ssMethod)
    }

    @Test fun `nulls and missing keys degrade to sane defaults`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":5,"name":"Trial"}]"""))
        val t = PanelApi.userTemplates(session).single()
        server.takeRequest()

        assertNull(t.dataLimit)
        assertNull(t.expireDuration)
        assertNull(t.hwidLimit)
        assertNull(t.usernamePrefix)
        assertNull(t.usernameSuffix)
        assertNull(t.status)
        assertNull(t.ssMethod)
        assertTrue(t.groupIds.isEmpty())
        // نبودِ کلید نباید رشتهٔ خالی بدهد — باید پیش‌فرضِ پنل باشد
        assertEquals(TemplateOptions.RESET_NO_RESET, t.dataLimitResetStrategy)
    }

    @Test fun `blank name falls back to a readable placeholder`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":11}]"""))
        val t = PanelApi.userTemplates(session).single()
        server.takeRequest()
        assertTrue("placeholder must mention the id", t.name.contains("11"))
    }

    @Test fun `failed list surfaces an error instead of an empty list`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val err = runCatching { runBlocking { PanelApi.userTemplates(session) } }.exceptionOrNull()
        assertTrue("403 must not be swallowed", err != null)
    }

    // ── ساخت ─────────────────────────────────────────────────

    @Test fun `create posts to singular path with full body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":8,"name":"Gold"}"""))
        PanelApi.createUserTemplate(
            session,
            UserTemplateItem(
                id = 0, name = "  Gold  ", dataLimit = 1024L, expireDuration = 86400L,
                hwidLimit = 3, usernamePrefix = "g-", usernameSuffix = "-x",
                groupIds = listOf(2, 7), status = TemplateOptions.STATUS_ACTIVE,
                dataLimitResetStrategy = "week", resetUsages = false,
                isDisabled = false, ssMethod = "chacha20-ietf-poly1305"
            )
        )
        val rec = server.takeRequest()
        assertEquals("POST", rec.method)
        assertEquals("must use singular /api/user_template", "/api/user_template", rec.path)

        val b = JSONObject(rec.body.readUtf8())
        assertEquals("name must be trimmed", "Gold", b.getString("name"))
        assertEquals(1024L, b.getLong("data_limit"))
        assertEquals(86400L, b.getLong("expire_duration"))
        assertEquals(3, b.getInt("hwid_limit"))
        assertEquals("g-", b.getString("username_prefix"))
        assertEquals("-x", b.getString("username_suffix"))
        assertEquals("week", b.getString("data_limit_reset_strategy"))
        assertEquals("active", b.getString("status"))
        assertFalse(b.getBoolean("reset_usages"))
        assertEquals(listOf(2, 7), b.getJSONArray("group_ids").let { a -> List(a.length()) { a.getInt(it) } })
        assertEquals("chacha20-ietf-poly1305", b.getJSONObject("extra_settings").getString("method"))
    }

    @Test fun `empty affixes are sent as null not empty string`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        PanelApi.createUserTemplate(
            session,
            UserTemplateItem(id = 0, name = "T", groupIds = listOf(1), usernamePrefix = "  ", usernameSuffix = "")
        )
        val b = JSONObject(server.takeRequest().body.readUtf8())
        // پنل رشتهٔ خالی را به validate_username می‌دهد؛ null امن‌تر است
        assertTrue("blank prefix must be null", b.isNull("username_prefix"))
        assertTrue("blank suffix must be null", b.isNull("username_suffix"))
    }

    @Test fun `on_hold_timeout is only sent when status is on_hold`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        PanelApi.createUserTemplate(
            session,
            UserTemplateItem(
                id = 0, name = "A", groupIds = listOf(1),
                status = TemplateOptions.STATUS_ACTIVE, onHoldTimeout = 3600L
            )
        )
        val active = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse("active template must not carry on_hold_timeout", active.has("on_hold_timeout"))

        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        PanelApi.createUserTemplate(
            session,
            UserTemplateItem(
                id = 0, name = "B", groupIds = listOf(1),
                status = TemplateOptions.STATUS_ON_HOLD, onHoldTimeout = 3600L
            )
        )
        val onHold = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(3600L, onHold.getLong("on_hold_timeout"))
    }

    @Test fun `absent optional numbers are sent as explicit null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        PanelApi.createUserTemplate(session, UserTemplateItem(id = 0, name = "T", groupIds = listOf(1)))
        val b = JSONObject(server.takeRequest().body.readUtf8())
        // «نامحدود» باید صریحاً null باشد تا مقدارِ قبلی را در PUT پاک کند
        assertTrue(b.isNull("data_limit"))
        assertTrue(b.isNull("expire_duration"))
        assertTrue(b.isNull("hwid_limit"))
        assertFalse("no extra_settings when method unset", b.has("extra_settings"))
    }

    @Test fun `create failure is reported`() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"name exists"}"""))
        val err = runCatching {
            runBlocking { PanelApi.createUserTemplate(session, UserTemplateItem(id = 0, name = "Dup", groupIds = listOf(1))) }
        }.exceptionOrNull()
        assertTrue("duplicate name must raise", err != null)
    }

    // ── ویرایش ───────────────────────────────────────────────

    @Test fun `modify uses PUT on singular path with id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":4,"name":"Silver"}"""))
        PanelApi.modifyUserTemplate(
            session, 4,
            UserTemplateItem(id = 4, name = "Silver", groupIds = listOf(3), dataLimit = 2048L)
        )
        val rec = server.takeRequest()
        assertEquals("PUT", rec.method)
        assertEquals("/api/user_template/4", rec.path)
        assertEquals("Silver", JSONObject(rec.body.readUtf8()).getString("name"))
    }

    // ── حذف ──────────────────────────────────────────────────

    @Test fun `delete uses DELETE on singular path and accepts 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        PanelApi.deleteUserTemplate(session, 6)
        val rec = server.takeRequest()
        assertEquals("DELETE", rec.method)
        assertEquals("/api/user_template/6", rec.path)
    }

    @Test fun `delete failure includes the status code`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"not found"}"""))
        val err = runCatching { runBlocking { PanelApi.deleteUserTemplate(session, 99) } }.exceptionOrNull()
        assertTrue(err != null)
        assertTrue("message should carry the code", err!!.message!!.contains("404"))
    }

    // ── اعتبارسنجی ───────────────────────────────────────────

    @Test fun `name must be non-empty and at most 64 chars`() {
        assertEquals(TemplateValidation.ERR_NAME_EMPTY, TemplateValidation.validateName("   "))
        assertNull(TemplateValidation.validateName("  ok  "))
        assertNull(TemplateValidation.validateName("a".repeat(64)))
        assertEquals(TemplateValidation.ERR_NAME_LONG, TemplateValidation.validateName("a".repeat(65)))
    }

    @Test fun `groups are required on create`() {
        assertEquals(TemplateValidation.ERR_NO_GROUP, TemplateValidation.validateGroups(emptyList()))
        assertNull(TemplateValidation.validateGroups(listOf(1)))
    }

    @Test fun `affix rules mirror the panel username validator`() {
        assertNull("empty is allowed", TemplateValidation.validateAffix(""))
        assertNull(TemplateValidation.validateAffix(null))
        assertNull(TemplateValidation.validateAffix("ab-c_1"))
        assertNull(TemplateValidation.validateAffix("a@b.c"))
        assertEquals(TemplateValidation.ERR_AFFIX_LONG, TemplateValidation.validateAffix("a".repeat(21)))
        assertEquals(TemplateValidation.ERR_AFFIX_CHARS, TemplateValidation.validateAffix("bad space"))
        assertEquals(TemplateValidation.ERR_AFFIX_CHARS, TemplateValidation.validateAffix("سلام"))
        assertEquals(TemplateValidation.ERR_AFFIX_CONSECUTIVE, TemplateValidation.validateAffix("a--b"))
        assertEquals(TemplateValidation.ERR_AFFIX_CONSECUTIVE, TemplateValidation.validateAffix("a-_b"))
    }

    @Test fun `expire duration must fit the panel range`() {
        assertNull(TemplateValidation.validateExpire(null))
        assertNull(TemplateValidation.validateExpire(0L))
        assertNull(TemplateValidation.validateExpire(UserTemplateItem.MAX_EXPIRE_SECONDS))
        assertEquals(TemplateValidation.ERR_EXPIRE_RANGE, TemplateValidation.validateExpire(-1L))
        assertEquals(
            TemplateValidation.ERR_EXPIRE_RANGE,
            TemplateValidation.validateExpire(UserTemplateItem.MAX_EXPIRE_SECONDS + 1)
        )
    }

    @Test fun `data limit must not be negative`() {
        assertNull(TemplateValidation.validateDataLimit(null))
        assertNull(TemplateValidation.validateDataLimit(0L))
        assertEquals(TemplateValidation.ERR_DATA_NEGATIVE, TemplateValidation.validateDataLimit(-5L))
    }

    @Test fun `validateAll reports the first problem and skips groups on edit`() {
        assertEquals(
            TemplateValidation.ERR_NAME_EMPTY,
            TemplateValidation.validateAll("", emptyList(), null, null, null, null)
        )
        assertEquals(
            TemplateValidation.ERR_NO_GROUP,
            TemplateValidation.validateAll("ok", emptyList(), null, null, null, null)
        )
        // در ویرایش، پنل group_ids را nullable می‌پذیرد
        assertNull(TemplateValidation.validateAll("ok", emptyList(), null, null, null, null, requireGroup = false))
        assertEquals(
            TemplateValidation.ERR_AFFIX_CONSECUTIVE,
            TemplateValidation.validateAll("ok", listOf(1), "a--b", null, null, null)
        )
        assertNull(TemplateValidation.validateAll("ok", listOf(1), "p-", "-s", 100L, 3600L))
    }

    @Test fun `option lists match the panel enums exactly`() {
        assertEquals(listOf("active", "on_hold"), TemplateOptions.STATUSES)
        assertEquals(
            listOf("no_reset", "day", "week", "month", "year"),
            TemplateOptions.RESET_STRATEGIES
        )
        assertEquals(
            listOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-poly1305"),
            TemplateOptions.SS_METHODS
        )
    }
}
