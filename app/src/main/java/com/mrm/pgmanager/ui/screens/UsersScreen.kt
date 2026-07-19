package com.mrm.pgmanager.ui.screens

import android.content.Context
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
private fun trackBg(isDark: Boolean) = Color.White.copy(alpha = if (isDark) 0.22f else 0.32f)

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
            FilterChipItem("غیرفعال", currentFilter == UserFilter.DISABLED) { onFilterChange(UserFilter.DISABLED) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مرتب:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                SortPill("نام", currentSort == com.mrm.pgmanager.data.model.UserSort.NAME) { onSortChange(com.mrm.pgmanager.data.model.UserSort.NAME) }
                SortPill("مصرف", currentSort == com.mrm.pgmanager.data.model.UserSort.USAGE) { onSortChange(com.mrm.pgmanager.data.model.UserSort.USAGE) }
                SortPill("انقضا", currentSort == com.mrm.pgmanager.data.model.UserSort.EXPIRY) { onSortChange(com.mrm.pgmanager.data.model.UserSort.EXPIRY) }
                SortPill("ساخت", currentSort == com.mrm.pgmanager.data.model.UserSort.CREATED) { onSortChange(com.mrm.pgmanager.data.model.UserSort.CREATED) }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
    val scale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (selected) 1.02f else 1f, label = "chip")
    Box(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(14.dp)).background(if (selected) theme.lamp.primary else glassBg(theme.isDark)).border(BorderStroke(1.dp, if (selected) theme.lamp.primary else glassBorder(theme.isDark)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) theme.lamp.primary.copy(0.14f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, color = if (selected) theme.lamp.primary else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) theme.lamp.primary.copy(0.14f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 13.sp, color = if (selected) theme.lamp.primary else theme.mutedColor, fontWeight = FontWeight.Bold)
    }
}

// FIX 1: Progress bar visible, thicker, 6dp, track 24% visible
// FIX 2: Online dot green/gray left of username
// FIX 3: Show GB / GB and days left
@Composable
private fun LuxuryGridCard(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0.06f // FIX 1: if unlimited or new, show minimal 6% so bar visible
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E) // FIX 2

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp).background(statusColor))
        Column(Modifier.padding(start = 3.dp).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // FIX 2: Dot left of username
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(onlineDot)) // online dot
                Text(user.username, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (user.isOnline) Text("آنلاین", fontSize = 9.sp, color = GlassGreen, fontWeight = FontWeight.Bold)
            }
            // FIX 3: GB / GB
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / نامحدود" else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${daysLeftFull(user.expire)} • ${if (user.dataLimit == 0L) "∞" else "$progressPercent%"}", fontSize = 10.sp, color = theme.mutedColor)
                }
            }
            // FIX 1: Thicker, more visible track
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)).background(trackBg(theme.isDark))) {
                Box(Modifier.fillMaxWidth(if (user.dataLimit == 0L) 0.06f else actualProgress).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(progressColor))
            }
        }
    }
}

@Composable
private fun LuxuryCompactRow(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0.06f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(onlineDot))
                Column(Modifier.widthIn(min = 100.dp, max = 160.dp)) {
                    Text(user.username, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (user.dataLimit == 0L) formatBytes(user.usedTraffic) + " / نامحدود" else formatBytes(user.usedTraffic) + " / " + formatBytes(user.dataLimit)} • ${daysLeftFull(user.expire)}", fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.width(130.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)).background(trackBg(theme.isDark))) { Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(10.dp))) }
                Spacer(Modifier.height(3.dp))
                Text("${if (user.dataLimit == 0L) "∞" else "$progressPercent%"} • ${daysLeftFull(user.expire)}", fontSize = 10.sp, color = theme.mutedColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.subUrl.isNotEmpty()) MiniGlassButton("📋") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                    android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
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
    val actualProgress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0.06f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val onlineDot = if (user.isOnline) GlassGreen else Color(0xFF9E9E9E)

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(onlineDot))
            Text(user.username, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.widthIn(min = 80.dp, max = 120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.width(140.dp)) {
                Text("${formatBytes(user.usedTraffic)} / ${if (user.dataLimit == 0L) "∞" else formatBytes(user.dataLimit)} • ${daysLeftFull(user.expire)}", fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(6.dp)).background(trackBg(theme.isDark))) { Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.subUrl.isNotEmpty()) MiniGlassButton("📋") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                    android.widget.Toast.makeText(context, "کپی", android.widget.Toast.LENGTH_SHORT).show()
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

    // FIX 4: Removed nested scroll that caused jump - header always visible now
    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(com.mrm.pgmanager.data.model.UserSort.CREATED) }
    var viewMode by remember { mutableStateOf(ViewMode.MICRO_LIST) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val list = PanelApi.users(session)
                val sysOnline = PanelApi.onlineUserCount(session)
                users = list; onlineCount = maxOf(sysOnline, list.count { it.isOnline })
            }.onFailure {
                error = it.message; if (it.message?.contains("401") == true) onLogout()
            }
            loading = false
        }
    }
    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }.onFailure { error = it.message; if (it.message?.contains("401") == true) onLogout() }.onSuccess { load() }
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

    Scaffold(containerColor = Color.Transparent, floatingActionButton = {
        Box(modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(themeState.lamp.primary).clickable { createUser = true }.padding(horizontal = 20.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("کاربر جدید", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 13.sp)
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            // FIX 4: Header always visible, no AnimatedVisibility jump
            LuxuryTopStatsHeader(totalUsers = users.size, activeUsers = users.count { it.status == "active" }, onlineUsers = onlineCount, totalUsedTraffic = totalUsed, onRefresh = { load() }, onLogout = onLogout, onOpenThemeDialog = { showThemeDialog = true }, loading = loading)
            Spacer(Modifier.height(8.dp))
            GlassSearchBar(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(12.dp))
            FilterAndControlBar(currentFilter = currentFilter, onFilterChange = { currentFilter = it }, currentSort = currentSort, onSortChange = { currentSort = it }, viewMode = viewMode, onViewModeChange = { viewMode = it }, users = users)
            Spacer(Modifier.height(14.dp))
            when {
                loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(6) { SkeletonCard() } }
                error != null -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ خطا", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                        Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                        com.mrm.pgmanager.ui.components.GlassButton("🔄 تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                processedUsers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(36.dp).clip(RoundedCornerShape(24.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, glassBorder(themeState.isDark)), RoundedCornerShape(24.dp)).padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🔍", fontSize = 36.sp); Text("کاربری یافت نشد", fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                    }
                }
                else -> when (viewMode) {
                    ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryGridCard(user, onClick = { selectedUser = user }) } }
                    ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryCompactRow(user, onClick = { selectedUser = user }) } }
                    ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryMicroRow(user, onClick = { selectedUser = user }) } }
                }
            }
        }
    }

    if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)
    selectedUser?.let { user ->
        UserEditorDialog(initial = user, onDismiss = { selectedUser = null }, onSave = { limitGb, expireShamsi ->
            selectedUser = null; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user.username, limitGb.value, iso) }
        }, onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } }, onDelete = { deleteUser = user; selectedUser = null }, onResetUsage = {
            runAction { PanelApi.resetUsage(session, user.username); val refreshed = PanelApi.users(session); users = refreshed; selectedUser = refreshed.find { it.username == user.username } }
        }, onResetExpiry = {
            runAction { PanelApi.modifyUser(session, user.username, (user.dataLimit.toDouble() / 1073741824.0), ""); val refreshed = PanelApi.users(session); users = refreshed; selectedUser = refreshed.find { it.username == user.username } }
        })
    }
    if (createUser) UserEditorDialog(initial = null, onDismiss = { createUser = false }, onSave = { limitGb, expireShamsi ->
        createUser = false; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.createUser(session, limitGb.username, limitGb.value, iso) }
    }, onToggle = null, onDelete = null, onResetUsage = null, onResetExpiry = null)
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
}
