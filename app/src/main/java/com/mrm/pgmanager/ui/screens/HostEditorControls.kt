package com.mrm.pgmanager.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGChoiceDialogFrame
import com.mrm.pgmanager.ui.components.PGChoiceOption
import com.mrm.pgmanager.ui.components.PGFormInput
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSingleChoiceDialog
import com.mrm.pgmanager.ui.components.PGStartAlign
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.pgFieldSurface
import com.mrm.pgmanager.ui.theme.LocalThemeState

internal data class HostEditorChoice(val value: String, val label: String)

/** Compact visual surface with a full-sized accessible touch target. */
@Composable
internal fun HostEditorSmallIconButton(
    icon: AppIcon,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).testTag(tag)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(32.dp).testTag("${tag}_visual").clip(DsRadius.Md)
                .background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.controlBorderColor), DsRadius.Md)
                .indication(interaction, LocalIndication.current),
            contentAlignment = Alignment.Center
        ) {
            RoundedAppIcon(
                icon, tint = if (enabled) theme.inkColor else theme.mutedColor, size = 18.dp,
                modifier = Modifier.testTag("${tag}_icon")
            )
        }
    }
}

@Composable
internal fun HostEditorTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    hint: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    tag: String = "",
    technical: Boolean = false,
    help: String? = null,
    variablesHelp: Boolean = false,
    customVariables: Boolean = false
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HostEditorLabel(label, tag, help, variablesHelp, customVariables)
        PGFormInput(
            value = value,
            onValueChange = onChange,
            label = label,
            placeholder = hint,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrect = !technical,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            ),
            textStyle = TextStyle(
                fontSize = 14.sp, lineHeight = 20.sp,
                textDirection = if (technical) TextDirection.Ltr else TextDirection.Content
            ),
            modifier = if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier
        )
    }
}

@Composable
internal fun HostEditorPickerField(
    label: String,
    value: String,
    enabled: Boolean,
    tag: String,
    expanded: Boolean? = null,
    help: String? = null,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val expandedState = expanded?.let { stringResource(if (it) R.string.host_editor_expanded else R.string.host_editor_collapsed) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HostEditorLabel(label, tag, help)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).pgFieldSurface(theme)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label: $value"
                    if (expandedState != null) stateDescription = expandedState
                }
                .testTag(tag).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(value, Modifier.weight(1f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (enabled) theme.inkColor else theme.mutedColor, textAlign = PGStartAlign())
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
    PGSingleChoiceDialog(
        title = title,
        searchLabel = searchLabel,
        choices = choices.map { PGChoiceOption(it.value, it.label) },
        selected = selected,
        onDismiss = onDismiss,
        onSelect = onSelect,
        tag = "host_editor_picker"
    )
}

@Composable
internal fun HostStatusFilter(selected: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
    val theme = LocalThemeState.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    val statuses = listOf(
        "active" to stringResource(R.string.active),
        "disabled" to stringResource(R.string.disabled),
        "limited" to stringResource(R.string.limited),
        "expired" to stringResource(R.string.expired),
        "on_hold" to stringResource(R.string.on_hold)
    )
    val known = statuses.map { it.first }.toSet()
    val unknown = selected.distinct().filterNot { it in known }
        .map { it to stringResource(R.string.host_editor_unknown_status, it) }
    val options = statuses + unknown
    val summary = if (selected.isEmpty()) {
        stringResource(R.string.host_editor_no_status)
    } else {
        selected.distinct().joinToString(", ") { status -> options.firstOrNull { it.first == status }?.second ?: status }
    }
    Column(Modifier.fillMaxWidth().testTag("host_editor_status_filter"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HostEditorPickerField(
            label = stringResource(R.string.host_editor_user_status), value = summary,
            enabled = enabled, tag = "host_editor_status_dropdown", expanded = expanded
        ) { expanded = true }
        Text(stringResource(R.string.host_editor_status_hint), fontSize = 12.sp, color = theme.mutedColor)
    }
    if (expanded && enabled) {
        HostStatusDialog(
            options = options,
            selected = selected,
            onChange = onChange,
            onDismiss = { expanded = false }
        )
    }
}

/** Changes apply immediately, as in the previous multiselect menu. */
@Composable
private fun HostStatusDialog(
    options: List<Pair<String, String>>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = options.filter { it.second.contains(query.trim(), ignoreCase = true) }
    PGChoiceDialogFrame(
        title = stringResource(R.string.host_editor_user_status),
        searchLabel = stringResource(R.string.host_editor_search_statuses),
        query = query,
        onQueryChange = { query = it },
        onDismiss = onDismiss,
        tag = "host_editor_status_dialog"
    ) {
        if (filtered.isEmpty()) {
            Text(stringResource(R.string.no_results), fontSize = 13.sp, color = theme.mutedColor, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).testTag("host_editor_status_list")) {
                items(filtered, key = { it.first }) { (value, label) ->
                    val checked = value in selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = checked, role = Role.Checkbox) {
                                onChange(if (it) selected + value else selected.filterNot { old -> old == value })
                            }.testTag("host_editor_status_$value").padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = theme.inkColor, textAlign = PGStartAlign())
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PGSecondaryButton(
                text = stringResource(R.string.host_editor_status_clear),
                onClick = { onChange(emptyList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("host_editor_status_clear")
            )
            PGPrimaryButton(
                text = stringResource(R.string.host_editor_status_done),
                onClick = onDismiss,
                icon = null,
                modifier = Modifier.weight(1f).testTag("host_editor_status_done")
            )
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
    help: String? = null,
    onChange: (String) -> Unit
) {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    val unknownLabel = if (value.isEmpty()) stringResource(R.string.host_editor_inherit) else value
    val current = choices.firstOrNull { it.value == value }?.label ?: unknownLabel
    HostEditorPickerField(label, current, enabled, tag, expanded = expanded, help = help) { expanded = true }
    if (expanded && enabled) {
        val options = if (choices.any { it.value == value }) choices else listOf(HostEditorChoice(value, unknownLabel)) + choices
        HostEditorOptionsDialog(
            title = label,
            searchLabel = stringResource(R.string.search_cd),
            choices = options,
            selected = value,
            onDismiss = { expanded = false },
            onSelect = { onChange(it); expanded = false }
        )
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
    "transport_settings.xhttp_settings.no_grpc_header" -> R.string.host_editor_xhttp_no_grpc
    "transport_settings.xhttp_settings.x_padding_obfs_mode" -> R.string.host_editor_xhttp_padding_obfs
    "transport_settings.xhttp_settings.x_padding_bytes" -> R.string.host_editor_xhttp_padding_bytes
    "transport_settings.xhttp_settings.sc_max_each_post_bytes" -> R.string.host_editor_xhttp_post_bytes
    "transport_settings.xhttp_settings.sc_min_posts_interval_ms" -> R.string.host_editor_xhttp_post_interval
    "transport_settings.xhttp_settings.x_padding_key" -> R.string.host_editor_xhttp_padding_key
    "transport_settings.xhttp_settings.x_padding_header" -> R.string.host_editor_xhttp_padding_header
    "transport_settings.xhttp_settings.x_padding_placement" -> R.string.host_editor_xhttp_padding_placement
    "transport_settings.xhttp_settings.x_padding_method" -> R.string.host_editor_xhttp_padding_method
    "transport_settings.xhttp_settings.uplink_http_method" -> R.string.host_editor_xhttp_uplink_method
    "transport_settings.xhttp_settings.uplink_data_placement" -> R.string.host_editor_xhttp_uplink_placement
    "transport_settings.xhttp_settings.uplink_data_key" -> R.string.host_editor_xhttp_uplink_key
    "transport_settings.xhttp_settings.uplink_chunk_size" -> R.string.host_editor_xhttp_uplink_chunk
    "transport_settings.xhttp_settings.session_placement" -> R.string.host_editor_xhttp_session_placement
    "transport_settings.xhttp_settings.session_key" -> R.string.host_editor_xhttp_session_key
    "transport_settings.xhttp_settings.session_id_table" -> R.string.host_editor_xhttp_session_table
    "transport_settings.xhttp_settings.session_id_length" -> R.string.host_editor_xhttp_session_length
    "transport_settings.xhttp_settings.seq_placement" -> R.string.host_editor_xhttp_seq_placement
    "transport_settings.xhttp_settings.seq_key" -> R.string.host_editor_xhttp_seq_key
    "transport_settings.xhttp_settings.download_settings" -> R.string.host_editor_xhttp_download
    "transport_settings.tcp_settings.header" -> R.string.host_editor_tcp_header
    "transport_settings.tcp_settings.request.version", "transport_settings.tcp_settings.response.version" -> R.string.host_editor_tcp_version
    "transport_settings.tcp_settings.request.method" -> R.string.host_editor_tcp_method
    "transport_settings.tcp_settings.response.status" -> R.string.host_editor_tcp_status
    "transport_settings.tcp_settings.response.reason" -> R.string.host_editor_tcp_reason
    "transport_settings.kcp_settings.mtu" -> R.string.host_editor_kcp_mtu
    "transport_settings.kcp_settings.tti" -> R.string.host_editor_kcp_tti
    "transport_settings.kcp_settings.uplink_capacity" -> R.string.host_editor_kcp_uplink
    "transport_settings.kcp_settings.downlink_capacity" -> R.string.host_editor_kcp_downlink
    "transport_settings.kcp_settings.congestion" -> R.string.host_editor_kcp_congestion
    "transport_settings.kcp_settings.read_buffer_size" -> R.string.host_editor_kcp_read
    "transport_settings.kcp_settings.write_buffer_size" -> R.string.host_editor_kcp_write
    "transport_settings.masque_settings.remote_dns" -> R.string.host_editor_masque_dns
    "transport_settings.xhttp_settings.xmux.max_concurrency", "transport_settings.masque_settings.xmux.max_concurrency" -> R.string.host_editor_xmux_max_concurrency
    "transport_settings.xhttp_settings.xmux.max_connections", "transport_settings.masque_settings.xmux.max_connections" -> R.string.host_editor_xmux_max_connections
    "transport_settings.xhttp_settings.xmux.c_max_reuse_times", "transport_settings.masque_settings.xmux.c_max_reuse_times" -> R.string.host_editor_xmux_reuse
    "transport_settings.xhttp_settings.xmux.h_max_reusable_secs", "transport_settings.masque_settings.xmux.h_max_reusable_secs" -> R.string.host_editor_xmux_seconds
    "transport_settings.xhttp_settings.xmux.h_max_request_times", "transport_settings.masque_settings.xmux.h_max_request_times" -> R.string.host_editor_xmux_requests
    "transport_settings.xhttp_settings.xmux.h_keep_alive_period", "transport_settings.masque_settings.xmux.h_keep_alive_period" -> R.string.host_editor_xmux_keepalive
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
    "mux_settings.xray.xudp_concurrency" -> R.string.host_editor_xudp_concurrency
    "mux_settings.xray.xudp_proxy_udp_443" -> R.string.host_editor_xudp_proxy
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
