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
