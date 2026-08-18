package com.mrm.pgmanager.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import java.time.LocalDate
import java.util.Locale

/* ──────────────────────────────────────────────────────────────────────────
 *  ساخت/ویرایش کاربر — هم‌زبانِ دیالوگِ جزئیات کاربر
 *
 *  همان سه‌لایه‌ای که در جزئیات پیاده شد اینجا هم برقرار است:
 *    ۱. سربرگِ ثابت (بیرونِ اسکرول)
 *    ۲. بدنهٔ اسکرول‌شونده، تقسیم‌شده به بخش‌های عنوان‌دار: هویت / پلن /
 *       پیشرفته / دسترسی
 *    ۳. نوارِ پایینِ ثابت برای انصراف و ذخیره — قبلاً با اسکرول بالا و پایین
 *       می‌رفت و در فرم‌های بلند باید تا ته اسکرول می‌کردی تا ذخیره را ببینی
 *
 *  همهٔ فیلدها و گزینه‌های قبلی سرجایشان هستند؛ فقط گروه‌بندی، وزنِ بصری و
 *  متن‌ها (که هاردکد بودند) درست شده‌اند.
 * ────────────────────────────────────────────────────────────────────────── */

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
    val store = remember { SessionStore(context) }
    val isCreating = initial == null

    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember {
        mutableStateOf(
            if (initial == null || initial.dataLimit == 0L) ""
            else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')
        )
    }
    var days by remember {
        mutableStateOf(runCatching {
            initial?.let { user ->
                val expires = try {
                    java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                } catch (_: Exception) {
                    LocalDate.parse(user.expire?.take(10) ?: "")
                }
                java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expires).coerceAtLeast(0L).toString()
            } ?: ""
        }.getOrDefault(""))
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hwid by remember { mutableStateOf(initial?.hwidLimit?.toString() ?: "") }
    var groupIds by remember { mutableStateOf(initial?.groupIds ?: emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var active by remember { mutableStateOf(initial?.status != "disabled") }
    var selectedTemplate by remember { mutableStateOf<Int?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = گروه‌ها، 1 = تمپلت‌ها
    var groupSearchQuery by remember { mutableStateOf("") }
    var advancedOpen by remember { mutableStateOf(!isCreating) }

    val randomLabel = stringResource(R.string.ue_random)
    val pickDateLabel = stringResource(R.string.ue_pick_date)
    val closeLabel = stringResource(R.string.ud_close)

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
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
            ) {
                // ── ۱) سربرگِ ثابت
                Row(
                    Modifier.fillMaxWidth()
                        .background(theme.cardSurfaceColor)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        Modifier.size(42.dp).clip(DsRadius.Full)
                            .background(
                                Brush.verticalGradient(
                                    listOf(theme.accentPrimary.copy(0.30f), theme.accentPrimary.copy(0.12f))
                                )
                            )
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full),
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedAppIcon(
                            if (isCreating) AppIcon.UserAdd else AppIcon.Edit,
                            tint = theme.accentPrimary, size = 19.dp
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(if (isCreating) R.string.ue_create_title else R.string.ue_edit_title),
                            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(if (isCreating) R.string.ue_create_sub else R.string.ue_edit_sub),
                            fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        Modifier.size(28.dp).clip(DsRadius.Full)
                            .background(theme.searchBgColor)
                            .semantics { contentDescription = closeLabel }
                            .pressScale(0.9f)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("×", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
                }
                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))

                // ── ۲) بدنه
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── هویت: نام کاربری + وضعیت
                    EditorSection(stringResource(R.string.ue_identity)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                FieldLabel(stringResource(R.string.ue_username))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isCreating) {
                                        UserFormTextField(
                                            value = username,
                                            onValueChange = { username = it },
                                            placeholder = stringResource(R.string.ue_username_hint),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            Modifier.size(40.dp).clip(DsRadius.Md)
                                                .background(theme.accentPrimary.copy(0.12f))
                                                .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.30f)), DsRadius.Md)
                                                .semantics { contentDescription = randomLabel }
                                                .pressScale(0.92f)
                                                .clickable { username = store.readUsernamePattern().randomName() },
                                            contentAlignment = Alignment.Center
                                        ) { RoundedAppIcon(AppIcon.Random, tint = theme.accentPrimary, size = 18.dp) }
                                    } else {
                                        // نام کاربری بعد از ساخت قابل تغییر نیست (مثل پنل وب).
                                        Row(
                                            Modifier.weight(1f).height(40.dp).clip(DsRadius.Md)
                                                .background(theme.searchBgColor)
                                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 13.dp)
                                            MrmText(
                                                initial?.username.orEmpty(),
                                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true
                                            )
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                FieldLabel(stringResource(R.string.ue_status))
                                var statusMenuExpanded by remember { mutableStateOf(false) }
                                val statusColor = if (active) GlassGreen else GlassRed
                                Box {
                                    Row(
                                        Modifier.fillMaxWidth().height(40.dp).clip(DsRadius.Md)
                                            .background(statusColor.copy(0.12f))
                                            .border(BorderStroke(DsBorder.Hairline, statusColor.copy(0.32f)), DsRadius.Md)
                                            .pressScale(0.96f)
                                            .clickable { statusMenuExpanded = true }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(statusColor)
                                        )
                                        Text(
                                            stringResource(if (active) R.string.active else R.string.disabled),
                                            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = statusColor,
                                            modifier = Modifier.weight(1f), maxLines = 1
                                        )
                                        RoundedAppIcon(AppIcon.ChevronDown, tint = statusColor, size = 13.dp)
                                    }
                                    DropdownMenu(
                                        expanded = statusMenuExpanded,
                                        onDismissRequest = { statusMenuExpanded = false },
                                        modifier = Modifier.background(theme.cardSurfaceColor)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.active),
                                                    color = GlassGreen, fontWeight = FontWeight.Bold
                                                )
                                            },
                                            onClick = { active = true; statusMenuExpanded = false }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.disabled),
                                                    color = GlassRed, fontWeight = FontWeight.Bold
                                                )
                                            },
                                            onClick = { active = false; statusMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── پلن: حجم و انقضا، دو چیزی که همیشه تغییر می‌کنند
                    EditorSection(stringResource(R.string.ue_plan)) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FieldLabel(stringResource(R.string.ue_data_limit))
                            UserFormTextField(
                                value = limitGb,
                                onValueChange = { limitGb = it.filter { c -> c.isDigit() || c == '.' } },
                                placeholder = stringResource(R.string.ue_data_limit_hint),
                                keyboardType = KeyboardType.Decimal,
                                leading = AppIcon.Storage
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FieldLabel(stringResource(R.string.ue_expiry))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UserFormTextField(
                                    value = days,
                                    onValueChange = { days = it.filter { c -> c.isDigit() } },
                                    placeholder = stringResource(R.string.ue_expiry_hint),
                                    keyboardType = KeyboardType.Number,
                                    leading = AppIcon.Timer,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    Modifier.size(40.dp).clip(DsRadius.Md)
                                        .background(theme.accentPrimary.copy(0.12f))
                                        .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.30f)), DsRadius.Md)
                                        .semantics { contentDescription = pickDateLabel }
                                        .pressScale(0.92f)
                                        .clickable { showCalendar = true },
                                    contentAlignment = Alignment.Center
                                ) { RoundedAppIcon(AppIcon.Calendar, tint = theme.accentPrimary, size = 18.dp) }
                            }
                            // چیپ‌های افزودنِ سریع — کپسولی، هم‌شکلِ چیپ‌های اپ.
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(7, 30, 60, 90, 180, 365).forEach { value ->
                                    Box(
                                        Modifier.clip(DsRadius.Full)
                                            .background(theme.searchBgColor)
                                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full)
                                            .pressScale(0.93f)
                                            .clickable { days = ((days.toIntOrNull() ?: 0) + value).toString() }
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            stringResource(R.string.ue_add_days, value),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── پیشرفته: جمع‌شونده، چون در ساختِ سریع لازم نیست
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        EditorSectionLabel(stringResource(R.string.ue_advanced))
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Xl)
                                .background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(42.dp).clip(DsRadius.Lg)
                                    .pressScale(0.985f)
                                    .clickable { advancedOpen = !advancedOpen }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                RoundedAppIcon(AppIcon.Tune, tint = theme.mutedColor, size = 16.dp)
                                Text(
                                    stringResource(R.string.ue_advanced),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = theme.inkColor, modifier = Modifier.weight(1f)
                                )
                                val rot by animateFloatAsState(
                                    targetValue = if (advancedOpen) 180f else 0f,
                                    animationSpec = DsAnim.normal(), label = "advChevron"
                                )
                                RoundedAppIcon(
                                    AppIcon.ChevronDown, tint = theme.mutedColor, size = 15.dp,
                                    modifier = Modifier.graphicsLayer { rotationZ = rot }
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = advancedOpen,
                                enter = DsTransition.expandEnter,
                                exit = DsTransition.expandExit
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        FieldLabel(stringResource(R.string.ue_hwid))
                                        UserFormTextField(
                                            value = hwid,
                                            onValueChange = { hwid = it.filter { c -> c.isDigit() } },
                                            placeholder = stringResource(R.string.ue_hwid_hint),
                                            keyboardType = KeyboardType.Number,
                                            leading = AppIcon.Device
                                        )
                                        Text(
                                            stringResource(R.string.ue_hwid_help),
                                            fontSize = 9.sp, color = theme.mutedColor,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        FieldLabel(stringResource(R.string.ue_note))
                                        UserFormTextField(
                                            value = note,
                                            onValueChange = { note = it.take(500) },
                                            placeholder = stringResource(R.string.ue_note_hint),
                                            singleLine = false,
                                            modifier = Modifier.height(70.dp)
                                        )
                                    }
                                    // ردیفِ تنظیماتِ پروکسی (نمایشی — مدیریتش در پنل است)
                                    Row(
                                        Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                            .background(theme.searchBgColor)
                                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 15.dp)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                stringResource(R.string.ue_proxy),
                                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                                            )
                                            Text(
                                                stringResource(R.string.ue_proxy_hint),
                                                fontSize = 9.sp, color = theme.mutedColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── دسترسی: گروه‌ها / تمپلت‌ها
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        EditorSectionLabel(stringResource(R.string.ue_access))
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Xl)
                                .background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // سگمنتِ کپسولی — هم‌شکلِ سگمنت‌های تنظیمات
                            Row(
                                Modifier.fillMaxWidth().height(38.dp).clip(DsRadius.Full)
                                    .background(theme.searchBgColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                listOf(
                                    stringResource(R.string.ue_groups) to AppIcon.Users,
                                    stringResource(R.string.ue_templates) to AppIcon.Template
                                ).forEachIndexed { index, (label, icon) ->
                                    val sel = activeTab == index
                                    Row(
                                        Modifier.weight(1f).fillMaxHeight().clip(DsRadius.Full)
                                            .background(if (sel) theme.accentPrimary else Color.Transparent)
                                            .pressScale(0.96f)
                                            .clickable { activeTab = index },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        RoundedAppIcon(
                                            icon,
                                            tint = if (sel) Color(0xFF422006) else theme.mutedColor,
                                            size = 14.dp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (sel) Color(0xFF422006) else theme.mutedColor
                                        )
                                        if (index == 0 && groupIds.isNotEmpty()) {
                                            Spacer(Modifier.width(5.dp))
                                            Box(
                                                Modifier.clip(DsRadius.Full)
                                                    .background(
                                                        if (sel) Color(0xFF422006).copy(0.18f)
                                                        else theme.accentPrimary.copy(0.18f)
                                                    )
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "${groupIds.size}", fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (sel) Color(0xFF422006) else theme.accentPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedContent(
                                targetState = activeTab,
                                transitionSpec = DsTransition.tabSwitch<Int>(activeTab == 1),
                                label = "editorAccessTab"
                            ) { tab ->
                                if (tab == 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        UserFormTextField(
                                            value = groupSearchQuery,
                                            onValueChange = { groupSearchQuery = it },
                                            placeholder = stringResource(R.string.ue_search_groups),
                                            leading = AppIcon.Search
                                        )
                                        if (filteredGroups.isEmpty()) {
                                            EmptyHint(stringResource(R.string.ue_no_groups))
                                        } else {
                                            filteredGroups.forEach { g ->
                                                val picked = groupIds.contains(g.id)
                                                PickerRow(
                                                    icon = AppIcon.Folder,
                                                    label = g.name,
                                                    selected = picked,
                                                    onClick = {
                                                        groupIds = if (picked) groupIds - g.id else groupIds + g.id
                                                    }
                                                ) {
                                                    CheckboxIcon(selected = picked, onToggle = {
                                                        groupIds = if (picked) groupIds - g.id else groupIds + g.id
                                                    })
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        if (templates.isEmpty()) {
                                            EmptyHint(stringResource(R.string.ue_no_templates))
                                        } else {
                                            templates.forEach { t ->
                                                val picked = selectedTemplate == t.id
                                                PickerRow(
                                                    icon = AppIcon.Template,
                                                    label = t.name,
                                                    selected = picked,
                                                    onClick = {
                                                        selectedTemplate = t.id
                                                        t.dataLimit?.let {
                                                            limitGb = "%.2f".format(Locale.US, it / 1073741824.0)
                                                                .trimEnd('0').trimEnd('.')
                                                        }
                                                        t.expireDuration?.let { days = (it / 86400L).toString() }
                                                    }
                                                ) {
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = picked,
                                                        enter = androidx.compose.animation.scaleIn(DsAnim.bouncy()) +
                                                            androidx.compose.animation.fadeIn(DsAnim.fast()),
                                                        exit = androidx.compose.animation.scaleOut(DsAnim.exit()) +
                                                            androidx.compose.animation.fadeOut(DsAnim.exit())
                                                    ) {
                                                        Box(
                                                            Modifier.size(18.dp).clip(RoundedCornerShape(50))
                                                                .background(theme.accentPrimary),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            RoundedAppIcon(AppIcon.Check, tint = Color(0xFF422006), size = 11.dp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── ۳) نوارِ پایینِ ثابت
                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
                Row(
                    Modifier.fillMaxWidth()
                        .background(theme.cardSurfaceColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.ue_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.35f)
                    )
                    PrimaryButton(
                        text = stringResource(if (isCreating) R.string.ue_create else R.string.ue_save),
                        modifier = Modifier.weight(0.65f),
                        onClick = {
                            val expire = days.toIntOrNull()?.takeIf { it >= 0 }
                                ?.let { JalaliCalendar.isoToShamsi(LocalDate.now().plusDays(it.toLong()).toString()) }
                                ?: ""
                            val hwidValue = hwid.toIntOrNull() ?: 0
                            val values = UserEditorValues(username, limitGb.toDoubleOrNull() ?: 0.0, note, hwidValue, groupIds)
                            if (selectedTemplate != null && isCreating && onSaveWithTemplate != null) {
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

/** عنوانِ کوچکِ بالای هر بخش — هم‌شکلِ دیالوگِ جزئیات کاربر. */
@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = LocalThemeState.current.mutedColor,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** یک بخشِ کارت‌شده با عنوان. */
@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        EditorSectionLabel(title)
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Xl)
                .background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** برچسبِ بالای هر فیلد. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = LocalThemeState.current.mutedColor
    )
}

/** پیامِ «چیزی نیست» با فاصله‌گذاریِ درست به‌جای متنِ لخت. */
@Composable
private fun EmptyHint(text: String) {
    val theme = LocalThemeState.current
    Box(
        Modifier.fillMaxWidth().clip(DsRadius.Lg)
            .background(theme.searchBgColor)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 11.sp, color = theme.mutedColor) }
}

/** ردیفِ انتخابِ گروه/تمپلت. */
@Composable
private fun PickerRow(
    icon: AppIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Lg)
            .background(if (selected) theme.accentPrimary.copy(0.12f) else theme.searchBgColor)
            .border(
                BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(0.40f) else theme.borderColor),
                DsRadius.Lg
            )
            .pressScale(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RoundedAppIcon(icon, tint = if (selected) theme.accentPrimary else theme.mutedColor, size = 15.dp)
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = theme.inkColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/**
 * فیلدِ متنیِ فرم.
 *
 * ⚠️ همان باگِ هم‌ترازیِ کِرسر که در صفحهٔ ورود گرفته شد اینجا هم بود: متنِ
 * راهنما `lineHeight` را از LocalTextStyle (۲۴sp) ارث می‌برد در حالی که
 * استایلِ فیلد lineHeight نداشت، پس کِرسر بالاتر از راهنما می‌نشست. حالا هر
 * دو از یک استایلِ واحد تغذیه می‌شوند و داخلِ یک Box وسط‌چین قرار دارند.
 */
@Composable
private fun UserFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    leading: AppIcon? = null
) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) theme.accentPrimary else theme.borderColor,
        animationSpec = DsAnim.fast(), label = "fieldBorder"
    )
    val fieldStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = theme.inkColor,
        fontWeight = FontWeight.Medium
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (singleLine) 40.dp else Dp.Unspecified)
            .clip(DsRadius.Md)
            .background(theme.searchBgColor)
            .border(BorderStroke(if (isFocused) 1.2.dp else DsBorder.Hairline, borderColor), DsRadius.Md)
            .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leading != null) RoundedAppIcon(leading, tint = theme.mutedColor, size = 14.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = fieldStyle,
            cursorBrush = SolidColor(theme.accentPrimary),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = fieldStyle.copy(color = theme.mutedColor.copy(0.6f)),
                            maxLines = if (singleLine) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
    }
}
