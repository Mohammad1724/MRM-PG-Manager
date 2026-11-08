package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.theme.LocalThemeState

@Composable
internal fun HostTcpSection(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val prefix = "transport_settings.tcp_settings."
    val versions = listOf("1.0", "1.1", "2.0", "3.0").map { HostEditorChoice(it, "HTTP/$it") }
    HostEditorSection(stringResource(R.string.host_editor_tcp), "transport_tcp", enabled) {
        DraftSelect(draft, prefix + "header", enabled, onChange, listOf(
            HostEditorChoice("", stringResource(R.string.host_editor_inherit)),
            HostEditorChoice("none", stringResource(R.string.host_security_none)),
            HostEditorChoice("http", "HTTP")
        ))
        if (draft.field(prefix + "header") == "http") {
            HostEditorSection(stringResource(R.string.host_editor_tcp_request), "transport_tcp_request", enabled) {
                DraftSelect(draft, prefix + "request.version", enabled, onChange, versions)
                DraftSelect(draft, prefix + "request.method", enabled, onChange,
                    listOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH", "TRACE", "CONNECT")
                        .map { HostEditorChoice(it, it) })
                HostTcpHeadersEditor(draft, "request", enabled, onChange)
            }
            HostEditorSection(stringResource(R.string.host_editor_tcp_response), "transport_tcp_response", enabled) {
                DraftSelect(draft, prefix + "response.version", enabled, onChange, versions)
                DraftText(draft, prefix + "response.status", enabled, onChange, number = true)
                DraftSelect(draft, prefix + "response.reason", enabled, onChange, hostTcpReasons.map { HostEditorChoice(it, it) })
                HostTcpHeadersEditor(draft, "response", enabled, onChange)
            }
        }
    }
}

@Composable
private fun HostTcpHeadersEditor(draft: HostDraft, direction: String, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    val state = if (direction == "request") draft.tcpRequestHeaders else draft.tcpResponseHeaders
    val prefix = "host_editor_tcp_${direction}_headers"
    Column(Modifier.fillMaxWidth().testTag(prefix), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HostEditorLabel(stringResource(R.string.host_editor_tcp_headers), prefix, customVariables = true)
            }
            HostEditorSmallIconButton(
                icon = AppIcon.Add, label = stringResource(R.string.host_editor_http_header_add),
                enabled = enabled && state.supported, tag = "${prefix}_add",
                onClick = { onChange(draft.withTcpHeaders(direction, state.headers + HostTcpHeader("", listOf("")))) }
            )
        }
        if (!state.supported) {
            Text(stringResource(R.string.host_editor_tcp_headers_unsupported), fontSize = 12.sp, color = theme.mutedColor)
        } else {
            Text(stringResource(R.string.host_editor_tcp_headers_hint), fontSize = 12.sp, color = theme.mutedColor)
            state.headers.forEachIndexed { index, header ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.host_editor_http_header_number, index + 1), Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    HostEditorSmallIconButton(
                        icon = AppIcon.Close, label = stringResource(R.string.host_editor_http_header_remove, index + 1),
                        enabled = enabled, tag = "${prefix}_remove_$index",
                        onClick = { onChange(draft.withTcpHeaders(direction, state.headers.filterIndexed { i, _ -> i != index })) }
                    )
                }
                HostEditorTextField(
                    label = stringResource(R.string.host_editor_http_header_name, index + 1), value = header.name,
                    enabled = enabled, tag = "${prefix}_name_$index", technical = true,
                    onChange = { value -> onChange(draft.withTcpHeaders(direction, state.headers.toMutableList().also { it[index] = header.copy(name = value) })) }
                )
                HostEditorTextField(
                    label = stringResource(R.string.host_editor_http_header_value, index + 1), value = header.values.joinToString("\n"),
                    enabled = enabled, tag = "${prefix}_value_$index", technical = true, singleLine = false,
                    onChange = { value -> onChange(draft.withTcpHeaders(direction, state.headers.toMutableList().also { it[index] = header.copy(values = value.split('\n')) })) }
                )
            }
        }
    }
}
