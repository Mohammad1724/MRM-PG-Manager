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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PrimaryButton
import com.mrm.pgmanager.ui.components.SecondaryButton
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.RenewalLogic
import com.mrm.pgmanager.utils.RevenueLogic
import java.time.LocalDate

/**
 * دیالوگِ تمدیدِ اشتراک.
 *
 * برخلافِ «ریستِ زمان» که انقضا را از امروز حساب می‌کند، اینجا پیش‌فرض بر
 * **افزودن** به زمانِ باقی‌مانده است و پیش از تأیید، تاریخِ نهایی به کاربر
 * نشان داده می‌شود تا اشتباهی رخ ندهد.
 */
@Composable
fun RenewUserDialog(
    user: PanelUser,
    onDismiss: () -> Unit,
    currency: String = "تومان",
    onConfirm: (days: Int, mode: RenewalLogic.Mode, amount: Long) -> Unit
) {
    val theme = LocalThemeState.current
    var days by remember { mutableStateOf("30") }
    var mode by remember { mutableStateOf(RenewalLogic.Mode.EXTEND) }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val parsedDays = days.toIntOrNull()?.takeIf { it > 0 }
    val remaining = DateLogic.remainingDays(user.expire)
    val newDate: LocalDate? = parsedDays?.let { RenewalLogic.newExpiryDate(user.expire, it, mode) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().clip(DsRadius.Xxl)
                .background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("تمدید اشتراک", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

            // ── وضعیتِ فعلی
            Row(
                Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Md)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MrmText(user.username, isTechnical = true, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    DateLogic.daysLeftText(user.expire),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        remaining == null -> theme.mutedColor
                        remaining < 0L -> GlassRed
                        remaining <= 3L -> GlassRed
                        remaining <= 7L -> com.mrm.pgmanager.ui.theme.GlassAmber
                        else -> GlassGreen
                    }
                )
            }

            // ── شیوهٔ محاسبه
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("شیوهٔ محاسبه", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip(
                        label = "افزودن به باقی‌مانده",
                        selected = mode == RenewalLogic.Mode.EXTEND,
                        modifier = Modifier.weight(1f)
                    ) { mode = RenewalLogic.Mode.EXTEND }
                    ModeChip(
                        label = "شروع از امروز",
                        selected = mode == RenewalLogic.Mode.FROM_TODAY,
                        modifier = Modifier.weight(1f)
                    ) { mode = RenewalLogic.Mode.FROM_TODAY }
                }
                Text(
                    if (mode == RenewalLogic.Mode.EXTEND)
                        "زمانِ باقی‌ماندهٔ کاربر حفظ می‌شود و مدتِ جدید روی آن اضافه می‌گردد."
                    else
                        "زمانِ باقی‌مانده نادیده گرفته می‌شود و انقضا از امروز حساب می‌شود.",
                    fontSize = 10.sp,
                    color = theme.mutedColor
                )
            }

            // ── مدت
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Md).background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    days,
                    { days = it.filter(Char::isDigit).take(4); error = null },
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
                if (days.isEmpty()) Text("مدت تمدید (روز)", color = theme.mutedColor, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 90, 180).forEach { value ->
                    val sel = parsedDays == value
                    Box(
                        Modifier.weight(1f).height(32.dp).clip(DsRadius.Sm)
                            .background(if (sel) theme.accentPrimary.copy(alpha = 0.14f) else theme.searchBgColor)
                            .border(
                                BorderStroke(DsBorder.Hairline, if (sel) theme.accentPrimary.copy(alpha = 0.45f) else theme.borderColor),
                                DsRadius.Sm
                            )
                            .clickable { days = value.toString(); error = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$value روز",
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) theme.accentPrimary else theme.inkColor
                        )
                    }
                }
            }

            // ── پیش‌نمایشِ نتیجه
            if (newDate != null) {
                val shamsi = JalaliCalendar.gregorianToJalali(newDate.year, newDate.monthValue, newDate.dayOfMonth).toString()
                val totalDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), newDate)
                Column(
                    Modifier.fillMaxWidth().clip(DsRadius.Md)
                        .background(theme.accentPrimary.copy(alpha = 0.10f))
                        .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.28f)), DsRadius.Md)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("انقضای جدید", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MrmText(shamsi, isTechnical = true, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.accentPrimary)
                        Text("($totalDays روز از امروز)", fontSize = 10.sp, color = theme.mutedColor)
                    }
                }
            }

            // ── مبلغ (اختیاری) — پایهٔ گزارشِ درآمد
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Md).background(theme.searchBgColor)
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            BasicTextField(
                                amount,
                                { v -> amount = v.filter(Char::isDigit).take(12) },
                                textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (amount.isEmpty()) Text("مبلغ (اختیاری)", color = theme.mutedColor, fontSize = 13.sp)
                        }
                        Text(currency, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                    }
                }
                val parsedAmount = amount.toLongOrNull()?.takeIf { it > 0L }
                if (parsedAmount != null) {
                    Text(
                        RevenueLogic.formatAmount(parsedAmount) + " " + currency,
                        fontSize = 10.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("خالی بگذارید تا در گزارشِ درآمد ثبت نشود.", fontSize = 9.5.sp, color = theme.mutedColor)
                }
            }

            error?.let { Text(it, fontSize = 11.sp, color = GlassRed, fontWeight = FontWeight.Medium) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "تمدید",
                    onClick = {
                        val n = parsedDays
                        if (n == null) error = "عدد روز معتبر (بزرگ‌تر از صفر) وارد کنید."
                        else onConfirm(n, mode, amount.toLongOrNull() ?: 0L)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = parsedDays != null
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.accentPrimary.copy(alpha = 0.14f) else theme.searchBgColor)
            .border(
                BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(alpha = 0.45f) else theme.borderColor),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) theme.accentPrimary else theme.mutedColor
        )
    }
}
