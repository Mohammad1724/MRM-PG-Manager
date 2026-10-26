package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.Session
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * تست‌های رفتاری داشبورد — I1 (شرح دسترس‌پذیری انتخابگر بازه) و
 * I2 (تپ کارت‌های حلقه‌ای → پرش به تب آمار). مسیر شبکه با MockWebServer لوکال
 * و فلگ تستی cleartext پاسخ داده می‌شود.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class DashboardScreenTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var session: Session

    // نسخهٔ قالبی هم لازم است: `chart_range_selector` یک آرگومانِ `%1$s` دارد و
    // فراخوانیِ `str(R.string.x, "7d")` با امضای تک‌پارامتری کامپایل نمی‌شد.
    private fun str(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    /**
     * ترتیب درخواست‌ها بین افکت لود و افکت بازه قطعی نیست؛ به‌جای صف FIFO،
     * بر اساس مسیر پاسخ درست برگردانده می‌شود تا تست قطعی بماند.
     */
    private fun installDispatcher() {
        val systemJson = """{"uptime_seconds":3600,"mem_total":1000,"mem_used":400,"disk_total":1000,"disk_used":200,
                   |"cpu_cores":4,"cpu_usage":12.5,"total_user":10,"online_users":3,"active_users":8,
                   |"expired_users":1,"limited_users":1,"disabled_users":0,"on_hold_users":0,
                   |"incoming_bandwidth":500,"outgoing_bandwidth":250,"version":"5.4.1"}""".trimMargin()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = when {
                    path.startsWith("/api/system") -> systemJson
                    path.startsWith("/api/users/usage") -> """{"stats":{}}"""
                    path.startsWith("/api/admins") -> """{"admins":[]}"""
                    path.startsWith("/api/nodes") -> """{}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body)
            }
        }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        PanelApi.allowCleartextLoopbackForTests = true
        PanelCache.clear()
        session = Session(server.url("/").toString().trimEnd('/'), "tok", "admin")
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    private fun render(onOpenStatistics: () -> Unit = {}) = rule.setContent {
        DashboardScreen(
            session = session,
            settings = MonitoringSettings(notificationsEnabled = false, autoRefreshEnabled = false),
            onSessionExpired = {},
            onOpenStatistics = onOpenStatistics
        )
    }

    @Test fun ringCards_click_invokesOpenStatistics() {
        installDispatcher()
        var opens = 0
        render(onOpenStatistics = { opens++ })

        val label = str(R.string.db_open_statistics)
        // چهار کارت حلقه‌ای (CPU/RAM/دیسک/ترافیک) بعد از لود ظاهر می‌شوند.
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size == 4
        }
        rule.onAllNodesWithContentDescription(label)[0].performClick()
        rule.waitForIdle()
        assertEquals(1, opens)
    }

    @Test fun rangeSelector_exposesAccessibleDescription() {
        installDispatcher()
        render()

        // پیش‌فرض LAST_7D با برچسب "7d".
        val expected = str(R.string.chart_range_selector, "7d")
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(expected).assertExists()
    }

    @Test fun emptyTraffic_showsNoTrendText() {
        installDispatcher()
        render()

        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(str(R.string.no_trend)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(server.requestCount > 0)
    }
}
