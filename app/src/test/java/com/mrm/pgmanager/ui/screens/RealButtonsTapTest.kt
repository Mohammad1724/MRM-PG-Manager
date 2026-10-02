package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.PGScreenHeader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * تستِ رفتاریِ «دکمه‌های واقعیِ اپ» (نه یک Box نمونه): لمسِ واقعی روی همان کامپوننت‌هایی که
 * کاربر گزارش کرد کار نمی‌کنند — فیلتر، مرتب‌سازی، گروه، نما، تنظیمات و تازه‌سازیِ هدر.
 * هر کدام باید اثرِ خودش را داشته باشد (باز شدنِ شیت/منو یا فراخوانیِ callback).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealButtonsTapTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun setBar(
        groups: List<Group> = listOf(Group(1, "VIP")),
        onView: (ViewMode) -> Unit = {}
    ) = rule.setContent {
        FilterAndControlBar(
            currentFilter = UserFilter.ALL, onFilterChange = {},
            currentSort = UserSort.NAME, onSortChange = {},
            viewMode = ViewMode.GRID, onViewModeChange = onView,
            groups = groups
        )
    }

    @Test fun filterButton_opensFilterSheet() {
        setBar()
        rule.onAllNodesWithText(str(R.string.filter)).assertCountEquals(1)   // فقط برچسبِ دکمه
        rule.onNodeWithText(str(R.string.filter)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.filter)).assertCountEquals(2)   // + عنوانِ شیت
    }

    @Test fun sortButton_opensSortSheet() {
        setBar()
        rule.onAllNodesWithText(str(R.string.sort)).assertCountEquals(1)
        rule.onNodeWithText(str(R.string.sort)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.sort)).assertCountEquals(2)
    }

    @Test fun groupButton_opensGroupMenu() {
        setBar()
        rule.onAllNodesWithText(str(R.string.us_group_all)).assertCountEquals(0)
        rule.onNodeWithText(str(R.string.us_group_filter)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.us_group_all)).assertCountEquals(1)
    }

    @Test fun viewModeIcon_invokesCallback() {
        var picked: ViewMode? = null
        setBar(onView = { picked = it })
        rule.onNodeWithContentDescription(str(R.string.us_view_compact)).performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.COMPACT_LIST, picked)
    }

    @Test fun headerSettingsAndRefresh_invokeCallbacks() {
        var settings = 0
        var refresh = 0
        rule.setContent {
            PGScreenHeader(
                title = "T", subtitle = "S", refreshing = false,
                onRefresh = { refresh++ }, onOpenSettings = { settings++ },
                settingsLabel = "SETTINGS_BTN", refreshLabel = "REFRESH_BTN"
            )
        }
        rule.onNodeWithContentDescription("SETTINGS_BTN").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("REFRESH_BTN").performClick()
        rule.waitForIdle()
        assertEquals("تنظیمات", 1, settings)
        assertEquals("تازه‌سازی", 1, refresh)
    }
}
