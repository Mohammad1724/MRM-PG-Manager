package com.mrm.pgmanager.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import com.mrm.pgmanager.ui.designsystem.ScreenSecurity
import com.mrm.pgmanager.ui.dialogs.SubscriptionQrDialog
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionShareSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val user = PanelUser(
        id = 12L, username = "share-user", status = "active", usedTraffic = 0L, dataLimit = 0L,
        expire = null, createdAt = null, subUrl = "https://example.test/sub/share-user"
    )
    private var rowClicks = 0
    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { context -> context.getSharedPreferences("subscription_share_test", Context.MODE_PRIVATE) }
        RuntimeEnvironment.getApplication().getSharedPreferences("subscription_share_test", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After fun tearDown() {
        MotionPrefs.reduceMotion = false
        SessionStore.resetPrefsForTests()
    }

    @Suppress("DEPRECATION")
    private fun assertImageShare(chooser: Intent, expectedFilePrefix: String) {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val share = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("image/png", share.type)
        assertEquals(user.subUrl, share.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = requireNotNull(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("${rule.activity.packageName}.fileprovider", uri.authority)
        assertTrue(uri.lastPathSegment.orEmpty().startsWith(expectedFilePrefix))
        val signature = ByteArray(8)
        rule.activity.contentResolver.openInputStream(uri).use { stream ->
            assertEquals(8, requireNotNull(stream).read(signature))
        }
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), signature)
    }

    @Test fun `share opens existing secure sheet with QR copy and card sharing`() {
        val initialHolds = ScreenSecurity.holds
        rule.setContent {
            var selected by remember { mutableStateOf<PanelUser?>(null) }
            Box(Modifier.width(360.dp)) {
                LuxuryMicroRow(user = user, onClick = { rowClicks++ }, onShareSubscription = { selected = it })
            }
            selected?.let { current -> SubscriptionQrDialog(user = current, onDismiss = { selected = null }) }
        }

        rule.onNodeWithContentDescription(str(R.string.us_share_subscription, user.username))
            .performTouchInput { click(center) }
        rule.onNodeWithText("QR ${user.username}").assertIsDisplayed()
        rule.onNodeWithContentDescription("QR").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.qr_copy)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.qr_only)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.qr_send_card)).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(0, rowClicks)
            assertEquals(initialHolds + 1, ScreenSecurity.holds)
        }

        rule.onNodeWithText(str(R.string.qr_copy)).performClick()
        rule.runOnIdle {
            val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(user.subUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }

        rule.onNodeWithText(str(R.string.qr_only)).performClick()
        val shadow = shadowOf(rule.activity)
        rule.waitUntil(20_000) { shadow.peekNextStartedActivity() != null }
        assertImageShare(requireNotNull(shadow.nextStartedActivity), "qr-${user.username}")

        rule.onNodeWithText(str(R.string.qr_send_card)).performClick()
        rule.waitUntil(20_000) { shadow.peekNextStartedActivity() != null }
        assertImageShare(requireNotNull(shadow.nextStartedActivity), "card-${user.username}")

        rule.onNodeWithText(str(R.string.qr_close)).performClick()
        rule.runOnIdle { assertEquals(initialHolds, ScreenSecurity.holds) }
    }
}
