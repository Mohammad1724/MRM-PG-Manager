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

import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsMotion
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.DsTileRadius

/* ──────────────────────────────────────────────────────────────────────────
 *  نوارهای بالای صفحهٔ کاربران
 *
 *  سربرگ، کارت‌های آمار، جست‌وجو و نوارِ فیلتر/مرتب‌سازی/نمای فهرست — یعنی هر
 *  چیزی که *بالای* فهرست می‌نشیند. جدا شد تا تغییرِ ظاهرِ کنترل‌ها به منطقِ
 *  بارگذاری و انتخابِ گروهیِ صفحه دست نزند.
 * ────────────────────────────────────────────────────────────────────────── */


@Composable
internal fun StatGlassCard(icon: AppIcon, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Lg
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(shape)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), shape)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isGold = accent == theme.accentPrimary
                val iconBg = if (isGold) { if (theme.isDark) theme.accentPrimary.copy(0.15f) else theme.accentPrimary.copy(alpha = 0.12f) } else accent.copy(0.10f)
                val iconBorder = if (isGold) { if (theme.isDark) theme.accentPrimary.copy(0.22f) else theme.accentPrimary.copy(alpha = 0.24f) } else accent.copy(0.18f)
                val iconTint = if (isGold) { theme.accentPrimary } else accent
                Box(Modifier.size(24.dp).clip(DsRadius.Sm).background(iconBg).border(BorderStroke(DsBorder.Hairline, iconBorder), DsRadius.Sm), contentAlignment = Alignment.Center) {
                    RoundedAppIcon(icon, tint = iconTint, size = 13.dp)
                }
                Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TechnicalContainer {
                Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.35f, targetValue = 0.65f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(DsRadius.Lg).background(theme.cardBgColor.copy(alpha = alpha)).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).height(120.dp))
}

@Composable
internal fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
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
            RoundedAppIcon(AppIcon.Search, contentDescription = stringResource(R.string.search), tint = theme.mutedColor, size = 16.dp)
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
internal fun TopBarHeader(
    onRefresh: () -> Unit,
    loading: Boolean,
    onOpenSettings: () -> Unit = {}
) {
    // این سربرگ تنها سربرگی بود که با بقیهٔ صفحه‌ها فرق داشت: نه کارت داشت نه
    // حاشیه، دکمه‌هایش ۳۲dp بود به‌جای ۳۴dp، رفرشش به‌جای چرخیدن با یک اسپینر
    // عوض می‌شد، و عنوانش رشتهٔ انگلیسیِ هاردکد بود. حالا همان PGScreenHeaderِ
    // مشترک است.
    //
    // ساختِ کاربر از دکمهٔ شناورِ پایین انجام می‌شود؛ کال‌بکش اینجا گرفته می‌شد
    // ولی هیچ دکمه‌ای در سربرگ صدایش نمی‌زد.
    PGScreenHeader(
        title = stringResource(R.string.users),
        subtitle = stringResource(R.string.control_users_desc),
        refreshing = loading,
        onRefresh = onRefresh,
        onOpenSettings = onOpenSettings,
        settingsLabel = stringResource(R.string.app_settings),
        refreshLabel = stringResource(R.string.refresh)
    )
}

@Composable
internal fun StatsCardsRow(
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

/**
 * سبکِ متنِ فشرده برای دکمه‌های دوسطریِ فیلتر/مرتب‌سازی.
 * فاصلهٔ اضافیِ بالا و پایینِ فونت حذف می‌شود تا دو سطر روی هم نیفتند.
 */
internal val CompactLabelStyle = TextStyle(
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
    )
)

@Composable
internal fun FilterAndControlBar(
    currentFilter: UserFilter,
    onFilterChange: (UserFilter) -> Unit,
    currentSort: UserSort,
    onSortChange: (UserSort) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    debtorCount: Int = 0,
    groups: List<com.mrm.pgmanager.data.model.Group> = emptyList(),
    groupFilterId: Int? = null,
    onGroupFilterChange: (Int?) -> Unit = {}
) {
    val theme = LocalThemeState.current
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Filter dropdown button like PasarGuard panel
        Box(Modifier.weight(1f).height(38.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).pressScale(0.97f).clickable { showFilterSheet = true }.padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Filter, tint = theme.mutedColor, size = 13.dp)
                    // برچسبِ ثابت «فیلتر» + مقدارِ فعلی زیرِ آن.
                    // lineHeight و includeFontPadding صریح تعیین شده تا دو سطر از کادر بیرون نزند.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(stringResource(R.string.filter), fontSize = 9.sp, lineHeight = 10.sp, style = CompactLabelStyle, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(filterLabel(currentFilter), fontSize = 11.sp, lineHeight = 13.sp, style = CompactLabelStyle, color = theme.inkColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Text("▾", fontSize = 10.sp, color = theme.mutedColor)
            }
        }
        // Sort dropdown - with icon
        Box(Modifier.weight(1f).height(38.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).pressScale(0.97f).clickable { showSortSheet = true }.padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Sort, tint = theme.mutedColor, size = 13.dp)
                    // برچسبِ ثابت «مرتب‌سازی» + مقدارِ فعلی
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(stringResource(R.string.sort), fontSize = 9.sp, lineHeight = 10.sp, style = CompactLabelStyle, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(when(currentSort){ UserSort.NAME->stringResource(R.string.name); UserSort.USAGE->stringResource(R.string.usage_sort); UserSort.EXPIRY->stringResource(R.string.expiry); UserSort.CREATED->stringResource(R.string.created)}, fontSize = 11.sp, lineHeight = 13.sp, style = CompactLabelStyle, color = theme.inkColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Text("▾", fontSize = 10.sp, color = theme.mutedColor)
            }
        }
        // فیلترِ گروه — پنل خودش اعمالش می‌کند (`?group=`)
        if (groups.isNotEmpty()) {
            var groupMenu by remember { mutableStateOf(false) }
            val selectedName = groups.firstOrNull { it.id == groupFilterId }?.name
            Box {
                Row(
                    Modifier.height(38.dp).clip(DsRadius.Sm)
                        .background(if (groupFilterId != null) theme.accentPrimary.copy(0.16f) else theme.searchBgColor)
                        .border(BorderStroke(DsBorder.Hairline, if (groupFilterId != null) theme.accentPrimary.copy(0.34f) else theme.borderColor), DsRadius.Sm)
                        .pressScale(0.97f)
                        .clickable { groupMenu = true }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    RoundedAppIcon(AppIcon.Folder, tint = if (groupFilterId != null) theme.accentPrimary else theme.mutedColor, size = 13.dp)
                    Text(
                        selectedName ?: stringResource(R.string.us_group_filter),
                        fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (groupFilterId != null) theme.accentPrimary else theme.mutedColor,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 84.dp)
                    )
                }
                androidx.compose.material3.DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.us_group_all), fontSize = 12.sp) },
                        onClick = { onGroupFilterChange(null); groupMenu = false }
                    )
                    groups.forEach { g ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(g.name, fontSize = 12.sp) },
                            onClick = { onGroupFilterChange(g.id); groupMenu = false }
                        )
                    }
                }
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
                listOf(
                    stringResource(R.string.all) to UserFilter.ALL,
                    stringResource(R.string.active) to UserFilter.ACTIVE,
                    stringResource(R.string.expired) to UserFilter.EXPIRED,
                    stringResource(R.string.limited) to UserFilter.LIMITED,
                    stringResource(R.string.on_hold) to UserFilter.ON_HOLD,
                    stringResource(R.string.disabled) to UserFilter.DISABLED,
                    stringResource(R.string.near_limit) to UserFilter.NEAR_LIMIT,
                    (if (debtorCount > 0) stringResource(R.string.debtor) + " ($debtorCount)" else stringResource(R.string.debtor)) to UserFilter.DEBTOR
                ).forEach { (label, f) ->
                    val sel = currentFilter == f
                    Box(Modifier.fillMaxWidth().height(40.dp).clip(DsRadius.Sm).background(if(sel) theme.accentPrimary else theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, if(sel) theme.accentPrimary else theme.borderColor), DsRadius.Sm).clickable { onFilterChange(f); showFilterSheet=false }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
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
                    Box(Modifier.fillMaxWidth().height(40.dp).clip(DsRadius.Sm).background(if(sel) theme.accentPrimary else theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, if(sel) theme.accentPrimary else theme.borderColor), DsRadius.Sm).clickable { onSortChange(s); showSortSheet=false }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(label, fontSize = 12.sp, fontWeight = if(sel) FontWeight.SemiBold else FontWeight.Medium, color = if(sel) Color(0xFF422006) else theme.inkColor)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Sm
    Box(modifier = Modifier
        .height(32.dp)
        .clip(shape)
        .background(if (selected) theme.accentPrimary else theme.searchBgColor)
        .border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary else theme.borderColor), shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF422006) else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Sm
    Box(modifier = Modifier.clip(shape).background(if (selected) theme.accentPrimary else Color.Transparent).border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary else Color.Transparent), shape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, color = if (selected) Color(0xFF422006) else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
internal fun ViewModeIcon(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Sm
    Box(modifier = Modifier.size(32.dp).clip(shape).background(if (selected) theme.accentPrimary else Color.Transparent).border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary else Color.Transparent), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        RoundedAppIcon(icon, tint = if (selected) Color(0xFF422006) else theme.mutedColor, size = 18.dp)
    }
}

/** برچسبِ فارسی/انگلیسیِ هر فیلتر — یک‌جا تا با اضافه‌شدنِ فیلتر جا نماند. */
@Composable
private fun filterLabel(f: UserFilter): String = when (f) {
    UserFilter.ALL -> stringResource(R.string.all)
    UserFilter.ACTIVE -> stringResource(R.string.active)
    UserFilter.EXPIRED -> stringResource(R.string.expired)
    UserFilter.LIMITED -> stringResource(R.string.limited)
    UserFilter.ON_HOLD -> stringResource(R.string.on_hold)
    UserFilter.DISABLED -> stringResource(R.string.disabled)
    UserFilter.NEAR_LIMIT -> stringResource(R.string.near_limit)
    UserFilter.DEBTOR -> stringResource(R.string.debtor)
}
