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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.JalaliCalendar
import java.util.Locale

@Composable
fun ThemeEditorDialog(
    themeState: com.mrm.pgmanager.ui.theme.ThemeState,
    onDismiss: () -> Unit,
    onThemeChange: (com.mrm.pgmanager.ui.theme.ThemeState) -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(30.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("🎨 ظاهر برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text("رنگ و تم را شخصی‌سازی کنید", fontSize = 12.sp, color = theme.mutedColor)
                    }
                    AppLogo(height = 22.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حالت پس‌زمینه", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ModeToggleBtn("☀️ روشن", !themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(isDark = false)) }
                        ModeToggleBtn("🌙 تیره", themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(isDark = true)) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("رنگ تاکیدی", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.mrm.pgmanager.ui.theme.LampColor.values().forEach { lamp ->
                            val isSelected = themeState.lamp == lamp
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) lamp.primary.copy(0.16f) else Color.Transparent)
                                    .border(BorderStroke(1.2.dp, if (isSelected) lamp.primary else Color.White.copy(if (theme.isDark) 0.14f else 0.45f)), RoundedCornerShape(16.dp))
                                    .clickable { onThemeChange(themeState.copy(lamp = lamp)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                            .background(Brush.linearGradient(listOf(lamp.primary, lamp.light)))
                                            .border(BorderStroke(1.dp, Color.White.copy(0.8f)), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(lamp.emoji, fontSize = 16.sp) }
                                    Column(Modifier.weight(1f)) {
                                        Text(lamp.labelFa, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = theme.inkColor, fontSize = 13.sp)
                                        Text(lamp.label, fontSize = 10.sp, color = theme.mutedColor)
                                    }
                                    if (isSelected) Box(
                                        Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(lamp.primary),
                                        contentAlignment = Alignment.Center
                                    ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
                GlassButton("✓ تایید و بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ModeToggleBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.primary.copy(0.78f))) else Brush.linearGradient(listOf(if (theme.isDark) Color.White.copy(0.10f) else Color.White.copy(0.6f), if (theme.isDark) Color.White.copy(0.04f) else Color.White.copy(0.30f))))
            .border(BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(0.35f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
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
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor)
                .border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📱 اشتراک ${user.username}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                Box(
                    Modifier.size(240.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    else Text("QR نیازمند ZXing", color = com.mrm.pgmanager.ui.theme.GlassRed, fontSize = 12.sp)
                }
                Text(user.subUrl, fontSize = 11.sp, color = theme.mutedColor, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(if (theme.isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton("📋 کپی", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                        android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f))
                    PrimarySaveButton("📤 اشتراک", onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "پاسارگارد ${user.username}:\n${user.subUrl}") }
                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری"))
                    }, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("بستن", color = theme.mutedColor, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ShamsiCalendarPickerDialog(initialDateShamsi: String, onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
    val theme = LocalThemeState.current
    val today = JalaliCalendar.todayJalali()
    val parsedInitial = remember(initialDateShamsi) {
        val p = initialDateShamsi.replace("-", "/").split("/")
        if (p.size == 3) JalaliCalendar.Date(p[0].toIntOrNull() ?: today.year, p[1].toIntOrNull() ?: today.month, p[2].toIntOrNull() ?: today.day) else today
    }
    var selectedYear by remember { mutableStateOf(parsedInitial.year) }
    var selectedMonth by remember { mutableStateOf(parsedInitial.month) }
    var selectedDay by remember { mutableStateOf(parsedInitial.day) }
    val daysInMonth = when { selectedMonth in 1..6 -> 31; selectedMonth in 7..11 -> 30; else -> if (selectedYear % 4 == 3) 30 else 29 }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("📅 تقویم شمسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    TextButton(onClick = { selectedYear = today.year; selectedMonth = today.month; selectedDay = today.day }) { Text("امروز", color = theme.lamp.primary, fontWeight = FontWeight.Bold) }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { if (selectedMonth > 1) selectedMonth-- else { selectedMonth = 12; selectedYear-- } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.18f), contentColor = theme.inkColor)) { Text("◀") }
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.1f)).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            val tempD = JalaliCalendar.Date(selectedYear, selectedMonth, 1)
                            Text("${tempD.getMonthName()} $selectedYear", fontWeight = FontWeight.Bold, color = theme.inkColor, fontSize = 14.sp)
                        }
                        Button(onClick = { if (selectedMonth < 12) selectedMonth++ else { selectedMonth = 1; selectedYear++ } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.18f), contentColor = theme.inkColor)) { Text("▶") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { selectedYear-- }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f), contentColor = theme.inkColor)) { Text("-", fontSize = 12.sp) }
                        Button(onClick = { selectedYear++ }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f), contentColor = theme.inkColor)) { Text("+", fontSize = 12.sp) }
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(210.dp)) {
                    items((1..daysInMonth).toList()) { day ->
                        val isSel = day == selectedDay
                        Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (isSel) theme.lamp.primary else (if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(0.60f))).clickable { selectedDay = day }, contentAlignment = Alignment.Center) {
                            Text("$day", color = if (isSel) Color.White else theme.inkColor, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    PrimarySaveButton("تایید تاریخ", onClick = { onDateSelected(JalaliCalendar.Date(selectedYear, selectedMonth, selectedDay).toString()); onDismiss() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun UserEditorDialog(
    initial: PanelUser?,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onResetUsage: (() -> Unit)?,
    onResetExpiry: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    var expireShamsi by remember { mutableStateOf(if (initial?.expire != null && initial.expire != "0") JalaliCalendar.isoToShamsi(initial.expire) else "") }
    var formError by remember { mutableStateOf<String?>(null) }
    var showShamsiCalendar by remember { mutableStateOf(false) }
    var customAddDays by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var confirmResetUsage by remember { mutableStateOf(false) }
    var confirmResetExpiry by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(theme.dialogBgColor).border(BorderStroke(1.6.dp, theme.cardBorderBrush), RoundedCornerShape(30.dp))) {
            Box(
                Modifier.size(280.dp).align(Alignment.TopEnd).offset(x = 80.dp, y = (-60).dp)
                    .background(Brush.radialGradient(listOf(theme.lamp.spotHigh, theme.lamp.spotLow, Color.Transparent)), shape = RoundedCornerShape(200.dp))
            )
            Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (initial == null) "➕ کاربر جدید" else "✏️ ویرایش ${initial.username}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(theme.lamp.primary.copy(0.12f)), contentAlignment = Alignment.Center) { Text(if (initial == null) "🆕" else "👤", fontSize = 18.sp) }
                }

                if (initial == null) UltraPremiumField(value = username, onValueChange = { username = it }, label = "نام کاربری", placeholder = "3-32 کاراکتر", leadingIcon = "👤")
                UltraPremiumField(value = limitGb, onValueChange = { limitGb = it }, label = "حجم گیگابایت", placeholder = "خالی=نامحدود", leadingIcon = "💾", keyboardType = KeyboardType.Decimal)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { UltraPremiumField(value = expireShamsi, onValueChange = { expireShamsi = it }, label = "تاریخ انقضا شمسی", placeholder = "1404/05/19", leadingIcon = "📅") }
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(theme.lamp.primary.copy(0.14f))
                                .border(BorderStroke(1.dp, theme.lamp.primary.copy(0.25f)), RoundedCornerShape(14.dp))
                                .clickable { showShamsiCalendar = true }, contentAlignment = Alignment.Center
                        ) { Text("📅", fontSize = 18.sp) }
                    }
                    Text("افزودن سریع:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        MiniGlassButton("+۳۰ روز") {
                            val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                            expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, 30))
                        }
                        MiniGlassButton("+۶۰ روز") {
                            val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                            expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, 60))
                        }
                        MiniGlassButton("+۹۰ روز") {
                            val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                            expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, 90))
                        }
                        Box(
                            Modifier.width(76.dp).height(34.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (theme.isDark) Color.White.copy(0.10f) else Color.White.copy(0.60f))
                                .border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (customAddDays.isEmpty()) Text("+روز", fontSize = 11.sp, color = theme.mutedColor.copy(0.8f))
                            BasicTextField(value = customAddDays, onValueChange = { customAddDays = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(color = theme.inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                        }
                        if (customAddDays.isNotEmpty()) MiniGlassButton("✓") {
                            val d = customAddDays.toIntOrNull() ?: 0
                            if (d > 0) {
                                val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                                expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, d))
                                customAddDays = ""
                            }
                        }
                    }
                }

                initial?.let { user ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(if (theme.isDark) 0.06f else 0.45f))
                            .border(BorderStroke(1.dp, Color.White.copy(0.22f)), RoundedCornerShape(18.dp)).padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                        .background(when (user.status) { "active" -> com.mrm.pgmanager.ui.theme.GlassGreen.copy(0.18f); "disabled" -> Color.Gray.copy(0.15f); else -> com.mrm.pgmanager.ui.theme.GlassRed.copy(0.15f) }),
                                    contentAlignment = Alignment.Center
                                ) { Text(if (user.status == "active") "🟢" else "🔴", fontSize = 14.sp) }
                                Column {
                                    Text("${com.mrm.pgmanager.utils.formatBytes(user.usedTraffic)} مصرف • ${user.status.uppercase()}", color = theme.inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("ایجاد: ${JalaliCalendar.isoToShamsi(user.createdAt ?: "")} • ${if (user.isOnline) "آنلاین" else "آفلاین"}", color = theme.mutedColor, fontSize = 11.sp)
                                }
                            }
                            if (user.subUrl.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text("لینک اشتراک:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                                .background(if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(0.70f))
                                                .border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(user.subUrl, fontSize = 11.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        MiniGlassButton("📋") {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                            android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        MiniGlassButton("📱") { showQrDialog = true }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                onResetUsage?.let { GlassButton("♻️ ریست حجم", onClick = { confirmResetUsage = true }, modifier = Modifier.weight(1f)) }
                                onResetExpiry?.let { GlassButton("⏰ ریست زمان", onClick = { confirmResetExpiry = true }, modifier = Modifier.weight(1f)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                onToggle?.let { toggle -> GlassButton(text = if (user.status == "disabled") "🟢 فعال‌سازی" else "⚪ غیرفعال", onClick = toggle, modifier = Modifier.weight(1f)) }
                                onDelete?.let { del -> GlassButton(text = "🗑 حذف", onClick = del, modifier = Modifier.weight(1f), isRed = true) }
                            }
                        }
                    }
                }

                formError?.let {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(com.mrm.pgmanager.ui.theme.GlassRed.copy(0.10f))
                            .border(BorderStroke(1.dp, com.mrm.pgmanager.ui.theme.GlassRed.copy(0.20f)), RoundedCornerShape(12.dp)).padding(12.dp)
                    ) { Text(it, color = com.mrm.pgmanager.ui.theme.GlassRed, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MutedCancelButton("✕ انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    PrimarySaveButton("✓ ذخیره", onClick = {
                        val cleanLimitStr = limitGb.replace(',', '.').trim()
                        val limit = if (cleanLimitStr.isBlank()) 0.0 else cleanLimitStr.toDoubleOrNull()
                        if (username.length !in 3..32 && initial == null) formError = "نام کاربری باید ۳ تا ۳۲ کاراکتر باشد."
                        else if (limit == null || limit < 0) formError = "حجم نامعتبر است."
                        else if (expireShamsi.isNotBlank() && !Regex("^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}$").matches(expireShamsi)) formError = "فرمت تاریخ شمسی مثل ۱۴۰۴/۰۵/۱۹"
                        else onSave(UserEditorValues(username, limit), expireShamsi)
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmResetUsage && initial != null) {
        Dialog(onDismissRequest = { confirmResetUsage = false }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("⚠️ تایید ریست حجم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = com.mrm.pgmanager.ui.theme.GlassRed)
                    Text("آیا مطمئن هستید که حجم مصرفی ${initial.username} به صفر بازنشانی شود؟", color = theme.inkColor, fontSize = 13.sp, lineHeight = 20.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MutedCancelButton("انصراف", onClick = { confirmResetUsage = false }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        GlassButton("تایید و ریست", onClick = { confirmResetUsage = false; onResetUsage?.invoke() }, modifier = Modifier.weight(1f), isRed = true)
                    }
                }
            }
        }
    }

    if (confirmResetExpiry && initial != null) {
        Dialog(onDismissRequest = { confirmResetExpiry = false }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("⏰ تایید ریست زمان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.lamp.primary)
                    Text("تاریخ انقضا برای ${initial.username} نامحدود شود؟", color = theme.inkColor, fontSize = 13.sp, lineHeight = 20.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MutedCancelButton("انصراف", onClick = { confirmResetExpiry = false }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        GlassButton("تایید", onClick = { confirmResetExpiry = false; onResetExpiry?.invoke() }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showQrDialog && initial != null && initial.subUrl.isNotEmpty()) SubscriptionQrDialog(user = initial, onDismiss = { showQrDialog = false })
    if (showShamsiCalendar) ShamsiCalendarPickerDialog(initialDateShamsi = expireShamsi, onDismiss = { showShamsiCalendar = false }, onDateSelected = { expireShamsi = it })
}
