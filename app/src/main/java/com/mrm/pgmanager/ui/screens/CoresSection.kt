package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.CoreType
import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** نام / نوع / تگ‌ها / ترتیب — همان چهار اطلاعاتی که قبلاً سه چیپِ رنگی بود. */
private val CORE_COLUMNS = listOf(
    InfraColumn(R.string.col_name, 1f),
    InfraColumn(R.string.col_type, 0.66f),
    InfraColumn(R.string.col_tags, 0.52f),
    InfraColumn(R.string.col_sort, 0.4f)
)

/** Core listing and metadata editing; structural configuration remains in the panel. */
@Composable
internal fun CoresSection(session: Session, sectionTabs: @Composable () -> Unit) {
    androidx.compose.runtime.key(session) {
        CoresSectionContent(session, sectionTabs)
    }
}

@Composable
private fun CoresSectionContent(session: Session, sectionTabs: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val editorModel: CoreEditorViewModel = viewModel()
    val editor = editorModel.state
    val editorSnapshot by editor.snapshot.collectAsState()
    val activeEditor = editorSnapshot.active?.takeIf { it.session == session }
    val saveNotice = editorSnapshot.notice?.takeIf { it.session == session }

    val coresKey = PanelCache.coresKey(session.baseUrl)
    val accessState = com.mrm.pgmanager.data.AdminAccess.state
    val canRead = com.mrm.pgmanager.data.AdminAccess.can("cores", "read")
    var cores by remember(session) { mutableStateOf(emptyList<PanelCore>()) }
    var loading by remember(session) { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PanelCore?>(null) }
    var restarting by remember { mutableStateOf<PanelCore?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var loadRequest by remember { mutableStateOf(0L) }

    val msgUpdated = stringResource(R.string.core_updated)
    val msgRestarted = stringResource(R.string.core_restarted)
    val msgDeleted = stringResource(R.string.core_deleted)

    suspend fun load() {
        val request = ++loadRequest
        if (!canRead) {
            cores = emptyList()
            loading = false
            loadError = null
            return
        }
        try {
            val result = PanelApi.cores(session)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (request == loadRequest) {
                cores = result
                loadError = null
                PanelCache.put(coresKey, result)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (request == loadRequest) {
                val kind = com.mrm.pgmanager.utils.ApiErrorMapper.kindOf(e)
                if (kind == com.mrm.pgmanager.utils.ApiErrorKind.FORBIDDEN || kind == com.mrm.pgmanager.utils.ApiErrorKind.UNAUTHORIZED) cores = emptyList()
                loadError = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e)
            }
        } finally {
            if (request == loadRequest) loading = false
        }
    }

    LaunchedEffect(session, canRead) {
        editor.bind(session)
        loading = canRead
        load()
    }

    LaunchedEffect(saveNotice) {
        saveNotice?.let {
            toast = msgUpdated
            load()
            editor.consumeNotice(it.requestId)
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
    val canUpdate = canRead && com.mrm.pgmanager.data.AdminAccess.can("cores", "update")
    val canDelete = canRead && com.mrm.pgmanager.data.AdminAccess.can("cores", "delete")

    val intro = if (filtered.isNotEmpty()) rememberListIntro() else null
    InfraScrollableContent(
        listTag = "infra_cores_list",
        header = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                sectionTabs()
                InfraSectionHeader(
                    title = stringResource(R.string.cores_title),
                    subtitle = stringResource(R.string.cores_subtitle),
                    count = if (canRead) cores.size else null,
                    onRefresh = { if (canRead) scope.launch { loading = cores.isEmpty(); load() } },
                    // ساخت از گوشی ممکن نیست (کانفیگِ JSON کامل لازم است) — توضیحش در حالتِ خالی است.
                    onAdd = null,
                    addLabel = ""
                )
                if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
                banner?.let { InfraBanner(text = it, success = false) { banner = null } }
                if (cores.isNotEmpty()) loadError?.let { InfraBanner(text = it, success = false) { loadError = null } }
                if (cores.isNotEmpty()) {
                    PGSearchBar(
                        query = query, onQueryChange = { query = it },
                        placeholder = stringResource(R.string.search_cores)
                    )
                }
                if (cores.isNotEmpty() && filtered.isNotEmpty()) InfraTableHeader(CORE_COLUMNS)
            }
        }
    ) {
        when {
            accessState == com.mrm.pgmanager.data.AdminAccessState.Unknown ||
                accessState == com.mrm.pgmanager.data.AdminAccessState.Loading -> item(key = "cores_access_loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp)
                }
            }
            !canRead -> item(key = "cores_permission") {
                MrmEmptyState(
                    title = stringResource(if (accessState == com.mrm.pgmanager.data.AdminAccessState.Unavailable) R.string.access_unavailable_title else R.string.core_read_permission),
                    subtitle = stringResource(if (accessState == com.mrm.pgmanager.data.AdminAccessState.Unavailable) R.string.access_unavailable else R.string.core_read_permission_hint),
                    icon = AppIcon.Lock
                )
            }
            loading && cores.isEmpty() -> item(key = "cores_loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp)
                }
            }
            loadError != null && cores.isEmpty() -> item(key = "cores_error") {
                MrmErrorState(onRetry = { scope.launch { loading = true; load() } }, message = loadError)
            }
            cores.isEmpty() -> item(key = "cores_empty") {
                MrmEmptyState(
                    title = stringResource(R.string.no_cores),
                    subtitle = stringResource(R.string.no_cores_hint),
                    icon = AppIcon.Storage
                ) {
                    PGSecondaryButton(stringResource(R.string.refresh), onClick = { scope.launch { loading = true; load() } })
                }
            }
            filtered.isEmpty() -> item(key = "cores_no_results") {
                MrmEmptyState(
                    title = stringResource(R.string.no_results),
                    subtitle = stringResource(R.string.no_results_hint),
                    icon = AppIcon.Search
                ) { PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { query = "" }) }
            }
            else -> itemsIndexed(filtered, key = { _, core -> core.id }) { index, core ->
                Box(Modifier.animateItem().listIntro(requireNotNull(intro), index)) {
                    CoreRow(
                        core = core,
                        busy = busyId == core.id,
                        onEdit = if (canUpdate && busyId == null) ({ editor.open(session, core) }) else null,
                        onRestart = if (canUpdate && busyId == null) ({ restarting = core }) else null,
                        onDelete = if (canDelete && core.id > 1 && busyId == null) ({ deleting = core }) else null
                    )
                }
            }
        }
    }

    activeEditor?.takeIf { canUpdate }?.let { current ->
        val target = current.initial
        var tagsLoading by remember(target) { mutableStateOf(true) }
        var availableTags by remember(target) { mutableStateOf<List<String>?>(null) }
        LaunchedEffect(target) {
            availableTags = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.mrm.pgmanager.data.api.coreConfigInboundTags(target.config, target.type)
            }
            tagsLoading = false
        }
        CoreEditorDialog(
            initial = target,
            draft = current.draft,
            saving = current.saving,
            error = current.error,
            availableTags = availableTags,
            tagsLoading = tagsLoading,
            onDraftChange = { editor.update(current.requestId, it) },
            onDismiss = { editor.dismiss(current.requestId) },
            onSave = save@ {
                val operation = editor.beginSave(current.requestId) ?: return@save
                val appContext = context.applicationContext.createConfigurationContext(context.resources.configuration)
                editorModel.viewModelScope.launch {
                    try {
                        val draft = operation.draft
                        PanelApi.modifyCore(
                            session = operation.session, core = operation.initial,
                            name = draft.name, type = draft.type.ifBlank { null },
                            excludeInboundTags = draft.exclude.toList(), fallbacksInboundTags = draft.fallbacks.toList(),
                            restartNodes = draft.restartNodes
                        )
                        editor.finishSave(operation.requestId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = when {
                            e is com.mrm.pgmanager.data.api.CoreEditConflictException -> appContext.getString(R.string.core_edit_conflict)
                            e is com.mrm.pgmanager.data.api.CoreUpdateUnconfirmedException -> appContext.getString(R.string.core_update_unconfirmed)
                            e is IllegalArgumentException ||
                                com.mrm.pgmanager.utils.ApiErrorMapper.kindOf(e) == com.mrm.pgmanager.utils.ApiErrorKind.VALIDATION ->
                                appContext.getString(R.string.core_edit_rejected)
                            else -> com.mrm.pgmanager.utils.ApiErrorMapper.friendly(appContext, e)
                        }
                        editor.failSave(operation.requestId, message)
                    }
                }
            }
        )
    }

    restarting?.takeIf { canUpdate }?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.core_restart),
            message = stringResource(R.string.core_restart_confirm, target.name),
            confirmLabel = stringResource(R.string.core_restart),
            onDismiss = { restarting = null },
            onConfirm = {
                busyId = target.id
                restarting = null
                scope.launch {
                    try {
                        PanelApi.restartCore(session, target.id)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        toast = msgRestarted
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e)
                    }
                    busyId = null
                }
            }
        )
    }

    deleting?.takeIf { canDelete }?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.delete_core),
            message = stringResource(R.string.delete_core_confirm, target.name),
            confirmLabel = stringResource(R.string.delete_core),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                busyId = target.id
                deleting = null
                scope.launch {
                    try {
                        PanelApi.deleteCore(session, target.id)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        toast = msgDeleted
                        load()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e)
                    }
                    busyId = null
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
    val tagCount = core.excludeInboundTags.size + core.fallbacksInboundTags.size
    InfraTableRow(
        columns = CORE_COLUMNS,
        name = core.name,
        cells = listOf(
            coreTypeLabel(core.type),
            if (tagCount > 0) stringResource(R.string.core_tags_fmt, tagCount) else "—",
            if (core.sortOrder > 0) core.sortOrder.toString() else "—"
        ),
        // کور وضعیتِ فعال/غیرفعال ندارد، پس نقطه‌ای نیست — ولی خانه‌اش رزرو
        // می‌ماند تا ستونِ نامِ سه جدول هم‌عرض بماند.
        dot = null,
        busy = busy,
        actions = buildList {
            if (onEdit != null) add(InfraRowAction(R.string.qa_edit) { onEdit() })
            if (onRestart != null) add(InfraRowAction(R.string.core_restart) { onRestart() })
            if (onDelete != null) add(InfraRowAction(R.string.delete_core, danger = true) { onDelete() })
        },
        onEdit = onEdit
    )
}

/** Known types are localized; null is the panel default and unknown types remain visible. */
@Composable
internal fun coreTypeLabel(type: String?): String = when (type) {
    CoreType.XRAY -> stringResource(R.string.core_type_xray)
    CoreType.WG -> stringResource(R.string.core_type_wg)
    CoreType.MTPROTO -> stringResource(R.string.core_type_mtproto)
    CoreType.SING_BOX -> stringResource(R.string.core_type_singbox)
    null, "" -> stringResource(R.string.core_type_unknown)
    else -> type
}
