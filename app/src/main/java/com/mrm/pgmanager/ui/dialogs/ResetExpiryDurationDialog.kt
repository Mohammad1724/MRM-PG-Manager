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
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.glassBorder

@Composable
fun ResetExpiryDurationDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val theme = LocalThemeState.current
    var days by remember { mutableStateOf("30") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ریست زمان اشتراک", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text("مدت واقعی اشتراک را وارد کنید. انقضا از امروز دوباره محاسبه می‌شود.", fontSize = 11.sp, color = theme.mutedColor)
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(days, { days = it.filter(Char::isDigit) }, textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                if (days.isEmpty()) Text("مدت اشتراک (روز)", color = theme.mutedColor)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // پیش‌تنظیم‌های روز: کاشی خاکستریِ خنثیِ design system.
                listOf(7, 30, 60, 90).forEach { value -> Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(8.dp)).clickable { days = value.toString() }, contentAlignment = Alignment.Center) { Text("$value روز", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Text("انصراف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
                // دکمهٔ اصلی: کپسول اکسنت ۷۸٪ + متن تیره (هم‌سبک با سگمنت تنظیمات).
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(theme.accentPrimary.copy(.78f)).clickable { days.toIntOrNull()?.takeIf { it > 0 }?.let(onConfirm) }, contentAlignment = Alignment.Center) { Text("اعمال زمان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124)) }
            }
        }
    }
}
