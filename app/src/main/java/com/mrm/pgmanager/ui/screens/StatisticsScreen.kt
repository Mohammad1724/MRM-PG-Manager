package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.TrafficPoint
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(session: Session, onSettings: () -> Unit) {
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var trafficPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf("Overall") }
    var timeRange by remember { mutableStateOf("More") }
    var adminFilter by remember { mutableStateOf("All admins") }
    var onlineFilter by remember { mutableStateOf("Online Users") }

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it }
        runCatching { PanelApi.trafficUsage(session) }.onSuccess { trafficPoints = it }
        loading = false
    }
    LaunchedEffect(session) { load() }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } }, state = pullState,
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = refreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter), containerColor = theme.cardSurfaceColor, color = com.mrm.pgmanager.ui.designsystem.DsAccent.Gold) }) {

        Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Screen, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Header
            Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Statistics", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFFFBEB)).border(BorderStroke(0.5.dp, Color(0xFFFDE68A)), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("○", fontSize = 7.sp, color = Color(0xFFCA8A04))
                        }
                    }
                    Text("Monitor your servers and users", fontSize = 10.sp, color = theme.mutedColor)
                }
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable { scope.launch { refreshing = true; load(true); refreshing = false } }, contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(14.dp), color = theme.mutedColor, strokeWidth = 1.6.dp) else RoundedAppIcon(AppIcon.Refresh, tint = theme.mutedColor, size = 16.dp)
                }
            }

            // ── Nodes selector
            Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Nodes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text("Select a node to view detailed statistics", fontSize = 10.sp, color = theme.mutedColor) }
                    PGDropdown(value = selectedNode, onClick = {})
                }
            }

            if (loading && stats == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsAccent.Gold) }
            }

            stats?.let { s ->
                // ── System
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Gauge, tint = Color(0xFFCA8A04), size = 14.dp); Text("System", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGStatCard(label = "CPU Usage", value = "${"%.1f".format(s.cpuUsage)}%", icon = AppIcon.Gauge, modifier = Modifier.weight(1f), trailing = { Text("${s.cpuCores} cores", fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) })
                        PGStatCard(label = "RAM Usage", value = "${formatBytes(s.memUsed)}/${formatBytes(s.memTotal)}", icon = AppIcon.Memory, modifier = Modifier.weight(1f), trailing = { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("${if (s.memTotal>0) (s.memUsed*100/s.memTotal).toInt() else 0}%", fontSize = 10.sp, color = theme.mutedColor) } })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGStatCard(label = "Disk Usage", value = "${formatBytes(s.diskUsed)}/${formatBytes(s.diskTotal)}", icon = AppIcon.Storage, modifier = Modifier.weight(1f), trailing = { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("${if (s.diskTotal>0) (s.diskUsed*100/s.diskTotal).toInt() else 0}%", fontSize = 10.sp, color = theme.mutedColor) } })
                        // Total Traffic with in/out - same 92dp height
                        Column(Modifier.weight(1f).height(92.dp).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(if (theme.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) DsAccent.Gold.copy(0.22f) else Color(0xFFFDE68A)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Storage, tint = if (theme.isDark) DsAccent.Gold else Color(0xFFCA8A04), size = 12.dp) }
                                Text("Total Traffic", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            Text(formatBytes(s.incomingBandwidth + s.outgoingBandwidth), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                    }
                    // Bandwidth - separate card
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(if (theme.isDark) Color(0xFF064E3B).copy(0.45f) else Color(0xFFDCFCE7)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) Color(0xFF10B981).copy(0.30f) else Color(0xFFBBF7D0)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Download, tint = if (theme.isDark) Color(0xFF6EE7B7) else Color(0xFF065F46), size = 14.dp) }
                                Text("Download", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            Text(formatBytes(s.incomingBandwidth), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1)
                        }
                        Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(if (theme.isDark) Color(0xFF1E3A8A).copy(0.35f) else Color(0xFFDBEAFE)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) Color(0xFF60A5FA).copy(0.30f) else Color(0xFFBFDBFE)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Upload, tint = if (theme.isDark) Color(0xFF93C5FD) else Color(0xFF1E3A8A), size = 14.dp) }
                                Text("Upload", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            Text(formatBytes(s.outgoingBandwidth), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(if (theme.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) DsAccent.Gold.copy(0.22f) else Color(0xFFFDE68A)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Timer, tint = if (theme.isDark) DsAccent.Gold else Color(0xFFCA8A04), size = 12.dp) }
                        Column { Text("Uptime", fontSize = 10.sp, color = theme.mutedColor); Text("1 day, 1 hour", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    }
                }

                // ── Traffic Usage
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Traffic Usage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Text("Total traffic usage across all servers", fontSize = 10.sp, color = theme.mutedColor)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1h","6h","24h","3d","More").forEach { t ->
                            val sel = t == timeRange
                            Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) DsAccent.Gold else theme.searchBgColor).border(BorderStroke(1.dp, if (sel) DsAccent.Gold else theme.borderColor), RoundedCornerShape(8.dp)).clickable { timeRange = t }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(t, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) Color(0xFF422006) else theme.mutedColor)
                            }
                        }
                        PGDropdown(value = "Auto", onClick = {})
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Calendar, tint = theme.mutedColor, size = 14.dp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PGDropdown(value = adminFilter, onClick = {}); Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Tune, tint = theme.mutedColor, size = 14.dp) } }
                    Text("Usage During Period", fontSize = 10.sp, color = theme.mutedColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Gauge, tint = theme.mutedColor, size = 12.dp); Spacer(Modifier.width(6.dp)); Text("275.46 GB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    }
                    PGChart(points = trafficPoints, accent = DsAccent.Gold, themeIsDark = theme.isDark)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFFBEB)).border(BorderStroke(0.7.dp, Color(0xFFFDE68A)), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("Germany node 🇩🇪", fontSize = 9.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ── User Count
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("User Count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Text("Online, expired, and limited user activity counts over time", fontSize = 10.sp, color = theme.mutedColor)
                    Text("Status changes can skew history. Usage resets clear chart history only if chart-data cleanup is enabled.", fontSize = 8.sp, color = theme.mutedColor, lineHeight = 10.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1h","6h","24h","3d","More").forEach { t ->
                            Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(t, fontSize = 10.sp, color = theme.mutedColor)
                            }
                        }
                        PGDropdown(value = "Auto", onClick = {})
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("⎙", fontSize = 12.sp, color = theme.mutedColor) }
                        PGDropdown(value = onlineFilter, onClick = { onlineFilter = if (onlineFilter == "Online Users") "Active Users" else "Online Users" })
                        PGDropdown(value = "All admins", onClick = {})
                        Spacer(Modifier.width(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Group by node", fontSize = 10.sp, color = theme.mutedColor)
                        Box(Modifier.width(36.dp).height(20.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE5E7EB)).padding(2.dp)) {
                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Color.White))
                        }
                    }
                    Text("Count During Period", fontSize = 10.sp, color = theme.mutedColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Wifi, tint = theme.mutedColor, size = 12.dp); Text("66", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                        }
                    }
                    PGChart(points = trafficPoints, accent = Color(0xFFF59E0B), themeIsDark = theme.isDark, isOrange = true)
                    Spacer(Modifier.height(56.dp))
                }
            }
        }
    }
}

@Composable private fun PGDropdown(value: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
        Text("▾", fontSize = 10.sp, color = theme.mutedColor)
    }
}

@Composable private fun PGChart(points: List<TrafficPoint>, accent: Color, themeIsDark: Boolean, isOrange: Boolean = false) {
    val grid = Color(0xFFE5E7EB)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width; val h = size.height
        // grid horizontal 4 lines
        for (i in 1..4) drawLine(grid, androidx.compose.ui.geometry.Offset(0f, h * i / 5f), androidx.compose.ui.geometry.Offset(w, h * i / 5f), 0.7f)
        val max = points.maxOfOrNull { it.totalTraffic }?.coerceAtLeast(1L) ?: 1L
        if (points.size > 1) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { idx, pt -> val x = w * idx / (points.size - 1); val y = h - (pt.totalTraffic.toFloat() / max * h * 0.78f) - h * 0.08f; if (idx==0) path.moveTo(x,y) else path.lineTo(x,y) }
            val fill = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(w,h); lineTo(0f,h); close() }
            drawPath(fill, accent.copy(0.14f)); drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        } else {
            // mock subtle hill like reference
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h*0.38f); cubicTo(w*0.18f, h*0.18f, w*0.38f, h*0.14f, w*0.52f, h*0.16f); cubicTo(w*0.66f, h*0.18f, w*0.78f, h*0.22f, w*0.92f, h*0.45f); lineTo(w, h*0.95f)
            }
            drawPath(androidx.compose.ui.graphics.Path().apply { addPath(p); lineTo(w,h); lineTo(0f,h); close() }, accent.copy(0.12f))
            drawPath(p, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        // x labels
        val labels = listOf("08/03","08/04","08/05","08/06","08/07","08/08","08/0")
        // labels drawn as overlay text outside canvas — handled by caller if needed
    }
    // x-axis labels row (outside canvas for readability)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("08/03","08/04","08/05","08/06","08/07","08/08","08/0").forEach { Text(it, fontSize = 8.sp, color = Color(0xFF9CA3AF)) }
    }
}
