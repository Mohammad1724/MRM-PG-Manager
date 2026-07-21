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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.SubscriptionQrDialog
import com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog
import com.mrm.pgmanager.ui.dialogs.UserEditorDialog
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.mrm.pgmanager.ui.theme.glassBg
import com.mrm.pgmanager.ui.theme.glassBorder

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

@Composable
private fun StatGlassCard(icon: String, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(0.14f)).border(BorderStroke(1.dp, accent.copy(0.18f)), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 11.sp) }
                Text(label, fontSize = 10.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
            }
            Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.18f, targetValue = 0.42f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(glassBg(theme.isDark).copy(alpha = alpha)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(20.dp)).height(120.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(13.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(13.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔍", fontSize = 13.5.sp)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLogo(height = 22.dp)
            Column {
                Text("Pasarguard", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = theme.inkColor)
                Text("MRM Manager", fontSize = 10.sp, color = theme.mutedColor)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(icon = { Text("🎨", fontSize = 14.sp) }, onClick = onOpenThemeDialog)
            ActionIconButton(icon = { if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = theme.inkColor, strokeWidth = 2.dp) else Text("🔄", fontSize = 14.sp) }, onClick = onRefresh, enabled = !loading)
            ActionIconButton(icon = { ExitIcon() }, onClick = onLogout, isRed = true)
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
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            StatGlassCard(icon = "👥", label = "کل", value = "$totalUsers", accent = theme.lamp.primary, modifier = Modifier.weight(1f))
            StatGlassCard(icon = "🟢", label = "فعال", value = "$activeUsers", accent = GlassGreen, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            StatGlassCard(icon = "⚡", label = "آنلاین", value = "$onlineUsers", accent = Color(0xFF0EA89B), modifier = Modifier.weight(1f))
            StatGlassCard(icon = "📊", label = "ترافیک", value = formatBytes(totalUsedTraffic), accent = Color(0xFFD9822B), modifier = Modifier.weight(1f))
        }
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
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(8.dp)).padding(1.5.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                ViewModeIcon("⊞", viewMode == ViewMode.GRID) { onViewModeChange(ViewMode.GRID) }
                ViewModeIcon("☰", viewMode == ViewMode.COMPACT_LIST) { onViewModeChange(ViewMode.COMPACT_LIST) }
                ViewModeIcon("≡", viewMode == ViewMode.MICRO_LIST) { onViewModeChange(ViewMode.MICRO_LIST) }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) theme.lamp.primary else glassBg(theme.isDark)).border(BorderStroke(1.dp, if (selected) theme.lamp.primary else glassBorder(theme.isDark)), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontSize = 9.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) theme.lamp.primary.copy(0.16f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.5.dp)) {
        Text(label, color = if (selected) theme.lamp.primary else theme.mutedColor, fontSize = 8.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) theme.lamp.primary.copy(0.16f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.5.dp), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 10.5.sp, color = if (selected) theme.lamp.primary else theme.mutedColor, fontWeight = FontWeight.Bold)
    }
}

// FIX 1: Progress bar visible, thicker, 8dp, gray track 28%
@Composable
private fun CheckboxIcon(selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val isDark = theme.isDark
    val bg = if (selected) theme.lamp.primary else if (isDark) Color(0xFF383842).copy(alpha = 0.92f) else Color(0xFFC8C4B8).copy(alpha = 0.92f)
    val borderCol = if (selected) theme.lamp.primary else if (isDark) Color(0xFF8E8C98) else Color(0xFF8C877D)
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(BorderStroke(1.2.dp, borderCol), RoundedCornerShape(6.dp))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val strokeWidth = 2.2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(2.dp.toPx(), 6.5.dp.toPx()),
                    end = Offset(5.dp.toPx(), 9.5.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(5.dp.toPx(), 9.5.dp.toPx()),
                    end = Offset(10.dp.toPx(), 3.5.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

// FIX 2: Online dot
// FIX 3: GB / GB and days left
@Composable
private fun LuxuryGridCard(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val displayProgress = if (user.dataLimit == 0L) 0.08f else actualProgress.coerceAtLeast(0.08f)
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (selected) theme.lamp.primary.copy(0.12f) else glassBg(theme.isDark)).border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) theme.lamp.primary else glassBorder(theme.isDark)), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp).background(statusColor))
        Column(Modifier.padding(start = 3.dp).padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(onlineDot))
                Text(user.username, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (user.note?.isNotBlank() == true) Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3B82F6).copy(0.14f)), contentAlignment = Alignment.Center) { Text("📝", fontSize = 8.sp) }
            }
            Text(if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / نامحدود" else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${daysLeftFull(user.expire)}", fontSize = 9.5.sp, color = theme.mutedColor, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(10.dp)).background(trackBg(theme.isDark))) {
                Box(Modifier.fillMaxWidth(displayProgress).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(progressColor))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.subUrl.isNotBlank()) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(0.10f)).border(BorderStroke(0.8.dp, Color.White.copy(0.14f)), RoundedCornerShape(7.dp)).clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                        android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) { Text("📋", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(0.10f)).border(BorderStroke(0.8.dp, Color.White.copy(0.14f)), RoundedCornerShape(7.dp)).clickable { onQrClick(user) }.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) { Text("📱 QR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                }
                Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(if (user.isOnline) GlassGreen.copy(0.12f) else Color.Gray.copy(0.10f)).border(BorderStroke(0.8.dp, if (user.isOnline) GlassGreen.copy(0.18f) else Color.Gray.copy(0.12f)), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text(if (user.isOnline) "🟢" else "⚫", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (user.isOnline) GlassGreen else Color.Gray)
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
private fun LuxuryCompactRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0.08f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (selected) theme.lamp.primary.copy(0.12f) else glassBg(theme.isDark)).border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) theme.lamp.primary else glassBorder(theme.isDark)), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1.1f)) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(onlineDot))
                Text(user.username, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / نامحدود" else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}", fontSize = 10.5.sp, color = theme.inkColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(daysLeftFull(user.expire), fontSize = 9.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(10.dp)).background(trackBg(theme.isDark))) {
                    Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(10.dp)))
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.subUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(0.12f))
                            .border(BorderStroke(0.8.dp, Color.White.copy(0.16f)), RoundedCornerShape(7.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("📋", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(0.12f))
                            .border(BorderStroke(0.8.dp, Color.White.copy(0.16f)), RoundedCornerShape(7.dp))
                            .clickable { onQrClick(user) }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("📱 QR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                }
            }
        }
    }
}

@Composable
private fun LuxuryMicroRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val p = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0.08f
    val progressColor = when { p < 70 -> GlassGreen; p in 70..89 -> GlassAmber; else -> GlassRed }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) theme.lamp.primary.copy(0.12f) else glassBg(theme.isDark)).border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) theme.lamp.primary else glassBorder(theme.isDark)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckboxIcon(selected = selected, onToggle = onSelectToggle)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(onlineDot))
            Text(user.username, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.width(88.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.width(125.dp)) {
                Text("${formatBytes(user.usedTraffic)}/${if (user.dataLimit == 0L) "∞" else formatBytes(user.dataLimit)} • ${daysLeftFull(user.expire)}", fontSize = 9.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(6.dp)).background(trackBg(theme.isDark))) { Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor)) }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.subUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(0.10f))
                            .border(BorderStroke(0.8.dp, Color.White.copy(0.14f)), RoundedCornerShape(7.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("📋", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(0.10f))
                            .border(BorderStroke(0.8.dp, Color.White.copy(0.14f)), RoundedCornerShape(7.dp))
                            .clickable { onQrClick(user) }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("📱 QR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                }
            }
        }
    }
}

@Composable
fun UsersScreen(
    session: Session,
    onLogout: () -> Unit,
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    isAppLockEnabled: Boolean = false,
    onAppLockChange: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var qrUser by remember { mutableStateOf<PanelUser?>(null) }
    var onlineCount by remember { mutableStateOf(0) }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(com.mrm.pgmanager.data.model.UserSort.CREATED) }
    var viewMode by remember { mutableStateOf(ViewMode.MICRO_LIST) }
    var selectedUserIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkTemplateDialog by remember { mutableStateOf(false) }

    // Collapsing header state for the 4 top stat buttons/cards (Dynamic measurement = exact alignment & zero gaps)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statsCardsHeightPx = remember { mutableStateOf(0f) }
    val totalHeaderHeightPx = remember { mutableStateOf(0f) }

    val fallbackStatsPx = remember(density) { with(density) { 104.dp.toPx() } }
    val headerHeight = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else fallbackStatsPx
    val fallbackTotalDp = 242.dp
    val totalHeaderDp = if (totalHeaderHeightPx.value > 0f) with(density) { totalHeaderHeightPx.value.toDp() } else fallbackTotalDp
    val scrollOffset = remember { mutableStateOf(0f) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val list = PanelApi.users(session)
                val sysOnline = PanelApi.onlineUserCount(session)
                users = list; onlineCount = maxOf(sysOnline, list.count { it.isOnline })
                scrollOffset.value = 0f
            }.onFailure {
                error = it.message
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                }
            }
            loading = false
        }
    }
    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }.onFailure {
                error = it.message
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                }
            }.onSuccess { load() }
        }
    }
    LaunchedEffect(Unit) { load() }

    val processedUsers = remember(users, query, currentFilter, currentSort) {
        var list = users.filter { it.username.contains(query, ignoreCase = true) }
        list = when (currentFilter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.status == "active" }
            UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= 0.72 }
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
            Box(modifier = Modifier.padding(bottom = 66.dp).clip(RoundedCornerShape(26.dp)).background(themeState.lamp.primary).clickable { createUser = true }.padding(horizontal = 20.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("کاربر جدید", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }) { padding ->
        val topInsets = padding.calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // 1. Lists / Grid (Fixed outer Box size = 0 Remeasurements during scroll!)
            // We use dynamic totalHeaderDp measured accurately on screen + lockstep offset so there is ZERO empty gap above item #1 during collapse!
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .offset {
                        val current = scrollOffset.value.coerceIn(0f, headerHeight)
                        IntOffset(0, -current.roundToInt())
                    }
            ) {
                when {
                    loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(6) { SkeletonCard() } }
                    error != null -> Box(Modifier.fillMaxWidth().padding(top = totalHeaderDp + topInsets + 4.dp).clip(RoundedCornerShape(20.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(20.dp)).padding(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️ خطا", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                            Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                            com.mrm.pgmanager.ui.components.GlassButton("🔄 تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    processedUsers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = totalHeaderDp + topInsets + 4.dp).clip(RoundedCornerShape(24.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, glassBorder(themeState.isDark)), RoundedCornerShape(24.dp)).padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🔍", fontSize = 36.sp); Text("کاربری یافت نشد", fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                        }
                    }
                    else -> when (viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(processedUsers) { user -> LuxuryGridCard(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrUser = it }) } }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(processedUsers) { user -> LuxuryCompactRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrUser = it }) } }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(processedUsers) { user -> LuxuryMicroRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrUser = it }) } }
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
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(if (themeState.isDark) Color(0xFF141418).copy(alpha = 0.98f) else Color(0xFFFAF5EC).copy(alpha = 0.98f))
                    .border(BorderStroke(1.2.dp, glassBorder(themeState.isDark)), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .padding(top = topInsets)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp)
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
                FilterAndControlBar(currentFilter = currentFilter, onFilterChange = { currentFilter = it }, currentSort = currentSort, onSortChange = { currentSort = it }, viewMode = viewMode, onViewModeChange = { viewMode = it }, users = users)
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
                        onDelete = {
                            val ids = selectedUserIds.toSet()
                            selectedUserIds = emptySet()
                            runAction { PanelApi.bulkDeleteUsers(session, ids) }
                        },
                        onResetUsage = {
                            val ids = selectedUserIds.toSet()
                            selectedUserIds = emptySet()
                            runAction { PanelApi.bulkResetUsersUsage(session, ids) }
                        },
                        onDisable = {
                            val ids = selectedUserIds.toSet()
                            selectedUserIds = emptySet()
                            runAction { PanelApi.bulkDisableUsers(session, ids) }
                        },
                        onEnable = {
                            val ids = selectedUserIds.toSet()
                            selectedUserIds = emptySet()
                            runAction { PanelApi.bulkEnableUsers(session, ids) }
                        },
                        onApplyTemplate = {
                            showBulkTemplateDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showBulkTemplateDialog) {
        var templates by remember { mutableStateOf<List<com.mrm.pgmanager.data.model.UserTemplateItem>>(emptyList()) }
        LaunchedEffect(Unit) {
            templates = PanelApi.userTemplates(session)
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
            }
        )
    }

    if (showThemeDialog) {
        ThemeEditorDialog(
            themeState = themeState,
            isAppLockEnabled = isAppLockEnabled,
            onDismiss = { showThemeDialog = false },
            onThemeChange = onThemeChange,
            onAppLockChange = onAppLockChange
        )
    }
    selectedUser?.let { user ->
        UserEditorDialog(initial = user, onDismiss = { selectedUser = null }, onSave = { limitGb, expireShamsi ->
            selectedUser = null; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
        }, onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } }, onDelete = { deleteUser = user; selectedUser = null }, onResetUsage = {
            runAction { PanelApi.resetUsage(session, user.username); val refreshed = PanelApi.users(session); users = refreshed; selectedUser = refreshed.find { it.username == user.username } }
        }, onResetExpiry = {
            runAction { PanelApi.modifyUser(session, user.username, (user.dataLimit.toDouble() / 1073741824.0), "", "", null, null); val refreshed = PanelApi.users(session); users = refreshed; selectedUser = refreshed.find { it.username == user.username } }
        }, session = session)
    }
    if (createUser) UserEditorDialog(initial = null, onDismiss = { createUser = false }, onSave = { limitGb, expireShamsi ->
        createUser = false; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.createUser(session, limitGb.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
    }, onToggle = null, onDelete = null, onResetUsage = null, onResetExpiry = null, session = session)
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
                        com.mrm.pgmanager.ui.components.GlassButton("حذف", onClick = { deleteUser = null; runAction { PanelApi.deleteUser(session, user.username) } }, modifier = Modifier.weight(1f), isRed = true)
                    }
                }
            }
        }
    }
    qrUser?.let { user ->
        SubscriptionQrDialog(user = user, onDismiss = { qrUser = null })
    }
}
