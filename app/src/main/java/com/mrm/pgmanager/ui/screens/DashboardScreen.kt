package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.mrm.pgmanager.data.model.TrafficPoint
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.utils.NotificationHelper
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(session: Session, settings: MonitoringSettings, onSettings: () -> Unit, onLogout: () -> Unit) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var cpuAlerted by remember { mutableStateOf(false) }
    var ramAlerted by remember { mutableStateOf(false) }
    var diskAlerted by remember { mutableStateOf(false) }
    var panelOfflineAlerted by remember { mutableStateOf(false) }
    var lastNodeStates by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trafficPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    fun evaluateHealth(s: SystemStats) {
        if (!settings.notificationsEnabled || !settings.notifySystemHealth) return
        fun alert(id: Int, title: String, message: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_SYSTEM, title, message)
        if (s.cpuUsage >= settings.cpuThreshold) { if (!cpuAlerted) alert(3101, "هشدار CPU", "مصرف CPU به ${"%.1f".format(s.cpuUsage)}٪ رسیده است"); cpuAlerted = true } else cpuAlerted = false
        val ram = if (s.memTotal > 0) (s.memUsed * 100 / s.memTotal).toInt() else 0
        if (ram >= settings.ramThreshold) { if (!ramAlerted) alert(3102, "هشدار RAM", "مصرف RAM به $ram٪ رسیده است"); ramAlerted = true } else ramAlerted = false
        val disk = if (s.diskTotal > 0) (s.diskUsed * 100 / s.diskTotal).toInt() else 0
        if (disk >= settings.diskThreshold) { if (!diskAlerted) alert(3103, "هشدار Disk", "مصرف Disk به $disk٪ رسیده است"); diskAlerted = true } else diskAlerted = false
    }
    suspend fun load() { loading = true; error = null; runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it; panelOfflineAlerted = false; evaluateHealth(it) }.onFailure { e -> error = e.message ?: "خطا در دریافت آمار"; if (settings.notificationsEnabled && settings.notifyPanelOffline && !panelOfflineAlerted) { NotificationHelper.post(context, 3104, NotificationHelper.CHANNEL_SYSTEM, "اتصال به پنل ناموفق", "دریافت آمار Dashboard از پنل PasarGuard ناموفق بود"); panelOfflineAlerted = true } }; runCatching { PanelApi.trafficUsage(session) }.onSuccess { trafficPoints = it }
        runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
            if (settings.notificationsEnabled && settings.notifyNodeOffline && lastNodeStates.isNotEmpty()) states.forEach { (id, online) ->
                val previous = lastNodeStates[id]
                if (previous == true && !online) NotificationHelper.post(context, 4100 + id, NotificationHelper.CHANNEL_SYSTEM, "نود آفلاین شد", "نود شماره $id در دسترس نیست")
                if (previous == false && online) NotificationHelper.post(context, 4200 + id, NotificationHelper.CHANNEL_SYSTEM, "نود دوباره آنلاین شد", "نود شماره $id دوباره در دسترس است")
            }
            lastNodeStates = states
        }; loading = false }
    // آمار لحظه‌ای سیستم مانند پنل: هر ۵ ثانیه CPU/RAM/Disk و کاربران دوباره خوانده می‌شوند.
    LaunchedEffect(session, settings.autoRefreshEnabled, settings.refreshIntervalSeconds) { if (settings.autoRefreshEnabled) while (kotlinx.coroutines.currentCoroutineContext().isActive) { load(); kotlinx.coroutines.delay(settings.refreshIntervalSeconds.coerceIn(5, 3600) * 1_000L) } else load() }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = manualRefreshing, onRefresh = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, state = pullState, modifier = Modifier.fillMaxSize(), indicator = { PullToRefreshDefaults.Indicator(isRefreshing = manualRefreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter)) }) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("داشبورد", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
LiveStatusBadge(settings.autoRefreshEnabled, settings.refreshIntervalSeconds)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(40.dp).background(Color.White, RoundedCornerShape(11.dp)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(11.dp)).clickable { onSettings() }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 19.dp) }
                Box(Modifier.size(40.dp).background(theme.lamp.primary.copy(.16f), RoundedCornerShape(11.dp)).clickable { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, contentAlignment = Alignment.Center) { if (manualRefreshing) CircularProgressIndicator(Modifier.size(18.dp), color = theme.lamp.primary, strokeWidth = 2.dp) else RoundedAppIcon(AppIcon.Refresh, tint = theme.inkColor, size = 19.dp) }
                Box(Modifier.size(40.dp).background(Color(0xFFC93B3B).copy(.10f), RoundedCornerShape(11.dp)).clickable { onLogout() }, contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Logout, tint = Color(0xFFC93B3B), size = 19.dp) }
            }
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
            TrafficChartCard(points = trafficPoints, incoming = s.incomingBandwidth, outgoing = s.outgoingBandwidth)
        }
    }
    }
}

@Composable
private fun LiveStatusBadge(enabled: Boolean, seconds: Int) {
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "liveAlpha")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(GlassGreen.copy(alpha), RoundedCornerShape(4.dp)))
        Text(if (enabled) "زنده · بروزرسانی هر $seconds ثانیه" else "بروزرسانی خودکار خاموش", fontSize = 10.sp, color = LocalThemeState.current.mutedColor)
    }
}

@Composable
private fun TrafficChartCard(points: List<TrafficPoint>, incoming: Long, outgoing: Long) {
    val t = LocalThemeState.current
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, glassBorder(t.isDark)), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("نمودار مصرف زنده", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.inkColor)
        Text("دریافت ${formatBytes(incoming)} · ارسال ${formatBytes(outgoing)}", fontSize = 9.sp, color = t.mutedColor)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width; val h = size.height
            for (i in 1..4) drawLine(Color(0xFFE8E8EC), androidx.compose.ui.geometry.Offset(0f, h * i / 5f), androidx.compose.ui.geometry.Offset(w, h * i / 5f), 1f)
            val max = points.maxOfOrNull { it.totalTraffic }?.coerceAtLeast(1L) ?: 1L
            if (points.size > 1) for (i in 0 until points.lastIndex) {
                val y1 = h - (points[i].totalTraffic.toFloat() / max * h)
                val y2 = h - (points[i + 1].totalTraffic.toFloat() / max * h)
                drawLine(t.lamp.primary, androidx.compose.ui.geometry.Offset(w*i/(points.size-1), y1), androidx.compose.ui.geometry.Offset(w*(i+1)/(points.size-1), y2), 4f)
            }
        }
        Text(if (points.isEmpty()) "دادهٔ تاریخی ترافیک از پنل دریافت نشد." else "${points.size} نقطهٔ واقعی از آمار ترافیک پنل", fontSize = 8.sp, color = t.mutedColor)
    }
}

@Composable private fun DashCard(label: String, value: String, icon: AppIcon, modifier: Modifier, accent: Color? = null) {
    val t = LocalThemeState.current; val c = accent ?: t.lamp.primary
    Column(modifier.height(92.dp).background(Color.White, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, glassBorder(t.isDark)), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { RoundedAppIcon(icon, tint = c, size = 16.dp); Text(label, fontSize = 10.sp, color = t.mutedColor) }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = t.inkColor, maxLines = 1)
    }
}
