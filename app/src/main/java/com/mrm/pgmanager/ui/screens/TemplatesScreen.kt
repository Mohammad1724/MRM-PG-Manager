package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.TemplateOptions
import com.mrm.pgmanager.data.model.TemplateValidation
import com.mrm.pgmanager.data.model.UserTemplateItem
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
import com.mrm.pgmanager.ui.dialogs.ChipSelector
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/** یک گیگابایت بر حسب بایت — پنل حجم را بایتی می‌گیرد. */
private const val BYTES_PER_GB = 1_073_741_824L
/** ثانیه‌های یک روز — پنل مدت را ثانیه‌ای می‌گیرد. */
private const val SECONDS_PER_DAY = 86_400L

/**
 * صفحهٔ مدیریت تمپلت‌های کاربر — فهرست، ساخت، ویرایش و حذف.
 *
 * چرا این صفحه لازم بود: اپ از قبل تمپلت‌ها را در پنج جا **مصرف** می‌کرد
 * (ساخت کاربر، ویرایش، جزئیات، ساخت گروهی، اعمال گروهی) ولی هیچ راهی برای
 * ساخت یا ویرایششان نداشت؛ کاربر مجبور بود به پنل وب برود.
 *
 * واحدها: پنل حجم را بایت و مدت را ثانیه می‌گیرد، ولی فرم گیگابایت و روز
 * می‌گیرد چون کاربر این‌طور فکر می‌کند. تبدیل فقط در همین فایل انجام می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(session: Session, onOpenSettings: () -> Unit = {}) {
    val settingsLabel = stringResource(R.string.app_settings)
    val refreshLabel = stringResource(R.string.refresh)
    val addTemplateLabel = stringResource(R.string.add_template)
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()

    val templatesKey = PanelCache.templatesKey(session.baseUrl)
    val templateGroupsKey = PanelCache.templateGroupsKey(session.baseUrl)
    var templates by remember(session) {
        mutableStateOf(PanelCache.get<List<UserTemplateItem>>(templatesKey) ?: emptyList())
    }
    var availableGroups by remember(session) {
        mutableStateOf(PanelCache.get<List<Group>>(templateGroupsKey) ?: emptyList())
    }
    var loading by remember(session) {
        mutableStateOf(PanelCache.get<List<UserTemplateItem>>(templatesKey) == null)
    }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }

    val msgCreated = stringResource(R.string.template_created)
    val msgUpdated = stringResource(R.string.template_updated)
    val msgDeleted = stringResource(R.string.template_deleted)
    val msgLoadFailed = stringResource(R.string.load_failed)

    // ویرایشگر: null یعنی بسته. id=0 یعنی «ساخت جدید».
    var editing by remember { mutableStateOf<UserTemplateItem?>(null) }
    var deleting by remember { mutableStateOf<UserTemplateItem?>(null) }

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        runCatching { PanelApi.userTemplates(session) }
            .onSuccess { templates = it; loadError = null; PanelCache.put(templatesKey, it) }
            .onFailure { loadError = it.message ?: "error" }
        // گروه‌ها برای انتخابگرِ فرم لازم‌اند؛ نبودشان صفحه را از کار نمی‌اندازد.
        runCatching { PanelApi.groups(session) }
            .onSuccess { availableGroups = it; PanelCache.put(templateGroupsKey, it) }
        loading = false
    }

    LaunchedEffect(session) {
        if (!PanelCache.isFresh(templatesKey)) load(silent = templates.isNotEmpty())
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    val filtered = remember(templates, query) {
        val q = query.trim()
        if (q.isEmpty()) templates
        else templates.filter { it.name.contains(q, ignoreCase = true) }
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
                    .semantics { contentDescription = addTemplateLabel }
                    .clickable { editing = UserTemplateItem(id = 0, name = "") },
                contentAlignment = Alignment.Center
            ) {
                RoundedAppIcon(
                    AppIcon.Add,
                    tint = com.mrm.pgmanager.ui.designsystem.DsAccent.OnAccent,
                    size = DsComponent.IconLg
                )
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
                PGScreenHeader(
                    title = stringResource(R.string.templates_title),
                    subtitle = stringResource(R.string.templates_subtitle),
                    refreshing = refreshing,
                    onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } },
                    onOpenSettings = onOpenSettings,
                    settingsLabel = settingsLabel,
                    refreshLabel = refreshLabel,
                    badge = if (templates.isNotEmpty()) ({ PGBadge("${templates.size}") }) else null
                )

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

                // ── نوار خطای غیرمسدودکننده
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

                if (templates.isNotEmpty()) {
                    PGSearchBar(
                        query = query, onQueryChange = { query = it },
                        placeholder = stringResource(R.string.search_templates)
                    )
                }

                when {
                    loading && templates.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = theme.accentPrimary)
                        }
                    }

                    loadError != null && templates.isEmpty() -> {
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Warning, tint = DsSemantic.Danger, size = 26.dp)
                            Text(
                                stringResource(R.string.load_failed), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = theme.inkColor
                            )
                            Text(
                                loadError.orEmpty(), fontSize = 10.sp, color = theme.mutedColor,
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                            PGSecondaryButton(stringResource(R.string.retry), onClick = { scope.launch { load() } })
                        }
                    }

                    templates.isEmpty() -> {
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Template, tint = theme.mutedColor, size = 28.dp)
                            Text(
                                stringResource(R.string.no_templates), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = theme.inkColor
                            )
                            Text(stringResource(R.string.no_templates_hint), fontSize = 10.sp, color = theme.mutedColor)
                            Spacer(Modifier.height(2.dp))
                            PGPrimaryButton(
                                stringResource(R.string.create_template),
                                onClick = { editing = UserTemplateItem(id = 0, name = "") },
                                icon = AppIcon.Add
                            )
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_results), fontSize = 11.sp, color = theme.mutedColor)
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 140.dp)
                        ) {
                            items(filtered, key = { it.id }) { tpl ->
                                Box(Modifier.animateItem()) {
                                    TemplateRow(
                                        template = tpl,
                                        groups = availableGroups,
                                        onEdit = { editing = tpl },
                                        onDelete = { deleting = tpl }
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
        TemplateEditorDialog(
            initial = target,
            availableGroups = availableGroups,
            onDismiss = { editing = null },
            onSave = { draft, onResult ->
                scope.launch {
                    val isCreate = target.id == 0
                    runCatching {
                        if (isCreate) PanelApi.createUserTemplate(session, draft)
                        else PanelApi.modifyUserTemplate(session, target.id, draft)
                    }.onSuccess {
                        editing = null
                        toast = if (isCreate) msgCreated else msgUpdated
                        load(silent = true)
                    }.onFailure { e ->
                        onResult(e.message ?: "error")
                    }
                }
            }
        )
    }

    // ── تأیید حذف
    deleting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.delete_template),
            message = stringResource(R.string.delete_template_confirm, target.name),
            confirmLabel = stringResource(R.string.delete_template),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    runCatching { PanelApi.deleteUserTemplate(session, target.id) }
                        .onSuccess { toast = msgDeleted; load(silent = true) }
                        .onFailure { banner = it.message ?: msgLoadFailed }
                    deleting = null
                }
            }
        )
    }
}

/** یک ردیفِ تمپلت در فهرست — خلاصهٔ حجم، مدت و گروه‌ها. */
@Composable
private fun TemplateRow(
    template: UserTemplateItem,
    groups: List<Group>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val deleteTemplateLabel = stringResource(R.string.delete_template_cd)
    val theme = LocalThemeState.current
    val unlimited = stringResource(R.string.tpl_unlimited)
    val daysLabel = stringResource(R.string.tpl_days)
    val gbLabel = stringResource(R.string.tpl_gb)

    // خلاصهٔ خوانا: «۱۰۰ گیگابایت • ۳۰ روز • Premium، Trial»
    val summary = remember(template, groups, unlimited, daysLabel, gbLabel) {
        val parts = mutableListOf<String>()
        parts += template.dataLimit
            ?.let { "${it / BYTES_PER_GB} $gbLabel" }
            ?: unlimited
        template.expireDuration?.takeIf { it > 0 }?.let { parts += "${it / SECONDS_PER_DAY} $daysLabel" }
        val names = template.groupIds.mapNotNull { id -> groups.firstOrNull { it.id == id }?.name }
        if (names.isNotEmpty()) parts += names.joinToString(" · ")
        parts.joinToString(" • ")
    }

    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .clickable { onEdit() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) {
            RoundedAppIcon(AppIcon.Template, tint = theme.accentPrimary, size = 17.dp)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    template.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (template.status == TemplateOptions.STATUS_ON_HOLD) {
                    PGBadge(stringResource(R.string.tpl_status_on_hold))
                }
            }
            Text(
                summary, fontSize = 10.sp, color = theme.mutedColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            Modifier.size(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor)
                .semantics { contentDescription = deleteTemplateLabel }
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) { RoundedAppIcon(AppIcon.Delete, tint = DsSemantic.Danger, size = 15.dp) }
    }
}

/**
 * انتخابگرِ تک‌گزینه‌ای به‌شکلِ chip.
 * برای ۲ تا ۵ گزینه از dropdown بهتر است: همهٔ گزینه‌ها دیده می‌شوند و
 * یک ضربه کافی است. [labels] هم‌اندازهٔ [values] است.
 */


/** برچسبِ کوچکِ بالای هر فیلد. */
@Composable
private fun FieldLabel(text: String) {
    val theme = LocalThemeState.current
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.mutedColor)
}

/**
 * دیالوگِ ساخت/ویرایش تمپلت.
 * `initial.id == 0` یعنی ساخت — در این حالت پنل حداقل یک گروه می‌خواهد.
 *
 * فرم به دو بخش تقسیم شده: فیلدهای پرکاربرد همیشه دیده می‌شوند و بقیه پشتِ
 * «تنظیمات پیشرفته» جمع شده‌اند تا فرم در نگاهِ اول ترسناک نباشد.
 */
@Composable
private fun TemplateEditorDialog(
    initial: UserTemplateItem,
    availableGroups: List<Group>,
    onDismiss: () -> Unit,
    onSave: (UserTemplateItem, (String) -> Unit) -> Unit
) {
    val theme = LocalThemeState.current
    val isCreate = initial.id == 0

    var name by remember(initial.id) { mutableStateOf(initial.name) }
    // حجم و مدت در فرم گیگابایت/روز هستند؛ خالی یعنی نامحدود.
    var dataGb by remember(initial.id) {
        mutableStateOf(initial.dataLimit?.let { (it / BYTES_PER_GB).toString() } ?: "")
    }
    var days by remember(initial.id) {
        mutableStateOf(initial.expireDuration?.takeIf { it > 0 }?.let { (it / SECONDS_PER_DAY).toString() } ?: "")
    }
    var hwid by remember(initial.id) { mutableStateOf(initial.hwidLimit?.toString() ?: "") }
    var selectedGroups by remember(initial.id) { mutableStateOf(initial.groupIds.toSet()) }
    var prefix by remember(initial.id) { mutableStateOf(initial.usernamePrefix.orEmpty()) }
    var suffix by remember(initial.id) { mutableStateOf(initial.usernameSuffix.orEmpty()) }
    var status by remember(initial.id) { mutableStateOf(initial.status ?: TemplateOptions.STATUS_ACTIVE) }
    var resetStrategy by remember(initial.id) { mutableStateOf(initial.dataLimitResetStrategy) }
    var onHoldDays by remember(initial.id) {
        mutableStateOf(initial.onHoldTimeout?.takeIf { it > 0 }?.let { (it / SECONDS_PER_DAY).toString() } ?: "")
    }
    var resetUsages by remember(initial.id) { mutableStateOf(initial.resetUsages ?: false) }
    var isDisabled by remember(initial.id) { mutableStateOf(initial.isDisabled ?: false) }
    var ssMethod by remember(initial.id) { mutableStateOf(initial.ssMethod) }
    var showAdvanced by remember(initial.id) { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var serverError by remember(initial.id) { mutableStateOf<String?>(null) }

    // تبدیلِ ورودی‌های فرم به واحدهای پنل — با نرمال‌سازی ارقام فارسی
    val dataBytes = com.mrm.pgmanager.utils.normalizePersianDigits(dataGb).trim().toLongOrNull()?.times(BYTES_PER_GB)
    val expireSeconds = com.mrm.pgmanager.utils.normalizePersianDigits(days).trim().toLongOrNull()?.times(SECONDS_PER_DAY)
    val onHoldSeconds = com.mrm.pgmanager.utils.normalizePersianDigits(onHoldDays).trim().toLongOrNull()?.times(SECONDS_PER_DAY)

    val errorKey = TemplateValidation.validateAll(
        name = name,
        groupIds = selectedGroups.toList(),
        prefix = prefix,
        suffix = suffix,
        dataLimit = dataBytes,
        expireSeconds = expireSeconds,
        requireGroup = isCreate
    )

    val errNameEmpty = stringResource(R.string.tpl_name_empty)
    val errNameLong = stringResource(R.string.tpl_name_long)
    val errNoGroup = stringResource(R.string.tpl_no_group)
    val errAffixLong = stringResource(R.string.tpl_affix_long)
    val errAffixChars = stringResource(R.string.tpl_affix_chars)
    val errAffixConsecutive = stringResource(R.string.tpl_affix_consecutive)
    val errExpireRange = stringResource(R.string.tpl_expire_range)
    val errDataNegative = stringResource(R.string.tpl_data_negative)

    val errorText = when {
        serverError != null -> serverError
        !touched -> null
        errorKey == TemplateValidation.ERR_NAME_EMPTY -> errNameEmpty
        errorKey == TemplateValidation.ERR_NAME_LONG -> errNameLong
        errorKey == TemplateValidation.ERR_NO_GROUP -> errNoGroup
        errorKey == TemplateValidation.ERR_AFFIX_LONG -> errAffixLong
        errorKey == TemplateValidation.ERR_AFFIX_CHARS -> errAffixChars
        errorKey == TemplateValidation.ERR_AFFIX_CONSECUTIVE -> errAffixConsecutive
        errorKey == TemplateValidation.ERR_EXPIRE_RANGE -> errExpireRange
        errorKey == TemplateValidation.ERR_DATA_NEGATIVE -> errDataNegative
        else -> null
    }
    val canSave = errorKey == null && !saving

    val statusLabels = listOf(
        stringResource(R.string.tpl_status_active),
        stringResource(R.string.tpl_status_on_hold)
    )
    val resetLabels = listOf(
        stringResource(R.string.tpl_reset_no_reset),
        stringResource(R.string.tpl_reset_day),
        stringResource(R.string.tpl_reset_week),
        stringResource(R.string.tpl_reset_month),
        stringResource(R.string.tpl_reset_year)
    )

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (isCreate) R.string.create_template else R.string.edit_template),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
            )

            // فرم بلند است؛ محتوا داخل ناحیهٔ اسکرول‌شونده می‌ماند تا دکمه‌ها
            // همیشه پایین دیده شوند.
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── نام
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FieldLabel(stringResource(R.string.template_name))
                    CompactGlassField(
                        value = name,
                        onValueChange = { name = it; touched = true; serverError = null },
                        placeholder = stringResource(R.string.template_name_hint),
                        keyboardType = KeyboardType.Text,
                        leadingAppIcon = AppIcon.Template
                    )
                }

                // ── حجم و مدت، کنار هم
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FieldLabel(stringResource(R.string.tpl_data_limit) + " (" + stringResource(R.string.tpl_gb) + ")")
                        CompactGlassField(
                            value = dataGb,
                            onValueChange = { raw ->
                                val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                                dataGb = n.filter { c -> c.isDigit() }; touched = true; serverError = null
                            },
                            placeholder = stringResource(R.string.tpl_unlimited)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FieldLabel(stringResource(R.string.tpl_expire_duration) + " (" + stringResource(R.string.tpl_days) + ")")
                        CompactGlassField(
                            value = days,
                            onValueChange = { raw ->
                                val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                                days = n.filter { c -> c.isDigit() }; touched = true; serverError = null
                            },
                            placeholder = stringResource(R.string.tpl_unlimited)
                        )
                    }
                }

                // ── گروه‌ها
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FieldLabel(stringResource(R.string.tpl_groups))
                        if (selectedGroups.isNotEmpty()) {
                            PGBadge(stringResource(R.string.selected_count, selectedGroups.size))
                        }
                    }
                    if (availableGroups.isEmpty()) {
                        Text(
                            stringResource(R.string.tpl_no_groups_available),
                            fontSize = 10.sp, color = theme.mutedColor
                        )
                    } else {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())
                                .clip(DsRadius.Md).background(theme.searchBgColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                .padding(vertical = 4.dp)
                        ) {
                            availableGroups.forEach { g ->
                                val checked = g.id in selectedGroups
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            selectedGroups =
                                                if (checked) selectedGroups - g.id else selectedGroups + g.id
                                            touched = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    CheckboxIcon(
                                        selected = checked,
                                        onToggle = {
                                            selectedGroups =
                                                if (checked) selectedGroups - g.id else selectedGroups + g.id
                                            touched = true
                                        }
                                    )
                                    Text(
                                        g.name, fontSize = 12.sp,
                                        color = if (checked) theme.inkColor else theme.mutedColor,
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // ── تنظیمات پیشرفته (جمع‌شونده)
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Md)
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoundedAppIcon(
                        if (showAdvanced) AppIcon.ChevronUp else AppIcon.ChevronDown,
                        tint = theme.mutedColor, size = 14.dp
                    )
                    Text(
                        stringResource(R.string.tpl_advanced), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, color = theme.mutedColor
                    )
                }

                if (showAdvanced) {
                    // وضعیت اولیه
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FieldLabel(stringResource(R.string.tpl_initial_status))
                        ChipSelector(
                            values = TemplateOptions.STATUSES,
                            labels = statusLabels,
                            selected = status,
                            onSelect = { status = it; touched = true }
                        )
                    }

                    // مهلت فعال‌سازی — فقط در حالتِ on_hold معنی دارد
                    if (status == TemplateOptions.STATUS_ON_HOLD) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FieldLabel(
                                stringResource(R.string.tpl_on_hold_timeout) +
                                    " (" + stringResource(R.string.tpl_days) + ")"
                            )
                            CompactGlassField(
                                value = onHoldDays,
                                onValueChange = { raw ->
                                    val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                                    onHoldDays = n.filter { c -> c.isDigit() }; touched = true
                                },
                                placeholder = stringResource(R.string.tpl_unlimited)
                            )
                        }
                    }

                    // دورهٔ ریست حجم
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FieldLabel(stringResource(R.string.tpl_reset_strategy))
                        ChipSelector(
                            values = TemplateOptions.RESET_STRATEGIES,
                            labels = resetLabels,
                            selected = resetStrategy,
                            onSelect = { resetStrategy = it; touched = true }
                        )
                    }

                    // سقف دستگاه
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FieldLabel(
                            stringResource(R.string.tpl_hwid_limit) +
                                " (" + stringResource(R.string.tpl_devices) + ")"
                        )
                        CompactGlassField(
                            value = hwid,
                            onValueChange = { raw ->
                                val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                                hwid = n.filter { c -> c.isDigit() }; touched = true
                            },
                            placeholder = stringResource(R.string.tpl_unlimited)
                        )
                    }

                    // پیشوند و پسوندِ نام کاربری
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FieldLabel(stringResource(R.string.tpl_username_prefix))
                            CompactGlassField(
                                value = prefix,
                                onValueChange = { prefix = it; touched = true; serverError = null },
                                placeholder = "—",
                                keyboardType = KeyboardType.Text
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FieldLabel(stringResource(R.string.tpl_username_suffix))
                            CompactGlassField(
                                value = suffix,
                                onValueChange = { suffix = it; touched = true; serverError = null },
                                placeholder = "—",
                                keyboardType = KeyboardType.Text
                            )
                        }
                    }

                    // روش رمزنگاری Shadowsocks
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FieldLabel(stringResource(R.string.tpl_ss_method))
                        ChipSelector(
                            values = TemplateOptions.SS_METHODS,
                            labels = TemplateOptions.SS_METHODS,
                            selected = ssMethod,
                            onSelect = { ssMethod = if (ssMethod == it) null else it; touched = true }
                        )
                    }

                    // سوییچ‌ها
                    Row(
                        Modifier.fillMaxWidth().clip(DsRadius.Md)
                            .clickable { resetUsages = !resetUsages }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        CheckboxIcon(selected = resetUsages, onToggle = { resetUsages = !resetUsages })
                        Text(stringResource(R.string.tpl_reset_usages), fontSize = 12.sp, color = theme.inkColor)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(DsRadius.Md)
                            .clickable { isDisabled = !isDisabled }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        CheckboxIcon(selected = isDisabled, onToggle = { isDisabled = !isDisabled })
                        Text(stringResource(R.string.tpl_is_disabled), fontSize = 12.sp, color = theme.inkColor)
                    }
                }
            }

            errorText?.let {
                Text(it, fontSize = 10.sp, color = DsSemantic.Danger, fontWeight = FontWeight.SemiBold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PGSecondaryButton(
                    stringResource(R.string.cancel),
                    onClick = { if (!saving) onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                PGPrimaryButton(
                    stringResource(R.string.save_changes),
                    onClick = {
                        touched = true
                        if (canSave) {
                            saving = true
                            serverError = null
                            val draft = UserTemplateItem(
                                id = initial.id,
                                name = name.trim(),
                                dataLimit = dataBytes,
                                expireDuration = expireSeconds,
                                hwidLimit = com.mrm.pgmanager.utils.normalizePersianDigits(hwid).trim().toIntOrNull(),
                                usernamePrefix = prefix.trim().takeIf { it.isNotEmpty() },
                                usernameSuffix = suffix.trim().takeIf { it.isNotEmpty() },
                                groupIds = selectedGroups.toList(),
                                status = status,
                                dataLimitResetStrategy = resetStrategy,
                                onHoldTimeout = onHoldSeconds,
                                resetUsages = resetUsages,
                                isDisabled = isDisabled,
                                ssMethod = ssMethod
                            )
                            onSave(draft) { err ->
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
