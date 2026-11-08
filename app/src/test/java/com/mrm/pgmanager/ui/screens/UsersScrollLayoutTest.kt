package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsersScrollLayoutTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var listState: LazyListState
    private lateinit var gridState: LazyGridState
    private var gridMode by mutableStateOf(false)
    private var showList by mutableStateOf(true)
    private var showStats by mutableStateOf(true)
    private var rowClicks = 0
    private var rowLongClicks = 0
    private var headerClicks = 0
    private var refreshes = 0

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    private fun render(rtlLargeText: Boolean = false) {
        rule.setContent {
            val density = LocalDensity.current
            val textDensity = if (rtlLargeText) Density(density.density, 2f) else density
            val direction = if (rtlLargeText) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalDensity provides textDensity, LocalLayoutDirection provides direction) {
                listState = rememberLazyListState()
                gridState = rememberLazyGridState()
                val active: ScrollableState? = if (!showList) null else if (gridMode) gridState else listState
                val headerScroll = rememberUsersHeaderScrollState(active)
                var headerHeight by remember { mutableStateOf(0) }
                var collapse by remember { mutableStateOf(0f) }
                val collapseLimit = with(textDensity) { 80.dp.toPx() }
                val connection = remember(collapseLimit) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val previous = collapse
                            collapse = (previous - available.y).coerceIn(0f, collapseLimit)
                            return Offset(0f, previous - collapse)
                        }
                    }
                }

                Box(Modifier.size(320.dp, 640.dp).nestedScroll(connection).background(Color.White).testTag("screen")) {
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = false, onRefresh = { refreshes++ }, state = pullState,
                        modifier = Modifier.fillMaxSize(), indicator = {}
                    ) {
                        Box(Modifier.fillMaxSize().usersListViewport { headerHeight }) {
                            if (!showList) {
                                Box(Modifier.fillMaxSize().testTag("empty_state"))
                            } else if (gridMode) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2), state = gridState,
                                    modifier = Modifier.fillMaxSize().testTag("users_list")
                                ) {
                                    items((0 until 50).toList()) { index ->
                                        Box(
                                            Modifier.fillMaxWidth().height(64.dp)
                                                .combinedClickable(onClick = { rowClicks++ }, onLongClick = { rowLongClicks++ })
                                                .testTag("user_$index")
                                        ) { Text("User $index") }
                                    }
                                }
                            } else {
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("users_list")) {
                                    items((0 until 50).toList()) { index ->
                                        Box(
                                            Modifier.fillMaxWidth().height(64.dp)
                                                .combinedClickable(onClick = { rowClicks++ }, onLongClick = { rowLongClicks++ })
                                                .testTag("user_$index")
                                        ) { Text("User $index") }
                                    }
                                }
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                                .usersHeaderDrag(headerScroll).background(Color.White).testTag("header")
                        ) {
                            if (showStats) {
                                Box(
                                    Modifier.layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val height = (placeable.height - collapse.roundToInt()).coerceAtLeast(0)
                                        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                                    }.clipToBounds()
                                ) { StatsCardsRow(totalUsers = 50, activeUsers = 20, onlineUsers = 10) }
                            }
                            Box(Modifier.size(48.dp).clickable { headerClicks++ }.testTag("header_action")) {
                                Text("Action")
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun `tapping and holding the restored header never activates scrolled rows`() {
        render()
        rule.onNodeWithTag("users_list").performScrollToIndex(10)
        val expandedHeight = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height
        rule.onNodeWithTag("users_list").performTouchInput {
            swipeUp(startY = 150.dp.toPx(), endY = 20.dp.toPx(), durationMillis = 1000)
        }
        val collapsedHeight = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height
        assertTrue("The header should collapse when scrolling down", collapsedHeight < expandedHeight)
        rule.onNodeWithTag("users_list").performTouchInput {
            swipeDown(startY = 20.dp.toPx(), endY = 150.dp.toPx(), durationMillis = 1000)
        }
        assertTrue(listState.firstVisibleItemIndex > 0)

        val header = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        assertEquals(expandedHeight, header.height, 1f)
        val list = rule.onNodeWithTag("users_list").fetchSemanticsNode().boundsInRoot
        assertEquals(header.bottom, list.top, 1f)

        rule.onNodeWithTag("header").performTouchInput { click(Offset(30.dp.toPx(), 30.dp.toPx())) }
        rule.onNodeWithTag("header").performTouchInput { longClick(Offset(30.dp.toPx(), 30.dp.toPx())) }
        rule.runOnIdle {
            assertEquals(0, rowClicks)
            assertEquals(0, rowLongClicks)
        }
        rule.onNodeWithTag("header_action").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, headerClicks) }
        rule.onNodeWithTag("users_list").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, rowClicks) }
    }

    @Test fun `dragging the header scrolls the active list and then the grid`() {
        render()
        rule.onNodeWithTag("header").performTouchInput {
            swipeUp(startY = bottom - 10.dp.toPx(), endY = 10.dp.toPx(), durationMillis = 500)
        }
        assertTrue("A drag on the header should move the list", listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        rule.runOnIdle { gridMode = true }
        rule.onNodeWithTag("header").performTouchInput {
            swipeUp(startY = bottom - 10.dp.toPx(), endY = 10.dp.toPx(), durationMillis = 500)
        }
        assertTrue("The header should forward to the grid after switching modes", gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
        rule.runOnIdle { assertEquals(0, rowClicks) }
    }

    @Test fun `pulling down on the header can still request refresh`() {
        render()
        rule.onNodeWithTag("header").performTouchInput {
            swipeDown(startY = 4.dp.toPx(), endY = bottom - 4.dp.toPx(), durationMillis = 600)
        }
        rule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun `empty state keeps header actions and refresh available`() {
        render()
        rule.runOnIdle { showList = false }
        rule.onNodeWithTag("header_action").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, headerClicks) }
        rule.onNodeWithTag("header").performTouchInput {
            swipeDown(startY = 4.dp.toPx(), endY = bottom - 4.dp.toPx(), durationMillis = 600)
        }
        rule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    @Config(qualifiers = "fa-rIR-w320dp-h800dp")
    fun `RTL large text keeps the list below the resized header`() {
        render(rtlLargeText = true)
        rule.onNodeWithTag("users_list").performScrollToIndex(10)
        rule.runOnIdle { showStats = false }
        rule.runOnIdle { showStats = true }
        val header = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val list = rule.onNodeWithTag("users_list").fetchSemanticsNode().boundsInRoot
        assertEquals(header.bottom, list.top, 1f)
        rule.onNodeWithTag("header").performTouchInput { click(Offset(30.dp.toPx(), 30.dp.toPx())) }
        rule.runOnIdle { assertEquals(0, rowClicks) }
        rule.onNodeWithTag("header_action").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, headerClicks) }
    }
}
