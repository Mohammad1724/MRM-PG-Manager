package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.designsystem.DsAnim
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * ناوبریِ پایینِ صفحه — جایگزینِ کشوی کناری برای کارِ تک‌دستی.
 *
 * چرا پایین: دکمهٔ همبرگری در بالای صفحه بود و روی گوشی‌های بلند با یک دست
 * عملاً دور از دسترس؛ ضمن اینکه هر جابه‌جایی سه حرکت می‌خواست
 * (رسیدن به بالا → باز کردن کشو → انتخاب). حالا یک تپ در ناحیهٔ شست کافی است.
 *
 * سه بخشِ پرکاربرد مستقیم اینجا هستند و بقیه در شیتِ «بیشتر»:
 * ایندکس‌ها عمداً با شاخه‌های `when(selectedTab)` در MainActivity یکی است
 * (۰ داشبورد، ۱ کاربران، ۲ آمار، ۳ گروه‌ها، ۴ تمپلت‌ها) و با `TAB_COUNT`
 * به پیجر می‌رود. چیپِ حساب (آواتار) سمتِ راستِ کپسول اولین آیتم است و
 * در ایندکس‌ها حساب نشده — اسکرولِ خودکار با `selectedTab + 1` جلو می‌رود.
 */
const val TAB_DASHBOARD = 0
const val TAB_USERS = 1
const val TAB_STATISTICS = 2
const val TAB_GROUPS = 3
const val TAB_TEMPLATES = 4

/** بخش‌های روی کپسول، به همان ترتیبِ صفحه‌های Pager. */
private val NAV_ITEMS = listOf(
    TAB_DASHBOARD to AppIcon.Gauge,
    TAB_USERS to AppIcon.Users,
    TAB_STATISTICS to AppIcon.Timer,
    TAB_GROUPS to AppIcon.Folder,
    TAB_TEMPLATES to AppIcon.Template
)

/** تعداد کل تب‌ها — `pageCount` پیجر در MainActivity از همین‌جا می‌خواند. */
val TAB_COUNT = NAV_ITEMS.size

@Composable
private fun navLabel(tab: Int): String = when (tab) {
    TAB_DASHBOARD -> stringResource(R.string.dashboard)
    TAB_USERS -> stringResource(R.string.users)
    TAB_STATISTICS -> stringResource(R.string.statistics)
    TAB_GROUPS -> stringResource(R.string.groups_title)
    else -> stringResource(R.string.templates_title)
}

/**
 * نوارِ ناوبریِ **شناور** — یک کپسول که روی محتوا می‌نشیند.
 *
 * همهٔ بخش‌ها اینجا هستند و چون در عرضِ گوشی جا نمی‌شوند، کپسول **اسکرولِ
 * افقی** دارد؛ عمداً اندازهٔ چیپ‌ها کوچک نشده تا با شست به‌راحتی زده شوند.
 * با عوض‌شدن بخش، لیست خودکار روی بخشِ فعال اسکرول می‌کند تا همیشه دیده شود.
 *
 * @param visible وقتی false شود، کپسول با محو‌شدن و سُر خوردن به پایین می‌رود.
 *   MainActivity این را از جهتِ اسکرولِ صفحه‌ها می‌گیرد (nested scroll)، پس
 *   هنگام خواندنِ لیستِ کاربران جلوی محتوا را نمی‌گیرد و با کوچک‌ترین اسکرول
 *   به بالا برمی‌گردد.
 */
@Composable
fun MrmFloatingNav(
    selectedTab: Int,
    visible: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accountLabel: String = "",
    onAccount: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedTab) {
        // چون آواتار حساب آیتمِ ۰ است، بخشِ فعال = ایندکس + ۱؛ وسطِ دید بیاور.
        listState.animateScrollToItem((selectedTab + 1).coerceIn(0, TAB_COUNT), scrollOffset = -80)
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // ورود: از پایین سُر می‌خورد و نرم می‌ایستد. خروج: کوتاه‌تر و تندشونده،
        // چون منتظرِ رفتنِ نوار ماندن آزاردهنده است.
        enter = androidx.compose.animation.fadeIn(DsAnim.enter()) +
            androidx.compose.animation.slideInVertically(DsAnim.enter()) { it / 2 },
        exit = androidx.compose.animation.fadeOut(DsAnim.exit()) +
            androidx.compose.animation.slideOutVertically(DsAnim.exit()) { it / 2 }
    ) {
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .shadow(10.dp, DsRadius.Full, clip = false)
                .clip(DsRadius.Full)
                .background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full)
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.lazy.item(key = "account") {
                    AccountNavChip(label = accountLabel) { onAccount() }
                }
                items(NAV_ITEMS.size) { index ->
                    val (tab, icon) = NAV_ITEMS[index]
                    NavChip(
                        icon = icon,
                        label = navLabel(tab),
                        selected = selectedTab == tab
                    ) { onSelect(tab) }
                }
            }
        }
    }
}

/**
 * چیپِ حساب (آواتار) — اولین آیتمِ کپسول، سمتِ راستِ (در RTL) نوار ناوبری.
 * حلقهٔ اول نام ادمین است و ورقهٔ `AccountSheet` را باز می‌کند؛ جایگزینِ
 * دکمهٔ منوی کشوی کناریِ حذف‌شده است.
 */
@Composable
private fun AccountNavChip(
    label: String,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val cd = stringResource(R.string.cd_account)
    Box(
        Modifier
            .size(46.dp)
            .clip(DsRadius.Full)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full)
            .semantics { contentDescription = cd }
            .pressScale(0.94f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (label.trim().firstOrNull() ?: "?").uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = theme.inkColor
        )
    }
}

/**
 * یک چیپِ داخلِ کپسول. برچسبِ همهٔ بخش‌ها همیشه دیده می‌شود (کپسول اسکرول
 * می‌شود، پس نیازی به کوچک‌کردنشان نیست) و بخشِ فعال پرکنندهٔ اصلیِ تم را می‌گیرد.
 */
@Composable
private fun NavChip(
    icon: AppIcon,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    // پس‌زمینهٔ گرادیانیِ حالتِ فعال با آلفا محو/ظاهر می‌شود (Brush را نمی‌شود
    // مثل Color انیمیت کرد)؛ رنگِ آیکون و متن جداگانه می‌لغزد.
    val selectedAlpha by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = DsAnim.normal(), label = "navChipBg"
    )
    val tint by animateColorAsState(
        if (selected) theme.onPrimary else theme.mutedColor,
        animationSpec = DsAnim.normal(), label = "navChipTint"
    )
    val brush = theme.primaryBrush
    val edge = theme.primaryEdge
    Row(
        modifier
            // ۴۶dp ارتفاع + padding کپسول ⇒ هدفِ لمس بالای ۴۸dpِ توصیه‌شده.
            .height(46.dp)
            .clip(DsRadius.Full)
            .drawBehind {
                if (selectedAlpha > 0.01f) {
                    val r = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(brush = brush, cornerRadius = r, alpha = selectedAlpha)
                    drawRoundRect(
                        color = edge, cornerRadius = r, alpha = selectedAlpha,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
            .semantics { contentDescription = label }
            .pressScale(0.94f)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoundedAppIcon(icon, tint = tint, size = 18.dp)
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
