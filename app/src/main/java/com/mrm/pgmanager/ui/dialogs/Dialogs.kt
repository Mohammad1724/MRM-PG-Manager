package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.components.PrimaryButton
import com.mrm.pgmanager.ui.components.SecondaryButton
import com.mrm.pgmanager.ui.components.DangerButton
import com.mrm.pgmanager.ui.components.GlassButton
import com.mrm.pgmanager.ui.components.SmallButton
import com.mrm.pgmanager.ui.components.MiniGlassButton
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.TechnicalContainer
import com.mrm.pgmanager.ui.components.ActionIconButton
import com.mrm.pgmanager.ui.components.AppLogo
import com.mrm.pgmanager.ui.components.PrimarySaveButton
import com.mrm.pgmanager.ui.components.MutedCancelButton
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LampColor
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.usersToCsv
import com.mrm.pgmanager.utils.usersToJson
import com.mrm.pgmanager.utils.lastSeenText
import com.mrm.pgmanager.utils.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

/** رنگ خاکستریِ واضح برای کادرِ کاشی‌ها (تمایز بهتر در حالت روشن/تیره). */
fun tileBorderColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF606068) else Color(0xFF9C978C)

/** دیالوگ کوچکِ تأییدِ عملیات (مثل ریست حجم/زمان و عملیات گروهی). */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "تایید",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /** عملیات مخرب (مثل حذف) → قرمز؛ بقیه → اکسنت برنامه. */
    danger: Boolean = false
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp)).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = theme.inkColor)
                Text(message, fontSize = 13.5.sp, color = theme.mutedColor, lineHeight = 20.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    if (danger) {
                        DangerButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                    } else {
                        PrimaryButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** منوی اکشنِ سریع (long-press روی کارت): تمدید سریع + عملیات‌های پرتکرار بدون دیالوگِ کامل. */
@Composable
fun QuickActionSheet(
    user: PanelUser,
    onDismiss: () -> Unit,
    onUseTemplate: () -> Unit,
    onToggle: () -> Unit,
    onCopySub: () -> Unit,
    onQr: () -> Unit,
    onEdit: () -> Unit,
    onResetUsage: () -> Unit,
    onResetExpiry: () -> Unit,
    onDelete: () -> Unit,
    onDebtor: (() -> Unit)? = null,
    isDebtor: Boolean = false,
    onInvoice: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(if (user.isOnline) GlassGreen.copy(.14f) else Color.Gray.copy(.12f)), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (user.isOnline) GlassGreen else Color.Gray)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { 
                        MrmText(user.username, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                        MrmText(lastSeenText(user.onlineAt, user.isOnline), fontSize = 10.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, isTechnical = true)
                    }
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(theme.accentPrimary.copy(.14f)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(if (user.status == "disabled") "غیرفعال" else "فعال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionRow(AppIcon.Template, "تمپلت", theme.accentPrimary, Modifier.weight(1f)) { onUseTemplate(); onDismiss() }
                        QuickActionRow(AppIcon.Edit, "ویرایش", theme.inkColor, Modifier.weight(1f)) { onEdit(); onDismiss() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionRow(AppIcon.Reset, "ریست حجم", theme.accentPrimary, Modifier.weight(1f)) { onResetUsage(); onDismiss() }
                        QuickActionRow(AppIcon.Calendar, "ریست زمان", theme.accentPrimary, Modifier.weight(1f)) { onResetExpiry(); onDismiss() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionRow(AppIcon.Copy, "کپی لینک", theme.inkColor, Modifier.weight(1f)) { onCopySub(); onDismiss() }
                        QuickActionRow(AppIcon.Qr, "نمایش QR", theme.inkColor, Modifier.weight(1f)) { onQr(); onDismiss() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionRow(AppIcon.Note, "فاکتور 🧾", theme.accentPrimary, Modifier.weight(1f)) {
                            if (onInvoice != null) { onInvoice(); onDismiss() } else { onDismiss() }
                        }
                        QuickActionRow(if (isDebtor) AppIcon.CheckCircle else AppIcon.Warning, if (isDebtor) "تسویه بدهی" else "بدهکار", GlassRed, Modifier.weight(1f)) {
                            if (onDebtor != null) { onDebtor(); onDismiss() } else { onDismiss() }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionRow(AppIcon.User, if (user.status == "disabled") "فعال‌سازی" else "غیرفعال‌سازی", theme.inkColor, Modifier.weight(1f)) { onToggle(); onDismiss() }
                        QuickActionRow(AppIcon.Delete, "حذف کاربر", GlassRed, Modifier.weight(1f)) { onDelete(); onDismiss() }
                    }
                }
                
                SecondaryButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuickActionRow(icon: AppIcon, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // چیپ رنگی کم‌رنگ؛ همان زبان ردیف‌های اکشنِ تنظیمات (مرز یک‌چهارم رنگ).
    Box(modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(.10f)).border(BorderStroke(1.dp, color.copy(.26f)), RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundedAppIcon(icon, tint = color, size = 16.dp)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

/** ردیف سوئیچ استاندارد تنظیمات: عنوان + توضیح اختیاری + Switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (enabled) theme.inkColor else theme.mutedColor)
            if (subtitle != null) Text(subtitle, fontSize = 9.5.sp, color = theme.mutedColor)
        }
        Switch(checked = checked, onCheckedChange = { if (enabled) onChange(it) }, enabled = enabled)
    }
}

/** استپر عددی (− / +) به‌جای فیلدهای متنی کوچک؛ سریع و بدون خطای تایپ. */
@Composable
fun SettingsStepper(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int = 1,
    enabled: Boolean = true,
    onChange: (Int) -> Unit
) {
    val theme = LocalThemeState.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enabled) theme.inkColor else theme.mutedColor)
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (enabled) theme.accentPrimary.copy(.18f) else theme.searchBgColor)
                .clickable(enabled = enabled) { onChange((value - step).coerceIn(range)) },
            contentAlignment = Alignment.Center
        ) { Text("−", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
        Box(
            Modifier.width(66.dp).height(30.dp).clip(RoundedCornerShape(9.dp))
                .background(theme.searchBgColor)
                .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) { Text("$value $unit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (enabled) theme.accentPrimary.copy(.18f) else theme.searchBgColor)
                .clickable(enabled = enabled) { onChange((value + step).coerceIn(range)) },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
    }
}

/** کنترل سگمنت‌شدهٔ هم‌سبک با تب‌های شناور پایین برنامه (accent + متن تیره روی گزینهٔ فعال). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    icons: List<AppIcon> = emptyList(),
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(theme.searchBgColor.copy(alpha = 0.6f))
            .border(BorderStroke(1.2.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
            .padding(4.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.55f),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) theme.accentPrimary.copy(.85f) else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (icons.getOrNull(index) != null) RoundedAppIcon(icons[index], tint = if (selected) Color(0xFF1A1A1A) else theme.mutedColor, size = 16.dp)
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color(0xFF1A1A1A) else theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** کارت استاندارد هر بخش تنظیمات؛ همان surface خنثی + border ظریفِ کارت‌های داشبورد. */
@Composable
fun SettingsCard(
    title: String,
    icon: AppIcon,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalThemeState.current
    val ac = accent ?: theme.accentPrimary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(ac.copy(.12f)), contentAlignment = Alignment.Center) {
                RoundedAppIcon(icon, tint = ac, size = 16.dp)
            }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
        }
        content()
    }
}

/** ردیف اکشن رنگی با آیکون (خروج از حساب، بازنشانی و ...). */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    icon: AppIcon,
    accent: Color,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(accent.copy(.08f))
            .border(BorderStroke(1.dp, accent.copy(.22f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(.12f)), contentAlignment = Alignment.Center) {
            RoundedAppIcon(icon, tint = accent, size = 18.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = theme.mutedColor)
        }
    }
}

/** ردیف اطلاعات فقط‌خواندنی با قابلیت کپی (آدرس پنل / نام کاربری). */
@Composable
fun SettingsInfoRow(label: String, value: String, copyable: Boolean = false) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 9.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
            MrmText(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
        }
        if (copyable) {
            ActionIconButton(
                icon = { RoundedAppIcon(AppIcon.Copy, tint = theme.inkColor, size = 16.dp) },
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                    android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                },
                size = 36.dp
            )
        }
    }
}

/** آیتم انتخاب رنگ اکسنت با پیش‌نمایش گرادیانی و تیک هنگام انتخاب. */
@Composable
private fun LampColorItem(lamp: LampColor, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Row(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) lamp.primary.copy(.10f) else Color.Transparent)
            .border(BorderStroke(if (selected) 2.dp else 1.2.dp, if (selected) lamp.primary else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(lamp.primary, lamp.primary.copy(alpha = 0.7f))))
                .border(BorderStroke(1.dp, Color.White.copy(0.3f)), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { if (selected) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 18.dp) }
        Text(lamp.labelFa, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
    onAddAccount: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context) }
    var section by remember { mutableStateOf("ظاهر") }
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
    val sections = remember(session) { if (session == null) listOf("ظاهر", "پشتیبان") else listOf("ظاهر", "پایش", "اعلان‌ها", "اتصال", "کاربران", "فاکتور", "پشتیبان", "امنیت") }

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
        Box(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).clip(RoundedCornerShape(26.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(26.dp))
                .padding(16.dp)
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                // هدر: آیکون اکسنت + عنوان + دکمهٔ بستن
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                            .background(theme.accentPrimary.copy(.16f))
                            .border(BorderStroke(1.dp, theme.accentPrimary.copy(.32f)), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text("تنظیمات", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text("شخصی‌سازی، پایش و امنیت حساب مدیر", fontSize = 10.sp, color = theme.mutedColor)
                    }
                }
                // تب بخش‌ها به‌صورت سگمنت یکدست (اگر فقط یک بخش باشد، مخفی می‌ماند)
                if (sections.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                            .background(theme.searchBgColor)
                            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(13.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sections.forEach { label ->
                            val selected = section == label
                            Box(
                                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) theme.accentPrimary.copy(.78f) else Color.Transparent)
                                    .clickable { section = label },
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
                    when (section) {
                        "ظاهر" -> {
                            SettingsCard("حالت نمایش", AppIcon.Palette) {
                                SegmentedControl(
                                    options = listOf("روشن", "تیره", "خودکار"),
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
                                Text("در حالت «خودکار» برنامه از حالت روشن/تیرهٔ سیستم پیروی می‌کند.", fontSize = 9.5.sp, color = theme.mutedColor)
                            }
                            SettingsCard("رنگ اصلی برنامه", AppIcon.Palette) {
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
                            SettingsCard("رنگ سفارشی", AppIcon.Palette, accent = themeState.customColor ?: theme.accentPrimary) {
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
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(preview, preview.copy(alpha = .6f)))).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                        if (activeCustom != null) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 15.dp)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(if (activeCustom != null) "رنگ سفارشی فعال است" else "با اسلایدرها رنگ دلخواهت را بساز", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                        Text("تغییرات با رهاشدن اسلایدر اعمال می‌شود", fontSize = 9.sp, color = theme.mutedColor)
                                    }
                                }
                                @Composable fun colorSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, label: String, labelFaWidth: androidx.compose.ui.unit.Dp = 46.dp, onDone: () -> Unit = {}) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(label, fontSize = 9.sp, color = theme.mutedColor, modifier = Modifier.width(labelFaWidth))
                                        Slider(
                                            value = value, onValueChange = onChange, valueRange = range,
                                            onValueChangeFinished = onDone,
                                            colors = SliderDefaults.colors(thumbColor = preview, activeTrackColor = preview, inactiveTrackColor = theme.searchBgColor),
                                            modifier = Modifier.weight(1f).height(22.dp)
                                        )
                                    }
                                }
                                colorSlider(hue, { hue = it }, 0f..360f, "رنگ‌مایه") { onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, valueCmp))) }
                                colorSlider(sat, { sat = it }, 0.25f..1f, "غلظت") { onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, valueCmp))) }
                                colorSlider(valueCmp, { valueCmp = it }, 0.45f..1f, "روشنایی") { onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, valueCmp))) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(preview.copy(.18f)).border(BorderStroke(1.dp, preview.copy(.4f)), RoundedCornerShape(9.dp)).clickable { onThemeChange(themeState.copy(customColor = preview)) }, contentAlignment = Alignment.Center) { Text("اعمال این رنگ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                                    if (activeCustom != null) Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(GlassRed.copy(.10f)).border(BorderStroke(1.dp, GlassRed.copy(.3f)), RoundedCornerShape(9.dp)).clickable { onThemeChange(themeState.copy(customColor = null)) }, contentAlignment = Alignment.Center) { Text("حذف رنگ سفارشی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassRed) }
                                }
                            }
                            SettingsCard("تیرهٔ خالص (AMOLED)", AppIcon.DarkMode) {
                                SettingsSwitchRow(
                                    "پس‌زمینهٔ مشکی مطلق",
                                    "در حالت تیره، پس‌زمینه کاملاً سیاه می‌شود؛ صرفه‌جویی باتری در نمایشگرهای AMOLED",
                                    themeState.amoledDark
                                ) { onThemeChange(themeState.copy(amoledDark = it)) }
                            }
                        }
                        "پایش" -> {
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
                        "اعلان‌ها" -> {
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
                                Text("وقتی کاربری به عنوان بدهکار ثبت می‌شود یا پس از مهلت به صورت خودکار قطع می‌شود، اعلان دریافت می‌کنی.", fontSize = 9.sp, color = theme.mutedColor)
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
                        "اتصال" -> {
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
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .background(if (isActive) theme.accentPrimary.copy(.10f) else theme.searchBgColor)
                                                .border(BorderStroke(1.dp, if (isActive) theme.accentPrimary.copy(.35f) else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                MrmText(acc.username, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                                                MrmText(acc.baseUrl, fontSize = 8.5.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                                            }
                                            if (isActive) {
                                                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(theme.accentPrimary.copy(.20f)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("فعال", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                                            } else {
                                                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(GlassGreen.copy(.16f)).clickable {
                                                    store.setActive(acc); accounts = store.readAccounts(); onSwitchAccount(acc)
                                                }.padding(horizontal = 8.dp, vertical = 4.dp)) { Text("اتصال", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlassGreen) }
                                                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(GlassRed.copy(.12f)).clickable {
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
                                    Text("برقراری ارتباط با پنل و دریافت آمار سیستم، برای اطمینان از سلامت دسترسی.", fontSize = 9.5.sp, color = theme.mutedColor)
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
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .background(color.copy(.10f))
                                                .border(BorderStroke(1.dp, color.copy(.30f)), RoundedCornerShape(12.dp))
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
                        "کاربران" -> {
                            var pattern by remember { mutableStateOf(store.readUsernamePattern()) }
                            fun savePattern(p: com.mrm.pgmanager.data.model.UsernamePattern) { pattern = p; store.saveUsernamePattern(p) }
                            SettingsCard("الگوی نام خودکار", AppIcon.User) {
                                Text("در ساخت کاربر جدید (تکی یا گروهی)، نام‌ها با این الگو تولید می‌شوند.", fontSize = 9.5.sp, color = theme.mutedColor)
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
                                Text("نمونه: ${if (pattern.sequential) pattern.sequentialName(0) else pattern.randomName()}", fontSize = 9.5.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold)
                            }
                            SettingsCard("بدهکاران - قطع خودکار", AppIcon.Warning, accent = GlassRed) {
                                Text("وقتی کاربری به عنوان بدهکار ثبت شد، پس از مدت تعیین‌شده به صورت خودکار غیرفعال می‌شود. با تسویه بدهی، دوباره فعال می‌گردد.", fontSize = 9.5.sp, color = theme.mutedColor)
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
                                Text("فهرست کامل کاربران پنل را به‌صورت فایل CSV یا JSON ذخیره و اشتراک‌گذاری کن.", fontSize = 9.5.sp, color = theme.mutedColor)
                                SettingsActionRow(if (exportBusy) "در حال آماده‌سازی..." else "خروجی CSV", "مناسب اکسل و گزارش‌گیری", AppIcon.Download, GlassGreen) { startExport("csv") { name -> csvLauncher.launch(name) } }
                                SettingsActionRow(if (exportBusy) "در حال آماده‌سازی..." else "خروجی JSON", "مناسب برنامه‌نویسی و بکاپ", AppIcon.Download, theme.accentPrimary) { startExport("json") { name -> jsonLauncher.launch(name) } }
                            }
                        }
                        "فاکتور" -> {
                            SettingsCard("لوگوی فاکتور", AppIcon.Image, accent = theme.accentPrimary) {
                                Text("تصویری که بالای فاکتورهای متنی و PDF نمایش داده می‌شود.", fontSize = 9.5.sp, color = theme.mutedColor)
                                // پیش‌نمایش لوگو
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .background(if (theme.isDark) Color.White.copy(0.06f) else Color(0xFFF8F8FA))
                                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (invoiceLogoBitmap != null) {
                                        Image(
                                            bitmap = invoiceLogoBitmap!!.asImageBitmap(),
                                            contentDescription = "Logo Preview",
                                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)),
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
                                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
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
                                            Modifier.height(42.dp).width(42.dp).clip(RoundedCornerShape(12.dp))
                                                .background(GlassRed.copy(0.10f))
                                                .border(BorderStroke(1.dp, GlassRed.copy(0.30f)), RoundedCornerShape(12.dp))
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
                                Text("این نام زیر لوگو و بالای فاکتور نمایش داده می‌شود.", fontSize = 9.5.sp, color = theme.mutedColor)
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
                        "پشتیبان" -> {
                            SettingsCard("پوشه ذخیره‌سازی", AppIcon.Folder) {
                                Text("پشتیبان‌ها در پوشه‌ای که انتخاب می‌کنید روی حافظهٔ گوشی ذخیره می‌شوند.", fontSize = 9.5.sp, color = theme.mutedColor)
                                Box(
                                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(theme.searchBgColor)
                                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp))
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
                                        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp))
                                            .background(GlassRed.copy(0.08f))
                                            .border(BorderStroke(0.8.dp, GlassRed.copy(0.20f)), RoundedCornerShape(8.dp))
                                            .clickable {
                                                store.saveBackupUri(null)
                                                backupFolderUri = null
                                            }.padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لغو انتخاب پوشه", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                                    }
                                }
                            }

                            SettingsCard("پشتیبان‌گیری خودکار", AppIcon.Backup, accent = theme.accentPrimary) {
                                Text("در بازه‌های زمانی تعیین‌شده از همهٔ تنظیمات و حساب‌ها یک کپی امن در پوشهٔ انتخابی گرفته می‌شود.", fontSize = 9.5.sp, color = theme.mutedColor)
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
                                Text("با تعیین رمز، فایل بکاپ با AES-256 رمزنگاری می‌شود و بدون رمز روی دستگاه دیگری قابل بازیابی نیست. برای بکاپ ساده و بدون رمز این فیلد را خالی بگذارید.", fontSize = 9.5.sp, color = theme.mutedColor)
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
                                    Text(backupLastMsg, fontSize = 9.sp, color = theme.mutedColor)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
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
                                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                                            .background(theme.searchBgColor)
                                            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp))
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
                        "امنیت" -> {
                            SettingsCard("قفل برنامه", AppIcon.Lock, accent = GlassGreen) {
                                SettingsSwitchRow(
                                    "قفل امنیتی برنامه",
                                    "ورود با اثر انگشت یا پین/الگوی گوشی هنگام بازکردن اپ",
                                    isAppLockEnabled
                                ) { onAppLockChange(it) }
                                Text(
                                    if (isAppLockEnabled) "قفل فعال است؛ هنگام هر بار ورود، هویت شما تأیید می‌شود."
                                    else "با فعال‌سازی، هر بار ورود به برنامه نیازمند تأیید هویت خواهد بود.",
                                    fontSize = 9.5.sp, color = theme.mutedColor
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(7.dp)).background(theme.searchBgColor)
                                .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Mohammad1724/MRM-PG-Manager"))) } }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RoundedAppIcon(AppIcon.OpenNew, tint = theme.mutedColor, size = 11.dp)
                                Text("گیت‌هاب", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            }
                        }
                        Text("نسخهٔ ${appVersion.ifBlank { "—" }}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    }
                }
                SecondaryButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
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
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("بازیابی پشتیبان", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

                    if (restorePreview != null) {
                        val info = restorePreview!!
                        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("اطلاعات فایل:", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
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
                                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(theme.searchBgColor.copy(0.5f)), contentAlignment = Alignment.Center) {
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor)
            .border(BorderStroke(0.8.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
            .background(if (checked) theme.accentPrimary.copy(0.78f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (checked) theme.accentPrimary else theme.mutedColor.copy(0.4f)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) RoundedAppIcon(AppIcon.Check, tint = Color(0xFF202124), size = 13.dp)
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SubscriptionQrDialog(user: PanelUser, onDismiss: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val qrBitmap = remember(user.subUrl) {
        runCatching {
            val writerClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter")
            val formatClass = Class.forName("com.google.zxing.BarcodeFormat")
            val hintClass = Class.forName("com.google.zxing.EncodeHintType")
            val qrCodeFormat = formatClass.getField("QR_CODE").get(null)
            val marginHint = hintClass.getField("MARGIN").get(null)
            val writer = writerClass.getDeclaredConstructor().newInstance()
            val encodeMethod = writerClass.getMethod("encode", String::class.java, formatClass, Int::class.java, Int::class.java, Map::class.java)
            val bitMatrix = encodeMethod.invoke(writer, user.subUrl, qrCodeFormat, 512, 512, mapOf(marginHint to 1))
            val matrixClass = bitMatrix!!.javaClass
            val getMethod = matrixClass.getMethod("get", Int::class.java, Int::class.java)
            val getWidthMethod = matrixClass.getMethod("getWidth")
            val getHeightMethod = matrixClass.getMethod("getHeight")
            val w = getWidthMethod.invoke(bitMatrix) as Int
            val h = getHeightMethod.invoke(bitMatrix) as Int
            val pixels = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val isBlack = getMethod.invoke(bitMatrix, x, y) as Boolean
                pixels[y * w + x] = if (isBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    // به اشتراک‌گذاری عکس QR + لینک متنی از طریق FileProvider.
    fun shareQr() {
        val bitmap = qrBitmap ?: run {
            android.widget.Toast.makeText(context, "ساخت QR ممکن نشد", android.widget.Toast.LENGTH_SHORT).show()
            // Fallback: فقط لینک
            val fallback = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, user.subUrl) }
            context.startActivity(Intent.createChooser(fallback, "اشتراک"))
            return
        }
        runCatching {
            val shareDir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            // پاک‌کردن فایل‌های قدیمی برای انباشته‌نشدن کش
            shareDir.listFiles()?.forEach {
                if (it.lastModified() < System.currentTimeMillis() - 3_600_000L) it.delete()
            }
            val file = java.io.File(shareDir, "qr-${user.username}.png")
            java.io.FileOutputStream(file).use { out ->
                // برای خوانایی بهتر در تلگرام/واتساپ پس‌زمینهٔ سفید با حاشیه ذخیره می‌کنیم.
                val pad = 32
                val bmp = android.graphics.Bitmap.createBitmap(bitmap.width + pad * 2, bitmap.height + pad * 2, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(bitmap, pad.toFloat(), pad.toFloat(), null)
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                if (bmp !== bitmap) bmp.recycle()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // لینک متنی هم در متن قرار می‌گیرد تا اگر اپلیکیشن مقصد عکس را پشتیبانی نکرد، لینک برود.
                putExtra(Intent.EXTRA_TEXT, user.subUrl)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک QR"))
        }.onFailure { e ->
            android.widget.Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MrmText("QR ${user.username}", fontWeight = FontWeight.Bold, color = theme.inkColor, isTechnical = true)
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text("QR خطا", fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("کپی", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                        android.widget.Toast.makeText(context, "لینک اشتراک کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f))
                    PrimaryButton("اشتراک", onClick = ::shareQr, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("بستن", color = theme.mutedColor) }
            }
        }
    }
}

@Composable
fun ShamsiCalendarPickerDialog(initialDateShamsi: String, onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
    val theme = LocalThemeState.current
    val today = JalaliCalendar.todayJalali()
    val parsed = remember(initialDateShamsi) {
        val p = initialDateShamsi.replace("-", "/").split("/")
        if (p.size == 3) JalaliCalendar.Date(p[0].toIntOrNull() ?: today.year, p[1].toIntOrNull() ?: today.month, p[2].toIntOrNull() ?: today.day) else today
    }
    var y by remember { mutableStateOf(parsed.year) }
    var m by remember { mutableStateOf(parsed.month) }
    var d by remember { mutableStateOf(parsed.day) }
    val daysInMonth = when {
        m in 1..6 -> 31
        m in 7..11 -> 30
        else -> {
            // محاسبهٔ سال کبیسهٔ شمسی بر پایهٔ چرخهٔ ۳۳ساله (دقت خوب برای سال‌های ۱۲۰۰ تا ۱۵۰۰ ه‍.ش)
            val mod = ((y - 474) % 33 + 33) % 33
            val isLeap = mod == 1 || mod == 5 || mod == 9 || mod == 13 || mod == 17 || mod == 22 || mod == 26 || mod == 30
            if (isLeap) 30 else 29
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp)).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("تقویم", fontWeight = FontWeight.Bold, color = theme.inkColor)
                    TextButton(onClick = { y = today.year; m = today.month; d = today.day }) { Text("امروز", color = theme.accentPrimary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ناوبری ماه: کاشی‌های خاکستریِ خنثیِ design system.
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(9.dp)).clickable { if (m > 1) m-- else { m = 12; y-- } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Prev, tint = theme.inkColor, size = 18.dp) }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp)).padding(8.dp), contentAlignment = Alignment.Center) { Text("${JalaliCalendar.Date(y, m, 1).getMonthName()} $y", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(9.dp)).clickable { if (m < 12) m++ else { m = 1; y++ } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Next, tint = theme.inkColor, size = 18.dp) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(200.dp)) {
                    items((1..daysInMonth).toList()) { day ->
                        val sel = day == d
                        // روز انتخاب‌شده = کپسول اکسنت ۷۸٪ و متن تیره؛ سایر روزها شفاف (هم‌تراز با سگمنت تنظیمات).
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) theme.accentPrimary.copy(.78f) else Color.Transparent).clickable { d = day }, contentAlignment = Alignment.Center) {
                            Text("$day", color = if (sel) Color(0xFF202124) else theme.inkColor, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    PrimaryButton("تایید", onClick = { onDateSelected(JalaliCalendar.Date(y, m, d).toString()); onDismiss() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Small compact field for dialog - fixes half number issue
@Composable
fun CompactGlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    leading: String = "",
    leadingAppIcon: AppIcon? = null,
    fieldHeight: androidx.compose.ui.unit.Dp = 42.dp
) {
    val theme = LocalThemeState.current
    Box(
        // فیلد استاندارد فرم: سطح خاکستری روشن و border خنثی، نزدیک به ورودی‌های پنل وب.
        modifier = modifier.fillMaxWidth().height(fieldHeight).clip(RoundedCornerShape(10.dp))
            .background(if (theme.isDark) Color.White.copy(.10f) else theme.searchBgColor)
            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (leadingAppIcon != null) RoundedAppIcon(leadingAppIcon, tint = theme.mutedColor, size = 16.dp) else if (leading.isNotEmpty()) Text(leading, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = theme.mutedColor.copy(0.55f), fontSize = 12.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(0.06f)).clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) { Text("×", fontSize = 12.sp, color = theme.mutedColor) }
        }
    }
}

// === NEW JELLY GLASS USER EDITOR - v5.1 with groups & userlimit ===
@Composable
@Suppress("UNUSED_PARAMETER") // onDelete/onResetExpiry/onApplyTemplateToUser برای سازگاری API نگه داشته شده‌اند
fun UserEditorDialog(
    initial: PanelUser?, onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?, onDelete: (() -> Unit)?, onResetUsage: (() -> Unit)?, onResetExpiry: (() -> Unit)?,
    onSaveWithTemplate: ((username: String, templateId: Int, note: String) -> Unit)? = null,
    onApplyTemplateToUser: ((templateId: Int, note: String) -> Unit)? = null,
    session: com.mrm.pgmanager.data.model.Session? = null
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    // «زمان کل» = روزهای باقی‌مانده از امروز تا تاریخ انقضا (همان مقداری که پس از ریست/تمدید معنادار است).
    // پیش از این، روزها از تاریخ ساخت حساب می‌شمرده که پس از تمدید باعث نمایش عدد غلط (مثل ۱۰۴ روز) می‌شد.
    var days by remember { mutableStateOf(runCatching {
        initial?.let { user ->
            val expires = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expires).coerceAtLeast(0L).toString()
        } ?: ""
    }.getOrDefault("")) }
    // ورودی‌های افزایشی؛ مقدار نهایی حجم/زمان جدا نگه داشته می‌شود تا با +GB و +روز جمع شود.
    var addGb by remember { mutableStateOf("") }
    var addDaysInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hwid by remember { mutableStateOf(initial?.hwidLimit?.toString() ?: "0") }
    var groupIds by remember { mutableStateOf(initial?.groupIds ?: emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var active by remember { mutableStateOf(initial?.status != "disabled") }
    var selectedTemplate by remember { mutableStateOf<Int?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var resetUsage by remember { mutableStateOf(false) }

    LaunchedEffect(session) { if (session != null) {
        groups = runCatching { PanelApi.groups(session) }.getOrDefault(emptyList())
        templates = runCatching { PanelApi.userTemplates(session) }.getOrDefault(emptyList())
    } }
    // هر بخش اصلی یک کادر مستقل دارد تا فرم در موبایل سریع‌تر قابل اسکن باشد.
    fun card() = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if (theme.isDark) Color.White.copy(.075f) else Color.White)
        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp))
        .padding(8.dp)
    fun addDays(value: Int) { days = ((days.toIntOrNull() ?: 0) + value).toString() }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(RoundedCornerShape(16.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(16.dp))) {
            Column(Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundedAppIcon(AppIcon.Edit, tint = theme.inkColor, size = 18.dp)
                    Text(if (initial == null) "ایجاد کاربر" else "ویرایش کاربر", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                }
                // اطلاعات پایه
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اطلاعات پایه", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (initial == null) {
                            // هنگام ساخت کاربر، تولید نام تصادفی دوباره در دسترس است.
                            CompactGlassField(username, { username = it }, "نام کاربری", Modifier.weight(1f), KeyboardType.Ascii, "")
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(theme.accentPrimary.copy(.18f)).clickable { username = store.readUsernamePattern().randomName() }, contentAlignment = Alignment.Center) {
                                RoundedAppIcon(AppIcon.Random, tint = theme.inkColor, size = 19.dp)
                            }
                        } else {
                            // در حالت ویرایش، نام مانند پنل PasarGuard فقط برای مشاهده است.
                            // نام در حالت ویرایش فقط نمایش داده می‌شود و عمداً بسیار کوتاه است.
                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(7.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                                MrmText(initial.username, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                            }
                        }
                        if (initial != null) {
                            Box(Modifier.height(26.dp).clip(RoundedCornerShape(7.dp)).background(if (active) GlassGreen.copy(.14f) else GlassRed.copy(.12f)).border(BorderStroke(1.dp, if (active) GlassGreen else GlassRed), RoundedCornerShape(7.dp)).clickable { active = !active }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text(if (active) "فعال" else "غیرفعال", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (active) GlassGreen else GlassRed) }
                        }
                    }
                }
                // حجم و زمان
                Column(card(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("حجم و زمان اشتراک", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    // حجم کل مستقیماً قابل تعیین است؛ کادر +GB فقط مقدار افزایشی را به آن اضافه می‌کند.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactGlassField(limitGb, { limitGb = it.filter { c -> c.isDigit() || c == '.' } }, "حجم کل (GB)", Modifier.weight(1.35f), KeyboardType.Decimal, "", fieldHeight = 34.dp)
                        CompactGlassField(addGb, { addGb = it.filter { c -> c.isDigit() || c == '.' } }, "+ GB", Modifier.weight(.65f), KeyboardType.Decimal, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.accentPrimary.copy(.18f)).clickable { val add = addGb.toDoubleOrNull() ?: 0.0; if (add > 0) { limitGb = ((limitGb.toDoubleOrNull() ?: 0.0) + add).toString(); addGb = "" } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 18.dp) }
                    }
                    // زمان کل نیز مستقل قابل ویرایش است و +روز به مقدار فعلی افزوده می‌شود.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactGlassField(days, { days = it.filter(Char::isDigit) }, "زمان کل", Modifier.weight(1.15f), KeyboardType.Number, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.accentPrimary.copy(.18f)).clickable { showCalendar = true }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Calendar, tint = theme.inkColor, size = 18.dp) }
                        CompactGlassField(addDaysInput, { addDaysInput = it.filter(Char::isDigit) }, "+ روز", Modifier.weight(.65f), KeyboardType.Number, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.accentPrimary.copy(.18f)).clickable { val add = addDaysInput.toIntOrNull() ?: 0; if (add > 0) { days = ((days.toIntOrNull() ?: 0) + add).toString(); addDaysInput = "" } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 18.dp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(7, 30, 60, 90).forEach { value -> MiniGlassButton("+$value روز", Modifier.weight(1f)) { days = ((days.toIntOrNull() ?: 0) + value).toString() } } }
                }
                // دسترسی و یادداشت
                Column(card(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("دسترسی و جزئیات", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactGlassField(if (hwid == "0" || hwid.isEmpty()) "" else hwid, { v -> hwid = v.filter(Char::isDigit).ifBlank { "0" } }, "محدودیت دستگاه", Modifier.weight(.52f), KeyboardType.Number, "", leadingAppIcon = AppIcon.Device, fieldHeight = 30.dp)
                        Box(Modifier.weight(.48f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(8.dp)).clickable { hwid = "0" }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text("نامحدود", fontSize = 9.sp, color = theme.mutedColor) }
                    }
                    Text("یادداشت داخلی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = if (theme.isDark) .06f else .70f)).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(12.dp)).padding(10.dp)) { BasicTextField(note, { note = it.take(500) }, textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp), modifier = Modifier.fillMaxSize()) }
                }
                // گروه‌ها
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("گروه‌ها", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (groups.isEmpty()) Text("گروهی یافت نشد", fontSize = 10.sp, color = theme.mutedColor) else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { groups.forEach { g -> val picked = groupIds.contains(g.id); Box(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (picked) theme.accentPrimary.copy(.78f) else theme.searchBgColor).border(BorderStroke(1.dp, if (picked) theme.searchBgColor else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(9.dp)).clickable { groupIds = if (picked) groupIds - g.id else groupIds + g.id }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (picked) RoundedAppIcon(AppIcon.Check, tint = Color(0xFF202124), size = 12.dp); Text(g.name, fontSize = 10.sp, color = if (picked) Color(0xFF202124) else theme.inkColor) } } } }
                }
                // تمپلت‌ها
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تمپلت‌ها", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (templates.isEmpty()) Text("تمپلتی یافت نشد", fontSize = 10.sp, color = theme.mutedColor) else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { templates.forEach { t -> val picked = selectedTemplate == t.id; Box(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (picked) theme.accentPrimary.copy(.78f) else theme.searchBgColor).border(BorderStroke(1.dp, if (picked) theme.searchBgColor else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(9.dp)).clickable {
                            selectedTemplate = t.id
                            // انتخاب تمپلت، مقادیر واقعی آن را فوراً در فیلدهای فرم نشان می‌دهد.
                            t.dataLimit?.let { limitGb = "%.2f".format(Locale.US, it / 1073741824.0).trimEnd('0').trimEnd('.') }
                            t.expireDuration?.let { days = (it / 86400L).toString() }
                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text(t.name, fontSize = 10.sp, color = if (picked) Color(0xFF202124) else theme.inkColor) } } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("انصراف", onDismiss, Modifier.weight(.35f))
                    PrimaryButton("ذخیرهٔ تغییرات", modifier = Modifier.weight(.65f), onClick = {
                        val expire = days.toIntOrNull()?.takeIf { it >= 0 }?.let { JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(it.toLong()).toString()) } ?: ""
                        val hwidValue = hwid.toIntOrNull() ?: 0
                        val values = UserEditorValues(username, limitGb.toDoubleOrNull() ?: 0.0, note, hwidValue, groupIds)
                        if (selectedTemplate != null && initial == null && onSaveWithTemplate != null) onSaveWithTemplate(username, selectedTemplate!!, note) else { onSave(values, expire); if (initial != null && active != (initial.status != "disabled")) onToggle?.invoke() }
                    })
                }
            }
        }
    }
    if (showCalendar) ShamsiCalendarPickerDialog(JalaliCalendar.todayJalali().toString(), { showCalendar = false }) { shamsi -> days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(JalaliCalendar.shamsiToIso(shamsi).take(10))).coerceAtLeast(0L).toString() }.getOrDefault("") }
    if (resetUsage) ConfirmActionDialog("ریست حجم مصرف‌شده؟", "مصرف این کاربر صفر می‌شود.", onDismiss = { resetUsage = false }, onConfirm = { resetUsage = false; onResetUsage?.invoke() })
}

@Composable
private fun detailDaysText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    return runCatching {
        val end = try { java.time.Instant.parse(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(expire.take(10)) }
        val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end)
        if (d < 0L) "منقضی" else "$d روز"
    }.getOrDefault("نامحدود")
}

/** دکمهٔ آیکون گرد برای کارت اشتراک و بخش‌های مشابه */
@Composable
private fun IconActionBtn(icon: AppIcon, contentDesc: String, theme: com.mrm.pgmanager.ui.theme.ThemeState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(0.8.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 16.dp)
    }
}

/** منوی کشویی کپسولی برای اکشن‌ها (بدهکار/فاکتور) - هماهنگ با design system */
@Composable
private fun CapsuleActionMenu(
    label: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    isDebtor: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalThemeState.current
    val headerColor = if (isDebtor) GlassRed else theme.accentPrimary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // دکمه سربرگ کشویی - هماهنگ با دکمه‌های کپسولی settings
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                .background(headerColor.copy(0.10f))
                .border(BorderStroke(1.2.dp, headerColor.copy(0.30f)), RoundedCornerShape(14.dp))
                .clickable { onToggleExpand() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedAppIcon(if (isDebtor) AppIcon.Warning else AppIcon.Money, tint = headerColor, size = 18.dp)
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = headerColor, modifier = Modifier.weight(1f))
                // فلش بالا/پایین: وقتی باز است به بالا، وقتی بسته است به پایین
                RoundedAppIcon(
                    AppIcon.Next,
                    tint = headerColor, size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) -90f else 90f }
                )
            }
        }
        // محتوای کشویی با انیمیشن
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(160))
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (theme.isDark) Color.White.copy(0.06f) else Color.White)
                    .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                content = actions
            )
        }
    }
}

/** یک ردیف دکمه کپسولی درون منوی کشویی */
@Composable
private fun CapsuleMenuItem(
    icon: AppIcon,
    label: String,
    accent: Color,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        primary -> accent.copy(0.78f)
        danger -> accent.copy(0.10f)
        else -> LocalThemeState.current.searchBgColor
    }
    val textColor = when {
        primary -> Color(0xFF202124)
        else -> accent
    }
    var borderColor = glassBorder(LocalThemeState.current.isDark, LocalThemeState.current.amoledDark)
    if (danger || primary) borderColor = accent.copy(if (primary) 0f else 0.30f)
    Box(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(icon, tint = textColor, size = 17.dp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
fun UserDetailsDialog(
    user: PanelUser,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onResetUsage: () -> Unit,
    onResetExpiry: (Int) -> Unit,
    onApplyTemplate: ((Int, String) -> Unit)? = null,
    session: com.mrm.pgmanager.data.model.Session? = null,
    debtorInfo: com.mrm.pgmanager.data.model.DebtorInfo? = null,
    onMarkDebtor: (() -> Unit)? = null,
    onClearDebt: (() -> Unit)? = null,
    onInvoice: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUser by remember(user) { mutableStateOf(user) }
    var editOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    // دریافت لینک اشتراک به‌صورت lazy (لیست کاربران بدون load_sub واکشی می‌شود تا به پنل فشار نیاید).
    fun ensureSub(onResult: (String) -> Unit) {
        if (currentUser.subUrl.isNotBlank()) {
            onResult(currentUser.subUrl)
        } else if (session != null) {
            scope.launch {
                runCatching { PanelApi.user(session, currentUser.username) }.onSuccess {
                    currentUser = it
                    onResult(it.subUrl)
                }.onFailure {
                    android.widget.Toast.makeText(context, "دریافت لینک اشتراک ناموفق بود", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            onResult(currentUser.subUrl)
        }
    }
    var usageConfirm by remember { mutableStateOf(false) }
    var expiryConfirm by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var availableTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesFailed by remember { mutableStateOf(false) }
    // وضعیت باز/بسته بودن منوی کشویی بدهی/فاکتور
    var debtorMenuExpanded by remember { mutableStateOf(false) }
    val traffic = if (currentUser.dataLimit == 0L) "نامحدود" else formatBytes(currentUser.dataLimit)
    val percentage = if (currentUser.dataLimit > 0L) ((currentUser.usedTraffic * 100f / currentUser.dataLimit).toInt()).coerceIn(0, 100) else 0
    val progressColor = when { percentage < 70 -> GlassGreen; percentage < 90 -> GlassAmber; else -> GlassRed }

    fun section() = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Color.White.copy(alpha = if (theme.isDark) .075f else .58f))
        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(20.dp)).padding(15.dp)

    @Composable fun sectionTitle(text: String) = Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
    @Composable fun statTile(label: String, value: String, modifier: Modifier = Modifier) {
        Column(modifier.height(54.dp).clip(RoundedCornerShape(10.dp)).background(if (theme.isDark) Color.White.copy(.07f) else Color.Black.copy(.035f)).padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 8.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, modifier = Modifier.offset(y = (-2).dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    // دکمهٔ اکشن دیالوگ جزئیات: primary = کپسول اکسنت ۷۸٪ + متن تیره (بدون مرز)، neutral = کاشی خاکستری، destructive = قرمز کم‌رنگ.
    @Composable fun action(text: String, modifier: Modifier = Modifier, destructive: Boolean = false, primary: Boolean = false, height: androidx.compose.ui.unit.Dp = 44.dp, click: () -> Unit) {
        val bg = when { primary -> theme.accentPrimary.copy(.78f); destructive -> GlassRed.copy(.10f); else -> theme.searchBgColor }
        val color = when { primary -> Color(0xFF202124); destructive -> GlassRed; else -> theme.inkColor }
        // حالت primary مرز نامرئی دارد (اکسنت با آلفای صفر) تا فقط پس‌زمینهٔ توپُر دیده شود؛ چیدمان ثابت می‌ماند.
        var borderColor = glassBorder(theme.isDark, theme.amoledDark)
        if (destructive) borderColor = GlassRed.copy(.30f)
        if (primary) borderColor = theme.accentPrimary.copy(0f)
        Box(
            modifier.height(height).clip(RoundedCornerShape(10.dp)).background(bg)
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(10.dp))
                .clickable(onClick = click),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontSize = if (height <= 30.dp) 9.sp else 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp))) {
            Column(Modifier.fillMaxWidth().padding(17.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("جزئیات کاربر", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

                // هدر کاربر عمداً فشرده است: فقط یک ردیف کوتاه برای هویت، فعالیت و وضعیت.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(.07f) else Color.White)
                        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (currentUser.isOnline) GlassGreen.copy(.14f) else Color.Gray.copy(.12f)), contentAlignment = Alignment.Center) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (currentUser.isOnline) GlassGreen else Color.Gray)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        MrmText(currentUser.username, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                        MrmText(lastSeenText(currentUser.onlineAt, currentUser.isOnline), fontSize = 8.sp, color = theme.mutedColor, maxLines = 1, isTechnical = true)
                    }
                    val active = currentUser.status != "disabled"
                    Box(Modifier.height(26.dp).width(50.dp).clip(RoundedCornerShape(8.dp)).background((if (active) GlassGreen else GlassRed).copy(.13f)), contentAlignment = Alignment.Center) { Text(if (active) "فعال" else "غیرفعال", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) GlassGreen else GlassRed) }
                }

                // توضیحات/یادداشت کاربر مستقیماً در پنجرهٔ جزئیات قابل مشاهده است.
                if (!currentUser.note.isNullOrBlank()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (theme.isDark) Color.White.copy(.06f) else Color(0xFFF6F6F8))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Note, tint = theme.mutedColor, size = 16.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("توضیحات", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            Text(currentUser.note.orEmpty(), fontSize = 11.sp, color = theme.inkColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // سه آمار ضروری در یک ردیف؛ محدودیت دستگاه از این نمای خلاصه حذف شده است.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (theme.isDark) Color.White.copy(.075f) else Color.White)
                        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("وضعیت اشتراک", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        statTile("مصرف‌شده", formatBytes(currentUser.usedTraffic), Modifier.weight(1f))
                        statTile("حجم کل", traffic, Modifier.weight(1f))
                        statTile("زمان باقی‌مانده", detailDaysText(currentUser.expire), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("مصرف", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(.18f))) { Box(Modifier.fillMaxWidth(percentage / 100f).fillMaxHeight().background(progressColor, RoundedCornerShape(4.dp))) }
                        Text("$percentage%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = progressColor)
                    }
                }

                // کارت اشتراک - فقط آیکون‌ها بدون متن
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (theme.isDark) Color.White.copy(.075f) else Color.White)
                        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("اشتراک", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    // دکمه‌های آیکون کپی و QR (لینک اشتراک در صورت نبود، lazy دریافت می‌شود)
                    IconActionBtn(AppIcon.Copy, "کپی", theme, Modifier.size(32.dp)) {
                        ensureSub { url ->
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", url))
                            android.widget.Toast.makeText(context, "لینک اشتراک کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    IconActionBtn(AppIcon.Qr, "QR", theme, Modifier.size(32.dp)) { ensureSub { _ -> qrOpen = true } }
                }

                // === بخش بدهی/فاکتور: منوی کشویی یکپارچه ===
                if (debtorInfo != null) {
                    CapsuleActionMenu(
                        label = "بخش مالی · ${debtorInfo.amount} ${debtorInfo.currency}",
                        expanded = debtorMenuExpanded,
                        onToggleExpand = { debtorMenuExpanded = !debtorMenuExpanded },
                        isDebtor = true
                    ) {
                        // اطلاعات بدهی
                        Text(
                            "از ${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(debtorInfo.markedAt))}" +
                                    if (debtorInfo.notes.isNotBlank()) " - ${debtorInfo.notes}" else " - بدون یادداشت",
                            fontSize = 10.sp, color = theme.mutedColor
                        )
                        if (debtorInfo.autoDisabled) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(GlassRed.copy(0.14f)).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 14.dp)
                                Text("به صورت خودکار به دلیل بدهی قطع شده است", fontSize = 9.sp, color = GlassRed, fontWeight = FontWeight.Bold)
                            }
                        }
                        CapsuleMenuItem(AppIcon.CheckCircle, "تسویه بدهی", GlassGreen, primary = true) { onClearDebt?.invoke() }
                        CapsuleMenuItem(AppIcon.Edit, "ویرایش بدهی", GlassRed, danger = true) { onMarkDebtor?.invoke() }
                        CapsuleMenuItem(AppIcon.Receipt, "صدور فاکتور", theme.accentPrimary) { onInvoice?.invoke() }
                    }
                } else {
                    CapsuleActionMenu(
                        label = "بخش مالی",
                        expanded = debtorMenuExpanded,
                        onToggleExpand = { debtorMenuExpanded = !debtorMenuExpanded }
                    ) {
                        CapsuleMenuItem(AppIcon.Warning, "ثبت بدهکار", GlassRed, danger = true) { onMarkDebtor?.invoke() }
                        CapsuleMenuItem(AppIcon.Receipt, "صدور فاکتور", theme.accentPrimary) { onInvoice?.invoke() }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    action("تمپلت‌ها", Modifier.weight(1f)) { templatePickerOpen = true }
                    action("ویرایش کاربر", Modifier.weight(2f), primary = true) { editOpen = true }
                }

                Column(section(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sectionTitle("عملیات سریع")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        action("ریست حجم", Modifier.weight(1f)) { usageConfirm = true }
                        action("ریست زمان", Modifier.weight(1f)) { expiryConfirm = true }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        action(if (currentUser.status == "disabled") "فعال‌کردن" else "غیرفعال‌کردن", Modifier.weight(1f)) { onToggle() }
                        action("حذف کاربر", Modifier.weight(1f), destructive = true) { onDelete() }
                    }
                }
                // دکمه بستن در پایین پنجره
                SecondaryButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (templatePickerOpen) {
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            val result = runCatching { session?.let { PanelApi.userTemplates(it) } ?: emptyList() }
            availableTemplates = result.getOrDefault(emptyList())
            templatesFailed = result.isFailure
            templatesLoading = false
        }
        BulkApplyTemplateDialog(
            templates = availableTemplates,
            selectedCount = 1,
            onDismiss = { templatePickerOpen = false },
            onApply = { templateId, note -> templatePickerOpen = false; onApplyTemplate?.invoke(templateId, note) },
            isLoading = templatesLoading,
            loadFailed = templatesFailed
        )
    }
    if (editOpen) UserEditorDialog(currentUser, { editOpen = false }, onSave, onToggle, onDelete, onResetUsage, { expiryConfirm = true }, onApplyTemplateToUser = onApplyTemplate, session = session)
    if (qrOpen) SubscriptionQrDialog(currentUser, { qrOpen = false })
    if (usageConfirm) ConfirmActionDialog("ریست حجم مصرف‌شده؟", "مصرف این کاربر صفر می‌شود.", onDismiss = { usageConfirm = false }, onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0L); onResetUsage() })
    if (expiryConfirm) ResetExpiryDurationDialog(onDismiss = { expiryConfirm = false }, onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) })
}

@Composable
fun BulkApplyTemplateDialog(
    templates: List<UserTemplateItem>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApply: (templateId: Int, note: String) -> Unit,
    isLoading: Boolean = false,
    loadFailed: Boolean = false
) {
    val theme = LocalThemeState.current
    var selectedTemplateId by remember { mutableStateOf<Int?>(templates.firstOrNull()?.id) }
    var note by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(GlassShape).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), GlassShape).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("اعمال تمپلت روی $selectedCount کاربر انتخابی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                Text("یک تمپلت آماده انتخاب کنید تا تنظیمات آن روی هر $selectedCount کاربر انتخابی اعمال شود:", color = theme.mutedColor, fontSize = 11.5.sp)

                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = theme.accentPrimary)
                        Text("در حال بارگذاریِ تمپلت‌ها...", fontSize = 11.sp, color = theme.mutedColor)
                    }
                } else if (loadFailed) {
                    Text("خطا در بارگذاریِ تمپلت‌ها. دوباره امتحان کنید.", fontSize = 11.sp, color = GlassRed)
                } else if (templates.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        templates.forEach { t ->
                            val sel = selectedTemplateId == t.id
                            // ردیف انتخاب تمپلت: انتخاب‌شده = کپسول اکسنت ۷۸٪ + متن تیره، بقیه = کاشی خاکستری.
                            Box(
                                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) theme.accentPrimary.copy(.78f) else theme.searchBgColor)
                                    .border(BorderStroke(1.dp, if (sel) theme.searchBgColor else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp))
                                    .clickable { selectedTemplateId = t.id }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name, fontSize = 12.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Bold, color = if (sel) Color(0xFF202124) else theme.inkColor)
                                    if (sel) Text("انتخاب شد", fontSize = 10.sp, color = Color(0xFF202124), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Text("تمپلتی در پنل یافت نشد.", fontSize = 11.sp, color = GlassRed)
                }

                CompactGlassField(value = note, onValueChange = { note = it }, placeholder = "یادداشت اختیاری...", leading = "")

                formError?.let { Text(it, color = GlassRed, fontSize = 11.sp) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    PrimaryButton("اعمال تمپلت", onClick = {
                        if (selectedTemplateId == null) formError = "لطفاً یک تمپلت انتخاب کنید"
                        else onApply(selectedTemplateId!!, note)
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
