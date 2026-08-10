package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

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
    fun card() = Modifier.fillMaxWidth().clip(DsRadius.Lg)
        .background(theme.cardSurfaceColor)
        .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg)
        .padding(10.dp)
    fun addDays(value: Int) { days = ((days.toIntOrNull() ?: 0) + value).toString() }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme) {
            Box(Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(DsRadius.Xxl).background(theme.cardSurfaceColor).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl)) {
            Column(Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundedAppIcon(AppIcon.Edit, tint = theme.inkColor, size = 18.dp)
                    Text(if (initial == null) stringResource(R.string.create_user_title) else stringResource(R.string.edit_user_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                }
                // اطلاعات پایه
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.basic_info), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (initial == null) {
                            // هنگام ساخت کاربر، تولید نام تصادفی دوباره در دسترس است.
                            CompactGlassField(username, { username = it }, "نام کاربری", Modifier.weight(1f), KeyboardType.Ascii, "")
                            Box(Modifier.size(42.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(.18f)).clickable { username = store.readUsernamePattern().randomName() }, contentAlignment = Alignment.Center) {
                                RoundedAppIcon(AppIcon.Random, tint = theme.inkColor, size = 19.dp)
                            }
                        } else {
                            // در حالت ویرایش، نام مانند پنل PasarGuard فقط برای مشاهده است.
                            // نام در حالت ویرایش فقط نمایش داده می‌شود و عمداً بسیار کوتاه است.
                            Box(Modifier.weight(1f).height(26.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(1.dp, tileBorderColor(theme.isDark)), DsRadius.Sm).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                                MrmText(initial.username, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                            }
                        }
                        if (initial != null) {
                            Box(Modifier.height(26.dp).clip(DsRadius.Sm).background(if (active) GlassGreen.copy(.14f) else GlassRed.copy(.12f)).border(BorderStroke(1.dp, if (active) GlassGreen else GlassRed), DsRadius.Sm).clickable { active = !active }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text(if (active) "فعال" else "غیرفعال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) GlassGreen else GlassRed) }
                        }
                    }
                }
                // حجم و زمان
                Column(card(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(stringResource(R.string.volume_and_time), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    // حجم کل مستقیماً قابل تعیین است؛ کادر +GB فقط مقدار افزایشی را به آن اضافه می‌کند.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactGlassField(limitGb, { limitGb = it.filter { c -> c.isDigit() || c == '.' } }, "حجم کل (GB)", Modifier.weight(1.35f), KeyboardType.Decimal, "", fieldHeight = 34.dp)
                        CompactGlassField(addGb, { addGb = it.filter { c -> c.isDigit() || c == '.' } }, "+ GB", Modifier.weight(.65f), KeyboardType.Decimal, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(DsRadius.Sm).background(theme.accentPrimary.copy(.18f)).clickable { val add = addGb.toDoubleOrNull() ?: 0.0; if (add > 0) { limitGb = ((limitGb.toDoubleOrNull() ?: 0.0) + add).toString(); addGb = "" } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 18.dp) }
                    }
                    // زمان کل نیز مستقل قابل ویرایش است و +روز به مقدار فعلی افزوده می‌شود.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactGlassField(days, { days = it.filter(Char::isDigit) }, "زمان کل", Modifier.weight(1.15f), KeyboardType.Number, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(DsRadius.Sm).background(theme.accentPrimary.copy(.18f)).clickable { showCalendar = true }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Calendar, tint = theme.inkColor, size = 18.dp) }
                        CompactGlassField(addDaysInput, { addDaysInput = it.filter(Char::isDigit) }, "+ روز", Modifier.weight(.65f), KeyboardType.Number, "", fieldHeight = 34.dp)
                        Box(Modifier.size(34.dp).clip(DsRadius.Sm).background(theme.accentPrimary.copy(.18f)).clickable { val add = addDaysInput.toIntOrNull() ?: 0; if (add > 0) { days = ((days.toIntOrNull() ?: 0) + add).toString(); addDaysInput = "" } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 18.dp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(7, 30, 60, 90).forEach { value -> MiniGlassButton("+$value روز", Modifier.weight(1f)) { days = ((days.toIntOrNull() ?: 0) + value).toString() } } }
                }
                // دسترسی و یادداشت
                Column(card(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.access_and_details), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactGlassField(if (hwid == "0" || hwid.isEmpty()) "" else hwid, { v -> hwid = v.filter(Char::isDigit).ifBlank { "0" } }, "محدودیت دستگاه", Modifier.weight(.52f), KeyboardType.Number, "", leadingAppIcon = AppIcon.Device, fieldHeight = 30.dp)
                        Box(Modifier.weight(.48f).height(30.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { hwid = "0" }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text("نامحدود", fontSize = 10.sp, color = theme.mutedColor) }
                    }
                    Text("یادداشت داخلی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Sm).background(Color.White.copy(alpha = if (theme.isDark) .06f else .70f)).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Lg).padding(10.dp)) { BasicTextField(note, { note = it.take(500) }, textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp), modifier = Modifier.fillMaxSize()) }
                }
                // گروه‌ها
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.groups_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (groups.isEmpty()) Text("گروهی یافت نشد", fontSize = 10.sp, color = theme.mutedColor) else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { groups.forEach { g -> val picked = groupIds.contains(g.id); Box(Modifier.height(32.dp).clip(DsRadius.Sm).background(if (picked) theme.accentPrimary.copy(.78f) else theme.searchBgColor).border(BorderStroke(1.dp, if (picked) theme.searchBgColor else theme.borderColor), DsRadius.Sm).clickable { groupIds = if (picked) groupIds - g.id else groupIds + g.id }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (picked) RoundedAppIcon(AppIcon.Check, tint = Color(0xFF202124), size = 12.dp); Text(g.name, fontSize = 10.sp, color = if (picked) Color(0xFF202124) else theme.inkColor) } } } }
                }
                // تمپلت‌ها
                Column(card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.templates_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (templates.isEmpty()) Text("تمپلتی یافت نشد", fontSize = 10.sp, color = theme.mutedColor) else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { templates.forEach { t -> val picked = selectedTemplate == t.id; Box(Modifier.height(32.dp).clip(DsRadius.Sm).background(if (picked) theme.accentPrimary.copy(.78f) else theme.searchBgColor).border(BorderStroke(1.dp, if (picked) theme.searchBgColor else theme.borderColor), DsRadius.Sm).clickable {
                            selectedTemplate = t.id
                            // انتخاب تمپلت، مقادیر واقعی آن را فوراً در فیلدهای فرم نشان می‌دهد.
                            t.dataLimit?.let { limitGb = "%.2f".format(Locale.US, it / 1073741824.0).trimEnd('0').trimEnd('.') }
                            t.expireDuration?.let { days = (it / 86400L).toString() }
                        }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text(t.name, fontSize = 10.sp, color = if (picked) Color(0xFF202124) else theme.inkColor) } } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("انصراف", onDismiss, Modifier.weight(.35f))
                    PrimaryButton(stringResource(R.string.save_changes), modifier = Modifier.weight(.65f), onClick = {
                        val expire = days.toIntOrNull()?.takeIf { it >= 0 }?.let { JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(it.toLong()).toString()) } ?: ""
                        val hwidValue = hwid.toIntOrNull() ?: 0
                        val values = UserEditorValues(username, limitGb.toDoubleOrNull() ?: 0.0, note, hwidValue, groupIds)
                        if (selectedTemplate != null && initial == null && onSaveWithTemplate != null) onSaveWithTemplate(username, selectedTemplate!!, note) else { onSave(values, expire); if (initial != null && active != (initial.status != "disabled")) onToggle?.invoke() }
                    })
                }
            }
        }
      }
    }
    if (showCalendar) ShamsiCalendarPickerDialog(JalaliCalendar.todayJalali().toString(), { showCalendar = false }) { shamsi -> days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(JalaliCalendar.shamsiToIso(shamsi).take(10))).coerceAtLeast(0L).toString() }.getOrDefault("") }
    if (resetUsage) ConfirmActionDialog("ریست حجم مصرف‌شده؟", "مصرف این کاربر صفر می‌شود.", onDismiss = { resetUsage = false }, onConfirm = { resetUsage = false; onResetUsage?.invoke() })
}
