package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.model.SaleRecord
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.RevenueLogic

/**
 * بخشِ «درآمد» — داخلِ صفحهٔ آمار نمایش داده می‌شود.
 *
 * دادهٔ این بخش کاملاً محلی است: هر تمدیدی که با مبلغ ثبت شود یک [SaleRecord]
 * می‌سازد. پنل چیزی دربارهٔ پول نمی‌داند.
 */
@Composable
fun RevenueSection(
    sales: List<SaleRecord>,
    currency: String,
    modifier: Modifier = Modifier,
    onDeleteSale: ((SaleRecord) -> Unit)? = null
) {
    val theme = LocalThemeState.current
    var range by remember { mutableStateOf(RevenueLogic.Range.THIS_MONTH) }
    var showAllSales by remember { mutableStateOf(false) }

    val filtered = remember(sales, range) { RevenueLogic.filter(sales, null, range) }
    val summary = remember(filtered) { RevenueLogic.summarize(filtered) }
    val buckets = remember(filtered, range) {
        if (range == RevenueLogic.Range.THIS_YEAR || range == RevenueLogic.Range.ALL)
            RevenueLogic.monthlyBuckets(filtered, 6)
        else RevenueLogic.dailyBuckets(filtered, if (range == RevenueLogic.Range.TODAY) 7 else 30)
    }
    val top = remember(filtered) { RevenueLogic.topUsers(filtered, 5) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── سرصفحه + انتخابِ بازه
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundedAppIcon(AppIcon.Money, tint = theme.accentPrimary, size = 14.dp)
                Text("درآمد", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
            }
            Text(
                "بر پایهٔ تمدیدهایی که با مبلغ ثبت کرده‌اید",
                fontSize = 10.sp, color = theme.mutedColor
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RangeChip("امروز", range == RevenueLogic.Range.TODAY) { range = RevenueLogic.Range.TODAY }
                RangeChip("این ماه", range == RevenueLogic.Range.THIS_MONTH) { range = RevenueLogic.Range.THIS_MONTH }
                RangeChip("ماه قبل", range == RevenueLogic.Range.LAST_MONTH) { range = RevenueLogic.Range.LAST_MONTH }
                RangeChip("۳۰ روز", range == RevenueLogic.Range.LAST_30_DAYS) { range = RevenueLogic.Range.LAST_30_DAYS }
                RangeChip("امسال", range == RevenueLogic.Range.THIS_YEAR) { range = RevenueLogic.Range.THIS_YEAR }
                RangeChip("همه", range == RevenueLogic.Range.ALL) { range = RevenueLogic.Range.ALL }
            }
        }

        // ── مبلغِ کل
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                .background(theme.accentPrimary.copy(alpha = 0.10f))
                .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.28f)), DsRadius.Lg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("مجموع درآمد", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MrmText(
                    RevenueLogic.formatAmount(summary.total),
                    isTechnical = true, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                    color = theme.accentPrimary, maxLines = 1
                )
                Text(currency, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
            }
        }

        // ── سه شاخص
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("تعداد فروش", summary.count.toString(), Modifier.weight(1f))
            MiniStat("مشتری", summary.uniqueUsers.toString(), Modifier.weight(1f))
            MiniStat("میانگین", RevenueLogic.formatAmountShort(summary.average), Modifier.weight(1f))
        }

        // ── نمودار میله‌ای
        if (buckets.any { it.total > 0L }) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("روندِ درآمد", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                RevenueBars(buckets, theme.accentPrimary)
            }
        }

        // ── پرفروش‌ترین‌ها
        if (top.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("بیشترین پرداخت", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                val max = top.maxOf { it.total }.coerceAtLeast(1L)
                top.forEach { b ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MrmText(b.label, isTechnical = true, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(
                                RevenueLogic.formatAmount(b.total),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(theme.searchBgColor)
                        ) {
                            Box(
                                Modifier.fillMaxWidth(b.total.toFloat() / max).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(theme.accentPrimary)
                            )
                        }
                    }
                }
            }
        }

        // ── فهرستِ فروش‌ها
        if (filtered.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                    .padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoundedAppIcon(AppIcon.Receipt, tint = theme.mutedColor, size = 24.dp)
                Text("فروشی در این بازه نیست", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                Text(
                    "هنگام تمدید، مبلغ را وارد کنید تا اینجا ثبت شود",
                    fontSize = 9.5.sp, color = theme.mutedColor
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("فروش‌ها", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Text("${filtered.size}", fontSize = 10.sp, color = theme.mutedColor)
                }
                val shown = if (showAllSales) filtered else filtered.take(8)
                shown.forEach { s -> SaleRow(s, currency, onDeleteSale) }
                if (filtered.size > 8) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(theme.searchBgColor)
                            .clickable { showAllSales = !showAllSales }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (showAllSales) "نمایش کمتر" else "نمایش همه (${filtered.size})",
                            fontSize = 10.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.accentPrimary.copy(alpha = 0.14f) else theme.searchBgColor)
            .border(
                BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(alpha = 0.45f) else theme.borderSubtle),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label, fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) theme.accentPrimary else theme.mutedColor
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Column(
        modifier.clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, fontSize = 9.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
        MrmText(value, isTechnical = true, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** نمودارِ میله‌ایِ ساده؛ برچسبِ محور فقط برای اولی/میانی/آخری تا شلوغ نشود. */
@Composable
private fun RevenueBars(buckets: List<RevenueLogic.Bucket>, accent: Color) {
    val theme = LocalThemeState.current
    val max = buckets.maxOf { it.total }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(90.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            buckets.forEach { b ->
                val frac = (b.total.toFloat() / max).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            // حداقل ارتفاع تا میله‌های صفر هم به‌عنوان «روزِ بدونِ فروش» دیده شوند
                            .fillMaxHeight(if (frac <= 0f) 0.02f else frac.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (b.total > 0L) accent else theme.borderSubtle)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val first = buckets.firstOrNull()?.label ?: ""
            val mid = buckets.getOrNull(buckets.size / 2)?.label ?: ""
            val last = buckets.lastOrNull()?.label ?: ""
            Text(first, fontSize = 8.5.sp, color = theme.mutedColor)
            if (buckets.size > 2) Text(mid, fontSize = 8.5.sp, color = theme.mutedColor)
            Text(last, fontSize = 8.5.sp, color = theme.mutedColor)
        }
    }
}

@Composable
private fun SaleRow(sale: SaleRecord, currency: String, onDelete: ((SaleRecord) -> Unit)?) {
    val theme = LocalThemeState.current
    val d = java.time.Instant.ofEpochMilli(sale.soldAt)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val j = JalaliCalendar.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MrmText(sale.username, isTechnical = true, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "${j} • ${sale.days} روز",
                fontSize = 9.sp, color = theme.mutedColor
            )
        }
        Text(
            RevenueLogic.formatAmount(sale.amount),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary
        )
        Text(currency, fontSize = 9.sp, color = theme.mutedColor)
        if (onDelete != null) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                    .clickable { onDelete(sale) },
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Delete, tint = theme.mutedColor, size = 13.dp) }
        }
    }
}
