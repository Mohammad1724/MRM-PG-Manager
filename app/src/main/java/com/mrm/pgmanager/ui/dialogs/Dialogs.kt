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
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassShape
import kotlin.math.roundToInt
import com.mrm.pgmanager.ui.theme.LocalThemeState
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

@Composable
fun ThemeEditorDialog(
    themeState: com.mrm.pgmanager.ui.theme.ThemeState,
    isAppLockEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onThemeChange: (com.mrm.pgmanager.ui.theme.ThemeState) -> Unit,
    onAppLockChange: (Boolean) -> Unit = {},
    monitoringSettings: com.mrm.pgmanager.data.model.MonitoringSettings = com.mrm.pgmanager.data.model.MonitoringSettings(),
    onMonitoringChange: (com.mrm.pgmanager.data.model.MonitoringSettings) -> Unit = {},
    appVersion: String = ""
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 22.dp); Text("تنظیمات", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = theme.inkColor) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeToggleBtn("روشن", AppIcon.LightMode, !themeState.followSystem && !themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(followSystem = false, isDark = false)) }
                    ModeToggleBtn("تیره", AppIcon.DarkMode, !themeState.followSystem && themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(followSystem = false, isDark = true)) }
                    ModeToggleBtn("خودکار", AppIcon.AutoMode, themeState.followSystem, Modifier.weight(1f)) { onThemeChange(themeState.copy(followSystem = true)) }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (theme.isDark) Color.White.copy(.06f) else Color(0xFFF7F7F8)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
