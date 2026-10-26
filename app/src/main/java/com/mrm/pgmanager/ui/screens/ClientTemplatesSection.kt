package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.ClientTemplate
import com.mrm.pgmanager.data.model.ClientTemplateType
import com.mrm.pgmanager.data.model.ClientTemplateValidation
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.PGStartAlign
import com.mrm.pgmanager.ui.components.PGWrapRow
import com.mrm.pgmanager.ui.components.PGBadge
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.dialogs.CheckboxIcon
import com.mrm.pgmanager.ui.dialogs.ChipSelector
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/**
 * بخشِ «قالب‌های کلاینت» — فهرست، فیلتر بر اساسِ نوع، ساخت، ویرایش و حذف.
 *
 * این با «قالبِ کاربر» (`/api/user_templates`) فرق دارد: آن یکی *الگوی ساختِ
 * کاربر* است (حجم/مدت/گروه)، این یکی *محتوای خروجیِ اشتراک* برای کلاینت‌ها
 * (Clash / Xray / sing-box / user-agent). اشتباه‌گرفتنشان رایج است، پس در متنِ
 * خالی هم توضیح داده شده.
 *
 * دو قاعدهٔ پنل که UI هم رعایتشان می‌کند:
 *  • `template_type` پس از ساخت تغییر نمی‌کند (`ClientTemplateModify` آن را ندارد)؛
 *  • قالبِ سیستمی (`is_system`) حذف نمی‌شود — دکمهٔ حذف برایش نشان داده نمی‌شود.
 */
@Composable
internal fun ClientTemplatesSection(session: Session) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()

    val key = PanelCache.clientTemplatesKey(session.baseUrl)
    var templates by remember(session) {
        mutableStateOf(PanelCache.get<List<ClientTemplate>>(key) ?: emptyList())
    }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<ClientTemplate>>(key) == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    // null = بسته، ClientTemplate با id = 0 = «ساختِ جدید».
    var editing by remember { mutableStateOf<ClientTemplate?>(null) }
    var deleting by remember { mutableStateOf<ClientTemplate?>(null) }

    val msgCreated = stringResource(R.string.ct_created)
    val msgUpdated = stringResource(R.string.ct_updated)
    val msgDeleted = stringResource(R.string.ct_deleted)

    suspend fun load() {
        runCatching { PanelApi.clientTemplates(session) }
            .onSuccess { templates = it; loadError = null; PanelCache.put(key, it) }
            .onFailure { loadError = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
        loading = false
    }

    LaunchedEffect(session) {
        if (!PanelCache.isFresh(key)) {
            loading = templates.isEmpty()
            load()
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    val filtered = remember(templates, query, typeFilter) {
        val q = query.trim()
        templates.filter { t ->
            (typeFilter.isEmpty() || t.templateType == typeFilter) &&
                (q.isEmpty() || t.name.contains(q, ignoreCase = true))
        }
    }
    val canCreate = com.mrm.pgmanager.data.AdminAccess.can("client_templates", "create")
    val canUpdate = com.mrm.pgmanager.data.AdminAccess.can("client_templates", "update")
    val canDelete = com.mrm.pgmanager.data.AdminAccess.can("client_templates", "delete")

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
        InfraSectionHeader(
            title = stringResource(R.string.ct_title),
            subtitle = stringResource(R.string.ct_subtitle),
            count = templates.size,
            onRefresh = { scope.launch { loading = templates.isEmpty(); load() } },
            onAdd = if (canCreate) ({
                editing = ClientTemplate(
                    id = 0, name = "", templateType = ClientTemplateType.CLASH, content = ""
                )
            }) else null,
            addLabel = stringResource(R.string.ct_create)
        )

        if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
        banner?.let { InfraBanner(text = it, success = false) { banner = null } }

        if (templates.isNotEmpty()) {
            PGSearchBar(
                query = query, onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_ct)
            )
            // فیلترِ نوع؛ «همه» با رشتهٔ خالی نشان داده می‌شود تا state ساده بماند.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
            ) {
                val chips = listOf("" to stringResource(R.string.ct_filter_all)) +
                    ClientTemplateType.ALL.map { it to templateTypeLabel(it) }
                chips.forEach { (value, label) ->
                    val active = typeFilter == value
                    Box(
                        Modifier.heightIn(min = 40.dp).clip(DsRadius.Full)
                            .clickable { typeFilter = value }
                            .background(if (active) theme.accentPrimary.copy(0.16f) else theme.searchBgColor)
                            .border(
                                BorderStroke(
                                    DsBorder.Hairline,
                                    if (active) theme.accentPrimary.copy(0.45f) else theme.borderColor
                                ),
                                DsRadius.Full
                            )
                            .padding(horizontal = DsSpacing.Mid),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label, fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) theme.accentPrimary else theme.mutedColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when {
            loading && templates.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) { MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp) }

            loadError != null && templates.isEmpty() -> MrmErrorState(
                onRetry = { scope.launch { loading = true; load() } },
                message = loadError
            )

            templates.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.no_ct),
                subtitle = stringResource(R.string.no_ct_hint),
                icon = AppIcon.Template
            ) {
                if (canCreate) PGPrimaryButton(
                    stringResource(R.string.ct_create),
                    onClick = {
                        editing = ClientTemplate(
                            id = 0, name = "", templateType = ClientTemplateType.CLASH, content = ""
                        )
                    },
                    icon = AppIcon.Add
                )
            }

            filtered.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.no_results),
                subtitle = stringResource(R.string.no_results_hint),
                icon = AppIcon.Search
            ) {
                PGSecondaryButton(
                    stringResource(R.string.clear_filter),
                    onClick = { query = ""; typeFilter = "" }
                )
            }

            else -> {
                val intro = rememberListIntro()
                LazyColumn(
                    // نوارِ ناوبریِ پایین: این صفحه تمام‌صفحه است و نوارِ تبِ پایین
                    // ندارد، پس خودِ لیست باید insetِ پایین را کم کند — وگرنه آخرین
                    // ردیف زیرِ پیلِ حرکتِ انگشت می‌ماند (گزارشِ همان بریدگیِ پایین).
                    modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Md),
                    contentPadding = PaddingValues(bottom = DsSpacing.Xl)
                ) {
                    itemsIndexed(filtered, key = { _, t -> t.id }) { index, template ->
                        Box(Modifier.animateItem().listIntro(intro, index)) {
                            ClientTemplateRow(
                                template = template,
                                onEdit = if (canUpdate) ({ editing = template }) else null,
                                onDelete = if (canDelete && !template.isSystem) ({ deleting = template }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        ClientTemplateEditorDialog(
            initial = target,
            isCreate = target.id == 0,
            onDismiss = { editing = null },
            onSave = { name, type, content, isDefault, onResult ->
                scope.launch {
                    val creating = target.id == 0
                    runCatching {
                        if (creating) PanelApi.createClientTemplate(session, name, type, content, isDefault)
                        else PanelApi.modifyClientTemplate(session, target.id, name, content, isDefault)
                    }.onSuccess {
                        editing = null
                        toast = if (creating) msgCreated else msgUpdated
                        load()
                    }.onFailure { e ->
                        onResult(com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e))
                    }
                }
            }
        )
    }

    deleting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.delete_ct),
            message = stringResource(R.string.delete_ct_confirm, target.name),
            confirmLabel = stringResource(R.string.delete_ct),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    runCatching { PanelApi.deleteClientTemplate(session, target.id) }
                        .onSuccess { toast = msgDeleted; load() }
                        .onFailure { banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
                    deleting = null
                }
            }
        )
    }
}

/** یک ردیفِ قالب: نام، نوع، و بج‌های پیش‌فرض/سیستمی. */
@Composable
private fun ClientTemplateRow(
    template: ClientTemplate,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .then(if (onEdit != null) Modifier.clickable { onEdit() } else Modifier)
            .heightIn(min = 44.dp)
            .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        Box(
            Modifier.size(34.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) { RoundedAppIcon(AppIcon.Template, tint = theme.accentPrimary, size = 16.dp) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
            Text(
                template.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = PGStartAlign()
            )
            PGWrapRow(horizontalGap = DsSpacing.Xs, verticalGap = DsSpacing.Xxs) {
                PGBadge(templateTypeLabel(template.templateType), theme.accentPrimary)
                if (template.isDefault) PGBadge(
                    stringResource(R.string.ct_default_badge), DsSemantic.Success
                )
                if (template.isSystem) PGBadge(
                    stringResource(R.string.ct_system_badge), theme.mutedColor
                )
            }
        }

        if (onDelete != null) InfraIconButton(
            icon = AppIcon.Delete,
            description = stringResource(R.string.delete_ct),
            tint = DsSemantic.Danger,
            onClick = onDelete
        )
    }
}

/** برچسبِ نوعِ قالب؛ نوعِ ناشناخته همان مقدارِ خامِ پنل نمایش داده می‌شود. */
@Composable
private fun templateTypeLabel(type: String): String = when (type) {
    ClientTemplateType.CLASH -> stringResource(R.string.ct_type_clash)
    ClientTemplateType.XRAY -> stringResource(R.string.ct_type_xray)
    ClientTemplateType.SING_BOX -> stringResource(R.string.ct_type_singbox)
    ClientTemplateType.USER_AGENT -> stringResource(R.string.ct_type_user_agent)
    ClientTemplateType.GRPC_USER_AGENT -> stringResource(R.string.ct_type_grpc_user_agent)
    else -> type
}

/**
 * دیالوگِ ساخت/ویرایشِ قالب.
 *
 * محتوا می‌تواند هزاران کاراکتر باشد (کلِ فایلِ YAML/JSON)، پس:
 *  • یک ناحیهٔ اسکرول‌شونده با فونتِ monospace (چندخطی) به‌جای فیلدِ تک‌خطی؛
 *  • شمارندهٔ کاراکتر برای اینکه کاربر بفهمد محتوا کامل آمده یا نه؛
 *  • نوع فقط در حالتِ ساخت قابلِ انتخاب است (قاعدهٔ پنل).
 */
@Composable
private fun ClientTemplateEditorDialog(
    initial: ClientTemplate,
    isCreate: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: String,
        content: String,
        isDefault: Boolean,
        onResult: (String) -> Unit
    ) -> Unit
) {
    val theme = LocalThemeState.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var type by remember(initial.id) { mutableStateOf(initial.templateType) }
    var content by remember(initial.id) { mutableStateOf(initial.content) }
    var isDefault by remember(initial.id) { mutableStateOf(initial.isDefault) }
    var saving by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var serverError by remember(initial.id) { mutableStateOf<String?>(null) }

    val errorKey = ClientTemplateValidation.validate(name, content)
    val errName = stringResource(R.string.err_ct_name)
    val errNameLong = stringResource(R.string.err_ct_name_long)
    val errContent = stringResource(R.string.err_ct_content)
    val errorText = when {
        serverError != null -> serverError
        !touched -> null
        errorKey == ClientTemplateValidation.ERR_NAME -> errName
        errorKey == ClientTemplateValidation.ERR_NAME_LONG -> errNameLong
        errorKey == ClientTemplateValidation.ERR_CONTENT -> errContent
        else -> null
    }
    val canSave = errorKey == null && !saving
    val charCount = stringResource(R.string.ct_chars_fmt, content.length)

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).imePadding()
                .clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)
        ) {
            Text(
                stringResource(if (isCreate) R.string.ct_create else R.string.ct_edit),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
            )

            InfraFieldLabel(stringResource(R.string.ct_name))
            CompactGlassField(
                value = name,
                onValueChange = { name = it; touched = true; serverError = null },
                placeholder = stringResource(R.string.ct_name_hint),
                keyboardType = KeyboardType.Text,
                leadingAppIcon = AppIcon.Template
            )

            if (isCreate) {
                InfraFieldLabel(stringResource(R.string.ct_type))
                ChipSelector(
                    values = ClientTemplateType.ALL,
                    labels = ClientTemplateType.ALL.map { templateTypeLabel(it) },
                    selected = type,
                    onSelect = { type = it; serverError = null }
                )
            } else {
                // نوع پس از ساخت تغییر نمی‌کند؛ به‌جای چیپِ غیرفعال، متنِ ثابت + دلیل.
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                ) {
                    PGBadge(templateTypeLabel(type), theme.accentPrimary)
                    Text(
                        stringResource(R.string.ct_type_locked),
                        fontSize = 10.sp, color = theme.mutedColor
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = DsSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfraFieldLabel(stringResource(R.string.ct_content))
                Text(charCount, fontSize = 10.sp, color = theme.mutedColor)
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp)
                    .clip(DsRadius.Md)
                    .background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                    .padding(DsSpacing.Mid)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = content,
                    onValueChange = { content = it; touched = true; serverError = null },
                    textStyle = TextStyle(
                        color = theme.inkColor, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    enabled = !saving
                )
                if (content.isEmpty()) Text(
                    stringResource(R.string.ct_content_hint),
                    fontSize = 11.sp, color = theme.mutedColor.copy(0.6f)
                )
            }

            Row(
                Modifier.fillMaxWidth().clip(DsRadius.Md).clickable { isDefault = !isDefault },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
            ) {
                CheckboxIcon(selected = isDefault, onToggle = { isDefault = !isDefault })
                Text(
                    stringResource(R.string.ct_default), fontSize = 12.sp, color = theme.inkColor
                )
            }

            errorText?.let {
                Text(it, fontSize = 11.sp, color = DsSemantic.Danger, fontWeight = FontWeight.SemiBold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
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
                            onSave(name.trim(), type, content, isDefault) { err ->
                                saving = false
                                serverError = err
                            }
                        }
                    },
                    // بی‌وزن تا متنِ دکمه بریده نشود؛ «لغو» باقیِ جا را می‌گیرد.
                    enabled = canSave
                )
            }
        }
    }
}
