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
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.formatBytes
import java.util.Locale

@Composable
fun ThemeEditorDialog(
    themeState: com.mrm.pgmanager.ui.theme.ThemeState,
    onDismiss: () -> Unit,
    onThemeChange: (com.mrm.pgmanager.ui.theme.ThemeState) -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🎨 تم برنامه", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = theme.inkColor)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeToggleBtn("☀️ روشن", !themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(isDark = false)) }
                    ModeToggleBtn("🌙 تیره", themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(isDark = true)) }
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

// === NEW PROFESSIONAL USER EDITOR - compact tiles, gold glass ===
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
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var formError by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var customDay by remember { mutableStateOf("") }
    var customGb by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun addGb(amount: Double) {
        val current = limitGb.toDoubleOrNull() ?: 0.0
        val newVal = (current + amount).coerceAtLeast(0.0)
        limitGb = if (newVal == 0.0) "" else "%.2f".format(Locale.US, newVal).trimEnd('0').trimEnd('.')
    }
    fun addDays(days: Int) {
        val baseIso = if (initial?.expire != null && initial.expire != "0" && initial.expire != "null") initial.expire else null
        val newIso = JalaliCalendar.addDaysToIso(baseIso, days)
        expireShamsi = JalaliCalendar.isoToShamsi(newIso)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(26.dp))) {
            Column(Modifier.fillMaxWidth().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header - compact
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (initial == null) "کاربر جدید" else initial.username, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        if (initial != null) Text("${formatBytes(initial.usedTraffic)} مصرف • ${initial.status}", fontSize = 11.sp, color = theme.mutedColor)
                    }
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(theme.lamp.primary.copy(0.12f)), contentAlignment = Alignment.Center) { Text(if (initial == null) "🆕" else "👤", fontSize = 16.sp) }
                }

                // Username if new - small
                if (initial == null) {
                    UltraPremiumField(value = username, onValueChange = { username = it }, label = "نام کاربری", placeholder = "3-32 حرف", leadingIcon = "👤")
                }

                // === VOLUME SECTION - compact tile ===
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(if (theme.isDark) 0.06f else 0.44f)).border(BorderStroke(1.dp, Color.White.copy(0.18f)), RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💾", fontSize = 12.sp)
                        Text("حجم", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Spacer(Modifier.weight(1f))
                        Text(if (limitGb.isBlank()) "نامحدود" else "$limitGb GB", fontSize = 11.sp, color = theme.mutedColor)
                    }
                    UltraPremiumField(value = limitGb, onValueChange = { limitGb = it }, label = "", placeholder = "گیگابایت (خالی=نامحدود)", leadingIcon = "💾", keyboardType = KeyboardType.Decimal)
                    // Quick add GB chips - small, horizontal scroll
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0).forEach { gb ->
                            Box(
                                Modifier.height(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(0.10f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(9.dp))
                                    .clickable { addGb(gb) }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("+${gb.toInt()}G", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                        }
                        Box(Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.12f)), RoundedCornerShape(9.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            if (customGb.isEmpty()) Text("+GB", fontSize = 9.sp, color = theme.mutedColor)
                            BasicTextField(value = customGb, onValueChange = { customGb = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                        }
                        if (customGb.isNotEmpty()) Box(Modifier.height(28.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary).clickable {
                            val v = customGb.toDoubleOrNull() ?: 0.0
                            if (v > 0) { addGb(v); customGb = "" }
                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 11.sp) }
                    }
                }

                // === EXPIRY SECTION - same main tile, add days ===
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(if (theme.isDark) 0.06f else 0.44f)).border(BorderStroke(1.dp, Color.White.copy(0.18f)), RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📅", fontSize = 12.sp)
                        Text("انقضا", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary.copy(0.12f)).border(BorderStroke(1.dp, theme.lamp.primary.copy(0.18f)), RoundedCornerShape(9.dp))
                                .clickable { showCalendar = true }, contentAlignment = Alignment.Center
                        ) { Text("📅", fontSize = 14.sp) }
                    }
                    UltraPremiumField(value = expireShamsi, onValueChange = { expireShamsi = it }, label = "", placeholder = "1404/05/19 - از تقویم انتخاب کن", leadingIcon = "⏰")
                    // Add days in same tile - small chips
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 30, 60, 90, 180).forEach { d ->
                            Box(
                                Modifier.height(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(0.10f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(9.dp))
                                    .clickable { addDays(d) }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("+$d روز", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                        }
                        Box(Modifier.width(56.dp).height(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.12f)), RoundedCornerShape(9.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            if (customDay.isEmpty()) Text("+روز", fontSize = 9.sp, color = theme.mutedColor)
                            BasicTextField(value = customDay, onValueChange = { customDay = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                        }
                        if (customDay.isNotEmpty()) Box(Modifier.height(28.dp).clip(RoundedCornerShape(9.dp)).background(theme.lamp.primary).clickable {
                            val v = customDay.toIntOrNull() ?: 0
                            if (v > 0) { addDays(v); customDay = "" }
                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 11.sp) }
                    }
                }

                // === NOTE SECTION - visible and editable ===
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(if (theme.isDark) 0.06f else 0.44f)).border(BorderStroke(1.dp, Color.White.copy(0.18f)), RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📝", fontSize = 12.sp)
                        Text("توضیحات", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Spacer(Modifier.weight(1f))
                        Text("${note.length}/500", fontSize = 10.sp, color = theme.mutedColor)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(12.dp)).padding(10.dp)
                    ) {
                        if (note.isEmpty()) Text("توضیحات کاربر...", color = theme.mutedColor.copy(0.6f), fontSize = 12.sp)
                        BasicTextField(value = note, onValueChange = { if (it.length <= 500) note = it }, textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp), modifier = Modifier.fillMaxSize(), maxLines = 4)
                    }
                }

                // Sub URL
                initial?.let { user ->
                    if (user.subUrl.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.06f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("لینک اشتراک", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.08f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(user.subUrl, fontSize = 11.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                MiniGlassButton("📋") {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                    android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                MiniGlassButton("📱") { showQr = true }
                            }
                        }
                    }
                }

                // Actions row - small buttons
                initial?.let { user ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(12.dp)).clickable { onToggle?.invoke() }, contentAlignment = Alignment.Center) {
                            Text(if (user.status == "disabled") "🟢 فعال" else "⚪ غیرفعال", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(12.dp)).clickable { /* reset usage dialog */ }, contentAlignment = Alignment.Center) {
                            GlassButton("♻️ حجم", onClick = { /* handled via parent */ }, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onResetUsage?.let { Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(12.dp)).clickable { onResetUsage.invoke() }, contentAlignment = Alignment.Center) { Text("♻️ ریست حجم", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) } }
                        onResetExpiry?.let { Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(12.dp)).clickable { onResetExpiry.invoke() }, contentAlignment = Alignment.Center) { Text("⏰ ریست زمان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) } }
                        onDelete?.let { Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(com.mrm.pgmanager.ui.theme.GlassRed.copy(0.12f)).border(BorderStroke(1.dp, com.mrm.pgmanager.ui.theme.GlassRed.copy(0.22f)), RoundedCornerShape(12.dp)).clickable { onDelete.invoke() }, contentAlignment = Alignment.Center) { Text("🗑 حذف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.mrm.pgmanager.ui.theme.GlassRed) } }
                    }
                }

                formError?.let {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(com.mrm.pgmanager.ui.theme.GlassRed.copy(0.08f)).border(BorderStroke(1.dp, com.mrm.pgmanager.ui.theme.GlassRed.copy(0.18f)), RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Text(it, color = com.mrm.pgmanager.ui.theme.GlassRed, fontSize = 11.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f).height(42.dp))
                    PrimarySaveButton("ذخیره", onClick = {
                        val clean = limitGb.replace(',', '.').trim()
                        val lim = if (clean.isBlank()) 0.0 else clean.toDoubleOrNull()
                        if (username.length !in 3..32 && initial == null) formError = "نام کاربری ۳-۳۲ حرف"
                        else if (lim == null || lim < 0) formError = "حجم نامعتبر"
                        else if (expireShamsi.isNotBlank() && !Regex("^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}$").matches(expireShamsi)) formError = "تاریخ مثل ۱۴۰۴/۰۵/۱۹"
                        else onSave(UserEditorValues(username, lim, note), expireShamsi)
                    }, modifier = Modifier.weight(1f).height(42.dp))
                }
            }
        }
    }

    if (showQr && initial != null && initial.subUrl.isNotEmpty()) SubscriptionQrDialog(user = initial, onDismiss = { showQr = false })
    if (showCalendar) ShamsiCalendarPickerDialog(initialDateShamsi = expireShamsi, onDismiss = { showCalendar = false }, onDateSelected = { expireShamsi = it })
}
