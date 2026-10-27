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

        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        assertEquals(saved, SessionStore(context).readUiPrefs())
    }

    @Test fun `invalid persisted and written values are bounded`() {
        prefs.edit().putInt("ui_font_scale_percent", -10).commit()
        assertEquals(AppFontScale.MIN_PERCENT, store.readUiPrefs().fontScalePercent)
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 1000))
        assertEquals(AppFontScale.MAX_PERCENT, store.readUiPrefs().fontScalePercent)
    }

    @Test fun `backup includes the current text size`() {
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 120))
        val output = ByteArrayOutputStream()
        BackupManager.createBackup(context, output)
        val data = JSONObject(output.toString(Charsets.UTF_8.name())).getJSONObject("data")
        assertEquals(120, data.getJSONObject("theme").getInt("font_scale_percent"))
    }

    @Test fun `restore updates only the font preference and keeps other ui preferences`() {
        val current = UiPrefs(hapticsEnabled = false, reduceMotion = true, secureScreen = true, fontScalePercent = 80)
        store.saveUiPrefs(current)
        restoreTheme("\"font_scale_percent\": 130")
        assertEquals(current.copy(fontScalePercent = 130), store.readUiPrefs())
    }

    @Test fun `older backups and skipping settings keep the current font size`() {
        store.saveUiPrefs(store.readUiPrefs().copy(fontScalePercent = 120))
        restoreTheme("")
        assertEquals(120, store.readUiPrefs().fontScalePercent)
        restoreTheme("\"font_scale_percent\": 80", restoreSettings = false)
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
