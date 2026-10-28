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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.HostXrayTemplate
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.HostSecurity
import com.mrm.pgmanager.data.model.HostValidation
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.AppModalBottomSheet
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.WcagContrast
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal sealed interface HostOptions<out T> {
    data object Loading : HostOptions<Nothing>
    data class Ready<T>(val values: List<T>) : HostOptions<T>
    data class Failed(val message: String) : HostOptions<Nothing>
}

/** The two independent queries keep their failures visible without clearing saved selections. */
@Composable
internal fun HostEditorDialog(
    initial: PanelHost,
    session: Session,
    draft: HostDraft,
    saving: Boolean,
    error: String?,
    onDraftChange: (HostDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var inboundAttempt by remember(session) { mutableIntStateOf(0) }
    var templateAttempt by remember(session) { mutableIntStateOf(0) }
    var inbounds by remember(session) { mutableStateOf<HostOptions<String>>(HostOptions.Loading) }
    var templates by remember(session) { mutableStateOf<HostOptions<HostXrayTemplate>>(HostOptions.Loading) }

    LaunchedEffect(session, inboundAttempt) {
        inbounds = HostOptions.Loading
        try {
            inbounds = HostOptions.Ready(PanelApi.inboundTagsStrict(session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            inbounds = HostOptions.Failed(ApiErrorMapper.friendly(context, e))
        }
    }
    LaunchedEffect(session, templateAttempt) {
        templates = HostOptions.Loading
        try {
            templates = HostOptions.Ready(PanelApi.hostXrayTemplates(session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            templates = HostOptions.Failed(ApiErrorMapper.friendly(context, e))
        }
    }
    HostEditorContent(
        initial = initial,
        draft = draft,
        saving = saving,
        error = error,
        onDraftChange = onDraftChange,
        inbounds = inbounds,
        templates = templates,
        onRetryInbounds = { inboundAttempt++ },
        onRetryTemplates = { templateAttempt++ },
        onDismiss = onDismiss,
        onSave = onSave
    )
}

/** The body scrolls independently while the title and save controls stay reachable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HostEditorContent(
    initial: PanelHost,
    draft: HostDraft,
    saving: Boolean,
    error: String?,
    onDraftChange: (HostDraft) -> Unit,
    inbounds: HostOptions<String>,
    templates: HostOptions<HostXrayTemplate>,
    onRetryInbounds: () -> Unit,
    onRetryTemplates: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val theme = LocalThemeState.current
    val isCreate = initial.id == 0
    val title = stringResource(if (isCreate) R.string.create_host else R.string.edit_host)
    val baseline = remember(initial) { HostDraft.from(initial) }
    var attempted by rememberSaveable(initial.id) { mutableStateOf(false) }
    var discardConfirmation by rememberSaveable(initial.id) { mutableStateOf(false) }
    var showInboundPicker by rememberSaveable(initial.id) { mutableStateOf(false) }
    var showTemplatePicker by rememberSaveable(initial.id) { mutableStateOf(false) }
    val dirty = draft.isDirtyComparedTo(baseline)
    val currentDirty by rememberUpdatedState(dirty)
    val currentSaving by rememberUpdatedState(saving)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) { target ->
        when {
            target != SheetValue.Hidden -> true
            currentSaving -> false
            currentDirty -> { discardConfirmation = true; false }
            else -> true
        }
    }

    fun update(value: HostDraft) {
        onDraftChange(value)
    }
    fun requestDismiss() {
        if (currentSaving || currentDirty) {
            if (currentDirty && !currentSaving) discardConfirmation = true
            // The draft or saving state can change during a hide animation.
            if (!sheetState.isVisible) scope.launch { sheetState.show() }
        } else {
            currentOnDismiss()
        }
    }

    val validation = if (attempted) draft.validation(initial) else null
    val validationMessage = when (validation?.code) {
        HostValidation.ERR_REMARK -> stringResource(R.string.err_host_remark)
        HostValidation.ERR_ADDRESS -> stringResource(R.string.err_host_address)
        HostValidation.ERR_PORT -> stringResource(R.string.err_host_port)
        "inbound" -> stringResource(R.string.host_editor_inbound_required)
        "http_headers_unsupported" -> stringResource(R.string.host_editor_http_headers_unsupported)
        "http_header_name" -> stringResource(R.string.host_editor_http_header_invalid_name)
        "http_header_duplicate" -> stringResource(R.string.host_editor_http_header_duplicate)
        "http_header_value" -> stringResource(R.string.host_editor_http_header_invalid_value)
        "advanced" -> stringResource(
            R.string.host_editor_invalid_field,
            stringResource(hostEditorFieldLabel(validation.field.orEmpty()))
        )
        else -> null
    }
    val displayedError = error ?: validationMessage

    AppModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = theme.dialogBgColor,
        contentColor = theme.inkColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 24.dp).testTag("host_editor_drag_handle"),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(theme.mutedColor.copy(alpha = 0.35f), RoundedCornerShape(50)))
            }
        }
    ) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().heightIn(max = 800.dp).fillMaxHeight(0.94f)
                    .navigationBarsPadding().imePadding().paneTitle(title).testTag("host_editor_window")
            ) {
                // Measure actions separately so a short IME viewport cannot push them below the sheet.
                var footerHeightPx by remember { mutableIntStateOf(0) }
                val footerHeight = with(LocalDensity.current) { footerHeightPx.toDp() }
                val maxErrorHeight = (maxHeight - 96.dp).coerceIn(0.dp, 80.dp)
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("host_editor_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            title, modifier = Modifier.weight(1f).semantics { heading() },
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                        )
                        HostEditorSmallIconButton(
                            icon = AppIcon.Close, label = stringResource(R.string.cd_close), enabled = !saving,
                            tag = "host_editor_close", onClick = ::requestDismiss
                        )
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .testTag("host_editor_scroll").padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.host_editor_basics),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.inkColor,
                            modifier = Modifier.semantics { heading() }
                        )
                        HostEditorTextField(
                            label = stringResource(R.string.host_remark),
                            hint = stringResource(R.string.host_remark_hint),
                            value = draft.remark, onChange = { update(draft.copy(remark = it)) },
                            enabled = !saving, tag = "host_editor_remark"
                        )
                        HostEditorTextField(
                            label = stringResource(R.string.host_address),
                            hint = stringResource(R.string.host_editor_comma_list),
                            value = draft.addressesText, onChange = { update(draft.copy(addressesText = it)) },
                            enabled = !saving, singleLine = false, tag = "host_editor_addresses", technical = true
                        )
                        HostEditorTextField(
                            label = stringResource(R.string.host_port),
                            hint = stringResource(R.string.host_port_hint),
                            value = draft.portText,
                            onChange = { update(draft.copy(portText = com.mrm.pgmanager.utils.normalizePersianDigits(it))) },
                            enabled = !saving, keyboardType = KeyboardType.Number, tag = "host_editor_port", technical = true
                        )
                        HostEditorTextField(
                            label = stringResource(R.string.col_priority),
                            hint = stringResource(R.string.host_editor_priority_default),
                            value = draft.field("priority"),
                            onChange = { update(draft.withField("priority", com.mrm.pgmanager.utils.normalizePersianDigits(it))) },
                            enabled = !saving, keyboardType = KeyboardType.Ascii, tag = "host_editor_priority", technical = true
                        )
                        Text(stringResource(R.string.host_editor_priority_hint), fontSize = 12.sp, color = theme.mutedColor)

                        val inboundLabel = draft.inboundTag ?: stringResource(R.string.host_editor_inbound_none)
                        HostEditorPickerField(
                            label = stringResource(R.string.host_inbound), value = inboundLabel,
                            enabled = !saving && inbounds is HostOptions.Ready,
                            tag = "host_editor_inbound", onClick = { showInboundPicker = true }
                        )
                        HostOptionsFeedback(inbounds, onRetryInbounds, enabled = !saving) {
                            if (it.isEmpty()) Text(stringResource(R.string.host_no_inbounds), fontSize = 12.sp, color = theme.mutedColor)
                        }
                        if (inbounds is HostOptions.Failed || (inbounds is HostOptions.Ready && inbounds.values.isEmpty())) {
                            HostEditorTextField(
                                label = stringResource(R.string.host_editor_inbound_manual),
                                value = draft.inboundTag.orEmpty(),
                                onChange = { update(draft.copy(inboundTag = it.ifBlank { null })) },
                                enabled = !saving, tag = "host_editor_inbound_manual", technical = true
                            )
                        }

                        val templateLabel = when {
                            draft.templateId != null -> {
                                val id = draft.templateId!!
                                val name = (templates as? HostOptions.Ready)?.values?.firstOrNull { it.id == id }?.name
                                name ?: stringResource(R.string.host_editor_template_unknown, id)
                            }
                            !draft.templateTouched && HostDraft.hasUnrecognizedTemplate(initial.raw) ->
                                stringResource(R.string.host_editor_template_unrecognized)
                            else -> stringResource(R.string.host_security_inbound_default)
                        }
                        HostEditorPickerField(
                            label = stringResource(R.string.host_editor_xray_template), value = templateLabel,
                            enabled = !saving && templates is HostOptions.Ready,
                            tag = "host_editor_template", onClick = { showTemplatePicker = true }
                        )
                        HostOptionsFeedback(templates, onRetryTemplates, enabled = !saving) {
                            if (it.isEmpty()) Text(stringResource(R.string.host_editor_templates_empty), fontSize = 12.sp, color = theme.mutedColor)
                        }

                        HostStatusFilter(
                            selected = draft.status,
                            enabled = !saving,
                            onChange = { update(draft.copy(status = it)) }
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.host_editor_enabled),
                            subtitle = stringResource(R.string.host_editor_enabled_hint),
                            checked = !draft.disabled, enabled = !saving,
                            onChange = { update(draft.copy(disabled = !it)) }
                        )
                        HostEditorAdvancedSections(draft = draft, enabled = !saving, onChange = ::update)
                        Text(
                            stringResource(R.string.host_editor_partial_advanced),
                            fontSize = 12.sp, color = theme.mutedColor
                        )
                    }
                    Spacer(Modifier.height(footerHeight))
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(theme.dialogBgColor)
                        .onSizeChanged { footerHeightPx = it.height }.testTag("host_editor_footer")
                ) {
                    displayedError?.let {
                        Text(
                            it, modifier = Modifier.fillMaxWidth().heightIn(max = maxErrorHeight)
                                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }.testTag("host_editor_error"),
                            fontSize = 12.sp,
                            color = Color(WcagContrast.forTextOn(DsSemantic.Danger.toArgb(), theme.dialogBgColor.toArgb())),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("host_editor_actions"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PGSecondaryButton(
                            stringResource(R.string.cancel), onClick = ::requestDismiss,
                            modifier = Modifier.weight(1f).testTag("host_editor_cancel"), enabled = !saving
                        )
                        PGPrimaryButton(
                            stringResource(R.string.host_editor_save),
                            onClick = {
                                attempted = true
                                if (draft.validation(initial) == null) onSave()
                            },
                            modifier = Modifier.weight(1f).testTag("host_editor_save"),
                            icon = null, enabled = !saving && (isCreate || dirty), loading = saving
                        )
                    }
                }
            }
        }
    }

    if (discardConfirmation) {
        ConfirmActionDialog(
            title = stringResource(R.string.host_editor_discard_title),
            message = stringResource(R.string.host_editor_discard_message),
            confirmLabel = stringResource(R.string.host_editor_discard),
            danger = true,
            onDismiss = { discardConfirmation = false },
            onConfirm = { discardConfirmation = false; if (!currentSaving) currentOnDismiss() }
        )
    }
    if (showInboundPicker && inbounds is HostOptions.Ready) {
        val none = stringResource(R.string.host_editor_inbound_none)
        val tags = (inbounds.values + listOfNotNull(draft.inboundTag)).distinct()
        HostEditorOptionsDialog(
            title = stringResource(R.string.host_inbound),
            searchLabel = stringResource(R.string.host_editor_search_inbounds),
            choices = listOf(HostEditorChoice("", none)) + tags.map { HostEditorChoice(it, it) },
            selected = draft.inboundTag.orEmpty(),
            onDismiss = { showInboundPicker = false },
            onSelect = { update(draft.copy(inboundTag = it.ifBlank { null })); showInboundPicker = false }
        )
    }
    if (showTemplatePicker && templates is HostOptions.Ready) {
        val current = draft.templateId
        val choices = templates.values.map { HostEditorChoice(it.id.toString(), it.name) }.toMutableList()
        if (current != null && templates.values.none { it.id == current }) {
            choices.add(0, HostEditorChoice(current.toString(), stringResource(R.string.host_editor_template_unknown, current)))
        }
        HostEditorOptionsDialog(
            title = stringResource(R.string.host_editor_xray_template),
            searchLabel = stringResource(R.string.host_editor_search_templates),
            choices = listOf(HostEditorChoice("", stringResource(R.string.host_security_inbound_default))) + choices,
            selected = current?.toString().orEmpty(),
            onDismiss = { showTemplatePicker = false },
            onSelect = { update(draft.copy(templateId = it.toIntOrNull(), templateTouched = true)); showTemplatePicker = false }
        )
    }
}
