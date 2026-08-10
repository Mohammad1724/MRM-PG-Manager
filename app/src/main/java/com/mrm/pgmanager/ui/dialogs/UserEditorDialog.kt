package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import java.time.LocalDate
import java.util.Locale

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
    val context = LocalContext.current
    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val store = remember { SessionStore(context) }
    
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    
    var days by remember { mutableStateOf(runCatching {
        initial?.let { user ->
            val expires = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expires).coerceAtLeast(0L).toString()
        } ?: ""
    }.getOrDefault("")) }
    
    var addGb by remember { mutableStateOf("") }
    var addDaysInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hwid by remember { mutableStateOf(initial?.hwidLimit?.toString() ?: "") }
    var groupIds by remember { mutableStateOf(initial?.groupIds ?: emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var active by remember { mutableStateOf(initial?.status != "disabled") }
    var selectedTemplate by remember { mutableStateOf<Int?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Groups, 1 = Templates
    var groupSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(session) {
        if (session != null) {
            groups = runCatching { PanelApi.groups(session) }.getOrDefault(emptyList())
            templates = runCatching { PanelApi.userTemplates(session) }.getOrDefault(emptyList())
        }
    }

    val filteredGroups = remember(groups, groupSearchQuery) {
        if (groupSearchQuery.isBlank()) groups
        else groups.filter { it.name.contains(groupSearchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .clip(DsRadius.Xxl)
                    .background(theme.cardSurfaceColor)
                    .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Header: Title + Close Button
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundedAppIcon(
                                if (initial == null) AppIcon.UserAdd else AppIcon.Edit,
                                tint = theme.accentPrimary,
                                size = 20.dp
                            )
                            Text(
                                if (initial == null) (if (isFa) "ایجاد کاربر" else "Create User")
                                else (if (isFa) "ویرایش کاربر" else "Edit User"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.inkColor
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Text("×", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        }
                    }

                    // ── Scrollable Fields Column
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Row 1: Username & Status side by side
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Username Input
                            Column(Modifier.weight(0.65f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (isFa) "نام کاربری" else "Username",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.inkColor
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (initial == null) {
                                        UserFormTextField(
                                            value = username,
                                            onValueChange = { username = it },
                                            placeholder = if (isFa) "نام کاربری را وارد کنید" else "Enter username",
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(DsRadius.Md)
                                                .background(theme.accentPrimary.copy(alpha = 0.12f))
                                                .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Md)
                                                .clickable { username = store.readUsernamePattern().randomName() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            RoundedAppIcon(AppIcon.Random, tint = theme.accentPrimary, size = 18.dp)
                                        }
                                    } else {
                                        // Read-only username just like web panel
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clip(DsRadius.Md)
                                                .background(theme.searchBgColor)
                                                .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Md)
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            MrmText(
                                                initial.username,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                isTechnical = true
                                            )
                                        }
                                    }
                                }
                            }

                            // Status Dropdown
                            Column(Modifier.weight(0.35f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (isFa) "وضعیت" else "Status",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.inkColor
                                )
                                var statusMenuExpanded by remember { mutableStateOf(false) }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(DsRadius.Md)
                                        .background(if (active) GlassGreen.copy(alpha = 0.12f) else GlassRed.copy(alpha = 0.12f))
                                        .border(BorderStroke(1.dp, if (active) GlassGreen.copy(alpha = 0.35f) else GlassRed.copy(alpha = 0.35f)), DsRadius.Md)
                                        .clickable { statusMenuExpanded = true }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (active) (if (isFa) "فعال" else "Active") else (if (isFa) "غیرفعال" else "Disabled"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) GlassGreen else GlassRed
                                        )
                                        Text("▾", fontSize = 10.sp, color = theme.mutedColor)
                                    }
                                    DropdownMenu(
                                        expanded = statusMenuExpanded,
                                        onDismissRequest = { statusMenuExpanded = false },
                                        modifier = Modifier.background(theme.cardSurfaceColor)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (isFa) "فعال" else "Active", color = GlassGreen, fontWeight = FontWeight.Bold) },
                                            onClick = { active = true; statusMenuExpanded = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isFa) "غیرفعال" else "Disabled", color = GlassRed, fontWeight = FontWeight.Bold) },
                                            onClick = { active = false; statusMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        // ── Row 2: Data Limit (GB)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isFa) "سقف حجم (GB)" else "Data Limit (GB)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.inkColor
                            )
                            UserFormTextField(
                                value = limitGb,
                                onValueChange = { limitGb = it.filter { c -> c.isDigit() || c == '.' } },
                                placeholder = if (isFa) "مثلاً 30 (برای نامحدود خالی بگذارید)" else "e.g. 30 (leave empty for unlimited)",
                                keyboardType = KeyboardType.Decimal
                            )
                        }

                        // ── Row 3: Expiry Date & Quick Selection
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isFa) "تاریخ انقضا" else "Expiry Date",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.inkColor
                            )
                            // Quick select chips
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val quickSelections = listOf(
                                    (if (isFa) "+7 روز" else "+7d") to 7,
                                    (if (isFa) "+30 روز" else "+30d") to 30,
                                    (if (isFa) "+60 روز" else "+60d") to 60,
                                    (if (isFa) "+90 روز" else "+90d") to 90,
                                    (if (isFa) "+180 روز" else "+6m") to 180,
                                    (if (isFa) "+365 روز" else "+1y") to 365
                                )
                                quickSelections.forEach { (label, value) ->
                                    Box(
                                        Modifier
                                            .clip(DsRadius.Sm)
                                            .background(theme.searchBgColor)
                                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                                            .clickable { days = ((days.toIntOrNull() ?: 0) + value).toString() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                                    }
                                }
                            }
                            // Expiry field with Calendar button
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UserFormTextField(
                                    value = days,
                                    onValueChange = { days = it.filter { c -> c.isDigit() } },
                                    placeholder = if (isFa) "تعداد روزهای انقضا (مثلاً 30)" else "Days to expire (e.g. 30)",
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(DsRadius.Md)
                                        .background(theme.accentPrimary.copy(alpha = 0.12f))
                                        .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Md)
                                        .clickable { showCalendar = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    RoundedAppIcon(AppIcon.Calendar, tint = theme.accentPrimary, size = 18.dp)
                                }
                            }
                        }

                        // ── Row 4: HWID Limit
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isFa) "محدودیت دستگاه" else "HWID Limit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.inkColor
                            )
                            UserFormTextField(
                                value = hwid,
                                onValueChange = { hwid = it.filter { c -> c.isDigit() } },
                                placeholder = if (isFa) "خالی = پیش‌فرض، 0 = نامحدود" else "Empty = default, 0 = unlimited"
                            )
                            Text(
                                if (isFa) "خالی = استفاده از سیاست پیش‌فرض پنل. 0 = بدون محدودیت دستگاه."
                                else "Empty = use default policy. 0 = unlimited and exempt from HWID.",
                                fontSize = 9.sp,
                                color = theme.mutedColor,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }

                        // ── Row 5: Internal Note
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isFa) "یادداشت داخلی" else "Note",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.inkColor
                            )
                            UserFormTextField(
                                value = note,
                                onValueChange = { note = it.take(500) },
                                placeholder = if (isFa) "یادداشت ادمین..." else "Note...",
                                singleLine = false,
                                modifier = Modifier.height(66.dp)
                            )
                        }

                        // ── Row 6: Proxy Settings (Decorative web style)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DsRadius.Md)
                                .background(theme.searchBgColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 16.dp)
                                Text(if (isFa) "تنظیمات پروکسی" else "Proxy Settings", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                            }
                            Text("▾", fontSize = 10.sp, color = theme.mutedColor)
                        }

                        // ── Row 7: Tabs (Groups / Templates)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(DsRadius.Md)
                                    .background(theme.searchBgColor)
                                    .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Md)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tabs = listOf(
                                    (if (isFa) "گروه‌ها" else "Groups") to AppIcon.Users,
                                    (if (isFa) "تمپلت‌ها" else "Templates") to AppIcon.Template
                                )
                                tabs.forEachIndexed { index, (label, icon) ->
                                    val sel = activeTab == index
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(DsRadius.Sm)
                                            .background(if (sel) theme.accentPrimary else Color.Transparent)
                                            .clickable { activeTab = index },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            RoundedAppIcon(icon, tint = if (sel) Color(0xFF1A1A1A) else theme.mutedColor, size = 14.dp)
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) Color(0xFF1A1A1A) else theme.mutedColor)
                                        }
                                    }
                                }
                            }

                            // Tab contents
                            if (activeTab == 0) {
                                // Groups list with Search
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Search groups
                                    UserFormTextField(
                                        value = groupSearchQuery,
                                        onValueChange = { groupSearchQuery = it },
                                        placeholder = if (isFa) "جست‌وجوی گروه‌ها..." else "Search Groups",
                                        modifier = Modifier.height(36.dp)
                                    )
                                    
                                    if (filteredGroups.isEmpty()) {
                                        Text(if (isFa) "گروهی یافت نشد" else "No groups found", fontSize = 11.sp, color = theme.mutedColor, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            filteredGroups.forEach { g ->
                                                val picked = groupIds.contains(g.id)
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(DsRadius.Md)
                                                        .background(theme.searchBgColor)
                                                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                                        .clickable {
                                                            groupIds = if (picked) groupIds - g.id else groupIds + g.id
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    CheckboxIcon(selected = picked, onToggle = {
                                                        groupIds = if (picked) groupIds - g.id else groupIds + g.id
                                                    })
                                                    Text(g.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Templates list
                                if (templates.isEmpty()) {
                                    Text(if (isFa) "تمپلتی یافت نشد" else "No templates found", fontSize = 11.sp, color = theme.mutedColor, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        templates.forEach { t ->
                                            val picked = selectedTemplate == t.id
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(DsRadius.Md)
                                                    .background(if (picked) theme.accentPrimary.copy(alpha = 0.12f) else theme.searchBgColor)
                                                    .border(BorderStroke(DsBorder.Hairline, if (picked) theme.accentPrimary else theme.borderColor), DsRadius.Md)
                                                    .clickable {
                                                        selectedTemplate = t.id
                                                        t.dataLimit?.let { limitGb = "%.2f".format(Locale.US, it / 1073741824.0).trimEnd('0').trimEnd('.') }
                                                        t.expireDuration?.let { days = (it / 86400L).toString() }
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    RoundedAppIcon(AppIcon.Template, tint = if (picked) theme.accentPrimary else theme.mutedColor, size = 16.dp)
                                                    Text(t.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                                }
                                                if (picked) {
                                                    Box(
                                                        Modifier
                                                            .size(18.dp)
                                                            .clip(RoundedCornerShape(50))
                                                            .background(theme.accentPrimary),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        RoundedAppIcon(AppIcon.Check, tint = Color(0xFF1A1A1A), size = 11.dp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Bottom Action Buttons
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SecondaryButton(
                            text = if (isFa) "انصراف" else "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.35f)
                        )
                        PrimaryButton(
                            text = if (initial == null) (if (isFa) "ایجاد کاربر" else "Create") else (if (isFa) "ذخیره تغییرات" else "Save Changes"),
                            modifier = Modifier.weight(0.65f),
                            onClick = {
                                val expire = days.toIntOrNull()?.takeIf { it >= 0 }?.let { JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(it.toLong()).toString()) } ?: ""
                                val hwidValue = hwid.toIntOrNull() ?: 0
                                val values = UserEditorValues(username, limitGb.toDoubleOrNull() ?: 0.0, note, hwidValue, groupIds)
                                if (selectedTemplate != null && initial == null && onSaveWithTemplate != null) {
                                    onSaveWithTemplate(username, selectedTemplate!!, note)
                                } else {
                                    onSave(values, expire)
                                    if (initial != null && active != (initial.status != "disabled")) {
                                        onToggle?.invoke()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCalendar) {
        ShamsiCalendarPickerDialog(
            initialDateShamsi = JalaliCalendar.todayJalali().toString(),
            onDismiss = { showCalendar = false }
        ) { shamsi ->
            days = runCatching {
                java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    LocalDate.parse(JalaliCalendar.shamsiToIso(shamsi).take(10))
                ).coerceAtLeast(0L).toString()
            }.getOrDefault("")
        }
    }
}

/** یک فرم فیلد متنی فوق‌العاده شیک و مدرن منطبق با پنل تحت وب پاسارگارد. */
@Composable
private fun UserFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (singleLine) 40.dp else Dp.Unspecified)
            .clip(DsRadius.Md)
            .background(theme.searchBgColor)
            .border(
                BorderStroke(
                    width = if (isFocused) 1.2.dp else 1.dp,
                    color = if (isFocused) theme.accentPrimary else theme.borderColor
                ),
                DsRadius.Md
            )
            .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = theme.mutedColor.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}
