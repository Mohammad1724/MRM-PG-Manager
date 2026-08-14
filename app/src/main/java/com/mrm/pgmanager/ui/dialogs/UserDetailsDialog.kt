package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
private fun detailDaysText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    return runCatching {
        val end = try { java.time.Instant.parse(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(expire.take(10)) }
        val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end)
        if (d < 0L) "منقضی" else "$d روز"
    }.getOrDefault("نامحدود")
}

/** دکمهٔ آیکون گرد برای کارت اشتراک و بخش‌های مشابه */
@Composable
private fun IconActionBtn(icon: AppIcon, contentDesc: String, theme: com.mrm.pgmanager.ui.theme.ThemeState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(DsRadius.Sm)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 16.dp)
    }
}

/** منوی کشویی کپسولی برای اکشن‌ها (بدهکار/فاکتور) - هماهنگ با design system */
@Composable
private fun CapsuleActionMenu(
    label: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    isDebtor: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalThemeState.current
    val headerColor = if (isDebtor) GlassRed else theme.accentPrimary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // دکمه سربرگ کشویی - هماهنگ با دکمه‌های کپسولی settings
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Xl)
                .background(headerColor.copy(0.10f))
                .border(BorderStroke(1.2.dp, headerColor.copy(0.30f)), DsRadius.Xl)
                .clickable { onToggleExpand() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedAppIcon(if (isDebtor) AppIcon.Warning else AppIcon.Money, tint = headerColor, size = 18.dp)
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = headerColor, modifier = Modifier.weight(1f))
                // فلش بالا/پایین: وقتی باز است به بالا، وقتی بسته است به پایین
                RoundedAppIcon(
                    AppIcon.Next,
                    tint = headerColor, size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) -90f else 90f }
                )
            }
        }
        // محتوای کشویی با انیمیشن
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(160))
        ) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xl)
                    .background(if (theme.isDark) Color.White.copy(0.06f) else Color.White)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                content = actions
            )
        }
    }
}

/** یک ردیف دکمه کپسولی درون منوی کشویی */
@Composable
private fun CapsuleMenuItem(
    icon: AppIcon,
    label: String,
    accent: Color,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        primary -> accent.copy(0.78f)
        danger -> accent.copy(0.10f)
        else -> LocalThemeState.current.searchBgColor
    }
    val textColor = when {
        primary -> Color(0xFF202124)
        else -> accent
    }
    var borderColor = LocalThemeState.current.borderColor
    if (danger || primary) borderColor = accent.copy(if (primary) 0f else 0.30f)
    Box(
        Modifier.fillMaxWidth().height(42.dp).clip(DsRadius.Lg).background(bg)
            .border(BorderStroke(DsBorder.Hairline, borderColor), DsRadius.Lg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(icon, tint = textColor, size = 17.dp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
fun UserDetailsDialog(
    user: PanelUser,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onResetUsage: () -> Unit,
    onResetExpiry: (Int) -> Unit,
    onApplyTemplate: ((Int, String) -> Unit)? = null,
    session: com.mrm.pgmanager.data.model.Session? = null,
    debtorInfo: com.mrm.pgmanager.data.model.DebtorInfo? = null,
    onMarkDebtor: (() -> Unit)? = null,
    onClearDebt: (() -> Unit)? = null,
    onInvoice: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var currentUser by remember(user) { mutableStateOf(user) }
    var editOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    
    // دریافت لینک اشتراک به‌صورت lazy
    fun ensureSub(onResult: (String) -> Unit) {
        if (currentUser.subUrl.isNotBlank()) {
            onResult(currentUser.subUrl)
        } else if (session != null) {
            scope.launch {
                runCatching { PanelApi.user(session, currentUser.username) }.onSuccess {
                    currentUser = it
                    onResult(it.subUrl)
                }.onFailure {
                    android.widget.Toast.makeText(context, "دریافت لینک اشتراک ناموفق بود", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            onResult(currentUser.subUrl)
        }
    }
    
    // ── نمودار مصرفِ همین کاربر ──────────────────────────────────────────────
    // بازهٔ پیش‌فرض ۷ روز است چون برای تشخیصِ الگوی مصرف گویاتر از ۲۴ ساعت است.
    var chartRange by remember { mutableStateOf(StatsRange.LAST_7D) }
    var chartPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    var chartLoading by remember { mutableStateOf(false) }
    var chartFailed by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser.username, chartRange, session) {
        val s = session ?: return@LaunchedEffect
        chartLoading = true
        chartFailed = false
        runCatching { PanelApi.userTrafficUsage(s, currentUser.username, chartRange) }
            .onSuccess { chartPoints = it }
            .onFailure { chartPoints = emptyList(); chartFailed = true }
        chartLoading = false
    }

    var usageConfirm by remember { mutableStateOf(false) }
    var expiryConfirm by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var availableTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesFailed by remember { mutableStateOf(false) }
    var debtorMenuExpanded by remember { mutableStateOf(false) }
    
    val traffic = if (currentUser.dataLimit == 0L) (if (isFa) "نامحدود" else "Unlimited") else formatBytes(currentUser.dataLimit)
    val percentage = if (currentUser.dataLimit > 0L) ((currentUser.usedTraffic * 100f / currentUser.dataLimit).toInt()).coerceIn(0, 100) else 0
    val progressColor = when { percentage < 70 -> GlassGreen; percentage < 90 -> GlassAmber; else -> GlassRed }

    fun section() = Modifier.fillMaxWidth().clip(DsRadius.Lg)
        .background(theme.cardSurfaceColor)
        .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg).padding(12.dp)

    @Composable fun sectionTitle(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
    
    @Composable fun statTile(label: String, value: String, modifier: Modifier = Modifier) {
        Column(
            modifier
                .height(58.dp)
                .clip(DsRadius.Md)
                .background(theme.searchBgColor)
                .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Md)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    
    @Composable fun action(text: String, modifier: Modifier = Modifier, destructive: Boolean = false, primary: Boolean = false, height: androidx.compose.ui.unit.Dp = 44.dp, click: () -> Unit) {
        val bg = when { primary -> theme.accentPrimary; destructive -> GlassRed.copy(.10f); else -> theme.searchBgColor }
        val color = when { primary -> Color(0xFF202124); destructive -> GlassRed; else -> theme.inkColor }
        var borderColor = theme.borderColor
        if (destructive) borderColor = GlassRed.copy(.30f)
        if (primary) borderColor = theme.accentPrimary
        Box(
            modifier
                .height(height)
                .clip(DsRadius.Md)
                .background(bg)
                .border(BorderStroke(0.7.dp, borderColor), DsRadius.Md)
                .clickable(onClick = click),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontSize = if (height <= 30.dp) 9.sp else 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .clip(DsRadius.Xxl)
                    .background(theme.cardSurfaceColor)
                    .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Header: Title + Close Button
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundedAppIcon(AppIcon.User, tint = theme.accentPrimary, size = 20.dp)
                            Text(
                                if (isFa) "جزئیات کاربر" else "User Details",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.inkColor
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Text("×", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        }
                    }

                    // ── User Identity & Activity Box
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DsRadius.Lg)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(DsRadius.Xl)
                                .background(if (currentUser.isOnline) GlassGreen.copy(0.14f) else Color.Gray.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (currentUser.isOnline) GlassGreen else Color.Gray)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            MrmText(
                                currentUser.username,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                isTechnical = true
                            )
                            MrmText(
                                lastSeenText(currentUser.onlineAt, currentUser.isOnline),
                                fontSize = 11.sp,
                                color = theme.mutedColor,
                                maxLines = 1,
                                isTechnical = true
                            )
                        }
                        val active = currentUser.status != "disabled"
                        Box(
                            Modifier
                                .height(26.dp)
                                .clip(DsRadius.Sm)
                                .background((if (active) GlassGreen else GlassRed).copy(alpha = 0.13f))
                                .border(BorderStroke(0.8.dp, if (active) GlassGreen.copy(alpha = 0.35f) else GlassRed.copy(alpha = 0.35f)), DsRadius.Sm)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (active) (if (isFa) "فعال" else "Active") else (if (isFa) "غیرفعال" else "Disabled"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) GlassGreen else GlassRed
                            )
                        }
                    }

                    // ── User Note Section (If exists)
                    if (!currentUser.note.isNullOrBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DsRadius.Md)
                                .background(theme.searchBgColor)
                                .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Md)
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 16.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(if (isFa) "توضیحات" else "Note", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                                Text(currentUser.note.orEmpty(), fontSize = 12.sp, color = theme.inkColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // ── Stats Section: Used / Total / Remaining
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(DsRadius.Lg)
                            .background(theme.cardSurfaceColor)
                            .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (isFa) "وضعیت اشتراک" else "Subscription Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            statTile(if (isFa) "مصرف‌شده" else "Used Traffic", formatBytes(currentUser.usedTraffic), Modifier.weight(1f))
                            statTile(if (isFa) "حجم کل" else "Data Limit", traffic, Modifier.weight(1f))
                            statTile(if (isFa) "زمان باقی‌مانده" else "Remaining Time", detailDaysText(currentUser.expire), Modifier.weight(1f))
                        }
                        // Custom Progress Bar with baseline-aligned percentage text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isFa) "مصرف" else "Usage", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(if (theme.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                                if (percentage > 0) {
                                    Box(Modifier.fillMaxWidth(percentage / 100f).fillMaxHeight().background(progressColor, RoundedCornerShape(50)))
                                }
                            }
                            Text(
                                text = if (currentUser.dataLimit == 0L) "∞" else "$percentage%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = progressColor,
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false
                                    )
                                )
                            )
                        }
                    }

                    // ── نمودار مصرف (فقط وقتی نشست در دسترس است)
                    if (session != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(DsRadius.Lg)
                                .background(theme.cardSurfaceColor)
                                .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isFa) "نمودار مصرف" else "Usage Chart",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.inkColor
                                )
                                // انتخابِ بازه: ۲۴ ساعت / ۷ روز / ۳۰ روز
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        StatsRange.LAST_24H to (if (isFa) "۲۴ ساعت" else "24h"),
                                        StatsRange.LAST_7D to (if (isFa) "۷ روز" else "7d"),
                                        StatsRange.LAST_30D to (if (isFa) "۳۰ روز" else "30d")
                                    ).forEach { (range, label) ->
                                        val sel = chartRange == range
                                        Box(
                                            Modifier
                                                .clip(DsRadius.Sm)
                                                .background(if (sel) theme.accentPrimary.copy(0.16f) else theme.searchBgColor)
                                                .border(
                                                    BorderStroke(
                                                        0.7.dp,
                                                        if (sel) theme.accentPrimary.copy(0.42f) else theme.borderColor
                                                    ),
                                                    DsRadius.Sm
                                                )
                                                .clickable { chartRange = range }
                                                .padding(horizontal = 7.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                fontSize = 9.sp,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (sel) theme.accentPrimary else theme.mutedColor,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            when {
                                chartLoading -> Box(
                                    Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp))
                                        .background(theme.searchBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isFa) "در حال دریافت…" else "Loading…",
                                        fontSize = 10.sp,
                                        color = theme.mutedColor
                                    )
                                }
                                // اگر پنل این اندپوینت را نداشته باشد، به‌جای نمودارِ خالی دلیلش را می‌گوییم
                                chartFailed -> Box(
                                    Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp))
                                        .background(theme.searchBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isFa) "دریافت نمودار ممکن نشد" else "Could not load chart",
                                        fontSize = 10.sp,
                                        color = theme.mutedColor
                                    )
                                }
                                else -> {
                                    UsageChart(
                                        points = chartPoints,
                                        accent = theme.accentPrimary,
                                        themeIsDark = theme.isDark,
                                        valueFormatter = ::formatBytes
                                    )
                                    // مجموعِ همان بازه — عددی که کاربر معمولاً دنبالش است
                                    if (chartPoints.isNotEmpty()) {
                                        val sum = chartPoints.sumOf { it.totalTraffic }
                                        Text(
                                            (if (isFa) "مجموع این بازه: " else "Total in range: ") + formatBytes(sum),
                                            fontSize = 10.sp,
                                            color = theme.mutedColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Subscription Action Chips (Copy Link / Show QR)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DsRadius.Md)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Md)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isFa) "لینک اشتراک" else "Subscription Link", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .height(30.dp)
                                    .clip(DsRadius.Sm)
                                    .background(theme.accentPrimary.copy(alpha = 0.12f))
                                    .border(BorderStroke(0.8.dp, theme.accentPrimary.copy(alpha = 0.35f)), DsRadius.Sm)
                                    .clickable {
                                        ensureSub { url ->
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", url))
                                            android.widget.Toast.makeText(context, "لینک اشتراک کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    RoundedAppIcon(AppIcon.Copy, tint = theme.accentPrimary, size = 12.dp)
                                    Text(if (isFa) "کپی" else "Copy", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary)
                                }
                            }
                            Box(
                                Modifier
                                    .height(30.dp)
                                    .clip(DsRadius.Sm)
                                    .background(theme.accentPrimary.copy(alpha = 0.12f))
                                    .border(BorderStroke(0.8.dp, theme.accentPrimary.copy(alpha = 0.35f)), DsRadius.Sm)
                                    .clickable { ensureSub { _ -> qrOpen = true } }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    RoundedAppIcon(AppIcon.Qr, tint = theme.accentPrimary, size = 12.dp)
                                    Text(if (isFa) "بارکد" else "QR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary)
                                }
                            }
                        }
                    }

                    // ── Financial Section (Debtor / Invoice Dropdown Accordion)
                    if (debtorInfo != null) {
                        CapsuleActionMenu(
                            label = if (isFa) "بخش مالی · ${debtorInfo.amount} ${debtorInfo.currency}" else "Financial · ${debtorInfo.amount} ${debtorInfo.currency}",
                            expanded = debtorMenuExpanded,
                            onToggleExpand = { debtorMenuExpanded = !debtorMenuExpanded },
                            isDebtor = true
                        ) {
                            Text(
                                (if (isFa) "ثبت شده در: " else "Marked at: ") +
                                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(debtorInfo.markedAt)) +
                                (if (debtorInfo.notes.isNotBlank()) " - ${debtorInfo.notes}" else ""),
                                fontSize = 10.sp, color = theme.mutedColor
                            )
                            if (debtorInfo.autoDisabled) {
                                Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(GlassRed.copy(0.14f)).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 14.dp)
                                    Text(if (isFa) "به صورت خودکار به دلیل بدهی قطع شده است" else "Automatically disabled due to overdue debt", fontSize = 10.sp, color = GlassRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            CapsuleMenuItem(AppIcon.CheckCircle, if (isFa) "تسویه بدهی" else "Clear Debt", GlassGreen, primary = true) { onClearDebt?.invoke() }
                            CapsuleMenuItem(AppIcon.Edit, if (isFa) "ویرایش بدهی" else "Edit Debt", GlassRed, danger = true) { onMarkDebtor?.invoke() }
                            CapsuleMenuItem(AppIcon.Receipt, if (isFa) "صدور فاکتور" else "Issue Invoice", theme.accentPrimary) { onInvoice?.invoke() }
                        }
                    } else {
                        CapsuleActionMenu(
                            label = if (isFa) "بخش مالی" else "Financial Section",
                            expanded = debtorMenuExpanded,
                            onToggleExpand = { debtorMenuExpanded = !debtorMenuExpanded }
                        ) {
                            CapsuleMenuItem(AppIcon.Warning, if (isFa) "ثبت بدهکار" else "Mark Debtor", GlassRed, danger = true) { onMarkDebtor?.invoke() }
                            CapsuleMenuItem(AppIcon.Receipt, if (isFa) "صدور فاکتور" else "Issue Invoice", theme.accentPrimary) { onInvoice?.invoke() }
                        }
                    }

                    // ── Primary Action Buttons: Templates & Edit User
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        action(if (isFa) "تمپلت‌ها" else "Templates", Modifier.weight(1f)) { templatePickerOpen = true }
                        action(if (isFa) "ویرایش کاربر" else "Edit User", Modifier.weight(2f), primary = true) { editOpen = true }
                    }

                    // ── Quick Actions Grid Box
                    Column(section(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sectionTitle(if (isFa) "عملیات سریع" else "Quick Actions")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            action(if (isFa) "ریست حجم" else "Reset Data", Modifier.weight(1f)) { usageConfirm = true }
                            action(if (isFa) "ریست زمان" else "Reset Expiry", Modifier.weight(1f)) { expiryConfirm = true }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            action(
                                text = if (currentUser.status == "disabled") (if (isFa) "فعال‌سازی" else "Enable") else (if (isFa) "غیرفعال‌سازی" else "Disable"),
                                modifier = Modifier.weight(1f)
                            ) { onToggle() }
                            action(if (isFa) "حذف کاربر" else "Delete User", Modifier.weight(1f), destructive = true) { onDelete() }
                        }
                    }

                    // ── Bottom Close Button
                    SecondaryButton(if (isFa) "بستن" else "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (templatePickerOpen) {
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            val result = runCatching { session?.let { PanelApi.userTemplates(it) } ?: emptyList() }
            availableTemplates = result.getOrDefault(emptyList())
            templatesFailed = result.isFailure
            templatesLoading = false
        }
        BulkApplyTemplateDialog(
            templates = availableTemplates,
            selectedCount = 1,
            onDismiss = { templatePickerOpen = false },
            onApply = { templateId, note -> templatePickerOpen = false; onApplyTemplate?.invoke(templateId, note) },
            isLoading = templatesLoading,
            loadFailed = templatesFailed
        )
    }

    if (editOpen) {
        UserEditorDialog(
            initial = currentUser,
            onDismiss = { editOpen = false },
            onSave = onSave,
            onToggle = onToggle,
            onDelete = onDelete,
            onResetUsage = onResetUsage,
            onResetExpiry = { expiryConfirm = true },
            onApplyTemplateToUser = onApplyTemplate,
            session = session
        )
    }

    if (qrOpen) {
        SubscriptionQrDialog(user = currentUser, onDismiss = { qrOpen = false })
    }

    if (usageConfirm) {
        ConfirmActionDialog(
            title = if (isFa) "ریست حجم مصرف‌شده؟" else "Reset Used Data?",
            message = if (isFa) "مصرف این کاربر صفر می‌شود." else "The user's usage will be set to zero.",
            onDismiss = { usageConfirm = false },
            onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0L); onResetUsage() }
        )
    }

    if (expiryConfirm) {
        ResetExpiryDurationDialog(
            onDismiss = { expiryConfirm = false },
            onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) }
        )
    }
}
