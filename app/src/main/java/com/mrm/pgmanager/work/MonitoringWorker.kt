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
