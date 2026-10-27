package com.mrm.pgmanager.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import com.mrm.pgmanager.ui.designsystem.ScreenSecurity
import com.mrm.pgmanager.ui.theme.ThemeState
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
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsersScreenTouchTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var session: Session
    private var settingsClicks = 0
    private val detailRequests = AtomicInteger()
    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { context -> context.getSharedPreferences("users_screen_touch_test", Context.MODE_PRIVATE) }
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("users_screen_touch_test", Context.MODE_PRIVATE).edit().clear()
            .putBoolean(SessionStore.HINT_LONG_PRESS, true)
            .putString("view_mode", ViewMode.MICRO_LIST.name)
            .commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().startsWith("/api/groups") -> MockResponse().setBody("""{"groups":[]}""")
                request.path.orEmpty().startsWith("/api/admins") -> MockResponse().setBody("""{"admins":[]}""")
                request.path == "/api/user/by-id/1" -> {
                    detailRequests.incrementAndGet()
                    MockResponse().setBody("""{"id":1,"username":"sample_1","status":"active","subscription_url":"https://example.test/sub/sample_1"}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        PanelApi.allowCleartextLoopbackForTests = true
        session = Session(server.url("/").toString().trimEnd('/'), "test-token", "test-admin")
        PanelCache.clear()
        PanelCache.put(PanelCache.usersKey(session.baseUrl), (1..50).map {
            PanelUser(id = it.toLong(), username = "sample_$it", status = "active", usedTraffic = 0,
                dataLimit = 0, expire = null, createdAt = null)
        })
    }

    @After fun tearDown() {
        MotionPrefs.reduceMotion = false
        SessionStore.resetPrefsForTests()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    @Test fun `real screen share fetches the missing link and opens the existing sheet`() {
        val initialHolds = ScreenSecurity.holds
        rule.setContent {
            UsersScreen(
                session = session,
                onSessionExpired = {},
                themeState = ThemeState(),
                monitoringSettings = MonitoringSettings(notificationsEnabled = false, autoRefreshEnabled = false)
            )
        }
        rule.onNodeWithContentDescription(str(R.string.us_share_subscription, "sample_1"))
            .performTouchInput { click(center) }
        rule.waitUntil(15_000) { detailRequests.get() == 1 && ScreenSecurity.holds > initialHolds }
        rule.onNodeWithText("QR sample_1").assertIsDisplayed()
        rule.onNodeWithContentDescription("QR").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.qr_send_card)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.qr_copy)).performClick()
        rule.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("https://example.test/sub/sample_1", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        rule.onNodeWithText(str(R.string.qr_close)).performClick()
        rule.runOnIdle { assertEquals(initialHolds, ScreenSecurity.holds) }
    }

    @Test fun `real screen header does not open an obscured user after scrolling`() {
        rule.setContent {
            UsersScreen(
                session = session,
                onSessionExpired = {},
                themeState = ThemeState(),
                monitoringSettings = MonitoringSettings(notificationsEnabled = false, autoRefreshEnabled = false),
                onOpenSettings = { settingsClicks++ }
            )
        }
        rule.onNodeWithTag("users_screen_list").performScrollToIndex(10)
        val initialHeight = rule.onNodeWithTag("users_screen_header").fetchSemanticsNode().boundsInRoot.height
        rule.onNodeWithTag("users_screen_list").performTouchInput {
            swipeUp(startY = 150.dp.toPx(), endY = 20.dp.toPx(), durationMillis = 1000)
        }
        val collapsedHeight = rule.onNodeWithTag("users_screen_header").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Scrolling down should collapse the real statistics header", collapsedHeight < initialHeight)
        rule.onNodeWithTag("users_screen_list").performTouchInput {
            swipeDown(startY = 20.dp.toPx(), endY = 150.dp.toPx(), durationMillis = 1000)
        }

        val header = rule.onNodeWithTag("users_screen_header").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("users_screen_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("The users viewport must remain below the header", viewport.top >= header.bottom)
        val stat = rule.onNodeWithText(str(R.string.online_users), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val statPoint = Offset(stat.center.x - header.left, stat.center.y - header.top)
        rule.onNodeWithTag("users_screen_header").performTouchInput { click(statPoint) }
        rule.onAllNodes(isDialog()).assertCountEquals(0)
        rule.onNodeWithTag("users_screen_header").performTouchInput { longClick(statPoint) }
        rule.onAllNodes(isDialog()).assertCountEquals(0)
        rule.onNodeWithContentDescription(str(R.string.app_settings)).performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, settingsClicks) }
    }
}
