package com.mrm.pgmanager.ui.dialogs

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.data.model.OnlineIp
import com.mrm.pgmanager.data.model.SubUpdate
import com.mrm.pgmanager.data.model.SubUpdateList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * «چشم» + قفلِ عددی برای بخشِ جزئیاتِ کاربر (IPهای آنلاین و تاریخچهٔ اشتراک).
 *
 * گزارشِ کاربر: «این قسمت هم فضای زیادی اشغال می‌کند» — ریشه: هر ردیفِ تاریخچه **دو خط**
 * بود (کلاینت بالا، آی‌پی زیرش) و خطِ خلاصه عددِ خامِ اتصال‌ها را می‌نوشت
 * («1791007477 connections»). حالا هر ردیف یک خط است و عدد فشرده شده.
 *
 * ادعای عددی: ارتفاعِ هر ردیفِ تاریخچه ≤ ۲۶dp (روی همان رندرِ xhdpiِ گوشی).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class UserDetailsSectionsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val bg = Color(0xFFF8F9FB)
    private var density = 1f
    private var fetchRowHeightDp = -1f
    private var rowCount = 0

    private val ips = listOf(
        OnlineIp(3, "89.199.110.216", 1_791_007_477),
        OnlineIp(7, "10.0.0.4", 42)
    )

    private val fetches = SubUpdateList(
        count = 5,
        updates = listOf(
            SubUpdate(createdAt = Instant.now().minusSeconds(1_440).toString(), userAgent = "v2rayNG/1.8.5", ip = "127.0.0.1"),
            SubUpdate(createdAt = Instant.now().minusSeconds(86_400).toString(), userAgent = "v2rayNG/1.8.5", ip = "127.0.0.1"),
            SubUpdate(createdAt = Instant.now().minusSeconds(86_400).toString(), userAgent = "v2rayNG/1.8.5", ip = "127.0.0.1"),
            SubUpdate(createdAt = Instant.now().minusSeconds(172_800).toString(), userAgent = "v2rayNG/1.8.5", ip = "127.0.0.1"),
            SubUpdate(createdAt = Instant.now().minusSeconds(172_800).toString(), userAgent = "v2rayNG/1.8.5", ip = "127.0.0.1")
        )
    )

    @Test fun renderUserDetailSections() {
        val out = File("build/test-screens").apply { mkdirs() }
        try {
            rule.setContent {
                density = LocalDensity.current.density
                Column(
                    Modifier.width(360.dp).background(bg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OnlineIpsCard(ips = ips, loading = false, nodeNames = mapOf(3 to "Finland Node", 7 to "DE-Node"), onCheck = {})
                    Box(Modifier.testTag("fetch_card")) { SubscriptionFetchesCard(fetches) }
                }
            }
            rule.waitForIdle()

            val cardH = rule.onNodeWithTag("fetch_card").fetchSemanticsNode().boundsInRoot.height / density
            // ارتفاعِ هر ردیف را مستقیم از خودِ ردیف‌ها می‌گیریم (تگِ sub_fetch_row).
            val rows = rule.onAllNodesWithTag("sub_fetch_row", useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.boundsInRoot.height / density }
            rowCount = rows.size
            fetchRowHeightDp = rows.maxOrNull() ?: -1f

            val view = rule.activity.window.decorView
            val w = view.width; val h = view.height
            require(w > 0 && h > 0) { "decorView size ${w}x$h" }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(out, "user_detail_sections.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(out, "user_detail_sections.txt").writeText(
                "size=${w}x$h card_h=${"%.1f".format(cardH)}dp fetch_row=${"%.1f".format(fetchRowHeightDp)}dp " +
                    "rendered_rows=$rowCount of ${fetches.updates.size}\n"
            )
        } catch (t: Throwable) {
            File(out, "error.txt").appendText("[user_detail_sections]\n" + t.stackTraceToString())
        }
        // ادعا بیرون از catch تا سیاستِ «خطای رندر = غیرِ مرگبار» بی‌اثرش نکند.
        if (fetchRowHeightDp > 0f) {
            assertTrue(
                "ارتفاعِ هر ردیفِ تاریخچهٔ اشتراک ${fetchRowHeightDp}dp است — سقفِ ۲۶dp (قبلاً ~۳۸dp با دو خط)",
                fetchRowHeightDp <= 26f
            )
        }
    }

    /**
     * سقفِ ردیف‌ها: با ۵ موردِ ورودی، فقط [SUB_FETCH_ROWS] ردیف رندر می‌شود و بقیه در خطِ
     * «+N مورد» جمع می‌شود — همان چیزی که شیت را از بلندشدن نجات می‌دهد.
     */
    @Test fun fetchRows_areCapped_atFourRows() {
        rule.setContent {
            density = LocalDensity.current.density
            Column(Modifier.width(360.dp)) { SubscriptionFetchesCard(fetches) }
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("sub_fetch_row", useUnmergedTree = true).assertCountEquals(SUB_FETCH_ROWS)
        val heights = rule.onAllNodesWithTag("sub_fetch_row", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot.height / density }
        for (h in heights) {
            assertTrue("ردیفِ تاریخچه ${h}dp ارتفاع گرفت (سقفِ ۲۶dp — یعنی دوباره دوخطی شده)", h <= 26f)
        }
    }
}
