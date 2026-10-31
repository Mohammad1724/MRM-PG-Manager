package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.HostCustomVariable
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.LocalThemeState

internal data class HostCustomVariablesAccess(
    val state: HostOptions<HostCustomVariable>?,
    val load: () -> Unit
)

internal val LocalHostCustomVariables = staticCompositionLocalOf<HostCustomVariablesAccess?> { null }

/** Inline help stays inside the scrolling sheet and does not obscure its fixed actions. */
@Composable
internal fun HostEditorLabel(
    label: String,
    tag: String,
    help: String? = null,
    variables: Boolean = false,
    customVariables: Boolean = false
) {
    val theme = LocalThemeState.current
    val customAccess = LocalHostCustomVariables.current
    val hasCustom = customVariables && customAccess != null
    if (help == null && !variables && !hasCustom) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
        return
    }
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    var customExpanded by rememberSaveable(tag) { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.host_editor_expanded else R.string.host_editor_collapsed)
    val customState = stringResource(if (customExpanded) R.string.host_editor_expanded else R.string.host_editor_collapsed)
    val description = stringResource(R.string.host_editor_help_for, label)
    val customDescription = stringResource(R.string.host_editor_custom_variables_for, label)
    LaunchedEffect(customExpanded, customAccess?.state) {
        if (customExpanded && customAccess != null && customAccess.state == null) customAccess.load()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
            if (help != null || variables) {
                Box(
                    Modifier.size(48.dp).testTag("${tag}_help")
                        .clickable(role = Role.Button) { expanded = !expanded; if (expanded) customExpanded = false }
                        .semantics { contentDescription = description; stateDescription = state },
                    contentAlignment = Alignment.Center
                ) {
                    RoundedAppIcon(AppIcon.Info, tint = theme.mutedColor, size = 18.dp)
                }
            }
            if (hasCustom) {
                Box(
                    Modifier.size(48.dp).testTag("${tag}_custom_variables")
                        .clickable(role = Role.Button) { customExpanded = !customExpanded; if (customExpanded) expanded = false }
                        .semantics { contentDescription = customDescription; stateDescription = customState },
                    contentAlignment = Alignment.Center
                ) {
                    Text("{}", style = TextStyle(fontSize = 15.sp, textDirection = TextDirection.Ltr), color = theme.mutedColor)
                }
            }
        }
        if (expanded || (customExpanded && hasCustom)) {
            Column(
                Modifier.fillMaxWidth().background(theme.searchBgColor, DsRadius.Md)
                    .border(BorderStroke(DsBorder.Hairline, theme.controlBorderColor), DsRadius.Md)
                    .padding(12.dp).testTag(if (expanded) "${tag}_help_content" else "${tag}_custom_content"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (expanded) {
                    if (help != null) Text(help, fontSize = 13.sp, lineHeight = 20.sp, color = theme.inkColor)
                    if (variables) HostBuiltInVariables(tag)
                } else if (customAccess != null) {
                    HostCustomVariablesContent(tag, customAccess)
                }
            }
        }
    }
}

@Composable
private fun HostCustomVariablesContent(tag: String, access: HostCustomVariablesAccess) {
    val theme = LocalThemeState.current
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(tag) { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.host_editor_custom_variables_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.inkColor)
    when (val state = access.state) {
        null, HostOptions.Loading -> Text(stringResource(R.string.host_editor_loading_options), fontSize = 12.sp, color = theme.inkColor)
        is HostOptions.Failed -> {
            Text(stringResource(R.string.host_editor_custom_variables_failed, state.message), fontSize = 12.sp, color = theme.inkColor)
            PGSecondaryButton(stringResource(R.string.retry), onClick = access.load, modifier = Modifier.testTag("${tag}_custom_retry"))
        }
        is HostOptions.Ready -> {
            if (state.values.isEmpty()) Text(stringResource(R.string.host_editor_custom_variables_empty), fontSize = 12.sp, color = theme.inkColor)
            val emptyValue = stringResource(R.string.host_editor_custom_empty_value)
            state.values.forEach { variable ->
                val token = "{${variable.key}}"
                val value = variable.value.ifEmpty { emptyValue }
                val description = stringResource(R.string.host_editor_copy_variable, token, value)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { clipboard.setText(AnnotatedString(token)); copied = token }
                        .semantics { contentDescription = description }.testTag("${tag}_custom_${variable.key}")
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(token, style = TextStyle(fontSize = 12.sp, textDirection = TextDirection.Ltr), fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Text(value, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp, color = theme.inkColor)
                }
            }
        }
    }
    copied?.let {
        Text(stringResource(R.string.host_editor_variable_copied, it),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_custom_copied"),
            fontSize = 12.sp, color = theme.inkColor)
    }
}

@Composable
private fun HostBuiltInVariables(tag: String) {
    val theme = LocalThemeState.current
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(tag) { mutableStateOf<String?>(null) }
    val variables = listOf(
        "USERNAME" to R.string.host_variable_username,
        "DATA_USAGE" to R.string.host_variable_data_usage,
        "USAGE_PERCENTAGE" to R.string.host_variable_usage_percentage,
        "DATA_LEFT" to R.string.host_variable_data_left,
        "DATA_LIMIT" to R.string.host_variable_data_limit,
        "DAYS_LEFT" to R.string.host_variable_days_left,
        "EXPIRE_DATE" to R.string.host_variable_expire_date,
        "JALALI_EXPIRE_DATE" to R.string.host_variable_jalali_expire_date,
        "TIME_LEFT" to R.string.host_variable_time_left,
        "STATUS_EMOJI" to R.string.host_variable_status_emoji,
        "PROTOCOL" to R.string.host_variable_protocol,
        "TRANSPORT" to R.string.host_variable_transport,
        "ADMIN_USERNAME" to R.string.host_variable_admin_username
    )
    Text(stringResource(R.string.host_editor_variables_hint), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.inkColor)
    variables.forEach { (key, descriptionId) ->
        val token = "{$key}"
        val description = stringResource(descriptionId)
        val copyDescription = stringResource(R.string.host_editor_copy_variable, token, description)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { clipboard.setText(AnnotatedString(token)); copied = token }
                .semantics { contentDescription = copyDescription }.testTag("${tag}_variable_$key")
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(token, style = TextStyle(fontSize = 12.sp, textDirection = TextDirection.Ltr), fontWeight = FontWeight.SemiBold, color = theme.inkColor)
            Text(description, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp, color = theme.inkColor)
        }
    }
    copied?.let {
        Text(
            stringResource(R.string.host_editor_variable_copied, it),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_variable_copied"),
            fontSize = 12.sp, color = theme.inkColor
        )
    }
}
