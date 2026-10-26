package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.CoreType
import com.mrm.pgmanager.data.model.PanelCore
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
 * بخشِ «کورها» — فهرست، ویرایشِ فیلدهای کوتاه، ری‌استارتِ نودها و حذف.
 *
 * ### چرا «ساختِ کور» ندارد
 * پنل برای ساخت، یک `config` کاملِ JSON (inbounds/outbounds/routing/…) می‌خواهد و
 * بی‌آن ۴۲۲ می‌دهد. ساختنش در گوشی یعنی ادیتورِ JSON روی صفحهٔ کوچک؛ کاری که
 * ادمین یک‌بار در پنلِ وب انجام می‌دهد. در عوض چیزی که در گوشی واقعاً به‌کار
 * می‌آید اینجاست: تغییرِ نام/نوع، تگ‌های استثنا و فالبک، ری‌استارتِ نودها پس از
 * تغییرِ کانفیگ، و حذف.
 *
 * ### نکتهٔ قرارداد
 * `PUT /api/cores/{id}` دو چیز را **الزامی** می‌خواهد که اگر از قلم بیفتند
 * همیشه ۴۲۲ می‌گیریم: کوئریِ `restart_nodes` (بدونِ پیش‌فرض) و `config` کامل.
 * [PanelCore.config] رشتهٔ خام است و دست‌نخورده برگردانده می‌شود.
 */
@Composable
internal fun CoresSection(session: Session) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()

    val coresKey = PanelCache.coresKey(session.baseUrl)
    val inboundsKey = PanelCache.inboundsKey(session.baseUrl)
    var cores by remember(session) {
        mutableStateOf(PanelCache.get<List<PanelCore>>(coresKey) ?: emptyList())
    }
    var inbounds by remember(session) {
        mutableStateOf(PanelCache.get<List<String>>(inboundsKey) ?: emptyList())
    }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<PanelCore>>(coresKey) == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<PanelCore?>(null) }
    var deleting by remember { mutableStateOf<PanelCore?>(null) }
    var restarting by remember { mutableStateOf<PanelCore?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }

    val msgUpdated = stringResource(R.string.core_updated)
    val msgRestarted = stringResource(R.string.core_restarted)
    val msgDeleted = stringResource(R.string.core_deleted)

    suspend fun load() {
        runCatching { PanelApi.cores(session) }
            .onSuccess { cores = it; loadError = null; PanelCache.put(coresKey, it) }
            .onFailure { loadError = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
        runCatching { PanelApi.inboundTags(session) }
            .onSuccess { inbounds = it; PanelCache.put(inboundsKey, it) }
        loading = false
    }

    LaunchedEffect(session) {
        if (!PanelCache.isFresh(coresKey)) {
            loading = cores.isEmpty()
            load()
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    val filtered = remember(cores, query) {
        val q = query.trim()
        if (q.isEmpty()) cores
        else cores.filter { it.name.contains(q, ignoreCase = true) }
    }
    val canUpdate = com.mrm.pgmanager.data.AdminAccess.can("cores", "update")
    val canDelete = com.mrm.pgmanager.data.AdminAccess.can("cores", "delete")

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
        InfraSectionHeader(
            title = stringResource(R.string.cores_title),
            subtitle = stringResource(R.string.cores_subtitle),
            count = cores.size,
            onRefresh = { scope.launch { loading = cores.isEmpty(); load() } },
            // ساخت از گوشی ممکن نیست (کانفیگِ JSON کامل لازم است) — توضیحش در حالتِ خالی است.
            onAdd = null,
            addLabel = ""
        )

        if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
        banner?.let { InfraBanner(text = it, success = false) { banner = null } }

        if (cores.isNotEmpty()) {
            PGSearchBar(
                query = query, onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_cores)
            )
        }

        when {
            loading && cores.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) { MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp) }

            loadError != null && cores.isEmpty() -> MrmErrorState(
                onRetry = { scope.launch { loading = true; load() } },
                message = loadError
            )

            cores.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.no_cores),
                subtitle = stringResource(R.string.no_cores_hint),
                icon = AppIcon.Storage
            ) { PGSecondaryButton(stringResource(R.string.refresh), onClick = { scope.launch { loading = true; load() } }) }

            filtered.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.no_results),
                subtitle = stringResource(R.string.no_results_hint),
                icon = AppIcon.Search
            ) { PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { query = "" }) }

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
                    itemsIndexed(filtered, key = { _, c -> c.id }) { index, core ->
                        Box(Modifier.animateItem().listIntro(intro, index)) {
                            CoreRow(
                                core = core,
                                busy = busyId == core.id,
                                onEdit = if (canUpdate) ({ editing = core }) else null,
                                onRestart = if (canUpdate) ({ restarting = core }) else null,
                                onDelete = if (canDelete) ({ deleting = core }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        CoreEditorDialog(
            initial = target,
            availableTags = inbounds,
            onDismiss = { editing = null },
            onSave = { name, type, exclude, fallbacks, restartNodes, onResult ->
                scope.launch {
                    runCatching {
                        PanelApi.modifyCore(
                            session = session, core = target, name = name, type = type,
                            excludeInboundTags = exclude, fallbacksInboundTags = fallbacks,
                            restartNodes = restartNodes
                        )
                    }.onSuccess {
                        editing = null
                        toast = msgUpdated
                        load()
                    }.onFailure { e ->
                        onResult(com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e))
                    }
                }
            }
        )
    }

    restarting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.core_restart),
            message = stringResource(R.string.core_restart_confirm, target.name),
            confirmLabel = stringResource(R.string.core_restart),
            onDismiss = { restarting = null },
            onConfirm = {
                busyId = target.id
                scope.launch {
                    runCatching { PanelApi.restartCore(session, target.id) }
                        .onSuccess { toast = msgRestarted }
                        .onFailure { banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
                    busyId = null
                    restarting = null
                }
            }
        )
    }

    deleting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.delete_core),
            message = stringResource(R.string.delete_core_confirm, target.name),
            confirmLabel = stringResource(R.string.delete_core),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    runCatching { PanelApi.deleteCore(session, target.id) }
                        .onSuccess { toast = msgDeleted; load() }
                        .onFailure { banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
                    deleting = null
                }
            }
        )
    }
}

/** یک ردیفِ کور: نام، نوع، ترتیب و تعدادِ تگ‌های استثنا/فالبک. */
@Composable
private fun CoreRow(
    core: PanelCore,
    busy: Boolean,
    onEdit: (() -> Unit)?,
    onRestart: (() -> Unit)?,
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
        ) { RoundedAppIcon(AppIcon.Storage, tint = theme.accentPrimary, size = 16.dp) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
            Text(
                core.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = PGStartAlign()
            )
            PGWrapRow(horizontalGap = DsSpacing.Xs, verticalGap = DsSpacing.Xxs) {
                PGBadge(coreTypeLabel(core.type), theme.accentPrimary)
                if (core.sortOrder > 0) PGBadge(
                    stringResource(R.string.core_sort_fmt, core.sortOrder),
                    theme.mutedColor
                )
                val tagCount = core.excludeInboundTags.size + core.fallbacksInboundTags.size
                if (tagCount > 0) PGBadge(
                    stringResource(R.string.core_tags_fmt, tagCount),
                    DsSemantic.Violet
                )
            }
        }

        if (busy) {
            MrmBusyIndicator(color = theme.accentPrimary, size = 18.dp, strokeWidth = 2.dp)
        } else {
            if (onRestart != null) InfraIconButton(
                icon = AppIcon.Reset,
                description = stringResource(R.string.core_restart),
                tint = DsSemantic.Warning,
                onClick = onRestart
            )
            if (onDelete != null) InfraIconButton(
                icon = AppIcon.Delete,
                description = stringResource(R.string.delete_core),
                tint = DsSemantic.Danger,
                onClick = onDelete
            )
        }
    }
}

/** برچسبِ نوعِ کور؛ null یعنی «پنل خودش تشخیص می‌دهد». */
@Composable
private fun coreTypeLabel(type: String?): String = when (type) {
    CoreType.XRAY -> stringResource(R.string.core_type_xray)
    CoreType.WG -> stringResource(R.string.core_type_wg)
    CoreType.MTPROTO -> stringResource(R.string.core_type_mtproto)
    CoreType.SING_BOX -> stringResource(R.string.core_type_singbox)
    else -> stringResource(R.string.core_type_unknown)
}

/**
 * دیالوگِ ویرایشِ کور: نام، نوع، تگ‌های استثنا/فالبک و «نودها هم ری‌استارت شوند».
 * کانفیگ نمایش داده نمی‌شود ولی در بدنهٔ PUT دست‌نخورده فرستاده می‌شود.
 */
@Composable
private fun CoreEditorDialog(
    initial: PanelCore,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: String?,
        exclude: List<String>,
        fallbacks: List<String>,
        restartNodes: Boolean,
        onResult: (String) -> Unit
    ) -> Unit
) {
    val theme = LocalThemeState.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var type by remember(initial.id) { mutableStateOf(initial.type ?: "") }
    var exclude by remember(initial.id) { mutableStateOf(initial.excludeInboundTags.toSet()) }
    var fallbacks by remember(initial.id) { mutableStateOf(initial.fallbacksInboundTags.toSet()) }
    var restartNodes by remember(initial.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var serverError by remember(initial.id) { mutableStateOf<String?>(null) }

    // تگ‌های فعلیِ همین کور هم نمایش داده می‌شوند، حتی اگر دیگر در پنل نباشند؛
    // وگرنه یک «ذخیره» بی‌صدا آن‌ها را حذف می‌کرد.
    val allTags = remember(availableTags, initial.excludeInboundTags, initial.fallbacksInboundTags) {
        (availableTags + initial.excludeInboundTags + initial.fallbacksInboundTags).distinct()
    }
    val typeValues = remember { listOf("") + CoreType.ALL }
    val typeLabels = typeValues.map { if (it.isEmpty()) stringResource(R.string.core_type_unknown) else coreTypeLabel(it) }
    val errName = stringResource(R.string.err_core_name)
    val errorText = when {
        serverError != null -> serverError
        touched && name.trim().isEmpty() -> errName
        else -> null
    }
    val canSave = name.trim().isNotEmpty() && !saving

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
                stringResource(R.string.core_edit_title), fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = theme.inkColor
            )

            InfraFieldLabel(stringResource(R.string.core_name))
            CompactGlassField(
                value = name,
                onValueChange = { name = it; touched = true; serverError = null },
                placeholder = stringResource(R.string.core_name_hint),
                keyboardType = KeyboardType.Text,
                leadingAppIcon = AppIcon.Storage
            )

            InfraFieldLabel(stringResource(R.string.core_type))
            ChipSelector(
                values = typeValues,
                labels = typeLabels,
                selected = type,
                onSelect = { type = it; serverError = null }
            )

            TagPicker(
                label = stringResource(R.string.core_exclude_tags),
                allTags = allTags,
                selected = exclude,
                onToggle = { tag -> exclude = if (tag in exclude) exclude - tag else exclude + tag }
            )

            TagPicker(
                label = stringResource(R.string.core_fallback_tags),
                allTags = allTags,
                selected = fallbacks,
                onToggle = { tag -> fallbacks = if (tag in fallbacks) fallbacks - tag else fallbacks + tag }
            )

            Row(
                Modifier.fillMaxWidth().clip(DsRadius.Md).clickable { restartNodes = !restartNodes },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
            ) {
                CheckboxIcon(selected = restartNodes, onToggle = { restartNodes = !restartNodes })
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.core_restart_nodes), fontSize = 12.sp,
                        color = theme.inkColor
                    )
                    Text(
                        stringResource(R.string.core_restart_nodes_hint),
                        fontSize = 10.sp, color = theme.mutedColor
                    )
                }
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
                            onSave(
                                name.trim(),
                                type.ifBlank { null },
                                exclude.toList(),
                                fallbacks.toList(),
                                restartNodes
                            ) { err ->
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

/**
 * انتخابگرِ چندگانهٔ تگ‌ها با ارتفاعِ محدود (تا دیالوگ از صفحه بیرون نزند).
 * وقتی تگی موجود نیست، به‌جای لیستِ خالی یک جملهٔ روشن نشان می‌دهد.
 */
@Composable
private fun TagPicker(
    label: String,
    allTags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfraFieldLabel(label)
            if (selected.isNotEmpty()) PGBadge("${selected.size}")
        }
        if (allTags.isEmpty()) {
            Text(stringResource(R.string.core_no_tags), fontSize = 11.sp, color = theme.mutedColor)
        } else {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())
                    .clip(DsRadius.Md).background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                    .padding(vertical = DsSpacing.Xs)
            ) {
                allTags.forEach { tag ->
                    val checked = tag in selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            .clickable { onToggle(tag) }
                            .padding(horizontal = DsSpacing.Mid),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
                    ) {
                        CheckboxIcon(selected = checked, onToggle = { onToggle(tag) })
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
}
