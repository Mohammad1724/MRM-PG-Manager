package com.mrm.pgmanager.ui.dialogs

import androidx.compose.ui.res.stringResource

import com.mrm.pgmanager.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.designsystem.DsComponent

@Composable
fun ResetExpiryDurationDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val theme = LocalThemeState.current
    val invalidMsg = stringResource(R.string.re_invalid)
    var days by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(DsSpacing.Xl), verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)) {
            Text(stringResource(R.string.re_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text(stringResource(R.string.re_subtitle), fontSize = 11.sp, color = theme.mutedColor)
            Box(Modifier.fillMaxWidth().height(DsComponent.Field).clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(horizontal = DsSpacing.FieldHorizontal), contentAlignment = Alignment.CenterStart) {
                BasicTextField(days, { raw ->
                    val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                    days = n.filter(Char::isDigit); error = null
                }, textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                if (days.isEmpty()) Text(stringResource(R.string.re_days_label), color = theme.mutedColor)
            }
            error?.let { Text(it, fontSize = 11.sp, color = GlassRed, fontWeight = FontWeight.Medium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                listOf(7, 30, 60, 90).forEach { value -> Box(Modifier.padding(vertical = 4.dp).weight(1f).height(32.dp).clip(DsRadius.Sm).clickable { days = value.toString(); error = null }.background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm), contentAlignment = Alignment.Center) { Text(stringResource(R.string.re_days_value, value), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = theme.inkColor) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                PGSecondaryButton(stringResource(R.string.re_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PGPrimaryButton(
                    text = stringResource(R.string.re_apply),
                    icon = AppIcon.Check,
                    onClick = {
                        val normalized = com.mrm.pgmanager.utils.normalizePersianDigits(days)
                        val n = normalized.toIntOrNull()?.takeIf { it > 0 }
                        if (n == null) error = invalidMsg
                        else onConfirm(n)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
