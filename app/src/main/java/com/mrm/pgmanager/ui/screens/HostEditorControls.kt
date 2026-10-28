package com.mrm.pgmanager.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.ui.designsystem.AppDropdownMenu
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.LocalThemeState

internal data class HostEditorChoice(val value: String, val label: String)

@Composable
internal fun HostEditorTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    hint: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    tag: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (hint.isNotEmpty()) Text(hint) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 5,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        ),
        modifier = Modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
    )
}

@Composable
internal fun HostEditorPickerField(
    label: String,
    value: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(DsRadius.Md)
                .background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.controlBorderColor), DsRadius.Md)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
                .testTag(tag).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(value, Modifier.weight(1f), fontSize = 14.sp, color = if (enabled) theme.inkColor else theme.mutedColor)
            RoundedAppIcon(AppIcon.ChevronDown, tint = theme.mutedColor, size = 18.dp)
        }
    }
}

@Composable
internal fun <T> HostOptionsFeedback(
    state: HostOptions<T>,
    onRetry: () -> Unit,
    enabled: Boolean,
    ready: @Composable (List<T>) -> Unit
) {
    val theme = LocalThemeState.current
    when (state) {
        HostOptions.Loading -> Text(stringResource(R.string.host_editor_loading_options), fontSize = 12.sp, color = theme.mutedColor)
        is HostOptions.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.host_editor_options_failed, state.message), fontSize = 12.sp, color = theme.mutedColor)
            Text(stringResource(R.string.host_editor_options_retained), fontSize = 12.sp, color = theme.mutedColor)
            PGSecondaryButton(stringResource(R.string.retry), onClick = onRetry, enabled = enabled)
        }
        is HostOptions.Ready -> ready(state.values)
    }
}

@Composable
internal fun HostEditorOptionsDialog(
    title: String,
    searchLabel: String,
    choices: List<HostEditorChoice>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val theme = LocalThemeState.current
    var query by rememberSaveable(title) { mutableStateOf("") }
    val filtered = choices.filter { it.label.contains(query.trim(), ignoreCase = true) }
    AppDialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Column(
                Modifier.fillMaxWidth().imePadding().heightIn(max = 500.dp).clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                    .padding(16.dp).paneTitle(title).testTag("host_editor_picker"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = theme.inkColor)
                PGSearchBar(query = query, onQueryChange = { query = it }, placeholder = searchLabel)
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.no_results), fontSize = 13.sp, color = theme.mutedColor, modifier = Modifier.padding(8.dp))
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false).testTag("host_editor_picker_list")) {
                        items(filtered, key = { it.value }) { choice ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(selected = choice.value == selected, role = Role.RadioButton) { onSelect(choice.value) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(choice.label, Modifier.weight(1f), fontSize = 14.sp, color = theme.inkColor)
                                if (choice.value == selected) RoundedAppIcon(AppIcon.Check, tint = theme.accentPrimary, size = 18.dp)
                            }
                        }
                    }
                }
                PGSecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun HostStatusFilter(selected: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
    val theme = LocalThemeState.current
    val statuses = listOf(
        "active" to stringResource(R.string.active),
        "disabled" to stringResource(R.string.disabled),
        "limited" to stringResource(R.string.limited),
        "expired" to stringResource(R.string.expired),
        "on_hold" to stringResource(R.string.on_hold)
    )
    val known = statuses.map { it.first }.toSet()
    val unknown = selected.filterNot { it in known }.map { it to stringResource(R.string.host_editor_unknown_status, it) }
    Column(Modifier.fillMaxWidth().testTag("host_editor_status_filter")) {
        Text(stringResource(R.string.host_editor_user_status), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
        Text(stringResource(R.string.host_editor_status_hint), fontSize = 12.sp, color = theme.mutedColor)
        if (selected.isEmpty()) Text(stringResource(R.string.host_editor_no_status), fontSize = 12.sp, color = theme.mutedColor)
        (statuses + unknown).forEach { (value, label) ->
            val checked = value in selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) {
                        onChange(if (it) selected + value else selected.filterNot { old -> old == value })
                    }.testTag("host_editor_status_$value").padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Text(label, Modifier.weight(1f), fontSize = 13.sp, color = if (enabled) theme.inkColor else theme.mutedColor)
            }
        }
    }
}

@Composable
internal fun HostEditorSection(
    title: String,
    tag: String,
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalThemeState.current
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.host_editor_expanded else R.string.host_editor_collapsed)
    Column(
        Modifier.fillMaxWidth().clip(DsRadius.Lg)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(enabled = enabled, role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state }.testTag("host_editor_section_$tag")
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
            RoundedAppIcon(if (expanded) AppIcon.ChevronUp else AppIcon.ChevronDown, tint = theme.mutedColor, size = 18.dp)
        }
        if (expanded) Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content
        )
    }
}

@Composable
internal fun HostEditorSelect(
    label: String,
    value: String,
    choices: List<HostEditorChoice>,
    enabled: Boolean,
    tag: String,
    onChange: (String) -> Unit
) {
    val theme = LocalThemeState.current
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == value }?.label ?: value
    Box {
        HostEditorPickerField(label, current, enabled, tag) { expanded = true }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            if (choices.none { it.value == value }) {
                DropdownMenuItem(text = { Text(value, color = theme.inkColor) }, onClick = { expanded = false })
            }
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label, color = theme.inkColor) },
                    onClick = { onChange(choice.value); expanded = false }
                )
            }
        }
    }
}

@StringRes
internal fun hostEditorFieldLabel(key: String): Int = when (key) {
    "priority" -> R.string.col_priority
    "host" -> R.string.host_editor_network_host
    "path" -> R.string.host_editor_path
    "random_user_agent" -> R.string.host_editor_random_agent
    "sni" -> R.string.host_editor_sni
    "alpn" -> R.string.host_editor_alpn
    "fingerprint" -> R.string.host_editor_fingerprint
    "cipher_suites" -> R.string.host_editor_cipher_suites
    "allowinsecure" -> R.string.host_editor_allow_insecure
    "use_sni_as_host" -> R.string.host_editor_sni_as_host
    "pinned_peer_cert_sha256" -> R.string.host_editor_pinned_cert
    "verify_peer_cert_by_name" -> R.string.host_editor_verify_names
    "vless_route" -> R.string.host_editor_vless_route
    "ech.xray.config_list" -> R.string.host_editor_ech_xray_config
    "ech.xray.query_strategy" -> R.string.host_editor_ech_strategy
    "ech.mihomo.config" -> R.string.host_editor_ech_mihomo_config
    "ech.mihomo.query_server_name" -> R.string.host_editor_ech_mihomo_name
    "ech.sing_box.config" -> R.string.host_editor_ech_singbox_config
    "ech.sing_box.query_server_name" -> R.string.host_editor_ech_singbox_name
    "transport_settings.xhttp_settings.mode" -> R.string.host_editor_xhttp_mode
    "transport_settings.grpc_settings.multi_mode" -> R.string.host_editor_grpc_multi
    "transport_settings.grpc_settings.idle_timeout" -> R.string.host_editor_grpc_idle
    "transport_settings.grpc_settings.health_check_timeout" -> R.string.host_editor_grpc_health
    "transport_settings.grpc_settings.permit_without_stream" -> R.string.host_editor_grpc_permit
    "transport_settings.grpc_settings.initial_windows_size" -> R.string.host_editor_grpc_window
    "transport_settings.websocket_settings.heartbeatPeriod" -> R.string.host_editor_ws_heartbeat
    "fragment_settings.xray.packets" -> R.string.host_editor_xray_fragment_packets
    "fragment_settings.xray.length" -> R.string.host_editor_xray_fragment_length
    "fragment_settings.xray.interval" -> R.string.host_editor_xray_fragment_interval
    "fragment_settings.sing_box.fragment" -> R.string.host_editor_sing_fragment
    "fragment_settings.sing_box.fragment_fallback_delay" -> R.string.host_editor_sing_delay
    "fragment_settings.sing_box.record_fragment" -> R.string.host_editor_record_fragment
    "mux_settings.xray.enabled" -> R.string.host_editor_xray_mux
    "mux_settings.xray.concurrency" -> R.string.host_editor_mux_concurrency
    "mux_settings.sing_box.enable" -> R.string.host_editor_sing_mux
    "mux_settings.clash.enable" -> R.string.host_editor_clash_mux
    "mux_settings.sing_box.protocol", "mux_settings.clash.protocol" -> R.string.host_editor_mux_protocol
    "mux_settings.sing_box.max_connections", "mux_settings.clash.max_connections" -> R.string.host_editor_mux_connections
    "mux_settings.sing_box.min_streams", "mux_settings.clash.min_streams" -> R.string.host_editor_mux_min_streams
    "mux_settings.sing_box.max_streams", "mux_settings.clash.max_streams" -> R.string.host_editor_mux_max_streams
    "mux_settings.sing_box.padding", "mux_settings.clash.padding" -> R.string.host_editor_mux_padding
    "mux_settings.clash.statistic" -> R.string.host_editor_mux_statistics
    "mux_settings.clash.only_tcp" -> R.string.host_editor_mux_tcp
    "wireguard_overrides.allowed_ips" -> R.string.host_editor_wg_allowed
    "wireguard_overrides.mtu" -> R.string.host_editor_wg_mtu
    "wireguard_overrides.keepalive_seconds" -> R.string.host_editor_wg_keepalive
    "wireguard_overrides.reserved" -> R.string.host_editor_wg_reserved
    "wireguard_overrides.dns" -> R.string.host_editor_wg_dns
    else -> R.string.host_editor_settings
}
