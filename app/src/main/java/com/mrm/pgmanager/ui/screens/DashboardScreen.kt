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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mrm.pgmanager.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.TrafficPoint
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.spinWhile
import com.mrm.pgmanager.ui.designsystem.animatedCount
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.NotificationHelper
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.formatPercent
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(session: Session, settings: MonitoringSettings, onLogout: () -> Unit, onOpenSettings: () -> Unit = {}) {
    val settingsLabel = stringResource(R.string.app_settings)
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
    // مقدارِ اولیه از حافظهٔ برنامه می‌آید: با برگشتن به این تب، صفحه فوراً با
    // آخرین دادهٔ دیده‌شده ساخته می‌شود به‌جای اینکه خالی بیاید و بعد پر شود.
    val statsKey = PanelCache.statsKey(session.baseUrl)
    val trafficKey = PanelCache.trafficKey(session.baseUrl)
    var stats by remember(session) { mutableStateOf(PanelCache.get<SystemStats>(statsKey)) }
    var loading by remember(session) { mutableStateOf(PanelCache.get<SystemStats>(statsKey) == null) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trafficPoints by remember(session) {
        mutableStateOf(PanelCache.get<List<TrafficPoint>>(trafficKey) ?: emptyList())
    }
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
        if (s.cpuUsage >= settings.cpuThreshold) { if (!cpuAlerted) alert(3101, context.getString(R.string.mw_cpu), context.getString(R.string.mw_cpu_body, "%.1f".format(s.cpuUsage))); cpuAlerted = true } else cpuAlerted = false
        val ram = if (s.memTotal > 0L) (s.memUsed * 100 / s.memTotal).toInt() else 0
        if (ram >= settings.ramThreshold) { if (!ramAlerted) alert(3102, context.getString(R.string.mw_ram), context.getString(R.string.mw_ram_body, ram)); ramAlerted = true } else ramAlerted = false
        val disk = if (s.diskTotal > 0L) (s.diskUsed * 100 / s.diskTotal).toInt() else 0
        if (disk >= settings.diskThreshold) { if (!diskAlerted) alert(3103, context.getString(R.string.mw_disk), context.getString(R.string.mw_disk_body, disk)); diskAlerted = true } else diskAlerted = false
        if (settings.notifyCapacity && s.onlineUsers >= settings.capacityOnlineLimit) {
            if (!capacityAlerted) alert(3105, context.getString(R.string.mw_capacity), context.getString(R.string.mw_capacity_body, s.onlineUsers, settings.capacityOnlineLimit)); capacityAlerted = true
        } else capacityAlerted = false
    }
    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it; panelOfflineAlerted = false; offlineAt = null
            PanelCache.put(statsKey, it)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.saveStatsCache(it) }
            refreshDebtors()
            if (System.currentTimeMillis() - lastWidgetUpdateAt > 30_000L) { lastWidgetUpdateAt = System.currentTimeMillis(); runCatching { com.mrm.pgmanager.widget.PanelWidgetProvider.updateAll(context) } }
            evaluateHealth(it)
        }.onFailure { e ->
            if (e.message?.contains("401") == true) { android.widget.Toast.makeText(context, context.getString(R.string.us_session_expired), android.widget.Toast.LENGTH_LONG).show(); onLogout() }
            else {
                if (settings.notificationsEnabled && settings.notifyPanelOffline && !panelOfflineAlerted) { NotificationHelper.post(context, 3104, NotificationHelper.CHANNEL_SYSTEM, context.getString(R.string.mw_unreachable), context.getString(R.string.db_error_stats)); panelOfflineAlerted = true }
                val cache = if (settings.offlineCacheEnabled) store.readStatsCache() else null
                if (cache != null) { stats = cache.first; offlineAt = cache.second; error = null } else error = e.message ?: context.getString(R.string.db_error_stats)
            }
        }
        runCatching { PanelApi.trafficUsage(session) }.onSuccess { trafficPoints = it; PanelCache.put(trafficKey, it) }
        runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
            if (settings.notificationsEnabled && settings.notifyNodeOffline && lastNodeStates.isNotEmpty()) states.forEach { (id, online) ->
                val prev = lastNodeStates[id]; if (prev == true && !online) NotificationHelper.post(context, 4100+id, NotificationHelper.CHANNEL_SYSTEM, context.getString(R.string.mw_node_offline), context.getString(R.string.mw_node_offline_body, id))
                if (prev == false && online) NotificationHelper.post(context, 4200+id, NotificationHelper.CHANNEL_SYSTEM, context.getString(R.string.mw_node_online), context.getString(R.string.mw_node_online_body, id))
            }
            lastNodeStates = states
        }
        loading = false
    }
    LaunchedEffect(session, settings.autoRefreshEnabled, settings.refreshIntervalSeconds) {
        // اگر داده تازه است، همین حالا درخواست نمی‌دهیم؛ سوایپ نباید با یک
        // ریکوئستِ همزمان سنگین شود. قبلاً حلقه اول load می‌کرد بعد delay، پس
        // هر بار که این تب ساخته می‌شد یک درخواستِ فوری می‌رفت.
        if (!PanelCache.isFresh(statsKey)) load(silent = stats != null)
        if (settings.autoRefreshEnabled) while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            kotlinx.coroutines.delay(settings.refreshIntervalSeconds.coerceIn(5,3600)*1_000L)
            if (inForeground) load(silent = true)
        }
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = manualRefreshing, onRefresh = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, state = pullState, modifier = Modifier.fillMaxSize(),
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = manualRefreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter), containerColor = theme.cardSurfaceColor, color = theme.accentPrimary) }) {

        Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = DsSpacing.Screen, end = DsSpacing.Screen, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Header: PasarGuard style top bar (Dashboard title + Quick Actions yellow button mimic)
            Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.dashboard), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.24f)), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("ⓘ", fontSize = 10.sp, color = theme.accentPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(stringResource(R.string.dashboard_subtitle), fontSize = 10.sp, color = theme.mutedColor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).pressScale(0.92f).clickable { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, contentAlignment = Alignment.Center) {
                        // به‌جای عوض‌شدنِ آیکون با یک اسپینر (که پرش داشت)، خودِ
                        // آیکونِ رفرش می‌چرخد؛ حرکت پیوسته و بدونِ قطع می‌ماند.
                        RoundedAppIcon(AppIcon.Refresh, tint = if (manualRefreshing) theme.accentPrimary else theme.mutedColor, size = 16.dp, modifier = Modifier.spinWhile(manualRefreshing))
                    }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).clickable(onClick = onOpenSettings).semantics { contentDescription = settingsLabel }, contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Settings, tint = theme.mutedColor, size = 16.dp)
                    }
                }
            }

            if (loading && stats == null) Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accentPrimary) }
            error?.let { Text(it, color = com.mrm.pgmanager.ui.theme.GlassRed, fontSize = 11.sp) }
            offlineAt?.let { cachedAt ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.24f)), DsRadius.Md).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    RoundedAppIcon(AppIcon.Warning, tint = if (theme.isDark) theme.accentPrimary else GlassAmber, size = 12.dp)
                    Text(stringResource(R.string.offline_state, java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))), color = if (theme.isDark) theme.accentPrimary else theme.accentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            stats?.let { s ->
                // ── معیارهای سیستم، حالا با نمودارِ حلقه‌ای
                //
                // قبلاً هر کاشی فقط عدد داشت و «چقدر پر شده» را باید ذهنی حساب
                // می‌کردی؛ حلقه همان نسبت را در یک نگاه می‌دهد و رنگش (سبز/کهربایی/
                // قرمز) خودش هشدار است.
                val memFraction = if (s.memTotal > 0) (s.memUsed.toFloat() / s.memTotal) else 0f
                val diskFraction = if (s.diskTotal > 0) (s.diskUsed.toFloat() / s.diskTotal) else 0f
                val cpuFraction = (s.cpuUsage / 100f).coerceIn(0f, 1f)
                val totalTraffic = s.incomingBandwidth + s.outgoingBandwidth
                val downShare = if (totalTraffic > 0) s.incomingBandwidth.toFloat() / totalTraffic else 0f

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    PGRingStatCard(
                        label = stringResource(R.string.disk_usage),
                        value = "${formatBytes(s.diskUsed)}/${formatBytes(s.diskTotal)}",
                        icon = AppIcon.Storage,
                        modifier = Modifier.weight(1f),
                        fraction = diskFraction,
                        percent = diskFraction.times(100).toInt(),
                        sub = stringResource(R.string.free_fmt, formatBytes((s.diskTotal - s.diskUsed).coerceAtLeast(0L)))
                    )
                    // ترافیک سقف ندارد، پس حلقه‌اش «نسبتِ پرشدن» نیست؛ سهمِ دانلود
                    // و آپلود را از کلِ ترافیک نشان می‌دهد — با دو سایه از رنگِ تم.
                    val (downColor, upColor) = accentPair()
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
                // Uptime — full width
                Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(0.7.dp, theme.accentPrimary.copy(alpha = 0.24f)), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Timer, tint = theme.accentPrimary, size = 15.dp)
                    }
                    Column {
                        Text(stringResource(R.string.uptime), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        // قبلاً اینجا «3 day, 4 hour» دستی ساخته می‌شد و در حالتِ
                        // فارسی هم انگلیسی می‌ماند؛ حالا از همان مسیرِ ترجمه‌شدهٔ
                        // صفحهٔ آمار رد می‌شود.
                        MrmText(com.mrm.pgmanager.utils.uptimeText(s.uptimeSeconds), isTechnical = true, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // ── Users section — mirrors PG Dashboard Users block
                PGSectionHeader(title = stringResource(R.string.users_section))
                // Users / Active Users 2-col
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Users, tint = theme.accentPrimary, size = 12.dp); Text(stringResource(R.string.users_section), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                        Text("${animatedCount(s.totalUsers)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    Row(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoundedAppIcon(AppIcon.CheckCircle, tint = theme.accentPrimary, size = 12.dp); Text(stringResource(R.string.active_users), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                            Text("${animatedCount(s.activeUsers)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                        // این بَج قبلاً عددِ ثابتِ «93.2%» بود و با دادهٔ واقعی جور
                        // درنمی‌آمد؛ حالا از خودِ آمار حساب می‌شود.
                        PGBadge(percentOf(s.activeUsers, s.totalUsers))
                    }
                }
                // Online Users full width
                Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Wifi, tint = theme.accentPrimary, size = 12.dp); Text(stringResource(R.string.online_users), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                        Text("${animatedCount(s.onlineUsers)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    PGBadge(percentOf(s.onlineUsers, s.totalUsers))
                }

                // ── Total Admins block (like screenshot bottom)
                Text(stringResource(R.string.total_admins), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    // Left: Users breakdown card
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.users_section), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                        Text(stringResource(R.string.monitor_users), fontSize = 10.sp, color = theme.mutedColor)
                        // رنگِ نقطه قبلاً با مقایسهٔ متنِ انگلیسیِ برچسب انتخاب می‌شد
                        // و در حالتِ فارسی همهٔ نقطه‌ها بی‌رنگ می‌شدند؛ حالا رنگ
                        // کنارِ خودِ ردیف تعریف شده و به زبان کاری ندارد.
                        listOf(
                            UserBreakdownRow(stringResource(R.string.users_section), s.totalUsers, Color.Transparent, false),
                            UserBreakdownRow(stringResource(R.string.active_users), s.activeUsers, Color(0xFF16A34A), true),
                            UserBreakdownRow(stringResource(R.string.online_users), s.onlineUsers, Color(0xFF22C55E), true),
                            UserBreakdownRow(stringResource(R.string.expired_users), s.expiredUsers, Color(0xFFF97316), true),
                            UserBreakdownRow(stringResource(R.string.limited_users), s.limitedUsers, Color(0xFFEF4444), true),
                            UserBreakdownRow(stringResource(R.string.on_hold_users), s.onHoldUsers, Color(0xFFA855F7), true),
                            UserBreakdownRow(stringResource(R.string.disabled_users), s.disabledUsers, Color(0xFF6B7280), true),
                        ).forEach { row ->
                            val pct = if (row.showPercent && s.totalUsers > 0)
                                String.format(java.util.Locale.US, "%.0f%%", row.count * 100.0 / s.totalUsers) else null
                            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (row.dot != Color.Transparent) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(row.dot))
                                    else RoundedAppIcon(AppIcon.Users, tint = theme.mutedColor, size = 12.dp)
                                    Text(row.label, fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (pct != null) Text(pct, fontSize = 10.sp, color = theme.mutedColor)
                                    Text("${animatedCount(row.count)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                }
                            }
                        }
                    }
                    // Right: Usage chart card
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(stringResource(R.string.usage), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text(stringResource(R.string.monitor_traffic_desc), fontSize = 10.sp, color = theme.mutedColor, lineHeight = 12.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.5.dp, theme.borderColor), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(stringResource(R.string.days_7) + " ▾", fontSize = 10.sp, color = theme.mutedColor)
                            }
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.5.dp, theme.borderColor), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(stringResource(R.string.auto) + " ▾", fontSize = 10.sp, color = theme.mutedColor)
                            }
                        }
                        UsageMiniChart(points = trafficPoints, themeIsDark = theme.isDark, accent = theme.accentPrimary)
                        run {
                            val totalPeriod = trafficPoints.sumOf { it.totalTraffic }
                            val trendingText = if (trafficPoints.size >= 2) {
                                val first = trafficPoints.first().totalTraffic.toDouble().coerceAtLeast(1.0)
                                val last = trafficPoints.last().totalTraffic.toDouble()
                                val diff = ((last - first) / first * 100).toInt()
                                if (diff >= 0) stringResource(R.string.trending_up, diff) else stringResource(R.string.trending_down, -diff)
                            } else stringResource(R.string.no_trend)
                            val trendingColor = if (trafficPoints.size >= 2 && trafficPoints.last().totalTraffic >= trafficPoints.first().totalTraffic) Color(0xFF16A34A) else Color(0xFFDC2626)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(trendingText, fontSize = 10.sp, color = trendingColor, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.usage_during_period, formatBytes(totalPeriod)) + "\n" + stringResource(R.string.total_traffic_desc), fontSize = 10.sp, color = theme.mutedColor, lineHeight = 12.sp)
                            }
                        }
                    }
                }

                // Debtor block
                if (debtorCount > 0) {
                    Text(stringResource(R.string.debtors), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                        PGStatCard(label = stringResource(R.string.db_debtor_count), value = stringResource(R.string.db_debtor_people, debtorCount), icon = AppIcon.Warning, modifier = Modifier.weight(1f), accent = com.mrm.pgmanager.ui.theme.GlassRed)
                        PGStatCard(label = stringResource(R.string.db_debt_total), value = "${formatDebtorAmountFull(debtorTotalAmount)} $debtorCurrency", icon = AppIcon.Money, modifier = Modifier.weight(1f), accent = com.mrm.pgmanager.ui.theme.GlassRed)
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
        for (i in 1..3) drawLine(if (themeIsDark) Color.White.copy(0.08f) else Color(0xFFE5E7EB), androidx.compose.ui.geometry.Offset(0f, h * i / 4f), androidx.compose.ui.geometry.Offset(w, h * i / 4f), 0.7f)
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

private data class UserBreakdownRow(
    val label: String,
    val count: Int,
    val dot: Color,
    val showPercent: Boolean
)

/** درصدِ یک بخش از کل، با یک رقمِ اعشار — «۸۹.۰%». اگر کل صفر باشد، «—». */
private fun percentOf(part: Int, total: Int): String =
    if (total <= 0) "—" else String.format(java.util.Locale.US, "%.1f%%", part * 100.0 / total)

private fun formatDebtorAmountFull(amount: Long): String = when {
    amount == 0L -> "0"
    amount >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2fB", amount / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    amount >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", amount / 1_000_000.0).trimEnd('0').trimEnd('.')
    amount >= 1000L -> "%,d".format(java.util.Locale.US, amount)
    else -> amount.toString()
}
