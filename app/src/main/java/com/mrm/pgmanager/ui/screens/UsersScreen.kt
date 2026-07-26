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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.BuildConfig
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.BulkCreateUsersDialog
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.QuickActionSheet
import com.mrm.pgmanager.ui.dialogs.SettingsActionRow
import com.mrm.pgmanager.ui.dialogs.SubscriptionQrDialog
import com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog
import com.mrm.pgmanager.ui.dialogs.UserEditorDialog
import com.mrm.pgmanager.ui.dialogs.UserDetailsDialog
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
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

/** یک عملیاتِ گروهیِ در انتظارِ تأییدِ کاربر. */
private data class PendingBulk(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit)

// Track more gray and visible
private fun trackBg(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.26f) else Color(0xFF6B7280).copy(alpha = 0.28f)

private fun daysLeftText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    // تلاش برای پارس به‌عنوان لحظهٔ زمانی (ISO با ساعت) تا مثل پنل، روز را دقیق و سازگار محاسبه کنیم
    return try {
        val inst = java.time.Instant.parse(expire)
        val diffSec = inst.epochSecond - java.time.Instant.now().epochSecond
        when {
            diffSec <= 0 -> "منقضی"
            diffSec < 86400 -> "امروز"
            else -> "${(diffSec + 86399L) / 86400L} روز" // گردکردنِ رو‌به‌بالا = هم‌خوان با پنل
        }
    } catch (e: Exception) {
        try {
            val exp = LocalDate.parse(expire.take(10))
            val diff = ChronoUnit.DAYS.between(LocalDate.now(), exp)
            when {
                diff < 0 -> "منقضی"
                diff == 0L -> "امروز"
                diff == 1L -> "۱ روز"
                diff <= 7 -> "$diff روز"
                diff <= 30 -> "${diff} روز"
                else -> "${diff} روز"
            }
        } catch (e2: Exception) { JalaliCalendar.isoToShamsi(expire).ifEmpty { "نامحدود" } }
    }
}

private fun daysLeftFull(expire: String?): String = daysLeftText(expire)

/** متنِ وضعیت برای کارت: اول وضعیت (غیرفعال/منقضی/محدود)، بعد روزِ مانده. */
private fun cardStatusText(user: PanelUser): String = when (user.status) {
    "disabled" -> "غیرفعال"
    "expired" -> "منقضی"
    "limited" -> "محدود"
    "on_hold" -> "در انتظار"
    else -> daysLeftText(user.expire)
}

@Composable
private fun StatGlassCard(icon: AppIcon, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.height(72.dp).clip(RoundedCornerShape(14.dp)).background(glassBg(theme.isDark, theme.amoledDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(23.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(.12f)), contentAlignment = Alignment.Center) { RoundedAppIcon(icon, tint = accent, size = 12.dp) }
                Text(label, fontSize = 10.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
            }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.18f, targetValue = 0.42f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(glassBg(theme.isDark, theme.amoledDark).copy(alpha = alpha)).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(20.dp)).height(120.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(AppIcon.Search, tint = theme.mutedColor, size = 18.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("جستجو کاربر...", color = theme.mutedColor.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } }
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.14f)).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) { Text("×", color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TopBarHeader(
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    loading: Boolean
) {
    val theme = LocalThemeState.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("کاربران", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text("مدیریت و نظارت بر حساب‌های کاربری", fontSize = 10.5.sp, color = theme.mutedColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(icon = { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 19.dp) }, onClick = onOpenThemeDialog)
            ActionIconButton(icon = { if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = theme.inkColor, strokeWidth = 2.dp) else RoundedAppIcon(AppIcon.Refresh, tint = theme.inkColor, size = 19.dp) }, onClick = onRefresh, enabled = !loading)
            ActionIconButton(icon = { RoundedAppIcon(AppIcon.Logout, tint = GlassRed, size = 19.dp) }, onClick = onLogout, isRed = true)
        }
    }
}

@Composable
private fun StatsCardsRow(
    totalUsers: Int,
    activeUsers: Int,
    onlineUsers: Int,
    totalUsedTraffic: Long
) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // همان hierarchy پنل: شاخص‌های زنده در بالا و شمار کل در یک سطح جداگانه.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatGlassCard(icon = AppIcon.User, label = "کاربران آنلاین", value = "$onlineUsers", accent = GlassGreen, modifier = Modifier.weight(1f))
            StatGlassCard(icon = AppIcon.Check, label = "کاربران فعال", value = "$activeUsers", accent = theme.accentPrimary, modifier = Modifier.weight(1f))
        }
        StatGlassCard(icon = AppIcon.Users, label = "همهٔ کاربران", value = "$totalUsers", accent = theme.accentPrimary, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FilterAndControlBar(currentFilter: UserFilter, onFilterChange: (UserFilter) -> Unit, currentSort: com.mrm.pgmanager.data.model.UserSort, onSortChange: (com.mrm.pgmanager.data.model.UserSort) -> Unit, viewMode: ViewMode, onViewModeChange: (ViewMode) -> Unit, users: List<PanelUser>) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChipItem("همه", currentFilter == UserFilter.ALL, onClick = { onFilterChange(UserFilter.ALL) })
            FilterChipItem("فعال", currentFilter == UserFilter.ACTIVE, onClick = { onFilterChange(UserFilter.ACTIVE) })
            FilterChipItem("لب مرز", currentFilter == UserFilter.NEAR_LIMIT, onClick = { onFilterChange(UserFilter.NEAR_LIMIT) })
            FilterChipItem("منقضی", currentFilter == UserFilter.EXPIRED, onClick = { onFilterChange(UserFilter.EXPIRED) })
            FilterChipItem("غیرفعال", currentFilter == UserFilter.DISABLED, onClick = { onFilterChange(UserFilter.DISABLED) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مرتب:", fontSize = 8.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                SortPill("نام", currentSort == com.mrm.pgmanager.data.model.UserSort.NAME) { onSortChange(com.mrm.pgmanager.data.model.UserSort.NAME) }
                SortPill("مصرف", currentSort == com.mrm.pgmanager.data.model.UserSort.USAGE) { onSortChange(com.mrm.pgmanager.data.model.UserSort.USAGE) }
                SortPill("انقضا", currentSort == com.mrm.pgmanager.data.model.UserSort.EXPIRY) { onSortChange(com.mrm.pgmanager.data.model.UserSort.EXPIRY) }
                SortPill("ساخت", currentSort == com.mrm.pgmanager.data.model.UserSort.CREATED) { onSortChange(com.mrm.pgmanager.data.model.UserSort.CREATED) }
            }
            Spacer(Modifier.width(4.dp))
            // کلاستر حالت نمایش: همان کپسول سگمنت‌شدهٔ تنظیمات (کاشی خاکستری + آیتم فعال اکسنت).
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp)).padding(2.5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ViewModeIcon(AppIcon.GridView, viewMode == ViewMode.GRID) { onViewModeChange(ViewMode.GRID) }
                ViewModeIcon(AppIcon.ListRows, viewMode == ViewMode.COMPACT_LIST) { onViewModeChange(ViewMode.COMPACT_LIST) }
                ViewModeIcon(AppIcon.DenseList, viewMode == ViewMode.MICRO_LIST) { onViewModeChange(ViewMode.MICRO_LIST) }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    // چیپ فیلتر: هم‌سبک با سگمنت تنظیمات — فعال = کپسول اکسنت ۷۸٪ با متن تیره، غیرفعال = کاشی خاکستری.
    Box(modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) theme.accentPrimary.copy(.78f) else theme.searchBgColor).border(BorderStroke(1.dp, if (selected) theme.searchBgColor else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 4.5.dp)) {
        Text(label, color = if (selected) Color(0xFF202124) else theme.inkColor, fontSize = 9.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) theme.accentPrimary.copy(.78f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.5.dp)) {
        Text(label, color = if (selected) Color(0xFF202124) else theme.mutedColor, fontSize = 8.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ViewModeIcon(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) theme.accentPrimary.copy(.78f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        RoundedAppIcon(icon, tint = if (selected) Color(0xFF202124) else theme.mutedColor, size = 13.dp)
    }
}

// FIX 1: Progress bar visible, thicker, 8dp, gray track 28%
@Composable
private fun CheckboxIcon(selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val isDark = theme.isDark
    val bg = if (selected) theme.accentPrimary else if (isDark) Color(0xFF383842) else Color.White
    val borderCol = if (selected) theme.accentPrimary else if (isDark) Color(0xFF8E8C98) else Color(0xFFB8BBC2)
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(4.dp))
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

// FIX 2: Online dot
// FIX 3: GB / GB and days left
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxuryGridCard(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val displayProgress = if (user.dataLimit == 0L) 0.08f else actualProgress.coerceAtLeast(0.08f)
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)

    // نمای گرید نیز از همان کارت‌های خنثی و مرز ظریف design system جدید استفاده می‌کند.
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) theme.accentPrimary.copy(.10f) else glassBg(theme.isDark, theme.amoledDark)).border(BorderStroke(if (selected) 1.2.dp else 1.dp, if (selected) theme.accentPrimary else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp).background(statusColor))
        Column(Modifier.padding(start = 3.dp).padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(2.5.dp)).background(onlineDot))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Text(user.username, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(statusColor)) }
                    Text(lastSeenShort(user.onlineAt, user.isOnline), fontSize = 8.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, maxLines = 1)
                }
                if (user.note?.isNotBlank() == true) Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3B82F6).copy(0.14f)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Note, tint = Color(0xFF3B82F6), size = 11.dp) }
            }
            Text(if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / نامحدود" else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${cardStatusText(user)}", fontSize = 9.5.sp, color = theme.mutedColor, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(10.dp)).background(trackBg(theme.isDark))) {
                Box(Modifier.fillMaxWidth(displayProgress).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(progressColor))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.subUrl.isNotBlank()) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(theme.searchBgColor).border(BorderStroke(0.8.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(7.dp)).clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                        android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) { Text("کپی", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(theme.searchBgColor).border(BorderStroke(0.8.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(7.dp)).clickable { onQrClick(user) }.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) { Text("QR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                }
                Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(if (user.isOnline) GlassGreen.copy(0.12f) else Color.Gray.copy(0.10f)).border(BorderStroke(0.8.dp, if (user.isOnline) GlassGreen.copy(0.18f) else Color.Gray.copy(0.12f)), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text(if (user.isOnline) "آنلاین" else "آفلاین", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (user.isOnline) GlassGreen else Color.Gray)
                }
                if (user.groupNames.isNotEmpty()) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF8B5CF6).copy(0.10f)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                        Text(user.groupNames.first(), fontSize = 8.5.sp, color = Color(0xFF8B5CF6), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineBadge(user: PanelUser) {
    // در نمای فشرده، تنها نشانگر رنگی کافی است و فضای نام کاربر را نمی‌گیرد.
    val color = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(4.5.dp))
            .background(color)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.55f)), RoundedCornerShape(4.5.dp))
    )
}

@Composable
private fun UserStatusBadge(user: PanelUser, modifier: Modifier = Modifier, compact: Boolean = false) {
    val theme = LocalThemeState.current
    val (label, color) = when (user.status) {
        "active" -> "فعال" to GlassGreen
        "disabled" -> "غیرفعال" to Color(0xFF8A8A8A)
        "expired" -> "منقضی" to GlassRed
        "limited" -> "محدود" to GlassAmber
        "on_hold" -> "در انتظار" to Color(0xFF7A42D4)
        else -> cardStatusText(user) to theme.mutedColor
    }
    Box(
        modifier.height(if (compact) 17.dp else 22.dp).clip(RoundedCornerShape(if (compact) 5.dp else 7.dp))
            .background(color.copy(alpha = 0.13f))
            .border(BorderStroke(if (compact) 0.7.dp else 0.8.dp, color.copy(alpha = 0.25f)), RoundedCornerShape(if (compact) 5.dp else 7.dp))
            .padding(horizontal = if (compact) 3.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = if (compact) 7.sp else 8.5.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun RowAction(label: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 23.dp, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    // اکشن‌های نمای فشرده: کاشی خاکستریِ خنثیِ design system، سبک و بدون border سنگین.
    Box(modifier.height(height).clip(RoundedCornerShape(6.dp))
        .background(theme.searchBgColor)
        .border(BorderStroke(0.8.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(6.dp))
        .clickable(onClick = onClick).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
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
    Box(Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
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
    // دکمه‌های کارت کاربر: همان کاشی خاکستریِ خنثیِ پنجرهٔ تنظیمات.
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1)
    }
}

/**
 * کارت استاندارد فهرست موبایل.
 * هر بخشِ حساس به طول متن در ستون خودش قرار دارد؛ اکشن‌ها عرض ثابت دارند و
 * نام کاربر فقط در فضای خودش کوتاه می‌شود، بنابراین هیچ‌وقت QR/کپی جابه‌جا یا بریده نمی‌شوند.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxuryCompactRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val shownProgress = if (user.dataLimit > 0) actualProgress else .035f
    val progressPercent = (actualProgress * 100).roundToInt()
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen
        progressPercent < 90 -> GlassAmber
        else -> GlassRed
    }
    val traffic = if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / نامحدود" else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}"

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.accentPrimary.copy(.10f) else glassBg(theme.isDark, theme.amoledDark))
            .border(BorderStroke(if (selected) 1.2.dp else 1.dp, if (selected) theme.accentPrimary else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ردیف هدر: اکشن‌ها و وضعیت عرض ثابت دارند؛ نام تنها بخش انعطاف‌پذیر است.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                OnlineBadge(user)
                Text(
                    user.username,
                    // عرض ثابت، بج وضعیت را بدون فاصلهٔ کش‌دار دقیقاً کنار نام نگه می‌دارد.
                    modifier = Modifier.width(125.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.inkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // بج بلافاصله بعد از نام قرار می‌گیرد؛ جای اکشن‌ها همچنان ثابت است.
                UserStatusBadge(user, Modifier.width(42.dp))
                if (user.subUrl.isNotBlank()) {
                    UserCardAction("کپی", Modifier.width(46.dp)) { copySubscription(context, user) }
                    UserCardAction("QR", Modifier.width(40.dp)) { onQrClick(user) }
                }
            }

            // ردیف داده‌ها: دو انتهای کارت ثابت و قابل اسکن هستند.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("مصرف ترافیک", fontSize = 8.5.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                    Text(traffic, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("اعتبار باقی‌مانده", fontSize = 8.5.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                    Text(daysLeftText(user.expire), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1)
                    Text(lastSeenShort(user.onlineAt, user.isOnline), fontSize = 8.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, maxLines = 1)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // نوار کم‌ضخامت‌تر تا در نمای لیستی بزرگ، فرم جدول‌مانند و سبک بماند.
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(5.dp)).background(trackBg(theme.isDark))) {
                    Box(Modifier.fillMaxWidth(shownProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(5.dp)))
                }
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxuryMicroRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else .035f
    val progressColor = when { user.dataLimit <= 0L || actualProgress < .70f -> GlassGreen; actualProgress < .90f -> GlassAmber; else -> GlassRed }
    val traffic = "${formatBytes(user.usedTraffic)}/${if (user.dataLimit == 0L) "∞" else formatBytes(user.dataLimit)}"

    // ردیف داده‌ای فشرده: سطح سفید، border ظریف و ستون‌های ثابت؛ نزدیک به جدول Users پنل.
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) theme.accentPrimary.copy(.10f) else glassBg(theme.isDark, theme.amoledDark)).border(BorderStroke(if (selected) 1.2.dp else 1.dp, if (selected) theme.accentPrimary else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) }).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckboxIcon(selected = selected, onToggle = onSelectToggle)
            OnlineBadge(user)
            // نام و آخرین فعالیت یک ستون واحدند؛ بنابراین فعالیت دقیقاً زیر نام باقی می‌ماند.
            Column(Modifier.width(76.dp).offset(y = 13.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                // نام کمی پایین‌تر و فعالیت با فاصلهٔ فشرده‌تر دقیقاً زیر آن قرار می‌گیرد.
                Text(user.username, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lastSeenShort(user.onlineAt, user.isOnline), modifier = Modifier.offset(y = (-7).dp), fontSize = 6.8.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, maxLines = 1)
            }
            // بج وضعیت در جای طبیعی خودش، بلافاصله بعد از نام قرار دارد.
            UserStatusBadge(user, Modifier.width(28.dp), compact = true)
            // تنها ستون انعطاف‌پذیر ردیف است: فضای آزاد را می‌گیرد، نوار بلندتر می‌شود
            // و اکشن‌ها دقیقاً به لبهٔ راست کارت می‌چسبند.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(traffic, fontSize = 7.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(daysLeftText(user.expire), fontSize = 7.5.sp, color = theme.mutedColor, maxLines = 1)
                }
                // نوار مصرف کمی بالاتر قرار گرفته تا با نام و بج وضعیت تراز بصری بهتری داشته باشد.
                Box(Modifier.fillMaxWidth().offset(y = (-8).dp).height(3.dp).clip(RoundedCornerShape(3.dp)).background(trackBg(theme.isDark))) {
                    Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(3.dp)))
                }
            }
            if (user.subUrl.isNotBlank()) {
                RowAction("کپی", Modifier.width(36.dp), 22.dp) { copySubscription(context, user) }
                RowAction("QR", Modifier.width(32.dp), 22.dp) { onQrClick(user) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onAddAccount: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { com.mrm.pgmanager.data.storage.SessionStore(context) }
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // مهر زمانی کش آفلاین؛ وقتی null نیست یعنی داده‌ها از حافظهٔ محلی نمایش داده می‌شوند.
    var offlineAt by remember { mutableStateOf<Long?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var qrUser by remember { mutableStateOf<PanelUser?>(null) }
    var onlineCount by remember { mutableStateOf(0) }
    // آخرین وضعیت دیده‌شده برای جلوگیری از اعلان تکراری در هر refresh.
    var lastUserStates by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(com.mrm.pgmanager.data.model.UserSort.CREATED) }
    // حالت نمایش (Grid/List) با آخرین انتخاب کاربر پایدار می‌ماند.
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
    var quickTemplates by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.UserTemplateItem>>(emptyList()) }
    var quickTemplatesLoading by remember { mutableStateOf(true) }
    var quickTemplatesFailed by remember { mutableStateOf(false) }

    // Collapsing header state for the 4 top stat buttons/cards (Dynamic measurement = exact alignment & zero gaps)
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
            // رفرش خودکار بی‌صداست: اسکلت‌لودینگ و چرخهٔ Pull-to-refresh فقط برای رفرش دستی/اولیه است.
            if (!silent) loading = true
            error = null
            runCatching {
                val list = PanelApi.users(session)
                users = list; onlineCount = list.count { it.isOnline }
                // کش آفلاین: آخرین واکشی موفق ذخیره می‌شود تا هنگام قطعی پنل نمایش بماند.
                store.saveUsersCache(list)
                offlineAt = null
                val settings = store.readMonitoringSettings()
                val nextStates = list.associate { u ->
                    val usage = if (u.dataLimit > 0) ((u.usedTraffic * 100L) / u.dataLimit).toInt() else 0
                    val remainingDays = runCatching {
                        val date = try { java.time.Instant.parse(u.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(u.expire?.take(10) ?: "") }
                        ChronoUnit.DAYS.between(LocalDate.now(), date).coerceAtLeast(0)
                    }.getOrDefault(Long.MAX_VALUE)
                    val nearExpiry = remainingDays <= settings.nearExpiryDays
                    u.id to "${u.status}|$usage|$nearExpiry"
                }
                // اولین دریافت فقط baseline است؛ اعلان‌ها از تغییرات بعدی صادر می‌شوند.
                if (lastUserStates.isNotEmpty() && settings.notificationsEnabled) {
                    list.forEach { u ->
                        val previous = lastUserStates[u.id] ?: return@forEach
                        val current = nextStates[u.id] ?: return@forEach
                        if (previous == current) return@forEach
                        fun notify(id: Int, title: String, text: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_EVENTS, title, text)
                        if (settings.notifyLimited && u.status == "limited" && !previous.startsWith("limited")) notify(("limited" + u.id).hashCode(), "کاربر محدود شد", "${u.username} به سقف حجم رسیده است")
                        if (settings.notifyExpired && u.status == "expired" && !previous.startsWith("expired")) notify(("expired" + u.id).hashCode(), "اشتراک منقضی شد", "اشتراک ${u.username} منقضی شده است")
                        val usage = if (u.dataLimit > 0) ((u.usedTraffic * 100L) / u.dataLimit).toInt() else 0
                        val oldUsage = previous.split("|").getOrNull(1)?.toIntOrNull() ?: 0
                        if (settings.notifyNearLimit && usage >= settings.nearLimitPercent && oldUsage < settings.nearLimitPercent) notify(("near_limit" + u.id).hashCode(), "هشدار مصرف", "${u.username} به $usage٪ مصرف حجم رسیده است")
                        val nearExpiry = current.substringAfterLast("|").toBoolean()
                        val wasNearExpiry = previous.substringAfterLast("|").toBoolean()
                        if (settings.notifyNearExpiry && nearExpiry && !wasNearExpiry) notify(("near_expire" + u.id).hashCode(), "هشدار انقضا", "اشتراک ${u.username} نزدیک به انقضا است")
                    }
                }
                lastUserStates = nextStates
                // در رفرش خودکارِ پس‌زمینه، هدرِ جمع‌شده کاربر دست‌نخورده می‌ماند.
                if (resetHeader) scrollOffset.value = 0f
            }.onFailure {
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                } else {
                    // کش آفلاین: اگر اتصال قطع است و دادهٔ قبلی داریم، همان را با بنر «آفلاین» نشان می‌دهیم.
                    val cache = if (monitoringSettings.offlineCacheEnabled) store.readUsersCache() else null
                    if (cache != null) {
                        users = cache.first
                        onlineCount = 0
                        offlineAt = cache.second
                        error = null
                    } else if (!silent) {
                        // خطای رفرش خودکارِ پس‌زمینه بی‌صدا می‌ماند تا لیستِ فعلی کاربر نپرد؛
                        // فقط رفرش دستی/اولیه است که صفحهٔ خطا نشان می‌دهد.
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
    // خروجی گرفتن از کاربران انتخاب‌شده (SAF: کاربر محل ذخیره را خودش انتخاب می‌کند).
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
    // پایش دوره‌ای «کل برنامه»: فقط وقتی کاربر این محدوده را در تنظیمات فعال کرده باشد.
    LaunchedEffect(session, monitoringSettings.autoRefreshEnabled, monitoringSettings.refreshWhileAppOpen, monitoringSettings.refreshIntervalSeconds) {
        if (monitoringSettings.autoRefreshEnabled && monitoringSettings.refreshWhileAppOpen) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(monitoringSettings.refreshIntervalSeconds.coerceIn(5, 3600) * 1_000L)
                load(resetHeader = false, silent = true)
            }
        }
    }

    val processedUsers = remember(users, query, currentFilter, currentSort, monitoringSettings.nearLimitPercent) {
        var list = users.filter { it.username.contains(query, ignoreCase = true) }
        list = when (currentFilter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.status == "active" }
            UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= monitoringSettings.nearLimitPercent / 100.0 }
            UserFilter.EXPIRED -> list.filter { val p = if (it.dataLimit > 0) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= 1.0 || it.status == "expired" || it.status == "limited" }
            UserFilter.DISABLED -> list.filter { it.status == "disabled" }
        }
        when (currentSort) {
            com.mrm.pgmanager.data.model.UserSort.NAME -> list.sortedBy { it.username.lowercase() }
            com.mrm.pgmanager.data.model.UserSort.USAGE -> list.sortedByDescending { it.usedTraffic }
            com.mrm.pgmanager.data.model.UserSort.EXPIRY -> list.sortedBy { it.expire ?: "9999" }
            com.mrm.pgmanager.data.model.UserSort.CREATED -> list.sortedByDescending { it.id }
        }
    }

    val totalUsed = remember(users) { users.sumOf { it.usedTraffic } }

    // NestedScrollConnection - track scroll for collapsing/expanding the 4 top stat cards smoothly
    val nestedScrollConnection = remember(headerHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (headerHeight <= 0f) return Offset.Zero

                val delta = -available.y
                val current = scrollOffset.value
                // Collapsing header while dragging UP
                if (delta > 0f && current < headerHeight) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                // Expanding header while dragging DOWN (EnterAlways / Quick Return)
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
            Box(modifier = Modifier.padding(bottom = 18.dp).size(52.dp).clip(RoundedCornerShape(26.dp)).background(themeState.accentPrimary.copy(.78f)).clickable { createMenuOpen = true }, contentAlignment = Alignment.Center) {
                Text("+", fontSize = 27.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202124))
            }
        }
    }) { padding ->
        val topInsets = padding.calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // 1. Lists / Grid با قابلیتِ Pull-to-refresh
            val scrollOffsetDp = with(density) { scrollOffset.value.toDp() }
            val listTopPad = (totalHeaderDp - scrollOffsetDp).coerceAtLeast(0.dp) + topInsets + 4.dp
            val ptrState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { load() },
                // ردیف‌های کاربر تمام‌عرض‌اند؛ padding افقی فقط داخل خود کارت اعمال می‌شود.
                modifier = Modifier.fillMaxSize(),
                state = ptrState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        isRefreshing = loading,
                        state = ptrState,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = listTopPad)
                    )
                }
            ) {
                // هنگام جمع‌شدنِ هدر، paddingِ بالای لیست هم‌زمان کم می‌شود تا آیتم‌ها جایِ هدر را پر کنند
                // و لیست تا پایینِ صفحه پر بماند (بدون فاصلهٔ بیهودهٔ پایین هنگام اسکرول).
                when {
                    loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(6) { SkeletonCard() } }
                    error != null -> Box(Modifier.fillMaxWidth().padding(top = listTopPad).clip(RoundedCornerShape(20.dp)).background(glassBg(themeState.isDark, themeState.amoledDark)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(20.dp)).padding(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("خطا", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                            Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                            com.mrm.pgmanager.ui.components.GlassButton("تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    processedUsers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = listTopPad).clip(RoundedCornerShape(24.dp)).background(glassBg(themeState.isDark, themeState.amoledDark)).border(BorderStroke(1.dp, glassBorder(themeState.isDark, themeState.amoledDark)), RoundedCornerShape(24.dp)).padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("کاربری یافت نشد", fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                        }
                    }
                    else -> when (viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(processedUsers) { user -> LuxuryGridCard(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrUser = it }, onLongClick = { quickActionUser = user }) } }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(processedUsers) { user -> LuxuryCompactRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrUser = it }, onLongClick = { quickActionUser = user }) } }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(processedUsers) { user -> LuxuryMicroRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrUser = it }, onLongClick = { quickActionUser = user }) } }
                    }
                }
            }

            // 2. Dynamic Header Column: Automatically arranges TopBar, StatsCards, SearchBar & FilterBar
            // Measures exact heights so there are zero gaps, zero overlaps with TopBar, and zero showing-through of user cards!
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
                    .border(BorderStroke(1.dp, glassBorder(themeState.isDark, themeState.amoledDark)))
                    .padding(top = topInsets)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                // Top Bar: Fixed right at the top
                TopBarHeader(onRefresh = { load() }, onLogout = onLogout, onOpenThemeDialog = { showThemeDialog = true }, loading = loading)

                // The 4 Stat Cards: Smoothly collapses upwards via custom layout/placement without triggering Measure on grid!
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
                    StatsCardsRow(totalUsers = users.size, activeUsers = users.count { it.status == "active" }, onlineUsers = onlineCount, totalUsedTraffic = totalUsed)
                }

                Spacer(Modifier.height(6.dp))
                GlassSearchBar(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(8.dp))
                FilterAndControlBar(currentFilter = currentFilter, onFilterChange = { currentFilter = it }, currentSort = currentSort, onSortChange = { currentSort = it }, viewMode = viewMode, onViewModeChange = { viewMode = it; store.saveViewMode(it) }, users = users)
                // بنر حالت آفلاین: فقط وقتی داده‌ها از کش محلی نمایش داده می‌شوند.
                offlineAt?.let { cachedAt ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(GlassAmber.copy(.12f)).border(BorderStroke(1.dp, GlassAmber.copy(.30f)), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassAmber, size = 14.dp)
                        Text("حالت آفلاین — آخرین دادهٔ دریافتی: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = GlassAmber, maxLines = 1)
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
                        onDelete = { pendingBulk = PendingBulk("حذف ${selectedUserIds.size} کاربر؟", "این کاربرها برای همیشه حذف می‌شوند و غیرقابل‌بازگشت هستند.", "حذف") { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); runAction(notification = "حذف گروهی" to "${ids.size} کاربر حذف شدند") { PanelApi.bulkDeleteUsers(session, ids) } } },
                        onResetUsage = { pendingBulk = PendingBulk("ریست حجم ${selectedUserIds.size} کاربر؟", "مصرفِ این کاربرها صفر می‌شود.", "تایید") { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); runAction(notification = "ریست حجم گروهی" to "مصرف ${ids.size} کاربر صفر شد") { PanelApi.bulkResetUsersUsage(session, ids) } } },
                        onDisable = { pendingBulk = PendingBulk("غیرفعال‌سازی ${selectedUserIds.size} کاربر؟", "این کاربرها غیرفعال می‌شوند و اتصالشان قطع می‌شود.", "تایید") { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); runAction(notification = "غیرفعال‌سازی گروهی" to "${ids.size} کاربر غیرفعال شدند") { PanelApi.bulkDisableUsers(session, ids) } } },
                        onEnable = { pendingBulk = PendingBulk("فعال‌سازی ${selectedUserIds.size} کاربر؟", "این کاربرها فعال می‌شوند.", "تایید") { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); runAction(notification = "فعال‌سازی گروهی" to "${ids.size} کاربر فعال شدند") { PanelApi.bulkEnableUsers(session, ids) } } },
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
            var list: List<com.mrm.pgmanager.data.model.UserTemplateItem>? = null
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
        var templates by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.UserTemplateItem>>(emptyList()) }
        var templatesLoading by remember { mutableStateOf(true) }
        var templatesFailed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            var list: List<com.mrm.pgmanager.data.model.UserTemplateItem>? = null
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
            onDismiss = { pendingBulk = null },
            onConfirm = { p.action(); pendingBulk = null }
        )
    }

    quickActionUser?.let { u ->
        QuickActionSheet(
            user = u,
            onDismiss = { quickActionUser = null },
            onUseTemplate = { quickTemplateUser = u },
            onToggle = { runAction(notification = "وضعیت کاربر" to "وضعیت ${u.username} تغییر کرد") { PanelApi.setDisabled(session, u.username, u.status != "disabled") } },
            onCopySub = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", u.subUrl))
                android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
            },
            onQr = { qrUser = u },
            onEdit = { selectedUser = u },
            onResetUsage = { runAction(notification = "ریست حجم" to "مصرف ${u.username} صفر شد") { PanelApi.resetUsage(session, u.username) } },
            onResetExpiry = {
                runAction {
                    val totalDays = runCatching {
                        val expires = try { java.time.Instant.parse(u.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(u.expire?.take(10) ?: "") }
                        val created = try { java.time.Instant.parse(u.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(u.createdAt?.take(10) ?: "") }
                        ChronoUnit.DAYS.between(created, expires).coerceAtLeast(1)
                    }.getOrDefault(0)
                    val newExpire = if (totalDays > 0) LocalDate.now().plusDays(totalDays).toString() else ""
                    PanelApi.modifyUser(session, u.username, u.dataLimit.toDouble() / 1073741824.0, newExpire, u.note ?: "", u.hwidLimit, u.groupIds)
                }
            },
            onDelete = { deleteUser = u }
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
        UserDetailsDialog(user = user, onDismiss = { selectedUser = null }, onSave = { limitGb, expireShamsi ->
            selectedUser = null; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
        }, onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } }, onDelete = { deleteUser = user; selectedUser = null }, onResetUsage = {
            runAction(notification = "ریست حجم" to "مصرف ${user.username} صفر شد") { PanelApi.resetUsage(session, user.username) }
        }, onResetExpiry = { days ->
            runAction {
                val newExpire = java.time.LocalDate.now().plusDays(days.toLong()).toString()
                PanelApi.modifyUser(session, user.username, user.dataLimit.toDouble() / 1073741824.0, newExpire, user.note ?: "", user.hwidLimit, user.groupIds)
            }
        },  onApplyTemplate = { templateId, note ->
            selectedUser = null; runAction { PanelApi.bulkApplyTemplate(session, setOf(user.id), templateId, note) }
        }, session = session)
    }
    // منوی ساخت: تکی یا گروهی
    if (createMenuOpen) {
        Dialog(onDismissRequest = { createMenuOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(themeState.dialogBgColor).border(BorderStroke(1.2.dp, themeState.cardBorderBrush), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ساخت کاربر", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                SettingsActionRow("ساخت تکی", "یک کاربر جدید با فرم کامل", AppIcon.UserAdd, themeState.accentPrimary) { createMenuOpen = false; createUser = true }
                SettingsActionRow("ساخت گروهی", "چند کاربر هم‌زمان با الگوی نام، از تمپلت یا دستی", AppIcon.Users, GlassGreen) { createMenuOpen = false; bulkCreateOpen = true }
                com.mrm.pgmanager.ui.components.MutedCancelButton("انصراف", onClick = { createMenuOpen = false }, modifier = Modifier.fillMaxWidth().height(38.dp))
            }
        }
    }
    if (bulkCreateOpen) {
        BulkCreateUsersDialog(session = session, onDismiss = { bulkCreateOpen = false }, onFinished = { n -> bulkCreateOpen = false; if (n > 0) load(resetHeader = false, silent = true) })
    }
    // انتخاب فرمت خروجیِ کاربران انتخاب‌شده
    if (exportChooserOpen) {
        Dialog(onDismissRequest = { exportChooserOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(themeState.dialogBgColor).border(BorderStroke(1.2.dp, themeState.cardBorderBrush), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("خروجی ${selectedUserIds.size} کاربر", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text("فرمت فایل را انتخاب کن؛ سپس محل ذخیره‌سازی پرسیده می‌شود.", fontSize = 10.sp, color = themeState.mutedColor)
                SettingsActionRow("خروجی CSV", "مناسب اکسل و گزارش‌گیری", AppIcon.Download, GlassGreen) { exportChooserOpen = false; beginExport("csv") }
                SettingsActionRow("خروجی JSON", "مناسب برنامه‌نویسی و بکاپ", AppIcon.Download, themeState.accentPrimary) { exportChooserOpen = false; beginExport("json") }
                com.mrm.pgmanager.ui.components.MutedCancelButton("انصراف", onClick = { exportChooserOpen = false }, modifier = Modifier.fillMaxWidth().height(38.dp))
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
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(GlassShape).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), GlassShape).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("حذف ${user.username}؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text("غیرقابل بازگشت", color = theme.mutedColor, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        com.mrm.pgmanager.ui.components.GlassButton("انصراف", onClick = { deleteUser = null }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        com.mrm.pgmanager.ui.components.GlassButton("حذف", onClick = { deleteUser = null; runAction(notification = "حذف کاربر" to "${user.username} حذف شد") { PanelApi.deleteUser(session, user.username) } }, modifier = Modifier.weight(1f), isRed = true)
                    }
                }
            }
        }
    }
    qrUser?.let { user ->
        SubscriptionQrDialog(user = user, onDismiss = { qrUser = null })
    }
}
