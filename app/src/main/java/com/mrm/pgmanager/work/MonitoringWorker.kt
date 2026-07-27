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
            // اتصال برقرار شد؛ latch مربوط به آفلاین/انقضای نشست ریست می‌شود.
            store.saveAlertFlag("panel_offline", false)
            store.saveAlertFlag("auth_expired", false)
            // کش آفلاین/ویجت: آخرین وضعیت موفق همیشه به‌روز نگه داشته می‌شود.
            store.saveStatsCache(stats)
            // بروزرسانی ویجت پس از کش جدید
            com.mrm.pgmanager.widget.PanelWidgetProvider.updateAll(applicationContext)
            val oldStates = store.readNotificationStates()
            val users = PanelApi.users(session)
            store.saveUsersCache(users)
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

            // وضعیت نودها: تغییر آنلاین/آفلاین در پس‌زمینه هم هشدار می‌دهد (baseline ذخیره می‌شود).
            runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
                val oldNodes = store.readNodeStates()
                if (settings.notifyNodeOffline && oldNodes.isNotEmpty()) states.forEach { (id, online) ->
                    val prev = oldNodes[id]
                    if (prev == true && !online) NotificationHelper.post(applicationContext, 6100 + id, NotificationHelper.CHANNEL_SYSTEM, "نود آفلاین شد", "نود شماره $id در دسترس نیست")
                    if (prev == false && online) NotificationHelper.post(applicationContext, 6200 + id, NotificationHelper.CHANNEL_SYSTEM, "نود دوباره آنلاین شد", "نود شماره $id دوباره در دسترس است")
                }
                store.saveNodeStates(states)
            }

            // === بدهکاران: قطع خودکار پس از X ساعت ===
            if (settings.debtorAutoDisableEnabled) {
                val debtors = store.readDebtors().values.filter { it.baseUrl == session.baseUrl }
                debtors.forEach { d ->
                    if (d.autoDisabled) return@forEach
                    if (!d.isOverdue(settings.debtorAutoDisableAfterHours)) return@forEach
                    // کاربر را در لیست پیدا کن
                    val pu = users.find { it.username == d.username } ?: return@forEach
                    if (pu.status == "disabled") {
                        // اگر دستی غیرفعال شده، فقط فلگ را بزن
                        store.setDebtor(d.copy(autoDisabled = true))
                        return@forEach
                    }
                    runCatching { PanelApi.setDisabled(session, d.username, true) }.onSuccess {
                        store.setDebtor(d.copy(autoDisabled = true))
                        if (settings.notificationsEnabled && settings.notifyDebtorOverdue) {
                            NotificationHelper.post(applicationContext, ("debtor_"+d.username).hashCode(), NotificationHelper.CHANNEL_EVENTS, "قطع خودکار بدهکار", "${d.username} پس از ${settings.debtorAutoDisableAfterHours} ساعت بدهکاری قطع شد (${d.amount} ${d.currency})")
                        }
                    }
                }
            }

            // هشدار سلامت سیستم با latch: تا وقتی شرط برقرار است فقط یک‌بار هشدار می‌دهیم؛
            // با برطرف‌شدن شرط، latch آزاد می‌شود تا هشدار بعدی دوباره صادر شود.
            if (settings.notifySystemHealth) {
                fun healthAlert(key: String, id: Int, title: String, body: String, condition: Boolean) {
                    if (condition && !store.readAlertFlag(key)) {
                        NotificationHelper.post(applicationContext, id, NotificationHelper.CHANNEL_SYSTEM, title, body)
                    }
                    store.saveAlertFlag(key, condition)
                }
                val ram = if (stats.memTotal > 0) (stats.memUsed * 100 / stats.memTotal).toInt() else 0
                val disk = if (stats.diskTotal > 0) (stats.diskUsed * 100 / stats.diskTotal).toInt() else 0
                healthAlert("cpu", 5101, "هشدار CPU", "مصرف CPU به ${"%.1f".format(stats.cpuUsage)}٪ رسیده است", stats.cpuUsage >= settings.cpuThreshold)
                healthAlert("ram", 5102, "هشدار RAM", "مصرف RAM به $ram٪ رسیده است", ram >= settings.ramThreshold)
                healthAlert("disk", 5103, "هشدار Disk", "مصرف Disk به $disk٪ رسیده است", disk >= settings.diskThreshold)
                // هشدار ظرفیت آنلاین: ترکیب با کلید خودِ ظرفیت؛ با غیرفعال‌کردن گزینه latch هم خودکار ریست می‌شود.
                healthAlert("capacity", 5106, "هشدار ظرفیت", "کاربران آنلاین هم‌زمان به ${stats.onlineUsers} رسید (حد مجاز: ${settings.capacityOnlineLimit})", settings.notifyCapacity && stats.onlineUsers >= settings.capacityOnlineLimit)
            }
            Result.success()
        }.getOrElse { e ->
            val msg = e.message.orEmpty()
            when {
                // توکن منقضی شده: اسپم نمی‌کنیم؛ فقط یک‌بار اطلاع و توقف retry.
                msg.contains("401") -> {
                    if (!store.readAlertFlag("auth_expired")) {
                        NotificationHelper.post(applicationContext, 5105, NotificationHelper.CHANNEL_SYSTEM, "نشست منقضی شد", "برای ادامهٔ پایش، برنامه را باز کنید و دوباره وارد شوید")
                        store.saveAlertFlag("auth_expired", true)
                    }
                    Result.success()
                }
                else -> {
                    // آفلاین‌بودن پنل هم با latch اطلاع داده می‌شود، نه هر ۱۵ دقیقه.
                    if (settings.notifyPanelOffline && !store.readAlertFlag("panel_offline")) {
                        NotificationHelper.post(applicationContext, 5104, NotificationHelper.CHANNEL_SYSTEM, "اتصال به پنل ناموفق", "بررسی دوره‌ای نتوانست به پنل PasarGuard متصل شود")
                        store.saveAlertFlag("panel_offline", true)
                    }
                    Result.retry()
                }
            }
        }
    }
}
