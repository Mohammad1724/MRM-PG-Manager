package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

    // دکمه‌های فیلتر/مرتب‌سازی دیگر متن ندارند (درخواستِ کاربر: برچسبِ دوسطری بریده
    // می‌شد). پس هدفِ لمس و «قراردادِ اطلاع‌رسانی» را از contentDescription
    // («برچسب: مقدارِ فعلی») می‌گیریم — همان چیزی که TalkBack هم می‌خواند.
    @Test fun filterButton_isIconOnly_withFullDescription() {
        setBar()   // currentFilter = ALL
        val desc = str(R.string.filter) + ": " + str(R.string.all)
        rule.onAllNodesWithText(str(R.string.filter)).assertCountEquals(0)   // هیچ متنِ برچسبی نمانده
        rule.onNodeWithContentDescription(desc).assertExists()
        rule.onNodeWithContentDescription(desc).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.filter)).assertCountEquals(1)   // فقط عنوانِ شیت
    }

    @Test fun sortButton_isIconOnly_withFullDescription() {
        setBar()   // currentSort = NAME
        val desc = str(R.string.sort) + ": " + str(R.string.name)
        rule.onAllNodesWithText(str(R.string.sort)).assertCountEquals(0)
        rule.onNodeWithContentDescription(desc).assertExists()
        rule.onNodeWithContentDescription(desc).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.sort)).assertCountEquals(1)
    }

    /** مقدارِ فیلترِ فعلی باید در شرحِ آیکون دیده شود — وگرنه با آیکون‌شدن، اطلاعات گم می‌شود. */
    @Test fun filterIcon_descriptionFollowsCurrentValue() {
        rule.setContent {
            FilterAndControlBar(
                currentFilter = UserFilter.ONLINE, onFilterChange = {},
                currentSort = UserSort.LAST_ONLINE, onSortChange = {},
                viewMode = ViewMode.COMPACT_LIST, onViewModeChange = {}
            )
        }
        rule.onNodeWithContentDescription(str(R.string.filter) + ": " + str(R.string.online)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.sort) + ": " + str(R.string.us_sort_last_online)).assertExists()
    }

    /** دکمه‌های آیکون‌محور باید کفِ لمسِ خودشان را داشته باشند (کفِ ۳۸dp + گسترشِ خودکارِ Compose). */
    @Test fun iconButtons_keepMinimumHeight() {
        setBar()
        val desc = str(R.string.filter) + ": " + str(R.string.all)
        rule.onNodeWithContentDescription(desc).assertHeightIsAtLeast(38.dp)
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
