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
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
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
    session: com.mrm.pgmanager.data.model.Session? = null
) {
    val theme = LocalThemeState.current
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    var expireShamsi by remember { mutableStateOf(if (initial?.expire != null && initial.expire != "0") JalaliCalendar.isoToShamsi(initial.expire) else "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hwidLimit by remember { mutableStateOf(initial?.hwidLimit?.toString() ?: "") }
    var selectedGroupIds by remember { mutableStateOf(initial?.groupIds ?: emptyList()) }
    var allGroups by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.Group>>(emptyList()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var addDayInput by remember { mutableStateOf("") }
    var addGbInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(session) {
        if (session != null) {
            allGroups = com.mrm.pgmanager.data.api.PanelApi.groups(session)
        }
    }

    fun addGb(amount: Double) {
        val current = limitGb.toDoubleOrNull() ?: 0.0
        val newVal = (current + amount).coerceAtLeast(0.0)
        limitGb = if (newVal == 0.0) "" else "%.2f".format(Locale.US, newVal).trimEnd('0').trimEnd('.')
    }
    fun addDays(days: Int) {
        val baseIso = if (initial?.expire != null && initial.expire != "0" && initial.expire != "null" && initial.expire.isNotBlank()) initial.expire else null
        val newIso = JalaliCalendar.addDaysToIso(baseIso, days)
        expireShamsi = JalaliCalendar.isoToShamsi(newIso)
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
                        if (initial != null) Text("${formatBytes(initial.usedTraffic)} • ${initial.status}", fontSize = 10.sp, color = theme.mutedColor)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // QR button for existing users with subscription
                        initial?.let { user ->
                            if (user.subUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.lamp.primary.copy(0.14f))
                                        .border(BorderStroke(1.dp, theme.lamp.primary.copy(0.20f)), RoundedCornerShape(10.dp))
                                        .clickable { showQr = true }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("📱", fontSize = 13.sp)
                                        Text("QR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.lamp.primary)
                                    }
                                }
                            }
                        }
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary.copy(0.14f)), contentAlignment = Alignment.Center) { Text(if (initial == null) "🆕" else "👤", fontSize = 13.sp) }
                    }
                }

                if (initial == null) {
                    CompactGlassField(value = username, onValueChange = { username = it }, placeholder = "نام کاربری 3-32", leading = "👤")
                }

                // Volume - SINGLE small opaque tile
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = if (theme.isDark) 0.12f else 0.88f))
                        .border(BorderStroke(1.dp, Color.White.copy(0.20f)), RoundedCornerShape(14.dp)).padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("💾 ${if (limitGb.isBlank()) "نامحدود" else "$limitGb GB"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
                            if (initial != null) Text(formatBytes(initial.usedTraffic), fontSize = 10.sp, color = theme.mutedColor)
                        }
                        CompactGlassField(value = limitGb, onValueChange = { limitGb = it }, placeholder = "حجم GB", leading = "💾", keyboardType = KeyboardType.Decimal)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(1.0, 5.0, 10.0, 20.0, 50.0).forEach { gb ->
                                Box(
                                    Modifier.height(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(8.dp))
                                        .clickable { addGb(gb) }.padding(horizontal = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("+${gb.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                            }
                            // custom GB
                            Box(Modifier.width(64.dp).height(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.10f)).border(BorderStroke(1.dp, Color.White.copy(0.14f)), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                if (addGbInput.isEmpty()) Text("+GB", fontSize = 9.sp, color = theme.mutedColor)
                                BasicTextField(value = addGbInput, onValueChange = { addGbInput = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 10.5.sp, color = theme.inkColor, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                            }
                            if (addGbInput.isNotEmpty()) Box(Modifier.height(26.dp).clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary).clickable {
                                val v = addGbInput.toDoubleOrNull() ?: 0.0; if (v > 0) { addGb(v); addGbInput = "" }
                            }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 10.sp) }
                        }
                    }
                }

                // Date - SINGLE small opaque tile - only days input
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = if (theme.isDark) 0.12f else 0.88f))
                        .border(BorderStroke(1.dp, Color.White.copy(0.20f)), RoundedCornerShape(14.dp)).padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📅 ${if (expireShamsi.isBlank()) "نامحدود" else expireShamsi}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary.copy(0.12f)).border(BorderStroke(1.dp, theme.lamp.primary.copy(0.18f)), RoundedCornerShape(8.dp))
                                    .clickable { showCalendar = true }, contentAlignment = Alignment.Center
                            ) { Text("📅", fontSize = 13.sp) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (addDayInput.isEmpty()) Text("فقط عدد روز مثلا 10", fontSize = 11.sp, color = theme.mutedColor.copy(0.6f))
                                BasicTextField(value = addDayInput, onValueChange = { addDayInput = it.filter { c -> c.isDigit() } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 13.sp, color = theme.inkColor, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                            }
                            Box(Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(theme.lamp.primary).clickable {
                                val d = addDayInput.toIntOrNull() ?: 0; if (d > 0) { addDays(d); addDayInput = "" }
                            }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("+روز", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(7, 30, 60, 90, 180).forEach { d ->
                                Box(
                                    Modifier.height(24.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.12f)), RoundedCornerShape(7.dp))
                                        .clickable { addDays(d) }.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("+$d", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                            }
                        }
                    }
                }

                // Note + HWID (userlimit) row - compact
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.82f))
                            .border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(12.dp)).padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📝", fontSize = 10.sp)
                                Text("${note.length}/500", fontSize = 9.sp, color = theme.mutedColor)
                            }
                            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.10f)), RoundedCornerShape(8.dp)).padding(6.dp)) {
                                if (note.isEmpty()) Text("یادداشت...", color = theme.mutedColor.copy(0.5f), fontSize = 10.sp)
                                BasicTextField(value = note, onValueChange = { if (it.length <= 500) note = it }, textStyle = TextStyle(color = theme.inkColor, fontSize = 11.sp), modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Box(
                        Modifier.width(92.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.82f))
                            .border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(12.dp)).padding(8.dp)
                    ) {
                        var hwid by remember { mutableStateOf(hwidLimit) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("userlimit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            Box(
                                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.04f)).border(BorderStroke(1.dp, Color.White.copy(0.12f)), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hwid.isEmpty()) Text("∞", fontSize = 12.sp, color = theme.mutedColor.copy(0.6f))
                                BasicTextField(value = hwid, onValueChange = { hwid = it.filter { c -> c.isDigit() }; hwidLimit = hwid }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 13.sp, color = theme.inkColor, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                // Groups selection - new
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.82f))
                        .border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(14.dp)).padding(10.dp)
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

                // Sub URL compact
                initial?.let { user ->
                    if (user.subUrl.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(BorderStroke(1.dp, Color.White.copy(0.12f)), RoundedCornerShape(12.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(user.subUrl, fontSize = 9.5.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            MiniGlassButton("📋") {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            MiniGlassButton("QR") { showQr = true }
                        }
                    }
                }

                // Bottom actions - clean 2 rows
                initial?.let { user ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (user.status == "disabled") GlassGreen.copy(0.12f) else Color.White.copy(0.08f)).border(BorderStroke(1.dp, if (user.status == "disabled") GlassGreen.copy(0.20f) else Color.White.copy(0.12f)), RoundedCornerShape(10.dp)).clickable { onToggle?.invoke() }, contentAlignment = Alignment.Center) {
                            Text(if (user.status == "disabled") "🟢 فعال" else "⚪ غیرفعال", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (user.status == "disabled") GlassGreen else theme.inkColor)
                        }
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(GlassRed.copy(0.08f)).border(BorderStroke(1.dp, GlassRed.copy(0.16f)), RoundedCornerShape(10.dp)).clickable { onDelete?.invoke() }, contentAlignment = Alignment.Center) {
                            Text("🗑 حذف", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(0.06f)).border(BorderStroke(1.dp, Color.White.copy(0.10f)), RoundedCornerShape(9.dp)).clickable { onResetUsage?.invoke() }, contentAlignment = Alignment.Center) {
                            Text("♻️ ریست حجم", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                        Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(0.06f)).border(BorderStroke(1.dp, Color.White.copy(0.10f)), RoundedCornerShape(9.dp)).clickable { onResetExpiry?.invoke() }, contentAlignment = Alignment.Center) {
                            Text("⏰ ریست زمان", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
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
                        val clean = limitGb.replace(',', '.').trim()
                        val lim = if (clean.isBlank()) 0.0 else clean.toDoubleOrNull()
                        val hwidInt = hwidLimit.toIntOrNull()
                        if (username.length !in 3..32 && initial == null) formError = "نام کاربری ۳-۳۲"
                        else if (lim == null || lim < 0) formError = "حجم نامعتبر"
                        else onSave(UserEditorValues(username, lim, note, hwidInt, selectedGroupIds), expireShamsi)
                    }, modifier = Modifier.weight(1f).height(40.dp))
                }
            }
        }
    }

    if (showQr && initial != null && initial.subUrl.isNotEmpty()) SubscriptionQrDialog(user = initial, onDismiss = { showQr = false })
    if (showCalendar) ShamsiCalendarPickerDialog(initialDateShamsi = expireShamsi, onDismiss = { showCalendar = false }, onDateSelected = { expireShamsi = it })
}
