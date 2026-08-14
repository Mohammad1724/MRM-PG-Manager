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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.CountMetric
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.TrafficPoint
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.formatPercent
import com.mrm.pgmanager.utils.formatUptime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(session: Session, onSettings: () -> Unit) {
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var trafficPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    var countPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    var nodes by remember { mutableStateOf<List<PanelNode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    // فیلترهای واقعی (قبلاً رشتهٔ ثابت و بی‌اثر بودند)
    var selectedNode by remember { mutableStateOf<PanelNode?>(null) }
    var nodeMenuOpen by remember { mutableStateOf(false) }
    var trafficRange by remember { mutableStateOf(StatsRange.LAST_24H) }
    var countRange by remember { mutableStateOf(StatsRange.LAST_24H) }
    var countMetric by remember { mutableStateOf(CountMetric.ONLINE) }
    var metricMenuOpen by remember { mutableStateOf(false) }

    // درآمد: کاملاً محلی است، پس با هر بار نمایشِ صفحه از حافظه خوانده می‌شود.
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { com.mrm.pgmanager.data.storage.SessionStore(context) }
    val currency = remember { store.readMonitoringSettings().debtorCurrency }
    var sales by remember { mutableStateOf(store.readSalesForBase(session.baseUrl)) }

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it }
        runCatching { PanelApi.trafficUsage(session, trafficRange, selectedNode?.id) }
            .onSuccess { trafficPoints = it }
        runCatching { PanelApi.userCountMetric(session, countMetric, countRange) }
            .onSuccess { countPoints = it }
        // فروش‌ها محلی‌اند ولی ممکن است در تبِ تمدیدها تازه ثبت شده باشند.
        sales = store.readSalesForBase(session.baseUrl)
        loading = false
    }

    LaunchedEffect(session) { runCatching { PanelApi.nodes(session) }.onSuccess { nodes = it } }
    LaunchedEffect(session) { sales = store.readSalesForBase(session.baseUrl) }
    // با تغییرِ هر فیلتر، داده دوباره از پنل گرفته می‌شود.
    LaunchedEffect(session, trafficRange, selectedNode, countRange, countMetric) { load(silent = stats != null) }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } }, state = pullState,
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = refreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter), containerColor = theme.cardSurfaceColor, color = theme.accentPrimary) }) {

        Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Screen, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Header
            Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(0.5.dp, theme.accentPrimary.copy(alpha = 0.24f)), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("○", fontSize = 7.sp, color = theme.accentPrimary)
                        }
                    }
                    Text(stringResource(R.string.statistics_subtitle), fontSize = 10.sp, color = theme.mutedColor)
                }
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable { scope.launch { refreshing = true; load(true); refreshing = false } }, contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(14.dp), color = theme.mutedColor, strokeWidth = 1.6.dp) else RoundedAppIcon(AppIcon.Refresh, tint = theme.mutedColor, size = 16.dp)
                }
            }

            // ── Nodes selector
            Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(stringResource(R.string.nodes), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text(stringResource(R.string.nodes_desc), fontSize = 10.sp, color = theme.mutedColor) }
                    Box {
                        PGDropdown(value = selectedNode?.name ?: stringResource(R.string.all_nodes), onClick = { nodeMenuOpen = true })
                        DropdownMenu(expanded = nodeMenuOpen, onDismissRequest = { nodeMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all_nodes)) },
                                onClick = { selectedNode = null; nodeMenuOpen = false }
                            )
                            nodes.forEach { n ->
                                DropdownMenuItem(text = { Text(n.name) }, onClick = { selectedNode = n; nodeMenuOpen = false })
                            }
                        }
                    }
                }
            }

            if (loading && stats == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accentPrimary) }
            }

            stats?.let { s ->
                // ── System
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Gauge, tint = theme.accentPrimary, size = 14.dp); Text(stringResource(R.string.system), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGStatCard(label = stringResource(R.string.cpu_usage), value = "${formatPercent(s.cpuUsage)}%", icon = AppIcon.Gauge, modifier = Modifier.weight(1f), trailing = { Text("${s.cpuCores} cores", fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) })
                        PGStatCard(label = stringResource(R.string.ram_usage), value = "${formatBytes(s.memUsed)}/${formatBytes(s.memTotal)}", icon = AppIcon.Memory, modifier = Modifier.weight(1f), trailing = { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("${if (s.memTotal>0) (s.memUsed*100/s.memTotal).toInt() else 0}%", fontSize = 10.sp, color = theme.mutedColor) } })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGStatCard(label = stringResource(R.string.disk_usage), value = "${formatBytes(s.diskUsed)}/${formatBytes(s.diskTotal)}", icon = AppIcon.Storage, modifier = Modifier.weight(1f), trailing = { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("${if (s.diskTotal>0) (s.diskUsed*100/s.diskTotal).toInt() else 0}%", fontSize = 10.sp, color = theme.mutedColor) } })
                        // Total Traffic with in/out - same 92dp height
                        Column(Modifier.weight(1f).height(92.dp).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.24f)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Storage, tint = theme.accentPrimary, size = 12.dp) }
                                Text(stringResource(R.string.total_traffic), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            MrmText(formatBytes(s.incomingBandwidth + s.outgoingBandwidth), isTechnical = true, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Bandwidth - separate card
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(if (theme.isDark) Color(0xFF064E3B).copy(0.45f) else Color(0xFFDCFCE7)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) Color(0xFF10B981).copy(0.30f) else Color(0xFFBBF7D0)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Download, tint = if (theme.isDark) Color(0xFF6EE7B7) else Color(0xFF065F46), size = 14.dp) }
                                Text(stringResource(R.string.download), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            MrmText(formatBytes(s.incomingBandwidth), isTechnical = true, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(if (theme.isDark) Color(0xFF1E3A8A).copy(0.35f) else Color(0xFFDBEAFE)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) Color(0xFF60A5FA).copy(0.30f) else Color(0xFFBFDBFE)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Upload, tint = if (theme.isDark) Color(0xFF93C5FD) else Color(0xFF1E3A8A), size = 14.dp) }
                                Text(stringResource(R.string.upload), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            MrmText(formatBytes(s.outgoingBandwidth), isTechnical = true, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.24f)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Timer, tint = theme.accentPrimary, size = 12.dp) }
                        Column { Text(stringResource(R.string.uptime), fontSize = 10.sp, color = theme.mutedColor); MrmText(formatUptime(s.uptimeSeconds), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                // ── Traffic Usage
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.traffic_usage), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Text(stringResource(R.string.traffic_usage_desc), fontSize = 10.sp, color = theme.mutedColor)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatsRange.entries.forEach { r ->
                            val sel = r == trafficRange
                            Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) theme.accentPrimary else theme.searchBgColor).border(BorderStroke(1.dp, if (sel) theme.accentPrimary else theme.borderColor), RoundedCornerShape(8.dp)).clickable { trafficRange = r }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(r.label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) Color(0xFF422006) else theme.mutedColor)
                            }
                        }
                    }
                    Text(stringResource(R.string.usage_in_range), fontSize = 10.sp, color = theme.mutedColor)
                    val periodTotal = remember(trafficPoints) { trafficPoints.sumOf { it.totalTraffic } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Gauge, tint = theme.mutedColor, size = 12.dp); Spacer(Modifier.width(6.dp))
                        MrmText(formatBytes(periodTotal), isTechnical = true, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    UsageChart(points = trafficPoints, accent = theme.accentPrimary, themeIsDark = theme.isDark, valueFormatter = ::formatBytes)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(0.7.dp, theme.accentPrimary.copy(alpha = 0.24f)), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(selectedNode?.name ?: stringResource(R.string.all_nodes), fontSize = 9.sp, color = theme.accentPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ── User Count
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.user_count), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Text(stringResource(R.string.user_count_desc), fontSize = 10.sp, color = theme.mutedColor)
                    Text(stringResource(R.string.status_history_note), fontSize = 10.sp, color = theme.mutedColor, lineHeight = 12.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatsRange.entries.forEach { r ->
                            val sel = r == countRange
                            Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) theme.accentPrimary else theme.searchBgColor).border(BorderStroke(1.dp, if (sel) theme.accentPrimary else theme.borderColor), RoundedCornerShape(8.dp)).clickable { countRange = r }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(r.label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) Color(0xFF422006) else theme.mutedColor)
                            }
                        }
                        Box {
                            PGDropdown(value = countMetric.label, onClick = { metricMenuOpen = true })
                            DropdownMenu(expanded = metricMenuOpen, onDismissRequest = { metricMenuOpen = false }) {
                                CountMetric.entries.forEach { m ->
                                    DropdownMenuItem(text = { Text(m.label) }, onClick = { countMetric = m; metricMenuOpen = false })
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(stringResource(R.string.count_during_period), fontSize = 10.sp, color = theme.mutedColor)
                    val peakCount = remember(countPoints) { countPoints.maxOfOrNull { it.totalTraffic } ?: 0L }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Wifi, tint = theme.mutedColor, size = 12.dp)
                            MrmText("$peakCount", isTechnical = true, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    UsageChart(points = countPoints, accent = Color(0xFFF59E0B), themeIsDark = theme.isDark, valueFormatter = { it.toString() })
                }
            }

            // ── درآمد (دادهٔ محلی؛ مستقل از در دسترس بودنِ آمارِ پنل)
            RevenueSection(
                sales = sales,
                currency = currency,
                onDeleteSale = { sale -> store.removeSale(sale.id); sales = store.readSalesForBase(session.baseUrl) }
            )
            Spacer(Modifier.height(56.dp))
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

