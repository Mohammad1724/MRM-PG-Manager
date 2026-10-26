package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * تستِ رفتاریِ **صفحهٔ «کلیدهای API»** — چیزی که تستِ قرارداد نمی‌بیند.
 *
 * `ApiKeysApiTest` می‌گوید مسیرها و بدنه‌ها درست‌اند؛ اینجا سنجیده می‌شود که خودِ
 * صفحه همان چیزی را نشان می‌دهد که ادعا می‌کند: فقط متنِ ماسک‌شده (نه رازِ
 * کامل)، حفظِ ردیف‌ها وقتی رفرش شکست می‌خورد، دیالوگِ ساختی که بدونِ نام
 * اجازهٔ ساخت نمی‌دهد، و غیب‌شدنِ اکشن‌ها وقتی نقشِ ادمین نوشتن ندارد.
 *
 * ### قاعدهٔ نوشتنِ این تست‌ها (یک‌بار گران تمام شد)
 * صفحه **هرگز** نباید در حالی که `CircularProgressIndicator` روی آن است مونتاژ
 * شود: انیمیشنِ بی‌پایان هیچ‌وقت idle نمی‌شود، `setContent`/`performClick` تا ۶۰
 * ثانیه بلوکه می‌ماند و چون لوپرِ اصلیِ Robolectric در همان لحظه در اختیارِ تست
 * است، ادامهٔ کوروتینِ شبکه (که روی Main می‌آید) هم اجرا نمی‌شود — قفلِ کامل،
 * آن‌قدر شدید که تست‌هایِ بعد از این کلاس هم سرخ می‌شدند. دو نتیجهٔ عملی:
 *  ۱. کش با داده پر می‌شود تا `loading` از همان فریمِ اول false باشد؛
 *  ۲. هیچ اکشنی که `busy` را روشن می‌کند لمس نمی‌شود (submitِ دیالوگِ ساخت،
 *     چرخش، حذف). پس «نمایشِ یک‌بارهٔ کلید» اینجا تست نمی‌شود — همان مسیر در
 *     `ApiKeysApiTest` روی لایهٔ داده قفل شده است. اضافه‌کردنِ `waitForIdle` یا
 *     لمسِ «بساز» به این فایل، همان قفل را برمی‌گرداند؛ دست نزنید.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class ApiKeysScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    private fun str(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        PanelApi.allowCleartextLoopbackForTests = true
        PanelCache.clear()
        AdminAccess.reset()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() {
        AdminAccess.reset()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    /** پاسخِ `GET /api/api_keys` از دو کلیدِ ساختگی. */
    private fun keysBody(): String =
        """{"api_keys":[
             {"id":3,"admin_id":1,"name":"widget","note":"رویِ سرورِ ci","created_at":"2026-10-01T10:00:00Z",
              "api_key_trimmed":"pg_key_…f7a2","status":"active","is_expired":false,
              "expire_date":null,"inherit_permissions":true},
             {"id":4,"admin_id":1,"name":"ci","note":null,"created_at":"2026-10-02T10:00:00Z",
              "api_key_trimmed":"pg_key_…11bc","status":"disabled","is_expired":false,
              "expire_date":"2026-10-05T10:00:00Z","inherit_permissions":true}
           ],"total":2}"""

    private val seedKeys = listOf(
        PanelApiKey(id = 3, name = "widget", trimmed = "pg_key_…f7a2"),
        PanelApiKey(id = 4, name = "ci", trimmed = "pg_key_…11bc", status = PanelApiKey.DISABLED)
    )

    /** کشِ تازه ⇒ صفحه بدونِ حالتِ بارگذاری (و بدونِ اسپینرِ بی‌پایان) ساخته می‌شود. */
    private fun seedCache(keys: List<PanelApiKey> = seedKeys) =
        PanelCache.put(PanelCache.apiKeysKey(session.baseUrl), keys)

    /**
     * @param adminBody پاسخِ `GET /api/admin` — برای تست‌هایِ RBAC. `null` یعنی
     *   مجوزها نامعلوم می‌مانند و اپ «همه‌چیز مجاز» را پیش می‌گیرد.
     */
    private fun installDispatcher(
        keysResponse: MockResponse = MockResponse().setBody(keysBody()),
        adminBody: String? = null
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return when {
                    path == "/api/admin" -> adminBody?.let { MockResponse().setBody(it) }
                        ?: MockResponse().setResponseCode(404)
                    path == "/api/api_keys" -> keysResponse
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun render() = rule.setContent {
        ApiKeysScreen(session = session, onBack = {})
    }

    private fun await(what: String, observed: () -> String, condition: () -> Boolean) {
        try {
            rule.waitUntil(15_000, condition)
        } catch (timeout: Throwable) {
            throw AssertionError("$what محقق نشد؛ دیده‌شده: ${observed()}", timeout)
        }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun countDesc(desc: String) = rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().size

    @Test fun row_shows_masked_key_and_never_the_secret() {
        seedCache()
        installDispatcher()
        render()

        await("ردیفِ کلیدِ ماسک‌شده", observed = { "تعداد: ${count("pg_key_…f7a2")}" }) {
            count("pg_key_…f7a2") > 0
        }
        // دو وضعیت از هم جدا می‌مانند: یکی فعال، یکی غیرفعال.
        assertTrue("وضعیتِ فعال باید دیده شود", count(str(R.string.ak_status_active)) > 0)
        assertTrue("وضعیتِ غیرفعال باید دیده شود", count(str(R.string.ak_status_disabled)) > 0)
        // بی‌انقضا در برابرِ کلیدِ گذشته: «هرگز» نه یک خطِ خالی.
        assertTrue("ردیفِ بی‌انقضا باید «هرگز» داشته باشد", count(str(R.string.ak_no_expiry)) > 0)
        // متنِ کاملِ کلید هیچ‌حق ندارد در فهرست باشد.
        assertEquals(0, count("pg_key_full_secret"))
        // یادداشت فقط وقتی هست نشان داده می‌شود.
        assertTrue("یادداشتِ کلید باید دیده شود", count("رویِ سرورِ ci") > 0)
    }

    @Test fun failed_refresh_keeps_the_rows_it_already_showing() {
        seedCache()
        // ۴۰۳ روی رفرش: فهرستِ کش‌شده باید بماند، نه اینکه صفحه خالی شود.
        installDispatcher(keysResponse = MockResponse().setResponseCode(403).setBody("""{"detail":"nope"}"""))
        render()

        await("پاسخِ خطا ثبت شود", observed = { "درخواست‌ها: ${server.requestCount}" }) {
            server.requestCount > 0
        }
        assertEquals("ردیف‌ها نباید با یک خطای رفرش پاک شوند", 1, count("pg_key_…f7a2"))
        assertEquals("حالتِ خالی نباید جای دادهٔ کش‌شده را بگیرد", 0, count(str(R.string.ak_empty)))
    }

    @Test fun create_dialog_requires_a_name() {
        seedCache()
        installDispatcher()
        render()

        val addDesc = str(R.string.ak_create)
        await("دکمهٔ ساختِ سربرگ", observed = { "تعداد: ${countDesc(addDesc)}" }) { countDesc(addDesc) > 0 }
        // کفِ ناحیهٔ لمسِ دکمه‌های آیکونیِ همین صفحه.
        rule.onNodeWithContentDescription(addDesc).assertHeightIsAtLeast(40.dp)
        rule.onNodeWithContentDescription(addDesc).performClick()

        await("دیالوگِ ساخت", observed = { "تعدادِ راهنما: ${count(str(R.string.ak_create_hint))}" }) {
            count(str(R.string.ak_create_hint)) > 0
        }
        // سه گزینهٔ انقضا + «هرگز» — چیپ‌ها، نه DatePicker.
        assertTrue("گزینهٔ ۳۰ روز باید باشد", count(str(R.string.ak_days_30)) > 0)
        assertTrue("گزینهٔ هرگز نباید منقضی شود باید باشد", count(str(R.string.ak_never)) > 0)

        val submit = str(R.string.ak_create_action)
        assertEquals("دکمهٔ تأیید باید یکتا باشد (برچسبِ جدا از عنوانِ دیالوگ)", 1, count(submit))
        rule.onNodeWithText(submit).assertIsNotEnabled()

        val fields = rule.onAllNodes(hasSetTextAction())
        assertTrue("دیالوگِ ساخت باید فیلدِ متنی داشته باشد", fields.fetchSemanticsNodes().isNotEmpty())
        fields[0].performTextInput("widget")

        // `performTextInput` خودش بینِ فریم‌ها sync می‌کند، پس این assertion
        // بی‌واسطه درست است؛ حالتِ busy (اسپینر) هنوز شروع نشده.
        rule.onNodeWithText(submit).assertIsEnabled()
    }

    @Test fun rows_hide_every_action_when_the_role_cannot_write() {
        seedCache()
        // نقشِ فقط‌خواندن: api_keys.read بدونِ create/update/delete.
        installDispatcher(
            adminBody = """{"id":1,"username":"ops","role":{"id":2,"name":"Viewer","is_owner":false,
                          "permissions":{"api_keys":{"read":true}}}}"""
        )
        runBlocking { AdminAccess.refresh(session, force = true) }

        render()

        await("ردیف‌ها با مجوزِ خواندن هم ساخته می‌شوند", observed = { "تعداد: ${count("pg_key_…f7a2")}" }) {
            count("pg_key_…f7a2") > 0
        }
        assertEquals("بدونِ create دکمهٔ ساخت نباید باشد", 0, countDesc(str(R.string.ak_create)))
        assertEquals("بدونِ update دکمهٔ چرخش نباید باشد", 0, countDesc(str(R.string.ak_rotate)))
        assertEquals("بدونِ delete دکمهٔ حذف نباید باشد", 0, countDesc(str(R.string.ak_delete)))
        assertEquals("بدونِ update دکمهٔ فعال/غیرفعال نباید باشد", 0, countDesc(str(R.string.ak_disable)))
    }

    @Test fun empty_list_offers_the_create_path() {
        seedCache(keys = emptyList())
        installDispatcher(keysResponse = MockResponse().setBody("""{"api_keys":[],"total":0}"""))
        render()

        await("حالتِ خالی", observed = { "تعداد: ${count(str(R.string.ak_empty))}" }) {
            count(str(R.string.ak_empty)) > 0
        }
        // حالتِ خالی باید راهِ حل را هم پیشنهاد کند، نه فقط «چیزی نیست».
        assertTrue("توضیحِ حالتِ خالی باید دیده شود", count(str(R.string.ak_empty_hint)) > 0)
        assertEquals("دکمهٔ ساختِ درونِ حالتِ خالی", 1, count(str(R.string.ak_create)))
    }
}
