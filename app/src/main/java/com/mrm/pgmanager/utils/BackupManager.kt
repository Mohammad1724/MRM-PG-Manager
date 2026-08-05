package com.mrm.pgmanager.utils

import android.content.Context
import android.net.Uri
import com.mrm.pgmanager.data.storage.SessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * مدیریت پشتیبان‌گیری و بازیابی تنظیمات.
 * فرمت فایل: JSON رمزنگاری‌شده با AES-256 (PBKDF2) یا JSON خام (بدون رمز).
 */
object BackupManager {

    const val BACKUP_VERSION = 1
    const val FILE_PREFIX = "mrm-backup-"
    const val FILE_EXT = ".mbak"
    private const val SALT_LEN = 16
    private const val IV_LEN = 16
    private const val ITERATIONS = 65536
    private const val KEY_LEN = 256

    data class BackupInfo(
        val version: Int,
        val createdAt: Long,
        val appVersion: String,
        val accountsCount: Int,
        val debtorsCount: Int,
        val hasLogo: Boolean,
        val sellerName: String,
        val encrypted: Boolean
    )

    // ============ Build payload ============

    private fun buildPayload(store: SessionStore): JSONObject {
        val prefs = store.prefs  // needs expose for full dump
        val allPrefs = prefs.all
        val json = JSONObject()

        // === حساب‌ها (پنل‌های متصل) ===
        json.put("accounts", JSONArray().apply {
            store.readAccounts().forEach { acc ->
                put(JSONObject()
                    .put("base", acc.baseUrl)
                    .put("token", acc.token)
                    .put("username", acc.username))
            }
        })
        val active = store.read()
        if (active != null) {
            json.put("active_base", active.baseUrl)
            json.put("active_username", active.username)
        }

        // === تم ===
        json.put("theme", JSONObject().apply {
            val t = store.readTheme()
            put("lamp", t.lamp.name)
            put("dark", t.isDark)
            put("follow_system", t.followSystem)
            put("amoled", t.amoledDark)
            put("custom", t.customColor?.value?.toLong() ?: -1L)
        })

        // === قفل برنامه ===
        json.put("app_lock", JSONObject().apply {
            put("enabled", store.readAppLock())
            put("timeout", store.readAppLockTimeoutSecs())
        })

        // === حالت نمایش ===
        json.put("view_mode", store.readViewMode().name)

        // === الگوی نام ===
        json.put("username_pattern", JSONObject().apply {
            val p = store.readUsernamePattern()
            put("prefix", p.prefix)
            put("digits", p.randomDigits)
            put("start", p.sequentialStart)
            put("sequential", p.sequential)
        })

        // === تنظیمات پایش ===
        json.put("monitoring", JSONObject().apply {
            val m = store.readMonitoringSettings()
            put("auto", m.autoRefreshEnabled)
            put("interval", m.refreshIntervalSeconds)
            put("always", m.refreshWhileAppOpen)
            put("notify_enabled", m.notificationsEnabled)
            put("notify_actions", m.notifyUserActions)
            put("notify_limited", m.notifyLimited)
            put("notify_expired", m.notifyExpired)
            put("notify_near_limit", m.notifyNearLimit)
            put("limit_percent", m.nearLimitPercent)
            put("notify_near_expiry", m.notifyNearExpiry)
            put("expiry_days", m.nearExpiryDays)
            put("notify_system", m.notifySystemHealth)
            put("cpu", m.cpuThreshold)
            put("ram", m.ramThreshold)
            put("disk", m.diskThreshold)
            put("notify_panel_offline", m.notifyPanelOffline)
            put("notify_node_offline", m.notifyNodeOffline)
            put("offline_cache", m.offlineCacheEnabled)
            put("notify_capacity", m.notifyCapacity)
            put("capacity_online", m.capacityOnlineLimit)
            put("debtor_auto_disable", m.debtorAutoDisableEnabled)
            put("debtor_auto_hours", m.debtorAutoDisableAfterHours)
            put("notify_debtor", m.notifyDebtor)
            put("notify_debtor_overdue", m.notifyDebtorOverdue)
            put("debtor_currency", m.debtorCurrency)
        })

        // === بدهکاران ===
        json.put("debtors", JSONArray().apply {
            store.readDebtors().values.forEach { d ->
                put(JSONObject()
                    .put("username", d.username)
                    .put("baseUrl", d.baseUrl)
                    .put("amount", d.amount)
                    .put("currency", d.currency)
                    .put("markedAt", d.markedAt)
                    .put("notes", d.notes)
                    .put("autoDisabled", d.autoDisabled)
                    .put("userId", d.userId))
            }
        })

        // === فاکتور ===
        json.put("invoice", JSONObject().apply {
            put("seller", store.readInvoiceSeller())
            val logoPath = store.readInvoiceLogoPath()
            if (!logoPath.isNullOrBlank()) {
                val f = File(logoPath)
                if (f.exists()) {
                    put("logo_b64", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                }
            }
        })

        // === فیلترها / وضعیت اعلان‌ها/نودها ===
        json.put("notification_states", allPrefs["notification_user_states"] as? String ?: "{}")
        json.put("node_states", allPrefs["node_states"] as? String ?: "{}")

        return json
    }

    // ============ Encrypt/Decrypt ============

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val tmp = factory.generateSecret(spec).encoded
        return SecretKeySpec(tmp, "AES")
    }

    private fun encrypt(raw: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val enc = cipher.doFinal(raw)
        // Output: salt(16) + iv(16) + ciphertext
        return ByteArrayOutputStream(salt.size + iv.size + enc.size).apply {
            write(salt); write(iv); write(enc)
        }.toByteArray()
    }

    private fun decrypt(data: ByteArray, password: String): ByteArray {
        require(data.size > SALT_LEN + IV_LEN) { "فایل بکاپ خراب است" }
        val salt = data.copyOfRange(0, SALT_LEN)
        val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val enc = data.copyOfRange(SALT_LEN + IV_LEN, data.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(enc)
    }

    // ============ Create backup ============

    fun generateFileName(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        return FILE_PREFIX + sdf.format(Date(timestamp)) + FILE_EXT
    }

    fun createBackup(
        context: Context,
        out: OutputStream,
        password: String = "",
        appVersion: String = ""
    ): BackupInfo {
        val store = SessionStore(context)
        val payload = buildPayload(store)
        val meta = JSONObject().apply {
            put("v", BACKUP_VERSION)
            put("ts", System.currentTimeMillis())
            put("app", appVersion)
            put("encrypted", password.isNotBlank())
        }
        val container = JSONObject().apply {
            put("_meta", meta)
            put("data", payload)
        }
        val raw = container.toString(2).toByteArray(Charsets.UTF_8)
        val outBytes = if (password.isNotBlank()) {
            meta.put("enc", "AES-256/PBKDF2")
            container.put("_meta", meta)
            val finalRaw = container.toString(2).toByteArray(Charsets.UTF_8)
            encrypt(finalRaw, password)
        } else raw
        out.use { it.write(outBytes) }

        store.saveLastBackupAt(meta.getLong("ts"))
        store.saveLastBackupSuccess(true)
        store.saveLastBackupMessage("پشتیبان‌گیری موفق")

        return BackupInfo(
            version = meta.getInt("v"),
            createdAt = meta.getLong("ts"),
            appVersion = meta.optString("app", ""),
            accountsCount = payload.optJSONArray("accounts")?.length() ?: 0,
            debtorsCount = payload.optJSONArray("debtors")?.length() ?: 0,
            hasLogo = payload.optJSONObject("invoice")?.optString("logo_b64", "")?.isNotBlank() == true,
            sellerName = payload.optJSONObject("invoice")?.optString("seller", "") ?: "",
            encrypted = password.isNotBlank()
        )
    }

    // ============ Inspect/restore ============

    fun inspect(input: InputStream, password: String = ""): BackupInfo {
        val bytes = input.use { it.readBytes() }
        return try {
            // First try parse as plain JSON
            val s = String(bytes, Charsets.UTF_8)
            if (s.trimStart().startsWith("{")) {
                parseBackupInfo(JSONObject(s))
            } else {
                // Encrypted binary
                require(password.isNotBlank()) { "این بکاپ رمزنگاری شده؛ رمز وارد کنید." }
                val dec = decrypt(bytes, password)
                parseBackupInfo(JSONObject(String(dec, Charsets.UTF_8)))
            }
        } catch (e: Exception) {
            if (password.isBlank() && bytes.size > SALT_LEN + IV_LEN) {
                // Likely encrypted but password not provided
                throw IllegalArgumentException("این فایل بکاپ رمزنگاری شده است. لطفاً رمز را وارد کنید.", e)
            }
            throw e
        }
    }

    private fun parseBackupInfo(obj: JSONObject): BackupInfo {
        val meta = obj.optJSONObject("_meta") ?: throw IllegalArgumentException("فایل بکاپ نامعتبر است")
        val data = obj.optJSONObject("data") ?: throw IllegalArgumentException("بدون داده")
        val invoice = data.optJSONObject("invoice")
        return BackupInfo(
            version = meta.optInt("v", 0),
            createdAt = meta.optLong("ts", 0L),
            appVersion = meta.optString("app", ""),
            accountsCount = data.optJSONArray("accounts")?.length() ?: 0,
            debtorsCount = data.optJSONArray("debtors")?.length() ?: 0,
            hasLogo = invoice?.optString("logo_b64", "")?.isNotBlank() == true,
            sellerName = invoice?.optString("seller", "") ?: "",
            encrypted = meta.optBoolean("encrypted", false)
        )
    }

    fun restoreBackup(
        context: Context,
        input: InputStream,
        password: String = "",
        restoreAccounts: Boolean = true,
        restoreDebtors: Boolean = true,
        restoreSettings: Boolean = true,
        restoreInvoice: Boolean = true
    ): Pair<Int, String> {
        val bytes = input.use { it.readBytes() }
        val container: JSONObject
        val s0 = String(bytes, Charsets.UTF_8)
        container = if (s0.trimStart().startsWith("{")) {
            JSONObject(s0)
        } else {
            require(password.isNotBlank()) { "رمز بکاپ را وارد کنید." }
            JSONObject(String(decrypt(bytes, password), Charsets.UTF_8))
        }
        val data = container.optJSONObject("data") ?: throw IllegalArgumentException("فایل بکاپ خراب است")
        val store = SessionStore(context)
        var restored = 0

        if (restoreSettings) {
            // تم
            data.optJSONObject("theme")?.let { t ->
                val lamp = runCatching {
                    com.mrm.pgmanager.ui.theme.LampColor.valueOf(t.optString("lamp", "GOLD"))
                }.getOrDefault(com.mrm.pgmanager.ui.theme.LampColor.GOLD)
                val custom = t.optLong("custom", -1L).takeIf { it >= 0L }?.let { androidx.compose.ui.graphics.Color(it) }
                store.saveTheme(com.mrm.pgmanager.ui.theme.ThemeState(
                    lamp = lamp,
                    customColor = custom,
                    isDark = t.optBoolean("dark", false),
                    followSystem = t.optBoolean("follow_system", false),
                    amoledDark = t.optBoolean("amoled", false)
                ))
                restored++
            }
            // قفل برنامه — فقط اگر دستگاه بیومتریک/پین فعال دارد، قفل بازیابی شود
            // (در غیر این صورت کاربر بدون امکان بازکردن قفل گیر نمی‌افتد).
            data.optJSONObject("app_lock")?.let { a ->
                val canAuthenticate = androidx.biometric.BiometricManager.from(context)
                    .canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                store.saveAppLock(a.optBoolean("enabled", false) && canAuthenticate)
                store.saveAppLockTimeoutSecs(a.optInt("timeout", 0))
                restored++
            }
            // حالت نمایش
            data.optString("view_mode", "").takeIf { it.isNotBlank() }?.let { vm ->
                runCatching { store.saveViewMode(com.mrm.pgmanager.data.model.ViewMode.valueOf(vm)) }
                restored++
            }
            // الگوی نام
            data.optJSONObject("username_pattern")?.let { p ->
                store.saveUsernamePattern(com.mrm.pgmanager.data.model.UsernamePattern(
                    prefix = p.optString("prefix", "user"),
                    randomDigits = p.optInt("digits", 4).coerceIn(3, 6),
                    sequentialStart = p.optInt("start", 1).coerceIn(1, 999998),
                    sequential = p.optBoolean("sequential", false)
                ))
                restored++
            }
            // پایش
            data.optJSONObject("monitoring")?.let { m ->
                store.saveMonitoringSettings(com.mrm.pgmanager.data.model.MonitoringSettings(
                    autoRefreshEnabled = m.optBoolean("auto", true),
                    refreshIntervalSeconds = m.optInt("interval", 10).coerceIn(5, 3600),
                    refreshWhileAppOpen = m.optBoolean("always", false),
                    notificationsEnabled = m.optBoolean("notify_enabled", true),
                    notifyUserActions = m.optBoolean("notify_actions", true),
                    notifyLimited = m.optBoolean("notify_limited", true),
                    notifyExpired = m.optBoolean("notify_expired", true),
                    notifyNearLimit = m.optBoolean("notify_near_limit", true),
                    nearLimitPercent = m.optInt("limit_percent", 80),
                    notifyNearExpiry = m.optBoolean("notify_near_expiry", true),
                    nearExpiryDays = m.optInt("expiry_days", 1),
                    notifySystemHealth = m.optBoolean("notify_system", true),
                    cpuThreshold = m.optInt("cpu", 85),
                    ramThreshold = m.optInt("ram", 85),
                    diskThreshold = m.optInt("disk", 90),
                    notifyPanelOffline = m.optBoolean("notify_panel_offline", true),
                    notifyNodeOffline = m.optBoolean("notify_node_offline", true),
                    offlineCacheEnabled = m.optBoolean("offline_cache", true),
                    notifyCapacity = m.optBoolean("notify_capacity", false),
                    capacityOnlineLimit = m.optInt("capacity_online", 500),
                    debtorAutoDisableEnabled = m.optBoolean("debtor_auto_disable", false),
                    debtorAutoDisableAfterHours = m.optInt("debtor_auto_hours", 24).coerceIn(1, 720),
                    notifyDebtor = m.optBoolean("notify_debtor", true),
                    notifyDebtorOverdue = m.optBoolean("notify_debtor_overdue", true),
                    debtorCurrency = m.optString("debtor_currency", "تومان")
                ))
                restored++
            }
        }

        if (restoreAccounts) {
            val accArr = data.optJSONArray("accounts")
            if (accArr != null) {
                val accs = mutableListOf<com.mrm.pgmanager.data.model.Session>()
                for (i in 0 until accArr.length()) {
                    val o = accArr.optJSONObject(i) ?: continue
                    val base = o.optString("base"); val tok = o.optString("token")
                    if (base.isBlank() || tok.isBlank()) continue
                    accs.add(com.mrm.pgmanager.data.model.Session(base, tok, o.optString("username")))
                }
                if (accs.isNotEmpty()) {
                    store.saveAccounts(accs)
                    // فعال کردن حساب اول (در صورت نبود حساب فعال فعلی)
                    val activeBase = data.optString("active_base", "")
                    val activeUser = data.optString("active_username", "")
                    val toActivate = accs.firstOrNull { it.baseUrl == activeBase && it.username == activeUser } ?: accs.first()
                    store.setActive(toActivate)
                    restored += accs.size
                }
            }
        }

        if (restoreDebtors) {
            val dArr = data.optJSONArray("debtors")
            if (dArr != null) {
                val map = mutableMapOf<String, com.mrm.pgmanager.data.model.DebtorInfo>()
                for (i in 0 until dArr.length()) {
                    val o = dArr.optJSONObject(i) ?: continue
                    val username = o.optString("username"); val base = o.optString("baseUrl")
                    if (username.isBlank() || base.isBlank()) continue
                    val d = com.mrm.pgmanager.data.model.DebtorInfo(
                        username = username, baseUrl = base,
                        amount = o.optLong("amount", 0L),
                        currency = o.optString("currency", "تومان").ifBlank { "تومان" },
                        markedAt = o.optLong("markedAt", System.currentTimeMillis()),
                        notes = o.optString("notes", ""),
                        autoDisabled = o.optBoolean("autoDisabled", false),
                        userId = o.optLong("userId", 0L)
                    )
                    map[store.debtorKey(base, username)] = d
                }
                store.saveDebtors(map)
                restored += map.size
            }
        }

        if (restoreInvoice) {
            data.optJSONObject("invoice")?.let { inv ->
                store.saveInvoiceSeller(inv.optString("seller", ""))
                val b64 = inv.optString("logo_b64", "")
                if (b64.isNotBlank()) {
                    runCatching {
                        val logoBytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val logoFile = File(context.filesDir, "invoice_logo.png")
                        logoFile.writeBytes(logoBytes)
                        store.saveInvoiceLogoPath(logoFile.absolutePath)
                        restored++
                    }
                }
            }
        }

        val msg = "بازیابی کامل شد. $restored بخش بازنشانی شد."
        store.saveLastBackupAt(System.currentTimeMillis())
        store.saveLastBackupSuccess(true)
        store.saveLastBackupMessage(msg)
        return restored to msg
    }

    // ============ Retention: cleanup old backups in URI ============

    fun pruneBackups(context: Context, dirUri: Uri, keep: Int) {
        val contentResolver = context.contentResolver
        val childUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri,
            android.provider.DocumentsContract.getTreeDocumentId(dirUri)
        )
        data class Entry(val name: String, val docId: String)
        val files = contentResolver.query(childUri,
            arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { c ->
            val idIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val out = mutableListOf<Entry>()
            while (c.moveToNext()) {
                val name = c.getString(nameIdx) ?: continue
                val docId = c.getString(idIdx) ?: continue
                if (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_EXT)) out.add(Entry(name, docId))
            }
            out
        } ?: return

        if (files.size <= keep) return
        files.sortBy { it.name }
        val toRemove = files.take(files.size - keep)
        toRemove.forEach { (_, docId) ->
            val delUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
            runCatching { contentResolver.delete(delUri, null, null) }
        }
    }
}
