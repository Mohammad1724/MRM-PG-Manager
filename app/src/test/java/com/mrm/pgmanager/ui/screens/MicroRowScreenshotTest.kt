package com.mrm.pgmanager.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.data.model.PanelUser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * نه یک تستِ درستی‌سنجی، بلکه «چشم»: ردیف‌های micro را با دادهٔ مشابهِ گزارشِ کاربر در ۳۶۰dp/xhdpi
 * رندر و به‌صورت PNG ذخیره می‌کند (آرتیفکتِ CI به نام ui-screenshots). هرگز نباید گیت را بشکند؛
 * خطاها در error.txt ثبت می‌شوند.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xhdpi")
class MicroRowScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** تراکمِ واقعیِ رندرِ xhdpi (۲.۰) — از داخلِ کامپوزیشن خوانده می‌شود. */
    private var density = 1f

    /** بلندترین ارتفاعِ ردیف (dp) از آخرین رندرِ موفق؛ برای ادعای بیرون از try. */
    private var rowHeightDp = -1f

    private val gb = 1024L * 1024 * 1024
    private fun u(id: Long, name: String, status: String, used: Double, limit: Long, days: Long?) = PanelUser(
        id = id, username = name, status = status,
        usedTraffic = (used * gb).toLong(), dataLimit = limit * gb,
        expire = days?.let { Instant.now().plus(it, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS).toString() },
        createdAt = null
    )

    @Test fun renderMicroRows() {
        val out = File("build/test-screens").apply { mkdirs() }
        try {
            val users = listOf(
                u(1, "jabbar-kari", "active", 7.0, 30, 19),
                u(2, "user-5659", "limited", 20.2, 20, 1),
                u(3, "user-8536", "limited", 20.27, 20, 4),
                u(4, "user-3015", "active", 0.0, 20, 16),
                u(5, "user-9486", "disabled", 6.23, 30, 1),
                u(6, "Mahi-4", "active", 107.59, 0, 7),
                u(7, "user-5647", "active", 6.82, 20, 2),
                u(8, "AliReza-Roosta", "active", 21.61, 50, 4)
            )
            rule.setContent {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                Column(Modifier.width(360.dp).background(Color(0xFFF8F9FB)).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    users.forEachIndexed { i, u -> Box(Modifier.testTag("row_$i")) { LuxuryMicroRow(u, onClick = {}) } }
                }
            }
            rule.waitForIdle()
            // captureToImage() زیرِ Robolectric روی ‌forceRedraw تایم‌اوت می‌شود (حلقهٔ اصلی متوقف است)؛
            // پس decorView را مستقیم روی Bitmap می‌کشیم.
            val view = rule.activity.window.decorView
            val w = view.width; val h = view.height
            require(w > 0 && h > 0) { "decorView size ${w}x$h" }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            var nonWhite = 0
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            for (c in px) if (c != -1 && c != 0) nonWhite++
            // شاهدِ عددی: اندازهٔ دکمه‌ها + ارتفاعِ ردیف‌ها (xhdpi = شرایطِ رندرِ گوشی).
            val heights = users.indices.map { i ->
                val b = rule.onNodeWithTag("row_$i").fetchSemanticsNode().boundsInRoot
                b.height / density
            }
            File(out, "info.txt").writeText(
                "size=${w}x$h nonWhitePixels=$nonWhite\n" +
                "action_size=${MICRO_ACTION_SIZE.value}dp  compact_action=${COMPACT_ACTION_SIZE.value}dp\n" +
                "row_heights_dp=" + heights.joinToString(",") { "%.1f".format(it) } + "\n"
            )
            rowHeightDp = heights.maxOrNull() ?: -1f
            File(out, "micro_rows.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            // خطای *رندر* نباید CI را ببندد (این هارنس «چشم» است) — در error.txt می‌نشیند.
            File(out, "error.txt").writeText(t.stackTraceToString())
        }
        // اما افتِ هندسه یک رگرسیونِ واقعی است و *باید* CI را ببندد؛ پس ادعا بیرون از
        // catch است تا قربانیِ سیاستِ «خطای رندر = غیرِ مرگبار» نشود.
        if (rowHeightDp > 0f) {
            org.junit.Assert.assertTrue(
                "ارتفاعِ ردیف‌های micro ${rowHeightDp}dp شد — سقفِ ۵۶dp (قبلاً ۶۴dp بود؛ ریشه: ظرفِ چک‌باکس)",
                rowHeightDp <= 56f
            )
        }
    }
}
