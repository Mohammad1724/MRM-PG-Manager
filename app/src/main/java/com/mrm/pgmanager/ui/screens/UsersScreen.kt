package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private fun glassBg(isDark: Boolean) = if (isDark) Color(0xFF1E1E24).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.22f)
private fun glassBorder(isDark: Boolean) = Color.White.copy(alpha = if (isDark) 0.12f else 0.28f)
// FIX: Track more gray and visible - 30% gray instead of 12% white
private fun trackBg(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.24f) else Color(0xFF8A8A8A).copy(alpha = 0.28f)

private fun daysLeftText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    return try {
        val exp = LocalDate.parse(expire.take(10))
        val now = LocalDate.now()
        val diff = ChronoUnit.DAYS.between(now, exp)
        when {
            diff < 0 -> "منقضی"
            diff == 0L -> "امروز"
            diff == 1L -> "۱ روز"
            diff <= 7 -> "$diff روز"
            diff <= 30 -> "${diff} روز"
            else -> "${diff} روز"
        }
    } catch (e: Exception) { JalaliCalendar.isoToShamsi(expire).ifEmpty { "نامحدود" } }
}

private fun daysLeftFull(expire: String?): String = daysLeftText(expire)

@Composable
private fun StatGlassCard(icon: String, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(20.dp)).padding(13.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(0.14f)).border(BorderStroke(1.dp, accent.copy(0.18f)), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 13.sp) }
                Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
            }
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Box(modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🔍", fontSize = 15.sp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("جستجو کاربر...", color = theme.mutedColor.copy(0.6f), fontSize = 13.sp)
                BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true, textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.12f)).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) { Text("×", color = theme.inkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LuxuryTopStatsHeader(totalUsers: Int, activeUsers: Int, onlineUsers: Int, totalUsedTraffic: Long, onRefresh: () -> Unit, onLogout: () -> Unit, onOpenThemeDialog: () -> Unit, loading: Boolean) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLogo(height = 26.dp)
                Column {
                    Text("Pasarguard", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = theme.inkColor)
                    Text("MRM Manager", fontSize = 11.sp, color = theme.mutedColor)
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
                StatGlassCard(icon = "👥", label = "کل", value = "$totalUsers", accent = theme.lamp.primary, modifier = Modifier.weight(1f))
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
private fun FilterAndControlBar(currentFilter: UserFilter, onFilterChange: (UserFilter) -> Unit, currentSort: com.mrm.pgmanager.data.model.UserSort, onSortChange: (com.mrm.pgmanager.data.model.UserSort) -> Unit, viewMode: ViewMode, onViewModeChange: (ViewMode) -> Unit, users: List<PanelUser>) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipItem("همه ${users.size}", currentFilter == UserFilter.ALL) { onFilterChange(UserFilter.ALL) }
            FilterChipItem("فعال ${users.count { it.status == "active" }}", currentFilter == UserFilter.ACTIVE) { onFilterChange(UserFilter.ACTIVE) }
            FilterChipItem("لب مرز", currentFilter == UserFilter.NEAR_LIMIT) { onFilterChange(UserFilter.NEAR_LIMIT) }
            FilterChipItem("منقضی", currentFilter == UserFilter.EXPIRED) { onFilterChange(UserFilter.EXPIRED) }
