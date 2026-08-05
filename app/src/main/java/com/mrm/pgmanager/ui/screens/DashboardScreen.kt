package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
import com.mrm.pgmanager.ui.components.ActionIconButton
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassAmber
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
    var capacityAlerted by remember { mutableStateOf(false) }
    var lastNodeStates by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    val store = remember { com.mrm.pgmanager.data.storage.SessionStore(context) }
    var offlineAt by remember { mutableStateOf<Long?>(null) }
    var lastWidgetUpdateAt by remember { mutableStateOf(0L) }
    // پایش خودکار فقط در پیش‌زمینه اجرا می‌شود (صرفه‌جویی در باتری/شبکه و جلوگیری از فشار به پنل).
    var inForeground by remember { mutableStateOf(true) }
    val lifecycle = (androidx.compose.ui.platform.LocalContext.current as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            inForeground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trafficPoints by remember { mutableStateOf<List<TrafficPoint>>(emptyList()) }
    // بدهکاران
    var debtorTotalAmount by remember { mutableStateOf(0L) }
    var debtorCount by remember { mutableStateOf(0) }
    var debtorCurrency by remember { mutableStateOf(settings.debtorCurrency) }
    fun refreshDebtors() {
        val all = store.readDebtors().values.filter { it.baseUrl == session.baseUrl }
        debtorCount = all.size
        debtorTotalAmount = all.sumOf { it.amount }
        debtorCurrency = all.firstOrNull()?.currency ?: settings.debtorCurrency
    }
    LaunchedEffect(Unit) { refreshDebtors() }
    fun evaluateHealth(s: SystemStats) {
        if (!settings.notificationsEnabled || !settings.notifySystemHealth) return
        fun alert(id: Int, title: String, message: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_SYSTEM, title, message)
        if (s.cpuUsage >= settings.cpuThreshold) { if (!cpuAlerted) alert(3101, "هشدار CPU", "مصرف CPU به ${"%.1f".format(s.cpuUsage)}٪ رسیده است"); cpuAlerted = true } else cpuAlerted = false
        val ram = if (s.memTotal > 0L) (s.memUsed * 100 / s.memTotal).toInt() else 0
        if (ram >= settings.ramThreshold) { if (!ramAlerted) alert(3102, "هشدار RAM", "مصرف RAM به $ram٪ رسیده است"); ramAlerted = true } else ramAlerted = false
        val disk = if (s.diskTotal > 0L) (s.diskUsed * 100 / s.diskTotal).toInt() else 0
        if (disk >= settings.diskThreshold) { if (!diskAlerted) alert(3103, "هشدار Disk", "مصرف Disk به $disk٪ رسیده است"); diskAlerted = true } else diskAlerted = false
        // هشدار ظرفیت: عبور تعداد کاربران آنلاین هم‌زمان از حد تعیین‌شده (با latch تا رفع شرط).
        if (settings.notifyCapacity && s.onlineUsers >= settings.capacityOnlineLimit) {
            if (!capacityAlerted) alert(3105, "هشدار ظرفیت", "کاربران آنلاین هم‌زمان به ${s.onlineUsers} رسید (حد مجاز: ${settings.capacityOnlineLimit})")
            capacityAlerted = true
        } else capacityAlerted = false
    }
    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        error = null; runCatching { PanelApi.systemStats(session) }.onSuccess { stats = it; panelOfflineAlerted = false; offlineAt = null
        // نوشتن کش (رمزنگاری‌شده) روی ترد پس‌زمینه تا UI لَگ نزند
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.saveStatsCache(it) }
        refreshDebtors()
        // ویجت حداکثر هر ۳۰ ثانیه به‌روز شود؛ در هر چرخهٔ رفرش نه (جلوگیری از بار اضافی).
        if (System.currentTimeMillis() - lastWidgetUpdateAt > 30_000L) { lastWidgetUpdateAt = System.currentTimeMillis(); runCatching { com.mrm.pgmanager.widget.PanelWidgetProvider.updateAll(context) } }
        evaluateHealth(it) }.onFailure { e ->
        if (e.message?.contains("401") == true) {
            // نشست منقضی شده؛ مانند صفحهٔ کاربران، کاربر به صفحهٔ ورود برمی‌گردد.
            android.widget.Toast.makeText(context, "نشست منقضی شد، دوباره وارد شوید", android.widget.Toast.LENGTH_LONG).show()
            onLogout()
        } else {
            if (settings.notificationsEnabled && settings.notifyPanelOffline && !panelOfflineAlerted) { NotificationHelper.post(context, 3104, NotificationHelper.CHANNEL_SYSTEM, "اتصال به پنل ناموفق", "دریافت آمار Dashboard از پنل PasarGuard ناموفق بود"); panelOfflineAlerted = true }
            // کش آفلاین: اگر دادهٔ قبلی داریم، همان نمایش داده می‌شود و خطا به بنر آفلاین تبدیل می‌شود.
            val cache = if (settings.offlineCacheEnabled) store.readStatsCache() else null
            if (cache != null) { stats = cache.first; offlineAt = cache.second; error = null }
            else error = e.message ?: "خطا در دریافت آمار"
        } }; runCatching { PanelApi.trafficUsage(session) }.onSuccess { trafficPoints = it }
        runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
            if (settings.notificationsEnabled && settings.notifyNodeOffline && lastNodeStates.isNotEmpty()) states.forEach { (id, online) ->
                val previous = lastNodeStates[id]
                if (previous == true && !online) NotificationHelper.post(context, 4100 + id, NotificationHelper.CHANNEL_SYSTEM, "نود آفلاین شد", "نود شماره $id در دسترس نیست")
                if (previous == false && online) NotificationHelper.post(context, 4200 + id, NotificationHelper.CHANNEL_SYSTEM, "نود دوباره آنلاین شد", "نود شماره $id دوباره در دسترس است")
            }
            lastNodeStates = states
        }; loading = false }
    // آمار لحظه‌ای سیستم مانند پنل: هر N ثانیه CPU/RAM/Disk و کاربران دوباره خوانده می‌شوند.
    // رفرش خودکار بی‌صدا (بدون فلش اندیکاتور) و فقط در پیش‌زمینه اجرا می‌شود.
    LaunchedEffect(session, settings.autoRefreshEnabled, settings.refreshIntervalSeconds) {
        if (settings.autoRefreshEnabled) while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            if (inForeground) load(silent = true)
            kotlinx.coroutines.delay(settings.refreshIntervalSeconds.coerceIn(5, 3600) * 1_000L)
        } else load()
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = manualRefreshing, onRefresh = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, state = pullState, modifier = Modifier.fillMaxSize(), indicator = { PullToRefreshDefaults.Indicator(isRefreshing = manualRefreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter)) }) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                com.mrm.pgmanager.ui.components.MrmText("داشبورد", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                LiveStatusBadge(settings.autoRefreshEnabled, settings.refreshIntervalSeconds)
            }
            // دکمه‌های هدر: همان کاشی‌های خاکستریِ خنثیِ پنجرهٔ تنظیمات (خروج = حالت خطر).
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionIconButton(icon = { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 19.dp) }, onClick = { onSettings() }, size = 40.dp, contentDescription = "تنظیمات")
                ActionIconButton(icon = { if (manualRefreshing) CircularProgressIndicator(Modifier.size(18.dp), color = theme.accentPrimary, strokeWidth = 2.dp) else RoundedAppIcon(AppIcon.Refresh, tint = theme.inkColor, size = 19.dp) }, onClick = { scope.launch { manualRefreshing = true; load(); manualRefreshing = false } }, enabled = !manualRefreshing, size = 40.dp, contentDescription = "بروزرسانی")
                ActionIconButton(icon = { RoundedAppIcon(AppIcon.Logout, tint = Color(0xFFC93B3B), size = 19.dp) }, onClick = { onLogout() }, isRed = true, size = 40.dp, contentDescription = "خروج از حساب")
            }
        }
        if (loading && stats == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accentPrimary) }
        error?.let { Text(it, color = Color(0xFFC93B3B), fontSize = 12.sp) }
        offlineAt?.let { cachedAt ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundedAppIcon(AppIcon.Warning, tint = GlassAmber, size = 14.dp)
                Text("حالت آفلاین — نمایش آخرین آمار دریافتی (${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))})", color = GlassAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        stats?.let { s ->
            Text("سیستم", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("CPU", "${"%.1f".format(s.cpuUsage)}%", AppIcon.Gauge, Modifier.weight(1f)); DashCard("RAM", "${formatBytes(s.memUsed)} / ${formatBytes(s.memTotal)}", AppIcon.Memory, Modifier.weight(1f)) }
            val uptimeDays = s.uptimeSeconds / 86400L
            val uptimeHours = (s.uptimeSeconds % 86400L) / 3600L
            val uptimeText = if (uptimeDays > 0L) "$uptimeDays روز و $uptimeHours ساعت" else "$uptimeHours ساعت"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("Disk", "${formatBytes(s.diskUsed)} / ${formatBytes(s.diskTotal)}", AppIcon.Storage, Modifier.weight(1f)); DashCard("Uptime", uptimeText, AppIcon.Timer, Modifier.weight(1f)) }
            Text("کاربران", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("کل کاربران", "${s.totalUsers}", AppIcon.Users, Modifier.weight(1f)); DashCard("آنلاین", "${s.onlineUsers}", AppIcon.User, Modifier.weight(1f), GlassGreen) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("فعال", "${s.activeUsers}", AppIcon.Check, Modifier.weight(1f)); DashCard("منقضی / محدود", "${s.expiredUsers + s.limitedUsers}", AppIcon.Warning, Modifier.weight(1f), Color(0xFFD9822B)) }
            // بدهکاران - مجموع کل
            if (debtorCount > 0) {
                Text("بدهکاران", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashCard("تعداد بدهکار", "$debtorCount نفر", AppIcon.Warning, Modifier.weight(1f), Color(0xFFC93B3B))
                    DashCard("مجموع بدهی", "${formatDebtorAmountFull(debtorTotalAmount)} $debtorCurrency", AppIcon.Note, Modifier.weight(1f), Color(0xFFC93B3B))
                }
            }
            Text("ترافیک", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = theme.inkColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashCard("دریافت", formatBytes(s.incomingBandwidth), AppIcon.Download, Modifier.weight(1f), GlassGreen); DashCard("ارسال", formatBytes(s.outgoingBandwidth), AppIcon.Upload, Modifier.weight(1f)) }
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
        // با خاموش‌بودن رفرش خودکار، نقطهٔ وضعیت خاکستریِ ثابت می‌شود (تضادی با متن «خاموش» نداشته باشد).
        Box(Modifier.size(8.dp).background(if (enabled) GlassGreen.copy(alpha) else Color.Gray, RoundedCornerShape(4.dp)))
        Text(if (enabled) "زنده · بروزرسانی هر $seconds ثانیه" else "بروزرسانی خودکار خاموش", fontSize = 10.sp, color = LocalThemeState.current.mutedColor)
    }
}

@Composable
private fun TrafficChartCard(points: List<TrafficPoint>, incoming: Long, outgoing: Long) {
    val t = LocalThemeState.current
    Column(Modifier.fillMaxWidth().background(t.cardSurfaceColor, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, glassBorder(t.isDark, t.amoledDark)), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("نمودار مصرف زنده", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.inkColor)
        Text("دریافت ${formatBytes(incoming)} · ارسال ${formatBytes(outgoing)}", fontSize = 9.sp, color = t.mutedColor)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width; val h = size.height
            for (i in 1..4) drawLine(if (t.isDark) Color.White.copy(.12f) else Color(0xFFE8E8EC), androidx.compose.ui.geometry.Offset(0f, h * i / 5f), androidx.compose.ui.geometry.Offset(w, h * i / 5f), 1f)
            val max = points.maxOfOrNull { it.totalTraffic }?.coerceAtLeast(1L) ?: 1L
            if (points.size > 1) for (i in 0 until points.lastIndex) {
                val y1 = h - (points[i].totalTraffic.toFloat() / max * h)
                val y2 = h - (points[i + 1].totalTraffic.toFloat() / max * h)
                drawLine(t.accentPrimary, androidx.compose.ui.geometry.Offset(w*i/(points.size-1), y1), androidx.compose.ui.geometry.Offset(w*(i+1)/(points.size-1), y2), 4f)
            }
        }
        Text(if (points.isEmpty()) "دادهٔ تاریخی ترافیک از پنل دریافت نشد." else "${points.size} نقطهٔ واقعی از آمار ترافیک پنل", fontSize = 8.sp, color = t.mutedColor)
    }
}

private fun formatDebtorAmountFull(amount: Long): String {
    return when {
        amount == 0L -> "0"
        amount >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2fB", amount / 1_000_000_000.0).trimEnd('0').trimEnd('.')
        amount >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", amount / 1_000_000.0).trimEnd('0').trimEnd('.')
        amount >= 1000L -> "%,d".format(java.util.Locale.US, amount)
        else -> amount.toString()
    }
}

@Composable private fun DashCard(label: String, value: String, icon: AppIcon, modifier: Modifier, accent: Color? = null) {
    val t = LocalThemeState.current; val c = accent ?: t.accentPrimary
    Column(
        modifier
            .height(100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(t.cardSurfaceColor)
            .border(BorderStroke(1.dp, glassBorder(t.isDark, t.amoledDark)), RoundedCornerShape(18.dp))
            .padding(14.dp), 
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
            RoundedAppIcon(icon, tint = c, size = 18.dp)
            Text(label, fontSize = 11.sp, color = t.mutedColor, fontWeight = FontWeight.Bold) 
        }
        com.mrm.pgmanager.ui.components.MrmText(
            text = value, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.ExtraBold, 
            maxLines = 1,
            isTechnical = true
        )
    }
}
