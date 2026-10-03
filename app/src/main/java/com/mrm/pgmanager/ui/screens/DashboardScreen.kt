package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.mrm.pgmanager.ui.designsystem.DsNeutral
import com.mrm.pgmanager.ui.designsystem.DsSemantic
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
import com.mrm.pgmanager.data.model.PanelAdmin
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
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
fun DashboardScreen(session: Session, settings: MonitoringSettings, onSessionExpired: () -> Unit, onOpenSettings: () -> Unit = {}) {
    val settingsLabel = stringResource(R.string.app_settings)
    val refreshLabel = stringResource(R.string.refresh)
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
    val pageActive = rememberUpdatedState(com.mrm.pgmanager.ui.components.LocalPageActive.current)
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
    // بازهٔ نمودارِ مصرف — قبلاً دو «منو»ی تزئینی بود که کلیک هم نمی‌شدند.
    var chartRange by remember { mutableStateOf(StatsRange.LAST_7D) }
    var chartMenuOpen by remember { mutableStateOf(false) }
    // فهرستِ ادمین‌ها: فقط برای حسابی که مجوزِ خواندنش را دارد. اگر پنل ۴۰۳ داد،
    // بخش کلاً پنهان می‌شود به‌جای اینکه خطا نشان بدهیم.
    var admins by remember(session) { mutableStateOf<List<PanelAdmin>?>(null) }
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
        fun alert(id: Int, title: String, message: String, kind: String? = null) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_SYSTEM, title, message, targetTab = NotificationHelper.DEST_STATISTICS, kind = kind)
        if (s.cpuUsage >= settings.cpuThreshold) { if (!cpuAlerted) alert(3101, context.getString(R.string.mw_cpu), context.getString(R.string.mw_cpu_body, "%.1f".format(s.cpuUsage)), "system_health"); cpuAlerted = true } else cpuAlerted = false
        val ram = if (s.memTotal > 0L) (s.memUsed * 100 / s.memTotal).toInt() else 0
        if (ram >= settings.ramThreshold) { if (!ramAlerted) alert(3102, context.getString(R.string.mw_ram), context.getString(R.string.mw_ram_body, ram), "system_health"); ramAlerted = true } else ramAlerted = false
        val disk = if (s.diskTotal > 0L) (s.diskUsed * 100 / s.diskTotal).toInt() else 0
        if (disk >= settings.diskThreshold) { if (!diskAlerted) alert(3103, context.getString(R.string.mw_disk), context.getString(R.string.mw_disk_body, disk), "system_health"); diskAlerted = true } else diskAlerted = false
        if (settings.notifyCapacity && s.onlineUsers >= settings.capacityOnlineLimit) {
            if (!capacityAlerted) alert(3105, context.getString(R.string.mw_capacity), context.getString(R.string.mw_capacity_body, s.onlineUsers, settings.capacityOnlineLimit), "capacity"); capacityAlerted = true
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
            if (PanelApi.isUnauthorized(e)) { com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.us_session_expired)); onSessionExpired() }
            else {
                if (settings.notificationsEnabled && settings.notifyPanelOffline && !panelOfflineAlerted) { NotificationHelper.post(context, 3104, NotificationHelper.CHANNEL_SYSTEM, context.getString(R.string.mw_unreachable), context.getString(R.string.db_error_stats), kind = "panel_offline"); panelOfflineAlerted = true }
                val cache = if (settings.offlineCacheEnabled) store.readStatsCache() else null
                if (cache != null) { stats = cache.first; offlineAt = cache.second; error = null }
                else error = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e)
            }
        }
        runCatching { PanelApi.trafficUsage(session, chartRange) }.onSuccess { trafficPoints = it; PanelCache.put(trafficKey, it) }
        // ۴۰۳ یعنی این ادمین اجازهٔ دیدنِ بقیه را ندارد؛ لیستِ خالی = بخش پنهان.
        runCatching { PanelApi.admins(session) }.onSuccess { admins = it }.onFailure { admins = emptyList() }
        runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
            if (settings.notificationsEnabled && settings.notifyNodeOffline && lastNodeStates.isNotEmpty()) states.forEach { (id, online) ->
                val prev = lastNodeStates[id]; if (prev == true && !online) NotificationHelper.post(context, 4100+id, NotificationHelper.CHANNEL_SYSTEM, context.getString(R.string.mw_node_offline), context.getString(R.string.mw_node_offline_body, id), targetTab = NotificationHelper.DEST_STATISTICS, kind = "node_offline")
                if (prev == false && online) NotificationHelper.post(context, 4200+id, NotificationHelper.CHANNEL_SYSTEM, context.getString(R.string.mw_node_online), context.getString(R.string.mw_node_online_body, id), targetTab = NotificationHelper.DEST_STATISTICS)
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
            // فقط وقتی این تب جلوی چشم است؛ به‌عنوانِ همسایهٔ ساخته‌شده در Pager
            // نباید هر چند ثانیه چهار درخواست بفرستد (آمار، نمودار، ادمین‌ها، نودها).
            if (inForeground && pageActive.value) load(silent = true)
        }
    }
    // برگشتن به داشبورد بعد از مدتی: اگر داده کهنه است بی‌صدا تازه‌اش کن.
    var wasInactive by remember { mutableStateOf(false) }
    LaunchedEffect(pageActive.value) {
        if (!pageActive.value) { wasInactive = true; return@LaunchedEffect }
        if (!wasInactive) return@LaunchedEffect
        wasInactive = false
        if (stats != null && !PanelCache.isFresh(statsKey)) load(silent = true)
    }
    // عوض‌شدنِ بازه فقط نمودار را تازه می‌کند، نه کلِ صفحه را.
    LaunchedEffect(chartRange) {
        runCatching { PanelApi.trafficUsage(session, chartRange) }
            .onSuccess { trafficPoints = it; PanelCache.put(trafficKey, it) }
    }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = manualRefreshing, onRefresh = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, state = pullState, modifier = Modifier.fillMaxSize(),
        indicator = { PullToRefreshDefaults.Indicator(isRefreshing = manualRefreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter), containerColor = theme.cardSurfaceColor, color = theme.accentPrimary) }) {

        Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = DsSpacing.Screen, end = DsSpacing.Screen, top = DsSpacing.Mid, bottom = DsSpacing.Mid), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {

            // ── سربرگِ مشترکِ همهٔ صفحه‌ها
            PGScreenHeader(
                title = stringResource(R.string.dashboard),
                subtitle = stringResource(R.string.dashboard_subtitle),
                refreshing = manualRefreshing,
                onRefresh = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } },
                onOpenSettings = onOpenSettings,
                settingsLabel = settingsLabel,
                refreshLabel = refreshLabel
            )

            if (loading && stats == null) Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accentPrimary) }
            if (error != null && stats == null) {
                MrmErrorState(onRetry = { scope.launch { load() } }, message = error)
            } else error?.let { Text(it, color = com.mrm.pgmanager.ui.theme.GlassRed, fontSize = 11.sp) }
            offlineAt?.let { cachedAt ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm),
                    modifier = Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.24f)), DsRadius.Md).padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md)) {
                    RoundedAppIcon(AppIcon.Warning, tint = if (theme.isDark) theme.accentPrimary else GlassAmber, size = 12.dp)
                    Text(
                        stringResource(R.string.offline_state, java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))),
                        color = theme.accentTextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            stats?.let { s ->
                // ── کاربران، بالای صفحه (Above the fold)
                //
                // مهم‌ترین عددِ عملیاتیِ اپ «آنلاین» است؛ قبلاً بلوکِ کاربران
                // بعد از چهار کارتِ سیستمی و Uptime می‌نشست و روی گوشی‌های
                // کوتاه زیر اسکرول می‌ماند. حالا اولین چیزی که زیرِ سربرگ
                // می‌آید همین بلوک است؛ بنرِ آفلاین (هشدار) همچنان مقدم بر
                // همهٔ محتواست. فقط جابه‌جایی است — چیدمانِ داخلیِ کارت‌ها و
                // رنگ‌ها دست‌نخورده می‌مانند.
                // ── Users section — mirrors PG Dashboard Users block
                PGSectionHeader(title = stringResource(R.string.users_section))
                // Users / Active Users 2-col
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Card), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Users, tint = theme.accentPrimary, size = 12.dp); Text(stringResource(R.string.users_section), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                        Text("${animatedCount(s.totalUsers)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    Row(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Card), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs), modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
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
                Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Card),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                            RoundedAppIcon(AppIcon.Wifi, tint = theme.accentPrimary, size = 12.dp); Text(stringResource(R.string.online_users), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                        Text("${animatedCount(s.onlineUsers)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    PGBadge(percentOf(s.onlineUsers, s.totalUsers))
                }

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

                PGRingStatRow(spacing = DsSpacing.CardGap) {
                    PGRingStatCard(
                        label = stringResource(R.string.cpu_usage),
                        value = "${formatPercent(s.cpuUsage)}%",
                        icon = AppIcon.Gauge,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fraction = cpuFraction,
                        percent = cpuFraction.times(100).toInt(),
                        sub = stringResource(R.string.cpu_cores_fmt, s.cpuCores)
                    )
                    PGRingStatCard(
                        label = stringResource(R.string.ram_usage),
                        value = formatBytes(s.memUsed),
                        icon = AppIcon.Memory,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fraction = memFraction,
                        percent = memFraction.times(100).toInt(),
                        sub = stringResource(R.string.of_total_fmt, formatBytes(s.memTotal)) + "\n" + stringResource(R.string.free_fmt, formatBytes((s.memTotal - s.memUsed).coerceAtLeast(0L)))
                    )
                }
                PGRingStatRow(spacing = DsSpacing.CardGap) {
                    PGRingStatCard(
                        label = stringResource(R.string.disk_usage),
                        value = formatBytes(s.diskUsed),
                        icon = AppIcon.Storage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fraction = diskFraction,
                        percent = diskFraction.times(100).toInt(),
                        sub = stringResource(R.string.of_total_fmt, formatBytes(s.diskTotal)) + "\n" + stringResource(R.string.free_fmt, formatBytes((s.diskTotal - s.diskUsed).coerceAtLeast(0L)))
                    )
                    // ترافیک سقف ندارد، پس حلقه‌اش «نسبتِ پرشدن» نیست؛ سهمِ دانلود
                    // و آپلود را از کلِ ترافیک نشان می‌دهد — با دو سایه از رنگِ تم.
                    val (downColor, upColor) = accentPair()
                    PGRingStatCard(
                        label = stringResource(R.string.total_traffic),
                        value = formatBytes(totalTraffic),
                        icon = AppIcon.Storage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        segments = listOf(
                            RingSegment(downShare, downColor),
                            RingSegment(1f - downShare, upColor)
                        ),
                        ringColor = theme.accentPrimary,
                        centerIcon = AppIcon.Storage,
                        sub = "↓ ${formatBytes(s.incomingBandwidth)}\n↑ ${formatBytes(s.outgoingBandwidth)}"
                    )
                }
                // Uptime — full width
                Row(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Card),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.accentPrimary.copy(alpha = 0.12f)).border(BorderStroke(0.7.dp, theme.accentPrimary.copy(alpha = 0.24f)), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        RoundedAppIcon(AppIcon.Timer, tint = theme.accentPrimary, size = 15.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.uptime), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        // قبلاً اینجا «3 day, 4 hour» دستی ساخته می‌شد و در حالتِ
                        // فارسی هم انگلیسی می‌ماند؛ حالا از همان مسیرِ ترجمه‌شدهٔ
                        // صفحهٔ آمار رد می‌شود.
                        MrmText(com.mrm.pgmanager.utils.uptimeText(s.uptimeSeconds), isTechnical = true, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    // نسخهٔ پنل (از GET /api/system) — قبلاً فقط تهِ تنظیمات دیده می‌شد.
                    if (s.version.isNotBlank()) PGBadge("PasarGuard v${s.version}")
                }

                // ── تفکیکِ کاربران و نمودارِ مصرف
                //
                // این بخش قبلاً عنوانِ «Total Admins» داشت در حالی که هیچ دادهٔ
                // ادمینی درش نبود؛ عنوان با محتوا جور نبود. ادمین‌های واقعی حالا
                // بخشِ جداگانهٔ خودشان را دارند (پایین‌تر).
                Text(stringResource(R.string.users_section), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.CardGap)) {
                    // Left: Users breakdown card
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Mid), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                        Text(stringResource(R.string.users_section), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                        Text(stringResource(R.string.monitor_users), fontSize = 11.sp, color = theme.mutedColor)
                        // رنگِ نقطه قبلاً با مقایسهٔ متنِ انگلیسیِ برچسب انتخاب می‌شد
                        // و در حالتِ فارسی همهٔ نقطه‌ها بی‌رنگ می‌شدند؛ حالا رنگ
                        // کنارِ خودِ ردیف تعریف شده و به زبان کاری ندارد.
                        listOf(
                            UserBreakdownRow(stringResource(R.string.users_section), s.totalUsers, Color.Transparent, false),
                            UserBreakdownRow(stringResource(R.string.active_users), s.activeUsers, DsSemantic.Success, true),
                            UserBreakdownRow(stringResource(R.string.online_users), s.onlineUsers, DsSemantic.Success, true),
                            UserBreakdownRow(stringResource(R.string.expired_users), s.expiredUsers, DsSemantic.Warning, true),
                            UserBreakdownRow(stringResource(R.string.limited_users), s.limitedUsers, DsSemantic.Danger, true),
                            UserBreakdownRow(stringResource(R.string.on_hold_users), s.onHoldUsers, DsSemantic.Violet, true),
                            UserBreakdownRow(stringResource(R.string.disabled_users), s.disabledUsers, DsNeutral.Muted, true),
                        ).forEach { row ->
                            val pct = if (row.showPercent && s.totalUsers > 0)
                                String.format(java.util.Locale.US, "%.0f%%", row.count * 100.0 / s.totalUsers) else null
                            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                                    if (row.dot != Color.Transparent) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(row.dot))
                                    else RoundedAppIcon(AppIcon.Users, tint = theme.mutedColor, size = 12.dp)
                                    Text(row.label, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                                    if (pct != null) Text(pct, fontSize = 11.sp, color = theme.mutedColor)
                                    Text("${animatedCount(row.count)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                }
                            }
                        }
                    }
                    // Right: Usage chart card
                    Column(Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Mid), verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(stringResource(R.string.usage), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text(stringResource(R.string.monitor_traffic_desc), fontSize = 11.sp, color = theme.mutedColor, lineHeight = 12.sp) }
                        }
                        // انتخابگرِ بازه. قبلاً دو کادرِ «7 days ▾» و «Auto ▾» بود
                        // که هیچ‌کدام کلیک نمی‌شدند — شکلِ منوی پنل کپی شده بود
                        // بدونِ رفتارش. حالا واقعاً نمودار را عوض می‌کند.
                        Box {
                            Row(
                                Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(6.dp)).clickable { chartMenuOpen = true }.background(theme.searchBgColor)
                                    .border(BorderStroke(0.5.dp, theme.borderColor), RoundedCornerShape(6.dp))
                                    .pressScale(0.95f)
                                    
                                    .padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
                            ) {
                                Text(chartRange.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                                Text("▾", fontSize = 11.sp, color = theme.mutedColor)
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = chartMenuOpen,
                                onDismissRequest = { chartMenuOpen = false }
                            ) {
                                StatsRange.entries.forEach { r ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(r.label, fontSize = 12.sp) },
                                        onClick = { chartRange = r; chartMenuOpen = false }
                                    )
                                }
                            }
                        }
                        UsageChart(
                            points = trafficPoints,
                            accent = theme.accentPrimary,
                            themeIsDark = theme.isDark,
                            height = 90.dp,
                            showAxisLabels = false,
                            showScaleLabels = false,
                            valueFormatter = ::formatBytes,
                            // فاز ۵.۴: نمودارِ بی‌داده کاربر را رها نمی‌کند.
                            emptyActionLabel = stringResource(R.string.chart_empty_refresh),
                            onEmptyAction = { scope.launch { load(silent = true) } }
                        )
                        run {
                            val totalPeriod = trafficPoints.sumOf { it.totalTraffic }
                            val trendingText = if (trafficPoints.size >= 2) {
                                val first = trafficPoints.first().totalTraffic.toDouble().coerceAtLeast(1.0)
                                val last = trafficPoints.last().totalTraffic.toDouble()
                                val diff = ((last - first) / first * 100).toInt()
                                if (diff >= 0) stringResource(R.string.trending_up, diff) else stringResource(R.string.trending_down, -diff)
                            } else stringResource(R.string.no_trend)
                            val trendingColor = if (trafficPoints.size >= 2 && trafficPoints.last().totalTraffic >= trafficPoints.first().totalTraffic) DsSemantic.Success else DsSemantic.Danger
                            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
                                Text(trendingText, fontSize = 11.sp, color = trendingColor, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.usage_during_period, formatBytes(totalPeriod)) + "\n" + stringResource(R.string.total_traffic_desc), fontSize = 11.sp, color = theme.mutedColor, lineHeight = 12.sp)
                            }
                        }
                    }
                }

                // ── ادمین‌های واقعیِ پنل
                admins?.takeIf { it.isNotEmpty() }?.let { list ->
                    Text(stringResource(R.string.db_admins), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                            .padding(DsSpacing.Mid),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                    ) {
                        Text(stringResource(R.string.db_admins_desc), fontSize = 11.sp, color = theme.mutedColor)
                        list.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm)
                                    .padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
                            ) {
                                RoundedAppIcon(
                                    AppIcon.User,
                                    tint = if (a.status == "active") theme.accentPrimary else theme.mutedColor,
                                    size = 13.dp
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                        MrmText(a.username, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, isTechnical = true)
                                        if (a.isOwner) PGBadge(stringResource(R.string.db_admin_owner))
                                    }
                                    Text(
                                        stringResource(R.string.db_admin_users, a.totalUsers),
                                        fontSize = 11.sp, color = theme.mutedColor, maxLines = 1
                                    )
                                }
                                MrmText(
                                    if (a.dataLimit != null) "${formatBytes(a.usedTraffic)}/${formatBytes(a.dataLimit)}"
                                    else formatBytes(a.usedTraffic),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, isTechnical = true
                                )
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
                Spacer(Modifier.height(DsSpacing.NavClearance))
            }
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
