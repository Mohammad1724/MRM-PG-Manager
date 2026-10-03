package com.mrm.pgmanager.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGRingStatCard
import com.mrm.pgmanager.ui.components.PGRingStatRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * «چشم» برای ردیف‌های کارتِ حلقه‌ای با دادهٔ واقعیِ گزارشِ کاربر
 * (CPU: «9.3%» + «1 cores» | RAM: «614.95 MB» + «of 1.87 GB / 1.27 GB free»).
 *
 * ادعای تصویری: دو کارتِ هم‌ردیف **هم‌قد** و بدونِ فضای خالیِ ته کارت.
 * خروجی PNG در آرتیفکتِ CI (`ui-screenshots`) می‌نشیند.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h320dp-xhdpi")
class RingRowScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val bg = Color(0xFFF8F9FB)

    private fun shot(name: String) {
        val out = File("build/test-screens").apply { mkdirs() }
        try {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val w = view.width; val h = view.height
            require(w > 0 && h > 0) { "decorView size ${w}x$h" }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(out, "$name.txt").writeText("size=${w}x$h mode=ring-rows\n")
        } catch (t: Throwable) {
            File(out, "error.txt").appendText("[$name]\n" + t.stackTraceToString())
        }
    }

    @Test fun renderMetricRows() {
        rule.setContent {
            Column(
                Modifier.width(360.dp).background(bg).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // دقیقاً همان ردیفی که کاربر گزارش داد: CPU یک‌خطی، RAM دوخطی.
                PGRingStatRow {
                    PGRingStatCard(
                        label = "CPU Usage", value = "9.3%", icon = AppIcon.Gauge,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fraction = 0.093f, percent = 9, sub = "1 cores"
                    )
                    PGRingStatCard(
                        label = "RAM Usage", value = "614.95 MB", icon = AppIcon.Memory,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fraction = 0.32f, percent = 32, sub = "of 1.87 GB\n1.27 GB free"
                    )
                }
                PGRingStatRow {
                    PGRingStatCard(
                        label = "Disk Usage", value = "6.84 GB", icon = AppIcon.Storage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fraction = 0.28f, percent = 28, sub = "of 24.31 GB\n17.48 GB free"
                    )
                    PGRingStatCard(
                        label = "Total Traffic", value = "13.66 TB", icon = AppIcon.Storage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        segments = listOf(
                            com.mrm.pgmanager.ui.components.RingSegment(0.06f, Color(0xFFF0C848)),
                            com.mrm.pgmanager.ui.components.RingSegment(0.94f, Color(0xFFE6D48A))
                        ),
                        centerIcon = AppIcon.Storage,
                        sub = "↓ 831.18 GB\n↑ 12.85 TB"
                    )
                }
            }
        }
        shot("ring_rows_dashboard")
    }
}
