package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

@Composable
fun ThemeEditorDialog(
    themeState: ThemeState,
    isAppLockEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeState) -> Unit,
    onAppLockChange: (Boolean) -> Unit = {},
    monitoringSettings: com.mrm.pgmanager.data.model.MonitoringSettings = com.mrm.pgmanager.data.model.MonitoringSettings(),
    onMonitoringChange: (com.mrm.pgmanager.data.model.MonitoringSettings) -> Unit = {},
    appVersion: String = "",
    session: Session? = null,
    onLogout: (() -> Unit)? = null,
    appLockTimeout: Int = 0,
    onLockTimeoutChange: (Int) -> Unit = {},
    onSwitchAccount: (Session) -> Unit = {},
    onAddAccount: () -> Unit = {},
    appLanguage: String = "system",
    onLanguageChange: (String) -> Unit = {}
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context) }
    var section by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { section = context.getString(com.mrm.pgmanager.R.string.appearance) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var bulkCreateOpen by remember { mutableStateOf(false) }
    // خروجی کاربران: لیست در انتظار انتخاب محل ذخیره (برای CSV/JSON).
    var exportBusy by remember { mutableStateOf(false) }
    var exportList by remember { mutableStateOf<List<Pair<String, List<PanelUser>>>?>(null) }
    fun exportTimestamp() = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    fun toast(msg: String) { android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() }
    fun writeExport(uri: android.net.Uri?, payload: Pair<String, List<PanelUser>>?) {
        if (uri == null || payload == null) return
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val out = context.contentResolver.openOutputStream(uri) ?: error("no stream")
                out.use {
                    it.write(if (payload.first == "json") usersToJson(payload.second).toByteArray(Charsets.UTF_8) else usersToCsv(payload.second).toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            withContext(Dispatchers.Main) { toast(if (ok) "فایل با موفقیت ذخیره شد" else "خطا در ذخیرهٔ فایل") }
        }
    }
    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val payload = exportList?.firstOrNull { it.first == "csv" }; exportList = null; writeExport(uri, payload)
    }
    val jsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportList?.firstOrNull { it.first == "json" }; exportList = null; writeExport(uri, payload)
    }
    fun startExport(format: String, launcher: (String) -> Unit) {
        if (session == null || exportBusy) return
        scope.launch {
            exportBusy = true
            val list = runCatching { PanelApi.users(session) }.getOrNull()
            exportBusy = false
            if (list == null) { toast("دریافت فهرست کاربران ناموفق بود"); return@launch }
            exportList = listOf(format to list)
            launcher("mrm-users-${exportTimestamp()}.$format")
        }
    }
    // در صفحهٔ ورود (بدون نشست) فقط تنظیمات ظاهری معنا دارد؛ بقیهٔ تب‌ها پنهان می‌مانند.
    val sections = if (session == null) listOf(context.getString(R.string.appearance), context.getString(R.string.backup_title)) else listOf(context.getString(R.string.appearance), context.getString(R.string.monitoring_title), context.getString(R.string.notifications_title), context.getString(R.string.connection_title), context.getString(R.string.users_title), context.getString(R.string.invoice_title), context.getString(R.string.backup_title), context.getString(R.string.security_title))

    // === انتخاب لوگو برای فاکتور ===
    var invoiceLogoPath by remember { mutableStateOf(store.readInvoiceLogoPath()) }
    var invoiceLogoBitmap by remember(invoiceLogoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(invoiceLogoPath) {
        invoiceLogoBitmap = if (!invoiceLogoPath.isNullOrBlank()) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(invoiceLogoPath, opts)
            }.getOrNull()
        } else null
    }
    var invoiceSeller by remember { mutableStateOf(store.readInvoiceSeller()) }
    val invoiceLogoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val logoFile = File(context.filesDir, "invoice_logo.png")
                runCatching {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        // Scale down logo to save space
                        val original = BitmapFactory.decodeStream(input)
                        if (original != null) {
                            val size = 400
                            val scaled = Bitmap.createScaledBitmap(original, size, size, true)
                            FileOutputStream(logoFile).use { out ->
                                scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                            }
                            if (original !== scaled) original.recycle()
                            scaled.recycle()
                        }
                    }
                }.onSuccess {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        store.saveInvoiceLogoPath(logoFile.absolutePath)
                        invoiceLogoPath = logoFile.absolutePath
                        android.widget.Toast.makeText(context, "لوگو با موفقیت ذخیره شد", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "خطا در ذخیره لوگو: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // === پشتیبان‌گیری ===
    var backupEnabled by remember { mutableStateOf(store.readBackupEnabled()) }
    var backupInterval by remember { mutableStateOf(store.readBackupIntervalHours()) }
    var backupKeep by remember { mutableStateOf(store.readBackupKeepCount()) }
    var backupPassword by remember { mutableStateOf(store.readBackupPassword()) }
    var backupFolderUri by remember { mutableStateOf(store.readBackupUri()) }
    var backupFolderName by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupLastMsg by remember { mutableStateOf(store.readLastBackupMessage()) }
    var restoreDialogOpen by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<com.mrm.pgmanager.utils.BackupManager.BackupInfo?>(null) }
    var restorePasswordInput by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreResult by remember { mutableStateOf<String?>(null) }

    // Resolve backup folder name
    LaunchedEffect(backupFolderUri) {
        backupFolderName = if (backupFolderUri != null) {
            val uri = Uri.parse(backupFolderUri)
            runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        } else null
    }

    val pickBackupDir = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            store.saveBackupUri(it.toString())
            backupFolderUri = it.toString()
        }
    }

    val pickRestoreFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val ins = context.contentResolver.openInputStream(it) ?: throw Exception("باز کردن فایل ممکن نشد")
                ins.use { s -> com.mrm.pgmanager.utils.BackupManager.inspect(s) }
            }.onSuccess { info ->
                restorePreview = info
                restoreUri = it
                restorePasswordInput = ""
                restorePassword = info.encrypted
                restoreResult = null
                restoreDialogOpen = true
            }.onFailure { _ ->
                // Could be encrypted - ask password
                restorePreview = null
                restoreUri = it
                restorePasswordInput = ""
                restorePassword = true
                restoreResult = null
                restoreDialogOpen = true
            }
        }
    }

    fun performBackup(manual: Boolean) {
        if (backupBusy) return
        val targetUri = backupFolderUri
        if (targetUri.isNullOrBlank()) {
            android.widget.Toast.makeText(context, "ابتدا پوشهٔ ذخیره‌سازی را انتخاب کنید", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        backupBusy = true
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dirUri = Uri.parse(targetUri)
                val cr = context.contentResolver
                val name = com.mrm.pgmanager.utils.BackupManager.generateFileName()
                val docUri = android.provider.DocumentsContract.createDocument(
                    cr,
                    android.provider.DocumentsContract.buildDocumentUriUsingTree(dirUri, android.provider.DocumentsContract.getTreeDocumentId(dirUri)),
                    "application/octet-stream",
                    name
                ) ?: throw Exception("ایجاد فایل ممکن نشد")
                cr.openOutputStream(docUri)?.use { os ->
                    com.mrm.pgmanager.utils.BackupManager.createBackup(context, os, backupPassword, appVersion)
                }
                com.mrm.pgmanager.utils.BackupManager.pruneBackups(context, dirUri, backupKeep)
                store.saveBackupEnabled(backupEnabled)
                store.saveBackupIntervalHours(backupInterval)
                store.saveBackupKeepCount(backupKeep)
                store.saveBackupPassword(backupPassword)
                com.mrm.pgmanager.work.BackupWorker.schedule(context, if (backupEnabled) backupInterval else 0)
            }.onSuccess {
                backupLastMsg = if (manual) "پشتیبان‌گیری دستی موفق ✅" else "پشتیبان‌گیری خودکار انجام شد"
                store.saveLastBackupMessage(backupLastMsg)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "پشتیبان‌گیری با موفقیت ذخیره شد", android.widget.Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                backupLastMsg = "خطا: ${e.message}"
                store.saveLastBackupSuccess(false)
                store.saveLastBackupMessage(backupLastMsg)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "خطا در پشتیبان‌گیری: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            withContext(Dispatchers.Main) { backupBusy = false }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = themeState) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 720.dp).clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl)
                    .padding(16.dp)
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    // هدر: آیکون اکسنت + عنوان + دکمهٔ بستن
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(DsRadius.Xl)
                                .background(theme.accentPrimary.copy(.16f))
                            .border(BorderStroke(1.dp, theme.accentPrimary.copy(.32f)), DsRadius.Xl),
                        contentAlignment = Alignment.Center
                    ) { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_title), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text(stringResource(R.string.appearance_desc), fontSize = 10.sp, color = theme.mutedColor)
                    }
                }
                // تب بخش‌ها به‌صورت سگمنت یکدست با قابلیت اسکرول افقی (اگر فقط یک بخش باشد، مخفی می‌ماند)
                if (sections.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().clip(DsRadius.Xl)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sections.forEach { label ->
                            val selected = section == label
                            Box(
                                Modifier.height(34.dp).clip(DsRadius.Md)
                                    .background(if (selected) theme.accentPrimary.copy(.78f) else Color.Transparent)
                                    .clickable { section = label }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Color(0xFF202124) else theme.mutedColor, maxLines = 1) }
                        }
                    }
                }
                // محتوای بخش‌ها (اسکرول فقط همین ناحیه؛ فوتر همیشه دیده می‌شود)
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                    when (section) {
                        stringResource(R.string.appearance) -> {
                            SettingsCard(stringResource(R.string.language), AppIcon.Settings) {
                                SegmentedControl(
                                    options = listOf(stringResource(R.string.language_system), stringResource(R.string.language_fa), stringResource(R.string.language_en)),
                                    selectedIndex = when (appLanguage) { "fa" -> 1; "en" -> 2; else -> 0 },
                                    onSelect = { idx -> val lang = when (idx) { 1 -> "fa"; 2 -> "en"; else -> "system" }; onLanguageChange(lang) }
                                )
                                Text(stringResource(R.string.language_desc), fontSize = 11.sp, color = theme.mutedColor)
                            }
                            SettingsCard(if (isFa) "حالت نمایش" else "Display Mode", AppIcon.Palette) {
                                SegmentedControl(
                                    options = listOf(if (isFa) "روشن" else "Light", if (isFa) "تیره" else "Dark", if (isFa) "خودکار" else "Auto"),
                                    selectedIndex = if (themeState.followSystem) 2 else if (themeState.isDark) 1 else 0,
                                    onSelect = { index ->
                                        when (index) {
                                            0 -> onThemeChange(themeState.copy(followSystem = false, isDark = false))
                                            1 -> onThemeChange(themeState.copy(followSystem = false, isDark = true))
                                            else -> onThemeChange(themeState.copy(followSystem = true))
                                        }
                                    },
                                    icons = listOf(AppIcon.LightMode, AppIcon.DarkMode, AppIcon.AutoMode)
                                )
                                Text(if (isFa) "در حالت «خودکار» برنامه از حالت روشن/تیرهٔ سیستم پیروی می‌کند." else "In 'Auto' mode, the app follows the system light/dark theme.", fontSize = 11.sp, color = theme.mutedColor)
                            }
                            SettingsCard(if (isFa) "رنگ اصلی برنامه" else "Primary App Color", AppIcon.Palette) {
                                LampColor.values().toList().chunked(2).forEach { rowItems ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowItems.forEach { lamp ->
                                            val selected = themeState.customColor == null && themeState.lamp == lamp
                                            LampColorItem(lamp = lamp, selected = selected, modifier = Modifier.weight(1f)) { onThemeChange(themeState.copy(lamp = lamp, customColor = null)) }
                                        }
                                        if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            // انتخاب رنگ کاملاً دلخواه با اسلایدرهای HSV؛ پیش‌نمایش زنده دارد.
                            SettingsCard(if (isFa) "رنگ سفارشی" else "Custom Color", AppIcon.Palette, accent = themeState.customColor ?: theme.accentPrimary) {
                                val activeCustom = themeState.customColor
                                val seed = remember(activeCustom) {
                                    val out = FloatArray(3)
                                    if (activeCustom != null) android.graphics.Color.colorToHSV(activeCustom.toArgb(), out) else {
                                        out[0] = 42f; out[1] = 0.85f; out[2] = 0.96f
                                    }
                                    out
                                }
                                var hue by remember(activeCustom) { mutableStateOf(seed[0]) }
                                var sat by remember(activeCustom) { mutableStateOf(seed[1].coerceIn(0.25f, 1f)) }
                                var valueCmp by remember(activeCustom) { mutableStateOf(seed[2].coerceIn(0.45f, 1f)) }
                                val preview = Color.hsv(hue, sat, valueCmp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(38.dp).clip(DsRadius.Lg).background(Brush.linearGradient(listOf(preview, preview.copy(alpha = .6f)))).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg), contentAlignment = Alignment.Center) {
                                        if (activeCustom != null) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 15.dp)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(if (activeCustom != null) (if (isFa) "رنگ سفارشی فعال است" else "Custom color is active") else (if (isFa) "با اسلایدرها رنگ دلخواهت را بساز" else "Create your preferred color with sliders"), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                        Text(if (isFa) "تغییرات با رهاشدن اسلایدر اعمال می‌شود" else "Changes are applied when the slider is released", fontSize = 10.sp, color = theme.mutedColor)
                                    }
                                }
                                @Composable fun colorSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, label: String, labelFaWidth: androidx.compose.ui.unit.Dp = 46.dp, onDone: () -> Unit = {}) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(label, fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.width(labelFaWidth))
                                        Slider(
                                            value = value, onValueChange = onChange, valueRange = range,
                                            onValueChangeFinished = onDone,
                                            colors = SliderDefaults.colors(thumbColor = preview, activeTrackColor = preview, inactiveTrackColor = theme.searchBgColor),
                                            modifier = Modifier.weight(1f).height(22.dp)
                                        )
                                    }
                                }
                                colorSlider(hue, { hue = it }, 0f..360f, if (isFa) "رنگ‌مایه" else "Hue") { onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, valueCmp))) }
                                colorSlider(sat, { sat = it }, 0.25f..1f, if (isFa) "غلظت" else "Saturation") { onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, valueCmp))) }
                                colorSlider(valueCmp, { valueCmp = it }, 0.45f..1f, if (isFa) "روشنایی" else "Value") { onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, valueCmp))) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.weight(1f).height(30.dp).clip(DsRadius.Sm).background(preview.copy(.18f)).border(BorderStroke(1.dp, preview.copy(.4f)), DsRadius.Sm).clickable { onThemeChange(themeState.copy(customColor = preview)) }, contentAlignment = Alignment.Center) { Text(if (isFa) "اعمال این رنگ" else "Apply this color", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                                    if (activeCustom != null) Box(Modifier.weight(1f).height(30.dp).clip(DsRadius.Sm).background(GlassRed.copy(.10f)).border(BorderStroke(1.dp, GlassRed.copy(.3f)), DsRadius.Sm).clickable { onThemeChange(themeState.copy(customColor = null)) }, contentAlignment = Alignment.Center) { Text(if (isFa) "حذف رنگ سفارشی" else "Remove custom color", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassRed) }
                                }
                            }
                            // پیش‌نمایش زنده با تم فعلی
                            SettingsCard(if (isFa) "پیش‌نمایش تم" else "Theme Preview", AppIcon.Palette, accent = themeState.accentPrimary) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f).clip(com.mrm.pgmanager.ui.designsystem.DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(com.mrm.pgmanager.ui.designsystem.DsBorder.Hairline, theme.borderColor), com.mrm.pgmanager.ui.designsystem.DsRadius.Lg).padding(10.dp)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(22.dp).clip(com.mrm.pgmanager.ui.designsystem.DsRadius.Sm).background(if (theme.isDark) theme.accentPrimary.copy(0.18f) else theme.accentPrimary.copy(0.12f)).border(BorderStroke(com.mrm.pgmanager.ui.designsystem.DsBorder.Hairline, theme.accentPrimary.copy(0.25f)), com.mrm.pgmanager.ui.designsystem.DsRadius.Sm), contentAlignment = Alignment.Center) { com.mrm.pgmanager.ui.components.RoundedAppIcon(com.mrm.pgmanager.ui.components.AppIcon.Gauge, tint = theme.accentPrimary, size = 12.dp) }
                                                androidx.compose.material3.Text(if (isFa) "نمونه کارت" else "Sample Card", fontSize = 11.sp, color = theme.inkColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                            }
                                            androidx.compose.material3.Text(if (isFa) "پیش‌نمایش زندهٔ رنگ و حالت تیره/روشن" else "Live color & dark/light preview", fontSize = 10.sp, color = theme.mutedColor)
                                        }
                                    }
                                    Box(Modifier.weight(1f).clip(com.mrm.pgmanager.ui.designsystem.DsRadius.Lg).background(theme.searchBgColor).border(BorderStroke(com.mrm.pgmanager.ui.designsystem.DsBorder.Hairline, theme.borderColor), com.mrm.pgmanager.ui.designsystem.DsRadius.Lg).padding(10.dp), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Text(if (isFa) "جست‌وجو" else "Search", fontSize = 11.sp, color = theme.mutedColor)
                                    }
                                }
                                androidx.compose.material3.Text(if (isFa) "تغییر رنگ بالا بلافاصله روی این کارت‌ها اعمال می‌شود" else "Changing the color above instantly applies to these cards", fontSize = 9.sp, color = theme.mutedColor)
                            }
                            if (themeState.isDark) {
                                SettingsCard(if (isFa) "تیرهٔ خالص (AMOLED)" else "Pure Black (AMOLED)", AppIcon.DarkMode) {
                                    SettingsSwitchRow(
                                        if (isFa) "پس‌زمینهٔ مشکی مطلق" else "Pure Black Background",
                                        if (isFa) "در حالت تیره، پس‌زمینه کاملاً سیاه می‌شود؛ صرفه‌جویی باتری در نمایشگرهای AMOLED" else "In dark mode, the background becomes completely black; saves battery on AMOLED screens",
                                        themeState.amoledDark
                                    ) { onThemeChange(themeState.copy(amoledDark = it)) }
                                }
                            } else {
                                // در حالت روشن، سوییچ AMOLED بی‌معناست — نمایش غیرفعال با توضیح
                                Box(Modifier.fillMaxWidth().clip(com.mrm.pgmanager.ui.designsystem.DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(com.mrm.pgmanager.ui.designsystem.DsBorder.Hairline, theme.borderColor), com.mrm.pgmanager.ui.designsystem.DsRadius.Md).padding(10.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { com.mrm.pgmanager.ui.components.RoundedAppIcon(com.mrm.pgmanager.ui.components.AppIcon.DarkMode, tint = theme.mutedColor, size = 14.dp); androidx.compose.material3.Text(if (isFa) "تیرهٔ خالص (AMOLED)" else "Pure Black (AMOLED)", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = theme.mutedColor) }
                                        androidx.compose.material3.Text(if (isFa) "فقط در حالت تیره فعال است — ابتدا «تیره» را انتخاب کن" else "Only active in dark mode — choose 'Dark' first", fontSize = 10.sp, color = theme.mutedColor)
                                    }
                                }
                            }
                        }
                        stringResource(R.string.monitoring_title) -> {
                            SettingsCard("پایش خودکار", AppIcon.Tune) {
                                SettingsSwitchRow(
                                    "بروزرسانی خودکار داشبورد",
                                    "دریافت مجدد آمار سیستم و کاربران به‌صورت دوره‌ای",
                                    monitoringSettings.autoRefreshEnabled
                                ) { onMonitoringChange(monitoringSettings.copy(autoRefreshEnabled = it)) }
                                SettingsStepper("فاصلهٔ پایش", monitoringSettings.refreshIntervalSeconds, "ثانیه", 5..3600, step = 5, enabled = monitoringSettings.autoRefreshEnabled) {
                                    onMonitoringChange(monitoringSettings.copy(refreshIntervalSeconds = it))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("محدودهٔ اجرای پایش", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                    SegmentedControl(
                                        options = listOf("فقط داشبورد", "کل برنامه"),
                                        selectedIndex = if (monitoringSettings.refreshWhileAppOpen) 1 else 0
                                    ) { index -> onMonitoringChange(monitoringSettings.copy(refreshWhileAppOpen = index == 1)) }
                                }
                                SettingsSwitchRow(
                                    "حالت آفلاین (کش)",
                                    "هنگام قطع اتصال به پنل، آخرین داده‌های دریافت‌شده با برچسب «آفلاین» نمایش داده می‌شود",
                                    monitoringSettings.offlineCacheEnabled
                                ) { onMonitoringChange(monitoringSettings.copy(offlineCacheEnabled = it)) }
                            }
                            SettingsCard("بازنشانی", AppIcon.Reset, accent = GlassAmber) {
                                SettingsActionRow(
                                    "بازنشانی به پیش‌فرض",
                                    "همهٔ تنظیمات پایش و اعلان‌ها به حالت اولیه برمی‌گردد",
                                    AppIcon.Reset,
                                    GlassAmber
                                ) { onMonitoringChange(com.mrm.pgmanager.data.model.MonitoringSettings()) }
                            }
                        }
                        stringResource(R.string.notifications_title) -> {
                            val master = monitoringSettings.notificationsEnabled
                            SettingsCard("کلی", AppIcon.Bell) {
                                SettingsSwitchRow("فعال‌سازی اعلان‌ها", "کلید اصلیٔ همهٔ هشدارهای برنامه", master) { onMonitoringChange(monitoringSettings.copy(notificationsEnabled = it)) }
                                SettingsSwitchRow("اعلان عملیات کاربران", "ساخت، ویرایش، ریست و حذف کاربر", monitoringSettings.notifyUserActions, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyUserActions = it)) }
                            }
                            SettingsCard("هشدارهای اشتراک", AppIcon.Users) {
                                SettingsSwitchRow("کاربر Limited شد", checked = monitoringSettings.notifyLimited, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyLimited = it)) }
                                SettingsSwitchRow("اشتراک Expired شد", checked = monitoringSettings.notifyExpired, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyExpired = it)) }
                                SettingsSwitchRow("نزدیک به سقف حجم", checked = monitoringSettings.notifyNearLimit, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyNearLimit = it)) }
                                SettingsStepper("آستانهٔ نزدیک به سقف", monitoringSettings.nearLimitPercent, "٪", 10..100, step = 5, enabled = master && monitoringSettings.notifyNearLimit) { onMonitoringChange(monitoringSettings.copy(nearLimitPercent = it)) }
                                SettingsSwitchRow("نزدیک به انقضا", checked = monitoringSettings.notifyNearExpiry, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyNearExpiry = it)) }
                                SettingsStepper("هشدار انقضا از", monitoringSettings.nearExpiryDays, "روز قبل", 1..30, enabled = master && monitoringSettings.notifyNearExpiry) { onMonitoringChange(monitoringSettings.copy(nearExpiryDays = it)) }
                            }
                            SettingsCard("بدهکاران", AppIcon.Warning, accent = GlassRed) {
                                SettingsSwitchRow("اعلان ثبت بدهکار", checked = monitoringSettings.notifyDebtor, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyDebtor = it)) }
                                SettingsSwitchRow("اعلان قطع خودکار بدهکار", checked = monitoringSettings.notifyDebtorOverdue, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyDebtorOverdue = it)) }
                                Text("وقتی کاربری به عنوان بدهکار ثبت می‌شود یا پس از مهلت به صورت خودکار قطع می‌شود، اعلان دریافت می‌کنی.", fontSize = 10.sp, color = theme.mutedColor)
                            }
                            SettingsCard("سلامت سیستم و اتصال", AppIcon.Warning, accent = GlassRed) {
                                val healthEnabled = master && monitoringSettings.notifySystemHealth
                                SettingsSwitchRow("هشدار سلامت سیستم", "مصرف غیرعادی CPU، RAM یا Disk پنل", monitoringSettings.notifySystemHealth, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifySystemHealth = it)) }
                                SettingsStepper("آستانهٔ CPU", monitoringSettings.cpuThreshold, "٪", 50..100, step = 5, enabled = healthEnabled) { onMonitoringChange(monitoringSettings.copy(cpuThreshold = it)) }
                                SettingsStepper("آستانهٔ RAM", monitoringSettings.ramThreshold, "٪", 50..100, step = 5, enabled = healthEnabled) { onMonitoringChange(monitoringSettings.copy(ramThreshold = it)) }
                                SettingsStepper("آستانهٔ Disk", monitoringSettings.diskThreshold, "٪", 50..100, step = 5, enabled = healthEnabled) { onMonitoringChange(monitoringSettings.copy(diskThreshold = it)) }
                                SettingsSwitchRow("قطع اتصال پنل", checked = monitoringSettings.notifyPanelOffline, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyPanelOffline = it)) }
                                SettingsSwitchRow("قطع اتصال نود", checked = monitoringSettings.notifyNodeOffline, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyNodeOffline = it)) }
                                SettingsSwitchRow("هشدار ظرفیت آنلاین", "وقتی کاربران آنلاین هم‌زمان از حد مجاز عبور کنند", checked = monitoringSettings.notifyCapacity, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyCapacity = it)) }
                                SettingsStepper("حداکثر آنلاین مجاز", monitoringSettings.capacityOnlineLimit, "کاربر", 10..10000, step = 10, enabled = master && monitoringSettings.notifyCapacity) { onMonitoringChange(monitoringSettings.copy(capacityOnlineLimit = it)) }
                            }
                        }
                        stringResource(R.string.connection_title) -> {
                            if (session == null) {
                                SettingsCard("اتصال به پنل", AppIcon.Wifi) {
                                    Text("برای مشاهدهٔ اطلاعات اتصال و تست آن، ابتدا وارد حساب کاربری شوید.", fontSize = 10.5.sp, color = theme.mutedColor)
                                }
                            } else {
                                SettingsCard("سرور فعلی", AppIcon.Wifi) {
                                    SettingsInfoRow("آدرس پنل", session.baseUrl, copyable = true)
                                    SettingsInfoRow("کاربر مدیر", session.username)
                                    SettingsActionRow(
                                        "باز کردن پنل در مرورگر",
                                        "رفتن مستقیم به داشبورد وب پنل PasarGuard",
                                        AppIcon.OpenNew,
                                        theme.accentPrimary
                                    ) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.baseUrl.trimEnd('/') + "/dashboard/"))) } }
                                }
                                // حساب‌های ذخیره‌شده: سوئیچ سریع بین چند پنل بدون خروج از حساب فعلی.
                                SettingsCard("حساب‌های پنل (چند پنل)", AppIcon.Users) {
                                    var accounts by remember { mutableStateOf(store.readAccounts()) }
                                    if (accounts.isEmpty()) {
                                        Text("هنوز حسابی ذخیره نشده است.", fontSize = 10.sp, color = theme.mutedColor)
                                    } else accounts.forEach { acc ->
                                        val isActive = acc.baseUrl == session.baseUrl && acc.username == session.username
                                        Row(
                                            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                                .background(if (isActive) theme.accentPrimary.copy(.10f) else theme.searchBgColor)
                                                .border(BorderStroke(1.dp, if (isActive) theme.accentPrimary.copy(.35f) else theme.borderColor), DsRadius.Lg)
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                MrmText(acc.username, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                                                MrmText(acc.baseUrl, fontSize = 8.5.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                                            }
                                            if (isActive) {
                                                Box(Modifier.clip(DsRadius.Sm).background(theme.accentPrimary.copy(.20f)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("فعال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                                            } else {
                                                Box(Modifier.clip(DsRadius.Sm).background(GlassGreen.copy(.16f)).clickable {
                                                    store.setActive(acc); accounts = store.readAccounts(); onSwitchAccount(acc)
                                                }.padding(horizontal = 8.dp, vertical = 4.dp)) { Text("اتصال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassGreen) }
                                                Box(Modifier.size(24.dp).clip(DsRadius.Sm).background(GlassRed.copy(.12f)).clickable {
                                                    store.removeAccount(acc.baseUrl, acc.username); accounts = store.readAccounts()
                                                }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 12.dp) }
                                            }
                                        }
                                    }
                                    SettingsActionRow(
                                        "افزودن حساب جدید",
                                        "ورود به پنل دیگر بدون حذف حساب‌های ذخیره‌شده",
                                        AppIcon.UserAdd,
                                        theme.accentPrimary
                                    ) { onAddAccount() }
                                    Text("نکته: اگر پنل راه‌اندازی مجدد شود، نشست حساب‌ها منقضی می‌شود و هنگام اتصال باید دوباره وارد شوید.", fontSize = 8.5.sp, color = theme.mutedColor)
                                }
                                SettingsCard("تست اتصال", AppIcon.CheckCircle, accent = GlassGreen) {
                                    Text("برقراری ارتباط با پنل و دریافت آمار سیستم، برای اطمینان از سلامت دسترسی.", fontSize = 11.sp, color = theme.mutedColor)
                                    PrimarySaveButton(
                                        text = if (testing) "در حال بررسی اتصال..." else "تست اتصال به پنل",
                                        enabled = !testing,
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        onClick = {
                                            scope.launch {
                                                testing = true
                                                val result = runCatching { PanelApi.systemStats(session) }
                                                testResult = result.fold(
                                                    onSuccess = { s -> true to "اتصال برقرار است · آپ‌تایم ${s.uptimeSeconds / 86400L} روز و ${(s.uptimeSeconds % 86400L) / 3600L} ساعت" },
                                                    onFailure = { e -> false to ("خطا در اتصال: " + (e.message ?: "پنل در دسترس نیست")) }
                                                )
                                                testing = false
                                            }
                                        }
                                    )
                                    testResult?.let { (ok, message) ->
                                        val color = if (ok) GlassGreen else GlassRed
                                        Row(
                                            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                                .background(color.copy(.10f))
                                                .border(BorderStroke(1.dp, color.copy(.30f)), DsRadius.Lg)
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RoundedAppIcon(if (ok) AppIcon.CheckCircle else AppIcon.Warning, tint = color, size = 17.dp)
                                            Text(message, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color)
                                        }
                                    }
                                }
                            }
                        }
                        stringResource(R.string.users_title) -> {
                            var pattern by remember { mutableStateOf(store.readUsernamePattern()) }
                            fun savePattern(p: com.mrm.pgmanager.data.model.UsernamePattern) { pattern = p; store.saveUsernamePattern(p) }
                            SettingsCard("الگوی نام خودکار", AppIcon.User) {
                                Text("در ساخت کاربر جدید (تکی یا گروهی)، نام‌ها با این الگو تولید می‌شوند.", fontSize = 11.sp, color = theme.mutedColor)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("حالت تولید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                    SegmentedControl(options = listOf("تصادفی", "ترتیبی"), selectedIndex = if (pattern.sequential) 1 else 0) { savePattern(pattern.copy(sequential = it == 1)) }
                                }
                                CompactGlassField(
                                    pattern.prefix,
                                    { v -> savePattern(pattern.copy(prefix = v.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }.take(24))) },
                                    "پیشوند نام (مثل shop)",
                                    leadingAppIcon = AppIcon.Edit, keyboardType = KeyboardType.Ascii, fieldHeight = 38.dp
                                )
                                if (pattern.sequential) SettingsStepper("شروع شمارش از", pattern.sequentialStart, "عدد", 1..999000) { savePattern(pattern.copy(sequentialStart = it)) }
                                else SettingsStepper("تعداد ارقام", pattern.randomDigits, "رقم", 3..6) { savePattern(pattern.copy(randomDigits = it)) }
                                Text("نمونه: ${if (pattern.sequential) pattern.sequentialName(0) else pattern.randomName()}", fontSize = 11.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold)
                            }
                            SettingsCard("بدهکاران - قطع خودکار", AppIcon.Warning, accent = GlassRed) {
                                Text("وقتی کاربری به عنوان بدهکار ثبت شد، پس از مدت تعیین‌شده به صورت خودکار غیرفعال می‌شود. با تسویه بدهی، دوباره فعال می‌گردد.", fontSize = 11.sp, color = theme.mutedColor)
                                SettingsSwitchRow(
                                    "قطع خودکار بدهکار",
                                    "فعال‌سازی قطع خودکار پس از بدهکار شدن",
                                    monitoringSettings.debtorAutoDisableEnabled
                                ) { onMonitoringChange(monitoringSettings.copy(debtorAutoDisableEnabled = it)) }
                                SettingsStepper(
                                    "مهلت قطع پس از بدهکاری",
                                    monitoringSettings.debtorAutoDisableAfterHours,
                                    "ساعت",
                                    1..720,
                                    step = 1,
                                    enabled = monitoringSettings.debtorAutoDisableEnabled
                                ) { onMonitoringChange(monitoringSettings.copy(debtorAutoDisableAfterHours = it)) }
                                if (monitoringSettings.debtorAutoDisableEnabled) {
                                    Text("مثال: اگر 24 ساعت تنظیم کنی، کاربر بدهکار بعد 24 ساعت قطع می‌شود. با تسویه از دیالوگ بدهکار، خودکار وصل می‌شود.", fontSize = 8.5.sp, color = theme.mutedColor)
                                }
                                // نمایش تعداد بدهکاران فعلی این پنل
                                run {
                                    val debtorCount = store.readDebtors().values.count { it.baseUrl == session?.baseUrl }
                                    if (debtorCount > 0) {
                                        Text("در حال حاضر $debtorCount کاربر بدهکار در این پنل ثبت شده است.", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                                    }
                                }
                            }
                            SettingsCard("ساخت گروهی", AppIcon.Users, accent = GlassGreen) {
                                SettingsActionRow(
                                    "ساخت گروهی کاربر",
                                    "چند کاربر هم‌زمان با الگوی نام، از تمپلت یا با حجم/زمان دستی",
                                    AppIcon.Users,
                                    GlassGreen
                                ) { bulkCreateOpen = true }
                            }
                            SettingsCard("خروجی کاربران", AppIcon.Download, accent = theme.accentPrimary) {
                                Text("فهرست کامل کاربران پنل را به‌صورت فایل CSV یا JSON ذخیره و اشتراک‌گذاری کن.", fontSize = 11.sp, color = theme.mutedColor)
                                SettingsActionRow(if (exportBusy) "در حال آماده‌سازی..." else "خروجی CSV", "مناسب اکسل و گزارش‌گیری", AppIcon.Download, GlassGreen) { startExport("csv") { name -> csvLauncher.launch(name) } }
                                SettingsActionRow(if (exportBusy) "در حال آماده‌سازی..." else "خروجی JSON", "مناسب برنامه‌نویسی و بکاپ", AppIcon.Download, theme.accentPrimary) { startExport("json") { name -> jsonLauncher.launch(name) } }
                            }
                        }
                        stringResource(R.string.invoice_title) -> {
                            SettingsCard("لوگوی فاکتور", AppIcon.Image, accent = theme.accentPrimary) {
                                Text("تصویری که بالای فاکتورهای متنی و PDF نمایش داده می‌شود.", fontSize = 11.sp, color = theme.mutedColor)
                                // پیش‌نمایش لوگو
                                Box(
                                    Modifier.fillMaxWidth().clip(DsRadius.Xl)
                                        .background(if (theme.isDark) Color.White.copy(0.06f) else Color(0xFFF8F8FA))
                                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (invoiceLogoBitmap != null) {
                                        Image(
                                            bitmap = invoiceLogoBitmap!!.asImageBitmap(),
                                            contentDescription = "Logo Preview",
                                            modifier = Modifier.size(96.dp).clip(DsRadius.Xl),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            RoundedAppIcon(AppIcon.Image, tint = theme.mutedColor, size = 32.dp)
                                            Text("هنوز لوگویی انتخاب نشده است", fontSize = 10.sp, color = theme.mutedColor)
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        Modifier.weight(1f).height(42.dp).clip(DsRadius.Lg)
                                            .background(theme.accentPrimary.copy(0.78f))
                                            .clickable { invoiceLogoLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            RoundedAppIcon(AppIcon.Upload, tint = Color(0xFF1A1A1A), size = 16.dp)
                                            Text(if (invoiceLogoPath != null) "تغییر لوگو" else "انتخاب لوگو", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                        }
                                    }
                                    if (invoiceLogoPath != null) {
                                        Box(
                                            Modifier.height(42.dp).width(42.dp).clip(DsRadius.Lg)
                                                .background(GlassRed.copy(0.10f))
                                                .border(BorderStroke(1.dp, GlassRed.copy(0.30f)), DsRadius.Lg)
                                                .clickable {
                                                    store.clearInvoiceLogo()
                                                    invoiceLogoPath = null
                                                    android.widget.Toast.makeText(context, "لوگو حذف شد", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 18.dp)
                                        }
                                    }
                                }
                                Text("پیشنهاد: تصویر مربعی با پس‌زمینه شفاف (PNG) و حداکثر ۴۰۰x۴۰۰ پیکسل.", fontSize = 8.5.sp, color = theme.mutedColor)
                            }
                            SettingsCard("نام فروشنده/برند", AppIcon.Receipt) {
                                Text("این نام زیر لوگو و بالای فاکتور نمایش داده می‌شود.", fontSize = 11.sp, color = theme.mutedColor)
                                CompactGlassField(
                                    value = invoiceSeller,
                                    onValueChange = { v ->
                                        invoiceSeller = v.take(40)
                                        store.saveInvoiceSeller(v)
                                    },
                                    placeholder = "مثلاً فروشگاه VPN من",
                                    leadingAppIcon = AppIcon.Receipt,
                                    keyboardType = KeyboardType.Text
                                )
                            }
                        }
                        stringResource(R.string.backup_title) -> {
                            SettingsCard("پوشه ذخیره‌سازی", AppIcon.Folder) {
                                Text("پشتیبان‌ها در پوشه‌ای که انتخاب می‌کنید روی حافظهٔ گوشی ذخیره می‌شوند.", fontSize = 11.sp, color = theme.mutedColor)
                                Box(
                                    Modifier.fillMaxWidth().height(44.dp).clip(DsRadius.Lg)
                                        .background(theme.searchBgColor)
                                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                        .clickable { pickBackupDir.launch(null) }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RoundedAppIcon(AppIcon.Folder, tint = theme.accentPrimary, size = 17.dp)
                                        Text(
                                            backupFolderName ?: "انتخاب پوشه...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (backupFolderName != null) theme.inkColor else theme.mutedColor,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (backupFolderUri != null) {
                                    Box(
                                        Modifier.fillMaxWidth().height(32.dp).clip(DsRadius.Sm)
                                            .background(GlassRed.copy(0.08f))
                                            .border(BorderStroke(0.8.dp, GlassRed.copy(0.20f)), DsRadius.Sm)
                                            .clickable {
                                                store.saveBackupUri(null)
                                                backupFolderUri = null
                                            }.padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لغو انتخاب پوشه", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                                    }
                                }
                            }

                            SettingsCard("پشتیبان‌گیری خودکار", AppIcon.Backup, accent = theme.accentPrimary) {
                                Text("در بازه‌های زمانی تعیین‌شده از همهٔ تنظیمات و حساب‌ها یک کپی امن در پوشهٔ انتخابی گرفته می‌شود.", fontSize = 11.sp, color = theme.mutedColor)
                                SettingsSwitchRow("فعال‌سازی پشتیبان خودکار", "", backupEnabled) { v ->
                                    backupEnabled = v
                                    store.saveBackupEnabled(v)
                                    com.mrm.pgmanager.work.BackupWorker.schedule(context, if (v) backupInterval else 0)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("بازهٔ زمانی", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (backupEnabled) theme.inkColor else theme.mutedColor)
                                    val options = listOf("۶ ساعت", "۱۲ ساعت", "روزانه", "۳ روز", "هفتگی")
                                    val values = listOf(6, 12, 24, 72, 168)
                                    val idx = values.indexOf(backupInterval).coerceAtLeast(0)
                                    SegmentedControl(
                                        options = options,
                                        selectedIndex = if (backupEnabled) idx else 0,
                                        enabled = backupEnabled
                                    ) { i ->
                                        backupInterval = values[i]
                                        store.saveBackupIntervalHours(backupInterval)
                                        com.mrm.pgmanager.work.BackupWorker.schedule(context, backupInterval)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("تعداد نسخه‌های نگهداری", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                    val keepOptions = listOf("۳ نسخه", "۵ نسخه", "۷ نسخه", "۱۴ نسخه", "۳۰ نسخه")
                                    val keepValues = listOf(3, 5, 7, 14, 30)
                                    SegmentedControl(
                                        options = keepOptions,
                                        selectedIndex = keepValues.indexOf(backupKeep).coerceAtLeast(2)
                                    ) { i ->
                                        backupKeep = keepValues[i]
                                        store.saveBackupKeepCount(backupKeep)
                                    }
                                    Text("نسخه‌های قدیمی‌تر به‌طور خودکار حذف می‌شوند.", fontSize = 8.5.sp, color = theme.mutedColor)
                                }
                            }

                            SettingsCard("رمزگذاری", AppIcon.Lock, accent = GlassGreen) {
                                Text("با تعیین رمز، فایل بکاپ با AES-256 رمزنگاری می‌شود و بدون رمز روی دستگاه دیگری قابل بازیابی نیست. برای بکاپ ساده و بدون رمز این فیلد را خالی بگذارید.", fontSize = 11.sp, color = theme.mutedColor)
                                CompactGlassField(
                                    value = backupPassword,
                                    onValueChange = { v -> backupPassword = v.take(64); store.saveBackupPassword(v) },
                                    placeholder = "رمز بکاپ (اختیاری)",
                                    leadingAppIcon = AppIcon.Lock,
                                    keyboardType = KeyboardType.Password
                                )
                                // بکاپ بدون رمز شامل توکن‌های ورود به پنل‌هاست؛ هشدار صریح بده.
                                if (backupPassword.isBlank()) {
                                    Text("⚠️ بکاپ بدون رمز شامل توکن‌های ورود همهٔ پنل‌های ذخیره‌شده است. اگر فایل را برای کسی بفرستید، او به پنل‌های شما دسترسی کامل پیدا می‌کند. برای بکاپ امن حتماً رمز بگذارید.", fontSize = 8.5.sp, color = GlassRed, fontWeight = FontWeight.Bold)
                                }
                            }

                            SettingsCard("عملیات", AppIcon.Settings) {
                                if (backupLastMsg.isNotBlank()) {
                                    Text(backupLastMsg, fontSize = 10.sp, color = theme.mutedColor)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        Modifier.weight(1f).height(46.dp).clip(DsRadius.Lg)
                                            .background(if (backupBusy) theme.accentPrimary.copy(0.5f) else theme.accentPrimary.copy(0.78f))
                                            .clickable(enabled = !backupBusy) { performBackup(manual = true) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (backupBusy) {
                                            CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF1A1A1A), strokeWidth = 2.dp)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                RoundedAppIcon(AppIcon.Backup, tint = Color(0xFF1A1A1A), size = 16.dp)
                                                Text("پشتیبان‌گیری دستی", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                                            }
                                        }
                                    }
                                    Box(
                                        Modifier.weight(1f).height(46.dp).clip(DsRadius.Lg)
                                            .background(theme.searchBgColor)
                                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                            .clickable { pickRestoreFile.launch(arrayOf("*/*")) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            RoundedAppIcon(AppIcon.Restore, tint = theme.inkColor, size = 16.dp)
                                            Text("بازیابی از فایل", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                                        }
                                    }
                                }
                                val lastAt = store.readLastBackupAt()
                                if (lastAt > 0L) {
                                    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                                    Text("آخرین پشتیبان: ${sdf.format(java.util.Date(lastAt))}", fontSize = 8.5.sp, color = theme.mutedColor)
                                }
                            }
                        }
                        stringResource(R.string.security_title) -> {
                            SettingsCard("قفل برنامه", AppIcon.Lock, accent = GlassGreen) {
                                SettingsSwitchRow(
                                    "قفل امنیتی برنامه",
                                    "ورود با اثر انگشت یا پین/الگوی گوشی هنگام بازکردن اپ",
                                    isAppLockEnabled
                                ) { onAppLockChange(it) }
                                Text(
                                    if (isAppLockEnabled) "قفل فعال است؛ هنگام هر بار ورود، هویت شما تأیید می‌شود."
                                    else "با فعال‌سازی، هر بار ورود به برنامه نیازمند تأیید هویت خواهد بود.",
                                    fontSize = 11.sp, color = theme.mutedColor
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("مهلت قفل خودکار پس از خروج", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isAppLockEnabled) theme.inkColor else theme.mutedColor)
                                    SegmentedControl(
                                        options = listOf("فوری", "۱ دقیقه", "۵ دقیقه", "۱۵ دقیقه"),
                                        selectedIndex = listOf(0, 60, 300, 900).indexOf(appLockTimeout).coerceAtLeast(0),
                                        enabled = isAppLockEnabled
                                    ) { index -> onLockTimeoutChange(listOf(0, 60, 300, 900)[index]) }
                                    if (isAppLockEnabled) Text("در این بازه، بازگشت سریع به برنامه بدون احراز هویت ممکن است.", fontSize = 8.5.sp, color = theme.mutedColor)
                                }
                            }
                            if (onLogout != null) {
                                SettingsCard("حساب کاربری", AppIcon.Logout, accent = GlassRed) {
                                    SettingsActionRow(
                                        "خروج از حساب کاربری",
                                        "پاک‌شدن نشست فعلی و بازگشت به صفحهٔ ورود",
                                        AppIcon.Logout,
                                        GlassRed
                                    ) { onLogout() }
                                }
                            }
                        }
                    }
                }
                // فوتر «درباره» + دکمهٔ بستن
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppLogo(height = 17.dp)
                        Text("MRM PG Manager", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    }
                    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.clip(DsRadius.Sm).background(theme.searchBgColor)
                                .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Mohammad1724/MRM-PG-Manager"))) } }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RoundedAppIcon(AppIcon.OpenNew, tint = theme.mutedColor, size = 11.dp)
                                Text(if (isFa) "گیت‌هاب" else "GitHub", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            }
                        }
                        Text(if (isFa) "نسخهٔ ${appVersion.ifBlank { "—" }}" else "Version ${appVersion.ifBlank { "—" }}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    }
                }
                val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                SecondaryButton(if (isFa) "بستن" else "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
      }
    }
    if (bulkCreateOpen && session != null) {
        BulkCreateUsersDialog(session = session, onDismiss = { bulkCreateOpen = false })
    }
    // ==== دیالوگ بازیابی بکاپ ====
    if (restoreDialogOpen && restoreUri != null) {
        var restoreAccounts by remember { mutableStateOf(true) }
        var restoreDebtors by remember { mutableStateOf(true) }
        var restoreSettings by remember { mutableStateOf(true) }
        var restoreInvoice by remember { mutableStateOf(true) }
        var restoring by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        Dialog(onDismissRequest = { if (!restoring) restoreDialogOpen = false }) {
            Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl).padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("بازیابی پشتیبان", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

                    if (restorePreview != null) {
                        val info = restorePreview!!
                        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                        Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("اطلاعات فایل:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            Text("• تاریخ ساخت: ${sdf.format(java.util.Date(info.createdAt))}", fontSize = 10.sp, color = theme.inkColor)
                            Text("• نسخه اپ: ${info.appVersion.ifBlank { "-" }}", fontSize = 10.sp, color = theme.inkColor)
                            Text("• تعداد حساب: ${info.accountsCount}", fontSize = 10.sp, color = theme.inkColor)
                            Text("• تعداد بدهکار: ${info.debtorsCount}", fontSize = 10.sp, color = theme.inkColor)
                            if (info.sellerName.isNotBlank()) Text("• فروشنده: ${info.sellerName}", fontSize = 10.sp, color = theme.inkColor)
                            if (info.hasLogo) Text("• دارای لوگو", fontSize = 10.sp, color = theme.inkColor)
                            if (info.encrypted) Text("• رمزنگاری شده", fontSize = 10.sp, color = GlassGreen, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("فایل انتخاب شد. در صورت رمزدار بودن، رمز را وارد کنید.", fontSize = 10.sp, color = theme.mutedColor)
                    }

                    if (restorePassword) {
                        CompactGlassField(
                            value = restorePasswordInput,
                            onValueChange = { restorePasswordInput = it },
                            placeholder = "رمز بکاپ",
                            leadingAppIcon = AppIcon.Lock,
                            keyboardType = KeyboardType.Password
                        )
                    }

                    Text("انتخاب بخش‌ها برای بازیابی:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RestoreCheck("حساب‌های پنل (توکن‌ها)", restoreAccounts) { restoreAccounts = it }
                        RestoreCheck("بدهکاران", restoreDebtors) { restoreDebtors = it }
                        RestoreCheck("تنظیمات (تم، پایش، قفل، نما)", restoreSettings) { restoreSettings = it }
                        RestoreCheck("فاکتور (لوگو و نام فروشنده)", restoreInvoice) { restoreInvoice = it }
                    }

                    restoreResult?.let { Text(it, fontSize = 10.sp, color = GlassGreen, fontWeight = FontWeight.Bold) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (!restoring) {
                                SecondaryButton("انصراف", onClick = { restoreDialogOpen = false }, modifier = Modifier.fillMaxWidth())
                            } else {
                                Box(Modifier.fillMaxWidth().height(52.dp).clip(DsRadius.Xxl).background(theme.searchBgColor.copy(0.5f)), contentAlignment = Alignment.Center) {
                                    Text("انصراف", color = theme.mutedColor.copy(0.5f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            PrimaryButton(
                                text = "بازیابی",
                                enabled = !restoring,
                                loading = restoring,
                                onClick = {
                                    if (!restoreAccounts && !restoreDebtors && !restoreSettings && !restoreInvoice) {
                                        android.widget.Toast.makeText(ctx, "حداقل یک بخش را انتخاب کنید", android.widget.Toast.LENGTH_SHORT).show()
                                        return@PrimaryButton
                                    }
                                    restoring = true
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            ctx.contentResolver.openInputStream(restoreUri!!)?.use { ins ->
                                                com.mrm.pgmanager.utils.BackupManager.restoreBackup(
                                                    ctx, ins, restorePasswordInput,
                                                    restoreAccounts = restoreAccounts,
                                                    restoreDebtors = restoreDebtors,
                                                    restoreSettings = restoreSettings,
                                                    restoreInvoice = restoreInvoice
                                                )
                                            } ?: throw Exception("باز کردن فایل ممکن نشد")
                                        }.onSuccess { (_, msg) ->
                                            withContext(Dispatchers.Main) {
                                                restoreResult = "$msg\nلطفاً اپ را یک‌بار بسته و دوباره باز کنید."
                                                restorePreview = null
                                                restoring = false
                                                android.widget.Toast.makeText(ctx, "بازیابی موفق", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }.onFailure { e ->
                                            withContext(Dispatchers.Main) {
                                                restoreResult = "خطا: ${e.message}"
                                                restoring = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(20.dp).clip(DsRadius.Xs)
            .background(if (checked) theme.accentPrimary.copy(0.78f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (checked) theme.accentPrimary else theme.mutedColor.copy(0.4f)), DsRadius.Xs),
            contentAlignment = Alignment.Center
        ) {
            if (checked) RoundedAppIcon(AppIcon.Check, tint = Color(0xFF202124), size = 13.dp)
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LampColorItem(lamp: LampColor, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(
        modifier.clip(DsRadius.Xl)
            .background(if (selected) lamp.primary.copy(.10f) else Color.Transparent)
            .border(BorderStroke(if (selected) 2.dp else 1.2.dp, if (selected) lamp.primary else theme.borderColor), DsRadius.Xl)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(DsRadius.Md)
                .background(Brush.linearGradient(listOf(lamp.primary, lamp.primary.copy(alpha = 0.7f))))
                .border(BorderStroke(1.dp, Color.White.copy(0.3f)), DsRadius.Md),
            contentAlignment = Alignment.Center
        ) { if (selected) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 18.dp) }
        Text(if (isFa) lamp.labelFa else lamp.label, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
