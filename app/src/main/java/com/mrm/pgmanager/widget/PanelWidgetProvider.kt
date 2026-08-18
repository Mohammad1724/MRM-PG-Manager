package com.mrm.pgmanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.storage.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ویجت «وضعیت پنل»:
 * - کاربران آنلاین/کل
 * - مصرف RAM و CPU
 * - تعداد بدهکاران
 * - دکمه بروزرسانی دستی
 * - استایل Material You (رنگ داینامیک در اندروید ۱۲+) با پس‌زمینه نیمه‌شفاف تیره برای نسخه‌های قدیمی‌تر
 */
class PanelWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.mrm.pgmanager.widget.ACTION_REFRESH"

        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PanelWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun formatRam(bytes: Long): String {
            if (bytes <= 0L) return "-"
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) "%.1fG".format(gb) else "%.0fM".format(bytes / (1024.0 * 1024.0))
        }

        private fun buildViews(context: Context): RemoteViews {
            val layoutRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                R.layout.widget_panel_material
            } else {
                R.layout.widget_panel
            }
            val views = RemoteViews(context.packageName, layoutRes)
            val store = SessionStore(context)
            val cache = runCatching { store.readStatsCache() }.getOrNull()
            val session = store.read()
            val debtors = runCatching { store.readDebtors().values.count { it.baseUrl == session?.baseUrl } }.getOrDefault(0)

            if (cache != null) {
                val (stats, ts) = cache
                val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
                // کاربران
                views.setTextViewText(R.id.w_online, stats.onlineUsers.toString())
                views.setTextViewText(R.id.w_total, "/${stats.totalUsers}")
                views.setTextViewText(R.id.w_debtors, if (debtors > 0) context.getString(R.string.wg_debtors, debtors) else context.getString(R.string.wg_no_debt))
                // RAM
                val usedGb = stats.memUsed / (1024.0 * 1024.0 * 1024.0)
                val totalGb = stats.memTotal / (1024.0 * 1024.0 * 1024.0)
                val ramPercent = if (stats.memTotal > 0L) (stats.memUsed * 100 / stats.memTotal).toInt() else 0
                views.setTextViewText(R.id.w_ram_val, "${ramPercent}%")
                views.setTextViewText(R.id.w_ram_sub, "%.1f/%.1fG".format(usedGb, totalGb))
                // CPU
                views.setTextViewText(R.id.w_cpu_val, "%.0f%%".format(stats.cpuUsage))
                views.setTextViewText(R.id.w_cpu_sub, context.getString(R.string.wg_cores, stats.cpuCores))
                views.setTextViewText(R.id.w_updated, context.getString(R.string.wg_updated, time))
            } else {
                views.setTextViewText(R.id.w_online, "-")
                views.setTextViewText(R.id.w_total, "")
                views.setTextViewText(R.id.w_debtors, context.getString(R.string.wg_no_data))
                views.setTextViewText(R.id.w_ram_val, "-")
                views.setTextViewText(R.id.w_ram_sub, "RAM")
                views.setTextViewText(R.id.w_cpu_val, "-")
                views.setTextViewText(R.id.w_cpu_sub, "CPU")
                views.setTextViewText(R.id.w_updated, context.getString(R.string.wg_open_once))
            }

            // کلیک روی ویجت: باز کردن برنامه
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    PendingIntent.getActivity(context, 11, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            }
            // دکمه بروزرسانی
            val refresh = Intent(context, PanelWidgetProvider::class.java).setAction(ACTION_REFRESH)
            views.setOnClickPendingIntent(
                R.id.w_refresh,
                PendingIntent.getBroadcast(context, 12, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            // در صورت وجود نشست، فعال بودن دکمه برقرار است
            views.setBoolean(R.id.w_refresh, "setEnabled", session != null)
            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = SessionStore(context)
                store.read()?.let { session ->
                    runCatching { PanelApi.systemStats(session) }.onSuccess { store.saveStatsCache(it) }
                }
            } finally {
                updateAll(context)
                pending.finish()
            }
        }
    }
}
