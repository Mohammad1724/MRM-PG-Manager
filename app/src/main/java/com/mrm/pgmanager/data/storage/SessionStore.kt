package com.mrm.pgmanager.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.MonitoringSettings
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
        val followSystem = prefs.getBoolean("theme_follow_system", false)
        val lamp = runCatching { LampColor.valueOf(lampName) }.getOrDefault(LampColor.GOLD)
        return ThemeState(lamp = lamp, isDark = isDark, followSystem = followSystem)
    }

    fun saveTheme(themeState: ThemeState) = prefs.edit()
        .putString("theme_lamp", themeState.lamp.name)
        .putBoolean("theme_dark", themeState.isDark)
        .putBoolean("theme_follow_system", themeState.followSystem)
        .apply()

    fun readAppLock(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    fun saveAppLock(enabled: Boolean) = prefs.edit().putBoolean("app_lock_enabled", enabled).apply()

    fun readMonitoringSettings() = MonitoringSettings(
        autoRefreshEnabled = prefs.getBoolean("monitor_auto", true),
        refreshIntervalSeconds = prefs.getInt("monitor_interval", 10).coerceIn(5, 3600),
        refreshWhileAppOpen = prefs.getBoolean("monitor_always", false),
        notificationsEnabled = prefs.getBoolean("notify_enabled", true),
        notifyUserActions = prefs.getBoolean("notify_actions", true),
        notifyLimited = prefs.getBoolean("notify_limited", true),
        notifyExpired = prefs.getBoolean("notify_expired", true),
        notifyNearLimit = prefs.getBoolean("notify_near_limit", true),
        nearLimitPercent = prefs.getInt("notify_limit_percent", 80),
        notifyNearExpiry = prefs.getBoolean("notify_near_expiry", true),
        nearExpiryDays = prefs.getInt("notify_expiry_days", 1),
        notifySystemHealth = prefs.getBoolean("notify_system", true),
        cpuThreshold = prefs.getInt("notify_cpu", 85), ramThreshold = prefs.getInt("notify_ram", 85), diskThreshold = prefs.getInt("notify_disk", 90),
        notifyPanelOffline = prefs.getBoolean("notify_panel_offline", true)
    )

    fun saveMonitoringSettings(v: MonitoringSettings) = prefs.edit()
        .putBoolean("monitor_auto", v.autoRefreshEnabled).putInt("monitor_interval", v.refreshIntervalSeconds.coerceIn(5, 3600)).putBoolean("monitor_always", v.refreshWhileAppOpen)
        .putBoolean("notify_enabled", v.notificationsEnabled).putBoolean("notify_actions", v.notifyUserActions).putBoolean("notify_limited", v.notifyLimited).putBoolean("notify_expired", v.notifyExpired)
        .putBoolean("notify_near_limit", v.notifyNearLimit).putInt("notify_limit_percent", v.nearLimitPercent).putBoolean("notify_near_expiry", v.notifyNearExpiry).putInt("notify_expiry_days", v.nearExpiryDays)
        .putBoolean("notify_system", v.notifySystemHealth).putInt("notify_cpu", v.cpuThreshold).putInt("notify_ram", v.ramThreshold).putInt("notify_disk", v.diskThreshold).putBoolean("notify_panel_offline", v.notifyPanelOffline).apply()
}
