package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.BuildConfig
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.*
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.lastSeenText
import com.mrm.pgmanager.utils.lastSeenShort
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.NotificationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.mrm.pgmanager.ui.theme.glassBg
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsMotion
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.DsTileRadius

/** یک عملیاتِ گروهیِ در انتظارِ تأییدِ کاربر. */
private data class PendingBulk(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit, val danger: Boolean = false)

// Track more gray and visible
private fun trackBg(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.26f) else Color(0xFF6B7280).copy(alpha = 0.28f)

private fun daysLeftText(expire: String?): String = DateLogic.daysLeftText(expire)

private fun daysLeftFull(expire: String?): String = daysLeftText(expire)

private fun formatDebtorAmount(amount: Long): String {
    return when {
        amount >= 1_000_000_000L -> "${amount/1_000_000_000L}B"
        amount >= 1_000_000L -> "${amount/1_000_000L}M"
        amount >= 1_000L -> "${amount/1_000L}k"
        else -> "$amount"
    }
}

/** نشانگر کوچک بدهکار (سکه قرمز) کنار نام کاربری */
@Composable
private fun DebtorBadge(compact: Boolean = false) {
    val size = if (compact) 16.dp else 18.dp
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50))
            .background(GlassRed.copy(0.14f))
            .border(BorderStroke(0.8.dp, GlassRed.copy(0.32f)), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(AppIcon.Money, tint = GlassRed, size = if (compact) 10.dp else 11.dp)
    }
}

/** متنِ وضعیت برای کارت: اول وضعیت (غیرفعال/منقضی/محدود)، بعد روزِ مانده. */
@Composable
private fun cardStatusText(user: PanelUser): String = when (user.status) {
    "disabled" -> stringResource(R.string.disabled)
    "expired" -> stringResource(R.string.expired)
    "limited" -> stringResource(R.string.limited)
    "on_hold" -> stringResource(R.string.on_hold_users)
    else -> daysLeftText(user.expire)
}

@Composable
private fun StatGlassCard(icon: AppIcon, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Lg
    Box(
        modifier = modifier
            .height(82.dp)
            .clip(shape)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), shape)
            .padding(12.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isGold = accent == theme.accentPrimary || accent == DsAccent.Gold
                val iconBg = if (isGold) { if (theme.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB) } else accent.copy(0.10f)
                val iconBorder = if (isGold) { if (theme.isDark) DsAccent.Gold.copy(0.22f) else Color(0xFFFDE68A) } else accent.copy(0.18f)
                val iconTint = if (isGold) { if (theme.isDark) DsAccent.Gold else Color(0xFFCA8A04) } else accent
                Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(iconBg).border(BorderStroke(DsBorder.Hairline, iconBorder), DsRadius.Sm), contentAlignment = Alignment.Center) {
                    RoundedAppIcon(icon, tint = iconTint, size = 15.dp)
                }
                Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TechnicalContainer {
                Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.35f, targetValue = 0.65f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(DsRadius.Lg).background(theme.cardBgColor.copy(alpha = alpha)).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).height(120.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = DsRadius.Md
    Box(modifier = modifier
        .fillMaxWidth()
        .height(40.dp)
        .clip(shape)
        .background(theme.searchBgColor)
        .border(BorderStroke(DsBorder.Hairline, if (isFocused) theme.accentPrimary.copy(0.4f) else theme.borderColor), shape)
        .padding(horizontal = 12.dp)
        .onFocusChanged { isFocused = it.isFocused }
        , contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(AppIcon.Search, contentDescription = "جستجو", tint = theme.mutedColor, size = 16.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.search), color = theme.mutedColor.copy(0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accentPrimary),
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } }
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(theme.borderSubtle).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) { Text("×", color = theme.mutedColor, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TopBarHeader(
    onRefresh: () -> Unit,
    onCreateUser: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    loading: Boolean
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Users", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.18f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.30f) else Color(0xFFFDE68A)), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text("○", fontSize = 7.sp, color = Color(0xFFCA8A04))
                }
            }
            Text(stringResource(R.string.control_users_desc), fontSize = 10.sp, color = theme.mutedColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable(onClick = onRefresh), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(12.dp), color = theme.mutedColor, strokeWidth = 1.5.dp) else RoundedAppIcon(AppIcon.Refresh, contentDescription = "بروزرسانی", tint = theme.mutedColor, size = 14.dp)
            }
            Box(Modifier.size(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { onOpenThemeDialog() }, contentAlignment = Alignment.Center) {
                RoundedAppIcon(AppIcon.Settings, contentDescription = "تنظیمات", tint = theme.mutedColor, size = 14.dp)
            }
        }
    }
}

@Composable
private fun StatsCardsRow(
    totalUsers: Int,
    activeUsers: Int,
    onlineUsers: Int,
    debtorCount: Int = 0
) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // همان hierarchy پنل: شاخص‌های زنده در بالا و شمار کل در یک سطح جداگانه.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatGlassCard(icon = AppIcon.User, label = stringResource(R.string.online_users), value = "$onlineUsers", accent = GlassGreen, modifier = Modifier.weight(1f))
            StatGlassCard(icon = AppIcon.Check, label = stringResource(R.string.active_users), value = "$activeUsers", accent = theme.accentPrimary, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatGlassCard(icon = AppIcon.Users, label = stringResource(R.string.users_section), value = "$totalUsers", accent = theme.accentPrimary, modifier = Modifier.weight(1f))
            if (debtorCount > 0) {
                StatGlassCard(icon = AppIcon.Warning, label = stringResource(R.string.debtor), value = "$debtorCount", accent = GlassRed, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilterAndControlBar(currentFilter: UserFilter, onFilterChange: (UserFilter) -> Unit, currentSort: UserSort, onSortChange: (UserSort) -> Unit, viewMode: ViewMode, onViewModeChange: (ViewMode) -> Unit, debtorCount: Int = 0) {
    val theme = LocalThemeState.current
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Filter dropdown button like PasarGuard panel
        Box(Modifier.width(110.dp).height(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { showFilterSheet = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Tune, tint = theme.mutedColor, size = 13.dp)
                    Text(when(currentFilter){ UserFilter.ALL->stringResource(R.string.all); UserFilter.ACTIVE->stringResource(R.string.active); UserFilter.NEAR_LIMIT->stringResource(R.string.near_limit); UserFilter.EXPIRED->stringResource(R.string.expired); UserFilter.DISABLED->stringResource(R.string.disabled); UserFilter.DEBTOR->stringResource(R.string.debtor)}, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("▾", fontSize = 10.sp, color = theme.mutedColor)
            }
        }
        // Sort dropdown - with icon
        Box(Modifier.width(110.dp).height(32.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { showSortSheet = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Tune, tint = theme.mutedColor, size = 12.dp)
                    Text(when(currentSort){ UserSort.NAME->stringResource(R.string.name); UserSort.USAGE->stringResource(R.string.usage_sort); UserSort.EXPIRY->stringResource(R.string.expiry); UserSort.CREATED->stringResource(R.string.created)}, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Text("▾", fontSize = 10.sp, color = theme.mutedColor)
            }
        }
        // View mode compact
        Row(Modifier.clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ViewModeIcon(AppIcon.GridView, viewMode == ViewMode.GRID) { onViewModeChange(ViewMode.GRID) }
            ViewModeIcon(AppIcon.ListRows, viewMode == ViewMode.COMPACT_LIST) { onViewModeChange(ViewMode.COMPACT_LIST) }
            ViewModeIcon(AppIcon.DenseList, viewMode == ViewMode.MICRO_LIST) { onViewModeChange(ViewMode.MICRO_LIST) }
        }
    }
    if (showFilterSheet) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showFilterSheet = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.filter), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                listOf(stringResource(R.string.all) to UserFilter.ALL, stringResource(R.string.active) to UserFilter.ACTIVE, stringResource(R.string.near_limit) to UserFilter.NEAR_LIMIT, stringResource(R.string.expired) to UserFilter.EXPIRED, stringResource(R.string.disabled) to UserFilter.DISABLED, (if(debtorCount>0) stringResource(R.string.debtor) + " ($debtorCount)" else stringResource(R.string.debtor)) to UserFilter.DEBTOR).forEach { (label, f) ->
                    val sel = currentFilter == f
                    Box(Modifier.fillMaxWidth().height(40.dp).clip(DsRadius.Sm).background(if(sel) DsAccent.Gold else theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, if(sel) DsAccent.GoldDeep else theme.borderColor), DsRadius.Sm).clickable { onFilterChange(f); showFilterSheet=false }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(label, fontSize = 12.sp, fontWeight = if(sel) FontWeight.SemiBold else FontWeight.Medium, color = if(sel) Color(0xFF422006) else theme.inkColor)
                    }
                }
            }
        }
    }
    if (showSortSheet) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSortSheet = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sort), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                listOf(stringResource(R.string.name) to UserSort.NAME, stringResource(R.string.usage_sort) to UserSort.USAGE, stringResource(R.string.expiry) to UserSort.EXPIRY, stringResource(R.string.created) to UserSort.CREATED).forEach { (label, s) ->
                    val sel = currentSort == s
                    Box(Modifier.fillMaxWidth().height(40.dp).clip(DsRadius.Sm).background(if(sel) DsAccent.Gold else theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, if(sel) DsAccent.GoldDeep else theme.borderColor), DsRadius.Sm).clickable { onSortChange(s); showSortSheet=false }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(label, fontSize = 12.sp, fontWeight = if(sel) FontWeight.SemiBold else FontWeight.Medium, color = if(sel) Color(0xFF422006) else theme.inkColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Sm
    Box(modifier = Modifier
        .height(32.dp)
        .clip(shape)
        .background(if (selected) DsAccent.Gold else theme.searchBgColor)
        .border(BorderStroke(DsBorder.Hairline, if (selected) DsAccent.GoldDeep else theme.borderColor), shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF422006) else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Sm
    Box(modifier = Modifier.clip(shape).background(if (selected) DsAccent.Gold else Color.Transparent).border(BorderStroke(DsBorder.Hairline, if (selected) DsAccent.GoldDeep else Color.Transparent), shape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, color = if (selected) Color(0xFF422006) else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ViewModeIcon(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Sm
    Box(modifier = Modifier.size(32.dp).clip(shape).background(if (selected) DsAccent.Gold else Color.Transparent).border(BorderStroke(DsBorder.Hairline, if (selected) DsAccent.GoldDeep else Color.Transparent), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        RoundedAppIcon(icon, tint = if (selected) Color(0xFF422006) else theme.mutedColor, size = 18.dp)
    }
}

@Composable
private fun CheckboxIcon(selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val isDark = theme.isDark
    val bg = if (selected) DsAccent.Gold else if (isDark) Color(0xFF383842) else Color.White
    val borderCol = if (selected) DsAccent.GoldDeep else if (isDark) Color(0xFF8E8C98) else Color(0xFFB8BBC2)
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(DsRadius.Xs)
            .background(bg)
            .border(BorderStroke(DsBorder.Hairline, borderCol), DsRadius.Xs)
            .semantics { contentDescription = if (selected) "لغو انتخاب" else "انتخاب" }
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            // تیک با Canvas رسم می‌شود تا به baseline فونت وابسته نباشد و دقیقاً وسط مربع بماند.
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = size.minDimension * .14f, cap = StrokeCap.Round)
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .23f, size.height * .52f),
                    end = Offset(size.width * .43f, size.height * .71f),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .43f, size.height * .71f),
                    end = Offset(size.width * .78f, size.height * .30f),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxuryGridCard(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onCopySub: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}, debtorInfo: DebtorInfo? = null) {
    // PasarGuard-faithful: compact, no glass, thin progress, subtle status. Matches reference list screenshot.
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0L) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt().coerceIn(0,100) else 0
    val actualProgress = if (user.dataLimit > 0L) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val displayProgress = if (user.dataLimit == 0L) 0f else actualProgress
    val progressColor = when { user.dataLimit <= 0L -> Color(0xFF9CA3AF); progressPercent < 70 -> Color(0xFF16A34A); progressPercent < 90 -> Color(0xFFD97706); else -> Color(0xFFDC2626) }
    val shape = DsRadius.Lg
    val statusColor = Color.Transparent

    // نمای گرید: کارت شیشه‌ای با سایهٔ نرم و مرز ظریف design system جدید.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) { if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB) } else theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) { if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.25f) else Color(0xFFFDE68A) } else theme.borderColor), shape)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MrmText(
                            text = user.username,
                            fontSize = 12.sp,
                            fontWeight = DsFont.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            isTechnical = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (debtorInfo != null) DebtorBadge(compact = true)
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(statusColor))
                    }
                    MrmText(
                        text = lastSeenShort(user.onlineAt, user.isOnline),
                        fontSize = 10.sp,
                        color = if (user.isOnline) GlassGreen else theme.mutedColor,
                        maxLines = 1,
                        isTechnical = true
                    )
                }
                if (user.note?.isNotBlank() == true) Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(DsSemantic.Info.copy(0.16f)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Note, tint = DsSemantic.Info, size = 11.dp) }
            }
            MrmText(
                text = if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / " + stringResource(R.string.unlimited) else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}", 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                isTechnical = true
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${cardStatusText(user)}", fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.weight(1f), maxLines = 1)
                MrmText(
                    text = if (user.dataLimit == 0L) "∞" else "$progressPercent%", 
                    fontSize = 9.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = progressColor,
                    isTechnical = true
                )
            }
            // thin PG progress — 4dp, rounded, green fill, neutral track
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                if (displayProgress > 0f) Box(Modifier.fillMaxWidth(displayProgress).fillMaxHeight().clip(RoundedCornerShape(50)).background(progressColor))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconGridAction(AppIcon.Copy, contentDesc = "کپی لینک اشتراک") { onCopySub(user) }
                IconGridAction(AppIcon.Qr, contentDesc = "نمایش QR") { onQrClick(user) }
                Box(Modifier.height(24.dp).clip(DsRadius.Sm).background(if (user.isOnline) GlassGreen.copy(0.12f) else Color.Gray.copy(0.10f)).border(BorderStroke(DsBorder.Hairline, if (user.isOnline) GlassGreen.copy(0.18f) else Color.Gray.copy(0.12f)), DsRadius.Sm).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(if (user.isOnline) stringResource(R.string.online) else stringResource(R.string.offline), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (user.isOnline) GlassGreen else Color.Gray)
                }
                if (user.groupNames.isNotEmpty()) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF8B5CF6).copy(0.10f)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                        Text(user.groupNames.first(), fontSize = 10.sp, color = Color(0xFF8B5CF6), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineBadge(user: PanelUser) {
    val color = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(4.5.dp))
            .background(color)
            .border(BorderStroke(DsBorder.Hairline, color.copy(alpha = 0.55f)), DsRadius.Xs)
    )
}

@Composable
private fun UserStatusBadge(user: PanelUser, modifier: Modifier = Modifier, compact: Boolean = false) {
    val theme = LocalThemeState.current
    val (label, color) = when (user.status) {
        "active" -> stringResource(R.string.active) to GlassGreen
        "disabled" -> stringResource(R.string.disabled) to Color(0xFF8A8A8A)
        "expired" -> stringResource(R.string.expired) to GlassRed
        "limited" -> stringResource(R.string.limited) to GlassAmber
        "on_hold" -> stringResource(R.string.on_hold_users) to DsSemantic.Violet
        else -> cardStatusText(user) to theme.mutedColor
    }
    Box(
        modifier.height(if (compact) 17.dp else 22.dp).clip(RoundedCornerShape(if (compact) 5.dp else 7.dp))
            .background(color.copy(alpha = 0.13f))
            .border(BorderStroke(if (compact) 0.7.dp else 0.8.dp, color.copy(alpha = 0.25f)), RoundedCornerShape(if (compact) 5.dp else 7.dp))
            .padding(horizontal = if (compact) 3.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun RowAction(label: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 23.dp, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.height(height).clip(RoundedCornerShape(6.dp))
        .background(theme.searchBgColor)
        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
        .clickable(onClick = onClick).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
    }
}

private fun copySubscription(context: Context, user: PanelUser) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
    android.widget.Toast.makeText(context, "لینک اشتراک کپی شد", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun LargeRowAction(label: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(Modifier.height(38.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable(onClick = onClick).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
    }
}

@Composable
private fun LargeStat(label: String, value: String, valueColor: Color? = null, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor, maxLines = 1)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor ?: theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UserCardAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1)
    }
}

@Composable
private fun IconCardAction(icon: AppIcon, modifier: Modifier = Modifier, contentDesc: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
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

@Composable
private fun IconRowAction(icon: AppIcon, modifier: Modifier = Modifier, contentDesc: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier.clip(DsRadius.Sm)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 13.dp)
    }
}

@Composable
private fun IconGridAction(icon: AppIcon, contentDesc: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 14.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxuryCompactRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onCopySub: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}, debtorInfo: DebtorInfo? = null) {
    val theme = LocalThemeState.current
    val actualProgress = if (user.dataLimit > 0L) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val shownProgress = if (user.dataLimit > 0L) actualProgress else .035f
    val progressPercent = (actualProgress * 100).roundToInt()
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen
        progressPercent < 90 -> GlassAmber
        else -> GlassRed
    }
    val traffic = if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / " + stringResource(R.string.unlimited) else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}"
    val statusColor = when { user.status == "active" -> GlassGreen; user.status == "disabled" -> Color(0xFF8A8A8A); user.status == "expired" -> GlassRed; user.status == "limited" -> GlassAmber; user.status == "on_hold" -> DsSemantic.Violet; else -> theme.mutedColor }
    val shape = DsRadius.Lg

    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) { if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB) } else theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) { if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.25f) else Color(0xFFFDE68A) } else theme.borderColor), shape)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MrmText(
                        user.username,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        isTechnical = true
                    )
                    Text(
                        text = lastSeenShort(user.onlineAt, user.isOnline),
                        fontSize = 10.sp,
                        color = if (user.isOnline) GlassGreen else theme.mutedColor
                    )
                }
                UserStatusBadge(user, Modifier.width(42.dp))
                if (debtorInfo != null) DebtorBadge()
                IconCardAction(AppIcon.Copy, Modifier.size(34.dp), contentDesc = "کپی") { onCopySub(user) }
                IconCardAction(AppIcon.Qr, Modifier.size(34.dp), contentDesc = "QR") { onQrClick(user) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.traffic_usage_label), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                    MrmText(traffic, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.remaining_credit), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                    MrmText(daysLeftText(user.expire), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, isTechnical = false)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                    if (shownProgress > 0.01f) Box(Modifier.fillMaxWidth(shownProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(50)))
                }
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxuryMicroRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onCopySub: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}, debtorInfo: DebtorInfo? = null) {
    val theme = LocalThemeState.current
    val actualProgress = if (user.dataLimit > 0L) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else .035f
    val progressColor = when { user.dataLimit <= 0L || actualProgress < .70f -> GlassGreen; actualProgress < .90f -> GlassAmber; else -> GlassRed }
    val traffic = "${formatBytes(user.usedTraffic)}/${if (user.dataLimit == 0L) "∞" else formatBytes(user.dataLimit)}"
    val statusColor = when { user.status == "active" -> GlassGreen; user.status == "disabled" -> Color(0xFF8A8A8A); user.status == "expired" -> GlassRed; user.status == "limited" -> GlassAmber; user.status == "on_hold" -> DsSemantic.Violet; else -> theme.mutedColor }
    val shape = DsRadius.Lg

    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) { if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB) } else theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) { if (LocalThemeState.current.isDark) DsAccent.Gold.copy(0.25f) else Color(0xFFFDE68A) } else theme.borderColor), shape)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckboxIcon(selected = selected, onToggle = onSelectToggle)
            Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MrmText(user.username, fontSize = 11.sp, fontWeight = DsFont.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                MrmText(lastSeenShort(user.onlineAt, user.isOnline), fontSize = 10.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, maxLines = 1, isTechnical = true)
            }
            UserStatusBadge(user, Modifier.width(30.dp), compact = true)
            if (debtorInfo != null) DebtorBadge(compact = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MrmText(traffic, fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1, isTechnical = true)
                    MrmText(daysLeftText(user.expire), fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, isTechnical = false)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                    if (actualProgress > 0.01f) Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(50)))
                }
            }
            IconRowAction(AppIcon.Copy, Modifier.size(24.dp), contentDesc = "کپی") { onCopySub(user) }
            IconRowAction(AppIcon.Qr, Modifier.size(24.dp), contentDesc = "QR") { onQrClick(user) }
        }
    }
}

@Composable
fun DebtorEditDialog(
    user: PanelUser,
    existing: DebtorInfo?,
    currency: String = "تومان",
    onDismiss: () -> Unit,
    onSave: (amount: Long, notes: String) -> Unit,
    onClear: () -> Unit
) {
    val theme = LocalThemeState.current
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val amountLong = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (existing != null) "ویرایش بدهی ${user.username}" else "ثبت بدهکار برای ${user.username}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                if (existing != null) {
                    Text("ثبت شده: ${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(existing.markedAt))}", fontSize = 10.sp, color = theme.mutedColor)
                }
                Box(Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(currency, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        androidx.compose.foundation.text.BasicTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            textStyle = TextStyle(color = theme.inkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (amountText.isEmpty()) Text("مبلغ بدهی (مثلاً 50000)", color = theme.mutedColor.copy(0.6f), fontSize = 12.sp)
                                inner()
                            }
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = notes,
                        onValueChange = { notes = it.take(200) },
                        singleLine = false,
                        textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (notes.isEmpty()) Text("یادداشت بدهی (اختیاری)", color = theme.mutedColor.copy(0.6f), fontSize = 11.sp)
                            inner()
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    if (existing != null) {
                        PrimaryButton("تسویه ✅", onClick = { onClear() }, modifier = Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
                PrimaryButton(
                    text = if (existing != null) "ذخیره تغییرات" else "ثبت بدهکار",
                    enabled = amountLong > 0L,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSave(amountLong, notes) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UsersScreen(
    session: Session,
    onLogout: () -> Unit,
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    monitoringSettings: com.mrm.pgmanager.data.model.MonitoringSettings = com.mrm.pgmanager.data.model.MonitoringSettings(),
    onMonitoringChange: (com.mrm.pgmanager.data.model.MonitoringSettings) -> Unit = {},
    isAppLockEnabled: Boolean = false,
    onAppLockChange: (Boolean) -> Unit = {},
    appLockTimeout: Int = 0,
    onLockTimeoutChange: (Int) -> Unit = {},
    onSwitchAccount: (Session) -> Unit = {},
    onAddAccount: () -> Unit = {},
    deepLinkUsername: String? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineAt by remember { mutableStateOf<Long?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var qrUser by remember { mutableStateOf<PanelUser?>(null) }
    var onlineCount by remember { mutableStateOf(0) }
    var lastUserStates by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(UserSort.CREATED) }
    var viewMode by remember { mutableStateOf(store.readViewMode()) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var bulkCreateOpen by remember { mutableStateOf(false) }
    var exportChooserOpen by remember { mutableStateOf(false) }
    var exportPending by remember { mutableStateOf<Pair<String, List<PanelUser>>?>(null) }
    var selectedUserIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkTemplateDialog by remember { mutableStateOf(false) }
    var pendingBulk by remember { mutableStateOf<PendingBulk?>(null) }
    var quickActionUser by remember { mutableStateOf<PanelUser?>(null) }
    var quickTemplateUser by remember { mutableStateOf<PanelUser?>(null) }
    var quickTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var quickTemplatesLoading by remember { mutableStateOf(true) }
    var quickTemplatesFailed by remember { mutableStateOf(false) }

    var debtors by remember { mutableStateOf<Map<String, DebtorInfo>>(store.readDebtors()) }
    var debtorDialogUser by remember { mutableStateOf<PanelUser?>(null) }
    var invoiceDialogUser by remember { mutableStateOf<PanelUser?>(null) }
    var resetExpiryTarget by remember { mutableStateOf<PanelUser?>(null) }

    fun reloadDebtors() { debtors = store.readDebtors() }
    fun fetchSub(user: PanelUser, onResult: (PanelUser) -> Unit) {
        scope.launch {
            runCatching { PanelApi.user(session, user.username) }.onSuccess(onResult)
                .onFailure { android.widget.Toast.makeText(context, "دریافت لینک اشتراک ناموفق بود", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    fun copySubWithFetch(user: PanelUser) {
        if (user.subUrl.isNotBlank()) copySubscription(context, user)
        else fetchSub(user) { copySubscription(context, it) }
    }
    fun qrWithFetch(user: PanelUser) {
        if (user.subUrl.isNotBlank()) qrUser = user
        else fetchSub(user) { qrUser = it }
    }
    val debtorsForCurrentPanel = remember(debtors, session.baseUrl) { debtors.values.filter { it.baseUrl == session.baseUrl } }
    val debtorByUsername = remember(debtorsForCurrentPanel) { debtorsForCurrentPanel.associateBy { it.username } }
    val debtorCount = debtorsForCurrentPanel.size

    val density = androidx.compose.ui.platform.LocalDensity.current
    val statsCardsHeightPx = remember { mutableStateOf(0f) }
    val totalHeaderHeightPx = remember { mutableStateOf(0f) }

    val fallbackStatsPx = remember(density) { with(density) { 104.dp.toPx() } }
    val headerHeight = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else fallbackStatsPx
    val fallbackTotalDp = 242.dp
    val totalHeaderDp = if (totalHeaderHeightPx.value > 0f) with(density) { totalHeaderHeightPx.value.toDp() } else fallbackTotalDp
    val scrollOffset = remember { mutableStateOf(0f) }

    fun load(resetHeader: Boolean = true, silent: Boolean = false) {
        scope.launch {
            if (!silent) loading = true
            error = null
            runCatching {
                val list = PanelApi.users(session)
                users = list; onlineCount = list.count { it.isOnline }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.saveUsersCache(list) }
                offlineAt = null
                val settings = store.readMonitoringSettings()
                val nextStates = list.associate { u ->
                    val usage = if (u.dataLimit > 0L) ((u.usedTraffic * 100L) / u.dataLimit).toInt() else 0
                    val nearExpiry = DateLogic.isNearExpiry(u.expire, settings.nearExpiryDays)
                    u.id to "${u.status}|$usage|$nearExpiry"
                }
                if (lastUserStates.isNotEmpty() && settings.notificationsEnabled) {
                    list.forEach { u ->
                        val previous = lastUserStates[u.id] ?: return@forEach
                        val current = nextStates[u.id] ?: return@forEach
                        if (previous == current) return@forEach
                        fun notify(id: Int, title: String, text: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_EVENTS, title, text)
                        if (settings.notifyLimited && u.status == "limited" && !previous.startsWith("limited")) notify(("limited" + u.id).hashCode(), "کاربر محدود شد", "${u.username} به سقف حجم رسیده است")
                        if (settings.notifyExpired && u.status == "expired" && !previous.startsWith("expired")) notify(("expired" + u.id).hashCode(), "اشتراک منقضی شد", "اشتراک ${u.username} منقضی شده است")
                        val usage = if (u.dataLimit > 0L) ((u.usedTraffic * 100L) / u.dataLimit).toInt() else 0
                        val oldUsage = previous.split("|").getOrNull(1)?.toIntOrNull() ?: 0
                        if (settings.notifyNearLimit && usage >= settings.nearLimitPercent && oldUsage < settings.nearLimitPercent) notify(("near_limit" + u.id).hashCode(), "هشدار مصرف", "${u.username} به $usage٪ مصرف حجم رسیده است")
                        val nearExpiry = current.substringAfterLast("|").toBoolean()
                        val wasNearExpiry = previous.substringAfterLast("|").toBoolean()
                        if (settings.notifyNearExpiry && nearExpiry && !wasNearExpiry) notify(("near_expire" + u.id).hashCode(), "هشدار انقضا", "اشتراک ${u.username} نزدیک به انقضا است")
                    }
                }
                lastUserStates = nextStates
                if (resetHeader) scrollOffset.value = 0f
            }.onFailure {
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                } else {
                    val cache = if (monitoringSettings.offlineCacheEnabled) store.readUsersCache() else null
                    if (cache != null) {
                        users = cache.first
                        onlineCount = 0
                        offlineAt = cache.second
                        error = null
                    } else if (!silent) {
                        error = it.message
                    }
                }
            }
            loading = false
        }
    }
    fun runAction(notification: Pair<String, String>? = null, action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }.onFailure {
                error = it.message
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                } else {
                    android.widget.Toast.makeText(context, "خطا: ${it.message?.take(120)}", android.widget.Toast.LENGTH_LONG).show()
                }
            }.onSuccess { notification?.let { (title, message) -> val settings = store.readMonitoringSettings(); if (settings.notificationsEnabled && settings.notifyUserActions) NotificationHelper.post(context, (title + message).hashCode(), NotificationHelper.CHANNEL_EVENTS, title, message) }; load() }
        }
    }
    fun exportFileName(format: String) = "mrm-users-selected-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".$format"
    fun writeExport(uri: android.net.Uri?) {
        val payload = exportPending; exportPending = null
        if (uri == null || payload == null) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                val out = context.contentResolver.openOutputStream(uri) ?: error("no stream")
                out.use { it.write(if (payload.first == "json") com.mrm.pgmanager.utils.usersToJson(payload.second).toByteArray(Charsets.UTF_8) else com.mrm.pgmanager.utils.usersToCsv(payload.second).toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, if (ok) "فایل با موفقیت ذخیره شد" else "خطا در ذخیرهٔ فایل", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { writeExport(it) }
    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { writeExport(it) }
    fun beginExport(format: String) {
        val chosen = users.filter { selectedUserIds.contains(it.id) }
        if (chosen.isEmpty()) { android.widget.Toast.makeText(context, "ابتدا کاربرها را انتخاب کن", android.widget.Toast.LENGTH_SHORT).show(); return }
        exportPending = format to chosen
        if (format == "json") exportJsonLauncher.launch(exportFileName("json")) else exportCsvLauncher.launch(exportFileName("csv"))
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(deepLinkUsername, users) {
        val name = deepLinkUsername ?: return@LaunchedEffect
        if (users.isEmpty()) return@LaunchedEffect
        users.find { it.username == name }?.let {
            query = ""
            currentFilter = UserFilter.ALL
            selectedUser = it
        }
        onDeepLinkHandled()
    }
    var inForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            inForeground = event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(session, monitoringSettings.autoRefreshEnabled, monitoringSettings.refreshWhileAppOpen, monitoringSettings.refreshIntervalSeconds) {
        if (monitoringSettings.autoRefreshEnabled && monitoringSettings.refreshWhileAppOpen) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (inForeground) load(resetHeader = false, silent = true)
                kotlinx.coroutines.delay(monitoringSettings.refreshIntervalSeconds.coerceIn(5, 3600) * 1_000L)
            }
        }
    }

    val processedUsers = remember(users, query, currentFilter, currentSort, monitoringSettings.nearLimitPercent, debtorByUsername) {
        val q = query.trim()
        var list = if (q.isEmpty()) users else users.filter {
            it.username.contains(q, ignoreCase = true) ||
            (it.note ?: "").contains(q, ignoreCase = true)
        }
        list = when (currentFilter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.status == "active" }
            UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0L) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= monitoringSettings.nearLimitPercent / 100.0 }
            UserFilter.EXPIRED -> list.filter { val p = if (it.dataLimit > 0L) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= 1.0 || it.status == "expired" || it.status == "limited" }
            UserFilter.DISABLED -> list.filter { it.status == "disabled" }
            UserFilter.DEBTOR -> list.filter { debtorByUsername.containsKey(it.username) }
        }
        when (currentSort) {
            UserSort.NAME -> list.sortedBy { it.username.lowercase() }
            UserSort.USAGE -> list.sortedByDescending { it.usedTraffic }
            UserSort.EXPIRY -> list.sortedBy { it.expire ?: "9999" }
            UserSort.CREATED -> list.sortedByDescending { it.id }
        }
    }

    val nestedScrollConnection = remember(headerHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (headerHeight <= 0f) return Offset.Zero

                val delta = -available.y
                val current = scrollOffset.value
                if (delta > 0f && current < headerHeight) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                else if (delta < 0f && current > 0f) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return Offset.Zero
            }
        }
    }

    Scaffold(containerColor = Color.Transparent, floatingActionButton = {
        if (selectedUserIds.isEmpty()) {
            val fabShape = DsRadius.Lg
            val fabInteraction = remember { MutableInteractionSource() }
            val isFabPressed by fabInteraction.collectIsPressedAsState()
            val fabScale by animateFloatAsState(targetValue = if (isFabPressed) 0.95f else 1f, animationSpec = DsMotion.ScaleSpring, label = "fabScale")
            Box(
                modifier = Modifier
                    .padding(bottom = 72.dp, end = 4.dp)
                    .size(52.dp)
                    .graphicsLayer(scaleX = fabScale, scaleY = fabScale)
                    .clip(fabShape)
                    .background(DsAccent.Gold)
                    .border(BorderStroke(DsBorder.Hairline, DsAccent.GoldDeep), fabShape)
                    .clickable(interactionSource = fabInteraction, indication = null) { createMenuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                RoundedAppIcon(AppIcon.UserAdd, tint = DsAccent.OnAccent, size = DsComponent.IconLg)
            }
        }
    }) { padding ->
        val topInsets = padding.calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            val scrollOffsetDp = with(density) { scrollOffset.value.toDp() }
            val listTopPad = (totalHeaderDp - scrollOffsetDp).coerceAtLeast(0.dp) + topInsets + 4.dp
            val ptrState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { load() },
                modifier = Modifier.fillMaxSize(),
                state = ptrState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        isRefreshing = loading,
                        state = ptrState,
                        containerColor = themeState.cardSurfaceColor,
                        color = com.mrm.pgmanager.ui.designsystem.DsAccent.Gold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = listTopPad)
                    )
                }
            ) {
                when {
                    loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(6) { SkeletonCard() } }
                    error != null -> Box(Modifier.fillMaxWidth().padding(top = listTopPad).clip(DsRadius.Lg).background(themeState.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.18f)), DsRadius.Lg).padding(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("خطا", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                            Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                            SecondaryButton("تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    processedUsers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = listTopPad).clip(DsRadius.Lg).background(themeState.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Lg).padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(56.dp).clip(DsRadius.Md).background(themeState.searchBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Md), contentAlignment = Alignment.Center) {
                                com.mrm.pgmanager.ui.components.RoundedAppIcon(com.mrm.pgmanager.ui.components.AppIcon.Search, tint = themeState.mutedColor, size = 28.dp)
                            }
                            Text(stringResource(R.string.no_user_found), fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                            Text(if (query.isNotBlank() || currentFilter != com.mrm.pgmanager.data.model.UserFilter.ALL) stringResource(R.string.clear_filter_or_create) else stringResource(R.string.create_first_user), fontSize = 11.sp, color = themeState.mutedColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (query.isNotBlank() || currentFilter != com.mrm.pgmanager.data.model.UserFilter.ALL) {
                                    com.mrm.pgmanager.ui.components.SecondaryButton(stringResource(R.string.clear_filter), onClick = { query = ""; currentFilter = com.mrm.pgmanager.data.model.UserFilter.ALL }, modifier = Modifier.height(36.dp))
                                }
                                com.mrm.pgmanager.ui.components.PrimaryButton(stringResource(R.string.create_user), onClick = { createUser = true })
                            }
                        }
                    }
                    else -> androidx.compose.animation.AnimatedContent(targetState = viewMode, label = "viewModeSwitch") { mode ->
                        when (mode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            items(processedUsers, key = { it.id }) { user ->
                                Box(Modifier.animateItem()) { LuxuryGridCard(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrWithFetch(it) }, onCopySub = { copySubWithFetch(it) }, onLongClick = { quickActionUser = user }, debtorInfo = debtorByUsername[user.username]) }
                            }
                        }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            items(processedUsers, key = { it.id }) { user ->
                                Box(Modifier.animateItem()) { LuxuryCompactRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrWithFetch(it) }, onCopySub = { copySubWithFetch(it) }, onLongClick = { quickActionUser = user }, debtorInfo = debtorByUsername[user.username]) }
                            }
                        }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            items(processedUsers, key = { it.id }) { user ->
                                Box(Modifier.animateItem()) { LuxuryMicroRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrWithFetch(it) }, onCopySub = { copySubWithFetch(it) }, onLongClick = { quickActionUser = user }, debtorInfo = debtorByUsername[user.username]) }
                            }
                        }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        if (scrollOffset.value == 0f && coords.size.height > 0) {
                            val h = (coords.size.height.toFloat() - with(density) { topInsets.toPx() }).coerceAtLeast(0f)
                            if (totalHeaderHeightPx.value != h) {
                                totalHeaderHeightPx.value = h
                            }
                        }
                    }
                    .background(themeState.chromeBgColor)
                    .border(BorderStroke(DsBorder.Hairline, themeState.borderColor))
                    .padding(top = topInsets)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                TopBarHeader(onRefresh = { load() }, onCreateUser = { createUser = true }, onOpenThemeDialog = { showThemeDialog = true }, loading = loading)

                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (scrollOffset.value == 0f && coords.size.height > 0) {
                                if (statsCardsHeightPx.value != coords.size.height.toFloat()) {
                                    statsCardsHeightPx.value = coords.size.height.toFloat()
                                }
                            }
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else placeable.height.toFloat()
                            val progress = if (maxH > 0f) (scrollOffset.value / maxH).coerceIn(0f, 1f) else 0f
                            val currentH = (placeable.height * (1f - progress)).roundToInt().coerceAtLeast(0)
                            layout(placeable.width, currentH) {
                                placeable.placeRelative(0, (-progress * placeable.height * 0.38f).roundToInt())
                            }
                        }
                        .graphicsLayer {
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else 1f
                            val progress = (scrollOffset.value / maxH).coerceIn(0f, 1f)
                            this.alpha = (1f - progress * 1.3f).coerceIn(0f, 1f)
                        }
                ) {
                    StatsCardsRow(totalUsers = users.size, activeUsers = users.count { it.status == "active" }, onlineUsers = onlineCount, debtorCount = debtorCount)
                }

                Spacer(Modifier.height(6.dp))
                GlassSearchBar(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(8.dp))
                FilterAndControlBar(currentFilter = currentFilter, onFilterChange = { currentFilter = it }, currentSort = currentSort, onSortChange = { currentSort = it }, viewMode = viewMode, onViewModeChange = { viewMode = it; store.saveViewMode(it) }, debtorCount = debtorCount)
                offlineAt?.let { cachedAt ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(DsRadius.Sm).background(GlassAmber.copy(.12f)).border(BorderStroke(DsBorder.Hairline, GlassAmber.copy(.30f)), DsRadius.Sm).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassAmber, size = 14.dp)
                        Text(stringResource(R.string.offline_data, java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassAmber, maxLines = 1)
                    }
                }
            }

            if (selectedUserIds.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 68.dp, start = 12.dp, end = 12.dp)
                ) {
                    BulkActionsBar(
                        selectedCount = selectedUserIds.size,
                        onClear = { selectedUserIds = emptySet() },
                        onSelectAll = { selectedUserIds = processedUsers.map { it.id }.toSet() },
                        onExport = { exportChooserOpen = true },
                        onDelete = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = "حذف ${ids.size} کاربر؟", message = "این کاربرها برای همیشه حذف می‌شوند و غیرقابل‌بازگشت هستند.", confirmLabel = "حذف", danger = true, action = { runAction(notification = "حذف گروهی" to "${ids.size} کاربر حذف شدند") { PanelApi.bulkDeleteUsers(session, ids) } }) },
                        onResetUsage = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = "ریست حجم ${ids.size} کاربر؟", message = "مصرفِ این کاربرها صفر می‌شود.", confirmLabel = "تایید", action = { runAction(notification = "ریست حجم گروهی" to "مصرف ${ids.size} کاربر صفر شد") { PanelApi.bulkResetUsersUsage(session, ids) } }) },
                        onDisable = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = "غیرفعال‌سازی ${ids.size} کاربر؟", message = "این کاربرها غیرفعال می‌شوند و اتصالشان قطع می‌شود.", confirmLabel = "تایید", action = { runAction(notification = "غیرفعال‌سازی گروهی" to "${ids.size} کاربر غیرفعال شدند") { PanelApi.bulkDisableUsers(session, ids) } }) },
                        onEnable = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = "فعال‌سازی ${ids.size} کاربر؟", message = "این کاربرها فعال می‌شوند.", confirmLabel = "تایید", action = { runAction(notification = "فعال‌سازی گروهی" to "${ids.size} کاربر فعال شدند") { PanelApi.bulkEnableUsers(session, ids) } }) },
                        onApplyTemplate = {
                            showBulkTemplateDialog = true
                        }
                    )
                }
            }
        }
    }

    quickTemplateUser?.let { u ->
        LaunchedEffect(u) {
            quickTemplatesLoading = true; quickTemplatesFailed = false
            var list: List<UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            list?.let { quickTemplates = it } ?: run { quickTemplatesFailed = true }
            quickTemplatesLoading = false
        }
        com.mrm.pgmanager.ui.dialogs.BulkApplyTemplateDialog(
            templates = quickTemplates,
            selectedCount = 1,
            onDismiss = { quickTemplateUser = null },
            onApply = { templateId, note ->
                val id = u.id
                quickTemplateUser = null
                runAction { PanelApi.bulkApplyTemplate(session, setOf(id), templateId, note) }
            },
            isLoading = quickTemplatesLoading,
            loadFailed = quickTemplatesFailed
        )
    }

    if (showBulkTemplateDialog) {
        var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
        var templatesLoading by remember { mutableStateOf(true) }
        var templatesFailed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            var list: List<UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            list?.let { templates = it } ?: run { templatesFailed = true }
            templatesLoading = false
        }
        com.mrm.pgmanager.ui.dialogs.BulkApplyTemplateDialog(
            templates = templates,
            selectedCount = selectedUserIds.size,
            onDismiss = { showBulkTemplateDialog = false },
            onApply = { templateId, note ->
                val ids = selectedUserIds.toSet()
                selectedUserIds = emptySet()
                showBulkTemplateDialog = false
                runAction { PanelApi.bulkApplyTemplate(session, ids, templateId, note) }
            },
            isLoading = templatesLoading,
            loadFailed = templatesFailed
        )
    }

    pendingBulk?.let { p ->
        ConfirmActionDialog(
            title = p.title,
            message = p.message,
            confirmLabel = p.confirmLabel,
            danger = p.danger,
            onDismiss = { pendingBulk = null },
            onConfirm = { p.action(); pendingBulk = null }
        )
    }

    quickActionUser?.let { u ->
        val isDebtor = debtorByUsername.containsKey(u.username)
        QuickActionSheet(
            user = u,
            onDismiss = { quickActionUser = null },
            onUseTemplate = { quickTemplateUser = u },
            onToggle = { runAction(notification = "وضعیت کاربر" to "وضعیت ${u.username} تغییر کرد") { PanelApi.setDisabled(session, u.username, u.status != "disabled") } },
            onCopySub = { copySubWithFetch(u) },
            onQr = { qrUser = u },
            onEdit = { selectedUser = u },
            onResetUsage = { runAction(notification = "ریست حجم" to "مصرف ${u.username} صفر شد") { PanelApi.resetUsage(session, u.username) } },
            onResetExpiry = { resetExpiryTarget = u },
            onDelete = { deleteUser = u },
            onDebtor = { debtorDialogUser = u },
            isDebtor = isDebtor,
            onInvoice = { invoiceDialogUser = u }
        )
    }

    if (showThemeDialog) {
        ThemeEditorDialog(
            themeState = themeState,
            isAppLockEnabled = isAppLockEnabled,
            onDismiss = { showThemeDialog = false },
            onThemeChange = onThemeChange,
            onAppLockChange = onAppLockChange,
            monitoringSettings = monitoringSettings,
            onMonitoringChange = onMonitoringChange,
            appVersion = BuildConfig.VERSION_NAME,
            session = session,
            onLogout = { onLogout(); showThemeDialog = false },
            appLockTimeout = appLockTimeout,
            onLockTimeoutChange = onLockTimeoutChange,
            onSwitchAccount = { acc -> showThemeDialog = false; onSwitchAccount(acc) },
            onAddAccount = { showThemeDialog = false; onAddAccount() }
        )
    }
    selectedUser?.let { user ->
        val dInfo = debtorByUsername[user.username]
        UserDetailsDialog(
            user = user,
            onDismiss = { selectedUser = null },
            onSave = { limitGb, expireShamsi ->
                selectedUser = null; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
            },
            onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } },
            onDelete = { deleteUser = user; selectedUser = null },
            onResetUsage = {
                selectedUser = null; runAction(notification = "ریست حجم" to "مصرف ${user.username} صفر شد") { PanelApi.resetUsage(session, user.username) }
            },
            onResetExpiry = { days ->
                selectedUser = null; runAction(notification = "ریست زمان" to "زمان ${user.username} به $days روز ریست شد") {
                    val newExpire = LocalDate.now().plusDays(days.toLong()).toString()
                    PanelApi.modifyUser(session, user.username, user.dataLimit.toDouble() / 1073741824.0, newExpire, user.note ?: "", user.hwidLimit, user.groupIds)
                }
            },
            onApplyTemplate = { templateId, note ->
                selectedUser = null; runAction { PanelApi.bulkApplyTemplate(session, setOf(user.id), templateId, note) }
            },
            session = session,
            debtorInfo = dInfo,
            onMarkDebtor = { selectedUser = null; debtorDialogUser = user },
            onClearDebt = {
                val wasAutoDisabled = dInfo?.autoDisabled ?: false
                store.removeDebtor(session.baseUrl, user.username)
                reloadDebtors()
                selectedUser = null
                android.widget.Toast.makeText(context, "بدهی تسویه شد", android.widget.Toast.LENGTH_SHORT).show()
                if (wasAutoDisabled) {
                    scope.launch {
                        runCatching { PanelApi.setDisabled(session, user.username, false) }.onSuccess { load() }
                    }
                }
            },
            onInvoice = {
                invoiceDialogUser = user
                selectedUser = null
            }
        )
    }
    if (createMenuOpen) {
        Dialog(onDismissRequest = { createMenuOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(themeState.dialogBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ساخت کاربر", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                SettingsActionRow("ساخت تکی", "یک کاربر جدید با فرم کامل", AppIcon.UserAdd, themeState.accentPrimary) { createMenuOpen = false; createUser = true }
                SettingsActionRow("ساخت گروهی", "چند کاربر هم‌زمان با الگوی نام، از تمپلت یا دستی", AppIcon.Users, GlassGreen) { createMenuOpen = false; bulkCreateOpen = true }
                SecondaryButton("انصراف", onClick = { createMenuOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (bulkCreateOpen) {
        BulkCreateUsersDialog(session = session, onDismiss = { bulkCreateOpen = false }, onFinished = { n -> bulkCreateOpen = false; if (n > 0) load(resetHeader = false, silent = true) })
    }
    if (exportChooserOpen) {
        Dialog(onDismissRequest = { exportChooserOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(themeState.dialogBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("خروجی ${selectedUserIds.size} کاربر", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text("فرمت فایل را انتخاب کن؛ سپس محل ذخیره‌سازی پرسیده می‌شود.", fontSize = 10.sp, color = themeState.mutedColor)
                SettingsActionRow("خروجی CSV", "مناسب اکسل و گزارش‌گیری", AppIcon.Download, GlassGreen) { exportChooserOpen = false; beginExport("csv") }
                SettingsActionRow("خروجی JSON", "مناسب برنامه‌نویسی و بکاپ", AppIcon.Download, themeState.accentPrimary) { exportChooserOpen = false; beginExport("json") }
                SecondaryButton("انصراف", onClick = { exportChooserOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (createUser) UserEditorDialog(initial = null, onDismiss = { createUser = false }, onSave = { limitGb, expireShamsi ->
        createUser = false; runAction(notification = "کاربر جدید" to "${limitGb.username} ساخته شد") { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.createUser(session, limitGb.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
    }, onToggle = null, onDelete = null, onResetUsage = null, onResetExpiry = null, onSaveWithTemplate = { username, templateId, note ->
        createUser = false; runAction(notification = "کاربر جدید" to "$username از تمپلت ساخته شد") { PanelApi.createUserFromTemplate(session, username, templateId, note) }
    }, session = session)
    deleteUser?.let { user ->
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(DsRadius.Lg).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("حذف ${user.username}؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text("غیرقابل بازگشت", color = theme.mutedColor, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton("انصراف", onClick = { deleteUser = null }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        DangerButton("حذف", onClick = { deleteUser = null; runAction(notification = "حذف کاربر" to "${user.username} حذف شد") { PanelApi.deleteUser(session, user.username) } }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    qrUser?.let { user ->
        SubscriptionQrDialog(user = user, onDismiss = { qrUser = null })
    }
    invoiceDialogUser?.let { u ->
        InvoiceDialog(
            user = u,
            debtorInfo = debtorByUsername[u.username],
            currency = monitoringSettings.debtorCurrency,
            onDismiss = { invoiceDialogUser = null }
        )
    }
    debtorDialogUser?.let { u ->
        val existing = debtorByUsername[u.username]
        DebtorEditDialog(
            user = u,
            existing = existing,
            currency = monitoringSettings.debtorCurrency,
            onDismiss = { debtorDialogUser = null },
            onSave = { amount, notes ->
                val info = DebtorInfo(
                    username = u.username,
                    baseUrl = session.baseUrl,
                    amount = amount,
                    currency = monitoringSettings.debtorCurrency,
                    markedAt = existing?.markedAt ?: System.currentTimeMillis(),
                    notes = notes,
                    autoDisabled = existing?.autoDisabled ?: false,
                    userId = u.id
                )
                store.setDebtor(info)
                reloadDebtors()
                debtorDialogUser = null
                android.widget.Toast.makeText(context, if (existing==null) "بدهکار ثبت شد" else "بدهی بروزرسانی شد", android.widget.Toast.LENGTH_SHORT).show()
                if (monitoringSettings.debtorAutoDisableEnabled) {
                    val over = info.isOverdue(monitoringSettings.debtorAutoDisableAfterHours)
                    if (over && u.status != "disabled") {
                        scope.launch {
                            runCatching { PanelApi.setDisabled(session, u.username, true) }.onSuccess {
                                val updated = info.copy(autoDisabled = true)
                                store.setDebtor(updated)
                                reloadDebtors()
                                android.widget.Toast.makeText(context, "کاربر بدهکار به صورت خودکار قطع شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onClear = {
                val wasAutoDisabled = debtorByUsername[u.username]?.autoDisabled ?: false
                store.removeDebtor(session.baseUrl, u.username)
                reloadDebtors()
                debtorDialogUser = null
                android.widget.Toast.makeText(context, "بدهی تسویه شد", android.widget.Toast.LENGTH_SHORT).show()
                if (wasAutoDisabled) {
                    scope.launch {
                        runCatching { PanelApi.setDisabled(session, u.username, false) }.onSuccess {
                            load()
                            android.widget.Toast.makeText(context, "کاربر فعال شد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
    resetExpiryTarget?.let { u ->
        ResetExpiryDurationDialog(
            onDismiss = { resetExpiryTarget = null },
            onConfirm = { days ->
                val targetUser = u; resetExpiryTarget = null
                runAction(notification = "ریست زمان" to "زمان ${targetUser.username} به $days روز ریست شد") {
                    val newExpire = LocalDate.now().plusDays(days.toLong()).toString()
                    PanelApi.modifyUser(session, targetUser.username, targetUser.dataLimit.toDouble() / 1073741824.0, newExpire, targetUser.note ?: "", targetUser.hwidLimit, targetUser.groupIds)
                }
            }
        )
    }

    LaunchedEffect(users, monitoringSettings.debtorAutoDisableEnabled, monitoringSettings.debtorAutoDisableAfterHours) {
        if (!monitoringSettings.debtorAutoDisableEnabled) return@LaunchedEffect
        if (users.isEmpty()) return@LaunchedEffect
        debtorsForCurrentPanel.forEach { d ->
            if (!d.isOverdue(monitoringSettings.debtorAutoDisableAfterHours)) return@forEach
            if (d.autoDisabled) return@forEach
            val pu = users.find { it.username == d.username } ?: return@forEach
            if (pu.status == "disabled") {
                val updated = d.copy(autoDisabled = true)
                store.setDebtor(updated)
                reloadDebtors()
                return@forEach
            }
            runCatching { PanelApi.setDisabled(session, d.username, true) }.onSuccess {
                val updated = d.copy(autoDisabled = true)
                store.setDebtor(updated)
                reloadDebtors()
                if (monitoringSettings.notificationsEnabled && monitoringSettings.notifyDebtorOverdue) {
                    NotificationHelper.post(context, ("debtor_overdue_"+d.username).hashCode(), NotificationHelper.CHANNEL_EVENTS, "قطع خودکار بدهکار", "${d.username} پس از ${monitoringSettings.debtorAutoDisableAfterHours} ساعت بدهکاری قطع شد (${d.amount} ${d.currency})")
                }
            }
        }
    }
}
