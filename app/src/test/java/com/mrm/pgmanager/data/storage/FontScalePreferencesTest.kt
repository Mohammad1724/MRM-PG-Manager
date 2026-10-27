package com.mrm.pgmanager.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.mrm.pgmanager.data.model.AppFontScale
import com.mrm.pgmanager.data.model.UiPrefs
import com.mrm.pgmanager.utils.BackupManager
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FontScalePreferencesTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var store: SessionStore

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("font-scale-preferences-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        store = SessionStore(context)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        SessionStore.resetPrefsForTests()
    }

    @Test fun `default and saved size survive recreating the store`() {
        assertEquals(AppFontScale.DEFAULT_PERCENT, store.readUiPrefs().fontScalePercent)
        val saved = UiPrefs(hapticsEnabled = false, reduceMotion = true, secureScreen = true, fontScalePercent = 130)
        store.saveUiPrefs(saved)
        assertEquals(130, prefs.getInt("ui_font_scale_percent_v2", -1))
        assertEquals(104, prefs.getInt("ui_font_scale_percent", -1))

        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        assertEquals(saved, SessionStore(context).readUiPrefs())
    }

    @Test fun `legacy sizes migrate without changing unrelated settings`() {
        val conversions = mapOf(80 to 100, 90 to 113, 100 to 125, 110 to 138, 120 to 150, 130 to 163, 140 to 175)
        conversions.forEach { (legacy, current) ->
            prefs.edit().clear()
                .putInt("ui_font_scale_percent", legacy)
                .putBoolean("ui_haptics", false)
                .putBoolean("ui_reduce_motion", true)
                .putBoolean("ui_secure_screen", true)
                .commit()
            val expected = UiPrefs(hapticsEnabled = false, reduceMotion = true, secureScreen = true, fontScalePercent = current)
            assertEquals(expected, store.readUiPrefs())
            assertEquals(expected, store.readUiPrefs())
        }
    }

    @Test fun `a new value after migration is not treated as legacy again`() {
        prefs.edit().putInt("ui_font_scale_percent", 80).commit()
        assertEquals(100, store.readUiPrefs().fontScalePercent)
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 90))
        assertEquals(90, prefs.getInt("ui_font_scale_percent_v2", -1))
        assertEquals(72, prefs.getInt("ui_font_scale_percent", -1))
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        assertEquals(90, SessionStore(context).readUiPrefs().fontScalePercent)
    }

    @Test fun `invalid persisted and written values are bounded`() {
        prefs.edit().putInt("ui_font_scale_percent_v2", -10).commit()
        assertEquals(AppFontScale.MIN_PERCENT, store.readUiPrefs().fontScalePercent)
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 1000))
        assertEquals(AppFontScale.MAX_PERCENT, store.readUiPrefs().fontScalePercent)
    }

    @Test fun `backup includes both new and older compatible text sizes`() {
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 90))
        val output = ByteArrayOutputStream()
        BackupManager.createBackup(context, output)
        val data = JSONObject(output.toString(Charsets.UTF_8.name())).getJSONObject("data")
        val theme = data.getJSONObject("theme")
        assertEquals(72, theme.getInt("font_scale_percent"))
        assertEquals(90, theme.getInt("font_scale_percent_v2"))

        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 140))
        BackupManager.restoreBackup(
            context = context,
            input = output.toByteArray().inputStream(),
            restoreAccounts = false,
            restoreDebtors = false,
            restoreSettings = true,
            restoreInvoice = false
        )
        assertEquals(90, store.readUiPrefs().fontScalePercent)
    }

    @Test fun `restoring a new backup keeps other ui preferences`() {
        val current = UiPrefs(hapticsEnabled = false, reduceMotion = true, secureScreen = true, fontScalePercent = 80)
        store.saveUiPrefs(current)
        restoreTheme("\"font_scale_percent\": 104, \"font_scale_percent_v2\": 130")
        assertEquals(current.copy(fontScalePercent = 130), store.readUiPrefs())
    }

    @Test fun `legacy backups are converted to the new scale`() {
        store.saveUiPrefs(UiPrefs(hapticsEnabled = false, reduceMotion = true, secureScreen = true))
        restoreTheme("\"font_scale_percent\": 80")
        assertEquals(100, store.readUiPrefs().fontScalePercent)
        restoreTheme("\"font_scale_percent\": 100")
        assertEquals(125, store.readUiPrefs().fontScalePercent)
        restoreTheme("\"font_scale_percent\": 140")
        assertEquals(175, store.readUiPrefs().fontScalePercent)
        assertEquals(false, store.readUiPrefs().hapticsEnabled)
        assertEquals(true, store.readUiPrefs().secureScreen)
    }

    @Test fun `older backups and skipping settings keep the current font size`() {
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 120))
        restoreTheme("")
        assertEquals(120, store.readUiPrefs().fontScalePercent)
        restoreTheme("\"font_scale_percent\": 80, \"font_scale_percent_v2\": 90", restoreSettings = false)
        assertEquals(120, store.readUiPrefs().fontScalePercent)
    }

    private fun restoreTheme(fields: String, restoreSettings: Boolean = true) {
        val json = """{"_meta":{"v":1},"data":{"theme":{$fields}}}"""
        BackupManager.restoreBackup(
            context = context,
            input = json.byteInputStream(),
            restoreAccounts = false,
            restoreDebtors = false,
            restoreSettings = restoreSettings,
            restoreInvoice = false
        )
    }
}
