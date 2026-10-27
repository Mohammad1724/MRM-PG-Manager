package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.ClientTemplate
import com.mrm.pgmanager.data.model.ClientTemplateType
import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.GraphicsMode

/** Exercises the real sections with fresh test cache entries, not production mock data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelInfraScrollTest {
    @get:Rule val rule = createComposeRule()

    private val session = Session("https://panel.example.test", "test-token", "test-admin")
    private var backs = 0

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        AdminAccess.reset()
        PanelCache.clear()
        PanelCache.clock = { 1_000L }
        seed(session)
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        AdminAccess.reset()
        PanelCache.clear()
        PanelCache.resetClockForTests()
        MotionPrefs.reduceMotion = false
    }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun seed(target: Session, count: Int = 35) {
        PanelCache.put(PanelCache.hostsKey(target.baseUrl), (0 until count).map { index ->
            PanelHost(id = index + 1, remark = "Host $index", addresses = listOf("node-$index.example.test"), priority = index + 1)
        })
        PanelCache.put(PanelCache.inboundsKey(target.baseUrl), emptyList<String>())
        PanelCache.put(PanelCache.coresKey(target.baseUrl), (0 until count).map { index ->
            PanelCore(id = index + 1, name = "Core $index", type = "xray")
        })
        PanelCache.put(PanelCache.clientTemplatesKey(target.baseUrl), (0 until count).map { index ->
            ClientTemplate(
                id = index + 1,
                name = "Template $index",
                templateType = if (index % 2 == 0) ClientTemplateType.CLASH else ClientTemplateType.XRAY,
                content = "test content"
            )
        })
    }

    private fun render(
        target: Session = session,
        width: Dp = 320.dp,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction
            ) {
                Box(Modifier.size(width, 640.dp)) {
                    PanelInfraScreen(target, onBack = { backs++ })
                }
            }
        }
    }

    private fun titleBounds(): Rect =
        rule.onNodeWithTag("infra_page_header", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun checkSectionScroll(tag: String, item: String, searchLabel: Int) {
        rule.onNodeWithTag("infra_page_header", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription(str(searchLabel)).assertExists()
        rule.onNodeWithTag(tag).performScrollToIndex(20)
        rule.onNodeWithTag("infra_page_header", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription(str(searchLabel)).assertDoesNotExist()
        rule.onNodeWithText(item).assertIsDisplayed()
        rule.onNodeWithTag(tag).performScrollToIndex(0)
        rule.onNodeWithTag("infra_page_header", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription(str(searchLabel)).assertExists()
    }

    @Test fun `all three tabs scroll the header and section controls away with the rows`() {
        render()
        rule.onNodeWithContentDescription(str(R.string.cd_back))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        rule.onNodeWithText(str(R.string.infra_tab_hosts)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        checkSectionScroll("infra_hosts_list", "Host 19", R.string.search_hosts)
        rule.onNodeWithText(str(R.string.infra_tab_cores)).performClick()
        checkSectionScroll("infra_cores_list", "Core 19", R.string.search_cores)
        rule.onNodeWithText(str(R.string.infra_tab_templates)).performClick()
        checkSectionScroll("infra_templates_list", "Template 19", R.string.search_ct)
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun `swiping on the header moves the same list`() {
        render()
        val before = titleBounds()
        rule.onNodeWithTag("infra_page_header", useUnmergedTree = true).performTouchInput {
            swipeUp(startY = height * 0.8f, endY = 2f, durationMillis = 500)
        }
        val headers = rule.onAllNodesWithTag("infra_page_header", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("The page header should move or leave the viewport", headers.isEmpty() || headers[0].boundsInRoot.top < before.top)
    }

    @Test fun `search and clear still update the real host list`() {
        render()
        val search = rule.onNodeWithContentDescription(str(R.string.search_hosts))
        search.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("not-a-host")) }
        rule.onNodeWithText(str(R.string.no_results)).assertExists()
        rule.onNodeWithText(str(R.string.clear_filter)).performClick()
        rule.onNodeWithText("Host 0").assertExists()
        search.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("node-14.example.test")) }
        rule.onNodeWithText("Host 14").assertExists()
        rule.onNodeWithText("Host 0").assertDoesNotExist()
    }

    @Test fun `template type filter remains interactive inside the scrolling header`() {
        render()
        rule.onNodeWithText(str(R.string.infra_tab_templates)).performClick()
        rule.onNodeWithTag("infra_template_filter_${ClientTemplateType.XRAY}", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("infra_templates_list").performScrollToIndex(1)
        rule.onNodeWithText("Template 1").assertExists()
        rule.onNodeWithText("Template 0").assertDoesNotExist()
        rule.onNodeWithTag("infra_templates_list").performScrollToIndex(0)
        rule.onNodeWithTag("infra_template_filter_all", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("infra_templates_list").performScrollToIndex(1)
        rule.onNodeWithText("Template 0").assertExists()
    }

    @Test fun `empty sections retain navigation and their empty states`() {
        seed(session, count = 0)
        render()
        rule.onNodeWithTag("infra_hosts_list").performScrollToIndex(1)
        rule.onNodeWithText(str(R.string.no_hosts)).assertExists()
        rule.onNodeWithTag("infra_hosts_list").performScrollToIndex(0)
        rule.onNodeWithText(str(R.string.infra_tab_cores)).performClick()
        rule.onNodeWithTag("infra_cores_list").performScrollToIndex(1)
        rule.onNodeWithText(str(R.string.no_cores)).assertExists()
        rule.onNodeWithTag("infra_cores_list").performScrollToIndex(0)
        rule.onNodeWithText(str(R.string.infra_tab_templates)).performClick()
        rule.onNodeWithTag("infra_templates_list").performScrollToIndex(1)
        rule.onNodeWithText(str(R.string.no_ct)).assertExists()
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `RTL with large text still scrolls header and content together`() {
        render(direction = LayoutDirection.Rtl, fontScale = 2f)
        checkSectionScroll("infra_hosts_list", "Host 19", R.string.search_hosts)
        rule.onNodeWithText(str(R.string.infra_tab_cores)).performClick()
        checkSectionScroll("infra_cores_list", "Core 19", R.string.search_cores)
        rule.onNodeWithText(str(R.string.infra_tab_templates)).performClick()
        checkSectionScroll("infra_templates_list", "Template 19", R.string.search_ct)
        rule.onNodeWithContentDescription(str(R.string.cd_back))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp")
    fun `wide screens keep the table until text scale requires readable rows`() {
        var scale by mutableFloatStateOf(1f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                Box(Modifier.size(800.dp, 640.dp)) { PanelInfraScreen(session, onBack = {}) }
            }
        }
        assertEquals(0, rule.onAllNodesWithTag("host_readable_row", useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.onNodeWithText(str(R.string.col_address)).assertExists()
        rule.runOnIdle { scale = 2f }
        assertTrue(rule.onAllNodesWithTag("host_readable_row", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun `read-only permissions still hide host and template mutations`() {
        val server = MockWebServer()
        server.start()
        try {
            PanelApi.allowCleartextLoopbackForTests = true
            val readOnly = Session(server.url("/").toString().trimEnd('/'), "test-token", "reader")
            server.enqueue(MockResponse().setBody("""{"id":1,"username":"reader","role":{"is_owner":false,"permissions":{"hosts":{"read":true},"cores":{"read":true},"client_templates":{"read":true}}}}"""))
            runBlocking { AdminAccess.refresh(readOnly) }
            seed(readOnly)
            render(target = readOnly)
            rule.onNodeWithContentDescription(str(R.string.add_host)).assertDoesNotExist()
            rule.onNodeWithContentDescription(str(R.string.act_more)).assertDoesNotExist()
            rule.onAllNodesWithTag("host_readable_row")[0].assertHasNoClickAction()
            rule.onNodeWithText(str(R.string.infra_tab_templates)).performClick()
            rule.onNodeWithContentDescription(str(R.string.ct_create)).assertDoesNotExist()
            rule.onNodeWithContentDescription(str(R.string.act_more)).assertDoesNotExist()
        } finally {
            server.shutdown()
        }
    }

    @Test fun `failed host load shows retry and recovers without losing navigation`() {
        val server = MockWebServer()
        server.start()
        try {
            PanelApi.allowCleartextLoopbackForTests = true
            val target = Session(server.url("/").toString().trimEnd('/'), "test-token", "test-admin")
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            render(target = target)
            rule.waitUntil(15_000) { PanelCache.get<List<String>>(PanelCache.inboundsKey(target.baseUrl)) != null }
            rule.onNodeWithTag("infra_hosts_list").performScrollToIndex(1)
            rule.onNodeWithText(str(R.string.load_failed)).assertExists()
            PanelCache.clock = { 2_000L }
            rule.onNodeWithText(str(R.string.retry)).performClick()
            rule.waitUntil(15_000) {
                PanelCache.get<List<PanelHost>>(PanelCache.hostsKey(target.baseUrl)) != null &&
                    PanelCache.ageMs(PanelCache.inboundsKey(target.baseUrl)) == 0L
            }
            rule.onNodeWithText(str(R.string.no_hosts)).assertExists()
            rule.onNodeWithTag("infra_hosts_list").performScrollToIndex(0)
            rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
            rule.runOnIdle { assertEquals(1, backs) }
            assertEquals(4, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
