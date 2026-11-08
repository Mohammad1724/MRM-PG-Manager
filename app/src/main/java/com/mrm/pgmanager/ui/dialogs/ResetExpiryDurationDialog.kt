package com.mrm.pgmanager.ui.dialogs

import androidx.compose.ui.res.stringResource

import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.dialogs.paneTitle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.DsComponent

@Composable
fun ResetExpiryDurationDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val theme = LocalThemeState.current
    val invalidMsg = stringResource(R.string.re_invalid)
    val daysLabel = stringResource(R.string.re_days_label)
    var days by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().paneTitle(stringResource(R.string.re_title)).imePadding().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).verticalScroll(rememberScrollState()).padding(DsSpacing.Xl), verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)) {
            Text(stringResource(R.string.re_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text(stringResource(R.string.re_subtitle), fontSize = 11.sp, color = theme.mutedColor)
            Box(Modifier.fillMaxWidth().heightIn(min = DsComponent.Field).clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.controlBorderColor), DsRadius.Md).padding(horizontal = DsSpacing.FieldHorizontal, vertical = DsSpacing.Sm), contentAlignment = Alignment.CenterStart) {
                BasicTextField(days, { raw ->
                    val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                    days = n.filter(Char::isDigit); error = null
                }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = daysLabel })
                if (days.isEmpty()) Text(daysLabel, color = theme.mutedColor)
            }
            error?.let { Text(it, fontSize = 11.sp, color = GlassRed, fontWeight = FontWeight.Medium) }
            ExpiryPresetSelector(days = days, onSelect = { days = it.toString(); error = null })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                PGSecondaryButton(stringResource(R.string.re_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                // بی‌وزن تا متنِ دکمه بریده نشود؛ «لغو» باقیِ جا را می‌گیرد.
                PGPrimaryButton(
                    text = stringResource(R.string.re_apply),
                    icon = AppIcon.Check,
                    onClick = {
                        val normalized = com.mrm.pgmanager.utils.normalizePersianDigits(days)
                        val n = normalized.toIntOrNull()?.takeIf { it > 0 }
                        if (n == null) error = invalidMsg
                        else onConfirm(n)
                    }
                )
            }
        }
    }
}

/** Preset durations remain directly testable without opening a Dialog window. */
@Composable
internal fun ExpiryPresetSelector(days: String, onSelect: (Int) -> Unit) {
    val theme = LocalThemeState.current
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
        listOf(7, 30, 60, 90).forEach { value ->
            val selected = days == value.toString()
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(DsRadius.Sm)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .background(theme.searchBgColor)
                    .border(BorderStroke(if (selected) DsBorder.Focus else DsBorder.Hairline, if (selected) theme.accentTextColor else theme.controlBorderColor), DsRadius.Sm)
                    .padding(DsSpacing.Xs),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.re_days_value, value), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = theme.inkColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
