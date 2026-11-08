package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.ApiKeyQuota
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.theme.LocalThemeState

@Composable
internal fun ApiKeyQuotaDialog(
    key: PanelApiKey,
    onDismiss: () -> Unit,
    onSave: (ApiKeyQuota, (String) -> Unit) -> Unit
) {
    val theme = LocalThemeState.current
    var limit by remember(key) { mutableStateOf(key.maxRequests?.toString().orEmpty()) }
    var deleteOnLimit by remember(key) { mutableStateOf(key.deleteOnLimit == true) }
    var busy by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var pending by remember(key) { mutableStateOf<ApiKeyQuota?>(null) }
    val invalid = stringResource(R.string.ak_quota_invalid)
    val dirty = limit != key.maxRequests?.toString().orEmpty() || deleteOnLimit != key.deleteOnLimit

    AppDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .verticalScroll(rememberScrollState()).imePadding().padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
        ) {
            Text(stringResource(R.string.ak_edit_quota), fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text(key.name, fontSize = 12.sp, color = theme.inkColor)
            key.requestCount?.let {
                Text(stringResource(R.string.ak_request_usage, it.toString()), fontSize = 11.sp, color = theme.mutedColor)
            }
            InfraFieldLabel(stringResource(R.string.ak_max_requests))
            CompactGlassField(value = limit, onValueChange = { limit = it.trim(); error = null },
                placeholder = stringResource(R.string.ak_quota_unlimited), keyboardType = KeyboardType.Number)
            Text(stringResource(R.string.ak_quota_hint), fontSize = 10.sp, color = theme.mutedColor)
            SettingsSwitchRow(title = stringResource(R.string.ak_delete_on_limit),
                subtitle = stringResource(R.string.ak_delete_on_limit_hint), checked = deleteOnLimit,
                enabled = !busy, onChange = { deleteOnLimit = it })
            error?.let { InfraBanner(text = it, success = false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                PGSecondaryButton(text = stringResource(R.string.cd_close), onClick = onDismiss,
                    enabled = !busy, modifier = Modifier.weight(1f))
                PGPrimaryButton(text = stringResource(R.string.ps_save), modifier = Modifier.weight(1f),
                    enabled = dirty && !busy, loading = busy, onClick = {
                        val parsed = limit.toLongOrNull()
                        val quota = ApiKeyQuota(parsed, deleteOnLimit)
                        if ((limit.isNotBlank() && parsed == null) || !quota.isValid(key.maxRequests)) error = invalid
                        else pending = quota
                    })
            }
        }
    }
    pending?.let { quota ->
        ConfirmActionDialog(
            title = stringResource(R.string.ak_edit_quota),
            message = stringResource(R.string.ak_quota_confirm),
            confirmLabel = stringResource(R.string.ps_save),
            danger = true,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                busy = true
                onSave(quota) { message -> busy = false; error = message }
            }
        )
    }
}
