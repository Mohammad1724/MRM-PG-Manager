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
 *  ویرایش کاربر — نسخه فشرده داشبوردی v0.8.2
 *  هم‌زبان با جزئیات جدید:
 *  - هدر 28dp مربع گرد، بدون گرادینت
 *  - کارت‌ها Md (نه Xl)، پدینگ 8dp
 *  - فیلدها 32dp ارتفاع، فونت 12sp
 *  - چیپ‌ها کوچک 26dp
 *  - فاصله‌ها 8dp
 * ────────────────────────────────────────────────────────────────────────── */

@Composable
fun UserEditorDialog(
    initial: PanelUser?,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?,
    onSaveWithTemplate: ((username: String, templateId: Int, note: String) -> Unit)? = null,
    onApplyTemplateToUser: ((templateId: Int, note: String) -> Unit)? = null,
    session: Session? = null
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
    var nextPlanTemplate by remember { mutableStateOf(initial?.nextPlan?.templateId) }
    var nextPlanCarry by remember { mutableStateOf(initial?.nextPlan?.addRemainingTraffic ?: false) }
    var nextPlanMenu by remember { mutableStateOf(false) }
    var resetStrategy by remember { mutableStateOf(TemplateOptions.RESET_NO_RESET) }
    var autoDeleteDays by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var active by remember { mutableStateOf(initial?.status != "disabled") }
    var selectedTemplate by remember { mutableStateOf<Int?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }
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
        if (groupSearchQuery.isBlank()) groups else groups.filter { it.name.contains(groupSearchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 720.dp).clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
            ) {
                // هدر جدید 28dp
                Row(
                    Modifier.fillMaxWidth().background(theme.cardSurfaceColor).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedAppIcon(if (isCreating) AppIcon.UserAdd else AppIcon.Edit, tint = theme.mutedColor, size = 14.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(stringResource(if (isCreating) R.string.ue_create_title else R.string.ue_edit_title), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(if (isCreating) R.string.ue_create_sub else R.string.ue_edit_sub), fontSize = 9.5.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor)
                            .semantics { contentDescription = closeLabel }.pressScale(0.9f).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("×", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
                }
                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // هویت
                    EditorSection(stringResource(R.string.ue_identity)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(0.60f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FieldLabel(stringResource(R.string.ue_username))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (isCreating) {
                                        UserFormTextField(value = username, onValueChange = { username = it }, placeholder = stringResource(R.string.ue_username_hint), modifier = Modifier.weight(1f))
                                        Box(
                                            Modifier.size(32.dp).clip(DsRadius.Md).background(theme.searchBgColor)
                                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                                .semantics { contentDescription = randomLabel }.pressScale(0.92f)
                                                .clickable { username = store.readUsernamePattern().randomName() },
                                            contentAlignment = Alignment.Center
                                        ) { RoundedAppIcon(AppIcon.Random, tint = theme.mutedColor, size = 14.dp) }
                                    } else {
                                        Row(
                                            Modifier.weight(1f).height(32.dp).clip(DsRadius.Md).background(theme.searchBgColor)
                                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 11.dp)
                                            MrmText(initial?.username.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(0.40f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FieldLabel(stringResource(R.string.ue_status))
                                var statusMenuExpanded by remember { mutableStateOf(false) }
                                val statusColor = if (active) GlassGreen else GlassRed
                                Box {
                                    Row(
                                        Modifier.fillMaxWidth().height(32.dp).clip(DsRadius.Md).background(statusColor.copy(0.10f))
                                            .border(BorderStroke(DsBorder.Hairline, statusColor.copy(0.25f)), DsRadius.Md).pressScale(0.96f)
                                            .clickable { statusMenuExpanded = true }.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(statusColor))
                                        Text(stringResource(if (active) R.string.active else R.string.disabled), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.weight(1f), maxLines = 1)
                                        RoundedAppIcon(AppIcon.ChevronDown, tint = statusColor, size = 11.dp)
                                    }
                                    DropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }, modifier = Modifier.background(theme.cardSurfaceColor)) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.active), color = GlassGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp) }, onClick = { active = true; statusMenuExpanded = false })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.disabled), color = GlassRed, fontWeight = FontWeight.Bold, fontSize = 12.sp) }, onClick = { active = false; statusMenuExpanded = false })
                                    }
                                }
                            }
                        }
                    }

                    // پلن
                    EditorSection(stringResource(R.string.ue_plan)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FieldLabel(stringResource(R.string.ue_data_limit))
                            UserFormTextField(
                                value = limitGb,
                                onValueChange = { raw -> val normalized = normalizePersianDigits(raw); limitGb = normalized.filter { c -> c.isDigit() || c == '.' } },
                                placeholder = stringResource(R.string.ue_data_limit_hint),
                                keyboardType = KeyboardType.Decimal, leading = AppIcon.Storage
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FieldLabel(stringResource(R.string.ue_expiry))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                UserFormTextField(
                                    value = days, onValueChange = { raw -> val normalized = normalizePersianDigits(raw); days = normalized.filter { c -> c.isDigit() } },
                                    placeholder = stringResource(R.string.ue_expiry_hint), keyboardType = KeyboardType.Number, leading = AppIcon.Timer, modifier = Modifier.weight(1f)
                                )
                                Box(
                                    Modifier.size(32.dp).clip(DsRadius.Md).background(theme.searchBgColor)
                                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                        .semantics { contentDescription = pickDateLabel }.pressScale(0.92f).clickable { showCalendar = true },
                                    contentAlignment = Alignment.Center
                                ) { RoundedAppIcon(AppIcon.Calendar, tint = theme.mutedColor, size = 14.dp) }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(7, 30, 60, 90, 180, 365).forEach { value ->
                                    Box(
                                        Modifier.height(26.dp).clip(DsRadius.Full).background(theme.searchBgColor)
                                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full).pressScale(0.93f)
                                            .clickable { val cur = normalizePersianDigits(days).toIntOrNull() ?: 0; days = (cur + value).toString() }
                                            .padding(horizontal = 9.dp), contentAlignment = Alignment.Center
                                    ) { Text(stringResource(R.string.ue_add_days, value), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
                                }
                            }
                        }
                    }

                    // دسترسی
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        EditorSectionLabel(stringResource(R.string.ue_access))
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(30.dp).clip(DsRadius.Full).background(theme.searchBgColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full).padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf(stringResource(R.string.ue_groups) to AppIcon.Users, stringResource(R.string.ue_templates) to AppIcon.Template).forEachIndexed { index, (label, icon) ->
                                    val sel = activeTab == index
                                    Row(
                                        Modifier.weight(1f).fillMaxHeight().clip(DsRadius.Full).background(if (sel) theme.inkColor else Color.Transparent)
                                            .pressScale(0.96f).clickable { activeTab = index; if (index == 0) selectedTemplate = null },
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                                    ) {
                                        RoundedAppIcon(icon, tint = if (sel) theme.cardSurfaceColor else theme.mutedColor, size = 12.dp)
                                        Spacer(Modifier.width(5.dp))
                                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (sel) theme.cardSurfaceColor else theme.mutedColor)
                                        if (index == 0 && groupIds.isNotEmpty()) {
                                            Spacer(Modifier.width(5.dp))
                                            Box(Modifier.defaultMinSize(minWidth = 14.dp, minHeight = 14.dp).clip(RoundedCornerShape(50)).background(if (sel) theme.cardSurfaceColor else theme.inkColor).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                                Text("${groupIds.size}", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = if (sel) theme.inkColor else theme.cardSurfaceColor, style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedContent(targetState = activeTab, transitionSpec = DsTransition.tabSwitch<Int>(activeTab == 1), label = "editorAccessTab") { tab ->
                                if (tab == 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        UserFormTextField(value = groupSearchQuery, onValueChange = { groupSearchQuery = it }, placeholder = stringResource(R.string.ue_search_groups), leading = AppIcon.Search)
                                        if (filteredGroups.isEmpty()) EmptyHint(stringResource(R.string.ue_no_groups))
                                        else filteredGroups.forEach { g ->
                                            val picked = groupIds.contains(g.id)
                                            PickerRow(icon = AppIcon.Folder, label = g.name, selected = picked, onClick = { groupIds = if (picked) groupIds - g.id else groupIds + g.id }) {
                                                CheckboxIcon(selected = picked, onToggle = { groupIds = if (picked) groupIds - g.id else groupIds + g.id })
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (templates.isEmpty()) EmptyHint(stringResource(R.string.ue_no_templates))
                                        else templates.forEach { t ->
                                            val picked = selectedTemplate == t.id
                                            PickerRow(icon = AppIcon.Template, label = t.name, selected = picked, onClick = {
                                                selectedTemplate = t.id
                                                t.dataLimit?.let { limitGb = "%.2f".format(Locale.US, it / 1073741824.0).trimEnd('0').trimEnd('.') }
                                                t.expireDuration?.let { days = (it / 86400L).toString() }
                                            }) {
                                                androidx.compose.animation.AnimatedVisibility(visible = picked, enter = androidx.compose.animation.scaleIn(DsAnim.bouncy()) + androidx.compose.animation.fadeIn(DsAnim.fast()), exit = androidx.compose.animation.scaleOut(DsAnim.exit()) + androidx.compose.animation.fadeOut(DsAnim.exit())) {
                                                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(theme.inkColor), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Check, tint = theme.cardSurfaceColor, size = 10.dp) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (templates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            EditorSectionLabel(stringResource(R.string.ue_next_plan))
                            Column(
                                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(stringResource(R.string.ue_next_plan_desc), fontSize = 9.sp, color = theme.mutedColor)
                                Box {
                                    Row(
                                        Modifier.fillMaxWidth().height(32.dp).clip(DsRadius.Md).background(theme.searchBgColor)
                                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).pressScale(0.98f)
                                            .clickable { nextPlanMenu = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        RoundedAppIcon(AppIcon.Template, tint = theme.mutedColor, size = 12.dp)
                                        Text(templates.firstOrNull { it.id == nextPlanTemplate }?.name ?: stringResource(R.string.ue_next_plan_none), fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = if (nextPlanTemplate != null) theme.inkColor else theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text("▾", fontSize = 9.sp, color = theme.mutedColor)
                                    }
                                    DropdownMenu(expanded = nextPlanMenu, onDismissRequest = { nextPlanMenu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.ue_next_plan_none), fontSize = 11.sp) }, onClick = { nextPlanTemplate = null; nextPlanMenu = false })
                                        templates.forEach { t -> DropdownMenuItem(text = { Text(t.name, fontSize = 11.sp) }, onClick = { nextPlanTemplate = t.id; nextPlanMenu = false }) }
                                    }
                                }
                                if (nextPlanTemplate != null) {
                                    Row(Modifier.fillMaxWidth().clip(DsRadius.Md).pressScale(0.99f).clickable { nextPlanCarry = !nextPlanCarry }.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CheckboxIcon(selected = nextPlanCarry, onToggle = { nextPlanCarry = !nextPlanCarry })
                                        Text(stringResource(R.string.ue_next_plan_carry), fontSize = 10.sp, color = theme.inkColor, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        EditorSectionLabel(stringResource(R.string.ue_advanced))
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(32.dp).clip(DsRadius.Md).pressScale(0.985f).clickable { advancedOpen = !advancedOpen }.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                RoundedAppIcon(AppIcon.Tune, tint = theme.mutedColor, size = 13.dp)
                                Text(stringResource(R.string.ue_advanced), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
                                val rot by animateFloatAsState(targetValue = if (advancedOpen) 180f else 0f, animationSpec = DsAnim.normal(), label = "advChevron")
                                RoundedAppIcon(AppIcon.ChevronDown, tint = theme.mutedColor, size = 12.dp, modifier = Modifier.graphicsLayer { rotationZ = rot })
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = advancedOpen, enter = DsTransition.expandEnter, exit = DsTransition.expandExit) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FieldLabel(stringResource(R.string.ue_hwid))
                                        UserFormTextField(value = hwid, onValueChange = { raw -> val n = normalizePersianDigits(raw); hwid = n.filter { c -> c.isDigit() } }, placeholder = stringResource(R.string.ue_hwid_hint), keyboardType = KeyboardType.Number, leading = AppIcon.Device)
                                        Text(stringResource(R.string.ue_hwid_help), fontSize = 8.5.sp, color = theme.mutedColor, modifier = Modifier.padding(start = 2.dp))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FieldLabel(stringResource(R.string.ue_reset_strategy))
                                        ChipSelector(values = TemplateOptions.RESET_STRATEGIES, labels = listOf(stringResource(R.string.tpl_reset_no_reset), stringResource(R.string.tpl_reset_day), stringResource(R.string.tpl_reset_week), stringResource(R.string.tpl_reset_month), stringResource(R.string.tpl_reset_year)), selected = resetStrategy, onSelect = { resetStrategy = it })
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FieldLabel(stringResource(R.string.ue_auto_delete))
                                        UserFormTextField(value = autoDeleteDays, onValueChange = { raw -> val n = normalizePersianDigits(raw); autoDeleteDays = n.filter { c -> c.isDigit() } }, placeholder = stringResource(R.string.tpl_unlimited), keyboardType = KeyboardType.Number, leading = AppIcon.Delete)
                                        Text(stringResource(R.string.ue_auto_delete_hint), fontSize = 8.5.sp, color = theme.mutedColor, modifier = Modifier.padding(start = 2.dp))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FieldLabel(stringResource(R.string.ue_note))
                                        UserFormTextField(value = note, onValueChange = { note = it.take(500) }, placeholder = stringResource(R.string.ue_note_hint), singleLine = false, modifier = Modifier.height(52.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
                Row(Modifier.fillMaxWidth().background(theme.cardSurfaceColor).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton(text = stringResource(R.string.ue_cancel), onClick = onDismiss, modifier = Modifier.weight(0.35f))
                    PrimaryButton(
                        text = stringResource(if (isCreating) R.string.ue_create else R.string.ue_save), modifier = Modifier.weight(0.65f),
                        onClick = {
                            val normalizedDays = normalizePersianDigits(days)
                            val normalizedLimit = normalizePersianDigits(limitGb)
                            val normalizedHwid = normalizePersianDigits(hwid)
                            val normalizedAutoDelete = normalizePersianDigits(autoDeleteDays)
                            val expire = normalizedDays.toIntOrNull()?.takeIf { it >= 0 }?.let { LocalDate.now().plusDays(it.toLong()).toString() } ?: ""
                            val hwidValue = normalizedHwid.toIntOrNull() ?: 0
                            val values = UserEditorValues(username, normalizedLimit.toDoubleOrNull() ?: 0.0, note, hwidValue, groupIds, resetStrategy = resetStrategy, autoDeleteDays = normalizedAutoDelete.toIntOrNull(), nextPlan = NextPlan(templateId = nextPlanTemplate, addRemainingTraffic = nextPlanCarry))
                            if (activeTab == 1 && selectedTemplate != null && isCreating && onSaveWithTemplate != null) onSaveWithTemplate(username, selectedTemplate!!, note)
                            else if (activeTab == 1 && selectedTemplate != null && !isCreating && onApplyTemplateToUser != null) onApplyTemplateToUser(selectedTemplate!!, note)
                            else {
                                onSave(values, expire)
                                if (initial != null && active != (initial.status != "disabled")) onToggle?.invoke()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCalendar) {
        ShamsiCalendarPickerDialog(initialDateShamsi = JalaliCalendar.todayJalali().toString(), onDismiss = { showCalendar = false }) { shamsi ->
            days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(JalaliCalendar.shamsiToIso(shamsi).take(10))).coerceAtLeast(0L).toString() }.getOrDefault("")
        }
    }
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = LocalThemeState.current.mutedColor.copy(0.7f), letterSpacing = 0.3.sp, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        EditorSectionLabel(title)
        Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = LocalThemeState.current.mutedColor.copy(0.8f))
}

@Composable
private fun EmptyHint(text: String) {
    val theme = LocalThemeState.current
    Box(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor).padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(text, fontSize = 10.sp, color = theme.mutedColor) }
}

@Composable
private fun PickerRow(icon: AppIcon, label: String, selected: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().height(36.dp).clip(DsRadius.Md).background(if (selected) theme.inkColor.copy(0.06f) else theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) theme.inkColor.copy(0.15f) else theme.borderColor), DsRadius.Md).pressScale(0.98f).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundedAppIcon(icon, tint = if (selected) theme.inkColor else theme.mutedColor, size = 13.dp)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun UserFormTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, leading: AppIcon? = null) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by androidx.compose.animation.animateColorAsState(targetValue = if (isFocused) theme.inkColor.copy(0.3f) else theme.borderColor, animationSpec = DsAnim.fast(), label = "fieldBorder")
    val fieldStyle = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
    Row(
        modifier = modifier.fillMaxWidth().height(if (singleLine) 32.dp else Dp.Unspecified).clip(DsRadius.Md).background(theme.searchBgColor)
            .border(BorderStroke(if (isFocused) 1.dp else DsBorder.Hairline, borderColor), DsRadius.Md).padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 8.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leading != null) RoundedAppIcon(leading, tint = theme.mutedColor, size = 12.dp)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType), textStyle = fieldStyle, cursorBrush = SolidColor(theme.inkColor),
            modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner -> Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) { if (value.isEmpty()) Text(placeholder, style = fieldStyle.copy(color = theme.mutedColor.copy(0.5f)), maxLines = if (singleLine) 1 else 2, overflow = TextOverflow.Ellipsis); inner() } }
        )
    }
}
