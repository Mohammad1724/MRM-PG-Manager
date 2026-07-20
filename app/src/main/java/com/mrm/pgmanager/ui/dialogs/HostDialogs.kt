package com.mrm.pgmanager.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.PanelHostEditValues
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState

@Composable
fun HostEditorDialog(
    initial: PanelHost?,
    inbounds: List<String>,
    onDismiss: () -> Unit,
    onSave: (PanelHostEditValues) -> Unit
) {
    val theme = LocalThemeState.current
    var remark by remember { mutableStateOf(initial?.remark ?: "") }
    var addressStr by remember { mutableStateOf(initial?.address?.joinToString(", ") ?: "") }
    var portStr by remember { mutableStateOf(initial?.port?.toString() ?: "") }
    var inboundTag by remember { mutableStateOf(initial?.inboundTag ?: "") }
    var sniStr by remember { mutableStateOf(initial?.sni?.joinToString(", ") ?: "") }
    var hostStr by remember { mutableStateOf(initial?.host?.joinToString(", ") ?: "") }
    var path by remember { mutableStateOf(initial?.path ?: "") }
    var security by remember { mutableStateOf(initial?.security ?: "inbound_default") }
    var fingerprint by remember { mutableStateOf(initial?.fingerprint ?: "none") }
    var alpnList by remember { mutableStateOf(initial?.alpn ?: emptyList()) }
    var allowInsecure by remember { mutableStateOf(initial?.allowInsecure ?: false) }
    var isDisabled by remember { mutableStateOf(initial?.isDisabled ?: false) }
    var priorityStr by remember { mutableStateOf(initial?.priority?.toString() ?: "1") }
    var formError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (theme.isDark) Color(0xFF1E1E24).copy(0.96f) else Color.White.copy(0.94f),
                            if (theme.isDark) Color(0xFF18181E).copy(0.94f) else Color(0xFFFFF7E6).copy(0.90f)
                        )
                    )
                )
                .border(BorderStroke(1.2.dp, Color.White.copy(0.42f)), RoundedCornerShape(26.dp))
                .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = theme.lamp.primary.copy(0.18f))
        ) {
            Box(
                Modifier.size(240.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-60).dp)
                    .background(Brush.radialGradient(listOf(theme.lamp.spotHigh.copy(0.40f), theme.lamp.spotLow.copy(0.18f), Color.Transparent)), RoundedCornerShape(200.dp))
                    .blur(16.dp)
            )

            Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (initial == null) "🌐 ساخت هاست جدید" else "⚙️ ویرایش هاست: ${initial.remark}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text(if (initial == null) "پیکربندی دامنه‌ها و Inbounds" else "شناسه هاست #${initial.id}", fontSize = 10.sp, color = theme.mutedColor)
                    }
                }

                // Remark
                HostCompactField(value = remark, onValueChange = { remark = it }, placeholder = "عنوان هاست (مثلا MCI-CDN)", leading = "📝")

                // Address
                HostCompactField(value = addressStr, onValueChange = { addressStr = it }, placeholder = "آدرس/دامنه‌ها (با کاما جدا کنید)", leading = "🌐")

                // Port & Priority
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        HostCompactField(value = portStr, onValueChange = { portStr = it.filter { c -> c.isDigit() } }, placeholder = "پورت (خالی=پیش‌فرض)", leading = "🔌", keyboardType = KeyboardType.Number)
                    }
                    Box(Modifier.weight(1f)) {
                        HostCompactField(value = priorityStr, onValueChange = { priorityStr = it.filter { c -> c.isDigit() } }, placeholder = "اولویت (مثلا 1)", leading = "⭐", keyboardType = KeyboardType.Number)
                    }
                }

                // Inbound Tag selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎯 انتخاب Inbound Tag:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    if (inbounds.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            inbounds.forEach { tag ->
                                val sel = inboundTag == tag
                                Box(
                                    Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) theme.lamp.primary else Color.Black.copy(0.05f))
                                        .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.18f)), RoundedCornerShape(8.dp))
                                        .clickable { inboundTag = if (sel) "" else tag }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else theme.inkColor) }
                            }
                        }
                    }
                    HostCompactField(value = inboundTag, onValueChange = { inboundTag = it }, placeholder = "یا دستی تایپ کنید (مثلا VMess-TCP)", leading = "🎯")
                }

                // Security selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔒 نوع امنیت (Security):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("inbound_default" to "پیش‌فرض", "none" to "بدون TLS", "tls" to "TLS فعال").forEach { (sec, label) ->
                            val sel = security == sec
                            Box(
                                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) theme.lamp.primary else Color.Black.copy(0.05f))
                                    .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.18f)), RoundedCornerShape(9.dp))
                                    .clickable { security = sec },
                                contentAlignment = Alignment.Center
                            ) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else theme.inkColor) }
                        }
                    }
                }

                // TLS specific fields
                if (security == "tls") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.03f)).border(BorderStroke(1.dp, Color.White.copy(0.12f)), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HostCompactField(value = sniStr, onValueChange = { sniStr = it }, placeholder = "دامنه‌های SNI (با کاما)", leading = "🛡️")
                        HostCompactField(value = hostStr, onValueChange = { hostStr = it }, placeholder = "هدر Host (با کاما)", leading = "🏷️")

                        Text("🔏 Fingerprint:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("none", "chrome", "firefox", "safari", "ios", `android`, "edge", "random", "randomized").forEach { fp ->
                                val sel = fingerprint == fp
                                Box(
                                    Modifier.height(26.dp).clip(RoundedCornerShape(7.dp))
                                        .background(if (sel) theme.lamp.primary else Color.Black.copy(0.05f))
                                        .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.14f)), RoundedCornerShape(7.dp))
                                        .clickable { fingerprint = fp }.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(fp, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else theme.inkColor) }
                            }
                        }

                        Text("🚀 ALPN Protocols:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("h3", "h2", "http/1.1").forEach { proto ->
                                val sel = alpnList.contains(proto)
                                Box(
                                    Modifier.height(28.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) theme.lamp.primary else Color.Black.copy(0.05f))
                                        .border(BorderStroke(1.dp, if (sel) theme.lamp.primary else Color.White.copy(0.14f)), RoundedCornerShape(8.dp))
                                        .clickable { alpnList = if (sel) alpnList - proto else alpnList + proto }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(proto, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else theme.inkColor) }
                            }
                        }

                        Row(Modifier.fillMaxWidth().clickable { allowInsecure = !allowInsecure }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (allowInsecure) theme.lamp.primary else Color.Black.copy(0.06f)), contentAlignment = Alignment.Center) {
                                if (allowInsecure) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("تایید گواهی SSL نامعتبر (Allow Insecure)", fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Path
                HostCompactField(value = path, onValueChange = { path = it }, placeholder = "مسیر WebSocket / gRPC (مثلا /cdn)", leading = "🛤️")

                // Status Toggle
                val actionBg = if (theme.isDark) Color(0xFF2C2C34) else Color(0xFFE8E4DA)
                val actionBorder = if (theme.isDark) Color(0xFF7E7C88) else Color(0xFF88847A)
                Box(
                    Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (isDisabled) actionBg else GlassGreen.copy(0.18f))
                        .border(BorderStroke(1.2.dp, if (isDisabled) actionBorder else GlassGreen), RoundedCornerShape(10.dp))
                        .clickable { isDisabled = !isDisabled },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isDisabled) "⚪ وضعیت: غیرفعال" else "🟢 وضعیت: فعال", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDisabled) theme.inkColor else GlassGreen)
                }

                formError?.let {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GlassRed.copy(0.10f)).border(BorderStroke(1.dp, GlassRed.copy(0.20f)), RoundedCornerShape(10.dp)).padding(8.dp)) {
                        Text(it, color = GlassRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f).height(40.dp))
                    PrimarySaveButton("ذخیره", onClick = {
                        val addrList = addressStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val sniList = sniStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val hostList = hostStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val portInt = portStr.toIntOrNull()
                        val prioInt = priorityStr.toIntOrNull() ?: 1

                        if (remark.isBlank()) {
                            formError = "عنوان هاست (Remark) نمی‌تواند خالی باشد"
                        } else {
                            onSave(
                                PanelHostEditValues(
                                    remark = remark.trim(),
                                    address = addrList,
                                    inboundTag = inboundTag.trim(),
                                    port = portInt,
                                    sni = sniList,
                                    host = hostList,
                                    path = path.trim().ifEmpty { null },
                                    security = security,
                                    fingerprint = fingerprint,
                                    alpn = alpnList,
                                    allowInsecure = allowInsecure,
                                    isDisabled = isDisabled,
                                    priority = prioInt
                                )
                            )
                        }
                    }, modifier = Modifier.weight(1f).height(40.dp))
                }
            }
        }
    }
}

@Composable
fun HostDeleteConfirmDialog(
    host: PanelHost,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(GlassShape).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), GlassShape).padding(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("حذف هاست «${host.remark}»؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                Text("این هاست برای همیشه از سیستم حذف خواهد شد و غیرقابل بازگشت است.", color = theme.mutedColor, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    GlassButton("حذف", onClick = onDelete, modifier = Modifier.weight(1f), isRed = true)
                }
            }
        }
    }
}

@Composable
private fun HostCompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    leading: String = ""
) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (theme.isDark) 0.08f else 0.72f))
            .border(BorderStroke(1.dp, Color.White.copy(0.18f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (leading.isNotEmpty()) Text(leading, fontSize = 13.sp)
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = theme.mutedColor.copy(alpha = 0.6f), fontSize = 11.5.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
