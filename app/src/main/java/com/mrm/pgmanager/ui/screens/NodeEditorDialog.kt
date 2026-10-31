package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.NodeDraft
import com.mrm.pgmanager.data.api.NodeDraftError
import com.mrm.pgmanager.data.api.NodeProxyEdit
import com.mrm.pgmanager.data.api.NodeStatusEdit
import com.mrm.pgmanager.data.api.validationError
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGFormInput
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.AppModalBottomSheet
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.designsystem.SecureContent
import com.mrm.pgmanager.ui.designsystem.WcagContrast
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NodeEditorDialog(
    editor: ActiveNodeEditor,
    onDraftChange: (NodeDraft) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val theme = LocalThemeState.current
    val title = stringResource(if (editor.nodeId == null) R.string.node_create_title else R.string.node_edit_title)
    val draft = editor.draft
    val dirty = draft != null && draft != (editor.initial?.let(NodeDraft::from) ?: NodeDraft())
    var discard by remember(editor.requestId) { mutableStateOf(false) }
    var attempted by remember(editor.requestId) { mutableStateOf(false) }
    var picker by remember(editor.requestId) { mutableStateOf<String?>(null) }
    val currentDirty by rememberUpdatedState(dirty)
    val currentSaving by rememberUpdatedState(editor.saving)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true) { target ->
        when {
            target != SheetValue.Hidden -> true
            currentSaving -> false
            currentDirty -> { discard = true; false }
            else -> true
        }
    }
    fun requestDismiss() {
        if (currentSaving) return
        if (currentDirty) {
            discard = true
            if (!sheet.isVisible) scope.launch { sheet.show() }
        } else currentDismiss()
    }

    val validation = if (attempted && draft != null) draft.validationError(editor.nodeId == null, editor.initial) else null
    val fieldLabel = validation?.let { stringResource(nodeValidationLabel(it)) }
    val validationMessage = fieldLabel?.let { stringResource(R.string.node_invalid_field_fmt, it) }
    val displayedError = editor.error ?: validationMessage

    AppModalBottomSheet(
        onDismissRequest = ::requestDismiss, sheetState = sheet,
        containerColor = theme.dialogBgColor, contentColor = theme.inkColor,
        tonalElevation = 0.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp)
                    .background(theme.mutedColor.copy(alpha = 0.35f), RoundedCornerShape(50)))
            }
        }
    ) {
        SecureContent {
            LiquidGlassTheme(themeState = theme, drawBackground = false) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().heightIn(max = 800.dp).fillMaxHeight(0.94f)
                        .navigationBarsPadding().imePadding().paneTitle(title).testTag("node_editor_window")
                ) {
                    var footerHeightPx by remember { mutableIntStateOf(0) }
                    val footerHeight = with(LocalDensity.current) { footerHeightPx.toDp() }
                    val maxErrorHeight = (maxHeight - 96.dp).coerceIn(0.dp, 80.dp)
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("node_editor_header"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, color = theme.inkColor)
                            HostEditorSmallIconButton(AppIcon.Close, stringResource(R.string.cd_close), !editor.saving,
                                "node_editor_close", ::requestDismiss)
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .testTag("node_editor_scroll").padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when {
                                editor.loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                                    MrmBusyIndicator(color = theme.accentPrimary, size = 32.dp, strokeWidth = 2.dp)
                                }
                                draft == null -> PGSecondaryButton(stringResource(R.string.retry), onClick = onRetry)
                                else -> NodeEditorFields(
                                    draft = draft, initial = editor.initial, enabled = !editor.saving && !editor.blocked,
                                    picker = picker, onPickerChange = { picker = it }, onDraftChange = onDraftChange
                                )
                            }
                        }
                        Spacer(Modifier.height(footerHeight))
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(theme.dialogBgColor)
                            .onSizeChanged { footerHeightPx = it.height }.testTag("node_editor_footer")
                    ) {
                        displayedError?.let {
                            Text(it, modifier = Modifier.fillMaxWidth().heightIn(max = maxErrorHeight)
                                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }.testTag("node_editor_error"),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(WcagContrast.forTextOn(DsSemantic.Danger.toArgb(), theme.dialogBgColor.toArgb())))
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PGSecondaryButton(stringResource(R.string.cancel), onClick = ::requestDismiss,
                                modifier = Modifier.weight(1f).testTag("node_editor_cancel"), enabled = !editor.saving)
                            PGPrimaryButton(stringResource(R.string.save_changes), onClick = {
                                attempted = true
                                if (draft?.validationError(editor.nodeId == null, editor.initial) == null && draft != null) onSave()
                            }, modifier = Modifier.weight(1f).testTag("node_editor_save"), icon = null,
                                enabled = draft != null && !editor.loading && !editor.saving && !editor.blocked && (dirty || editor.nodeId == null),
                                loading = editor.saving)
                        }
                    }
                }
            }
        }
    }
    if (discard) ConfirmActionDialog(
        title = stringResource(R.string.host_editor_discard_title),
        message = stringResource(R.string.node_discard_message),
        confirmLabel = stringResource(R.string.host_editor_discard), danger = true,
        onDismiss = { discard = false }, onConfirm = { discard = false; onDismiss() }
    )
}

@Composable
internal fun NodeEditorFields(
    draft: NodeDraft,
    initial: com.mrm.pgmanager.data.api.NodeSnapshot?,
    enabled: Boolean,
    picker: String?,
    onPickerChange: (String?) -> Unit,
    onDraftChange: (NodeDraft) -> Unit
) {
    val theme = LocalThemeState.current
    val existing = initial != null
    Text(stringResource(if (existing) R.string.node_secret_keep_hint else R.string.node_secret_create_hint),
        fontSize = 12.sp, color = theme.mutedColor)
    NodeField(R.string.node_name, draft.name, { onDraftChange(draft.copy(name = it)) }, enabled, "node_editor_name", technical = false)
    NodeField(R.string.node_address, draft.address, { onDraftChange(draft.copy(address = it)) }, enabled, "node_editor_address", technical = true)
    NodeField(R.string.node_port, draft.port, { onDraftChange(draft.copy(port = it)) }, enabled, "node_editor_port", KeyboardType.Number)
    NodeField(R.string.node_api_port, draft.apiPort, { onDraftChange(draft.copy(apiPort = it)) }, enabled, "node_editor_api_port", KeyboardType.Number)
    NodeField(R.string.node_core_id, draft.coreId, { onDraftChange(draft.copy(coreId = it)) }, enabled, "node_editor_core", KeyboardType.Number)
    Text(stringResource(R.string.node_core_id_hint), fontSize = 12.sp, color = theme.mutedColor)
    NodeField(R.string.node_coefficient, draft.coefficient, { onDraftChange(draft.copy(coefficient = it)) }, enabled, "node_editor_coefficient", KeyboardType.Decimal)
    NodeEditorChoice(R.string.node_connection, "connection", draft.connectionType,
        (listOf("grpc" to "gRPC", "rest" to "REST") + listOf(draft.connectionType to draft.connectionType)).distinctBy { it.first },
        enabled, picker, onPickerChange) { onDraftChange(draft.copy(connectionType = it)) }
    NodeField(R.string.node_certificate, draft.certificate, { onDraftChange(draft.copy(certificate = it)) }, enabled,
        "node_editor_certificate", singleLine = false, technical = true)
    Text(stringResource(R.string.node_certificate_hint), fontSize = 12.sp, color = theme.mutedColor)
    NodeField(R.string.node_api_key, draft.apiKey, { onDraftChange(draft.copy(apiKey = it)) }, enabled,
        "node_editor_api_key", KeyboardType.Password, secret = true)
    NodeField(R.string.node_keep_alive, draft.keepAlive, { onDraftChange(draft.copy(keepAlive = it)) }, enabled, "node_editor_keep_alive", KeyboardType.Number)
    NodeField(R.string.node_data_limit, draft.dataLimit, { onDraftChange(draft.copy(dataLimit = it)) }, enabled, "node_editor_data_limit", KeyboardType.Number)
    Text(stringResource(R.string.node_data_limit_hint), fontSize = 12.sp, color = theme.mutedColor)
    val resetChoices = listOf(
        "no_reset" to stringResource(R.string.node_reset_none), "day" to stringResource(R.string.node_reset_day),
        "week" to stringResource(R.string.node_reset_week), "month" to stringResource(R.string.node_reset_month),
        "year" to stringResource(R.string.node_reset_year), draft.resetStrategy to draft.resetStrategy
    ).distinctBy { it.first }
    NodeEditorChoice(R.string.node_reset_strategy, "reset", draft.resetStrategy, resetChoices, enabled, picker, onPickerChange) {
        onDraftChange(draft.copy(resetStrategy = it))
    }
    NodeField(R.string.node_reset_time, draft.resetTime, { onDraftChange(draft.copy(resetTime = it)) }, enabled,
        "node_editor_reset_time", KeyboardType.Text, technical = true)
    Text(stringResource(R.string.node_reset_time_hint), fontSize = 12.sp, color = theme.mutedColor)
    NodeField(R.string.node_default_timeout, draft.defaultTimeout, { onDraftChange(draft.copy(defaultTimeout = it)) }, enabled,
        "node_editor_default_timeout", KeyboardType.Number)
    NodeField(R.string.node_internal_timeout, draft.internalTimeout, { onDraftChange(draft.copy(internalTimeout = it)) }, enabled,
        "node_editor_internal_timeout", KeyboardType.Number)
    if (existing) {
        val proxyChoices = listOf(
            NodeProxyEdit.KEEP.name to stringResource(R.string.node_keep_existing),
            NodeProxyEdit.REPLACE.name to stringResource(R.string.node_replace),
            NodeProxyEdit.CLEAR.name to stringResource(R.string.node_remove_value)
        )
        NodeEditorChoice(R.string.node_proxy_action, "proxy", draft.proxyEdit.name, proxyChoices, enabled, picker, onPickerChange) {
            onDraftChange(draft.copy(proxyEdit = NodeProxyEdit.valueOf(it), proxy = ""))
        }
    }
    if (!existing || draft.proxyEdit == NodeProxyEdit.REPLACE) {
        NodeField(R.string.node_proxy, draft.proxy, { onDraftChange(draft.copy(proxy = it)) }, enabled,
            "node_editor_proxy", KeyboardType.Password, secret = true)
        Text(stringResource(R.string.node_proxy_hint), fontSize = 12.sp, color = theme.mutedColor)
    }
    if (existing) {
        val statusChoices = listOf(
            NodeStatusEdit.KEEP.name to stringResource(R.string.node_keep_existing),
            NodeStatusEdit.ENABLE.name to stringResource(R.string.node_enable),
            NodeStatusEdit.DISABLE.name to stringResource(R.string.node_disable)
        )
        NodeEditorChoice(R.string.node_status_action, "status", draft.statusEdit.name, statusChoices, enabled, picker, onPickerChange) {
            onDraftChange(draft.copy(statusEdit = NodeStatusEdit.valueOf(it)))
        }
    }
}

@Composable
private fun NodeField(
    labelRes: Int,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    technical: Boolean = true,
    secret: Boolean = false
) {
    val label = stringResource(labelRes)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HostEditorLabel(label, tag)
        PGFormInput(value = value, onValueChange = onChange, label = label, placeholder = "", enabled = enabled,
            singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboardType,
                autoCorrect = false, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp,
                textDirection = if (technical) TextDirection.Ltr else TextDirection.Content),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun NodeEditorChoice(
    labelRes: Int,
    key: String,
    selected: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    picker: String?,
    onPickerChange: (String?) -> Unit,
    onSelect: (String) -> Unit
) {
    val label = stringResource(labelRes)
    HostEditorPickerField(label = label, value = options.firstOrNull { it.first == selected }?.second ?: selected,
        enabled = enabled, tag = "node_editor_$key", expanded = picker == key) { onPickerChange(key) }
    if (picker == key && enabled) HostEditorOptionsDialog(
        title = label, searchLabel = stringResource(R.string.search),
        choices = options.map { HostEditorChoice(it.first, it.second) }, selected = selected,
        onDismiss = { onPickerChange(null) }, onSelect = { onSelect(it); onPickerChange(null) }
    )
}

private fun nodeValidationLabel(error: NodeDraftError): Int = when (error) {
    NodeDraftError.NAME -> R.string.node_name
    NodeDraftError.ADDRESS -> R.string.node_address
    NodeDraftError.PORT -> R.string.node_port
    NodeDraftError.API_PORT -> R.string.node_api_port
    NodeDraftError.COEFFICIENT -> R.string.node_coefficient
    NodeDraftError.CONNECTION -> R.string.node_connection
    NodeDraftError.CERTIFICATE -> R.string.node_certificate
    NodeDraftError.KEEP_ALIVE -> R.string.node_keep_alive
    NodeDraftError.CORE -> R.string.node_core_id
    NodeDraftError.API_KEY -> R.string.node_api_key
    NodeDraftError.DATA_LIMIT -> R.string.node_data_limit
    NodeDraftError.RESET_STRATEGY -> R.string.node_reset_strategy
    NodeDraftError.RESET_TIME -> R.string.node_reset_time
    NodeDraftError.DEFAULT_TIMEOUT -> R.string.node_default_timeout
    NodeDraftError.INTERNAL_TIMEOUT -> R.string.node_internal_timeout
    NodeDraftError.PROXY -> R.string.node_proxy
}
