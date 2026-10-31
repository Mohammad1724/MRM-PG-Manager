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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.coreTagCanRoundTrip
import com.mrm.pgmanager.data.model.CoreType
import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGChoiceDialogFrame
import com.mrm.pgmanager.ui.components.PGChoiceOption
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.PGSingleChoiceDialog
import com.mrm.pgmanager.ui.components.PGStartAlign
import com.mrm.pgmanager.ui.designsystem.AppModalBottomSheet
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.WcagContrast
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/** The title and actions stay fixed while the metadata form scrolls independently. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoreEditorDialog(
    initial: PanelCore,
    draft: CoreEditorDraft,
    saving: Boolean,
    error: String?,
    availableTags: List<String>?,
    tagsLoading: Boolean = false,
    onDraftChange: (CoreEditorDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val theme = LocalThemeState.current
    val title = stringResource(R.string.core_edit_title)
    val name = draft.name
    val type = draft.type
    val exclude = draft.exclude
    val fallbacks = draft.fallbacks
    var attempted by rememberSaveable(initial.id) { mutableStateOf(false) }
    var discardConfirmation by rememberSaveable(initial.id) { mutableStateOf(false) }
    var picker by rememberSaveable(initial.id) { mutableStateOf<String?>(null) }
    val dirty = draft.isDirty(initial)
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
    fun requestDismiss() {
        if (currentSaving || currentDirty) {
            if (currentDirty && !currentSaving) discardConfirmation = true
            if (!sheetState.isVisible) scope.launch { sheetState.show() }
        } else {
            currentOnDismiss()
        }
    }

    val validationMessage = if (!attempted) null else when {
        draft.nameError() == CoreNameError.EMPTY -> stringResource(R.string.err_core_name)
        draft.nameError() == CoreNameError.TOO_LONG -> stringResource(R.string.err_core_name_length)
        draft.tagsTooLong() -> stringResource(R.string.err_core_tags_length)
        draft.hasUnstorableTags() -> stringResource(R.string.err_core_tags_storage)
        draft.wireGuardHasTags() -> stringResource(R.string.err_core_wg_tags)
        else -> null
    }
    val displayedError = error ?: validationMessage
    val allTags = remember(availableTags, initial, exclude, fallbacks) {
        (availableTags.orEmpty() + initial.excludeInboundTags + initial.fallbacksInboundTags + exclude + fallbacks).distinct()
    }
    // The panel ignores null on update and v5.5.1 implements Xray and WireGuard.
    val typeOptions = ((if (initial.type == null) listOf("") else emptyList()) +
        listOf(CoreType.XRAY, CoreType.WG) + listOfNotNull(initial.type)).distinct()
    val wireGuard = type == CoreType.WG

    AppModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = theme.dialogBgColor,
        contentColor = theme.inkColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp)
                    .background(theme.mutedColor.copy(alpha = 0.35f), RoundedCornerShape(50)))
            }
        }
    ) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().heightIn(max = 800.dp).fillMaxHeight(0.94f)
                    .navigationBarsPadding().imePadding().paneTitle(title).testTag("core_editor_window")
            ) {
                var footerHeightPx by remember { mutableIntStateOf(0) }
                val footerHeight = with(LocalDensity.current) { footerHeightPx.toDp() }
                val maxErrorHeight = (maxHeight - 96.dp).coerceIn(0.dp, 80.dp)
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("core_editor_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = theme.inkColor)
                        HostEditorSmallIconButton(
                            icon = AppIcon.Close, label = stringResource(R.string.cd_close), enabled = !saving,
                            tag = "core_editor_close", onClick = ::requestDismiss
                        )
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .testTag("core_editor_scroll").padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HostEditorTextField(
                            label = stringResource(R.string.core_name), value = name,
                            onChange = { onDraftChange(draft.copy(name = it)) }, enabled = !saving,
                            hint = stringResource(R.string.core_name_hint), tag = "core_editor_name"
                        )
                        HostEditorPickerField(
                            label = stringResource(R.string.core_type), value = coreTypeLabel(type), enabled = !saving,
                            tag = "core_editor_type", expanded = picker == "type", onClick = { picker = "type" }
                        )
                        Text(stringResource(R.string.core_type_conversion_hint), fontSize = 12.sp, color = theme.mutedColor)
                        HostEditorPickerField(
                            label = stringResource(R.string.core_exclude_tags),
                            value = stringResource(R.string.selected_count, exclude.size),
                            enabled = !saving && !tagsLoading && (availableTags != null || exclude.isNotEmpty()) &&
                                (!wireGuard || exclude.isNotEmpty()),
                            tag = "core_editor_exclude", expanded = picker == "exclude", onClick = { picker = "exclude" }
                        )
                        HostEditorPickerField(
                            label = stringResource(R.string.core_fallback_tags),
                            value = stringResource(R.string.selected_count, fallbacks.size),
                            enabled = !saving && !tagsLoading && (availableTags != null || fallbacks.isNotEmpty()) &&
                                (!wireGuard || fallbacks.isNotEmpty()),
                            tag = "core_editor_fallbacks", expanded = picker == "fallbacks", onClick = { picker = "fallbacks" }
                        )
                        if (draft.hasUnstorableTags()) {
                            Text(stringResource(R.string.err_core_tags_storage), fontSize = 12.sp, color = theme.mutedColor)
                        }
                        if (wireGuard) {
                            Text(stringResource(R.string.core_wg_tags_hint), fontSize = 12.sp, color = theme.mutedColor)
                        }
                        if (tagsLoading) {
                            Text(stringResource(R.string.core_tags_loading), fontSize = 12.sp, color = theme.mutedColor,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("core_editor_tags_loading"))
                        } else if (availableTags == null) {
                            Text(stringResource(R.string.core_tags_unavailable), fontSize = 12.sp, color = theme.mutedColor,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("core_editor_tags_error"))
                        }
                        SettingsSwitchRow(
                            title = stringResource(R.string.core_restart_nodes),
                            subtitle = stringResource(R.string.core_restart_nodes_hint),
                            checked = draft.restartNodes, enabled = !saving,
                            onChange = { onDraftChange(draft.copy(restartNodes = it)) }
                        )
                        Text(stringResource(R.string.core_editor_scope_hint), fontSize = 12.sp, color = theme.mutedColor,
                            modifier = Modifier.testTag("core_editor_scope_hint"))
                    }
                    Spacer(Modifier.height(footerHeight))
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(theme.dialogBgColor)
                        .onSizeChanged { footerHeightPx = it.height }.testTag("core_editor_footer")
                ) {
                    displayedError?.let {
                        Text(
                            it, modifier = Modifier.fillMaxWidth().heightIn(max = maxErrorHeight)
                                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }.testTag("core_editor_error"),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(WcagContrast.forTextOn(DsSemantic.Danger.toArgb(), theme.dialogBgColor.toArgb()))
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PGSecondaryButton(
                            stringResource(R.string.cancel), onClick = ::requestDismiss,
                            modifier = Modifier.weight(1f).testTag("core_editor_cancel"), enabled = !saving
                        )
                        PGPrimaryButton(
                            stringResource(R.string.save_changes),
                            onClick = {
                                attempted = true
                                if (draft.nameError() == null && !draft.tagsTooLong() &&
                                    !draft.hasUnstorableTags() && !draft.wireGuardHasTags()) onSave()
                            },
                            modifier = Modifier.weight(1f).testTag("core_editor_save"), icon = null,
                            enabled = !saving && dirty, loading = saving
                        )
                    }
                }
            }
        }
    }

    if (discardConfirmation) {
        ConfirmActionDialog(
            title = stringResource(R.string.host_editor_discard_title),
            message = stringResource(R.string.core_editor_discard_message),
            confirmLabel = stringResource(R.string.host_editor_discard),
            danger = true,
            onDismiss = { discardConfirmation = false },
            onConfirm = { discardConfirmation = false; onDismiss() }
        )
    }
    if (picker == "type") {
        PGSingleChoiceDialog(
            title = stringResource(R.string.core_type), searchLabel = stringResource(R.string.core_search_type),
            choices = typeOptions.map { PGChoiceOption(it, coreTypeLabel(it)) }, selected = type,
            onDismiss = { picker = null }, onSelect = { onDraftChange(draft.copy(type = it)); picker = null },
            tag = "core_editor_type_picker"
        )
    }
    if (picker == "exclude" || picker == "fallbacks") {
        val editingExclude = picker == "exclude"
        CoreTagSelectorDialog(
            title = stringResource(if (editingExclude) R.string.core_exclude_tags else R.string.core_fallback_tags),
            tags = if (wireGuard || availableTags == null) (if (editingExclude) exclude else fallbacks).toList() else allTags,
            selected = if (editingExclude) exclude else fallbacks,
            onDismiss = { picker = null },
            onToggle = { tag ->
                if (editingExclude) onDraftChange(draft.copy(exclude = if (tag in exclude) exclude - tag else exclude + tag))
                else onDraftChange(draft.copy(fallbacks = if (tag in fallbacks) fallbacks - tag else fallbacks + tag))
            }
        )
    }
}

/** Each checkbox has a single accessible 48dp target; selections apply immediately. */
@Composable
internal fun CoreTagSelectorDialog(
    title: String,
    tags: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit
) {
    val theme = LocalThemeState.current
    var query by rememberSaveable(title) { mutableStateOf("") }
    val filtered = remember(tags, query) { tags.distinct().filter { it.contains(query.trim(), ignoreCase = true) } }
    PGChoiceDialogFrame(
        title = title, searchLabel = stringResource(R.string.core_search_tags), query = query,
        onQueryChange = { query = it }, onDismiss = onDismiss, tag = "core_editor_tag_picker",
        headerAction = {
            HostEditorSmallIconButton(AppIcon.Close, stringResource(R.string.cd_close), true,
                "core_editor_tag_picker_close", onDismiss)
        }
    ) {
        if (filtered.isEmpty()) {
            Text(stringResource(if (tags.isEmpty()) R.string.core_no_tags else R.string.no_results),
                modifier = Modifier.padding(8.dp), fontSize = 13.sp, color = theme.mutedColor)
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).testTag("core_editor_tag_picker_list")) {
                items(filtered, key = { it }) { tag ->
                    val checked = tag in selected
                    val storable = coreTagCanRoundTrip(tag)
                    val canToggle = storable || checked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = checked, enabled = canToggle, role = Role.Checkbox, onValueChange = { onToggle(tag) })
                            .testTag("core_editor_tag_option_$tag").padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            val label = when {
                                tag.isEmpty() -> stringResource(R.string.core_empty_tag)
                                tag.isBlank() -> stringResource(R.string.core_whitespace_tag)
                                else -> tag
                            }
                            Text(label, fontSize = 14.sp, color = theme.inkColor, textAlign = PGStartAlign())
                            if (!storable) {
                                Text(stringResource(R.string.core_tag_unstorable), fontSize = 12.sp, color = theme.mutedColor)
                            }
                        }
                        Checkbox(checked = checked, onCheckedChange = null, enabled = canToggle)
                    }
                }
            }
        }
        PGSecondaryButton(stringResource(R.string.action_done), onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().testTag("core_editor_tag_picker_done"))
    }
}
