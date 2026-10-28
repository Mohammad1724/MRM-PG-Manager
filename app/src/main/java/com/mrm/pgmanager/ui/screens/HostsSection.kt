package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.HostSecurity
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
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
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/**
 * سه ستونِ پنل + اولویت. سرتیتر و هر ردیف همین لیست را می‌گیرند، پس لبهٔ
 * چپِ ستون‌ها در همهٔ سطرها یکی است (کارتِ قبلی جهتِ هر متن را خودش می‌گذاشت).
 */
private val HOST_COLUMNS = listOf(
    InfraColumn(R.string.col_name, 1f),
    InfraColumn(R.string.col_address, 0.78f),
    InfraColumn(R.string.col_inbound, 0.72f),
    InfraColumn(R.string.col_priority, 0.30f)
)

/** Host list and actions. The editor performs a guarded full-body update. */
@Composable
internal fun HostsSection(session: Session, sectionTabs: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val editorModel: HostEditorViewModel = viewModel()
    val editor = editorModel.state
    val editorSnapshot by editor.snapshot.collectAsState()
    val activeEditor = editorSnapshot.active?.takeIf { it.session == session }
    val saveNotice = editorSnapshot.notice?.takeIf { it.session == session }

    val hostsKey = PanelCache.hostsKey(session.baseUrl)
    var hosts by remember(session) {
        mutableStateOf(PanelCache.get<List<PanelHost>>(hostsKey) ?: emptyList())
    }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<PanelHost>>(hostsKey) == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PanelHost?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }

    val msgCreated = stringResource(R.string.host_created)
    val msgUpdated = stringResource(R.string.host_updated)
    val msgDeleted = stringResource(R.string.host_deleted)
    val msgEnabledFmt = stringResource(R.string.host_enabled_fmt)
    val msgDisabledFmt = stringResource(R.string.host_disabled_fmt)

    suspend fun load() {
        try {
            try {
                hosts = PanelApi.hosts(session)
                loadError = null
                PanelCache.put(hostsKey, hosts)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e)
            }
            // Keep the shared inbound cache warm for the other infrastructure sections.
            PanelCache.put(PanelCache.inboundsKey(session.baseUrl), PanelApi.inboundTags(session))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session) {
        editor.bind(session)
        if (!PanelCache.isFresh(hostsKey)) {
            loading = hosts.isEmpty()
            load()
        }
    }

    LaunchedEffect(saveNotice) {
        saveNotice?.let {
            toast = if (it.created) msgCreated else msgUpdated
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

    val filtered = remember(hosts, query) {
        val q = query.trim()
        if (q.isEmpty()) hosts
        else hosts.filter { h ->
            h.remark.contains(q, ignoreCase = true) ||
                h.addresses.any { it.contains(q, ignoreCase = true) } ||
                h.inboundTag?.contains(q, ignoreCase = true) == true
        }
    }

    val canCreate = com.mrm.pgmanager.data.AdminAccess.can("hosts", "create")
    val canUpdate = com.mrm.pgmanager.data.AdminAccess.can("hosts", "update")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A phone or large text needs more room per value than a four-column table.
        val readableRows = maxWidth < 560.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val intro = if (filtered.isNotEmpty()) rememberListIntro() else null
        InfraScrollableContent(
            listTag = "infra_hosts_list",
            header = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                    sectionTabs()
                    InfraSectionHeader(
                        title = stringResource(R.string.hosts_title),
                        subtitle = stringResource(R.string.hosts_subtitle),
                        count = hosts.size,
                        onRefresh = { scope.launch { loading = hosts.isEmpty(); load() } },
                        onAdd = if (canCreate) ({ editor.open(session, PanelHost(id = 0, remark = "")) }) else null,
                        addLabel = stringResource(R.string.add_host)
                    )
                    if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
                    banner?.let { InfraBanner(text = it, success = false) { banner = null } }
                    if (hosts.isNotEmpty()) {
                        PGSearchBar(
                            query = query, onQueryChange = { query = it },
                            placeholder = stringResource(R.string.search_hosts)
                        )
                    }
                    if (hosts.isNotEmpty() && filtered.isNotEmpty() && !readableRows) {
                        InfraTableHeader(HOST_COLUMNS)
                    }
                }
            }
        ) {
            when {
                loading && hosts.isEmpty() -> item(key = "hosts_loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp)
                    }
                }
                loadError != null && hosts.isEmpty() -> item(key = "hosts_error") {
                    MrmErrorState(onRetry = { scope.launch { loading = true; load() } }, message = loadError)
                }
                hosts.isEmpty() -> item(key = "hosts_empty") {
                    MrmEmptyState(
                        title = stringResource(R.string.no_hosts),
                        subtitle = stringResource(R.string.no_hosts_hint),
                        icon = AppIcon.Link
                    ) {
                        if (canCreate) PGPrimaryButton(
                            stringResource(R.string.add_host),
                            onClick = { editor.open(session, PanelHost(id = 0, remark = "")) },
                            icon = AppIcon.Add
                        )
                    }
                }
                filtered.isEmpty() -> item(key = "hosts_no_results") {
                    MrmEmptyState(
                        title = stringResource(R.string.no_results),
                        subtitle = stringResource(R.string.no_results_hint),
                        icon = AppIcon.Search
                    ) { PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { query = "" }) }
                }
                else -> itemsIndexed(filtered, key = { _, host -> host.id }) { index, host ->
                    Box(Modifier.animateItem().listIntro(requireNotNull(intro), index)) {
                        HostRow(
                            host = host,
                            readable = readableRows,
                            busy = busyId == host.id,
                            onEdit = if (canUpdate) ({ editor.open(session, host) }) else null,
                            onToggle = if (canUpdate) ({
                                busyId = host.id
                                scope.launch {
                                    runCatching {
                                        PanelApi.setHostsDisabled(session, setOf(host.id), !host.isDisabled)
                                    }.onSuccess { count ->
                                        toast = if (host.isDisabled) String.format(msgEnabledFmt, count)
                                        else String.format(msgDisabledFmt, count)
                                        load()
                                    }.onFailure {
                                        banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it)
                                    }
                                    busyId = null
                                }
                            }) else null,
                            onDelete = if (canUpdate) ({ deleting = host }) else null
                        )
                    }
                }
            }
        }
    }

    activeEditor?.let { current ->
        HostEditorDialog(
            initial = current.initial,
            session = current.session,
            draft = current.draft,
            saving = current.saving,
            error = current.error,
            onDraftChange = { editor.update(current.requestId, it) },
            onDismiss = { editor.dismiss(current.requestId) },
            onSave = save@ {
                val operation = editor.beginSave(current.requestId) ?: return@save
                val appContext = context.applicationContext.createConfigurationContext(context.resources.configuration)
                editorModel.viewModelScope.launch {
                    val target = operation.initial
                    val draft = operation.draft
                    try {
                        val changes = draft.changes(target)
                        if (target.id == 0) {
                            PanelApi.createHostWithChanges(
                                session = operation.session,
                                remark = draft.remark,
                                addresses = draft.addresses,
                                inboundTag = draft.inboundTag,
                                port = draft.port,
                                security = draft.security,
                                changes = changes
                            )
                        } else {
                            if (changes.isEmpty()) {
                                editor.failSave(operation.requestId, appContext.getString(R.string.host_editor_no_changes))
                                return@launch
                            }
                            PanelApi.modifyHostSafely(operation.session, target, changes)
                        }
                        editor.finishSave(operation.requestId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = if (e is com.mrm.pgmanager.data.api.HostEditConflictException) {
                            appContext.getString(R.string.host_editor_conflict)
                        } else {
                            com.mrm.pgmanager.utils.ApiErrorMapper.friendly(appContext, e)
                        }
                        editor.failSave(operation.requestId, message)
                    }
                }
            }
        )
    }

    deleting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.delete_host),
            message = stringResource(R.string.delete_host_confirm, target.remark),
            confirmLabel = stringResource(R.string.delete_host),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    runCatching { PanelApi.deleteHost(session, target.id) }
                        .onSuccess { toast = msgDeleted; load() }
                        .onFailure { banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
                    deleting = null
                }
            }
        )
    }
}

/** A host keeps the same actions in both the wide table and narrow list. */
@Composable
internal fun HostRow(
    host: PanelHost,
    readable: Boolean,
    busy: Boolean,
    onEdit: (() -> Unit)?,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val explicitSecurity = when (host.security) {
        HostSecurity.NONE, HostSecurity.TLS -> securityLabel(host.security)
        else -> ""
    }
    val tag = host.inboundTag?.takeIf { it.isNotBlank() }
    val address = buildString {
        append(host.addresses.joinToString(", "))
        host.port?.let { append(":").append(it) }
    }.ifBlank { "—" }
    val inbound = when {
        tag != null && explicitSecurity.isNotEmpty() -> "$tag · $explicitSecurity"
        tag != null -> tag
        else -> explicitSecurity.ifEmpty { "—" }
    }
    val priority = host.priority?.toString() ?: "—"
    val actions = hostRowActions(host, onEdit, onToggle, onDelete)
    if (readable) {
        HostReadableRow(
            name = host.remark, address = address, inbound = inbound, priority = priority,
            disabled = host.isDisabled, busy = busy, actions = actions, onEdit = onEdit
        )
    } else {
        InfraTableRow(
            columns = HOST_COLUMNS,
            name = host.remark,
            cells = listOf(address, inbound, priority),
            dot = if (host.isDisabled) theme.mutedColor else DsSemantic.Success,
            muted = host.isDisabled,
            busy = busy,
            actions = actions,
            onEdit = onEdit
        )
    }
}

internal fun hostRowActions(
    host: PanelHost,
    onEdit: (() -> Unit)?,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
): List<InfraRowAction> = buildList {
    if (onEdit != null) add(InfraRowAction(R.string.qa_edit) { onEdit() })
    if (onToggle != null) add(
        InfraRowAction(if (host.isDisabled) R.string.host_toggle_enable else R.string.host_toggle_disable) { onToggle() }
    )
    if (onDelete != null) add(InfraRowAction(R.string.delete_host, danger = true) { onDelete() })
}

/** Separate labeled values on narrow screens instead of squeezing four columns together. */
@Composable
private fun HostReadableRow(
    name: String,
    address: String,
    inbound: String,
    priority: String,
    disabled: Boolean,
    busy: Boolean,
    actions: List<InfraRowAction>,
    onEdit: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val statusLabel = stringResource(if (disabled) R.string.host_disabled_badge else R.string.active)
    Column(
        Modifier.fillMaxWidth().testTag("host_readable_row")
            .clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .then(if (onEdit != null) Modifier.clickable { onEdit() } else Modifier)
            .padding(start = DsSpacing.Card, end = DsSpacing.Card, top = DsSpacing.Xxs, bottom = DsSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)
    ) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
        ) {
            Box(
                Modifier.size(12.dp).semantics { contentDescription = statusLabel },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(7.dp).clip(DsRadius.Full)
                        .background(if (disabled) theme.mutedColor else DsSemantic.Success)
                )
            }
            Text(
                name, modifier = Modifier.weight(1f).testTag("host_readable_name"),
                fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                color = if (disabled) theme.mutedColor else theme.inkColor,
                textAlign = com.mrm.pgmanager.ui.components.PGStartAlign()
            )
            InfraRowActions(busy = busy, actions = actions)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = DsSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)
        ) {
            HostInfoLine(stringResource(R.string.col_address), address, "host_readable_address")
            HostInfoLine(stringResource(R.string.col_inbound), inbound, "host_readable_inbound")
            HostInfoLine(stringResource(R.string.col_priority), priority, "host_readable_priority")
        }
    }
}

@Composable
private fun HostInfoLine(label: String, value: String, tag: String) {
    val theme = LocalThemeState.current
    // Match the paragraph and layout widths, while preserving value space at normal text sizes.
    val labelWidth = (54f * LocalDensity.current.fontScale).coerceIn(54f, 96f).dp
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Text(
            "$label:", fontSize = 10.sp, lineHeight = 16.sp, color = theme.mutedColor,
            modifier = Modifier.width(labelWidth).alignByBaseline().testTag("${tag}_label"),
            textAlign = com.mrm.pgmanager.ui.components.PGStartAlign()
        )
        Text(
            value, modifier = Modifier.weight(1f).alignByBaseline().testTag(tag),
            fontSize = 11.sp, lineHeight = 16.sp, color = theme.inkColor,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            textAlign = androidx.compose.ui.text.style.TextAlign.Left
        )
    }
}

/** برچسبِ فارسی/انگلیسیِ `security` — مقدارِ خامِ پنل نگاشت می‌شود. */
@Composable
private fun securityLabel(value: String): String = when (value) {
    HostSecurity.NONE -> stringResource(R.string.host_security_none)
    HostSecurity.TLS -> stringResource(R.string.host_security_tls)
    else -> stringResource(R.string.host_security_inbound_default)
}
