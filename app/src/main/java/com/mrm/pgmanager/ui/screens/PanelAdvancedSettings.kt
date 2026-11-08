package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelCleanupSettings
import com.mrm.pgmanager.data.model.PanelRuntimeSettings
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.SettingsCard
import com.mrm.pgmanager.ui.dialogs.SettingsInfoRow
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState

@Composable
internal fun AdvancedSettingsTab(
    runtime: PanelRuntimeSettings?,
    cleanup: PanelCleanupSettings?,
    canEdit: Boolean,
    saving: Boolean,
    onSaveRuntime: (PanelRuntimeSettings, PanelRuntimeSettings) -> Unit,
    onSaveCleanup: (PanelCleanupSettings, PanelCleanupSettings) -> Unit
) {
    val theme = LocalThemeState.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.SectionGap)
    ) {
        if (runtime == null && cleanup == null) {
            Text(stringResource(R.string.ps_advanced_unavailable), color = theme.mutedColor, fontSize = 12.sp)
        }
        if (runtime != null) RuntimeSettingsForm(runtime, canEdit, saving, onSaveRuntime)
        if (cleanup != null) CleanupSettingsForm(cleanup, canEdit, saving, onSaveCleanup)
        Box(Modifier.heightIn(min = DsSpacing.Xl))
    }
}

@Composable
private fun RuntimeSettingsForm(
    base: PanelRuntimeSettings,
    canEdit: Boolean,
    saving: Boolean,
    onSave: (PanelRuntimeSettings, PanelRuntimeSettings) -> Unit
) {
    var draft by remember(base) { mutableStateOf(base) }
    var fail by remember(base) { mutableStateOf(base.failThreshold.toString()) }
    var recover by remember(base) { mutableStateOf(base.recoverThreshold.toString()) }
    var timeout by remember(base) { mutableStateOf(base.probeTimeout.toString()) }
    var backoff by remember(base) { mutableStateOf(base.maxBackoff.toString()) }
    var error by remember(base) { mutableStateOf(false) }
    var pending by remember(base) { mutableStateOf<PanelRuntimeSettings?>(null) }
    val dirty = draft != base || fail != base.failThreshold.toString() ||
        recover != base.recoverThreshold.toString() || timeout != base.probeTimeout.toString() ||
        backoff != base.maxBackoff.toString()
    val theme = LocalThemeState.current

    SettingsCard(title = stringResource(R.string.ps_runtime_usage), icon = AppIcon.Gauge) {
        SettingsSwitchRow(stringResource(R.string.ps_record_node_usage), checked = draft.recordNodeUsage, enabled = canEdit,
            onChange = { draft = draft.copy(recordNodeUsage = it) })
        SettingsSwitchRow(stringResource(R.string.ps_record_node_stats), subtitle = stringResource(R.string.ps_postgres_only),
            checked = draft.recordNodeStats, enabled = canEdit, onChange = { draft = draft.copy(recordNodeStats = it) })
        SettingsSwitchRow(stringResource(R.string.ps_record_outbound), subtitle = stringResource(R.string.ps_record_outbound_hint),
            checked = draft.recordOutboundUsage, enabled = canEdit, onChange = { draft = draft.copy(recordOutboundUsage = it) })
        SettingsSwitchRow(stringResource(R.string.ps_reset_chart), subtitle = stringResource(R.string.ps_reset_chart_hint),
            checked = draft.resetUsageCleanChartData, enabled = canEdit, onChange = { draft = draft.copy(resetUsageCleanChartData = it) })
    }
    SettingsCard(title = stringResource(R.string.ps_node_health), icon = AppIcon.Tune) {
        AdvancedNumberField(stringResource(R.string.ps_health_fail), fail, canEdit) { fail = it; error = false }
        AdvancedNumberField(stringResource(R.string.ps_health_recover), recover, canEdit) { recover = it; error = false }
        AdvancedNumberField(stringResource(R.string.ps_health_timeout), timeout, canEdit) { timeout = it; error = false }
        AdvancedNumberField(stringResource(R.string.ps_health_backoff), backoff, canEdit) { backoff = it; error = false }
        Text(stringResource(R.string.ps_health_hint), fontSize = 10.sp, color = theme.mutedColor)
        SettingsSwitchRow(stringResource(R.string.ps_stop_nodes), checked = draft.stopNodesOnShutdown, enabled = canEdit,
            onChange = { draft = draft.copy(stopNodesOnShutdown = it) })
    }
    AdvancedFooter(dirty, canEdit, saving, error, R.string.ps_runtime_invalid,
        onRevert = {
            draft = base
            fail = base.failThreshold.toString(); recover = base.recoverThreshold.toString()
            timeout = base.probeTimeout.toString(); backoff = base.maxBackoff.toString(); error = false
        },
        onSave = {
            val f = fail.toIntOrNull(); val r = recover.toIntOrNull()
            val t = timeout.toIntOrNull(); val b = backoff.toIntOrNull()
            if (f == null || r == null || t == null || b == null) error = true
            else {
                val candidate = draft.copy(failThreshold = f, recoverThreshold = r, probeTimeout = t, maxBackoff = b)
                error = !candidate.isValid()
                if (!error && !base.resetUsageCleanChartData && candidate.resetUsageCleanChartData) pending = candidate
                else if (!error) onSave(base, candidate)
            }
        })
    pending?.let { candidate ->
        ConfirmActionDialog(
            title = stringResource(R.string.ps_reset_chart),
            message = stringResource(R.string.ps_reset_chart_confirm),
            confirmLabel = stringResource(R.string.ps_save),
            danger = true,
            onDismiss = { pending = null },
            onConfirm = { pending = null; onSave(base, candidate) }
        )
    }
}

@Composable
private fun CleanupSettingsForm(
    base: PanelCleanupSettings,
    canEdit: Boolean,
    saving: Boolean,
    onSave: (PanelCleanupSettings, PanelCleanupSettings) -> Unit
) {
    var draft by remember(base) { mutableStateOf(base) }
    var expired by remember(base) { mutableStateOf(base.expiredUsersRetentionDays?.toString().orEmpty()) }
    var history by remember(base) { mutableStateOf(base.usageHistoryRetentionDays?.toString().orEmpty()) }
    var nodeStats by remember(base) { mutableStateOf(base.nodeStatsRetentionDays?.toString().orEmpty()) }
    var hourly by remember(base) { mutableStateOf(base.usageRollupHourlyAfterDays?.toString().orEmpty()) }
    var halfDay by remember(base) { mutableStateOf(base.usageRollupHalfDayAfterDays?.toString().orEmpty()) }
    var daily by remember(base) { mutableStateOf(base.usageRollupDailyAfterDays?.toString().orEmpty()) }
    var error by remember(base) { mutableStateOf(false) }
    var pending by remember(base) { mutableStateOf<PanelCleanupSettings?>(null) }
    val fields = listOf(expired, history, nodeStats, hourly, halfDay, daily)
    val initial = listOf(base.expiredUsersRetentionDays, base.usageHistoryRetentionDays, base.nodeStatsRetentionDays,
        base.usageRollupHourlyAfterDays, base.usageRollupHalfDayAfterDays, base.usageRollupDailyAfterDays)
        .map { it?.toString().orEmpty() }
    val dirty = draft != base || fields != initial
    val theme = LocalThemeState.current

    SettingsCard(title = stringResource(R.string.ps_cleanup_title), icon = AppIcon.Storage) {
        Text(stringResource(R.string.ps_cleanup_hint), fontSize = 11.sp, color = theme.mutedColor)
        AdvancedNumberField(stringResource(R.string.ps_cleanup_expired), expired, canEdit) { expired = it; error = false }
        SettingsSwitchRow(stringResource(R.string.ps_cleanup_limited), checked = draft.includeLimitedUsers, enabled = canEdit,
            onChange = { draft = draft.copy(includeLimitedUsers = it) })
        AdvancedNumberField(stringResource(R.string.ps_cleanup_history), history, canEdit) { history = it; error = false }
        AdvancedNumberField(stringResource(R.string.ps_cleanup_nodes), nodeStats, canEdit) { nodeStats = it; error = false }
        Text(stringResource(R.string.ps_rollup_hint), fontSize = 11.sp, color = theme.mutedColor)
        AdvancedNumberField(stringResource(R.string.ps_rollup_hourly), hourly, canEdit) { hourly = it; error = false }
        AdvancedNumberField(stringResource(R.string.ps_rollup_half_day), halfDay, canEdit) { halfDay = it; error = false }
        AdvancedNumberField(stringResource(R.string.ps_rollup_daily), daily, canEdit) { daily = it; error = false }
    }
    AdvancedFooter(dirty, canEdit, saving, error, R.string.ps_cleanup_invalid,
        onRevert = {
            draft = base; expired = initial[0]; history = initial[1]; nodeStats = initial[2]
            hourly = initial[3]; halfDay = initial[4]; daily = initial[5]; error = false
        },
        onSave = {
            if (fields.any { it.isNotBlank() && it.toIntOrNull() == null }) error = true
            else {
                val candidate = draft.copy(
                    expiredUsersRetentionDays = expired.toIntOrNull(), usageHistoryRetentionDays = history.toIntOrNull(),
                    nodeStatsRetentionDays = nodeStats.toIntOrNull(), usageRollupHourlyAfterDays = hourly.toIntOrNull(),
                    usageRollupHalfDayAfterDays = halfDay.toIntOrNull(), usageRollupDailyAfterDays = daily.toIntOrNull()
                )
                error = !candidate.isValid()
                if (!error) pending = candidate
            }
        })
    pending?.let { candidate ->
        ConfirmActionDialog(
            title = stringResource(R.string.ps_cleanup_confirm_title),
            message = stringResource(R.string.ps_cleanup_confirm),
            confirmLabel = stringResource(R.string.ps_save),
            danger = true,
            onDismiss = { pending = null },
            onConfirm = { pending = null; onSave(base, candidate) }
        )
    }
}

@Composable
private fun AdvancedNumberField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    if (!enabled) SettingsInfoRow(label, value.ifBlank { "—" })
    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
        InfraFieldLabel(label)
        CompactGlassField(value = value, onValueChange = { onChange(it.trim()) }, placeholder = label, keyboardType = KeyboardType.Number)
    }
}

@Composable
private fun AdvancedFooter(
    dirty: Boolean,
    canEdit: Boolean,
    saving: Boolean,
    error: Boolean,
    errorRes: Int,
    onRevert: () -> Unit,
    onSave: () -> Unit
) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
        if (error) InfraBanner(text = stringResource(errorRes), success = false)
        if (!canEdit) Text(stringResource(R.string.ps_readonly), fontSize = 10.sp, color = theme.mutedColor)
        if (canEdit) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
            PGSecondaryButton(text = stringResource(R.string.ps_revert), onClick = onRevert,
                enabled = dirty && !saving, modifier = Modifier.weight(1f))
            PGPrimaryButton(text = stringResource(R.string.ps_save), onClick = onSave,
                enabled = dirty && !saving, loading = saving, modifier = Modifier.weight(1f))
        }
    }
}
