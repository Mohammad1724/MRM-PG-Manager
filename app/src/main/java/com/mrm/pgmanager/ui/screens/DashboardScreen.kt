package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.TrafficPoint
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.NotificationHelper
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
    var capacityAlerted by remember { mutableStateOf(false) }
    var lastNodeStates by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    val store = remember { com.mrm.pgmanager.data.storage.SessionStore(context) }
    var offlineAt by remember { mutableStateOf<Long?>(null) }
    var lastWidgetUpdateAt by remember { mutableStateOf(0L) }
    var inForeground by remember { mutableStateOf(true) }
    val lifecycle = (androidx.compose.ui.platform.LocalContext.current as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> inForeground = event == Lifecycle.Event.ON_RESUME }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trafficPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    var debtorTotalAmount by remember { mutableStateOf(0L) }
    var debtorCount by remember { mutableStateOf(0) }
    var debtorCurrency by remember { mutableStateOf(settings.debtorCurrency) }
    fun refreshDebtors() {
        val all = store.readDebtors().values.filter { it.baseUrl == session.baseUrl }
        debtorCount = all.size; debtorTotalAmount = all.sumOf { it.amount }
        debtorCurrency = all.firstOrNull()?.currency ?: settings.debtorCurrency
    }
    LaunchedEffect(Unit) { refreshDebtors() }
    fun evaluateHealth(s: SystemStats) {
        if (!settings.notificationsEnabled || !settings.notifySystemHealth) return
        fun alert(id: Int, title: String, message: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_SYSTEM, title, message)
        if (s.cpuUsage >= settings.cpuThreshold) { if (!cpuAlerted) alert(3101, "هشدار CPU", "مصرف CPU به ${"%.1f".format(s.cpuUsage)}٪ رسیده"); cpuAlerted = true } else cpuAlerted = false
        val ram = if (s.memTotal > 0L) (s.memUsed * 100 / s.memTotal).toInt() else 0
        if (ram >= settings.ramThreshold) { if (!ramAlerted) alert(3102, "هشدار RAM", "مصرف RAM به $ram٪ رسیده"); ramAlerted = true } else ramAlerted = false
        val disk = if (s.diskTotal > 0L) (s.diskUsed * 100 / s.diskTotal).toInt() else 0
        if (disk >= settings.diskThreshold) { if (!diskAlerted) alert(3103, "هشدار Disk", "مصرف Disk به $disk٪ رسیده"); diskAlerted = true } else diskAlerted = false
        if (settings.notifyCapacity && s.onlineUsers >= settings.capacityOnlineLimit) {
            if (!capacityAlerted) alert(3105, "هشدار ظرفیت", "کاربران آنلاین به ${s.onlineUsers} رسید (حد: ${settings.capacityOnlineLimit})"); capacityAlerted = true
        } else capacityAlerted = false
    }
    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it; panelOfflineAlerted = false; offlineAt = null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.saveStatsCache(it) }
            refreshDebtors()
            if (System.currentTimeMillis() - lastWidgetUpdateAt > 30_000L) { lastWidgetUpdateAt = System.currentTimeMillis(); runCatching { com.mrm.pgmanager.widget.PanelWidgetProvider.updateAll(context) } }
            evaluateHealth(it)
        }.onFailure { e ->
            if (e.message?.contains("401") == true) { android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show(); onLogout() }
            else {
                if (settings.notificationsEnabled && settings.notifyPanelOffline && !panelOfflineAlerted) { NotificationHelper.post(context, 3104, NotificationHelper.CHANNEL_SYSTEM, "اتصال به پنل ناموفق", "دریافت آمار Dashboard ناموفق بود"); panelOfflineAlerted = true }
                val cache = if (settings.offlineCacheEnabled) store.readStatsCache() else null
                if (cache != null) { stats = cache.first; offlineAt = cache.second; error = null } else error = e.message ?: "خطا در دریافت آمار"
            }
        }
        runCatching { PanelApi.trafficUsage(session) }.onSuccess { trafficPoints = it }
        runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
            if (settings.notificationsEnabled && settings.notifyNodeOffline && lastNodeStates.isNotEmpty()) states.forEach { (id, online) ->
                val prev = lastNodeStates[id]; if (prev == true && !online) NotificationHelper.post(context, 4100+id, NotificationHelper.CHANNEL_SYSTEM, "نود آفلاین شد", "نود $id در دسترس نیست")
                if (prev == false && online) NotificationHelper.post(context, 4200+id, NotificationHelper.CHANNEL_SYSTEM, "نود آنلاین شد", "نود $id دوباره آنلاین است")
            }
            lastNodeStates = states
        }
        loading = false
    }
    LaunchedEffect(session, settings.autoRefreshEnabled, settings.refreshIntervalSeconds) {
        if (settings.autoRefreshEnabled) while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            if (inForeground) load(silent = true); kotlinx.coroutines.delay(settings.refreshIntervalSeconds.coerceIn(5,3600)*1_000L)
        } else load()
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = manualRefreshing, onRefresh = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, state = pullState, modifier = Modifier.fillMaxSize(),
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = manualRefreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter)) }) {

        Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Screen, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Header: PasarGuard style top bar (Dashboard title + Quick Actions yellow button mimic)
            Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Dashboard", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(if (theme.isDark) DsAccent.Gold.copy(0.18f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) DsAccent.Gold.copy(0.30f) else Color(0xFFFDE68A)), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("ⓘ", fontSize = 10.sp, color = if (theme.isDark) DsAccent.Gold else Color(0xFFCA8A04), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("PasarGuard Management Dashboard", fontSize = 10.sp, color = theme.mutedColor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, contentAlignment = Alignment.Center) {
                        if (manualRefreshing) CircularProgressIndicator(Modifier.size(14.dp), color = DsAccent.Gold, strokeWidth = 2.dp)
                        else RoundedAppIcon(AppIcon.Refresh, tint = theme.mutedColor, size = 16.dp)
                    }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Settings, tint = theme.mutedColor, size = 16.dp)
                    }
                }
            }

            if (loading && stats == null) Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsAccent.Gold) }
            error?.let { Text(it, color = com.mrm.pgmanager.ui.theme.GlassRed, fontSize = 11.sp) }
            offlineAt?.let { cachedAt ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(DsRadius.Md).background(if (theme.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) DsAccent.Gold.copy(0.25f) else Color(0xFFFDE68A)), DsRadius.Md).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    RoundedAppIcon(AppIcon.Warning, tint = if (theme.isDark) DsAccent.Gold else GlassAmber, size = 12.dp)
                    Text("حالت آفلاین — ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))}", color = if (theme.isDark) DsAccent.Gold else Color(0xFF92400E), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            stats?.let { s ->
                // System row — 2x2 grid exactly like PG screenshot
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    PGStatCard(label = "CPU Usage", value = "${"%.1f".format(s.cpuUsage)}%", icon = AppIcon.Gauge, modifier = Modifier.weight(1f),
                        valueSub = "${s.cpuCores} cores", trailing = { Text("${"%.1f".format(s.cpuUsage)}%", fontSize = 10.sp, color = theme.mutedLightColor) })
                    PGStatCard(label = "RAM Usage", value = "${formatBytes(s.memUsed)}/${formatBytes(s.memTotal)}", icon = AppIcon.Memory, modifier = Modifier.weight(1f),
                        trailing = { PGBadge("${if (s.memTotal>0) (s.memUsed*100/s.memTotal).toInt() else 0}%") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    PGStatCard(label = "Disk Usage", value = "${formatBytes(s.diskUsed)}/${formatBytes(s.diskTotal)}", icon = AppIcon.Storage, modifier = Modifier.weight(1f),
                        trailing = { PGBadge("${if (s.diskTotal>0) (s.diskUsed*100/s.diskTotal).toInt() else 0}%") })
                    // Total Traffic card with in/out badges - same 92dp height as PGStatCard
                    Column(Modifier.weight(1f).height(92.dp).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(if (theme.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) DsAccent.Gold.copy(0.22f) else Color(0xFFFDE68A)), DsRadius.Sm), contentAlignment = Alignment.Center) {
                                RoundedAppIcon(AppIcon.Storage, tint = if (theme.isDark) DsAccent.Gold else Color(0xFFCA8A04), size = 15.dp)
                            }
                            Text("Total Traffic", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor, modifier = Modifier.weight(1f))
                        }
                        TechnicalContainer { Text(formatBytes(s.incomingBandwidth + s.outgoingBandwidth), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    }
                }
                // Uptime — full width
                Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFFBEB)).border(BorderStroke(0.7.dp, Color(0xFFFDE68A)), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Timer, tint = Color(0xFFCA8A04), size = 15.dp)
                    }
                    Column {
                        Text("Uptime", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        val days = s.uptimeSeconds / 86400L; val hrs = (s.uptimeSeconds % 86400L)/3600L
                        val txt = if (days > 0) "$days day, $hrs hour" else "$hrs hour"
                        Text(txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                }

                // ── Users section — mirrors PG Dashboard Users block
                PGSectionHeader(title = "Users")
                // Users / Active Users 2-col
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Users, tint = Color(0xFFCA8A04), size = 12.dp); Text("Users", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                        Text("${s.totalUsers}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    Row(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoundedAppIcon(AppIcon.CheckCircle, tint = Color(0xFFCA8A04), size = 12.dp); Text("Active Users", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                            Text("${s.activeUsers}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                        PGBadge("93.2%")
                    }
                }
                // Online Users full width
                Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Wifi, tint = Color(0xFFCA8A04), size = 12.dp); Text("Online Users", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                        Text("${s.onlineUsers}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    PGBadge("36.8%")
                }

                // ── Total Admins block (like screenshot bottom)
                Text("Total Admins", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    // Left: Users breakdown card
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Users", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                        Text("Monitor Users", fontSize = 10.sp, color = theme.mutedLightColor)
                        listOf(
                            Triple("Users", "${s.totalUsers}", null),
                            Triple("Active Users", "${s.activeUsers}", if(s.totalUsers>0) String.format(java.util.Locale.US, "%.0f%%", s.activeUsers*100.0/s.totalUsers) else null),
                            Triple("Online Users", "${s.onlineUsers}", if(s.totalUsers>0) String.format(java.util.Locale.US, "%.0f%%", s.onlineUsers*100.0/s.totalUsers) else null),
                            Triple("Expired Users", "${s.expiredUsers}", if(s.totalUsers>0) String.format(java.util.Locale.US, "%.0f%%", s.expiredUsers*100.0/s.totalUsers) else null),
                            Triple("Limited Users", "${s.limitedUsers}", if(s.totalUsers>0) String.format(java.util.Locale.US, "%.0f%%", s.limitedUsers*100.0/s.totalUsers) else null),
                            Triple("On Hold Users", "0", null),
                            Triple("Disabled Users", "${s.disabledUsers}", if(s.totalUsers>0) String.format(java.util.Locale.US, "%.0f%%", s.disabledUsers*100.0/s.totalUsers) else null),
                        ).forEach { (label, value, pct) ->
                            val dotColor = when(label) {
                                "Online Users" -> Color(0xFF22C55E); "Expired Users" -> Color(0xFFF97316); "Limited Users" -> Color(0xFFEF4444); "On Hold Users" -> Color(0xFFA855F7); "Disabled Users" -> Color(0xFF6B7280); else -> Color.Transparent
                            }
                            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (dotColor != Color.Transparent) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dotColor))
                                    else RoundedAppIcon(AppIcon.Users, tint = theme.mutedColor, size = 12.dp)
                                    Text(label, fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (pct != null) Text(pct, fontSize = 10.sp, color = theme.mutedLightColor)
                                    Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                }
                            }
                        }
                    }
                    // Right: Usage chart card
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("Usage", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text("Monitor admin traffic\nusage over time", fontSize = 8.sp, color = theme.mutedLightColor, lineHeight = 10.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.5.dp, theme.borderColor), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text("7 days ▾", fontSize = 10.sp, color = theme.mutedColor)
                            }
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.5.dp, theme.borderColor), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text("Auto ▾", fontSize = 10.sp, color = theme.mutedColor)
                            }
                        }
                        UsageMiniChart(points = trafficPoints, themeIsDark = theme.isDark, accent = DsAccent.Gold)
                        run {
                            val totalPeriod = trafficPoints.sumOf { it.totalTraffic }
                            val trendingText = if (trafficPoints.size >= 2) {
                                val first = trafficPoints.first().totalTraffic.toDouble().coerceAtLeast(1.0)
                                val last = trafficPoints.last().totalTraffic.toDouble()
                                val diff = ((last - first) / first * 100).toInt()
                                if (diff >= 0) "Trending up by ${diff}% ↗" else "Trending down by ${-diff}% ↘"
                            } else "No trend yet"
                            val trendingColor = if (trafficPoints.size >= 2 && trafficPoints.last().totalTraffic >= trafficPoints.first().totalTraffic) Color(0xFF16A34A) else Color(0xFFDC2626)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(trendingText, fontSize = 10.sp, color = trendingColor, fontWeight = FontWeight.SemiBold)
                                Text("Usage During Period: ${formatBytes(totalPeriod)}\nTotal traffic usage across all servers", fontSize = 10.sp, color = theme.mutedLightColor, lineHeight = 10.sp)
                            }
                        }
                    }
                }

                // Debtor block
                if (debtorCount > 0) {
                    Text("بدهکاران", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                        PGStatCard(label = "تعداد بدهکار", value = "$debtorCount نفر", icon = AppIcon.Warning, modifier = Modifier.weight(1f), accent = com.mrm.pgmanager.ui.theme.GlassRed)
                        PGStatCard(label = "مجموع بدهی", value = "${formatDebtorAmountFull(debtorTotalAmount)} $debtorCurrency", icon = AppIcon.Money, modifier = Modifier.weight(1f), accent = com.mrm.pgmanager.ui.theme.GlassRed)
                    }
                }

                // Traffic mini chart separated (optional detailed)
                // keep subtle spacing at bottom for nav bar
                Spacer(Modifier.height(70.dp))
            }
        }
    }
}

@Composable
private fun UsageMiniChart(points: List<TrafficPoint>, themeIsDark: Boolean, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val w = size.width; val h = size.height
        // grid
        for (i in 1..3) drawLine(theme.borderColor, androidx.compose.ui.geometry.Offset(0f, h * i / 4f), androidx.compose.ui.geometry.Offset(w, h * i / 4f), 0.7f)
        val max = points.maxOfOrNull { it.totalTraffic }?.coerceAtLeast(1L) ?: 1L
        if (points.size > 1) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { idx, p ->
                val x = w * idx / (points.size - 1)
                val y = h - (p.totalTraffic.toFloat() / max * h * 0.85f) - h * 0.05f
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // fill
            val fillPath = androidx.compose.ui.graphics.Path().apply {
                addPath(path)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fillPath, accent.copy(0.18f))
            drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        } else if (points.isEmpty()) {
            // mock gentle curve if no data
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.45f); cubicTo(w*0.25f, h*0.2f, w*0.55f, h*0.15f, w*0.75f, h*0.35f); lineTo(w*0.85f, h*0.25f); lineTo(w, h*0.85f)
            }
            drawPath(androidx.compose.ui.graphics.Path().apply { addPath(p); lineTo(w,h); lineTo(0f,h); close() }, accent.copy(0.14f))
            drawPath(p, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

private fun formatDebtorAmountFull(amount: Long): String = when {
    amount == 0L -> "0"
    amount >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2fB", amount / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    amount >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", amount / 1_000_000.0).trimEnd('0').trimEnd('.')
    amount >= 1000L -> "%,d".format(java.util.Locale.US, amount)
    else -> amount.toString()
}
