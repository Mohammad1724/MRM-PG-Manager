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
import androidx.compose.runtime.key
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
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.NodeEditConflictException
import com.mrm.pgmanager.data.api.NodeWriteUnconfirmedException
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorKind
import com.mrm.pgmanager.utils.ApiErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private val NODE_COLUMNS = listOf(
    InfraColumn(R.string.node_name, 1f),
    InfraColumn(R.string.node_address, 1f),
    InfraColumn(R.string.node_status, 0.8f)
)

@Composable
internal fun NodesSection(session: Session, sectionTabs: @Composable () -> Unit) {
    key(session) { NodesSectionContent(session, sectionTabs) }
}

@Composable
private fun NodesSectionContent(session: Session, sectionTabs: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext.createConfigurationContext(context.resources.configuration)
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val model: NodeEditorViewModel = viewModel()
    val state = model.state
    val snapshot by state.snapshot.collectAsState()
    val active = snapshot.active?.takeIf { it.session == session }
    val notice = snapshot.notice?.takeIf { it.session == session }
    val action = snapshot.action?.takeIf { it.session == session }
    val actionNotice = snapshot.actionNotice?.takeIf { it.request.session == session }

    val canRead = AdminAccess.can("nodes", "read")
    val canCreate = AdminAccess.can("nodes", "create")
    val canUpdate = AdminAccess.can("nodes", "update")
    val canDelete = AdminAccess.can("nodes", "delete")
    val canReconnect = AdminAccess.can("nodes", "reconnect")
    var nodes by remember(session) { mutableStateOf(emptyList<PanelNode>()) }
    var loading by remember(session) { mutableStateOf(true) }
    var loadError by remember(session) { mutableStateOf<String?>(null) }
    var query by remember(session) { mutableStateOf("") }
    var banner by remember(session) { mutableStateOf<String?>(null) }
    var toast by remember(session) { mutableStateOf<String?>(null) }
    var deleting by remember(session) { mutableStateOf<PanelNode?>(null) }
    var syncing by remember(session) { mutableStateOf<PanelNode?>(null) }
    var loadRequest by remember(session) { mutableStateOf(0L) }
    val savedMessage = stringResource(R.string.node_saved)
    val createdMessage = stringResource(R.string.node_created)
    val deletedMessage = stringResource(R.string.node_deleted)
    val syncedMessage = stringResource(R.string.node_synced)
    val reconnectedMessage = stringResource(R.string.node_reconnected)

    suspend fun load() {
        val request = ++loadRequest
        if (!canRead) {
            nodes = emptyList(); loading = false; loadError = null
            return
        }
        try {
            val result = PanelApi.nodesStrict(session)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (request == loadRequest) {
                nodes = result; loadError = null
                PanelCache.put(PanelCache.nodesKey(session.baseUrl), result)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (request == loadRequest) {
                val kind = ApiErrorMapper.kindOf(error)
                if (kind == ApiErrorKind.FORBIDDEN || kind == ApiErrorKind.UNAUTHORIZED) nodes = emptyList()
                loadError = ApiErrorMapper.friendly(context, error)
            }
        } finally {
            if (request == loadRequest) loading = false
        }
    }

    fun loadEditor(editor: ActiveNodeEditor) {
        model.viewModelScope.launch {
            try {
                val result = PanelApi.nodeSnapshot(editor.session, requireNotNull(editor.nodeId))
                state.finishLoad(editor.requestId, result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.failLoad(editor.requestId, ApiErrorMapper.friendly(appContext, error))
            }
        }
    }

    fun startAction(target: PanelNode, kind: NodeAction) {
        val request = state.beginAction(session, target.id, kind) ?: return
        model.viewModelScope.launch {
            try {
                when (kind) {
                    NodeAction.DELETE -> PanelApi.deleteNode(session, target.id)
                    NodeAction.SYNC -> PanelApi.syncNodeUsers(session, target.id)
                    NodeAction.RECONNECT -> PanelApi.reconnectNode(session, target.id)
                }
                state.finishAction(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if (error is NodeWriteUnconfirmedException) appContext.getString(R.string.node_action_unconfirmed)
                else ApiErrorMapper.friendly(appContext, error)
                state.finishAction(request, message)
            } finally {
                // A failed or cancelled write may still have reached the panel.
                PanelCache.invalidate(PanelCache.nodesKey(session.baseUrl))
            }
        }
    }

    LaunchedEffect(session, canRead) {
        state.bind(session)
        loading = canRead
        load()
    }
    LaunchedEffect(notice) {
        notice?.let {
            toast = if (it.created) createdMessage else savedMessage
            load()
            state.consumeNotice(it.requestId)
        }
    }
    LaunchedEffect(actionNotice) {
        actionNotice?.let {
            if (it.error != null) banner = it.error
            else toast = when (it.request.action) {
                NodeAction.DELETE -> deletedMessage
                NodeAction.SYNC -> syncedMessage
                NodeAction.RECONNECT -> reconnectedMessage
            }
            load()
            state.consumeActionNotice(it.request.requestId)
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(2200); toast = null }
    }

    val filtered = remember(nodes, query) {
        val q = query.trim()
        nodes.filter { q.isEmpty() || it.name.contains(q, true) || it.address.contains(q, true) }
    }
    InfraScrollableContent(listTag = "infra_nodes_list", header = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
            sectionTabs()
            InfraSectionHeader(
                title = stringResource(R.string.nodes), subtitle = stringResource(R.string.node_management_hint),
                count = if (canRead) nodes.size else null,
                onRefresh = { if (canRead) scope.launch { loading = nodes.isEmpty(); load() } },
                onAdd = if (canCreate && action == null) ({ state.open(session, null) }) else null,
                addLabel = stringResource(R.string.node_create_title)
            )
            toast?.let { InfraBanner(it, success = true) }
            banner?.let { InfraBanner(it, success = false) { banner = null } }
            if (nodes.isNotEmpty()) loadError?.let { InfraBanner(it, success = false) { loadError = null } }
            if (nodes.isNotEmpty()) PGSearchBar(query, onQueryChange = { query = it }, placeholder = stringResource(R.string.node_search))
            if (filtered.isNotEmpty()) InfraTableHeader(NODE_COLUMNS)
        }
    }) {
        when {
            !canRead -> item(key = "node_permission") {
                MrmEmptyState(title = stringResource(R.string.node_read_permission),
                    subtitle = stringResource(R.string.node_read_permission_hint), icon = AppIcon.Lock)
            }
            loading && nodes.isEmpty() -> item(key = "node_loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp)
                }
            }
            loadError != null && nodes.isEmpty() -> item(key = "node_error") {
                MrmErrorState(onRetry = { scope.launch { loading = true; load() } }, message = loadError)
            }
            nodes.isEmpty() -> item(key = "node_empty") {
                MrmEmptyState(title = stringResource(R.string.node_empty), subtitle = stringResource(R.string.node_empty_hint), icon = AppIcon.Storage)
            }
            filtered.isEmpty() -> item(key = "node_no_results") {
                MrmEmptyState(title = stringResource(R.string.no_results), subtitle = stringResource(R.string.no_results_hint), icon = AppIcon.Search) {
                    PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { query = "" })
                }
            }
            else -> itemsIndexed(filtered, key = { _, item -> item.id }) { _, node ->
                val status = when (node.status) {
                    "connected" -> stringResource(R.string.node_status_connected)
                    "connecting" -> stringResource(R.string.node_status_connecting)
                    "disabled" -> stringResource(R.string.disabled)
                    "limited" -> stringResource(R.string.limited)
                    "error" -> stringResource(R.string.node_status_error)
                    else -> node.status.ifBlank { "—" }
                }
                val onEdit: (() -> Unit)? = if (canUpdate && action == null) ({ state.open(session, node.id)?.let(::loadEditor) }) else null
                InfraTableRow(
                    columns = NODE_COLUMNS, name = node.name, cells = listOf(node.address.ifBlank { "—" }, status),
                    dot = if (node.isConnected) DsSemantic.Success else if (node.status == "error") DsSemantic.Danger else null,
                    busy = action?.nodeId == node.id,
                    actions = buildList {
                        if (onEdit != null) add(InfraRowAction(R.string.qa_edit) { onEdit() })
                        if (canUpdate && node.isConnected && action == null) add(InfraRowAction(R.string.node_sync_users) { syncing = node })
                        if (canReconnect && node.status in setOf("error", "connecting") && action == null) {
                            add(InfraRowAction(R.string.node_reconnect) { startAction(node, NodeAction.RECONNECT) })
                        }
                        if (canDelete && action == null) add(InfraRowAction(R.string.node_delete, danger = true) { deleting = node })
                    }, onEdit = onEdit
                )
            }
        }
    }

    active?.let { editor ->
        NodeEditorDialog(editor = editor, onDraftChange = { state.update(editor.requestId, it) },
            onRetry = { state.beginLoad(editor.requestId)?.let(::loadEditor) },
            onDismiss = { state.dismiss(editor.requestId) }, onSave = save@ {
                val operation = state.beginSave(editor.requestId) ?: return@save
                model.viewModelScope.launch {
                    try {
                        val draft = requireNotNull(operation.draft)
                        if (operation.nodeId == null) PanelApi.createNode(operation.session, draft)
                        else PanelApi.modifyNode(operation.session, requireNotNull(operation.initial), draft)
                        state.finishSave(operation.requestId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val blocked = error is NodeWriteUnconfirmedException || error is NodeEditConflictException
                        val message = when (error) {
                            is NodeWriteUnconfirmedException -> appContext.getString(R.string.node_action_unconfirmed)
                            is NodeEditConflictException -> appContext.getString(R.string.node_edit_conflict)
                            is IllegalArgumentException -> appContext.getString(R.string.node_edit_rejected)
                            else -> ApiErrorMapper.friendly(appContext, error)
                        }
                        state.failSave(operation.requestId, message, blocked)
                    } finally {
                        // Invalidate even if the tab was left or the write's outcome is unknown.
                        PanelCache.invalidate(PanelCache.nodesKey(operation.session.baseUrl))
                    }
                }
            })
    }
    deleting?.let { target -> ConfirmActionDialog(
        title = stringResource(R.string.node_delete), message = stringResource(R.string.node_delete_confirm, target.name),
        confirmLabel = stringResource(R.string.node_delete), danger = true,
        onDismiss = { deleting = null }, onConfirm = { deleting = null; startAction(target, NodeAction.DELETE) }
    ) }
    syncing?.let { target -> ConfirmActionDialog(
        title = stringResource(R.string.node_sync_users), message = stringResource(R.string.node_sync_confirm, target.name),
        confirmLabel = stringResource(R.string.node_sync_users),
        onDismiss = { syncing = null }, onConfirm = { syncing = null; startAction(target, NodeAction.SYNC) }
    ) }
}
