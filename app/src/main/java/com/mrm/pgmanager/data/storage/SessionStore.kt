package com.mrm.pgmanager.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.theme.LampColor
import com.mrm.pgmanager.ui.theme.ThemeState

class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mrm_pg_manager",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read(): Session? {
        val base = prefs.getString("base", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Session(base, token, prefs.getString("username", "") ?: "")
    }

    fun save(value: Session) = prefs.edit()
        .putString("base", value.baseUrl)
        .putString("token", value.token)
        .putString("username", value.username)
        .apply()

    fun clear() = prefs.edit()
        .remove("base")
        .remove("token")
        .remove("username")
        .apply()

    fun readTheme(): ThemeState {
        val lampName = prefs.getString("theme_lamp", LampColor.GOLD.name) ?: LampColor.GOLD.name
        val isDark = prefs.getBoolean("theme_dark", false)
        val lamp = runCatching { LampColor.valueOf(lampName) }.getOrDefault(LampColor.GOLD)
        return ThemeState(lamp = lamp, isDark = isDark)
    }

    fun saveTheme(themeState: ThemeState) = prefs.edit()
        .putString("theme_lamp", themeState.lamp.name)
        .putBoolean("theme_dark", themeState.isDark)
        .apply()

    fun readAppLock(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    fun saveAppLock(enabled: Boolean) = prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
}
