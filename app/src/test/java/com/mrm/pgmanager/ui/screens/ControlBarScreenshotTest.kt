package com.mrm.pgmanager.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.ViewMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * «چشم» برای نوارِ کنترلِ صفحهٔ کاربران (فیلتر/مرتب‌سازی/گروه/نما).
 *
 * چرا: کاربر گزارش داد برچسبِ متنیِ فیلتر/مرتب‌سازی در عرضِ کم بریده می‌شود
 * («Filte»، «مرتبس…»). این هارنس هر دو زبان را رندر و PNG ذخیره می‌کند
 * (آرتیفکتِ CI به نام ui-screenshots) تا بشود با چشم چک کرد که:
 *   • هیچ برچسبی بریده نمی‌شود (دکمه‌ها آیکون‌محورند)،
 *   • حالتِ «روی پیش‌فرض نبودن» با رنگِ لهجه دیده می‌شود.
 *
 * هرگز نباید در CI خطا بدهد؛ خطاها در error.txt ثبت می‌شوند.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h320dp-xhdpi")
class ControlBarScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val bg = Color(0xFFF8F9FB)

    private fun shot(name: String) {
        val out = File("build/test-screens").apply { mkdirs() }
        try {
            rule.waitForIdle()
            // مثلِ MicroRowScreenshotTest: captureToImage زیرِ Robolectric تایم‌اوت می‌شود،
            // پس decorView را مستقیم روی Bitmap می‌کشیم.
            val view = rule.activity.window.decorView
            val w = view.width; val h = view.height
            require(w > 0 && h > 0) { "decorView size ${w}x$h" }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(out, "$name.txt").writeText("size=${w}x$h mode=control-bar\n")
        } catch (t: Throwable) {
            File(out, "error.txt").appendText("[$name]\n" + t.stackTraceToString())
        }
    }

    private fun renderBar(filter: UserFilter, sort: UserSort, withGroup: Boolean) {
        rule.setContent {
            Column(Modifier.width(360.dp).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterAndControlBar(
                    currentFilter = filter, onFilterChange = {},
                    currentSort = sort, onSortChange = {},
                    viewMode = ViewMode.COMPACT_LIST, onViewModeChange = {},
                    groups = if (withGroup) listOf(Group(1, "VIP"), Group(2, "Free")) else emptyList()
                )
                // همان نوار بدونِ گروه (پنلِ تک‌گروهه) — چیدمانِ لبه‌به‌لبه را چک می‌کند.
                FilterAndControlBar(
                    currentFilter = filter, onFilterChange = {},
                    currentSort = sort, onSortChange = {},
                    viewMode = ViewMode.MICRO_LIST, onViewModeChange = {},
                    groups = emptyList()
                )
            }
        }
    }

    @Test fun renderBar_defaultState() {
        renderBar(filter = UserFilter.ALL, sort = UserSort.CREATED, withGroup = true)
        shot("control_bar_default")
    }

    @Test fun renderBar_activeState() {
        // فیلترِ غیرِ پیش‌فرض + مرتب‌سازیِ غیرِ پیش‌فرض → باید لهجه‌ای دیده شوند.
        renderBar(filter = UserFilter.ONLINE, sort = UserSort.USAGE, withGroup = true)
        shot("control_bar_active")
    }
}
