package com.mrm.pgmanager.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.glassBorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ساخت گروهی کاربر: ۱ تا ۱۰۰ کاربر هم‌زمان با الگوی نام، از تمپلت یا با حجم/زمان دستی.
 * پیشرفت زنده نمایش داده می‌شود و خطاها (مثل نام تکراری) متوقف‌کنندهٔ بقیه نیستند.
 */
@Composable
fun BulkCreateUsersDialog(
    session: Session,
    onDismiss: () -> Unit,
    onFinished: (created: Int) -> Unit = {}
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context) }
    var pattern by remember { mutableStateOf(store.readUsernamePattern()) }
    var count by remember { mutableStateOf(10) }
    var useTemplate by remember { mutableStateOf(true) }
    var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(true) }
    var selectedTemplate by remember { mutableStateOf<Int?>(null) }
    var limitGb by remember { mutableStateOf("50") }
    var days by remember { mutableStateOf("30") }
    var note by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    val errors = remember { mutableStateListOf<String>() }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        templatesLoading = true
        templates = runCatching { PanelApi.userTemplates(session) }.getOrDefault(emptyList())
        if (templates.isEmpty()) useTemplate = false
        if (selectedTemplate == null) selectedTemplate = templates.firstOrNull()?.id
        templatesLoading = false
    }

    val canStart = !templatesLoading && (!useTemplate || selectedTemplate != null) &&
        (useTemplate || limitGb.toDoubleOrNull() != null) && pattern.prefix.isNotBlank()

    fun friendlyError(e: Throwable?): String = when {
        e?.message?.contains("409") == true -> "نام تکراری است"
        e?.message?.contains("422") == true -> "دادهٔ نامعتبر"
        e?.message?.contains("401") == true -> "نشست منقضی شده"
        else -> e?.message?.take(60) ?: "خطای نامشخص"
    }

    fun start() {
        if (running || !canStart) return
        store.saveUsernamePattern(pattern)
        running = true; done = false; progress = 0; successCount = 0; errors.clear()
        job = scope.launch {
            val isoExpire = if (!useTemplate) days.toIntOrNull()?.takeIf { it > 0 }?.let { LocalDate.now().plusDays(it.toLong()).toString() } ?: "" else ""
            // در حالت ترتیبی، شمارش از «شروع شمارش» فعلی با گام قابل‌پیگیری پیش می‌رود.
            for (i in 0 until count) {
                if (!isActive) break
                val name = if (pattern.sequential) pattern.sequentialName(i) else pattern.randomName()
                val result = runCatching {
                    if (useTemplate) PanelApi.createUserFromTemplate(session, name, selectedTemplate ?: -1, note)
                    else PanelApi.createUser(session, name, limitGb.toDoubleOrNull() ?: 0.0, isoExpire, note, null, emptyList())
                }
                result.onSuccess { successCount++ }.onFailure { errors += "$name: ${friendlyError(it)}" }
                progress = i + 1
                if (i + 1 < count) delay(150)
            }
            // در حالت ترتیبی، شمارنده جلو می‌رود تا دفعهٔ بعد نام‌های تکراری تولید نشود.
            if (pattern.sequential && successCount > 0) {
                store.saveUsernamePattern(pattern.copy(sequentialStart = pattern.sequentialStart + successCount))
            }
            running = false; done = true
        }
    }

    Dialog(onDismissRequest = { if (!running) onDismiss() }) {
        Box(
            Modifier.fillMaxWidth().imePadding().heightIn(max = 640.dp).clip(DsRadius.Xxl)
                .background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                // هدر
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).clip(DsRadius.Lg).background(GlassGreen.copy(.16f)).border(BorderStroke(DsBorder.Hairline, GlassGreen.copy(.32f)), DsRadius.Lg), contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Users, tint = theme.inkColor, size = 19.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("ساخت گروهی کاربر", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text("تولید چند کاربر هم‌زمان با نام یکدست", fontSize = 11.sp, color = theme.mutedColor)
                    }
                }

                if (!running && !done) {
                    // کارت نام‌گذاری
                    SettingsCard("نام‌گذاری", AppIcon.User) {
                        SegmentedControl(options = listOf("تصادفی", "ترتیبی"), selectedIndex = if (pattern.sequential) 1 else 0) { pattern = pattern.copy(sequential = it == 1) }
                        CompactGlassField(
                            pattern.prefix,
                            { v -> pattern = pattern.copy(prefix = v.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }.take(24)) },
                            "پیشوند نام",
                            leadingAppIcon = AppIcon.Edit, keyboardType = KeyboardType.Ascii, fieldHeight = 38.dp
                        )
                        if (pattern.sequential) SettingsStepper("شروع شمارش از", pattern.sequentialStart, "عدد", 1..999000) { pattern = pattern.copy(sequentialStart = it) }
                        else SettingsStepper("تعداد ارقام", pattern.randomDigits, "رقم", 3..6) { pattern = pattern.copy(randomDigits = it) }
                        Text("نمونه‌ها: ${if (pattern.sequential) "${pattern.sequentialName(0)} ، ${pattern.sequentialName(1)}" else "${pattern.randomName()} ، ${pattern.randomName()}"}", fontSize = 10.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // کارت مشخصات
                    SettingsCard("مشخصات اشتراک", AppIcon.Template) {
                        SegmentedControl(
                            options = listOf("از تمپلت", "دستی"),
                            selectedIndex = if (useTemplate) 0 else 1,
                            enabled = templates.isNotEmpty() || !useTemplate
                        ) { useTemplate = it == 0 }
                        if (templatesLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = theme.accentPrimary)
                                Text("در حال بارگذاری تمپلت‌ها...", fontSize = 10.sp, color = theme.mutedColor)
                            }
                        } else if (useTemplate && templates.isEmpty()) {
                            Text("تمپلتی یافت نشد؛ حالت دستی فعال شد.", fontSize = 10.sp, color = GlassRed)
                        }
                        if (useTemplate && templates.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                templates.forEach { t ->
                                    val picked = selectedTemplate == t.id
                                    Box(Modifier.height(30.dp).clip(DsRadius.Sm).background(if (picked) theme.accentPrimary.copy(.78f) else theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, if (picked) theme.searchBgColor else theme.borderColor), DsRadius.Sm).clickable { selectedTemplate = t.id }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(t.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (picked) Color(0xFF202124) else theme.inkColor, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (!useTemplate) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                CompactGlassField(limitGb, { limitGb = it.filter { c -> c.isDigit() || c == '.' } }, "حجم (GB)", Modifier.weight(1f), KeyboardType.Decimal, fieldHeight = 38.dp)
                                CompactGlassField(days, { days = it.filter(Char::isDigit) }, "مدت (روز)", Modifier.weight(1f), KeyboardType.Number, fieldHeight = 38.dp)
                            }
                        }
                    }
                    // تعداد + یادداشت
                    SettingsCard("تعداد و یادداشت", AppIcon.Tune) {
                        SettingsStepper("تعداد کاربر", count, "عدد", 1..100) { count = it }
                        CompactGlassField(note, { note = it.take(200) }, "یادداشت اختیاری برای همهٔ کاربران", leadingAppIcon = AppIcon.Note, fieldHeight = 38.dp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(.38f))
                        PrimaryButton(if (canStart) "ساخت $count کاربر" else "فرم ناقص است", enabled = canStart, modifier = Modifier.weight(.62f), onClick = { start() })
                    }
                } else {
                    // نمای پیشرفت / نتیجه
                    SettingsCard(if (done) "نتیجهٔ ساخت" else "در حال ساخت...", AppIcon.Users, accent = if (done) GlassGreen else theme.accentPrimary) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!done) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = theme.accentPrimary)
                            Text("$progress از $count", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(theme.searchBgColor)) {
                            Box(Modifier.fillMaxWidth(if (count > 0) progress.toFloat() / count else 0f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (errors.isEmpty()) GlassGreen else GlassAmber))
                        }
                        if (done) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                RoundedAppIcon(AppIcon.CheckCircle, tint = GlassGreen, size = 16.dp)
                                Text("$successCount کاربر ساخته شد", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GlassGreen)
                            }
                            if (errors.isNotEmpty()) {
                                Text("${errors.size} خطا:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).clip(DsRadius.Md).background(GlassRed.copy(.06f)).border(BorderStroke(DsBorder.Hairline, GlassRed.copy(.20f)), DsRadius.Md).padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    errors.forEach { com.mrm.pgmanager.ui.components.MrmText(it, fontSize = 11.sp, isTechnical = true) }
                                }
                            }
                        }
                    }
                    if (!done) {
                        DangerButton("لغو و توقف", onClick = { job?.cancel(); running = false; done = true }, modifier = Modifier.fillMaxWidth())
                    } else {
                        PrimaryButton("بستن", onClick = { onFinished(successCount); onDismiss() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
