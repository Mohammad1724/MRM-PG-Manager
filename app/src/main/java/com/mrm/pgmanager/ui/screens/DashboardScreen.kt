package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Composable
fun DashboardScreen(session: Session) {
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() { loading = true; error = null; runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it }.onFailure { error = it.message ?: "خطا در دریافت آمار" }; loading = false }
    // آمار لحظه‌ای سیستم مانند پنل: هر ۵ ثانیه CPU/RAM/Disk و کاربران دوباره خوانده می‌شوند.
    LaunchedEffect(session) { while (kotlinx.coroutines.currentCoroutineContext().isActive) { load(); kotlinx.coroutines.delay(10_000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("داشبورد", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor); Text("وضعیت سیستم، ترافیک و کاربران", fontSize = 11.sp, color = theme.mutedColor) }
            Box(Modifier.size(40.dp).background(theme.lamp.primary.copy(.16f), RoundedCornerShape(11.dp)).clickable { scope.launch { load() } }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Refresh, tint = theme.inkColor, size = 19.dp) }
        }
        if (loading && stats == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.lamp.primary) }
        error?.let { Text(it, color = Color(0xFFC93B3B), fontSize = 12.sp) }
        stats?.let { s ->
            Text("سیستم", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("CPU", "${"%.1f".format(s.cpuUsage)}%", AppIcon.Settings, Modifier.weight(1f)); DashCard("RAM", "${formatBytes(s.memUsed)} / ${formatBytes(s.memTotal)}", AppIcon.Template, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("Disk", "${formatBytes(s.diskUsed)} / ${formatBytes(s.diskTotal)}", AppIcon.Template, Modifier.weight(1f)); DashCard("Uptime", "${s.uptimeSeconds / 86400} روز", AppIcon.Calendar, Modifier.weight(1f)) }
            Text("کاربران", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("کل کاربران", "${s.totalUsers}", AppIcon.Users, Modifier.weight(1f)); DashCard("آنلاین", "${s.onlineUsers}", AppIcon.User, Modifier.weight(1f), GlassGreen) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("فعال", "${s.activeUsers}", AppIcon.Check, Modifier.weight(1f)); DashCard("منقضی / محدود", "${s.expiredUsers + s.limitedUsers}", AppIcon.Warning, Modifier.weight(1f), Color(0xFFD9822B)) }
            Text("ترافیک", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("دریافت", formatBytes(s.incomingBandwidth), AppIcon.Refresh, Modifier.weight(1f), GlassGreen); DashCard("ارسال", formatBytes(s.outgoingBandwidth), AppIcon.Link, Modifier.weight(1f)) }
        }
    }
}

@Composable private fun DashCard(label: String, value: String, icon: AppIcon, modifier: Modifier, accent: Color? = null) {
    val t = LocalThemeState.current; val c = accent ?: t.lamp.primary
    Column(modifier.height(92.dp).background(Color.White, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, glassBorder(t.isDark)), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { RoundedAppIcon(icon, tint = c, size = 16.dp); Text(label, fontSize = 10.sp, color = t.mutedColor) }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = t.inkColor, maxLines = 1)
    }
}
