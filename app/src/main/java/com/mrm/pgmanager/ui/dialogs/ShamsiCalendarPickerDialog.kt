package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

fun ShamsiCalendarPickerDialog(initialDateShamsi: String, onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
    val theme = LocalThemeState.current
    val today = JalaliCalendar.todayJalali()
    val parsed = remember(initialDateShamsi) {
        val p = initialDateShamsi.replace("-", "/").split("/")
        if (p.size == 3) JalaliCalendar.Date(p[0].toIntOrNull() ?: today.year, p[1].toIntOrNull() ?: today.month, p[2].toIntOrNull() ?: today.day) else today
    }
    var y by remember { mutableStateOf(parsed.year) }
    var m by remember { mutableStateOf(parsed.month) }
    var d by remember { mutableStateOf(parsed.day) }
    val daysInMonth = when {
        m in 1..6 -> 31
        m in 7..11 -> 30
        else -> {
            // محاسبهٔ سال کبیسهٔ شمسی بر پایهٔ چرخهٔ ۳۳ساله (دقت خوب برای سال‌های ۱۲۰۰ تا ۱۵۰۰ ه‍.ش)
            val mod = ((y - 474) % 33 + 33) % 33
            val isLeap = mod == 1 || mod == 5 || mod == 9 || mod == 13 || mod == 17 || mod == 22 || mod == 26 || mod == 30
            if (isLeap) 30 else 29
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme) {
            Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("تقویم", fontWeight = FontWeight.Bold, color = theme.inkColor)
                        TextButton(onClick = { y = today.year; m = today.month; d = today.day }) { Text("امروز", color = theme.accentPrimary) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // ناوبری ماه: کاشی‌های خاکستریِ خنثیِ design system.
                        Box(Modifier.size(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { if (m > 1) m-- else { m = 12; y-- } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Prev, tint = theme.inkColor, size = 18.dp) }
                        Box(Modifier.weight(1f).clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(8.dp), contentAlignment = Alignment.Center) { Text("${JalaliCalendar.Date(y, m, 1).getMonthName()} $y", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                        Box(Modifier.size(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { if (m < 12) m++ else { m = 1; y++ } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Next, tint = theme.inkColor, size = 18.dp) }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(200.dp)) {
                        items((1..daysInMonth).toList()) { day ->
                            val sel = day == d
                            // روز انتخاب‌شده = کپسول اکسنت ۷۸٪ و متن تیره؛ سایر روزها شفاف (هم‌تراز با سگمنت تنظیمات).
                            Box(Modifier.aspectRatio(1f).clip(DsRadius.Md).background(if (sel) theme.accentPrimary.copy(.78f) else Color.Transparent).clickable { d = day }, contentAlignment = Alignment.Center) {
                                Text("$day", color = if (sel) Color(0xFF202124) else theme.inkColor, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                        PrimaryButton("تایید", onClick = { onDateSelected(JalaliCalendar.Date(y, m, d).toString()); onDismiss() }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
