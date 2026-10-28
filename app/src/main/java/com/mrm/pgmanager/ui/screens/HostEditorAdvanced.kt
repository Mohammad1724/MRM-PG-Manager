package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.HostSecurity
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.normalizePersianDigits

@Composable
internal fun HostEditorAdvancedSections(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    val defaultLabel = stringResource(R.string.host_editor_inherit)
    val commaHint = stringResource(R.string.host_editor_comma_list)

    HostEditorSection(stringResource(R.string.host_editor_network), "network", enabled) {
        DraftText(draft, "host", enabled, onChange, hint = commaHint, singleLine = false)
        DraftText(draft, "path", enabled, onChange)
        DraftSwitch(draft, "random_user_agent", enabled, onChange)
        HostHttpHeadersEditor(draft, enabled, onChange)
    }
    HostEditorSection(stringResource(R.string.host_security), "security", enabled) {
        HostEditorSelect(
            label = stringResource(R.string.host_security), value = draft.security, enabled = enabled,
            tag = "host_editor_security",
            choices = listOf(
                HostEditorChoice(HostSecurity.INBOUND_DEFAULT, stringResource(R.string.host_security_inbound_default)),
                HostEditorChoice(HostSecurity.NONE, stringResource(R.string.host_security_none)),
                HostEditorChoice(HostSecurity.TLS, stringResource(R.string.host_security_tls))
            ),
            onChange = { onChange(draft.copy(security = it)) }
        )
        DraftText(draft, "sni", enabled, onChange, hint = commaHint, singleLine = false)
        HostAlpnFilter(draft, enabled, onChange)
        val fingerprints = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized", "randomizednoalpn", "unsafe")
        DraftSelect(
            draft, "fingerprint", enabled, onChange,
            listOf(HostEditorChoice("", stringResource(R.string.host_editor_fingerprint_default))) + fingerprints.map { HostEditorChoice(it, it) }
        )
        if (draft.field("fingerprint") == "unsafe" || draft.field("cipher_suites").isNotEmpty()) {
            DraftText(draft, "cipher_suites", enabled, onChange)
        }
        HostEditorSelect(
            label = stringResource(R.string.host_editor_tls_verification),
            value = draft.field("allowinsecure"), enabled = enabled, tag = "host_editor_tls_verification",
            choices = listOf(
                HostEditorChoice("", stringResource(R.string.host_editor_tls_inherit)),
                HostEditorChoice("false", stringResource(R.string.host_editor_tls_verify)),
                HostEditorChoice("true", stringResource(R.string.host_editor_tls_insecure))
            ),
            onChange = { onChange(draft.withField("allowinsecure", it)) }
        )
        Text(stringResource(R.string.host_editor_allow_insecure_hint), fontSize = 12.sp, color = theme.mutedColor)
        DraftSwitch(draft, "use_sni_as_host", enabled, onChange)
        DraftText(draft, "pinned_peer_cert_sha256", enabled, onChange)
        DraftText(draft, "verify_peer_cert_by_name", enabled, onChange, hint = commaHint, singleLine = false)
        HostEditorSection(stringResource(R.string.host_editor_ech), "ech", enabled) {
            DraftText(draft, "ech.xray.config_list", enabled, onChange, singleLine = false)
            DraftSelect(
                draft, "ech.xray.query_strategy", enabled, onChange,
                listOf(HostEditorChoice("", defaultLabel)) + listOf("none", "half", "full").map { HostEditorChoice(it, it) }
            )
            DraftText(draft, "ech.mihomo.config", enabled, onChange, singleLine = false)
            DraftText(draft, "ech.mihomo.query_server_name", enabled, onChange)
            DraftText(draft, "ech.sing_box.config", enabled, onChange, singleLine = false)
            DraftText(draft, "ech.sing_box.query_server_name", enabled, onChange)
        }
    }
    HostEditorSection(stringResource(R.string.host_editor_transport), "transport", enabled) {
        Text(stringResource(R.string.host_editor_transport_hint), fontSize = 12.sp, color = theme.mutedColor)
        DraftSelect(
            draft, "transport_settings.xhttp_settings.mode", enabled, onChange,
            listOf(HostEditorChoice("", defaultLabel)) + listOf("auto", "packet-up", "stream-up", "stream-one").map { HostEditorChoice(it, it) }
        )
        DraftSwitch(draft, "transport_settings.grpc_settings.multi_mode", enabled, onChange)
        DraftText(draft, "transport_settings.grpc_settings.idle_timeout", enabled, onChange, number = true)
        DraftText(draft, "transport_settings.grpc_settings.health_check_timeout", enabled, onChange, number = true)
        DraftSwitch(draft, "transport_settings.grpc_settings.permit_without_stream", enabled, onChange)
        DraftText(draft, "transport_settings.grpc_settings.initial_windows_size", enabled, onChange, number = true)
        DraftText(draft, "transport_settings.websocket_settings.heartbeatPeriod", enabled, onChange, number = true)
    }
    HostEditorSection(stringResource(R.string.host_editor_camouflage), "camouflage", enabled) {
        Text(stringResource(R.string.host_editor_xray_fragment_hint), fontSize = 12.sp, color = theme.mutedColor)
        listOf("packets", "length", "interval").forEach { key ->
            DraftText(draft, "fragment_settings.xray.$key", enabled, onChange)
        }
        DraftSwitch(draft, "fragment_settings.sing_box.fragment", enabled, onChange)
        DraftText(
            draft, "fragment_settings.sing_box.fragment_fallback_delay", enabled, onChange,
            hint = stringResource(R.string.host_editor_delay_hint)
        )
        DraftSwitch(draft, "fragment_settings.sing_box.record_fragment", enabled, onChange)
    }
    HostEditorSection(stringResource(R.string.host_editor_mux), "mux", enabled) {
        DraftSwitch(draft, "mux_settings.xray.enabled", enabled, onChange)
        DraftText(draft, "mux_settings.xray.concurrency", enabled, onChange, number = true)
        MuxClientFields(draft, "sing_box", enabled, onChange)
        MuxClientFields(draft, "clash", enabled, onChange)
    }
    HostEditorSection(stringResource(R.string.host_editor_routing), "routing", enabled) {
        DraftText(draft, "vless_route", enabled, onChange, hint = stringResource(R.string.host_editor_vless_hint))
    }
    HostEditorSection(stringResource(R.string.host_editor_wireguard), "wireguard", enabled) {
        Text(stringResource(R.string.host_editor_wireguard_hint), fontSize = 12.sp, color = theme.mutedColor)
        DraftText(draft, "wireguard_overrides.allowed_ips", enabled, onChange, hint = commaHint, singleLine = false)
        DraftText(draft, "wireguard_overrides.mtu", enabled, onChange, number = true)
        DraftText(draft, "wireguard_overrides.keepalive_seconds", enabled, onChange, number = true)
        DraftText(draft, "wireguard_overrides.reserved", enabled, onChange)
        DraftText(draft, "wireguard_overrides.dns", enabled, onChange, hint = commaHint, singleLine = false)
    }
}

@Composable
private fun HostHttpHeadersEditor(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth().testTag("host_editor_http_headers"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.host_editor_http_headers), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = theme.inkColor)
            HostEditorSmallIconButton(
                icon = AppIcon.Add, label = stringResource(R.string.host_editor_http_header_add),
                enabled = enabled && draft.httpHeadersSupported, tag = "host_editor_http_header_add",
                onClick = { onChange(draft.withHttpHeaders(draft.httpHeaders + HostHttpHeader("", ""))) }
            )
        }
        if (!draft.httpHeadersSupported) {
            Text(stringResource(R.string.host_editor_http_headers_unsupported), fontSize = 12.sp, color = theme.mutedColor)
        } else {
            Text(stringResource(R.string.host_editor_http_headers_hint), fontSize = 12.sp, color = theme.mutedColor)
            draft.httpHeaders.forEachIndexed { index, header ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.host_editor_http_header_number, index + 1), Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    HostEditorSmallIconButton(
                        icon = AppIcon.Close, label = stringResource(R.string.host_editor_http_header_remove, index + 1),
                        enabled = enabled, tag = "host_editor_http_header_remove_$index",
                        onClick = { onChange(draft.withHttpHeaders(draft.httpHeaders.filterIndexed { i, _ -> i != index })) }
                    )
                }
                HostEditorTextField(
                    label = stringResource(R.string.host_editor_http_header_name, index + 1), value = header.name,
                    enabled = enabled, tag = "host_editor_http_header_name_$index", technical = true,
                    onChange = { value -> onChange(draft.withHttpHeaders(draft.httpHeaders.toMutableList().also { it[index] = header.copy(name = value) })) }
                )
                HostEditorTextField(
                    label = stringResource(R.string.host_editor_http_header_value, index + 1), value = header.value,
                    enabled = enabled, tag = "host_editor_http_header_value_$index", technical = true,
                    onChange = { value -> onChange(draft.withHttpHeaders(draft.httpHeaders.toMutableList().also { it[index] = header.copy(value = value) })) }
                )
            }
        }
    }
}

@Composable
private fun MuxClientFields(draft: HostDraft, client: String, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val prefix = "mux_settings.$client"
    val theme = LocalThemeState.current
    Text(
        stringResource(if (client == "clash") R.string.host_editor_clash_mux else R.string.host_editor_sing_mux),
        fontWeight = FontWeight.SemiBold, color = theme.inkColor
    )
    DraftSwitch(draft, "$prefix.enable", enabled, onChange)
    DraftSelect(draft, "$prefix.protocol", enabled, onChange, listOf("smux", "yamux", "h2mux").map { HostEditorChoice(it, it) })
    DraftText(draft, "$prefix.max_connections", enabled, onChange, number = true)
    DraftText(draft, "$prefix.min_streams", enabled, onChange, number = true)
    DraftText(draft, "$prefix.max_streams", enabled, onChange, number = true)
    DraftSwitch(draft, "$prefix.padding", enabled, onChange)
    if (client == "clash") {
        DraftSwitch(draft, "$prefix.statistic", enabled, onChange)
        DraftSwitch(draft, "$prefix.only_tcp", enabled, onChange)
    }
}

@Composable
private fun DraftText(
    draft: HostDraft,
    key: String,
    enabled: Boolean,
    onChange: (HostDraft) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    number: Boolean = false
) {
    HostEditorTextField(
        label = stringResource(hostEditorFieldLabel(key)),
        value = draft.field(key),
        onChange = { onChange(draft.withField(key, if (number) normalizePersianDigits(it) else it)) },
        enabled = enabled,
        hint = hint,
        singleLine = singleLine,
        // The panel accepts signed values for some transport and mux settings.
        keyboardType = if (number) KeyboardType.Ascii else KeyboardType.Text,
        tag = "host_editor_field_$key",
        technical = true
    )
}

@Composable
private fun DraftSwitch(
    draft: HostDraft,
    key: String,
    enabled: Boolean,
    onChange: (HostDraft) -> Unit,
    subtitle: String? = null
) {
    SettingsSwitchRow(
        title = stringResource(hostEditorFieldLabel(key)), subtitle = subtitle,
        checked = draft.field(key) == "true", enabled = enabled,
        onChange = { onChange(draft.withField(key, it.toString())) }
    )
}

@Composable
private fun DraftSelect(
    draft: HostDraft,
    key: String,
    enabled: Boolean,
    onChange: (HostDraft) -> Unit,
    choices: List<HostEditorChoice>
) {
    HostEditorSelect(
        label = stringResource(hostEditorFieldLabel(key)), value = draft.field(key),
        choices = choices, enabled = enabled, tag = "host_editor_field_$key",
        onChange = { onChange(draft.withField(key, it)) }
    )
}

@Composable
private fun HostAlpnFilter(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    val selected = hostStringValues(draft.field("alpn"))
    val values = (listOf("h3", "h2", "http/1.1") + selected).distinct()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.host_editor_alpn), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
        values.forEach { value ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = value in selected, enabled = enabled, role = Role.Checkbox) { checked ->
                        val next = if (checked) selected + value else selected.filterNot { it == value }
                        onChange(draft.withField("alpn", next.joinToString(", ")))
                    }.testTag("host_editor_alpn_$value"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = value in selected, onCheckedChange = null, enabled = enabled)
                Text(value, color = theme.inkColor, fontSize = 13.sp)
            }
        }
    }
}
