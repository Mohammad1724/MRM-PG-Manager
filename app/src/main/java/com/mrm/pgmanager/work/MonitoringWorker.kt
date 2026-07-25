package com.mrm.pgmanager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.utils.NotificationHelper

class MonitoringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val session = store.read() ?: return Result.success()
        val settings = store.readMonitoringSettings()
        if (!settings.notificationsEnabled) return Result.success()
        return runCatching {
            val stats = PanelApi.systemStats(session)
            val oldStates = store.readNotificationStates()
            val users = PanelApi.users(session)
            val newStates = users.associate { user ->
                val usage = if (user.dataLimit > 0) ((user.usedTraffic * 100L) / user.dataLimit).toInt() else 0
                val nearExpiry = runCatching { val date = java.time.LocalDate.parse(user.expire?.take(10) ?: ""); java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), date).coerceAtLeast(0) <= settings.nearExpiryDays }.getOrDefault(false)
                user.id to "${user.status}|$usage|$nearExpiry"
            }
            if (oldStates.isNotEmpty()) users.forEach { user ->
                val old = oldStates[user.id] ?: return@forEach; val now = newStates[user.id] ?: return@forEach
                if (old == now) return@forEach
                fun notify(kind: String, title: String, body: String) = NotificationHelper.post(applicationContext, (kind + user.id).hashCode(), NotificationHelper.CHANNEL_EVENTS, title, body)
                if (settings.notifyLimited && user.status == "limited" && !old.startsWith("limited")) notify("limited", "کاربر محدود شد", "${user.username} به سقف حجم رسیده است")
                if (settings.notifyExpired && user.status == "expired" && !old.startsWith("expired")) notify("expired", "اشتراک منقضی شد", "اشتراک ${user.username} منقضی شده است")
                val oldUsage = old.split("|").getOrNull(1)?.toIntOrNull() ?: 0; val usage = if (user.dataLimit > 0) ((user.usedTraffic * 100L) / user.dataLimit).toInt() else 0
                if (settings.notifyNearLimit && usage >= settings.nearLimitPercent && oldUsage < settings.nearLimitPercent) notify("near_limit", "هشدار مصرف", "${user.username} به $usage٪ مصرف رسیده است")
                if (settings.notifyNearExpiry && now.substringAfterLast("|").toBoolean() && !old.substringAfterLast("|").toBoolean()) notify("near_expiry", "هشدار انقضا", "اشتراک ${user.username} نزدیک به انقضا است")
            }
            store.saveNotificationStates(newStates)
            if (settings.notifySystemHealth) {
                if (stats.cpuUsage >= settings.cpuThreshold) NotificationHelper.post(applicationContext, 5101, NotificationHelper.CHANNEL_SYSTEM, "هشدار CPU", "مصرف CPU به ${"%.1f".format(stats.cpuUsage)}٪ رسیده است")
                val ram = if (stats.memTotal > 0) (stats.memUsed * 100 / stats.memTotal).toInt() else 0
                if (ram >= settings.ramThreshold) NotificationHelper.post(applicationContext, 5102, NotificationHelper.CHANNEL_SYSTEM, "هشدار RAM", "مصرف RAM به $ram٪ رسیده است")
                val disk = if (stats.diskTotal > 0) (stats.diskUsed * 100 / stats.diskTotal).toInt() else 0
                if (disk >= settings.diskThreshold) NotificationHelper.post(applicationContext, 5103, NotificationHelper.CHANNEL_SYSTEM, "هشدار Disk", "مصرف Disk به $disk٪ رسیده است")
            }
            Result.success()
        }.getOrElse {
            if (settings.notifyPanelOffline) NotificationHelper.post(applicationContext, 5104, NotificationHelper.CHANNEL_SYSTEM, "اتصال به پنل ناموفق", "بررسی دوره‌ای نتوانست به پنل PasarGuard متصل شود")
            Result.retry()
        }
    }
}
