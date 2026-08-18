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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.spinWhile
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.formatPercent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(session: Session, onOpenSettings: () -> Unit = {}) {
    val settingsLabel = stringResource(R.string.app_settings)
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

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it }
        runCatching { PanelApi.trafficUsage(session, trafficRange, selectedNode?.id) }
            .onSuccess { trafficPoints = it }
        runCatching { PanelApi.userCountMetric(session, countMetric, countRange) }
            .onSuccess { countPoints = it }
        loading = false
    }

    LaunchedEffect(session) { runCatching { PanelApi.nodes(session) }.onSuccess { nodes = it } }
    // با تغییرِ هر فیلتر، داده دوباره از پنل گرفته می‌شود.
    LaunchedEffect(session, trafficRange, selectedNode, countRange, countMetric) { load(silent = stats != null) }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } }, state = pullState,
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = refreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter), containerColor = theme.cardSurfaceColor, color = theme.accentPrimary) }) {

        Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = DsSpacing.Screen, end = DsSpacing.Screen, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).pressScale(0.92f).clickable { scope.launch { refreshing = true; load(true); refreshing = false } }, contentAlignment = Alignment.Center) {
                    RoundedAppIcon(AppIcon.Refresh, tint = if (refreshing) theme.accentPrimary else theme.mutedColor, size = 16.dp, modifier = Modifier.spinWhile(refreshing))
                }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable(onClick = onOpenSettings).semantics { contentDescription = settingsLabel }, contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Settings, tint = theme.mutedColor, size = 16.dp)
                    }
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
                // نسبت‌های سیستم — یک‌بار حساب می‌شوند و به حلقه‌ها می‌روند.
                val memFraction = if (s.memTotal > 0) (s.memUsed.toFloat() / s.memTotal) else 0f
                val diskFraction = if (s.diskTotal > 0) (s.diskUsed.toFloat() / s.diskTotal) else 0f
                val cpuFraction = (s.cpuUsage / 100f).coerceIn(0f, 1f)
                val totalTraffic = s.incomingBandwidth + s.outgoingBandwidth
                val downShare = if (totalTraffic > 0) s.incomingBandwidth.toFloat() / totalTraffic else 0f
                val (downColor, upColor) = accentPair()

                // ── System
                Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Gauge, tint = theme.accentPrimary, size = 14.dp); Text(stringResource(R.string.system), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGRingStatCard(
                            label = stringResource(R.string.cpu_usage),
                            value = "${formatPercent(s.cpuUsage)}%",
                            icon = AppIcon.Gauge,
                            modifier = Modifier.weight(1f),
                            fraction = cpuFraction,
                            percent = cpuFraction.times(100).toInt(),
                            sub = stringResource(R.string.cpu_cores_fmt, s.cpuCores)
                        )
                        PGRingStatCard(
                            label = stringResource(R.string.ram_usage),
                            value = "${formatBytes(s.memUsed)}/${formatBytes(s.memTotal)}",
                            icon = AppIcon.Memory,
                            modifier = Modifier.weight(1f),
                            fraction = memFraction,
                            percent = memFraction.times(100).toInt(),
                            sub = stringResource(R.string.free_fmt, formatBytes((s.memTotal - s.memUsed).coerceAtLeast(0L)))
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGRingStatCard(
                            label = stringResource(R.string.disk_usage),
                            value = "${formatBytes(s.diskUsed)}/${formatBytes(s.diskTotal)}",
                            icon = AppIcon.Storage,
                            modifier = Modifier.weight(1f),
                            fraction = diskFraction,
                            percent = diskFraction.times(100).toInt(),
                            sub = stringResource(R.string.free_fmt, formatBytes((s.diskTotal - s.diskUsed).coerceAtLeast(0L)))
                        )
                        // حلقهٔ ترافیک سقف ندارد؛ سهمِ دانلود/آپلود را با دو سایه از
                        // رنگِ تم نشان می‌دهد.
                        PGRingStatCard(
                            label = stringResource(R.string.total_traffic),
                            value = formatBytes(totalTraffic),
                            icon = AppIcon.Storage,
                            modifier = Modifier.weight(1f),
                            segments = listOf(
                                RingSegment(downShare, downColor),
                                RingSegment(1f - downShare, upColor)
                            ),
                            ringColor = theme.accentPrimary,
                            centerIcon = AppIcon.Storage,
                            sub = "↓ ${formatBytes(s.incomingBandwidth)}  ↑ ${formatBytes(s.outgoingBandwidth)}"
                        )
                    }
                    // دانلود/آپلود — حلقه سهمِ هرکدام از کلِ ترافیک را می‌گوید.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PGRingStatCard(
                            label = stringResource(R.string.download),
                            value = formatBytes(s.incomingBandwidth),
                            icon = AppIcon.Download,
                            modifier = Modifier.weight(1f),
                            fraction = downShare,
                            percent = (downShare * 100).toInt(),
                            ringColor = downColor,
                            minHeight = 76.dp,
                            ringSize = 44.dp
                        )
                        PGRingStatCard(
                            label = stringResource(R.string.upload),
                            value = formatBytes(s.outgoingBandwidth),
                            icon = AppIcon.Upload,
                            modifier = Modifier.weight(1f),
                            fraction = 1f - downShare,
                            percent = ((1f - downShare) * 100).toInt(),
                            ringColor = upColor,
                            minHeight = 76.dp,
                            ringSize = 44.dp
                        )
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(22.dp).clip(DsRadius.Sm).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.24f)), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Timer, tint = theme.accentPrimary, size = 12.dp) }
                        Column { Text(stringResource(R.string.uptime), fontSize = 10.sp, color = theme.mutedColor); MrmText(com.mrm.pgmanager.utils.uptimeText(s.uptimeSeconds), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                            PGDropdown(value = stringResource(countMetric.labelRes), onClick = { metricMenuOpen = true })
                            DropdownMenu(expanded = metricMenuOpen, onDismissRequest = { metricMenuOpen = false }) {
                                CountMetric.entries.forEach { m ->
                                    DropdownMenuItem(text = { Text(stringResource(m.labelRes)) }, onClick = { countMetric = m; metricMenuOpen = false })
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

