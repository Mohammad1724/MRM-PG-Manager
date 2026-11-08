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
import com.mrm.pgmanager.utils.normalizePersianDigits

@Composable
internal fun HostNoiseEditor(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth().testTag("host_editor_noise"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HostEditorLabel(
                    label = stringResource(R.string.host_editor_noise), tag = "host_editor_noise",
                    help = stringResource(R.string.host_editor_help_noise)
                )
            }
            HostEditorSmallIconButton(
                icon = AppIcon.Add, label = stringResource(R.string.host_editor_noise_add),
                enabled = enabled && draft.noiseSupported, tag = "host_editor_noise_add",
                onClick = { onChange(draft.withNoise(draft.noise + HostNoiseItem())) }
            )
        }
        when {
            !draft.noiseSupported -> Text(stringResource(R.string.host_editor_noise_unsupported), fontSize = 12.sp, color = theme.mutedColor)
            draft.noise.isEmpty() -> Text(stringResource(R.string.host_editor_noise_empty), fontSize = 12.sp, color = theme.mutedColor)
            else -> draft.noise.forEachIndexed { index, item ->
                fun replace(updated: HostNoiseItem) {
                    onChange(draft.withNoise(draft.noise.toMutableList().also { it[index] = updated }))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.host_editor_noise_number, index + 1), Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.inkColor)
                    HostEditorSmallIconButton(
                        icon = AppIcon.Copy, label = stringResource(R.string.host_editor_noise_duplicate, index + 1),
                        enabled = enabled, tag = "host_editor_noise_duplicate_$index",
                        onClick = {
                            val copy = draft.noise.toMutableList().also { it.add(index + 1, item.copy()) }
                            onChange(draft.withNoise(copy))
                        }
                    )
                    HostEditorSmallIconButton(
                        icon = AppIcon.Delete, label = stringResource(R.string.host_editor_noise_remove, index + 1),
                        enabled = enabled, tag = "host_editor_noise_remove_$index",
                        onClick = { onChange(draft.withNoise(draft.noise.filterIndexed { i, _ -> i != index })) }
                    )
                }
                HostEditorSelect(
                    label = stringResource(R.string.host_editor_noise_type), value = item.type,
                    choices = listOf("rand", "str", "base64", "hex").map { HostEditorChoice(it, it) },
                    enabled = enabled, tag = "host_editor_noise_type_$index", onChange = { replace(item.copy(type = it)) }
                )
                HostEditorSelect(
                    label = stringResource(R.string.host_editor_noise_apply), value = item.applyTo,
                    choices = listOf("ip", "ipv4", "ipv6").map { HostEditorChoice(it, it) },
                    enabled = enabled, tag = "host_editor_noise_apply_$index", onChange = { replace(item.copy(applyTo = it)) }
                )
                HostEditorSelect(
                    label = stringResource(R.string.host_editor_noise_packet_format), value = if (item.packetIsArray) "array" else "text",
                    choices = listOf(
                        HostEditorChoice("text", stringResource(R.string.host_editor_noise_packet_string)),
                        HostEditorChoice("array", stringResource(R.string.host_editor_noise_packet_array))
                    ),
                    enabled = enabled, tag = "host_editor_noise_packet_format_$index",
                    onChange = { replace(item.copy(packetIsArray = it == "array")) }
                )
                HostEditorTextField(
                    label = stringResource(R.string.host_editor_noise_packet), value = item.packet,
                    hint = if (item.packetIsArray) stringResource(R.string.host_editor_noise_packet_hint) else "",
                    enabled = enabled, tag = "host_editor_noise_packet_$index", technical = true,
                    onChange = { replace(item.copy(packet = it)) }
                )
                HostEditorTextField(
                    label = stringResource(R.string.host_editor_noise_delay), value = item.delay,
                    hint = stringResource(R.string.host_editor_range_hint), enabled = enabled,
                    tag = "host_editor_noise_delay_$index", technical = true,
                    onChange = { replace(item.copy(delay = normalizePersianDigits(it))) }
                )
            }
        }
    }
}

@Composable
internal fun HostFinalMaskEditor(draft: HostDraft, enabled: Boolean, onChange: (HostDraft) -> Unit) {
    val theme = LocalThemeState.current
    HostEditorSection(stringResource(R.string.host_editor_final_mask), "final_mask", enabled) {
        Text(stringResource(R.string.host_editor_final_mask_hint), fontSize = 12.sp, color = theme.mutedColor)
        if (!draft.finalMaskSupported) {
            Text(stringResource(R.string.host_editor_final_mask_unsupported), fontSize = 12.sp, color = theme.inkColor)
        }
        HostEditorTextField(
            label = stringResource(R.string.host_editor_final_mask), value = draft.finalMaskText,
            enabled = enabled && draft.finalMaskSupported, singleLine = false, technical = true,
            tag = "host_editor_final_mask_json", help = stringResource(R.string.host_editor_help_final_mask),
            onChange = { onChange(draft.withFinalMask(it)) }
        )
    }
}
