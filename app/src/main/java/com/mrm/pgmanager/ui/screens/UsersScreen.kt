package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.UserSort
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

@Composable
private fun StatGlassCard(icon: String, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(theme.cardBgColor)
            .border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(20.dp)).padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.18f))
                        .border(BorderStroke(1.dp, accent.copy(0.25f)), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 14.sp) }
                Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().background(accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.35f, targetValue = 0.75f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(theme.cardBgColor.copy(alpha = alpha)).border(BorderStroke(1.dp, Color.White.copy(0.15f)), RoundedCornerShape(20.dp)).height(110.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(theme.searchBgColor)
            .border(BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(Color.White.copy(if (theme.isDark) 0.35f else 0.94f), theme.lamp.light.copy(0.55f), Color.White.copy(if (theme.isDark) 0.12f else 0.38f)))), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(theme.lamp.primary.copy(0.14f)), contentAlignment = Alignment.Center) { Text("🔍", fontSize = 14.sp) }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("جستجو کاربر ... Search", color = theme.mutedColor.copy(0.65f), fontSize = 13.sp)
                BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true, textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(if (theme.isDark) 0.18f else 0.75f)).clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) { Text("×", color = theme.inkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LuxuryTopStatsHeader(totalUsers: Int, activeUsers: Int, onlineUsers: Int, totalUsedTraffic: Long, onRefresh: () -> Unit, onLogout: () -> Unit, onOpenThemeDialog: () -> Unit, loading: Boolean) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.light)))
                        .border(BorderStroke(1.dp, Color.White.copy(0.75f)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("PG", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PasarGuard", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = theme.inkColor)
                        AppLogo(height = 22.dp)
                    }
                    Text("MRM Manager • v2.0 Luxury", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(icon = { Text("🎨", fontSize = 16.sp) }, onClick = onOpenThemeDialog)
                ActionIconButton(icon = { if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = theme.inkColor, strokeWidth = 2.dp) else Text("🔄", fontSize = 15.sp) }, onClick = onRefresh, enabled = !loading)
                ActionIconButton(icon = { ExitIcon() }, onClick = onLogout, isRed = true)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatGlassCard(icon = "👥", label = "کل کاربران", value = "$totalUsers", accent = theme.lamp.primary, modifier = Modifier.weight(1f))
                StatGlassCard(icon = "🟢", label = "فعال", value = "$activeUsers", accent = GlassGreen, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatGlassCard(icon = "⚡", label = "آنلاین", value = "$onlineUsers", accent = Color(0xFF0EA89B), modifier = Modifier.weight(1f))
                StatGlassCard(icon = "📊", label = "ترافیک", value = formatBytes(totalUsedTraffic), accent = Color(0xFFD9822B), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilterAndControlBar(currentFilter: UserFilter, onFilterChange: (UserFilter) -> Unit, currentSort: UserSort, onSortChange: (UserSort) -> Unit, viewMode: ViewMode, onViewModeChange: (ViewMode) -> Unit, users: List<PanelUser>) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChipItem("🌟 همه ${users.size}", currentFilter == UserFilter.ALL) { onFilterChange(UserFilter.ALL) }
            FilterChipItem("🟢 فعال ${users.count { it.status == "active" }}", currentFilter == UserFilter.ACTIVE) { onFilterChange(UserFilter.ACTIVE) }
            FilterChipItem("🟡 لب مرز", currentFilter == UserFilter.NEAR_LIMIT) { onFilterChange(UserFilter.NEAR_LIMIT) }
            FilterChipItem("🔴 منقضی", currentFilter == UserFilter.EXPIRED) { onFilterChange(UserFilter.EXPIRED) }
            FilterChipItem("⚪ غیرفعال", currentFilter == UserFilter.DISABLED) { onFilterChange(UserFilter.DISABLED) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مرتب:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                SortPill("نام", currentSort == UserSort.NAME) { onSortChange(UserSort.NAME) }
                SortPill("مصرف", currentSort == UserSort.USAGE) { onSortChange(UserSort.USAGE) }
                SortPill("انقضا", currentSort == UserSort.EXPIRY) { onSortChange(UserSort.EXPIRY) }
                SortPill("ساخت", currentSort == UserSort.CREATED) { onSortChange(UserSort.CREATED) }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (theme.isDark) Color.White.copy(0.1f) else Color.White.copy(0.60f))
                    .border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.22f) else Color.White.copy(0.9f)), RoundedCornerShape(12.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
    val scale by animateFloatAsState(targetValue = if (selected) 1.02f else 1f, label = "chipScale")
    Box(
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.primary.copy(0.75f))) else Brush.linearGradient(listOf(if (theme.isDark) Color.White.copy(0.10f) else Color.White.copy(0.50f), if (theme.isDark) Color.White.copy(0.06f) else Color.White.copy(0.28f))))
            .border(BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(if (theme.isDark) 0.18f else 0.65f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) (if (theme.isDark) Color.White.copy(0.18f) else Color.White.copy(0.95f)) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (selected) theme.lamp.primary else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) (if (theme.isDark) Color.White.copy(0.20f) else Color.White) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 13.sp, color = if (selected) theme.lamp.primary else theme.mutedColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LuxuryGridCard(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(theme.cardBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(4.dp).background(statusColor))
        Column(Modifier.padding(start = 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor).shadow(4.dp, RoundedCornerShape(4.dp), ambientColor = statusColor))
                Text(user.username, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (user.isOnline) Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(GlassGreen.copy(0.18f))
                        .border(BorderStroke(1.dp, GlassGreen.copy(0.3f)), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center
                ) { Text("●", fontSize = 9.sp, color = GlassGreen) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column { Text("مصرف", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor); Text(formatBytes(user.usedTraffic), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor) }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(progressColor.copy(0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = progressColor) }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.20f else 0.70f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(progressColor, progressColor.copy(0.7f)))))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (user.dataLimit == 0L) "نامحدود" else formatBytes(user.dataLimit), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                user.expire?.takeIf { it != "0" && it != "null" }?.let { iso ->
                    val shamsi = JalaliCalendar.isoToShamsi(iso)
                    Box(Modifier.background(if (theme.isDark) Color.White.copy(0.14f) else Color.White.copy(0.80f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text(shamsi, fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxuryCompactRow(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.cardBgColor).border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.16f) else Color.White.copy(0.80f)), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(statusColor))
                Column(Modifier.widthIn(min = 110.dp, max = 160.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(user.username, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.isOnline) Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(GlassGreen))
                    }
                    Text(user.expire?.takeIf { it != "0" && it != "null" }?.let { "انقضا: ${JalaliCalendar.isoToShamsi(it)}" } ?: "بدون انقضا", fontSize = 11.sp, color = theme.mutedColor)
                }
            }
            Column(Modifier.width(145.dp), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(formatBytes(user.usedTraffic), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = progressColor)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.22f else 0.80f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(progressColor, RoundedCornerShape(10.dp)))
                }
            }
            Box(Modifier.width(1.dp).height(28.dp).background(theme.cardBorderBrush))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.widthIn(min = 110.dp)) {
                Text(if (user.isOnline) "🟢 آنلاین" else "⚫ آفلاین", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (user.isOnline) GlassGreen else theme.mutedColor)
                Text("ساخت: ${JalaliCalendar.isoToShamsi(user.createdAt ?: "")}", fontSize = 11.sp, color = theme.mutedColor)
            }
            Box(Modifier.width(1.dp).height(28.dp).background(theme.cardBorderBrush))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.subUrl.isNotEmpty()) MiniGlassButton("📋") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub URL", user.subUrl))
                    android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                }
                MiniGlassButton("✏") { onClick() }
            }
        }
    }
}

@Composable
private fun LuxuryMicroRow(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; else -> Color.Gray }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (theme.isDark) Color(0xFF222226).copy(0.55f) else Color.White.copy(0.40f)).border(BorderStroke(0.8.dp, if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(0.70f)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
                Text(user.username, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(min = 90.dp, max = 130.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.width(110.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(if (theme.isDark) 0.20f else 0.70f))) { Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(progressColor, RoundedCornerShape(6.dp))) }
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
            Text(formatBytes(user.usedTraffic), fontSize = 11.sp, color = theme.mutedColor, maxLines = 1)
            Text(if (user.isOnline) "🟢" else "⚫", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.subUrl.isNotEmpty()) MiniGlassButton("📋") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub URL", user.subUrl))
                    android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                }
                MiniGlassButton("✏") { onClick() }
            }
        }
    }
}

@Composable
fun UsersScreen(session: Session, onLogout: () -> Unit, themeState: ThemeState, onThemeChange: (ThemeState) -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var onlineCount by remember { mutableStateOf(0) }
    var isHeaderVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -20f && isHeaderVisible) isHeaderVisible = false
                else if (available.y > 20f && !isHeaderVisible) isHeaderVisible = true
                return Offset.Zero
            }
        }
    }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(com.mrm.pgmanager.data.model.UserSort.CREATED) }
    var viewMode by remember { mutableStateOf(ViewMode.MICRO_LIST) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val list = PanelApi.users(session)
                val sysOnline = PanelApi.onlineUserCount(session)
                users = list
                onlineCount = maxOf(sysOnline, list.count { it.isOnline })
            }.onFailure {
                error = it.message ?: "Unable to load users"
                if (it.message?.contains("401") == true) onLogout()
            }
            loading = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { action() }.onFailure {
                error = it.message ?: "Action failed"
                if (it.message?.contains("401") == true) onLogout()
            }.onSuccess { load() }
        }
    }

    LaunchedEffect(Unit) { load() }

    val processedUsers = remember(users, query, currentFilter, currentSort) {
        var list = users.filter { it.username.contains(query, ignoreCase = true) }
        list = when (currentFilter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.status == "active" }
            UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0) (it.usedTraffic.toDouble() / it.dataLimit.toDouble()) else 0.0; p >= 0.72 }
            UserFilter.EXPIRED -> list.filter { val p = if (it.dataLimit > 0) (it.usedTraffic.toDouble() / it.dataLimit.toDouble()) else 0.0; p >= 1.0 || it.status == "expired" || it.status == "limited" }
            UserFilter.DISABLED -> list.filter { it.status == "disabled" }
        }
        when (currentSort) {
            com.mrm.pgmanager.data.model.UserSort.NAME -> list.sortedBy { it.username.lowercase() }
            com.mrm.pgmanager.data.model.UserSort.USAGE -> list.sortedByDescending { it.usedTraffic }
            com.mrm.pgmanager.data.model.UserSort.EXPIRY -> list.sortedBy { it.expire ?: "9999" }
            com.mrm.pgmanager.data.model.UserSort.CREATED -> list.sortedByDescending { if (it.id > 0) it.id else (it.createdAt ?: "").hashCode().toLong() }
        }
    }

    val totalUsedTraffic = remember(users) { users.sumOf { it.usedTraffic } }

    Scaffold(containerColor = Color.Transparent, modifier = Modifier.nestedScroll(nestedScrollConnection), floatingActionButton = {
        Box(modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(themeState.lamp.primary, themeState.lamp.light))).border(BorderStroke(1.2.dp, Color.White.copy(0.9f)), RoundedCornerShape(26.dp)).clickable { createUser = true }.padding(horizontal = 20.dp, vertical = 13.dp).shadow(12.dp, RoundedCornerShape(26.dp), spotColor = themeState.lamp.primary.copy(0.4f)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(0.22f)), contentAlignment = Alignment.Center) { Text("+", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White) }
                Text("کاربر جدید", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 13.sp)
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            AnimatedVisibility(visible = isHeaderVisible, enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(180)), exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut(tween(150))) {
                Column {
                    LuxuryTopStatsHeader(totalUsers = users.size, activeUsers = users.count { it.status == "active" }, onlineUsers = onlineCount, totalUsedTraffic = totalUsedTraffic, onRefresh = { load() }, onLogout = onLogout, onOpenThemeDialog = { showThemeDialog = true }, loading = loading)
                    Spacer(Modifier.height(4.dp))
                }
            }
            GlassSearchBar(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(12.dp))
            FilterAndControlBar(currentFilter = currentFilter, onFilterChange = { currentFilter = it }, currentSort = currentSort, onSortChange = { currentSort = it }, viewMode = viewMode, onViewModeChange = { viewMode = it }, users = users)
            Spacer(Modifier.height(14.dp))
            when {
                loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(6) { SkeletonCard() } }
                error != null -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (themeState.isDark) Color.White.copy(0.08f) else Color.White.copy(0.62f)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ خطا در دریافت اطلاعات", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                        Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                        com.mrm.pgmanager.ui.components.GlassButton("🔄 تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                else -> {
                    if (processedUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(36.dp).clip(RoundedCornerShape(24.dp)).background(if (themeState.isDark) Color.White.copy(0.05f) else Color.White.copy(0.45f)).border(BorderStroke(1.dp, Color.White.copy(0.3f)), RoundedCornerShape(24.dp)).padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🔍", fontSize = 36.sp); Text("کاربری یافت نشد", fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                                Text("فیلتر یا جستجو را تغییر دهید", color = themeState.mutedColor, fontSize = 12.sp)
                            }
                        }
                    } else when (viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryGridCard(user, onClick = { selectedUser = user }) } }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryCompactRow(user, onClick = { selectedUser = user }) } }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryMicroRow(user, onClick = { selectedUser = user }) } }
                    }
                }
            }
        }
    }

    if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)

    selectedUser?.let { user ->
        UserEditorDialog(initial = user, onDismiss = { selectedUser = null }, onSave = { limitGb, expireShamsi ->
            selectedUser = null; runAction {
                val iso = JalaliCalendar.shamsiToIso(expireShamsi)
                PanelApi.modifyUser(session, user.username, limitGb.value, iso)
            }
        }, onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } }, onDelete = { deleteUser = user; selectedUser = null }, onResetUsage = {
            runAction {
                PanelApi.resetUsage(session, user.username); val refreshed = PanelApi.users(session); users = refreshed
                selectedUser = refreshed.find { it.username == user.username }
            }
        }, onResetExpiry = {
            runAction {
                PanelApi.modifyUser(session, user.username, (user.dataLimit.toDouble() / 1073741824.0), ""); val refreshed = PanelApi.users(session); users = refreshed
                selectedUser = refreshed.find { it.username == user.username }
            }
        })
    }

    if (createUser) UserEditorDialog(initial = null, onDismiss = { createUser = false }, onSave = { limitGb, expireShamsi ->
        createUser = false; runAction {
            val iso = JalaliCalendar.shamsiToIso(expireShamsi)
            PanelApi.createUser(session, limitGb.username, limitGb.value, iso)
        }
    }, onToggle = null, onDelete = null, onResetUsage = null, onResetExpiry = null)

    deleteUser?.let { user ->
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(GlassShape).background(theme.dialogBgColor)
                    .border(BorderStroke(1.2.dp, theme.cardBorderBrush), GlassShape)
            ) {
                Box(
                    Modifier.size(240.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-50).dp)
                        .background(Brush.radialGradient(listOf(theme.lamp.spotHigh, Color.Transparent)), shape = RoundedCornerShape(200.dp))
                )
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("آیا ${user.username} حذف شود؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text("این عملیات غیرقابل بازگشت است.", color = theme.mutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        com.mrm.pgmanager.ui.components.GlassButton("انصراف", onClick = { deleteUser = null }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        com.mrm.pgmanager.ui.components.GlassButton("حذف کاربر", onClick = { deleteUser = null; runAction { PanelApi.deleteUser(session, user.username) } }, modifier = Modifier.weight(1f), isRed = true)
                    }
                }
            }
        }
    }
}
