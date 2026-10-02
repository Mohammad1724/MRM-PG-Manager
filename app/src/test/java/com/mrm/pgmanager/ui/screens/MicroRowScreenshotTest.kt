package com.mrm.pgmanager.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
    @get:Rule val rule = createComposeRule()

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
                Column(Modifier.width(360.dp).background(Color(0xFFF8F9FB)).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    users.forEach { LuxuryMicroRow(it, onClick = {}) }
                }
            }
            rule.waitForIdle()
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(out, "micro_rows.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            File(out, "error.txt").writeText(t.stackTraceToString())
        }
    }
}
