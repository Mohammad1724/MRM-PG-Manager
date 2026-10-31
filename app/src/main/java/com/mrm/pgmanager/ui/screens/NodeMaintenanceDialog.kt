package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.PGFormInput
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.ui.theme.LocalThemeState

/** Confirmation and validated parameters for the panel's node maintenance endpoints. */
@Composable
internal fun NodeMaintenanceDialog(
    nodeName: String,
    action: NodeAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    require(action == NodeAction.UPDATE_CORE || action == NodeAction.UPDATE_GEOFILES)
    val theme = LocalThemeState.current
    val isCore = action == NodeAction.UPDATE_CORE
    val title = stringResource(if (isCore) R.string.node_update_core else R.string.node_update_geofiles)
    var value by remember(nodeName, action) { mutableStateOf(if (isCore) "latest" else "iran") }
    var showRegions by remember { mutableStateOf(false) }
    val regions = listOf(
        HostEditorChoice("iran", stringResource(R.string.node_region_iran)),
        HostEditorChoice("china", stringResource(R.string.node_region_china)),
        HostEditorChoice("russia", stringResource(R.string.node_region_russia))
    )
    val valid = if (isCore) Regex("^(latest|v?\\d+\\.\\d+\\.\\d+)$").matches(value.trim())
        else regions.any { it.value == value }

    AppDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl)
                .heightIn(max = 600.dp).verticalScroll(rememberScrollState())
                .padding(DsSpacing.Dialog).paneTitle(title).testTag("node_maintenance_dialog"),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
            Text(stringResource(R.string.node_maintenance_warning, nodeName), fontSize = 13.sp, color = theme.mutedColor)
            if (isCore) {
                val label = stringResource(R.string.node_core_version)
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                PGFormInput(value = value, onValueChange = { value = it }, label = label,
                    placeholder = "latest", modifier = Modifier.testTag("node_core_version"))
                Text(stringResource(R.string.node_core_version_hint), fontSize = 12.sp, color = theme.mutedColor)
            } else {
                val label = stringResource(R.string.node_geofiles_region)
                HostEditorPickerField(label = label, value = regions.first { it.value == value }.label,
                    enabled = true, tag = "node_geofiles_region", expanded = showRegions) { showRegions = true }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Lg)) {
                PGSecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PGPrimaryButton(stringResource(R.string.confirm), onClick = { onConfirm(value.trim()) },
                    modifier = Modifier.weight(1f), enabled = valid)
            }
        }
    }
    if (showRegions) HostEditorOptionsDialog(
        title = stringResource(R.string.node_geofiles_region), searchLabel = stringResource(R.string.search),
        choices = regions, selected = value, onDismiss = { showRegions = false },
        onSelect = { value = it; showRegions = false }
    )
}
