package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.GroupDetail
import com.mrm.pgmanager.data.model.GroupValidation
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.spinWhile
import com.mrm.pgmanager.ui.designsystem.DsTransition
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.dialogs.CheckboxIcon
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/**
 * صفحهٔ مدیریت گروه‌ها — فهرست، ساخت، ویرایش و حذف.
 *
 * پنل نامِ گروه را ۳..۶۴ کاراکتر می‌پذیرد و برای ساخت حداقل یک inbound tag لازم دارد؛
 * اعتبارسنجی در GroupValidation (لایهٔ مدل) انجام می‌شود تا قابلِ تست باشد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(session: Session, onOpenSettings: () -> Unit = {}) {
    val settingsLabel = stringResource(R.string.app_settings)
    val addGroupLabel = stringResource(R.string.add_group)
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()

    val groupsKey = PanelCache.groupsKey(session.baseUrl)
    val inboundsKey = PanelCache.inboundsKey(session.baseUrl)
    var groups by remember(session) {
        mutableStateOf(PanelCache.get<List<GroupDetail>>(groupsKey) ?: emptyList())
    }
    var availableInbounds by remember(session) {
        mutableStateOf(PanelCache.get<List<String>>(inboundsKey) ?: emptyList())
    }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<GroupDetail>>(groupsKey) == null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    // نوار خطای غیرمسدودکننده وقتی فهرست روی صفحه است (مثلاً حذفِ ناموفق).
    var banner by remember { mutableStateOf<String?>(null) }
    // پیام‌ها از قبل ترجمه می‌شوند (stringResource فقط داخل @Composable مجاز است).
    val msgCreated = stringResource(R.string.group_created)
    val msgUpdated = stringResource(R.string.group_updated)
    val msgDeleted = stringResource(R.string.group_deleted)
    val msgLoadFailed = stringResource(R.string.load_failed)

    // ویرایشگر: null یعنی بسته. GroupDetail با id=0 یعنی «ساخت جدید».
    var editing by remember { mutableStateOf<GroupDetail?>(null) }
    var deleting by remember { mutableStateOf<GroupDetail?>(null) }

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        runCatching { PanelApi.groupsDetailed(session) }
            .onSuccess { groups = it; loadError = null; PanelCache.put(groupsKey, it) }
            .onFailure { loadError = it.message ?: "error" }
        // تگ‌ها اختیاری‌اند؛ نبودشان صفحه را از کار نمی‌اندازد.
        runCatching { PanelApi.inboundTags(session) }
            .onSuccess { availableInbounds = it; PanelCache.put(inboundsKey, it) }
        loading = false
    }

    LaunchedEffect(session) {
        // دادهٔ تازه = بدون درخواست؛ سوایپ روان می‌ماند.
        if (!PanelCache.isFresh(groupsKey)) load(silent = groups.isNotEmpty())
    }

    // پیام موفقیت پس از ۲ ثانیه محو می‌شود.
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    val filtered = remember(groups, query) {
        val q = query.trim()
        if (q.isEmpty()) groups
        else groups.filter { g ->
            g.name.contains(q, ignoreCase = true) ||
                g.inboundTags.any { it.contains(q, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            Box(
                Modifier
                    .padding(bottom = 72.dp, end = 4.dp)
                    .size(52.dp)
                    .clip(DsRadius.Lg)
                    .background(theme.accentPrimary)
                    .semantics { contentDescription = addGroupLabel }
                    .clickable { editing = GroupDetail(id = 0, name = "") },
                contentAlignment = Alignment.Center
            ) {
                RoundedAppIcon(AppIcon.Add, tint = com.mrm.pgmanager.ui.designsystem.DsAccent.OnAccent, size = DsComponent.IconLg)
            }
        }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } },
            state = pullState,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    isRefreshing = refreshing, state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = theme.cardSurfaceColor, color = theme.accentPrimary
                )
            }
        ) {
            Column(
                Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
                    // دکمهٔ همبرگری حذف شده (ناوبری به کپسولِ پایین رفت)، پس دیگر
                    // لازم نیست ۵۶dp بالای صفحه خالی بماند.
                    .padding(start = DsSpacing.Screen, end = DsSpacing.Screen, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── سربرگ
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.groups_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            if (groups.isNotEmpty()) PGBadge("${groups.size}")
                        }
                        Text(stringResource(R.string.groups_subtitle), fontSize = 10.sp, color = theme.mutedColor)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor)
                                .border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp))
                                .clickable { scope.launch { refreshing = true; load(true); refreshing = false } },
                            contentAlignment = Alignment.Center
                        ) {
                            RoundedAppIcon(AppIcon.Refresh, tint = if (refreshing) theme.accentPrimary else theme.mutedColor, size = 16.dp, modifier = Modifier.spinWhile(refreshing))
                        }
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor)
                                .border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp))
                                .semantics { contentDescription = settingsLabel }
                                .clickable(onClick = onOpenSettings),
                            contentAlignment = Alignment.Center
                        ) { RoundedAppIcon(AppIcon.Settings, tint = theme.mutedColor, size = 16.dp) }
                    }
                }

                // ── پیام موفقیت
                androidx.compose.animation.AnimatedVisibility(
                    visible = toast != null,
                    enter = DsTransition.bannerEnter,
                    exit = DsTransition.bannerExit
                ) {
                    val msg = toast.orEmpty()
                    Row(
                        Modifier.fillMaxWidth().clip(DsRadius.Md)
                            .background(DsSemantic.Success.copy(0.12f))
                            .border(BorderStroke(DsBorder.Hairline, DsSemantic.Success.copy(0.24f)), DsRadius.Md)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedAppIcon(AppIcon.CheckCircle, tint = DsSemantic.Success, size = 14.dp)
                        Text(msg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DsSemantic.Success)
                    }
                }

                // ── نوار خطای غیرمسدودکننده (قابل بستن)
                banner?.let { msg ->
                    Row(
                        Modifier.fillMaxWidth().clip(DsRadius.Md)
                            .background(DsSemantic.Danger.copy(0.12f))
                            .border(BorderStroke(DsBorder.Hairline, DsSemantic.Danger.copy(0.24f)), DsRadius.Md)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Warning, tint = DsSemantic.Danger, size = 14.dp)
                        Text(
                            msg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = DsSemantic.Danger, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.size(20.dp).clip(DsRadius.Xs).clickable { banner = null },
                            contentAlignment = Alignment.Center
                        ) { Text("×", fontSize = 13.sp, color = DsSemantic.Danger) }
                    }
                }

                // ── جست‌وجو (فقط وقتی چیزی برای جست‌وجو هست)
                if (groups.isNotEmpty()) {
                    PGSearchBar(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.search_groups))
                }

                when {
                    loading && groups.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = theme.accentPrimary)
                        }
                    }

                    loadError != null && groups.isEmpty() -> {
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Warning, tint = DsSemantic.Danger, size = 26.dp)
                            Text(stringResource(R.string.load_failed), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            Text(loadError.orEmpty(), fontSize = 10.sp, color = theme.mutedColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            PGSecondaryButton(stringResource(R.string.retry), onClick = { scope.launch { load() } })
                        }
                    }

                    groups.isEmpty() -> {
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Folder, tint = theme.mutedColor, size = 28.dp)
                            Text(stringResource(R.string.no_groups), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            Text(stringResource(R.string.no_groups_hint), fontSize = 10.sp, color = theme.mutedColor)
                            Spacer(Modifier.height(2.dp))
                            PGPrimaryButton(
                                stringResource(R.string.create_group),
                                onClick = { editing = GroupDetail(id = 0, name = "") },
                                icon = AppIcon.Add
                            )
                        }
                    }

                    filtered.isEmpty() -> {
                        // گروه هست ولی جست‌وجو چیزی پیدا نکرد.
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_results), fontSize = 11.sp, color = theme.mutedColor)
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 140.dp)
                        ) {
                            items(filtered, key = { it.id }) { group ->
                                Box(Modifier.animateItem()) {
                                    GroupRow(
                                        group = group,
                                        onEdit = { editing = group },
                                        onDelete = { deleting = group }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── ویرایشگر (ساخت/ویرایش)
    editing?.let { target ->
        GroupEditorDialog(
            initial = target,
            availableInbounds = availableInbounds,
            existingNames = groups.filter { it.id != target.id }.map { it.name },
            onDismiss = { editing = null },
            onSave = { name, tags, disabled, onResult ->
                scope.launch {
                    val isCreate = target.id == 0
                    runCatching {
                        if (isCreate) PanelApi.createGroup(session, name, tags, disabled)
                        else PanelApi.modifyGroup(session, target.id, name, tags, disabled)
                    }.onSuccess {
                        editing = null
                        toast = if (isCreate) msgCreated else msgUpdated
                        load(silent = true)
                    }.onFailure { e ->
                        // دیالوگ باز می‌ماند و خطا داخل خودش نمایش داده می‌شود.
                        onResult(e.message ?: "error")
                    }
                }
            }
        )
    }

    // ── تأیید حذف
    deleting?.let { target ->
        val warning = if (target.totalUsers > 0)
            stringResource(R.string.delete_group_warning, target.totalUsers) else ""
        ConfirmActionDialog(
            title = stringResource(R.string.delete_group),
            message = stringResource(R.string.delete_group_confirm, target.name) +
                (if (warning.isNotEmpty()) "\n\n$warning" else ""),
            confirmLabel = stringResource(R.string.delete_group),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    runCatching { PanelApi.deleteGroup(session, target.id) }
                        .onSuccess { toast = msgDeleted; load(silent = true) }
                        // فهرست روی صفحه است، پس نوار خطای بالای فهرست را نشان می‌دهیم.
                        .onFailure { banner = it.message ?: msgLoadFailed }
                    deleting = null
                }
            }
        )
    }
}

/** یک ردیفِ گروه در فهرست. */
@Composable
private fun GroupRow(group: GroupDetail, onEdit: () -> Unit, onDelete: () -> Unit) {
    val theme = LocalThemeState.current
    val deleteGroupLabel = stringResource(R.string.delete_group_cd)
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .clickable { onEdit() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // آیکون گروه با رنگِ وضعیت
        Box(
            Modifier.size(36.dp).clip(DsRadius.Md)
                .background(if (group.isDisabled) theme.searchBgColor else theme.accentPrimary.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) {
            RoundedAppIcon(
                AppIcon.Folder,
                tint = if (group.isDisabled) theme.mutedColor else theme.accentPrimary,
                size = 17.dp
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    group.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (group.isDisabled) theme.mutedColor else theme.inkColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (group.isDisabled) PGBadge(stringResource(R.string.group_disabled), DsSemantic.DangerSoft)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.group_users_count, group.totalUsers),
                    fontSize = 10.sp, color = theme.mutedColor
                )
                if (group.inboundTags.isNotEmpty()) {
                    Text("•", fontSize = 10.sp, color = theme.mutedColor)
                    Text(
                        group.inboundTags.joinToString(", "),
                        fontSize = 10.sp, color = theme.mutedColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            Modifier.size(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor)
                .semantics { contentDescription = deleteGroupLabel }
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) { RoundedAppIcon(AppIcon.Delete, tint = DsSemantic.Danger, size = 15.dp) }
    }
}

/**
 * دیالوگِ ساخت/ویرایش گروه.
 * `initial.id == 0` یعنی حالتِ ساخت — در این حالت پنل حداقل یک inbound می‌خواهد.
 */
@Composable
private fun GroupEditorDialog(
    initial: GroupDetail,
    availableInbounds: List<String>,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, Boolean, (String) -> Unit) -> Unit
) {
    val theme = LocalThemeState.current
    val isCreate = initial.id == 0

    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var disabled by remember(initial.id) { mutableStateOf(initial.isDisabled) }
    var selectedTags by remember(initial.id) { mutableStateOf(initial.inboundTags.toSet()) }
    var saving by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    // خطای برگشتی از سرور (مثلاً نام تکراری که پنل رد می‌کند)
    var serverError by remember(initial.id) { mutableStateOf<String?>(null) }

    // اگر گروه تگی دارد که دیگر روی پنل نیست، باز هم نشانش می‌دهیم تا سهواً حذف نشود.
    val allTags = remember(availableInbounds, initial.inboundTags) {
        (availableInbounds + initial.inboundTags).distinct()
    }

    val errorKey = GroupValidation.validate(name, selectedTags.toList(), isCreate)
    val duplicate = name.trim().isNotEmpty() &&
        existingNames.any { it.equals(name.trim(), ignoreCase = true) }
    val nameShort = stringResource(R.string.err_name_short)
    val nameLong = stringResource(R.string.err_name_long)
    val noInbound = stringResource(R.string.err_no_inbound)
    val nameTaken = stringResource(R.string.err_name_taken)
    val errorText = when {
        serverError != null -> serverError
        !touched -> null
        errorKey == GroupValidation.ERR_NAME_SHORT -> nameShort
        errorKey == GroupValidation.ERR_NAME_LONG -> nameLong
        errorKey == GroupValidation.ERR_NO_INBOUND -> noInbound
        duplicate -> nameTaken
        else -> null
    }
    val canSave = errorKey == null && !duplicate && !saving

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (isCreate) R.string.create_group else R.string.edit_group),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
            )

            // نام گروه
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.group_name), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.mutedColor)
                CompactGlassField(
                    value = name,
                    onValueChange = { name = it; touched = true; serverError = null },
                    placeholder = stringResource(R.string.group_name_hint),
                    keyboardType = KeyboardType.Text,
                    leadingAppIcon = AppIcon.Folder
                )
            }

            // انتخاب inbound tags
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.inbound_tags), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.mutedColor)
                    if (selectedTags.isNotEmpty()) PGBadge(stringResource(R.string.selected_count, selectedTags.size))
                }
                if (allTags.isEmpty()) {
                    Text(stringResource(R.string.no_inbounds_available), fontSize = 10.sp, color = theme.mutedColor)
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState())
                            .clip(DsRadius.Md).background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                            .padding(vertical = 4.dp)
                    ) {
                        allTags.forEach { tag ->
                            val checked = tag in selectedTags
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selectedTags = if (checked) selectedTags - tag else selectedTags + tag
                                        touched = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                CheckboxIcon(
                                    selected = checked,
                                    onToggle = {
                                        selectedTags = if (checked) selectedTags - tag else selectedTags + tag
                                        touched = true
                                    }
                                )
                                Text(
                                    tag, fontSize = 12.sp,
                                    color = if (checked) theme.inkColor else theme.mutedColor,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // غیرفعال‌سازی گروه
            Row(
                Modifier.fillMaxWidth().clip(DsRadius.Md)
                    .clickable { disabled = !disabled }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CheckboxIcon(selected = disabled, onToggle = { disabled = !disabled })
                Text(stringResource(R.string.group_is_disabled_label), fontSize = 12.sp, color = theme.inkColor)
            }

            errorText?.let {
                Text(it, fontSize = 10.sp, color = DsSemantic.Danger, fontWeight = FontWeight.SemiBold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PGSecondaryButton(stringResource(R.string.cancel), onClick = { if (!saving) onDismiss() }, modifier = Modifier.weight(1f))
                PGPrimaryButton(
                    stringResource(R.string.save_changes),
                    onClick = {
                        touched = true
                        if (canSave) {
                            saving = true
                            serverError = null
                            onSave(name.trim(), selectedTags.toList(), disabled) { err ->
                                saving = false
                                serverError = err
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSave
                )
            }
        }
    }
}
