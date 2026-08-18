package com.mrm.pgmanager.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.UsernamePattern
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.theme.LampColor
import com.mrm.pgmanager.ui.theme.ThemeState

class SessionStore(context: Context) {
    internal val prefs = EncryptedSharedPreferences.create(
        context,
        "mrm_pg_manager",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // مهاجرت خودکار: اگر نشستِ قدیمیِ تک‌حسابه وجود دارد ولی لیست حساب‌ها خالی است، آن را به لیست منتقل می‌کنیم.
    init {
        if (readAccounts().isEmpty()) {
            val base = prefs.getString("base", null)
            val token = prefs.getString("token", null)
            if (!base.isNullOrBlank() && !token.isNullOrBlank()) {
                saveAccounts(listOf(Session(base, token, prefs.getString("username", "") ?: "")))
            }
        }
    }

    fun read(): Session? {
        val base = prefs.getString("base", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Session(base, token, prefs.getString("username", "") ?: "")
    }

    /** ورود جدید: حساب در لیست ذخیره/به‌روز می‌شود و به‌عنوان حساب فعال تنظیم می‌گردد. */
    fun save(value: Session) {
        val others = readAccounts().filterNot { it.baseUrl == value.baseUrl && it.username == value.username }
        saveAccounts(others + value)
        setActive(value)
    }

    /** کلیدهای نشستِ فعال را بدون تغییر لیست حساب‌ها به حسابِ انتخاب‌شده سوئیچ می‌کند. */
    fun setActive(value: Session) = prefs.edit()
        .putString("base", value.baseUrl)
        .putString("token", value.token)
        .putString("username", value.username)
        .apply()

    /** خروج کامل: نشستِ فعال و حسابِ متناظر از لیست حذف می‌شود (حساب‌های دیگر باقی می‌مانند). */
    fun clear() {
        val active = read()
        if (active != null) saveAccounts(readAccounts().filterNot { it.baseUrl == active.baseUrl && it.username == active.username })
        prefs.edit().remove("base").remove("token").remove("username").apply()
    }

    // === حساب‌های چندگانه (چند پنل هم‌زمان) ===
    fun readAccounts(): List<Session> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val base = o.optString("base"); val token = o.optString("token")
            if (base.isBlank() || token.isBlank()) null else Session(base, token, o.optString("username"))
        }
    }.getOrDefault(emptyList())

    fun saveAccounts(list: List<Session>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("base", it.baseUrl).put("token", it.token).put("username", it.username)) }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    /** حذف یک حساب از لیست (اگر حسابِ فعال باشد حذف نمی‌شود؛ جلوی آن در UI گرفته شده). */
    fun removeAccount(baseUrl: String, username: String) {
        saveAccounts(readAccounts().filterNot { it.baseUrl == baseUrl && it.username == username })
    }

    // === تم ===
    fun readTheme(): ThemeState {
        val lampName = prefs.getString("theme_lamp", LampColor.GOLD.name) ?: LampColor.GOLD.name
        val isDark = prefs.getBoolean("theme_dark", false)
        val followSystem = prefs.getBoolean("theme_follow_system", false)
        val amoled = prefs.getBoolean("theme_amoled", false)
        val custom = prefs.getLong("theme_custom", -1L).takeIf { it >= 0L }
        val lamp = runCatching { LampColor.valueOf(lampName) }.getOrDefault(LampColor.GOLD)
        return ThemeState(lamp = lamp, customColor = custom?.let { androidx.compose.ui.graphics.Color(it) }, isDark = isDark, followSystem = followSystem, amoledDark = amoled)
    }

    fun saveTheme(themeState: ThemeState) = prefs.edit()
        .putString("theme_lamp", themeState.lamp.name)
        .putBoolean("theme_dark", themeState.isDark)
        .putBoolean("theme_follow_system", themeState.followSystem)
        .putBoolean("theme_amoled", themeState.amoledDark)
        .putLong("theme_custom", themeState.customColor?.value?.toLong() ?: -1L)
        .apply()

    // === قفل برنامه ===
    fun readAppLock(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    fun saveAppLock(enabled: Boolean) = prefs.edit().putBoolean("app_lock_enabled", enabled).apply()

    /** مهلت قفل خودکار بر حسب ثانیه؛ 0 یعنی بلافاصله پس از خروج از برنامه. */
    fun readAppLockTimeoutSecs(): Int = prefs.getInt("app_lock_timeout", 0).coerceIn(0, 3600)

    fun saveAppLockTimeoutSecs(value: Int) = prefs.edit().putInt("app_lock_timeout", value.coerceIn(0, 3600)).apply()

    // === حالت نمایش فهرست کاربران ===
    fun readViewMode(): ViewMode = runCatching { ViewMode.valueOf(prefs.getString("view_mode", ViewMode.MICRO_LIST.name) ?: ViewMode.MICRO_LIST.name) }.getOrDefault(ViewMode.MICRO_LIST)

    fun saveViewMode(mode: ViewMode) = prefs.edit().putString("view_mode", mode.name).apply()

    // === زبان برنامه ===
    fun readAppLanguage(): String = prefs.getString("app_language", "system") ?: "system"

    fun saveAppLanguage(value: String) {
        val v = when (value) { "fa", "en", "system" -> value else -> "system" }
        prefs.edit().putString("app_language", v).apply()
    }

    // === الگوی نام کاربری ===
    fun readUsernamePattern() = UsernamePattern(
        prefix = prefs.getString("uname_prefix", "user")?.ifBlank { "user" } ?: "user",
        randomDigits = prefs.getInt("uname_digits", 4).coerceIn(3, 6),
        sequentialStart = prefs.getInt("uname_start", 1).coerceIn(1, 999998),
        sequential = prefs.getBoolean("uname_sequential", false)
    )

    fun saveUsernamePattern(p: UsernamePattern) = prefs.edit()
        .putString("uname_prefix", p.prefix.ifBlank { "user" })
        .putInt("uname_digits", p.randomDigits.coerceIn(3, 6))
        .putInt("uname_start", p.sequentialStart.coerceIn(1, 999998))
        .putBoolean("uname_sequential", p.sequential)
        .apply()

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
        notifyPanelOffline = prefs.getBoolean("notify_panel_offline", true),
        notifyNodeOffline = prefs.getBoolean("notify_node_offline", true),
        offlineCacheEnabled = prefs.getBoolean("offline_cache", true),
        notifyCapacity = prefs.getBoolean("notify_capacity", false),
        capacityOnlineLimit = prefs.getInt("capacity_online", 500),
        debtorAutoDisableEnabled = prefs.getBoolean("debtor_auto_disable", false),
        debtorAutoDisableAfterHours = prefs.getInt("debtor_auto_hours", 24).coerceIn(1, 720),
        notifyDebtor = prefs.getBoolean("notify_debtor", true),
        notifyDebtorOverdue = prefs.getBoolean("notify_debtor_overdue", true),
        debtorCurrency = prefs.getString("debtor_currency", "") ?: ""
    )

    fun saveMonitoringSettings(v: MonitoringSettings) = prefs.edit()
        .putBoolean("monitor_auto", v.autoRefreshEnabled).putInt("monitor_interval", v.refreshIntervalSeconds.coerceIn(5, 3600)).putBoolean("monitor_always", v.refreshWhileAppOpen)
        .putBoolean("notify_enabled", v.notificationsEnabled).putBoolean("notify_actions", v.notifyUserActions).putBoolean("notify_limited", v.notifyLimited).putBoolean("notify_expired", v.notifyExpired)
        .putBoolean("notify_near_limit", v.notifyNearLimit).putInt("notify_limit_percent", v.nearLimitPercent).putBoolean("notify_near_expiry", v.notifyNearExpiry).putInt("notify_expiry_days", v.nearExpiryDays)
        .putBoolean("notify_system", v.notifySystemHealth).putInt("notify_cpu", v.cpuThreshold).putInt("notify_ram", v.ramThreshold).putInt("notify_disk", v.diskThreshold).putBoolean("notify_panel_offline", v.notifyPanelOffline).putBoolean("notify_node_offline", v.notifyNodeOffline)
        .putBoolean("offline_cache", v.offlineCacheEnabled).putBoolean("notify_capacity", v.notifyCapacity).putInt("capacity_online", v.capacityOnlineLimit)
        .putBoolean("debtor_auto_disable", v.debtorAutoDisableEnabled).putInt("debtor_auto_hours", v.debtorAutoDisableAfterHours.coerceIn(1,720))
        .putBoolean("notify_debtor", v.notifyDebtor).putBoolean("notify_debtor_overdue", v.notifyDebtorOverdue)
        .putString("debtor_currency", v.debtorCurrency)
        .apply()

    fun readNotificationStates(): Map<Long, String> = runCatching {
        val obj = org.json.JSONObject(prefs.getString("notification_user_states", "{}") ?: "{}")
        obj.keys().asSequence().associate { it.toLong() to obj.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveNotificationStates(states: Map<Long, String>) = prefs.edit().putString("notification_user_states", org.json.JSONObject(states.mapKeys { it.key.toString() }).toString()).apply()

    /** فلگ‌های latch اعلان‌ها (برای جلوگیری از اسپمِ هشدارهای مکرر در Worker). */
    fun readAlertFlag(key: String): Boolean = prefs.getBoolean("alert_$key", false)
    fun saveAlertFlag(key: String, value: Boolean) = prefs.edit().putBoolean("alert_$key", value).apply()

    /** آخرین وضعیت آنلاین/آفلاین نودها برای تشخیص تغییر در پس‌زمینه. */
    fun readNodeStates(): Map<Int, Boolean> = runCatching {
        val obj = org.json.JSONObject(prefs.getString("node_states", "{}") ?: "{}")
        obj.keys().asSequence().associate { it.toInt() to obj.getBoolean(it) }
    }.getOrDefault(emptyMap())

    fun saveNodeStates(states: Map<Int, Boolean>) = prefs.edit().putString("node_states", org.json.JSONObject(states.mapKeys { it.key.toString() }).toString()).apply()

    // === کش آفلاین: آخرین داده‌های موفق دریافتی (کل لیستِ لودشده، بدون محدودیت ۴۰۰تایی) ===
    fun saveUsersCache(users: List<PanelUser>) {
        val arr = org.json.JSONArray()
        users.forEach { u ->
            arr.put(org.json.JSONObject().apply {
                put("id", u.id); put("username", u.username); put("status", u.status)
                put("used_traffic", u.usedTraffic); put("data_limit", u.dataLimit)
                put("expire", u.expire ?: ""); put("created_at", u.createdAt ?: "")
                put("sub_url", u.subUrl); put("online_at", u.onlineAt ?: ""); put("note", u.note ?: "")
                if (u.hwidLimit != null) put("hwid_limit", u.hwidLimit)
                put("group_ids", org.json.JSONArray(u.groupIds)); put("group_names", org.json.JSONArray(u.groupNames))
            })
        }
        prefs.edit().putString("users_cache", arr.toString()).putLong("users_cache_ts", System.currentTimeMillis()).apply()
    }

    /** زوج (لیست کاربران، زمان کش)؛ null یعنی کش در دسترس نیست. isOnline همیشه false بازیابی می‌شود. */
    fun readUsersCache(): Pair<List<PanelUser>, Long>? {
        val raw = prefs.getString("users_cache", null) ?: return null
        val ts = prefs.getLong("users_cache_ts", 0L)
        if (ts <= 0L) return null
        val list = runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PanelUser(
                    id = o.optLong("id"), username = o.optString("username"), status = o.optString("status"),
                    usedTraffic = o.optLong("used_traffic"), dataLimit = o.optLong("data_limit"),
                    expire = o.optString("expire").ifBlank { null }, createdAt = o.optString("created_at").ifBlank { null },
                    subUrl = o.optString("sub_url"), onlineAt = o.optString("online_at").ifBlank { null }, isOnline = false,
                    note = o.optString("note").ifBlank { null }, hwidLimit = if (o.has("hwid_limit") && !o.isNull("hwid_limit")) o.optInt("hwid_limit").takeIf { it > 0 } else null,
                    groupIds = o.optJSONArray("group_ids")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
                    groupNames = o.optJSONArray("group_names")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                )
            }
        }.getOrDefault(emptyList())
        return if (list.isEmpty()) null else list to ts
    }

    fun saveStatsCache(s: SystemStats) {
        prefs.edit().putString("stats_cache", org.json.JSONObject().apply {
            put("uptime_seconds", s.uptimeSeconds); put("mem_total", s.memTotal); put("mem_used", s.memUsed)
            put("disk_total", s.diskTotal); put("disk_used", s.diskUsed); put("cpu_cores", s.cpuCores); put("cpu_usage", s.cpuUsage.toDouble())
            put("total_users", s.totalUsers); put("online_users", s.onlineUsers); put("active_users", s.activeUsers)
            put("expired_users", s.expiredUsers); put("limited_users", s.limitedUsers); put("disabled_users", s.disabledUsers); put("on_hold_users", s.onHoldUsers)
            put("incoming_bandwidth", s.incomingBandwidth); put("outgoing_bandwidth", s.outgoingBandwidth)
        }.toString()).putLong("stats_cache_ts", System.currentTimeMillis()).apply()
    }

    fun readStatsCache(): Pair<SystemStats, Long>? {
        val raw = prefs.getString("stats_cache", null) ?: return null
        val ts = prefs.getLong("stats_cache_ts", 0L)
        if (ts <= 0L) return null
        return runCatching {
            val o = org.json.JSONObject(raw)
            SystemStats(
                uptimeSeconds = o.optLong("uptime_seconds"), memTotal = o.optLong("mem_total"), memUsed = o.optLong("mem_used"),
                diskTotal = o.optLong("disk_total"), diskUsed = o.optLong("disk_used"), cpuCores = o.optInt("cpu_cores"), cpuUsage = o.optDouble("cpu_usage").toFloat(),
                totalUsers = o.optInt("total_users"), onlineUsers = o.optInt("online_users"), activeUsers = o.optInt("active_users"),
                expiredUsers = o.optInt("expired_users"), limitedUsers = o.optInt("limited_users"), disabledUsers = o.optInt("disabled_users"), onHoldUsers = o.optInt("on_hold_users"),
                incomingBandwidth = o.optLong("incoming_bandwidth"), outgoingBandwidth = o.optLong("outgoing_bandwidth")
            ) to ts
        }.getOrNull()
    }

    // === بدهکاران ===
    fun debtorKey(baseUrl: String, username: String): String = "$baseUrl|$username"

    fun readDebtors(): Map<String, com.mrm.pgmanager.data.model.DebtorInfo> = runCatching {
        val raw = prefs.getString("debtors", "[]") ?: "[]"
        val arr = org.json.JSONArray(raw)
        val map = mutableMapOf<String, com.mrm.pgmanager.data.model.DebtorInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val username = o.optString("username"); if (username.isBlank()) continue
            val baseUrl = o.optString("baseUrl").ifBlank { o.optString("base_url") }
            if (baseUrl.isBlank()) continue
            val info = com.mrm.pgmanager.data.model.DebtorInfo(
                username = username,
                baseUrl = baseUrl,
                amount = o.optLong("amount", 0L),
                currency = o.optString("currency", "").ifBlank { "" },
                markedAt = o.optLong("markedAt", o.optLong("marked_at", System.currentTimeMillis())),
                notes = o.optString("notes", o.optString("note", "")),
                autoDisabled = o.optBoolean("autoDisabled", o.optBoolean("auto_disabled", false)),
                userId = o.optLong("userId", o.optLong("user_id", 0L))
            )
            map[debtorKey(baseUrl, username)] = info
        }
        map
    }.getOrDefault(emptyMap())

    fun saveDebtors(map: Map<String, com.mrm.pgmanager.data.model.DebtorInfo>) {
        val arr = org.json.JSONArray()
        map.values.forEach { d ->
            arr.put(org.json.JSONObject().apply {
                put("username", d.username)
                put("baseUrl", d.baseUrl)
                put("amount", d.amount)
                put("currency", d.currency)
                put("markedAt", d.markedAt)
                put("notes", d.notes)
                put("autoDisabled", d.autoDisabled)
                put("userId", d.userId)
            })
        }
        prefs.edit().putString("debtors", arr.toString()).apply()
    }

    fun getDebtor(baseUrl: String, username: String): com.mrm.pgmanager.data.model.DebtorInfo? {
        return readDebtors()[debtorKey(baseUrl, username)]
    }

    fun setDebtor(info: com.mrm.pgmanager.data.model.DebtorInfo) {
        val map = readDebtors().toMutableMap()
        map[debtorKey(info.baseUrl, info.username)] = info
        saveDebtors(map)
    }

    fun removeDebtor(baseUrl: String, username: String) {
        val map = readDebtors().toMutableMap()
        map.remove(debtorKey(baseUrl, username))
        saveDebtors(map)
    }

    fun readDebtorsForBase(baseUrl: String): List<com.mrm.pgmanager.data.model.DebtorInfo> {
        return readDebtors().values.filter { it.baseUrl == baseUrl }
    }

    // === لوگوی فاکتور (در فایل خصوصی برنامه ذخیره می‌شود) ===
    fun hasInvoiceLogo(): Boolean = prefs.contains("invoice_logo_path")
    fun readInvoiceLogoPath(): String? = prefs.getString("invoice_logo_path", null)
    fun saveInvoiceLogoPath(path: String?) = prefs.edit().putString("invoice_logo_path", path).apply()
    fun clearInvoiceLogo() = prefs.edit().remove("invoice_logo_path").apply()

    // === نام فروشنده/برند برای فاکتور ===
    fun readInvoiceSeller(): String = prefs.getString("invoice_seller", "") ?: ""
    fun saveInvoiceSeller(name: String) = prefs.edit().putString("invoice_seller", name).apply()

    // === تنظیمات پشتیبان‌گیری ===
    fun readBackupUri(): String? = prefs.getString("backup_uri", null)
    fun saveBackupUri(uri: String?) = prefs.edit().putString("backup_uri", uri).apply()
    fun readBackupEnabled(): Boolean = prefs.getBoolean("backup_enabled", false)
    fun saveBackupEnabled(v: Boolean) = prefs.edit().putBoolean("backup_enabled", v).apply()
    /** بازه پشتیبان‌گیری خودکار بر حسب ساعت (0=فقط دستی، ۶/۱۲/۲۴/۷۲ ساعت) */
    fun readBackupIntervalHours(): Int = prefs.getInt("backup_interval_hours", 24).coerceIn(0, 720)
    fun saveBackupIntervalHours(h: Int) = prefs.edit().putInt("backup_interval_hours", h.coerceIn(0, 720)).apply()
    fun readBackupPassword(): String = prefs.getString("backup_password", "") ?: ""
    fun saveBackupPassword(p: String) = prefs.edit().putString("backup_password", p).apply()
    fun readBackupKeepCount(): Int = prefs.getInt("backup_keep_count", 7).coerceIn(2, 30)
    fun saveBackupKeepCount(n: Int) = prefs.edit().putInt("backup_keep_count", n.coerceIn(2, 30)).apply()
    fun readLastBackupAt(): Long = prefs.getLong("backup_last_at", 0L)
    fun saveLastBackupAt(ts: Long) = prefs.edit().putLong("backup_last_at", ts).apply()
    fun readLastBackupSuccess(): Boolean = prefs.getBoolean("backup_last_ok", true)
    fun saveLastBackupSuccess(ok: Boolean) = prefs.edit().putBoolean("backup_last_ok", ok).apply()
    fun readLastBackupMessage(): String = prefs.getString("backup_last_msg", "") ?: ""
    fun saveLastBackupMessage(m: String) = prefs.edit().putString("backup_last_msg", m).apply()
}
