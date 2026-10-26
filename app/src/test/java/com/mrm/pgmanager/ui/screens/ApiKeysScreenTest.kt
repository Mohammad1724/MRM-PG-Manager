package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * کامل)، حفظِ ردیف‌ها وقتی رفرش شکست می‌خورد، دیالوگِ ساختی که بدونِ نام اجازهٔ
 * ساخت نمی‌دهد، و غیب‌شدنِ اکشن‌ها وقتی نقشِ ادمین نوشتن ندارد.
 *
 * ### دو قاعدهٔ این فایل (هر دو یک بار CI را سرخ کردند)
 * ۱. **صفحه با اسپینرِ بی‌پایان مونتاژ نشود.** `CircularProgressIndicator`
 *    هرگز idle نمی‌شود؛ `setContent`/`performClick` تا ۶۰ ثانیه بلوکه می‌ماند و
 *    چون لوپرِ اصلیِ Robolectric در اختیارِ تست است، ادامهٔ کوروتینِ شبکه هم جلو
 *    نمی‌آید — قفلِ کامل، آن‌قدر شدید که تست‌هایِ بعد از این کلاس هم سرخ شدند.
 *    راهش همان مسیرِ تولید است: `PanelCache` را پر کن تا `loading` از همان
 *    فریمِ اول false باشد. برای همین `ApiKeysScreen` هم مثلِ بقیهٔ صفحه‌های
 *    سطح‌سرور با کشِ تازه دوباره درخواست نمی‌فرستد.
 * ۲. **هرگز از درونِ شرطِ `waitUntil` گره‌های semantis نخوان.** هر
 *    `fetchSemanticsNodes()` خودش `runOnIdle` می‌کند؛ داخلِ شرط یعنی
 *    `waitForIdle` تودرتو و همان قفلِ بالا. شرط‌ها فقط شمارندهٔ سرور باشند و
 *    بررسیِ متن بعد از آن، بی‌واسطه.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class ApiKeysScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    private val seedKeys = listOf(
        PanelApiKey(id = 3, name = "widget", trimmed = "pg_key_…f7a2", note = "رویِ سرورِ ci"),
        PanelApiKey(id = 4, name = "ci", trimmed = "pg_key_…11bc", status = PanelApiKey.DISABLED)
    )

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
        PanelCache.resetClockForTests()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    /** کشِ **تازه** ⇒ صفحه بی‌درخواستِ شبکه ساخته می‌شود (قاعدهٔ ۱). */
    private fun seedFresh(keys: List<PanelApiKey> = seedKeys) =
        PanelCache.put(PanelCache.apiKeysKey(session.baseUrl), keys)

    /** کشِ **کهنه** ⇒ همان اول یک رفرش راه می‌افتد؛ برای تستِ مسیرِ خطا. */
    private fun seedStale(keys: List<PanelApiKey> = seedKeys) {
        seedFresh(keys)
        // `at` با elapsedRealtimeِ Robolectric (عددِ کوچک) ثبت شده؛ این ساعت، سن
        // را از DefaultFreshMs رد می‌کند.
        PanelCache.clock = { 1_000_000_000L }
    }

    /**
     * @param keysBody پاسخِ `GET /api/api_keys`؛ `null` یعنی ۴۰۳ (مسیرِ خطا).
     * @param adminBody پاسخِ `GET /api/admin` برای نقش‌دادنِ تستِ RBAC.
     */
    private fun installResponses(keysBody: String? = null, adminBody: String? = null) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore('?')) {
                    "/api/admin" -> adminBody?.let { MockResponse().setBody(it) }
                        ?: MockResponse().setResponseCode(404)
                    "/api/api_keys" -> keysBody?.let { MockResponse().setBody(it) }
                        ?: MockResponse().setResponseCode(403).setBody("""{"detail":"nope"}""")
                    else -> MockResponse().setResponseCode(404)
                }
        }
    }

    private fun render() = rule.setContent {
        ApiKeysScreen(session = session, onBack = {})
    }

    /** قاعدهٔ ۲: فقط شمارندهٔ سرور؛ هیچ `fetchSemanticsNodes` داخلِ شرط نیست. */
    private fun awaitRequests(atLeast: Int) {
        rule.waitUntil(15_000) { server.requestCount >= atLeast }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun countDesc(desc: String) = rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().size

    @Test fun row_shows_the_masked_key_and_never_the_secret() {
        seedFresh()
        installResponses()
        render()

        // داده از کش آمد، پس اصلاً درخواستی نرفته باشد (قاعدهٔ کشِ ۲۰ ثانیه‌ای).
        assertEquals("با کشِ تازه نباید درخواستی برود", 0, server.requestCount)

        assertEquals("متنِ ماسک‌شده باید دقیقاً یک بار دیده شود", 1, count("pg_key_…f7a2"))
        assertTrue("وضعیتِ فعال باید از غیرفعال جدا باشد", count(str(R.string.ak_status_active)) > 0)
        assertTrue(count(str(R.string.ak_status_disabled)) > 0)
        assertTrue("کلیدِ بی‌انقضا باید «هرگز» داشته باشد", count(str(R.string.ak_no_expiry)) > 0)
        assertTrue("یادداشت فقط وقتی هست نشان داده می‌شود", count("رویِ سرورِ ci") > 0)
        // متنِ کاملِ کلید هیچ‌جا در فهرست نیست — این همان چیزی است که پنل هم
        // برنمی‌گرداند؛ اگر روزی بیاید، این ردیف‌ها آن را لو می‌دهند.
        assertEquals(0, count("pg_key_full_secret"))
    }

    @Test fun failed_refresh_keeps_the_rows_instead_of_showing_the_empty_state() {
        seedStale()
        installResponses(keysBody = null)   // ۴۰۳
        render()

        awaitRequests(atLeast = 1)
        // خطا روی دادهٔ موجود فقط بنر است: نه فهرست پاک می‌شود، نه «حالتِ خالی»
        // جای دادهٔ کش‌شده را می‌گیرد (ادامهٔ کار با همان ردیف‌ها بهتر از پریدنِ
        // کاربر به صفحهٔ خالی است).
        assertEquals(1, count("pg_key_…f7a2"))
        assertEquals(0, count(str(R.string.ak_empty)))
    }

    @Test fun create_dialog_refuses_an_empty_name() {
        seedFresh()
        installResponses()
        render()

        val addDesc = str(R.string.ak_create)
        assertEquals("دکمهٔ ساختِ سربرگ باید یکتا باشد", 1, countDesc(addDesc))
        // کفِ ناحیهٔ لمسِ دکمه‌های آیکونیِ همین صفحه (۴۰dp).
        rule.onNodeWithContentDescription(addDesc).assertHeightIsAtLeast(40.dp)
        rule.onNodeWithContentDescription(addDesc).performClick()

        assertTrue("دیالوگِ ساخت باید باز شود", count(str(R.string.ak_create_hint)) > 0)
        // انقضا با چیپ است نه تاریخ‌شمار: «هرگز» + ۳۰/۹۰/۳۶۵.
        assertTrue(count(str(R.string.ak_never)) > 0)
        assertTrue(count(str(R.string.ak_days_30)) > 0)
        assertTrue(count(str(R.string.ak_days_365)) > 0)

        // دو ورودیِ متنی: نام و یادداشت (شمردنِ بدونِ نوشتن — قاعدهٔ ۳).
        assertEquals(
            "دیالوگِ ساخت باید فیلدِ نام و یادداشت داشته باشد",
            2, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        )

        // گاردِ نامِ خالی: پنل `name` را ۱..۱۲۸ کاراکتر می‌خواهد، پس تا نام
        // نوشته نشود دکمهٔ تأیید باید غیرفعال باشد (نه اینکه ۴۲۲ از سرور گرفت).
        val submit = str(R.string.ak_create_action)
        assertEquals("برچسبِ تأیید جدا از عنوانِ دیالوگ است، پس یکتاست", 1, count(submit))
        rule.onNodeWithText(submit).assertIsNotEnabled()

        // دیالوگ را می‌بندیم تا پنجره‌اش تا پایانِ کلاس باز نماند.
        rule.onNodeWithText(str(R.string.cd_close)).performClick()
    }

    @Test fun read_only_role_sees_no_row_action_and_no_create_button() {
        seedFresh()
        installResponses(
            adminBody = """{"id":1,"username":"ops","role":{"id":2,"name":"Viewer","is_owner":false,
                          "permissions":{"api_keys":{"read":true}}}}"""
        )
        runBlocking { AdminAccess.refresh(session, force = true) }
        render()

        assertEquals("با مجوزِ خواندن هم ردیف‌ها ساخته می‌شوند", 1, count("pg_key_…f7a2"))
        assertEquals("بدونِ create دکمهٔ ساخت نباید باشد", 0, countDesc(str(R.string.ak_create)))
        assertEquals("بدونِ update دکمهٔ چرخش نباید باشد", 0, countDesc(str(R.string.ak_rotate)))
        assertEquals("بدونِ update دکمهٔ فعال/غیرفعال نباید باشد", 0, countDesc(str(R.string.ak_disable)))
        assertEquals("بدونِ delete دکمهٔ حذف نباید باشد", 0, countDesc(str(R.string.ak_delete)))
    }

    @Test fun empty_list_still_offers_the_create_path() {
        seedFresh(keys = emptyList())
        installResponses()
        render()

        assertEquals(1, count(str(R.string.ak_empty)))
        // حالتِ خالی باید راهِ حل را هم پیشنهاد کند، نه فقط «چیزی نیست».
        assertTrue(count(str(R.string.ak_empty_hint)) > 0)
        assertEquals("دکمهٔ ساختِ درونِ حالتِ خالی", 1, count(str(R.string.ak_create)))
    }
}
