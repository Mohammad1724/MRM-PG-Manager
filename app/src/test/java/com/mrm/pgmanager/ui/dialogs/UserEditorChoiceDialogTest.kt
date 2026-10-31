package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.PanelSupport
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.NextPlan
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.LocalChoiceWindow
import com.mrm.pgmanager.ui.components.TestChoiceWindow
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserEditorChoiceDialogTest {
    @get:Rule val rule = createComposeRule()
    private val choiceWindow = TestChoiceWindow()

    private lateinit var server: MockWebServer
    private var saved: UserEditorValues? = null
    private val user = PanelUser(
        id = 7L, username = "alpha", status = "active", usedTraffic = 0L,
        dataLimit = 0L, expire = null, createdAt = null
    )

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { it.getSharedPreferences("user_editor_choice_test", Context.MODE_PRIVATE) }
        AdminAccess.reset()
        PanelSupport.reset()
        PanelSupport.update("5.0.0") // Avoid unrelated general-settings requests in this test.
        PanelApi.allowCleartextLoopbackForTests = true
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path?.startsWith("/api/groups/simple") == true ->
                        MockResponse().setBody("""{"groups":[{"id":1,"name":"Group"}]}""")
                    request.path == "/api/user_templates" ->
                        MockResponse().setBody("""[{"id":1,"name":"Available template"}]""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }
    }

    @After fun tearDown() {
        server.shutdown()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelSupport.reset()
        AdminAccess.reset()
        SessionStore.resetPrefsForTests()
        MotionPrefs.reduceMotion = false
    }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun render(initial: PanelUser?, withTemplates: Boolean = false, fontScale: Float = 1f) {
        val session = if (withTemplates) Session(server.url("/").toString().trimEnd('/'), "test-token", "admin") else null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalChoiceWindow provides choiceWindow
            ) {
                Box(Modifier.fillMaxSize()) {
                    UserEditorDialog(initial = initial, onDismiss = {}, onSave = { values, _ -> saved = values }, session = session)
                    choiceWindow.Overlay()
                }
            }
        }
        if (withTemplates) {
            rule.waitUntil(15_000) { server.requestCount >= 2 }
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
            while (rule.onAllNodesWithTag("user_editor_next_plan").fetchSemanticsNodes().isEmpty()) {
                check(System.nanoTime() < deadline) { "The template choices did not load" }
                Thread.sleep(25)
            }
        }
    }

    private fun save() = rule.onNodeWithText(str(R.string.ue_save)).performClick()

    @Test fun `creation only offers supported initial statuses`() {
        render(initial = null)
        rule.onNodeWithTag("user_editor_status").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_status_dialog").assertIsDisplayed()
        rule.onNodeWithTag("user_editor_status_dialog_option_active").assertIsSelected()
        rule.onNodeWithTag("user_editor_status_dialog_option_on_hold").assertIsDisplayed()
        rule.onNodeWithTag("user_editor_status_dialog_option_disabled").assertDoesNotExist()
        rule.onNodeWithTag("user_editor_status_dialog_option_on_hold").performClick()
        rule.onNodeWithTag("user_editor_status_dialog").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h480dp")
    fun `user status dialog remains usable with large RTL text`() {
        render(user, fontScale = 1.8f)
        rule.onNodeWithTag("user_editor_status").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_status_dialog").assertIsDisplayed()
        rule.onNodeWithTag("user_editor_status_dialog_option_active").assertIsSelected()
        rule.onNodeWithTag("user_editor_status_dialog_cancel").assertIsDisplayed().performClick()
        rule.onNodeWithTag("user_editor_status_dialog").assertDoesNotExist()
    }

    @Test fun `canceling status selection preserves expiry and an untouched plan`() {
        val originalPlan = NextPlan(dataLimit = 123L, expireSeconds = 456L)
        render(user.copy(nextPlan = originalPlan))
        rule.onNodeWithTag("user_editor_status").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_status_dialog_option_disabled").assertIsDisplayed()
        rule.onNodeWithTag("user_editor_status_dialog_cancel").performClick()
        save()
        rule.runOnIdle {
            assertTrue(requireNotNull(saved).keepExpire)
            assertNull(saved?.nextPlan)
        }
    }

    @Test fun `reselecting active does not edit expiry`() {
        render(user)
        rule.onNodeWithTag("user_editor_status").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_status_dialog_option_active").performClick()
        save()
        rule.runOnIdle { assertTrue(requireNotNull(saved).keepExpire) }
    }

    @Test fun `unknown template remains selected without changing its original plan`() {
        val originalPlan = NextPlan(templateId = 99, dataLimit = 123L, expireSeconds = 456L, addRemainingTraffic = true)
        render(user.copy(nextPlan = originalPlan), withTemplates = true)
        rule.onNodeWithTag("user_editor_next_plan").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_next_plan_dialog_option_99").assertIsSelected().performClick()
        save()
        rule.runOnIdle { assertNull(saved?.nextPlan) }
    }

    @Test fun `custom plan stays intact unless the user explicitly clears it`() {
        val originalPlan = NextPlan(dataLimit = 123L, expireSeconds = 456L)
        render(user.copy(nextPlan = originalPlan), withTemplates = true)
        rule.onNodeWithTag("user_editor_next_plan").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_next_plan_dialog_option___current_custom_plan__").assertIsSelected().performClick()
        save()
        rule.runOnIdle { assertNull(saved?.nextPlan) }
        rule.onNodeWithTag("user_editor_next_plan").performScrollTo().performClick()
        rule.onNodeWithTag("user_editor_next_plan_dialog_option_").performClick()
        save()
        rule.runOnIdle { assertEquals(NextPlan(), saved?.nextPlan) }
    }
}
