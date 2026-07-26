package com.mrm.pgmanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * ویجت صفحهٔ اصلی «وضعیت پنل»: تعداد آنلاین/کل کاربران، مصرف CPU و آخرین بروزرسانی.
 * داده‌ها از کش محلی خوانده می‌شوند و دکمهٔ بروزرسانی، آمار تازه را از پنل می‌گیرد.
 */
class PanelWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.mrm.pgmanager.widget.ACTION_REFRESH"

        /** بازسازی RemoteViews از روی کش محلی؛ هزینهٔ کم، قابل فراخوانی از هر نقطهٔ برنامه. */
        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PanelWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_panel)
            val cache = runCatching { SessionStore(context).readStatsCache() }.getOrNull()
            if (cache != null) {
                val (stats, ts) = cache
                views.setTextViewText(R.id.widget_online, stats.onlineUsers.toString())
                views.setTextViewText(R.id.widget_sub, "از ${stats.totalUsers} کاربر · CPU ${"%.0f".format(stats.cpuUsage)}٪")
                val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
                views.setTextViewText(R.id.widget_updated, "بروزرسانی: $time")
            } else {
                views.setTextViewText(R.id.widget_online, "—")
                views.setTextViewText(R.id.widget_sub, "هنوز داده‌ای دریافت نشده")
                views.setTextViewText(R.id.widget_updated, "برنامه را یک‌بار باز کن")
            }
            // کلیک روی بدنه: بازکردن برنامه
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 11, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            }
            // کلیک روی دکمه: بروزرسانی دستی از پنل
            val refresh = Intent(context, PanelWidgetProvider::class.java).setAction(ACTION_REFRESH)
            views.setOnClickPendingIntent(
                R.id.widget_refresh,
                PendingIntent.getBroadcast(context, 12, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        // گرفتن آمار تازه از پنل در پس‌زمینه و سپس بازسازی ویجت‌ها.
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
