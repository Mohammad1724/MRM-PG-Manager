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
    onDelete: () -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⚡ ${user.username}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = theme.inkColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(user.status, fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    }
                    Text(lastSeenText(user.onlineAt, user.isOnline), fontSize = 9.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor)
                }
                QuickActionRow("📦", "استفاده از تمپلت (تمدید)", theme.lamp.primary) { onUseTemplate(); onDismiss() }
                QuickActionRow(if (user.status == "disabled") "🟢" else "⚪", if (user.status == "disabled") "فعال‌سازی" else "غیرفعال‌سازی", theme.inkColor) { onToggle(); onDismiss() }
                QuickActionRow("📋", "کپی ساب‌لینک", theme.inkColor) { onCopySub(); onDismiss() }
                QuickActionRow("📱", "نمایش QR", theme.inkColor) { onQr(); onDismiss() }
                QuickActionRow("✏️", "ویرایش کامل", theme.inkColor) { onEdit(); onDismiss() }
                QuickActionRow("🗑", "حذف کاربر", GlassRed) { onDelete(); onDismiss() }
                GlassButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuickActionRow(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, fontSize = 14.sp)
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = color)
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
    appVersion: String = ""
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⚙️ تنظیمات", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = theme.inkColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeToggleBtn("☀️ روشن", !themeState.followSystem && !themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(followSystem = false, isDark = false)) }
                    ModeToggleBtn("🌙 تیره", !themeState.followSystem && themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(followSystem = false, isDark = true)) }
                    ModeToggleBtn("🌗 خودکار", themeState.followSystem, Modifier.weight(1f)) { onThemeChange(themeState.copy(followSystem = true)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.mrm.pgmanager.ui.theme.LampColor.values().forEach { lamp ->
                        val sel = themeState.lamp == lamp
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) lamp.primary.copy(0.14f) else Color.Transparent)
                                .border(BorderStroke(1.dp, if (sel) lamp.primary else Color.White.copy(0.16f)), RoundedCornerShape(14.dp))
                                .clickable { onThemeChange(themeState.copy(lamp = lamp)) }.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(lamp.primary), contentAlignment = Alignment.Center) { Text(lamp.emoji, fontSize = 13.sp) }
                                Text(lamp.labelFa, color = theme.inkColor, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (sel) Text("✓", color = lamp.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (isAppLockEnabled) GlassGreen.copy(0.14f) else Color.White.copy(if (theme.isDark) 0.08f else 0.60f))
                        .border(BorderStroke(1.2.dp, if (isAppLockEnabled) GlassGreen else Color.White.copy(0.24f)), RoundedCornerShape(14.dp))
                        .clickable { onAppLockChange(!isAppLockEnabled) }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (isAppLockEnabled) GlassGreen else theme.lamp.primary.copy(0.18f)), contentAlignment = Alignment.Center) {
                                Text("👆", fontSize = 15.sp)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("🔒 قفل امنیتی برنامه", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                                Text("ورود با اثر انگشت یا پین/الگوی گوشی", fontSize = 9.5.sp, color = theme.mutedColor)
                            }
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (isAppLockEnabled) GlassGreen else Color.Gray.copy(0.20f)).padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (isAppLockEnabled) "فعال ✓" else "غیرفعال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isAppLockEnabled) Color.White else theme.inkColor)
                        }
                    }
                }
                // درباره
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("نسخهٔ برنامه", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    Text(appVersion.ifBlank { "—" }, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Bold)
                }
                GlassButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ModeToggleBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.lamp.primary else Color.White.copy(if (theme.isDark) 0.08f else 0.42f))
            .border(BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(0.22f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) Color.White else theme.inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                    Text("📅 تقویم", fontWeight = FontWeight.Bold, color = theme.inkColor)
                    TextButton(onClick = { y = today.year; m = today.month; d = today.day }) { Text("امروز", color = theme.lamp.primary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { if (m > 1) m-- else { m = 12; y-- } }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.14f))) { Text("◀", fontSize = 12.sp) }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.08f)).padding(8.dp), contentAlignment = Alignment.Center) { Text("${JalaliCalendar.Date(y, m, 1).getMonthName()} $y", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Button(onClick = { if (m < 12) m++ else { m = 1; y++ } }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.14f))) { Text("▶", fontSize = 12.sp) }
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
    leading: String = ""
) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.92f))
            .border(BorderStroke(1.dp, Color.White.copy(0.18f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (leading.isNotEmpty()) Text(leading, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
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
    initial: PanelUser?,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onResetUsage: (() -> Unit)?,
    onResetExpiry: (() -> Unit)?,
    onSaveWithTemplate: ((username: String, templateId: Int, note: String) -> Unit)? = null,
    onApplyTemplateToUser: ((templateId: Int, note: String) -> Unit)? = null,
    session: com.mrm.pgmanager.data.model.Session? = null
) {
    val theme = LocalThemeState.current
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    var dayField by remember {
        mutableStateOf(
            runCatching {
                val exp = initial?.expire
                if (exp.isNullOrBlank() || exp == "0" || exp == "null") ""
                else try {
                    // همان منطقِ کارت: مبتنی بر لحظهٔ زمانی و گردکردنِ رو‌به‌بالا
                    val diffSec = java.time.Instant.parse(exp).epochSecond - java.time.Instant.now().epochSecond
                    if (diffSec <= 0) "" else "${(diffSec + 86399L) / 86400L}"
                } catch (e: Exception) {
                    val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(exp.take(10)))
                    if (d >= 0) d.toString() else ""
                }
            }.getOrDefault("")
        )
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hwidLimit by remember { mutableStateOf(initial?.hwidLimit?.toString() ?: "") }
    var selectedGroupIds by remember { mutableStateOf(initial?.groupIds ?: emptyList()) }
    var allGroups by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.Group>>(emptyList()) }
    var allTemplates by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(true) }
    var templatesFailed by remember { mutableStateOf(false) }
    var isTemplateMode by remember { mutableStateOf(false) }
    var selectedTemplateId by remember { mutableStateOf<Int?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showResetUsageConfirm by remember { mutableStateOf(false) }
    var showResetExpiryConfirm by remember { mutableStateOf(false) }
    var addGbInput by remember { mutableStateOf("") }
    var addDayInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(session) {
        if (session != null) {
            allGroups = com.mrm.pgmanager.data.api.PanelApi.groups(session)
            templatesLoading = true; templatesFailed = false
            var list: List<com.mrm.pgmanager.data.model.UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { com.mrm.pgmanager.data.api.PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            if (list != null) {
                allTemplates = list
                if (allTemplates.isNotEmpty() && selectedTemplateId == null) selectedTemplateId = allTemplates.first().id
            } else {
                allTemplates = emptyList(); templatesFailed = true
            }
            templatesLoading = false
        }
    }

    fun addGb(amount: Double) {
        val current = limitGb.toDoubleOrNull() ?: 0.0
        val newVal = (current + amount).coerceAtLeast(0.0)
        limitGb = if (newVal == 0.0) "" else "%.2f".format(Locale.US, newVal).trimEnd('0').trimEnd('.')
    }
    fun addDays(days: Int) {
        val cur = dayField.toIntOrNull() ?: 0
        dayField = (cur + days).toString()
    }

    Dialog(onDismissRequest = onDismiss) {
        // Jelly glass outer - with lamp glow behind
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (theme.isDark) Color(0xFF1E1E24).copy(0.94f) else Color.White.copy(0.92f),
                            if (theme.isDark) Color(0xFF18181E).copy(0.92f) else Color(0xFFFFF7E6).copy(0.88f)
                        )
                    )
                )
                .border(BorderStroke(1.2.dp, Color.White.copy(0.42f)), RoundedCornerShape(26.dp))
                .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = theme.lamp.primary.copy(0.18f))
        ) {
            // Lamp glow behind
            Box(
                Modifier.size(280.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-60).dp)
                    .background(Brush.radialGradient(listOf(theme.lamp.spotHigh.copy(0.42f), theme.lamp.spotLow.copy(0.18f), Color.Transparent)), RoundedCornerShape(200.dp))
                    .blur(16.dp)
            )
            // Second glow bottom start
            Box(
                Modifier.size(200.dp).align(Alignment.BottomStart).offset(x = (-40).dp, y = 40.dp)
                    .background(Brush.radialGradient(listOf(theme.lamp.light.copy(0.22f), Color.Transparent)), RoundedCornerShape(200.dp))
                    .blur(20.dp)
            )

            Column(Modifier.fillMaxWidth().padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header small
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (initial == null) "کاربر جدید" else initial.username, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        if (initial != null) Text(initial.status, fontSize = 10.sp, color = theme.mutedColor)
                        if (initial != null) Text(lastSeenText(initial.onlineAt, initial.isOnline), fontSize = 9.sp, color = if (initial.isOnline) GlassGreen else theme.mutedColor)
                    }
                }

                if (initial == null && allTemplates.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeToggleBtn("🛠️ تنظیم دستی", !isTemplateMode, Modifier.weight(1f)) { isTemplateMode = false }
                        ModeToggleBtn("📦 ساخت از روی تمپلت", isTemplateMode, Modifier.weight(1f)) { isTemplateMode = true }
                    }
                }

                if (initial == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            CompactGlassField(value = username, onValueChange = { username = it }, placeholder = "نام کاربری (۳ تا ۳۲ حرف/عدد)", leading = "👤", keyboardType = KeyboardType.Ascii)
                        }
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(theme.lamp.primary.copy(alpha = 0.16f))
                                .border(BorderStroke(1.2.dp, theme.lamp.primary.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                                .clickable {
                                    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
                                    val prefix = listOf("user", "vip", "sub", "net", "pro").random()
                                    val randomStr = (1..6).map { chars.random() }.joinToString("")
                                    username = "${prefix}_$randomStr"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔄", fontSize = 16.sp)
                        }
                    }
                }

                if (isTemplateMode && initial == null) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.12f else 0.88f))
                            .border(BorderStroke(1.dp, Color.White.copy(0.20f)), RoundedCornerShape(14.dp)).padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📦 انتخاب تمپلت آماده (از پنل):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            if (allTemplates.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                    allTemplates.forEach { t ->
                                        val sel = selectedTemplateId == t.id
                                        Box(
                                            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp))
                                                .background(if (sel) theme.lamp.primary else Color.Black.copy(0.04f))
                                                .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.16f)), RoundedCornerShape(9.dp))
                                                .clickable { selectedTemplateId = t.id }.padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(t.name, fontSize = 12.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Bold, color = if (sel) Color.White else theme.inkColor)
                                                if (sel) Text("✓ انتخاب شد", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (templatesLoading) Text("⏳ در حال بارگذاری...", fontSize = 10.sp, color = theme.mutedColor) else if (templatesFailed) Text("⚠️ خطا در بارگذاری. دوباره امتحان کنید.", fontSize = 10.sp, color = GlassRed) else Text("تمپلتی یافت نشد. تمپلت‌ها را در پنل اصلی پاسارگارد بسازید.", fontSize = 10.sp, color = theme.mutedColor)
                            }
                        }
                    }

                    // ── کاشی یادداشت (تمپلت) ──
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.82f))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("📝", fontSize = 12.sp)
                                    Text("توضیحات / یادداشت (Note):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                }
                                Text("${note.length}/500", fontSize = 9.5.sp, color = theme.mutedColor)
                            }
                            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.10f)), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                if (note.isEmpty()) Text("یادداشت اختیاری برای این کاربر...", color = theme.mutedColor.copy(0.5f), fontSize = 11.sp)
                                BasicTextField(
                                    value = note,
                                    onValueChange = { if (it.length <= 500) note = it },
                                    textStyle = TextStyle(color = theme.inkColor, fontSize = 11.5.sp),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                } else {
                    // ── کاشی حجم ──
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.86f))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("💾 حجم", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            if (initial != null) {
                                val limitBytes = (limitGb.toDoubleOrNull() ?: 0.0) * 1073741824.0
                                val usedBytes = initial.usedTraffic
                                val progress = if (limitBytes > 0.0) (usedBytes.toDouble() / limitBytes).coerceIn(0.0, 1.0).toFloat() else 0f
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (limitBytes > 0.0) "${formatBytes(usedBytes)} از ${formatBytes(limitBytes.toLong())} مصرف شده" else "${formatBytes(usedBytes)} مصرف شده (نامحدود)", fontSize = 9.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(if (limitBytes > 0.0) "${(progress * 100).roundToInt()}%" else "∞", fontSize = 9.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(0.08f))) {
                                    if (progress > 0f) Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.04f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (progress >= 0.9f) GlassRed else if (progress >= 0.72f) GlassAmber else theme.lamp.primary))
                                }
                            }
                            // ورودی حجم + ریست حجم (کنار هم)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (theme.isDark) Color(0xFF16161A) else Color.White)
                                        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        Box(Modifier.weight(1f)) {
                                            if (limitGb.isEmpty()) Text("حجم (مثلا 10)", color = theme.mutedColor.copy(0.6f), fontSize = 12.sp)
                                            BasicTextField(value = limitGb, onValueChange = { limitGb = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                                        }
                                        Text("GB", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (initial != null) Box(Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(GlassAmber.copy(0.14f)).border(BorderStroke(1.dp, GlassAmber.copy(0.34f)), RoundedCornerShape(10.dp))
                                    .clickable { showResetUsageConfirm = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("♻️ ریست حجم", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassAmber) }
                            }
                            // چیپ‌های GB
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(1.0, 5.0, 10.0, 20.0, 50.0).forEach { gb ->
                                    Box(Modifier.height(24.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(7.dp))
                                        .clickable { addGb(gb) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text("+${gb.toInt()}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                                }
                                Box(Modifier.width(54.dp).height(24.dp).clip(RoundedCornerShape(7.dp)).background(if (theme.isDark) Color(0xFF16161A) else Color.White).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                    if (addGbInput.isEmpty()) Text("+GB", fontSize = 8.5.sp, color = theme.mutedColor)
                                    BasicTextField(value = addGbInput, onValueChange = { addGbInput = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                                }
                                if (addGbInput.isNotEmpty()) Box(Modifier.height(24.dp).clip(RoundedCornerShape(7.dp)).background(theme.lamp.primary).clickable {
                                    val v = addGbInput.toDoubleOrNull() ?: 0.0; if (v > 0) { addGb(v); addGbInput = "" }
                                }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 9.5.sp) }
                            }
                        }
                    }

                    // ── کاشی زمان ──
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.86f))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // هدر: عنوان + تاریخ زنده + روز مانده + تقویم
                            val daysInt = dayField.toIntOrNull()
                            val dateText = if (daysInt == null || daysInt < 0) "نامحدود" else JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(daysInt.toLong()).toString())
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("📅 زمان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                    Text(dateText, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = if (daysInt == null || daysInt < 0) theme.mutedColor else theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (daysInt != null && daysInt >= 0) Text("${daysInt} روز مانده", fontSize = 9.sp, color = if (daysInt in 0..3) GlassRed else theme.mutedColor, fontWeight = FontWeight.Bold)
                                    else Text("بدون محدودیت زمانی", fontSize = 9.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary.copy(0.12f)).border(BorderStroke(1.dp, theme.lamp.primary.copy(0.22f)), RoundedCornerShape(9.dp))
                                    .clickable { showCalendar = true }, contentAlignment = Alignment.Center) { Text("🗓️", fontSize = 13.sp) }
                            }
                            // ورودیِ زندهٔ تعداد روز + ریست زمان (کنار هم)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (theme.isDark) Color(0xFF16161A) else Color.White)
                                        .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        Box(Modifier.weight(1f)) {
                                            if (dayField.isEmpty()) Text("نامحدود (تعداد روز)", color = theme.mutedColor.copy(0.6f), fontSize = 12.sp)
                                            BasicTextField(value = dayField, onValueChange = { dayField = it.filter { c -> c.isDigit() }.take(5) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                                        }
                                        Text("روز", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (initial != null) Box(Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(GlassAmber.copy(0.14f)).border(BorderStroke(1.dp, GlassAmber.copy(0.34f)), RoundedCornerShape(10.dp))
                                    .clickable { showResetExpiryConfirm = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("♻️ ریست", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassAmber) }
                            }
                            // دکمه‌های سریع + ورودیِ روزِ دلخواه (همگی به فیلد اضافه می‌کنند)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(7, 30, 60, 90, 180).forEach { d ->
                                    Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary.copy(0.10f)).border(BorderStroke(1.dp, theme.lamp.primary.copy(0.26f)), RoundedCornerShape(8.dp))
                                        .clickable {
                                            addDays(d); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            android.widget.Toast.makeText(context, "+$d روز → ${dayField} روز مانده", android.widget.Toast.LENGTH_SHORT).show()
                                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text("+$d", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.lamp.primary) }
                                }
                                // ورودیِ روزِ دلخواه + تیک (مانند +GB در کاشی حجم)
                                Box(Modifier.width(54.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (theme.isDark) Color(0xFF16161A) else Color.White).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                    if (addDayInput.isEmpty()) Text("+روز", fontSize = 9.sp, color = theme.mutedColor)
                                    BasicTextField(value = addDayInput, onValueChange = { addDayInput = it.filter { c -> c.isDigit() }.take(4) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                                }
                                if (addDayInput.isNotEmpty()) Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary).clickable {
                                    val d = addDayInput.toIntOrNull() ?: 0; if (d > 0) { addDays(d); addDayInput = ""; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 10.sp) }
                            }
                        }
                    }

                    // Horizontal compact HWID (userlimit)
                    var hwid by remember { mutableStateOf(hwidLimit) }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.86f))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📱", fontSize = 11.sp)
                                Text("محدودیت همزمان (userlimit):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            }
                            Box(
                                Modifier.width(110.dp).height(30.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(0.05f))
                                    .border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hwid.isEmpty()) Text("∞ (نامحدود)", fontSize = 11.sp, color = theme.mutedColor.copy(0.7f))
                                BasicTextField(
                                    value = hwid,
                                    onValueChange = { val clean = it.filter { c -> c.isDigit() }; hwid = clean; hwidLimit = clean },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(fontSize = 12.5.sp, color = theme.inkColor, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // ── کاشی یادداشت ──
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.82f))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("📝", fontSize = 12.sp)
                                    Text("توضیحات / یادداشت (Note):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                }
                                Text("${note.length}/500", fontSize = 9.5.sp, color = theme.mutedColor)
                            }
                            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.10f)), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                if (note.isEmpty()) Text("یادداشت اختیاری برای این کاربر...", color = theme.mutedColor.copy(0.5f), fontSize = 11.sp)
                                BasicTextField(
                                    value = note,
                                    onValueChange = { if (it.length <= 500) note = it },
                                    textStyle = TextStyle(color = theme.inkColor, fontSize = 11.5.sp),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // ── کاشی گروه‌ها ──
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.82f))
                            .border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), RoundedCornerShape(14.dp)).padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("👥 گروه‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                Spacer(Modifier.weight(1f))
                                if (allGroups.isEmpty()) Text("در حال بارگذاری...", fontSize = 9.sp, color = theme.mutedColor)
                                else Text("${selectedGroupIds.size} انتخاب", fontSize = 9.sp, color = theme.mutedColor)
                            }
                            if (allGroups.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    allGroups.forEach { g ->
                                        val sel = selectedGroupIds.contains(g.id)
                                        Box(
                                            Modifier.height(28.dp).clip(RoundedCornerShape(9.dp))
                                                .background(if (sel) theme.lamp.primary else Color.Black.copy(0.04f))
                                                .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.14f)), RoundedCornerShape(9.dp))
                                                .clickable {
                                                    selectedGroupIds = if (sel) selectedGroupIds - g.id else selectedGroupIds + g.id
                                                }.padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) { Text(g.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else theme.inkColor) }
                                    }
                                }
                            } else {
                                Text("گروهی یافت نشد یا دسترسی ندارید - گروه‌ها از /api/groups/simple لود میشن", fontSize = 9.5.sp, color = theme.mutedColor, lineHeight = 13.sp)
                            }
                            if (initial?.groupNames?.isNotEmpty() == true) {
                                Text("فعلی: ${initial.groupNames.joinToString()}", fontSize = 9.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (initial != null && allTemplates.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (theme.isDark) 0.08f else 0.82f))
                            .border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(14.dp)).padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📦 تغییر و اعمال تمپلت روی این کاربر:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                allTemplates.forEach { t ->
                                    val sel = selectedTemplateId == t.id
                                    Box(
                                        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) theme.lamp.primary else Color.Black.copy(0.04f))
                                            .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.14f)), RoundedCornerShape(8.dp))
                                            .clickable { selectedTemplateId = t.id }.padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Text(t.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else theme.inkColor) }
                                }
                            }
                            if (selectedTemplateId != null) {
                                Box(
                                    Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp))
                                        .background(theme.lamp.primary.copy(0.18f))
                                        .border(BorderStroke(1.2.dp, theme.lamp.primary), RoundedCornerShape(8.dp))
                                        .clickable { onApplyTemplateToUser?.invoke(selectedTemplateId!!, note) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡ اعمال تمپلت روی ${initial.username}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                                }
                            }
                        }
                    }
                }

                // Sub URL compact
                initial?.let { user ->
                    if (user.subUrl.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.5.dp, Color.White.copy(0.35f)), RoundedCornerShape(12.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(user.subUrl, fontSize = 9.5.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            // Copy button - smaller icon only
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(0.15f))
                                    .border(BorderStroke(1.5.dp, Color.White.copy(0.55f)), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                        android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("📋", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                            // QR button - smaller icon only
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(0.15f))
                                    .border(BorderStroke(1.5.dp, Color.White.copy(0.55f)), RoundedCornerShape(8.dp))
                                    .clickable { showQr = true }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("📱", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                            // Share button
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(0.15f))
                                    .border(BorderStroke(1.5.dp, Color.White.copy(0.55f)), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, user.subUrl) }
                                        context.startActivity(Intent.createChooser(i, "اشتراک اشتراک"))
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("🔗", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                        }
                    }
                }

                // Bottom actions - clean 2 rows
                initial?.let { user ->
                    val actionBg = if (theme.isDark) Color(0xFF2C2C34) else Color(0xFFE8E4DA)
                    val actionBorder = if (theme.isDark) Color(0xFF7E7C88) else Color(0xFF88847A)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (user.status == "disabled") GlassGreen.copy(0.18f) else actionBg).border(BorderStroke(1.2.dp, if (user.status == "disabled") GlassGreen else actionBorder), RoundedCornerShape(10.dp)).clickable { onToggle?.invoke() }, contentAlignment = Alignment.Center) {
                            Text(if (user.status == "disabled") "🟢 فعال" else "⚪ غیرفعال", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (user.status == "disabled") GlassGreen else theme.inkColor)
                        }
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(GlassRed.copy(0.08f)).border(BorderStroke(1.dp, GlassRed.copy(0.24f)), RoundedCornerShape(10.dp)).clickable { onDelete?.invoke() }, contentAlignment = Alignment.Center) {
                            Text("🗑 حذف", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                        }
                    }
                }

                formError?.let {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GlassRed.copy(0.08f)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(10.dp)).padding(8.dp)) {
                        Text(it, color = GlassRed, fontSize = 11.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f).height(40.dp))
                    PrimarySaveButton("ذخیره", onClick = {
                        if (isTemplateMode && initial == null) {
                            if (username.length !in 3..32) formError = "نام کاربری ۳-۳۲"
                            else if (selectedTemplateId == null) formError = "لطفاً یک تمپلت انتخاب کنید"
                            else onSaveWithTemplate?.invoke(username, selectedTemplateId!!, note)
                        } else {
                            val clean = limitGb.replace(',', '.').trim()
                            val lim = if (clean.isBlank()) 0.0 else clean.toDoubleOrNull()
                            val hwidInt = hwidLimit.toIntOrNull()
                            if (username.length !in 3..32 && initial == null) formError = "نام کاربری ۳-۳۲"
                            else if (lim == null || lim < 0) formError = "حجم نامعتبر"
                            else {
                                val di = dayField.toIntOrNull()
                                val saveShamsi = if (di == null || di < 0) "" else JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(di.toLong()).toString())
                                onSave(UserEditorValues(username, lim, note, hwidInt, selectedGroupIds), saveShamsi)
                            }
                        }
                    }, modifier = Modifier.weight(1f).height(40.dp))
                }
            }
        }
    }

    if (showQr && initial != null && initial.subUrl.isNotEmpty()) SubscriptionQrDialog(user = initial, onDismiss = { showQr = false })
    if (showCalendar) ShamsiCalendarPickerDialog(
        initialDateShamsi = run {
            val di = dayField.toIntOrNull()
            if (di != null && di > 0) JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(di.toLong()).toString())
            else JalaliCalendar.todayJalali().toString()
        },
        onDismiss = { showCalendar = false },
        onDateSelected = { shamsi ->
            val iso = JalaliCalendar.shamsiToIso(shamsi)
            val days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(iso.take(10))) }.getOrNull()
            dayField = if (days != null && days >= 0) days.toString() else ""
        }
    )

    if (showResetUsageConfirm) ConfirmActionDialog(
        title = "ریست حجم مصرف‌شده؟",
        message = "مصرفِ این کاربر صفر می‌شود.",
        onDismiss = { showResetUsageConfirm = false },
        onConfirm = {
            showResetUsageConfirm = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onResetUsage?.invoke()
        }
    )
    if (showResetExpiryConfirm) ConfirmActionDialog(
        title = "ریست زمان اشتراک؟",
        message = "زمانِ این کاربر به حالت نامحدود درمی‌آید.",
        onDismiss = { showResetExpiryConfirm = false },
        onConfirm = {
            showResetExpiryConfirm = false
            dayField = ""
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            android.widget.Toast.makeText(context, "زمان ریست شد (نامحدود)", android.widget.Toast.LENGTH_SHORT).show()
            onResetExpiry?.invoke()
        }
    )
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
                Text("📦 اعمال تمپلت روی $selectedCount کاربر انتخابی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                Text("یک تمپلت آماده انتخاب کنید تا تنظیمات آن روی هر $selectedCount کاربر انتخابی اعمال شود:", color = theme.mutedColor, fontSize = 11.5.sp)

                if (isLoading) {
                    Text("⏳ در حال بارگذاریِ تمپلت‌ها...", fontSize = 11.sp, color = theme.mutedColor)
                } else if (loadFailed) {
                    Text("⚠️ خطا در بارگذاریِ تمپلت‌ها. دوباره امتحان کنید.", fontSize = 11.sp, color = GlassRed)
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
                                    if (sel) Text("✓ انتخاب شد", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Text("تمپلتی در پنل یافت نشد.", fontSize = 11.sp, color = GlassRed)
                }

                CompactGlassField(value = note, onValueChange = { note = it }, placeholder = "یادداشت اختیاری...", leading = "📝")

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
