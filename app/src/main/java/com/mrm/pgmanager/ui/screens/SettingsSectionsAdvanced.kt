package com.mrm.pgmanager.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UsernamePattern
import com.mrm.pgmanager.data.store.SessionStore
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PrimarySaveButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.SegmentedControl
import com.mrm.pgmanager.ui.dialogs.SettingsActionRow
import com.mrm.pgmanager.ui.dialogs.SettingsCard
import com.mrm.pgmanager.ui.dialogs.SettingsInfoRow
import com.mrm.pgmanager.ui.dialogs.SettingsStepper
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ─────────────────────────────────────────────────────────────
//  بخش‌های پیشرفتهٔ تنظیمات — فاز ۲
//
//  این‌ها از ThemeEditorDialog منتقل شده‌اند. برخلافِ بخش‌های فاز ۱ که
//  فقط حالتِ خودشان را دارند، این چهار بخش به Session، SessionStore و
//  چند ActivityResultLauncher وابسته‌اند؛ برای همین در فایلِ جدا هستند
//  تا SettingsScreen.kt خوانا بماند.
//
//  همهٔ متن‌ها از R.string می‌آیند — دیگر خبری از if (isFa) نیست.
// ─────────────────────────────────────────────────────────────

/** اتصال: سرور فعلی، حساب‌های ذخیره‌شده، و تست اتصال. */
@Composable
internal fun ConnectionSection(
    session: Session?,
    store: SessionStore,
    scope: CoroutineScope,
    onSwitchAccount: (Session) -> Unit,
    onAddAccount: () -> Unit
) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    if (session == null) {
        SettingsCard(stringResource(R.string.set_conn_panel), AppIcon.Wifi) {
            Text(
                stringResource(R.string.set_conn_login_first),
                fontSize = 10.5.sp, color = theme.mutedColor
            )
        }
        return
    }

    SettingsCard(stringResource(R.string.set_conn_current), AppIcon.Wifi) {
        SettingsInfoRow(stringResource(R.string.set_conn_url), session.baseUrl, copyable = true)
        SettingsInfoRow(stringResource(R.string.set_conn_admin), session.username)
        SettingsActionRow(
            stringResource(R.string.set_conn_open_browser),
            stringResource(R.string.set_conn_open_desc),
            AppIcon.OpenNew,
            theme.accentPrimary
        ) {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(session.baseUrl.trimEnd('/') + "/dashboard/")
                    )
                )
            }
        }
    }

    // حساب‌های ذخیره‌شده: سوئیچ سریع بین چند پنل بدون خروج از حساب فعلی.
    SettingsCard(stringResource(R.string.set_conn_accounts), AppIcon.Users) {
        var accounts by remember { mutableStateOf(store.readAccounts()) }
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.set_conn_no_accounts), fontSize = 10.sp, color = theme.mutedColor)
        } else accounts.forEach { acc ->
            val isActive = acc.baseUrl == session.baseUrl && acc.username == session.username
            Row(
                Modifier.fillMaxWidth().clip(DsRadius.Lg)
                    .background(if (isActive) theme.accentPrimary.copy(.10f) else theme.searchBgColor)
                    .border(
                        BorderStroke(1.dp, if (isActive) theme.accentPrimary.copy(.35f) else theme.borderColor),
                        DsRadius.Lg
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    MrmText(
                        acc.username, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true
                    )
                    MrmText(
                        acc.baseUrl, fontSize = 8.5.sp, color = theme.mutedColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true
                    )
                }
                if (isActive) {
                    Box(
                        Modifier.clip(DsRadius.Sm).background(theme.accentPrimary.copy(.20f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.set_conn_active), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, color = theme.inkColor
                        )
                    }
                } else {
                    Box(
                        Modifier.clip(DsRadius.Sm).background(GlassGreen.copy(.16f)).clickable {
                            store.setActive(acc); accounts = store.readAccounts(); onSwitchAccount(acc)
                        }.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.set_conn_connect), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, color = GlassGreen
                        )
                    }
                    Box(
                        Modifier.size(24.dp).clip(DsRadius.Sm).background(GlassRed.copy(.12f)).clickable {
                            store.removeAccount(acc.baseUrl, acc.username); accounts = store.readAccounts()
                        },
                        contentAlignment = Alignment.Center
                    ) { RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 12.dp) }
                }
            }
        }
        SettingsActionRow(
            stringResource(R.string.set_conn_add),
            stringResource(R.string.set_conn_add_desc),
            AppIcon.UserAdd,
            theme.accentPrimary
        ) { onAddAccount() }
        Text(stringResource(R.string.set_conn_note), fontSize = 8.5.sp, color = theme.mutedColor)
    }

    SettingsCard(stringResource(R.string.set_conn_test), AppIcon.CheckCircle, accent = GlassGreen) {
        val okTemplate = stringResource(R.string.set_conn_ok)
        val failTemplate = stringResource(R.string.set_conn_fail)
        val unreachable = stringResource(R.string.set_conn_unreachable)

        Text(stringResource(R.string.set_conn_test_desc), fontSize = 11.sp, color = theme.mutedColor)
        PrimarySaveButton(
            text = stringResource(
                if (testing) R.string.set_conn_testing else R.string.set_conn_test_btn
            ),
            enabled = !testing,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            onClick = {
                scope.launch {
                    testing = true
                    val result = runCatching { PanelApi.systemStats(session) }
                    testResult = result.fold(
                        onSuccess = { s ->
                            true to String.format(
                                okTemplate,
                                (s.uptimeSeconds / 86400L).toString(),
                                ((s.uptimeSeconds % 86400L) / 3600L).toString()
                            )
                        },
                        onFailure = { e -> false to String.format(failTemplate, e.message ?: unreachable) }
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

/** کاربران: الگوی نام، قطع خودکار بدهکار، ساخت گروهی، خروجی CSV/JSON. */
@Composable
internal fun UsersSettingsSection(
    session: Session?,
    store: SessionStore,
    scope: CoroutineScope,
    monitoringSettings: MonitoringSettings,
    onMonitoringChange: (MonitoringSettings) -> Unit,
    onBulkCreate: () -> Unit
) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var pattern by remember { mutableStateOf(store.readUsernamePattern()) }
    fun savePattern(p: UsernamePattern) { pattern = p; store.saveUsernamePattern(p) }

    var exportBusy by remember { mutableStateOf(false) }
    var exportList by remember { mutableStateOf<List<Pair<String, List<com.mrm.pgmanager.data.model.PanelUser>>>?>(null) }
    val savedMsg = stringResource(R.string.set_usr_export_saved)
    val errorMsg = stringResource(R.string.set_usr_export_error)
    val failedMsg = stringResource(R.string.set_usr_export_failed)

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    fun writeExport(uri: android.net.Uri?, payload: Pair<String, List<com.mrm.pgmanager.data.model.PanelUser>>?) {
        if (uri == null || payload == null) return
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val out = context.contentResolver.openOutputStream(uri) ?: error("no stream")
                out.use {
                    it.write(
                        if (payload.first == "json")
                            com.mrm.pgmanager.utils.usersToJson(payload.second).toByteArray(Charsets.UTF_8)
                        else
                            com.mrm.pgmanager.utils.usersToCsv(payload.second).toByteArray(Charsets.UTF_8)
                    )
                }
            }.isSuccess
            withContext(Dispatchers.Main) { toast(if (ok) savedMsg else errorMsg) }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val payload = exportList?.firstOrNull { it.first == "csv" }; exportList = null; writeExport(uri, payload)
    }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = exportList?.firstOrNull { it.first == "json" }; exportList = null; writeExport(uri, payload)
    }

    fun exportTimestamp() =
        java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())

    fun startExport(format: String, launcher: (String) -> Unit) {
        if (session == null || exportBusy) return
        scope.launch {
            exportBusy = true
            val list = runCatching { PanelApi.users(session) }.getOrNull()
            exportBusy = false
            if (list == null) { toast(failedMsg); return@launch }
            exportList = listOf(format to list)
            launcher("mrm-users-${exportTimestamp()}.$format")
        }
    }

    SettingsCard(stringResource(R.string.set_usr_pattern), AppIcon.User) {
        Text(stringResource(R.string.set_usr_pattern_desc), fontSize = 11.sp, color = theme.mutedColor)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.set_usr_mode), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = theme.inkColor
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.set_usr_random),
                    stringResource(R.string.set_usr_sequential)
                ),
                selectedIndex = if (pattern.sequential) 1 else 0
            ) { savePattern(pattern.copy(sequential = it == 1)) }
        }
        CompactGlassField(
            pattern.prefix,
            { v ->
                savePattern(
                    pattern.copy(
                        prefix = v.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }.take(24)
                    )
                )
            },
            stringResource(R.string.set_usr_prefix),
            leadingAppIcon = AppIcon.Edit,
            keyboardType = KeyboardType.Ascii,
            fieldHeight = 38.dp
        )
        if (pattern.sequential) {
            SettingsStepper(
                stringResource(R.string.set_usr_start_from),
                pattern.sequentialStart,
                stringResource(R.string.set_usr_unit_number),
                1..999000
            ) { savePattern(pattern.copy(sequentialStart = it)) }
        } else {
            SettingsStepper(
                stringResource(R.string.set_usr_digits),
                pattern.randomDigits,
                stringResource(R.string.set_usr_unit_digit),
                3..6
            ) { savePattern(pattern.copy(randomDigits = it)) }
        }
        Text(
            stringResource(
                R.string.set_usr_sample,
                if (pattern.sequential) pattern.sequentialName(0) else pattern.randomName()
            ),
            fontSize = 11.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold
        )
    }

    SettingsCard(stringResource(R.string.set_usr_debtor_cut), AppIcon.Warning, accent = GlassRed) {
        Text(stringResource(R.string.set_usr_debtor_desc), fontSize = 11.sp, color = theme.mutedColor)
        SettingsSwitchRow(
            stringResource(R.string.set_usr_debtor_switch),
            stringResource(R.string.set_usr_debtor_switch_desc),
            monitoringSettings.debtorAutoDisableEnabled
        ) { onMonitoringChange(monitoringSettings.copy(debtorAutoDisableEnabled = it)) }
        SettingsStepper(
            stringResource(R.string.set_usr_debtor_delay),
            monitoringSettings.debtorAutoDisableAfterHours,
            stringResource(R.string.set_usr_unit_hours),
            1..720,
            step = 1,
            enabled = monitoringSettings.debtorAutoDisableEnabled
        ) { onMonitoringChange(monitoringSettings.copy(debtorAutoDisableAfterHours = it)) }
        if (monitoringSettings.debtorAutoDisableEnabled) {
            Text(stringResource(R.string.set_usr_debtor_example), fontSize = 8.5.sp, color = theme.mutedColor)
        }
        // نمایش تعداد بدهکاران فعلی این پنل
        val debtorCount = store.readDebtors().values.count { it.baseUrl == session?.baseUrl }
        if (debtorCount > 0) {
            Text(
                stringResource(R.string.set_usr_debtor_count, debtorCount.toString()),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassRed
            )
        }
    }

    SettingsCard(stringResource(R.string.set_usr_bulk), AppIcon.Users, accent = GlassGreen) {
        SettingsActionRow(
            stringResource(R.string.set_usr_bulk_action),
            stringResource(R.string.set_usr_bulk_desc),
            AppIcon.Users,
            GlassGreen
        ) { onBulkCreate() }
    }

    SettingsCard(stringResource(R.string.set_usr_export), AppIcon.Download, accent = theme.accentPrimary) {
        Text(stringResource(R.string.set_usr_export_desc), fontSize = 11.sp, color = theme.mutedColor)
        SettingsActionRow(
            stringResource(if (exportBusy) R.string.set_usr_preparing else R.string.set_usr_export_csv),
            stringResource(R.string.set_usr_export_csv_desc),
            AppIcon.Download,
            GlassGreen
        ) { startExport("csv") { name -> csvLauncher.launch(name) } }
        SettingsActionRow(
            stringResource(if (exportBusy) R.string.set_usr_preparing else R.string.set_usr_export_json),
            stringResource(R.string.set_usr_export_json_desc),
            AppIcon.Download,
            theme.accentPrimary
        ) { startExport("json") { name -> jsonLauncher.launch(name) } }
    }
}

/** فاکتور: لوگو و نام فروشنده. */
@Composable
internal fun InvoiceSection(store: SessionStore, scope: CoroutineScope) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var invoiceLogoPath by remember { mutableStateOf(store.readInvoiceLogoPath()) }
    var invoiceLogoBitmap by remember(invoiceLogoPath) { mutableStateOf<Bitmap?>(null) }
    var invoiceSeller by remember { mutableStateOf(store.readInvoiceSeller()) }

    val logoPreview = stringResource(R.string.set_inv_logo_preview)
    val removedMsg = stringResource(R.string.set_inv_logo_removed)
    val savedMsg = stringResource(R.string.set_inv_logo_saved)
    val errorTemplate = stringResource(R.string.set_inv_logo_error)

    LaunchedEffect(invoiceLogoPath) {
        invoiceLogoBitmap = if (!invoiceLogoPath.isNullOrBlank()) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(invoiceLogoPath, opts)
            }.getOrNull()
        } else null
    }

    val invoiceLogoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val logoFile = File(context.filesDir, "invoice_logo.png")
                runCatching {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        val original = BitmapFactory.decodeStream(input)
                        if (original != null) {
                            val size = 400
                            val scaled = Bitmap.createScaledBitmap(original, size, size, true)
                            FileOutputStream(logoFile).use { out ->
                                scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            if (original !== scaled) original.recycle()
                            scaled.recycle()
                        }
                    }
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        store.saveInvoiceLogoPath(logoFile.absolutePath)
                        invoiceLogoPath = logoFile.absolutePath
                        android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, String.format(errorTemplate, e.message ?: ""),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    SettingsCard(stringResource(R.string.set_inv_logo), AppIcon.Image, accent = theme.accentPrimary) {
        Text(stringResource(R.string.set_inv_logo_desc), fontSize = 11.sp, color = theme.mutedColor)
        Box(
            Modifier.fillMaxWidth().clip(DsRadius.Xl)
                .background(if (theme.isDark) Color.White.copy(0.06f) else Color(0xFFF8F8FA))
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = invoiceLogoBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = logoPreview,
                    modifier = Modifier.size(96.dp).clip(DsRadius.Xl),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoundedAppIcon(AppIcon.Image, tint = theme.mutedColor, size = 32.dp)
                    Text(stringResource(R.string.set_inv_no_logo), fontSize = 10.sp, color = theme.mutedColor)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoundedAppIcon(AppIcon.Upload, tint = Color(0xFF1A1A1A), size = 16.dp)
                    Text(
                        stringResource(
                            if (invoiceLogoPath != null) R.string.set_inv_change_logo
                            else R.string.set_inv_pick_logo
                        ),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A)
                    )
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
                            android.widget.Toast.makeText(context, removedMsg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) { RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 18.dp) }
            }
        }
        Text(stringResource(R.string.set_inv_logo_hint), fontSize = 8.5.sp, color = theme.mutedColor)
    }

    SettingsCard(stringResource(R.string.set_inv_seller), AppIcon.Receipt) {
        Text(stringResource(R.string.set_inv_seller_desc), fontSize = 11.sp, color = theme.mutedColor)
        CompactGlassField(
            value = invoiceSeller,
            onValueChange = { v ->
                invoiceSeller = v.take(40)
                store.saveInvoiceSeller(v)
            },
            placeholder = stringResource(R.string.set_inv_seller_hint),
            leadingAppIcon = AppIcon.Receipt,
            keyboardType = KeyboardType.Text
        )
    }
}

/**
 * پشتیبان‌گیری: پوشه، زمان‌بندی خودکار، رمزگذاری و عملیات دستی.
 *
 * `onRequestRestore` فایل انتخاب‌شده را به بالا پاس می‌دهد تا دیالوگِ
 * بازیابی در سطحِ صفحه باز شود (نه داخلِ محتوای اسکرول‌شونده).
 */
@Composable
internal fun BackupSection(
    store: SessionStore,
    scope: CoroutineScope,
    appVersion: String,
    onRequestRestore: (android.net.Uri) -> Unit
) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var backupEnabled by remember { mutableStateOf(store.readBackupEnabled()) }
    var backupInterval by remember { mutableStateOf(store.readBackupIntervalHours()) }
    var backupKeep by remember { mutableStateOf(store.readBackupKeepCount()) }
    var backupPassword by remember { mutableStateOf(store.readBackupPassword()) }
    var backupFolderUri by remember { mutableStateOf(store.readBackupUri()) }
    var backupFolderName by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupLastMsg by remember { mutableStateOf(store.readLastBackupMessage()) }

    val pickFirstMsg = stringResource(R.string.set_bk_pick_first)
    val manualOkMsg = stringResource(R.string.set_bk_manual_ok)
    val autoOkMsg = stringResource(R.string.set_bk_auto_ok)
    val savedMsg = stringResource(R.string.set_bk_saved)
    val errorTemplate = stringResource(R.string.set_bk_error)
    val errorToastTemplate = stringResource(R.string.set_bk_error_toast)
    val cantCreate = stringResource(R.string.set_bk_cant_create)

    LaunchedEffect(backupFolderUri) {
        backupFolderName = if (backupFolderUri != null) {
            val uri = android.net.Uri.parse(backupFolderUri)
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
        } else null
    }

    val pickBackupDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            store.saveBackupUri(it.toString())
            backupFolderUri = it.toString()
        }
    }

    val pickRestoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? -> uri?.let { onRequestRestore(it) } }

    fun performBackup(manual: Boolean) {
        if (backupBusy) return
        val targetUri = backupFolderUri
        if (targetUri.isNullOrBlank()) {
            android.widget.Toast.makeText(context, pickFirstMsg, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        backupBusy = true
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dirUri = android.net.Uri.parse(targetUri)
                val cr = context.contentResolver
                val name = com.mrm.pgmanager.utils.BackupManager.generateFileName()
                val docUri = android.provider.DocumentsContract.createDocument(
                    cr,
                    android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        dirUri, android.provider.DocumentsContract.getTreeDocumentId(dirUri)
                    ),
                    "application/octet-stream",
                    name
                ) ?: throw Exception(cantCreate)
                cr.openOutputStream(docUri)?.use { os ->
                    com.mrm.pgmanager.utils.BackupManager.createBackup(context, os, backupPassword, appVersion)
                }
                com.mrm.pgmanager.utils.BackupManager.pruneBackups(context, dirUri, backupKeep)
                store.saveBackupEnabled(backupEnabled)
                store.saveBackupIntervalHours(backupInterval)
                store.saveBackupKeepCount(backupKeep)
                store.saveBackupPassword(backupPassword)
                com.mrm.pgmanager.work.BackupWorker.schedule(
                    context, if (backupEnabled) backupInterval else 0
                )
            }.onSuccess {
                backupLastMsg = if (manual) manualOkMsg else autoOkMsg
                store.saveLastBackupMessage(backupLastMsg)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                backupLastMsg = String.format(errorTemplate, e.message ?: "")
                store.saveLastBackupSuccess(false)
                store.saveLastBackupMessage(backupLastMsg)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context, String.format(errorToastTemplate, e.message ?: ""),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            withContext(Dispatchers.Main) { backupBusy = false }
        }
    }

    SettingsCard(stringResource(R.string.set_bk_folder), AppIcon.Folder) {
        Text(stringResource(R.string.set_bk_folder_desc), fontSize = 11.sp, color = theme.mutedColor)
        val pickLabel = stringResource(R.string.set_bk_pick_folder)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(DsRadius.Lg)
                .background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .clickable { pickBackupDir.launch(null) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoundedAppIcon(AppIcon.Folder, tint = theme.accentPrimary, size = 17.dp)
                Text(
                    backupFolderName ?: pickLabel,
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
                Text(
                    stringResource(R.string.set_bk_clear_folder), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = GlassRed
                )
            }
        }
    }

    SettingsCard(stringResource(R.string.set_bk_auto), AppIcon.Backup, accent = theme.accentPrimary) {
        Text(stringResource(R.string.set_bk_auto_desc), fontSize = 11.sp, color = theme.mutedColor)
        SettingsSwitchRow(stringResource(R.string.set_bk_auto_switch), null, backupEnabled) { v ->
            backupEnabled = v
            store.saveBackupEnabled(v)
            com.mrm.pgmanager.work.BackupWorker.schedule(context, if (v) backupInterval else 0)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.set_bk_interval), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (backupEnabled) theme.inkColor else theme.mutedColor
            )
            val values = listOf(6, 12, 24, 72, 168)
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.set_bk_int_6h),
                    stringResource(R.string.set_bk_int_12h),
                    stringResource(R.string.set_bk_int_daily),
                    stringResource(R.string.set_bk_int_3d),
                    stringResource(R.string.set_bk_int_weekly)
                ),
                selectedIndex = if (backupEnabled) values.indexOf(backupInterval).coerceAtLeast(0) else 0,
                enabled = backupEnabled
            ) { i ->
                backupInterval = values[i]
                store.saveBackupIntervalHours(backupInterval)
                com.mrm.pgmanager.work.BackupWorker.schedule(context, backupInterval)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.set_bk_keep), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = theme.inkColor
            )
            val keepValues = listOf(3, 5, 7, 14, 30)
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.set_bk_keep_3),
                    stringResource(R.string.set_bk_keep_5),
                    stringResource(R.string.set_bk_keep_7),
                    stringResource(R.string.set_bk_keep_14),
                    stringResource(R.string.set_bk_keep_30)
                ),
                selectedIndex = keepValues.indexOf(backupKeep).coerceAtLeast(2)
            ) { i ->
                backupKeep = keepValues[i]
                store.saveBackupKeepCount(backupKeep)
            }
            Text(stringResource(R.string.set_bk_keep_desc), fontSize = 8.5.sp, color = theme.mutedColor)
        }
    }

    SettingsCard(stringResource(R.string.set_bk_encryption), AppIcon.Lock, accent = GlassGreen) {
        Text(stringResource(R.string.set_bk_enc_desc), fontSize = 11.sp, color = theme.mutedColor)
        CompactGlassField(
            value = backupPassword,
            onValueChange = { v -> backupPassword = v.take(64); store.saveBackupPassword(v) },
            placeholder = stringResource(R.string.set_bk_password),
            leadingAppIcon = AppIcon.Lock,
            keyboardType = KeyboardType.Password
        )
        // بکاپ بدون رمز شامل توکن‌های ورود به پنل‌هاست؛ هشدار صریح بده.
        if (backupPassword.isBlank()) {
            Text(
                stringResource(R.string.set_bk_warn_plain), fontSize = 8.5.sp,
                color = GlassRed, fontWeight = FontWeight.Bold
            )
        }
    }

    SettingsCard(stringResource(R.string.set_bk_ops), AppIcon.Settings) {
        if (backupLastMsg.isNotBlank()) {
            Text(backupLastMsg, fontSize = 10.sp, color = theme.mutedColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.weight(1f).height(46.dp).clip(DsRadius.Lg)
                    .background(
                        if (backupBusy) theme.accentPrimary.copy(0.5f) else theme.accentPrimary.copy(0.78f)
                    )
                    .clickable(enabled = !backupBusy) { performBackup(manual = true) },
                contentAlignment = Alignment.Center
            ) {
                if (backupBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF1A1A1A), strokeWidth = 2.dp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Backup, tint = Color(0xFF1A1A1A), size = 16.dp)
                        Text(
                            stringResource(R.string.set_bk_manual), fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A)
                        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoundedAppIcon(AppIcon.Restore, tint = theme.inkColor, size = 16.dp)
                    Text(
                        stringResource(R.string.set_bk_restore_file), fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold, color = theme.inkColor
                    )
                }
            }
        }
        val lastAt = store.readLastBackupAt()
        if (lastAt > 0L) {
            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
            Text(
                stringResource(R.string.set_bk_last, sdf.format(java.util.Date(lastAt))),
                fontSize = 8.5.sp, color = theme.mutedColor
            )
        }
    }
}
