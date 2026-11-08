package com.mrm.pgmanager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.NotificationHelper
import com.mrm.pgmanager.utils.NotificationIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class MonitoringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /**
     * آیا خودِ دستگاه اینترنت دارد؟
     *
     * بدونِ این بررسی، هر بار که گوشی آفلاین بود (پرواز، بی‌آنتنی، قطعِ VPN)
     * بررسیِ دوره‌ای شکست می‌خورد و اعلانِ «اتصال به پنل ناموفق» می‌آمد — در حالی
     * که پنل سالم بود و مشکل از خودِ گوشی بود. آن اعلانِ تکراری از همین‌جا می‌آمد.
     */
    private fun deviceHasInternet(): Boolean {
        val cm = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val session = store.read() ?: return Result.success()
        val settings = store.readMonitoringSettings()
        return runCatching {
            val stats = PanelApi.systemStats(session)
            if (store.read() != session) return Result.success()
            // اتصال برقرار شد؛ latch مربوط به آفلاین/انقضای نشست ریست می‌شود.
            store.saveAlertFlag(session, "panel_offline", false)
            store.saveAlertCount(session, "panel_offline", 0)
            // کش آفلاین/ویجت: آخرین وضعیت موفق همیشه به‌روز نگه داشته می‌شود (حتی اگر اعلان‌ها خاموش باشند).
            store.saveStatsCache(session, stats)
            // بروزرسانی ویجت پس از کش جدید
            com.mrm.pgmanager.widget.PanelWidgetProvider.updateAll(applicationContext)

            // فهرستِ کاملِ کاربران فقط وقتی دانلود می‌شود که واقعاً لازم باشد:
            //  • اعلان‌های به‌ازای هر کاربر (limited/expired/near-limit/near-expiry) روشن باشند، یا
            //  • کشِ آفلاین روشن باشد (فهرست برای حالتِ بی‌اینترنت).
            // قبلاً هر ۱۵ دقیقه همهٔ کاربران (روی پنل‌های بزرگ چند مگابایت) گرفته می‌شد،
            // حتی وقتی همهٔ این‌ها خاموش بودند و فقط سلامتِ سیستم مهم بود.
            val perUserNotifications = settings.notificationsEnabled &&
                (settings.notifyLimited || settings.notifyExpired || settings.notifyNearLimit || settings.notifyNearExpiry)
            val needUsers = perUserNotifications || settings.offlineCacheEnabled
            val users: List<com.mrm.pgmanager.data.model.PanelUser> = if (needUsers) PanelApi.users(session) else emptyList()
            if (store.read() != session) return Result.success()
            if (needUsers) store.saveUsersCache(session, users)
            // اعلان‌ها فقط در صورت فعال‌بودن؛ کش/ویجت بالا مستقل از اعلان‌هاست.
            if (settings.notificationsEnabled) {
            if (perUserNotifications) {
            val oldStates = store.readNotificationStates(session)
            val newStates = users.associate { user ->
                val usage = if (user.dataLimit > 0L) ((user.usedTraffic * 100L) / user.dataLimit).toInt() else 0
                val nearExpiry = DateLogic.isNearExpiry(user.expire, settings.nearExpiryDays)
                user.id to "${user.status}|$usage|$nearExpiry"
            }
            if (oldStates.isNotEmpty()) users.forEach { user ->
                val old = oldStates[user.id] ?: return@forEach; val now = newStates[user.id] ?: return@forEach
                if (old == now) return@forEach
                fun notify(kind: String, title: String, body: String) = NotificationHelper.post(applicationContext, NotificationIds.userEvent(kind, user.id), NotificationHelper.CHANNEL_EVENTS, title, body, targetUsername = user.username, kind = kind)
                if (settings.notifyLimited && user.status == "limited" && !old.startsWith("limited")) notify("limited", applicationContext.getString(R.string.us_n_limited), applicationContext.getString(R.string.us_n_limited_body, user.username))
                if (settings.notifyExpired && user.status == "expired" && !old.startsWith("expired")) notify("expired", applicationContext.getString(R.string.us_n_expired), applicationContext.getString(R.string.us_n_expired_body, user.username))
                val oldUsage = old.split("|").getOrNull(1)?.toIntOrNull() ?: 0; val usage = if (user.dataLimit > 0L) ((user.usedTraffic * 100L) / user.dataLimit).toInt() else 0
                if (settings.notifyNearLimit && usage >= settings.nearLimitPercent && oldUsage < settings.nearLimitPercent) notify("near_limit", applicationContext.getString(R.string.us_n_near_limit), applicationContext.getString(R.string.us_n_near_limit_body, user.username, usage))
                if (settings.notifyNearExpiry && now.substringAfterLast("|").toBoolean() && !old.substringAfterLast("|").toBoolean()) notify("near_expiry", applicationContext.getString(R.string.us_n_near_expiry), applicationContext.getString(R.string.us_n_near_expiry_body, user.username))
            }
            store.saveNotificationStates(session, newStates)
            } // if perUserNotifications

            // وضعیت نودها: تغییر آنلاین/آفلاین در پس‌زمینه هم هشدار می‌دهد (baseline ذخیره می‌شود).
            // فقط وقتی هشدارِ نود روشن است؛ وگرنه یک درخواستِ اضافه (و برای ادمینِ بدونِ nodes.stats یک ۴۰۳ بیهوده) است.
            if (settings.notifyNodeOffline) runCatching { PanelApi.nodeOnlineStates(session) }.onSuccess { states ->
                if (store.read() != session) return Result.success()
                val oldNodes = store.readNodeStates(session)
                if (settings.notifyNodeOffline && oldNodes.isNotEmpty()) states.forEach { (id, online) ->
                    val prev = oldNodes[id]
                    if (prev == true && !online) NotificationHelper.post(applicationContext, NotificationIds.nodeOffline(id), NotificationHelper.CHANNEL_SYSTEM, applicationContext.getString(R.string.mw_node_offline), applicationContext.getString(R.string.mw_node_offline_body, id), targetTab = NotificationHelper.DEST_STATISTICS, kind = "node_offline")
                    if (prev == false && online) NotificationHelper.post(applicationContext, NotificationIds.nodeOnline(id), NotificationHelper.CHANNEL_SYSTEM, applicationContext.getString(R.string.mw_node_online), applicationContext.getString(R.string.mw_node_online_body, id), targetTab = NotificationHelper.DEST_STATISTICS)
                }
                if (store.read() == session) store.saveNodeStates(session, states)
            }
            } // if notificationsEnabled
            currentCoroutineContext().ensureActive()
            if (store.read() != session) return Result.success()

            // === بدهکاران: قطع خودکار پس از X ساعت ===
            if (settings.debtorAutoDisableEnabled) {
                val debtors = store.readDebtors().values.filter { it.baseUrl == session.baseUrl }
                debtors.forEach { d ->
                    currentCoroutineContext().ensureActive()
                    if (store.read() != session) return Result.success()
                    if (d.autoDisabled) return@forEach
                    if (!d.isOverdue(settings.debtorAutoDisableAfterHours)) return@forEach
                    // کاربر را در لیست پیدا کن؛ اگر فهرستِ کامل دانلود نشده، فقط همین یک نفر را از پنل می‌پرسیم.
                    val pu = users.find { it.username == d.username }
                        ?: runCatching {
                            // مسیرِ سبک: `/api/users/simple` فقط id و username می‌دهد و
                            // برای «پیدا کردنِ همین یک نفر» کافی است — نسخهٔ قبلی صفحهٔ
                            // کاملِ کاربران (با همهٔ فیلدهای سنگین) را برای یک نام می‌گرفت.
                            val ref = PanelApi.usersSimple(session, search = d.username, limit = 5)
                                .firstOrNull { it.username == d.username }
                            // وضعیت برای تصمیمِ «غیرفعال کن یا نه» لازم است؛ فقط وقتی
                            // پیدا شد همان یک کاربر را کامل می‌خوانیم.
                            ref?.let { PanelApi.user(session, com.mrm.pgmanager.data.model.PanelUser(id = it.id, username = it.username, status = "", usedTraffic = 0L, dataLimit = 0L, expire = null, createdAt = null)) }
                        }.getOrNull()
                        ?: return@forEach
                    currentCoroutineContext().ensureActive()
                    if (store.read() != session) return Result.success()
                    if (pu.status == "disabled") {
                        // اگر دستی غیرفعال شده، فقط فلگ را بزن
                        store.setDebtor(d.copy(autoDisabled = true))
                        return@forEach
                    }
                    runCatching { PanelApi.setDisabled(session, pu, true) }.onSuccess {
                        if (store.read() != session) return Result.success()
                        store.setDebtor(d.copy(autoDisabled = true))
                        if (settings.notificationsEnabled && settings.notifyDebtorOverdue) {
                            NotificationHelper.post(applicationContext, NotificationIds.debtorOverdue(d.username), NotificationHelper.CHANNEL_EVENTS, applicationContext.getString(R.string.us_n_auto_disable), applicationContext.getString(R.string.us_n_auto_disable_body, d.username, settings.debtorAutoDisableAfterHours, d.amount.toString(), d.currency), targetUsername = d.username, kind = "debtor_overdue")
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (store.read() != session) return Result.success()

            // هشدار سلامت سیستم با latch: تا وقتی شرط برقرار است فقط یک‌بار هشدار می‌دهیم؛
            // با برطرف‌شدن شرط، latch آزاد می‌شود تا هشدار بعدی دوباره صادر شود.
            // F1: ظرفیت آنلاین سوییچِ مستقل است و نباید پشت گیتِ notifySystemHealth زندانی باشد.
            fun healthAlert(key: String, id: Int, title: String, body: String, condition: Boolean) {
                if (condition && !store.readAlertFlag(session, key)) {
                    NotificationHelper.post(applicationContext, id, NotificationHelper.CHANNEL_SYSTEM, title, body, targetTab = NotificationHelper.DEST_STATISTICS, kind = if (key == "capacity") "capacity" else "system_health")
                }
                store.saveAlertFlag(session, key, condition)
            }
            if (settings.notificationsEnabled && settings.notifySystemHealth) {
                val ram = if (stats.memTotal > 0L) (stats.memUsed * 100 / stats.memTotal).toInt() else 0
                val disk = if (stats.diskTotal > 0L) (stats.diskUsed * 100 / stats.diskTotal).toInt() else 0
                if (stats.cpuUsage.isFinite()) {
                    healthAlert("cpu", NotificationIds.CPU, applicationContext.getString(R.string.mw_cpu), applicationContext.getString(R.string.mw_cpu_body, "%.1f".format(stats.cpuUsage)), stats.cpuUsage >= settings.cpuThreshold)
                }
                healthAlert("ram", NotificationIds.RAM, applicationContext.getString(R.string.mw_ram), applicationContext.getString(R.string.mw_ram_body, ram), ram >= settings.ramThreshold)
                healthAlert("disk", NotificationIds.DISK, applicationContext.getString(R.string.mw_disk), applicationContext.getString(R.string.mw_disk_body, disk), disk >= settings.diskThreshold)
            }
            // هشدار ظرفیت آنلاین: بلوک مستقل با کلید و latch خودش؛ با خاموش‌کردن سوییچ،
            // condition=false می‌شود و latch هم خودکار ریست می‌گردد.
            if (settings.notificationsEnabled && settings.notifyCapacity) {
                healthAlert("capacity", NotificationIds.CAPACITY, applicationContext.getString(R.string.mw_capacity), applicationContext.getString(R.string.mw_capacity_body, stats.onlineUsers, settings.capacityOnlineLimit), stats.onlineUsers >= settings.capacityOnlineLimit)
            }
            Result.success()
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            if (store.read() != session) return Result.success()
            when {
                // توکن منقضی شده: اسپم نمی‌کنیم؛ فقط یک‌بار اطلاع و توقف retry.
                // نشستِ فعال کنار می‌رود (حساب در لیست می‌ماند) تا دفعهٔ بعد که اپ باز شد
                // مستقیم صفحهٔ ورودِ پیش‌پرشده را ببیند و بررسی‌های بعدی بیهوده ۴۰۱ نخورند.
                PanelApi.isUnauthorized(e) -> {
                    // Only the worker that atomically expires this active credential may notify.
                    if (store.expireActive(session)) {
                        store.invalidateUsersCache(session)
                        store.invalidateStatsCache(session)
                        if (settings.notificationsEnabled) {
                            NotificationHelper.post(applicationContext, NotificationIds.SESSION_EXPIRED, NotificationHelper.CHANNEL_SYSTEM, applicationContext.getString(R.string.mw_session), applicationContext.getString(R.string.mw_session_body))
                        }
                    }
                    Result.success()
                }
                com.mrm.pgmanager.utils.ApiErrorMapper.kindOf(e) == com.mrm.pgmanager.utils.ApiErrorKind.FORBIDDEN -> {
                    store.invalidateUsersCache(session)
                    store.invalidateStatsCache(session)
                    com.mrm.pgmanager.widget.PanelWidgetProvider.updateAll(applicationContext)
                    Result.success()
                }
                else -> {
                    // سه شرط تا وقتی اعلان بدهیم:
                    //  ۱. خودِ گوشی اینترنت داشته باشد — وگرنه تقصیرِ پنل نیست.
                    //  ۲. دو بررسیِ پشتِ‌سرِ هم شکست خورده باشد — یک قطعیِ لحظه‌ای
                    //     (ری‌استارتِ سرور، تایم‌اوتِ گذرا) ارزشِ بیدارکردنِ کاربر را ندارد.
                    //  ۳. latch باز باشد — تا وقتی پنل برنگشته، دوباره اعلان ندهیم.
                    val online = deviceHasInternet()
                    val strikes = if (online) store.readAlertCount(session, "panel_offline") + 1 else 0
                    store.saveAlertCount(session, "panel_offline", strikes)
                    if (online && strikes >= 2 &&
                        settings.notificationsEnabled && settings.notifyPanelOffline &&
                        !store.readAlertFlag(session, "panel_offline")
                    ) {
                        val host = runCatching { java.net.URI(session.baseUrl).host }.getOrNull()
                            ?.takeIf { it.isNotBlank() } ?: session.baseUrl
                        NotificationHelper.post(
                            applicationContext, NotificationIds.PANEL_OFFLINE, NotificationHelper.CHANNEL_SYSTEM,
                            applicationContext.getString(R.string.mw_unreachable),
                            applicationContext.getString(R.string.mw_unreachable_body, host),
                            kind = "panel_offline"
                        )
                        store.saveAlertFlag(session, "panel_offline", true)
                    }
                    Result.retry()
                }
            }
        }
    }
}
