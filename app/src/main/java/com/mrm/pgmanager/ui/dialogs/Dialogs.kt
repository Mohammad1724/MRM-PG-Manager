package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
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
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LampColor
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import kotlin.math.roundToInt
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.lastSeenText
import com.mrm.pgmanager.utils.formatBytes
import java.util.Locale
import java.time.LocalDate

/** رنگ خاکستریِ واضح برای کادرِ کاشی‌ها (تمایز بهتر در حالت روشن/تیره). */
private fun tileBorderColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF606068) else Color(0xFF9C978C)

/** دیالوگ کوچکِ تأییدِ عملیات (مثل ریست حجم/زمان و عملیات گروهی). */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "تایید",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = theme.inkColor)
                Text(message, fontSize = 12.sp, color = theme.mutedColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f).height(40.dp))
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(GlassRed).clickable { onConfirm() }, contentAlignment = Alignment.Center) {
                        Text(confirmLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    onDelete: () -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(18.dp)).padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(if (user.isOnline) GlassGreen.copy(.14f) else Color.Gray.copy(.12f)), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (user.isOnline) GlassGreen else Color.Gray)) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text(user.username, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(lastSeenText(user.onlineAt, user.isOnline), fontSize = 9.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor) }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary.copy(.14f)).padding(horizontal = 7.dp, vertical = 4.dp)) { Text(if (user.status == "disabled") "غیرفعال" else "فعال", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuickActionRow(AppIcon.Template, "تمپلت", theme.lamp.primary, Modifier.weight(1f)) { onUseTemplate(); onDismiss() }
                    QuickActionRow(AppIcon.Edit, "ویرایش", theme.inkColor, Modifier.weight(1f)) { onEdit(); onDismiss() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuickActionRow(AppIcon.Reset, "ریست حجم", theme.lamp.primary, Modifier.weight(1f)) { onResetUsage(); onDismiss() }
                    QuickActionRow(AppIcon.Calendar, "ریست زمان", theme.lamp.primary, Modifier.weight(1f)) { onResetExpiry(); onDismiss() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuickActionRow(AppIcon.Copy, "کپی لینک", theme.inkColor, Modifier.weight(1f)) { onCopySub(); onDismiss() }
                    QuickActionRow(AppIcon.Qr, "نمایش QR", theme.inkColor, Modifier.weight(1f)) { onQr(); onDismiss() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuickActionRow(AppIcon.User, if (user.status == "disabled") "فعال‌سازی" else "غیرفعال‌سازی", theme.inkColor, Modifier.weight(1f)) { onToggle(); onDismiss() }
                    QuickActionRow(AppIcon.Delete, "حذف کاربر", GlassRed, Modifier.weight(1f)) { onDelete(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(icon: AppIcon, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(.10f)).border(BorderStroke(1.dp, color.copy(.22f)), RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundedAppIcon(icon, tint = color, size = 16.dp)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

// === پنجرهٔ تنظیمات v2 — بازطراحی کامل بر پایهٔ design system اصلی برنامه ===
// ساختار: هدر + تب‌های سگمنت‌شده + کارت‌های استانداردِ هم‌سبک با کارت‌های داشبورد.

/** ردیف سوئیچ استاندارد تنظیمات: عنوان + توضیح اختیاری + Switch. */
@Composable
private fun SettingsSwitchRow(
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
private fun SettingsStepper(
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
                .background(if (enabled) theme.lamp.primary.copy(.18f) else theme.searchBgColor)
                .clickable(enabled = enabled) { onChange((value - step).coerceIn(range)) },
            contentAlignment = Alignment.Center
        ) { Text("−", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
        Box(
            Modifier.width(66.dp).height(30.dp).clip(RoundedCornerShape(9.dp))
                .background(theme.searchBgColor)
                .border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) { Text("$value $unit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (enabled) theme.lamp.primary.copy(.18f) else theme.searchBgColor)
                .clickable(enabled = enabled) { onChange((value + step).coerceIn(range)) },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
    }
}

/** کنترل سگمنت‌شدهٔ هم‌سبک با تب‌های شناور پایین برنامه (accent + متن تیره روی گزینهٔ فعال). */
@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    icons: List<AppIcon> = emptyList(),
    onSelect: (Int) -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(13.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) theme.lamp.primary.copy(.78f) else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (icons.getOrNull(index) != null) RoundedAppIcon(icons[index], tint = if (selected) Color(0xFF202124) else theme.mutedColor, size = 14.dp)
                    Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (selected) Color(0xFF202124) else theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** کارت استاندارد هر بخش تنظیمات؛ همان surface خنثی + border ظریفِ کارت‌های داشبورد. */
@Composable
private fun SettingsCard(
    title: String,
    icon: AppIcon,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalThemeState.current
    val ac = accent ?: theme.lamp.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (theme.isDark) Color(0xFF202128) else Color.White)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(ac.copy(.16f)), contentAlignment = Alignment.Center) {
                RoundedAppIcon(icon, tint = theme.inkColor, size = 15.dp)
            }
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
        }
        content()
    }
}

/** ردیف اکشن رنگی با آیکون (خروج از حساب، بازنشانی و ...). */
@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    icon: AppIcon,
    accent: Color,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(accent.copy(.10f))
            .border(BorderStroke(1.dp, accent.copy(.26f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(.16f)), contentAlignment = Alignment.Center) {
            RoundedAppIcon(icon, tint = accent, size = 16.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
            if (subtitle != null) Text(subtitle, fontSize = 9.5.sp, color = theme.mutedColor)
        }
    }
}

/** ردیف اطلاعات فقط‌خواندنی با قابلیت کپی (آدرس پنل / نام کاربری). */
@Composable
private fun SettingsInfoRow(label: String, value: String, copyable: Boolean = false) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 8.5.sp, color = theme.mutedColor)
            Text(value, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (copyable) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary.copy(.16f))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                        android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Copy, tint = theme.inkColor, size = 13.dp) }
        }
    }
}

/** آیتم انتخاب رنگ اکسنت با پیش‌نمایش گرادیانی و تیک هنگام انتخاب. */
@Composable
private fun LampColorItem(lamp: LampColor, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Row(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) lamp.primary.copy(.12f) else Color.Transparent)
            .border(BorderStroke(if (selected) 1.4.dp else 1.dp, if (selected) lamp.primary.copy(.7f) else glassBorder(theme.isDark)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(lamp.primary, lamp.light)))
                .border(BorderStroke(1.dp, lamp.primary.copy(.5f)), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { if (selected) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 16.dp) }
        Text(lamp.labelFa, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onLogout: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf("ظاهر") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    // در صفحهٔ ورود (بدون نشست) فقط تنظیمات ظاهری معنا دارد؛ بقیهٔ تب‌ها پنهان می‌مانند.
    val sections = remember(session) { if (session == null) listOf("ظاهر") else listOf("ظاهر", "پایش", "اعلان‌ها", "اتصال", "امنیت") }

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
                            .background(theme.lamp.primary.copy(.16f))
                            .border(BorderStroke(1.dp, theme.lamp.primary.copy(.32f)), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text("تنظیمات", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text("شخصی‌سازی، پایش و امنیت حساب مدیر", fontSize = 10.sp, color = theme.mutedColor)
                    }
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (theme.isDark) Color.White.copy(.08f) else Color.Black.copy(.05f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("×", fontSize = 21.sp, color = theme.mutedColor) }
                }
                // تب بخش‌ها به‌صورت سگمنت یکدست (اگر فقط یک بخش باشد، مخفی می‌ماند)
                if (sections.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                            .background(theme.searchBgColor)
                            .border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(13.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sections.forEach { label ->
                            val selected = section == label
                            Box(
                                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) theme.lamp.primary.copy(.78f) else Color.Transparent)
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
                                            val selected = themeState.lamp == lamp
                                            LampColorItem(lamp = lamp, selected = selected, modifier = Modifier.weight(1f)) { onThemeChange(themeState.copy(lamp = lamp)) }
                                        }
                                        if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
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
                            SettingsCard("سلامت سیستم و اتصال", AppIcon.Warning, accent = GlassRed) {
                                val healthEnabled = master && monitoringSettings.notifySystemHealth
                                SettingsSwitchRow("هشدار سلامت سیستم", "مصرف غیرعادی CPU، RAM یا Disk پنل", monitoringSettings.notifySystemHealth, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifySystemHealth = it)) }
                                SettingsStepper("آستانهٔ CPU", monitoringSettings.cpuThreshold, "٪", 50..100, step = 5, enabled = healthEnabled) { onMonitoringChange(monitoringSettings.copy(cpuThreshold = it)) }
                                SettingsStepper("آستانهٔ RAM", monitoringSettings.ramThreshold, "٪", 50..100, step = 5, enabled = healthEnabled) { onMonitoringChange(monitoringSettings.copy(ramThreshold = it)) }
                                SettingsStepper("آستانهٔ Disk", monitoringSettings.diskThreshold, "٪", 50..100, step = 5, enabled = healthEnabled) { onMonitoringChange(monitoringSettings.copy(diskThreshold = it)) }
                                SettingsSwitchRow("قطع اتصال پنل", checked = monitoringSettings.notifyPanelOffline, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyPanelOffline = it)) }
                                SettingsSwitchRow("قطع اتصال نود", checked = monitoringSettings.notifyNodeOffline, enabled = master) { onMonitoringChange(monitoringSettings.copy(notifyNodeOffline = it)) }
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
                                                    onSuccess = { s -> true to "اتصال برقرار است · آپ‌تایم ${s.uptimeSeconds / 86400} روز و ${(s.uptimeSeconds % 86400) / 3600} ساعت" },
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
                GlassButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(44.dp))
            }
        }
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
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("QR ${user.username}", fontWeight = FontWeight.Bold, color = theme.inkColor)
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text("QR خطا", fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton("کپی", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                        android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f))
                    PrimarySaveButton("اشتراک", onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, user.subUrl) }
                        context.startActivity(Intent.createChooser(i, "اشتراک"))
                    }, modifier = Modifier.weight(1f))
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
    val daysInMonth = when { m in 1..6 -> 31; m in 7..11 -> 30; else -> if (y % 4 == 3) 30 else 29 }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp)).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("تقویم", fontWeight = FontWeight.Bold, color = theme.inkColor)
                    TextButton(onClick = { y = today.year; m = today.month; d = today.day }) { Text("امروز", color = theme.lamp.primary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { if (m > 1) m-- else { m = 12; y-- } }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.14f))) { RoundedAppIcon(AppIcon.Prev, tint = theme.inkColor, size = 18.dp) }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.08f)).padding(8.dp), contentAlignment = Alignment.Center) { Text("${JalaliCalendar.Date(y, m, 1).getMonthName()} $y", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Button(onClick = { if (m < 12) m++ else { m = 1; y++ } }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.14f))) { RoundedAppIcon(AppIcon.Next, tint = theme.inkColor, size = 18.dp) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(200.dp)) {
                    items((1..daysInMonth).toList()) { day ->
                        val sel = day == d
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) theme.lamp.primary else Color.White.copy(0.08f)).clickable { d = day }, contentAlignment = Alignment.Center) {
                            Text("$day", color = if (sel) Color.White else theme.inkColor, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    PrimarySaveButton("تایید", onClick = { onDateSelected(JalaliCalendar.Date(y, m, d).toString()); onDismiss() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Small compact field for dialog - fixes half number issue
@Composable
private fun CompactGlassField(
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
fun UserEditorDialog(
    initial: PanelUser?, onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?, onDelete: (() -> Unit)?, onResetUsage: (() -> Unit)?, onResetExpiry: (() -> Unit)?,
    onSaveWithTemplate: ((username: String, templateId: Int, note: String) -> Unit)? = null,
    onApplyTemplateToUser: ((templateId: Int, note: String) -> Unit)? = null,
    session: com.mrm.pgmanager.data.model.Session? = null
) {
    val theme = LocalThemeState.current
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    // «زمان کل» از فاصلهٔ تاریخ ساخت تا تاریخ انقضا محاسبه می‌شود؛ نه زمان باقی‌مانده تا امروز.
    var days by remember { mutableStateOf(runCatching {
        initial?.let { user ->
            val expires = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
            val created = try { java.time.Instant.parse(user.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(user.createdAt?.take(10) ?: "") }
            java.time.temporal.ChronoUnit.DAYS.between(created, expires).coerceAtLeast(0).toString()
        } ?: ""
    }.getOrDefault("")) }
    // ورودی‌های افزایشی؛ مقدار نهایی حجم/زمان جدا نگه داشته می‌شود تا با +GB و +روز جمع شود.
    var addGb by remember { mutableStateOf("") }
    var addDaysInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hwid by remember { mutableStateOf(initial?.hwidLimit?.toString() ?: "") }
    var groupIds by remember { mutableStateOf(initial?.groupIds ?: emptyList()) }
    var groups by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.Group>>(emptyList()) }
    var templates by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.UserTemplateItem>>(emptyList()) }
    var active by remember { mutableStateOf(initial?.status != "disabled") }
    var selectedTemplate by remember { mutableStateOf<Int?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var resetUsage by remember { mutableStateOf(false) }
    var resetExpiry by remember { mutableStateOf(false) }

    LaunchedEffect(session) { if (session != null) {
        groups = runCatching { com.mrm.pgmanager.data.api.PanelApi.groups(session) }.getOrDefault(emptyList())
        templates = runCatching { com.mrm.pgmanager.data.api.PanelApi.userTemplates(session) }.getOrDefault(emptyList())
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Edit, tint = theme.inkColor, size = 18.dp, modifier = Modifier.padding(end = 6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (initial == null) "ایجاد کاربر" else "ویرایش کاربر", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    }
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(if (theme.isDark) .10f else .04f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("×", fontSize = 21.sp, color = theme.mutedColor) }
                }
                // اطلاعات پایه
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اطلاعات پایه", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (initial == null) {
                            // هنگام ساخت کاربر، تولید نام تصادفی دوباره در دسترس است.
                            CompactGlassField(username, { username = it }, "نام کاربری", Modifier.weight(1f), KeyboardType.Ascii, "")
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(theme.lamp.primary.copy(.16f)).border(BorderStroke(1.dp, theme.lamp.primary.copy(.35f)), RoundedCornerShape(10.dp)).clickable { username = "user-" + (1000..9999).random() }, contentAlignment = Alignment.Center) {
                                RoundedAppIcon(AppIcon.Random, tint = theme.inkColor, size = 19.dp)
                            }
                        } else {
                            // در حالت ویرایش، نام مانند پنل PasarGuard فقط برای مشاهده است.
                            // نام در حالت ویرایش فقط نمایش داده می‌شود و عمداً بسیار کوتاه است.
                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(7.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                                Text(initial.username, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary.copy(.18f)).clickable { val add = addGb.toDoubleOrNull() ?: 0.0; if (add > 0) { limitGb = ((limitGb.toDoubleOrNull() ?: 0.0) + add).toString(); addGb = "" } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 18.dp) }
                    }
                    // زمان کل نیز مستقل قابل ویرایش است و +روز به مقدار فعلی افزوده می‌شود.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactGlassField(days, { days = it.filter(Char::isDigit) }, "زمان کل", Modifier.weight(1.15f), KeyboardType.Number, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary.copy(.14f)).clickable { showCalendar = true }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Calendar, tint = theme.inkColor, size = 18.dp) }
                        CompactGlassField(addDaysInput, { addDaysInput = it.filter(Char::isDigit) }, "+ روز", Modifier.weight(.65f), KeyboardType.Number, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary.copy(.18f)).clickable { val add = addDaysInput.toIntOrNull() ?: 0; if (add > 0) { days = ((days.toIntOrNull() ?: 0) + add).toString(); addDaysInput = "" } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 18.dp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(7, 30, 60, 90).forEach { value -> MiniGlassButton("+$value روز", Modifier.weight(1f)) { days = ((days.toIntOrNull() ?: 0) + value).toString() } } }
                }
                // دسترسی و یادداشت
                Column(card(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("دسترسی و جزئیات", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactGlassField(hwid, { hwid = it.filter(Char::isDigit) }, "محدودیت دستگاه", Modifier.weight(.52f), KeyboardType.Number, "", leadingAppIcon = AppIcon.Device, fieldHeight = 30.dp)
                        Box(Modifier.weight(.48f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(.05f)).clickable { hwid = "" }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text("نامحدود", fontSize = 9.sp, color = theme.mutedColor) }
                    }
                    Text("یادداشت داخلی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = if (theme.isDark) .06f else .70f)).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(12.dp)).padding(10.dp)) { BasicTextField(note, { note = it.take(500) }, textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp), modifier = Modifier.fillMaxSize()) }
                }
                // گروه‌ها
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("گروه‌ها", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (groups.isEmpty()) Text("گروهی یافت نشد", fontSize = 10.sp, color = theme.mutedColor) else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { groups.forEach { g -> val picked = groupIds.contains(g.id); Box(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (picked) theme.lamp.primary.copy(.18f) else Color.Black.copy(.05f)).clickable { groupIds = if (picked) groupIds - g.id else groupIds + g.id }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (picked) RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 12.dp); Text(g.name, fontSize = 10.sp, color = theme.inkColor) } } } }
                }
                // تمپلت‌ها
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تمپلت‌ها", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (templates.isEmpty()) Text("تمپلتی یافت نشد", fontSize = 10.sp, color = theme.mutedColor) else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { templates.forEach { t -> val picked = selectedTemplate == t.id; Box(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (picked) theme.lamp.primary.copy(.18f) else Color.Black.copy(.05f)).clickable {
                            selectedTemplate = t.id
                            // انتخاب تمپلت، مقادیر واقعی آن را فوراً در فیلدهای فرم نشان می‌دهد.
                            t.dataLimit?.let { limitGb = "%.2f".format(Locale.US, it / 1073741824.0).trimEnd('0').trimEnd('.') }
                            t.expireDuration?.let { days = (it / 86400L).toString() }
                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text(t.name, fontSize = 10.sp, color = theme.inkColor) } } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedCancelButton("انصراف", onDismiss, Modifier.weight(.35f))
                    PrimarySaveButton("ذخیرهٔ تغییرات", modifier = Modifier.weight(.65f), onClick = {
                        val expire = days.toIntOrNull()?.takeIf { it >= 0 }?.let { JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(it.toLong()).toString()) } ?: ""
                        val values = UserEditorValues(username, limitGb.toDoubleOrNull() ?: 0.0, note, hwid.toIntOrNull(), groupIds)
                        if (selectedTemplate != null && initial == null && onSaveWithTemplate != null) onSaveWithTemplate(username, selectedTemplate!!, note) else { onSave(values, expire); if (initial != null && active != (initial.status != "disabled")) onToggle?.invoke() }
                    })
                }
            }
        }
    }
    if (showCalendar) ShamsiCalendarPickerDialog(JalaliCalendar.todayJalali().toString(), { showCalendar = false }) { shamsi -> days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(JalaliCalendar.shamsiToIso(shamsi).take(10))).coerceAtLeast(0).toString() }.getOrDefault("") }
    if (resetUsage) ConfirmActionDialog("ریست حجم مصرف‌شده؟", "مصرف این کاربر صفر می‌شود.", onDismiss = { resetUsage = false }, onConfirm = { resetUsage = false; onResetUsage?.invoke() })
    if (resetExpiry) ConfirmActionDialog("ریست زمان اشتراک؟", "زمان اشتراک نامحدود می‌شود.", onDismiss = { resetExpiry = false }, onConfirm = { resetExpiry = false; onResetExpiry?.invoke() })
}

private fun detailDaysText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    return runCatching {
        val end = try { java.time.Instant.parse(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(expire.take(10)) }
        val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end)
        if (d < 0) "منقضی" else "$d روز"
    }.getOrDefault("نامحدود")
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
    session: com.mrm.pgmanager.data.model.Session? = null
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    var currentUser by remember(user) { mutableStateOf(user) }
    var editOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    var usageConfirm by remember { mutableStateOf(false) }
    var expiryConfirm by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var availableTemplates by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesFailed by remember { mutableStateOf(false) }
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
    @Composable fun action(text: String, modifier: Modifier = Modifier, destructive: Boolean = false, primary: Boolean = false, height: androidx.compose.ui.unit.Dp = 44.dp, click: () -> Unit) {
        val bg = when { primary -> theme.lamp.primary; destructive -> GlassRed.copy(.09f); else -> if (theme.isDark) Color.White.copy(.08f) else Color.White.copy(.68f) }
        val color = when { primary -> Color.White; destructive -> GlassRed; else -> theme.inkColor }
        val border = when { primary -> theme.lamp.primary; destructive -> GlassRed.copy(.45f); else -> tileBorderColor(theme.isDark) }
        Box(modifier.height(height).clip(RoundedCornerShape(10.dp)).background(bg).border(BorderStroke(1.dp, border), RoundedCornerShape(10.dp)).clickable(onClick = click), contentAlignment = Alignment.Center) {
            Text(text, fontSize = if (height <= 30.dp) 9.sp else 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp))) {
            Column(Modifier.fillMaxWidth().padding(17.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("جزئیات کاربر", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(if (theme.isDark) .12f else .05f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Text("×", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor) }
                }

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
                        Text(currentUser.username, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(lastSeenText(currentUser.onlineAt, currentUser.isOnline), fontSize = 8.sp, color = theme.mutedColor, maxLines = 1)
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

                // کارت اشتراک یک ردیف فشرده است؛ توضیح تکراری حذف شده تا فقط اکشن‌های اصلی بمانند.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (theme.isDark) Color.White.copy(.075f) else Color.White)
                        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("اشتراک", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    action("کپی", Modifier.width(48.dp), height = 26.dp) { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", currentUser.subUrl)) }
                    action("QR", Modifier.width(38.dp), height = 26.dp) { qrOpen = true }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    action("تمپلت‌ها", Modifier.weight(1f)) { templatePickerOpen = true }
                    action("تنظیمات کاربر", Modifier.weight(2f), primary = true) { editOpen = true }
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
            }
        }
    }
    if (templatePickerOpen) {
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            val result = runCatching { session?.let { com.mrm.pgmanager.data.api.PanelApi.userTemplates(it) } ?: emptyList() }
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
    if (qrOpen && currentUser.subUrl.isNotBlank()) SubscriptionQrDialog(user, { qrOpen = false })
    if (usageConfirm) ConfirmActionDialog("ریست حجم مصرف‌شده؟", "مصرف این کاربر صفر می‌شود.", onDismiss = { usageConfirm = false }, onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0); onResetUsage() })
    if (expiryConfirm) ResetExpiryDurationDialog(onDismiss = { expiryConfirm = false }, onConfirm = { days -> expiryConfirm = false; currentUser = currentUser.copy(expire = java.time.LocalDate.now().plusDays(days.toLong()).toString()); onResetExpiry(days) })
}

@Composable
fun BulkApplyTemplateDialog(
    templates: List<com.mrm.pgmanager.data.model.UserTemplateItem>,
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
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = theme.lamp.primary)
                        Text("در حال بارگذاریِ تمپلت‌ها...", fontSize = 11.sp, color = theme.mutedColor)
                    }
                } else if (loadFailed) {
                    Text("خطا در بارگذاریِ تمپلت‌ها. دوباره امتحان کنید.", fontSize = 11.sp, color = GlassRed)
                } else if (templates.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        templates.forEach { t ->
                            val sel = selectedTemplateId == t.id
                            Box(
                                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) theme.lamp.primary else Color.Black.copy(0.04f))
                                    .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.16f)), RoundedCornerShape(10.dp))
                                    .clickable { selectedTemplateId = t.id }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name, fontSize = 12.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Bold, color = if (sel) Color.White else theme.inkColor)
                                    if (sel) Text("انتخاب شد", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f).height(38.dp))
                    PrimarySaveButton("اعمال تمپلت", onClick = {
                        if (selectedTemplateId == null) formError = "لطفاً یک تمپلت انتخاب کنید"
                        else onApply(selectedTemplateId!!, note)
                    }, modifier = Modifier.weight(1f).height(38.dp))
                }
            }
        }
    }
}
