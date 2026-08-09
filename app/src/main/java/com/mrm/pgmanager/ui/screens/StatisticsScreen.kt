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
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = refreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter)) }) {

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
                        PGStatCard(label = "CPU Usage", value = "${"%.1f".format(s.cpuUsage)}%", icon = AppIcon.Gauge, modifier = Modifier.weight(1f), trailing = { Text("2 cores", fontSize = 9.sp, color = theme.mutedLightColor, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) })
                        PGStatCard(label = "RAM Usage", value = "${formatBytes(s.memUsed)}/${formatBytes(s.memTotal)}", icon = AppIcon.Memory, modifier = Modifier.weight(1f), trailing = { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("39.4%", fontSize = 9.sp, color = theme.mutedColor) } })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGStatCard(label = "Disk Usage", value = "${formatBytes(s.diskUsed)}/${formatBytes(s.diskTotal)}", icon = AppIcon.Storage, modifier = Modifier.weight(1f), trailing = { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("17.3%", fontSize = 9.sp, color = theme.mutedColor) } })
                        // Total Traffic with in/out - same 92dp height
                        Column(Modifier.weight(1f).height(92.dp).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFFBEB)).border(BorderStroke(0.7.dp, Color(0xFFFDE68A)), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Storage, tint = Color(0xFFCA8A04), size = 12.dp) }
                                Text("Total Traffic", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            Text(formatBytes(s.incomingBandwidth + s.outgoingBandwidth), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                    }
                    // Bandwidth details - separate card (was inside Total Traffic)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFDCFCE7)).border(BorderStroke(0.7.dp, Color(0xFFBBF7D0)), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("↓", fontSize = 12.sp, color = Color(0xFF065F46), fontWeight = FontWeight.Bold) }
                            Text("Download", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
