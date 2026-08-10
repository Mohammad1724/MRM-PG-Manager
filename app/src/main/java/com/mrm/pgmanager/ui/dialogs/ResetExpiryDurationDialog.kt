package com.mrm.pgmanager.ui.dialogs

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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.components.PrimaryButton
import com.mrm.pgmanager.ui.components.SecondaryButton
import com.mrm.pgmanager.ui.components.MrmText

@Composable
fun ResetExpiryDurationDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val theme = LocalThemeState.current
    var days by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ریست زمان اشتراک", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text("مدت واقعی اشتراک را وارد کنید. انقضا از امروز دوباره محاسبه می‌شود.", fontSize = 11.sp, color = theme.mutedColor)
            Box(Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(days, { days = it.filter(Char::isDigit); error = null }, textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                if (days.isEmpty()) Text("مدت اشتراک (روز)", color = theme.mutedColor)
            }
            error?.let { Text(it, fontSize = 11.sp, color = GlassRed, fontWeight = FontWeight.Medium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 60, 90).forEach { value -> Box(Modifier.weight(1f).height(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { days = value.toString(); error = null }, contentAlignment = Alignment.Center) { Text("$value روز", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = theme.inkColor) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "اعمال زمان",
                    onClick = {
                        val n = days.toIntOrNull()?.takeIf { it > 0 }
                        if (n == null) error = "عدد روز معتبر (بزرگ‌تر از صفر) وارد کنید."
                        else onConfirm(n)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
