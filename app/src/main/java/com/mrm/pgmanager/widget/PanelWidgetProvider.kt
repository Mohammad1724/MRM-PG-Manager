package com.mrm.pgmanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.mrm.pgmanager.MainActivity
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ویجت «وضعیت پنل» — بازطراحی کامل Material 3 / Android 12+ compliant
 *
 * - مطابق API رسمی PasarGuard: /api/system -> SystemStats
 *   online/total/active/expired/limited/disabled/on_hold + mem/cpu/disk + bandwidth + uptime
 * - بدهکاران: کش محلی SessionStore (per-panel)
 * - ریسایز هوشمند: ارتفاع ویجت را از AppWidgetOptions می‌خواند و ردیف‌ها را hide/show می‌کند
 *   110dp (3x2 min) -> فقط Online/Total/Active
 *   140dp+ -> Expired/Limited/Disabled
 *   170dp+ -> OnHold/Debtors/Traffic summary
 *   210dp+ -> CPU/RAM/Disk با ProgressBar
 *   260dp+ -> جزئیات bandwidth + cores
 * - آفلاین: اگر کش null یا قدیمی‌تر از 30 دقیقه باشد badge قرمز نمایش می‌دهد
 * - هاست: از baseUrl استخراج می‌شود (host بدون پورت)
 * - دیپ‌لینک: root -> dashboard, online/total/active -> users, debtors -> users debtor filter
 * - PendingIntent ها با requestCode یکتا per widgetId (جلوگیری از collision)
 * - پیش‌نمایش: previewLayout در panel_widget_info.xml
 * - تم: widget_bg_material با radius 28dp در Android 12+، widget_bg با 16dp در قدیمی‌تر
 */
class PanelWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.mrm.pgmanager.widget.ACTION_REFRESH"
        private const val OFFLINE_THRESHOLD_MS = 30 * 60 * 1000L // 30 دقیقه

        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PanelWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val store = SessionStore(context)
                val session = store.read()
                val cache = runCatching { store.readStatsCache() }.getOrNull()
                val debtorsCount = runCatching {
                    store.readDebtors().values.count { it.baseUrl == session?.baseUrl }
                }.getOrDefault(0)

                ids.forEach { widgetId ->
                    val options = manager.getAppWidgetOptions(widgetId)
                    val views = buildViewsForId(context, widgetId, options, session, cache, debtorsCount)
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        /** ساخت RemoteViews برای یک widgetId مشخص با توجه به سایز */
        private fun buildViewsForId(
            context: Context,
            appWidgetId: Int,
            options: Bundle?,
            session: com.mrm.pgmanager.data.model.Session?,
            cache: Pair<SystemStats, Long>?,
            debtorsCount: Int
        ): RemoteViews {
            val isMaterial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val layoutRes = if (isMaterial) R.layout.widget_panel_material else R.layout.widget_panel
            val views = RemoteViews(context.packageName, layoutRes)

            // --- سایز ویجت از options ---
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250

            // --- هاست ---
            val host = extractHost(session?.baseUrl)

            // --- وضعیت آفلاین / زمان ---
            val now = System.currentTimeMillis()
            val hasCache = cache != null
            val cacheAge = if (cache != null) now - cache.second else Long.MAX_VALUE
            val isOffline = session == null || !hasCache || cacheAge > OFFLINE_THRESHOLD_MS
            val isNoSession = session == null

            if (isNoSession) {
                // هیچ نشستی وجود ندارد
                views.setTextViewText(R.id.w_host, context.getString(R.string.wg_host_unknown))
                views.setTextViewText(R.id.w_uptime_val, "-")
                views.setTextViewText(R.id.w_updated, context.getString(R.string.wg_no_session))
                views.setViewVisibility(R.id.w_offline_badge, View.VISIBLE)
                views.setTextViewText(R.id.w_offline_badge, context.getString(R.string.wg_no_session))
                setAllValuesToDash(views)
                applySizing(views, minHeightDp, minWidthDp, hasData = false)
                attachClicks(context, views, appWidgetId, host, isNoSession = true)
                return views
            }

            // host واقعی
            views.setTextViewText(R.id.w_host, host)

            if (cache != null) {
                val (stats, ts) = cache
                // uptime
                views.setTextViewText(R.id.w_uptime_val, formatUptime(stats.uptimeSeconds))
                // updated relative
                views.setTextViewText(R.id.w_updated, formatRelativeTime(context, ts))

                // --- مقادیر اصلی ---
                views.setTextViewText(R.id.w_online_val, stats.onlineUsers.toString())
                views.setTextViewText(R.id.w_total_val, stats.totalUsers.toString())
                views.setTextViewText(R.id.w_active_val, stats.activeUsers.toString())

                views.setTextViewText(R.id.w_expired_val, stats.expiredUsers.toString())
                views.setTextViewText(R.id.w_limited_val, stats.limitedUsers.toString())
                views.setTextViewText(R.id.w_disabled_val, stats.disabledUsers.toString())
                views.setTextViewText(R.id.w_onhold_val, stats.onHoldUsers.toString())

                // debtors
                views.setTextViewText(R.id.w_debtors_val, debtorsCount.toString())

                // traffic total
                val totalTraffic = stats.incomingBandwidth + stats.outgoingBandwidth
                views.setTextViewText(R.id.w_traffic_val, formatBytes(totalTraffic))

                // resources percent
                val ramPct = if (stats.memTotal > 0) ((stats.memUsed * 100) / stats.memTotal).toInt().coerceIn(0, 100) else 0
                val diskPct = if (stats.diskTotal > 0) ((stats.diskUsed * 100) / stats.diskTotal).toInt().coerceIn(0, 100) else 0
                val cpuPct = stats.cpuUsage.toInt().coerceIn(0, 100)

                views.setTextViewText(R.id.w_cpu_val, "$cpuPct%")
                views.setTextViewText(R.id.w_ram_val, "$ramPct%")
                views.setTextViewText(R.id.w_disk_val, "$diskPct%")

                views.setProgressBar(R.id.w_cpu_bar, 100, cpuPct, false)
                views.setProgressBar(R.id.w_ram_bar, 100, ramPct, false)
                views.setProgressBar(R.id.w_disk_bar, 100, diskPct, false)

                // bandwidth details
                views.setTextViewText(R.id.w_incoming_val, formatBytes(stats.incomingBandwidth))
                views.setTextViewText(R.id.w_outgoing_val, formatBytes(stats.outgoingBandwidth))

                // cores
                val coresText = if (stats.cpuCores > 0) context.getString(R.string.wg_cores, stats.cpuCores) else ""
                views.setTextViewText(R.id.w_cores_val, coresText)

                // offline badge
                if (isOffline) {
                    views.setViewVisibility(R.id.w_offline_badge, View.VISIBLE)
                    views.setTextViewText(R.id.w_offline_badge, context.getString(R.string.wg_offline))
                } else {
                    views.setViewVisibility(R.id.w_offline_badge, View.GONE)
                }

                applySizing(views, minHeightDp, minWidthDp, hasData = true)
            } else {
                // کش وجود ندارد ولی نشست هست -> دفعه اول
                views.setTextViewText(R.id.w_uptime_val, "-")
                views.setTextViewText(R.id.w_updated, context.getString(R.string.wg_no_data))
                views.setViewVisibility(R.id.w_offline_badge, View.VISIBLE)
                views.setTextViewText(R.id.w_offline_badge, context.getString(R.string.wg_offline))
                setAllValuesToDash(views)
                applySizing(views, minHeightDp, minWidthDp, hasData = false)
            }

            attachClicks(context, views, appWidgetId, host, isNoSession = false)
            return views
        }

        private fun setAllValuesToDash(views: RemoteViews) {
            views.setTextViewText(R.id.w_online_val, "-")
            views.setTextViewText(R.id.w_total_val, "-")
            views.setTextViewText(R.id.w_active_val, "-")
            views.setTextViewText(R.id.w_expired_val, "-")
            views.setTextViewText(R.id.w_limited_val, "-")
            views.setTextViewText(R.id.w_disabled_val, "-")
            views.setTextViewText(R.id.w_onhold_val, "-")
            views.setTextViewText(R.id.w_debtors_val, "-")
            views.setTextViewText(R.id.w_traffic_val, "-")
            views.setTextViewText(R.id.w_cpu_val, "-")
            views.setTextViewText(R.id.w_ram_val, "-")
            views.setTextViewText(R.id.w_disk_val, "-")
            views.setProgressBar(R.id.w_cpu_bar, 100, 0, false)
            views.setProgressBar(R.id.w_ram_bar, 100, 0, false)
            views.setProgressBar(R.id.w_disk_bar, 100, 0, false)
            views.setTextViewText(R.id.w_incoming_val, "-")
            views.setTextViewText(R.id.w_outgoing_val, "-")
            views.setTextViewText(R.id.w_cores_val, "")
        }

        /** ریسایز هوشمند: بر اساس minHeight ردیف‌ها را hide/show می‌کند */
        private fun applySizing(views: RemoteViews, minHeightDp: Int, minWidthDp: Int, hasData: Boolean) {
            // همیشه row_main visible
            views.setViewVisibility(R.id.w_row_main, View.VISIBLE)

            when {
                minHeightDp < 140 -> {
                    // فقط ردیف اصلی
                    views.setViewVisibility(R.id.w_row_status2, View.GONE)
                    views.setViewVisibility(R.id.w_row_status3, View.GONE)
                    views.setViewVisibility(R.id.w_row_resources, View.GONE)
                    views.setViewVisibility(R.id.w_row_traffic, View.GONE)
                }
                minHeightDp < 170 -> {
                    views.setViewVisibility(R.id.w_row_status2, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_status3, View.GONE)
                    views.setViewVisibility(R.id.w_row_resources, View.GONE)
                    views.setViewVisibility(R.id.w_row_traffic, View.GONE)
                }
                minHeightDp < 210 -> {
                    views.setViewVisibility(R.id.w_row_status2, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_status3, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_resources, View.GONE)
                    views.setViewVisibility(R.id.w_row_traffic, View.GONE)
                }
                minHeightDp < 260 -> {
                    views.setViewVisibility(R.id.w_row_status2, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_status3, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_resources, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_traffic, View.GONE)
                }
                else -> {
                    // همه
                    views.setViewVisibility(R.id.w_row_status2, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_status3, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_resources, View.VISIBLE)
                    views.setViewVisibility(R.id.w_row_traffic, View.VISIBLE)
                }
            }

            // اگر عرض خیلی کم باشد (< 200dp) cores را مخفی کن
            if (minWidthDp < 200) {
                views.setViewVisibility(R.id.w_cores_val, View.GONE)
            } else {
                views.setViewVisibility(R.id.w_cores_val, View.VISIBLE)
            }
        }

        /** کلیک‌ها و دیپ‌لینک‌ها با requestCode یکتا */
        private fun attachClicks(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            host: String,
            isNoSession: Boolean
        ) {
            // base requestCode = widgetId * 100
            val base = appWidgetId * 100

            // root -> dashboard
            val dashboardIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(NotificationHelper.EXTRA_DEST, NotificationHelper.DEST_DASHBOARD)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(
                    context, base + 1, dashboardIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // online tile -> users (online filter via deep link, UsersScreen handles search? fallback to users)
            val usersIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(NotificationHelper.EXTRA_DEST, NotificationHelper.DEST_USERS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.w_tile_online,
                PendingIntent.getActivity(
                    context, base + 2, usersIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.w_tile_total,
                PendingIntent.getActivity(
                    context, base + 3, usersIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.w_tile_active,
                PendingIntent.getActivity(
                    context, base + 4, usersIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // debtors tile -> users (debtors are filtered locally, open users)
            views.setOnClickPendingIntent(
                R.id.w_tile_debtors,
                PendingIntent.getActivity(
                    context, base + 5, usersIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // refresh
            val refresh = Intent(context, PanelWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setOnClickPendingIntent(
                R.id.w_refresh,
                PendingIntent.getBroadcast(
                    context, base + 10, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // اگر نشست ندارد، دکمه رفرش را غیرفعال نشان نده ولی کلیک باز هم لاگین را باز کند
            if (isNoSession) {
                val loginIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (loginIntent != null) {
                    views.setOnClickPendingIntent(
                        R.id.w_refresh,
                        PendingIntent.getActivity(
                            context, base + 11, loginIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
        }

        // --- فرمت‌ها ---

        fun extractHost(baseUrl: String?): String {
            if (baseUrl.isNullOrBlank()) return "-"
            return try {
                val uri = URI(baseUrl.trim())
                var h = uri.host ?: baseUrl
                // اگر host شامل پورت بود جدا کن
                if (h.contains("/")) h = h.substringBefore("/")
                if (h.isBlank()) baseUrl else h
            } catch (e: Exception) {
                // fallback: حذف پروتکل و مسیر
                var s = baseUrl.removePrefix("https://").removePrefix("http://")
                s = s.substringBefore("/").substringBefore(":")
                if (s.length > 32) s.take(32) + "…" else s
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = 1024L
            val mb = kb * 1024
            val gb = mb * 1024
            val tb = gb * 1024
            return when {
                bytes >= tb -> String.format(Locale.US, "%.1f TB", bytes.toDouble() / tb)
                bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / gb)
                bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes.toDouble() / mb)
                bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / kb)
                else -> "$bytes B"
            }
        }

        fun formatUptime(seconds: Long): String {
            if (seconds <= 0L) return "-"
            val days = TimeUnit.SECONDS.toDays(seconds)
            val hours = TimeUnit.SECONDS.toHours(seconds) % 24
            val mins = TimeUnit.SECONDS.toMinutes(seconds) % 60
            return when {
                days > 0 -> "${days}d ${hours}h"
                hours > 0 -> "${hours}h ${mins}m"
                mins > 0 -> "${mins}m"
                else -> "${seconds}s"
            }
        }

        fun formatRelativeTime(context: Context, ts: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - ts
            if (diff < 0) {
                val t = SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
                return context.getString(R.string.wg_updated, t)
            }
            val mins = diff / 60000
            val rel = when {
                mins < 1 -> "now"
                mins < 60 -> "${mins}m ago"
                mins < 1440 -> "${mins / 60}h ago"
                else -> SimpleDateFormat("MM/dd HH:mm", Locale.US).format(Date(ts))
            }
            return context.getString(R.string.wg_updated, rel)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // برای هر ویجت جدا بساز تا سایز متفاوت پشتیبانی شود
        val store = SessionStore(context)
        val session = store.read()
        val cache = runCatching { store.readStatsCache() }.getOrNull()
        val debtorsCount = runCatching {
            store.readDebtors().values.count { it.baseUrl == session?.baseUrl }
        }.getOrDefault(0)

        appWidgetIds.forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val views = buildViewsForId(context, widgetId, options, session, cache, debtorsCount)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // وقتی کاربر ریسایز می‌کند، ویجت را با سایز جدید بازسازی کن
        val store = SessionStore(context)
        val session = store.read()
        val cache = runCatching { store.readStatsCache() }.getOrNull()
        val debtorsCount = runCatching {
            store.readDebtors().values.count { it.baseUrl == session?.baseUrl }
        }.getOrDefault(0)
        val views = buildViewsForId(context, appWidgetId, newOptions, session, cache, debtorsCount)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val store = SessionStore(context)
                    val sess = store.read()
                    if (sess != null) {
                        runCatching { PanelApi.systemStats(sess) }
                            .onSuccess { stats -> store.saveStatsCache(stats) }
                            .onFailure { /* keep old cache, badge will show offline */ }
                    }
                } finally {
                    updateAll(context)
                    pending.finish()
                }
            }
        }
    }
}
