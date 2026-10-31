package com.mrm.pgmanager.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.theme.LocalThemeState

@Composable
internal fun HostTransportSections(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    val xhttp = "transport_settings.xhttp_settings."
    val rangeHint = stringResource(R.string.host_editor_range_hint)
    HostEditorSection(stringResource(R.string.host_editor_xhttp), "transport_xhttp", enabled) {
        DraftSelect(draft, xhttp + "mode", enabled, onChange, defaultChoices(listOf("auto", "packet-up", "stream-up", "stream-one")))
        DraftNullableBoolean(draft, xhttp + "no_grpc_header", enabled, onChange)
        DraftNullableBoolean(draft, xhttp + "x_padding_obfs_mode", enabled, onChange)
        listOf("x_padding_bytes", "sc_max_each_post_bytes", "sc_min_posts_interval_ms").forEach {
            DraftText(draft, xhttp + it, enabled, onChange, hint = rangeHint, normalizeDigits = true)
        }
        HostEditorSection(stringResource(R.string.host_editor_xhttp_padding_advanced), "transport_xhttp_padding", enabled) {
            listOf("x_padding_key", "x_padding_header").forEach { DraftText(draft, xhttp + it, enabled, onChange) }
            DraftSelect(draft, xhttp + "x_padding_placement", enabled, onChange, defaultChoices(listOf("queryInHeader", "query", "header", "cookie")))
            DraftSelect(draft, xhttp + "x_padding_method", enabled, onChange, defaultChoices(listOf("repeat-x", "tokenish")))
        }
        HostEditorSection(stringResource(R.string.host_editor_xhttp_uplink), "transport_xhttp_uplink", enabled) {
            DraftText(draft, xhttp + "uplink_http_method", enabled, onChange)
            DraftSelect(draft, xhttp + "uplink_data_placement", enabled, onChange, defaultChoices(listOf("body", "header", "cookie")))
            DraftText(draft, xhttp + "uplink_data_key", enabled, onChange)
            DraftText(draft, xhttp + "uplink_chunk_size", enabled, onChange, hint = rangeHint, normalizeDigits = true)
        }
        HostEditorSection(stringResource(R.string.host_editor_xhttp_session), "transport_xhttp_session", enabled) {
            val positions = defaultChoices(listOf("path", "query", "header", "cookie"))
            DraftSelect(draft, xhttp + "session_placement", enabled, onChange, positions)
            DraftText(draft, xhttp + "session_key", enabled, onChange)
            DraftText(draft, xhttp + "session_id_table", enabled, onChange)
            DraftText(draft, xhttp + "session_id_length", enabled, onChange, hint = rangeHint, normalizeDigits = true)
            DraftSelect(draft, xhttp + "seq_placement", enabled, onChange, positions)
            DraftText(draft, xhttp + "seq_key", enabled, onChange)
        }
        HostEditorSection(stringResource(R.string.host_editor_xmux), "transport_xhttp_xmux", enabled) {
            HostXmuxFields(draft, xhttp + "xmux.", enabled, onChange, includeMaxRequests = true)
        }
        DraftText(draft, xhttp + "download_settings", enabled, onChange, number = true,
            help = stringResource(R.string.host_editor_xhttp_download_help))
    }
    HostEditorSection(stringResource(R.string.host_editor_grpc), "transport_grpc", enabled) {
        DraftSwitch(draft, "transport_settings.grpc_settings.multi_mode", enabled, onChange)
        DraftText(draft, "transport_settings.grpc_settings.idle_timeout", enabled, onChange, number = true)
        DraftText(draft, "transport_settings.grpc_settings.health_check_timeout", enabled, onChange, number = true)
        DraftSwitch(draft, "transport_settings.grpc_settings.permit_without_stream", enabled, onChange)
        DraftText(draft, "transport_settings.grpc_settings.initial_windows_size", enabled, onChange, number = true)
    }
    HostEditorSection(stringResource(R.string.host_editor_kcp), "transport_kcp", enabled) {
        val prefix = "transport_settings.kcp_settings."
        listOf("mtu", "tti", "uplink_capacity", "downlink_capacity").forEach {
            DraftText(draft, prefix + it, enabled, onChange, number = true)
        }
        DraftNullableBoolean(draft, prefix + "congestion", enabled, onChange)
        listOf("read_buffer_size", "write_buffer_size").forEach {
            DraftText(draft, prefix + it, enabled, onChange, number = true)
        }
    }
    HostTcpSection(draft, enabled, onChange)
    HostEditorSection(stringResource(R.string.host_editor_websocket), "transport_websocket", enabled) {
        DraftText(draft, "transport_settings.websocket_settings.heartbeatPeriod", enabled, onChange, number = true)
    }
    HostEditorSection(stringResource(R.string.host_editor_masque), "transport_masque", enabled) {
        val prefix = "transport_settings.masque_settings."
        Text(stringResource(R.string.host_editor_masque_hint), fontSize = 12.sp, color = theme.mutedColor)
        DraftText(draft, prefix + "remote_dns", enabled, onChange,
            hint = stringResource(R.string.host_editor_comma_list), singleLine = false)
        HostEditorSection(stringResource(R.string.host_editor_xmux), "transport_masque_xmux", enabled) {
            HostXmuxFields(draft, prefix + "xmux.", enabled, onChange, includeMaxRequests = false)
        }
    }
}

@Composable
private fun HostXmuxFields(draft: HostDraft, prefix: String, enabled: Boolean, onChange: (HostDraft) -> Unit, includeMaxRequests: Boolean) {
    val range = stringResource(R.string.host_editor_range_hint)
    val keys = listOf("max_concurrency", "max_connections", "c_max_reuse_times", "h_max_reusable_secs") +
        if (includeMaxRequests) listOf("h_max_request_times") else emptyList()
    keys.forEach { DraftText(draft, prefix + it, enabled, onChange, hint = range, normalizeDigits = true) }
    DraftText(draft, prefix + "h_keep_alive_period", enabled, onChange, number = true)
}

@Composable
private fun DraftNullableBoolean(draft: HostDraft, key: String, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    DraftSelect(draft, key, enabled, onChange, listOf(
        HostEditorChoice("", stringResource(R.string.host_editor_inherit)),
        HostEditorChoice("true", stringResource(R.string.host_editor_yes)),
        HostEditorChoice("false", stringResource(R.string.host_editor_no))
    ))
}

@Composable
private fun defaultChoices(values: List<String>): List<HostEditorChoice> =
    listOf(HostEditorChoice("", stringResource(R.string.host_editor_inherit))) + values.map { HostEditorChoice(it, it) }
