package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/* ──────────────────────────────────────────────────────────────────────────
 *  state-holder صفحهٔ کاربران — فاز ۱.۳ نقشه راه UX.
 *
 *  همهٔ stateهای داده/فیلتر/انتخاب/دیالوگ‌ها + منطقِ load (صفحه‌ای/کامل/
 *  بیشتر)، ساختِ کوئری، اجرای اکشن‌ها و خروجیِ فایل اینجا جمع شده‌اند تا
 *  ترکیبِ UI در `UsersScreen.kt` بماند. رفتار عیناً همانِ قبلِ تفکیک است:
 *  همان initializerها (کشِ حافظه، نمای پیش‌فرض، بدهکاران) و همان مسیرها.
 * ────────────────────────────────────────────────────────────────────────── */

/** اندازهٔ هر صفحه از فهرستِ کاربران (سمتِ سرور). */
internal const val PAGE_SIZE = 60

/** یک عملیاتِ گروهیِ در انتظارِ تأییدِ کاربر. */
internal data class PendingBulk(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val action: () -> Unit,
    val danger: Boolean = false,
    /**
     * عملیاتِ برگشت‌ناپذیرِ پرخطر (حذفِ گروهی): واژه‌ای که کاربر باید تایپ کند.
     * `null` یعنی تأییدِ ساده کافی است.
     */
    val confirmWord: String? = null
)

internal class UsersUiState(
    val scope: CoroutineScope,
    val context: Context,
    val session: Session,
    val store: SessionStore,
    monitoring: MonitoringSettings,
    private val onSessionExpired: () -> Unit
) {
    /**
     * تنظیماتِ مانیتورینگِ در حالِ استفاده. مقدارِ اولیه از پارامترِ کامپوزیبل
     * می‌آید و بعد از هر recomposition با `SideEffect` در `UsersScreen` تازه
     * می‌شود؛ state نیست عمداً — فقط متد‌ها آن را می‌خوانند.
     */
    var monitoringSettings: MonitoringSettings = monitoring

    /** کلیدِ کشِ فهرست برای این پنل. */
    val usersKey = PanelCache.usersKey(session.baseUrl)

    // ── دادهٔ فهرست و سربرگ ──────────────────────────────────────────────
    var users by mutableStateOf<List<PanelUser>>(PanelCache.get<List<PanelUser>>(usersKey) ?: emptyList())
    var query by mutableStateOf("")
    var loading by mutableStateOf(PanelCache.get<List<PanelUser>>(usersKey) == null)
    var error by mutableStateOf<String?>(null)
    var offlineAt by mutableStateOf<Long?>(null)
    var onlineCount by mutableStateOf(PanelCache.get<List<PanelUser>>(usersKey)?.count { it.isOnline } ?: 0)
    var lastUserStates by mutableStateOf<Map<Long, String>>(emptyMap())
    // شمارنده‌های سربرگ از خودِ پنل می‌آیند (فقط یک صفحه دانلود می‌شود).
    var counts by mutableStateOf<com.mrm.pgmanager.data.model.SystemStats?>(null)

    // ── فیلتر/مرتب‌سازی/نمایش ────────────────────────────────────────────
    var currentFilter by mutableStateOf(UserFilter.ALL)
    // فیلترِ گروه — پنل خودش با پارامترِ `group` اعمالش می‌کند.
    var groupFilterId by mutableStateOf<Int?>(null)
    // فیلترِ مالک (`admin=`) — فقط در پنل‌های چندادمینی معنا دارد.
    var ownerFilter by mutableStateOf<String?>(null)
    var adminOptions by mutableStateOf<List<com.mrm.pgmanager.data.model.PanelAdmin>>(emptyList())
    var groupOptions by mutableStateOf<List<com.mrm.pgmanager.data.model.Group>>(emptyList())
    var currentSort by mutableStateOf(UserSort.CREATED)
    var viewMode by mutableStateOf(store.readViewMode())

    // ── صفحه‌بندیِ سمتِ سرور ──────────────────────────────────────────────
    var totalMatches by mutableStateOf(0)
    var loadingMore by mutableStateOf(false)
    var endReached by mutableStateOf(false)
    var firstLoad by mutableStateOf(true)

    /**
     * آفستِ جمع‌شدنِ سربرگ (پیکسل). عمداً `val` رویِ MutableState مانده تا
     * همهٔ خواندن‌ها/نوشتن‌ها `scrollOffset.value` بمانند؛ loadها هم با
     * `resetHeader` صفرش می‌کنند (همان رفتارِ قبل از تفکیک).
     */
    val scrollOffset = mutableStateOf(0f)

    // ── انتخاب و دیالوگ‌ها ───────────────────────────────────────────────
    var selectedUser by mutableStateOf<PanelUser?>(null)
    var createUser by mutableStateOf(false)
    var deleteUser by mutableStateOf<PanelUser?>(null)
    var qrUser by mutableStateOf<PanelUser?>(null)
    var selectedUserIds by mutableStateOf<Set<Long>>(emptySet())
    var createMenuOpen by mutableStateOf(false)
    var bulkCreateOpen by mutableStateOf(false)
    var exportChooserOpen by mutableStateOf(false)
    var exportPending by mutableStateOf<Pair<String, List<PanelUser>>?>(null)
    var quickActionUser by mutableStateOf<PanelUser?>(null)
    var quickTemplateUser by mutableStateOf<PanelUser?>(null)
    var quickTemplates by mutableStateOf<List<UserTemplateItem>>(emptyList())
    var quickTemplatesLoading by mutableStateOf(true)
    var quickTemplatesFailed by mutableStateOf(false)
    var invoiceDialogUser by mutableStateOf<PanelUser?>(null)
    var debtorDialogUser by mutableStateOf<PanelUser?>(null)
    var resetExpiryTarget by mutableStateOf<PanelUser?>(null)

    // ── عملیاتِ گروهی ────────────────────────────────────────────────────
    // انتخابگرِ گروه برای عملیاتِ گروهی
    var bulkGroupPicker by mutableStateOf(false)
    var bulkGroupAdd by mutableStateOf(true)
    // دیالوگِ عددی برای تمدید/افزودنِ حجمِ گروهی
    var bulkAmountKind by mutableStateOf<String?>(null)   // "days" یا "data"
    var bulkAmountText by mutableStateOf("")
    /** تعدادِ کاربرانِ متأثر از عملیات — از `dry_run` پنل؛ `-1` یعنی نامعلوم، `null` یعنی هنوز نرسیده. */
    var bulkPreviewCount by mutableStateOf<Int?>(null)
    var bulkPreviewLoading by mutableStateOf(false)
    var bulkRevokeConfirm by mutableStateOf(false)
    var showBulkTemplateDialog by mutableStateOf(false)
    var pendingBulk by mutableStateOf<PendingBulk?>(null)
    // پاک‌سازیِ منقضی‌ها
    var cleanupNames by mutableStateOf<List<String>?>(null)

    // ── بدهکاران ─────────────────────────────────────────────────────────
    var debtors by mutableStateOf(store.readDebtors())

    val debtorsForCurrentPanel: List<DebtorInfo>
        get() = debtors.values.filter { it.baseUrl == session.baseUrl }
    val debtorByUsername: Map<String, DebtorInfo>
        get() = debtorsForCurrentPanel.associateBy { it.username }
    val debtorCount: Int
        get() = debtorsForCurrentPanel.size

    /** آیا فیلترِ فعلی را پنل می‌تواند اعمال کند؟ (بدهکار و نزدیک‌به‌سقف محلی‌اند) */
    val serverMode: Boolean
        get() = currentFilter.serverSide

    fun reloadDebtors() { debtors = store.readDebtors() }

    fun fetchSub(user: PanelUser, onResult: (PanelUser) -> Unit) {
        scope.launch {
            runCatching { PanelApi.user(session, user) }.onSuccess(onResult)
                .onFailure { com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.ud_sub_failed)) }
        }
    }

    fun copySubWithFetch(user: PanelUser) {
        if (user.subUrl.isNotBlank()) copySubscription(context, user)
        else fetchSub(user) { copySubscription(context, it) }
    }

    fun qrWithFetch(user: PanelUser) {
        if (user.subUrl.isNotBlank()) qrUser = user
        else fetchSub(user) { qrUser = it }
    }

    /** نمای پیش‌فرض (بدونِ جست‌وجو/فیلتر/مرتب‌سازیِ خاص) — تنها نمایی که کش می‌شود. */
    fun isDefaultView(): Boolean =
        query.isBlank() && currentFilter == UserFilter.ALL && groupFilterId == null &&
            ownerFilter == null && currentSort == UserSort.CREATED

    fun buildQuery(offset: Int, limit: Int = PAGE_SIZE): com.mrm.pgmanager.data.model.UserQuery {
        val expiring = if (currentFilter == UserFilter.EXPIRING_SOON)
            com.mrm.pgmanager.data.model.UserQuery.expiringWindow(com.mrm.pgmanager.data.model.UserQuery.expiringWindowDays(monitoringSettings.nearExpiryDays))
        else null
        return com.mrm.pgmanager.data.model.UserQuery(
            search = query.trim().takeIf { it.isNotBlank() },
            status = currentFilter.panelStatus,
            online = currentFilter.panelOnline,
            groupId = groupFilterId,
            sort = currentSort.panelSort,
            expireAfter = expiring?.first,
            expireBefore = expiring?.second,
            noDataLimit = currentFilter.panelNoDataLimit,
            noExpire = currentFilter.panelNoExpire,
            noGroup = currentFilter.panelNoGroup,
            admin = ownerFilter,
            offset = offset,
            limit = limit
        )
    }

    /**
     * بارگذاریِ صفحه‌ایِ سمتِ سرور — حالتِ عادی.
     * فقط همان چند ده کاربری که دیده می‌شوند از شبکه می‌آیند.
     */
    fun loadPage(silent: Boolean = false, resetHeader: Boolean = true) {
        scope.launch {
            if (!silent) loading = true
            error = null
            endReached = false
            // رفرشِ بی‌صدا (خودکار/پس از عملیات) همان تعداد سطری را می‌گیرد که الان روی
            // صفحه است؛ وگرنه کاربری که سه صفحه پایین رفته بود با هر رفرش به ۶۰ سطرِ اول
            // پرت می‌شد.
            val limit = if (silent && users.size > PAGE_SIZE) users.size.coerceAtMost(PAGE_SIZE * 10) else PAGE_SIZE
            val defaultView = isDefaultView()
            // فاز ۶.۲ — رفرشِ بی‌صدا لیست را «جایگزین» نمی‌کند، «هم‌تراز» می‌کند:
            // ردیف‌های تازه از پنل می‌آیند، ولی ترتیبِ فعلی و ردیف‌هایی که هنوز
            // در پاسخ نیستند دست‌نخورده می‌مانند تا لیست نلرزد.
            val previousOrder = if (silent) users.map { it.id } else emptyList()
            runCatching { PanelApi.usersPage(session, buildQuery(0, limit)) }.onSuccess { page ->
                users = if (previousOrder.isEmpty()) page.users else {
                    val incoming = page.users.associateBy { it.id }
                    val merged = previousOrder.mapNotNull { incoming[it] } + page.users.filter { it.id !in previousOrder }
                    merged.ifEmpty { page.users }
                }
                totalMatches = page.total
                endReached = page.users.isEmpty() || page.users.size >= page.total
                offlineAt = null
                if (resetHeader) scrollOffset.value = 0f
                if (defaultView) {
                    // همان نقشی که loadAll برای کشِ حافظه/دیسک داشت — حالا در مسیرِ عادی هم:
                    // برگشتن به این تب دوباره اسکلت+درخواست نمی‌شود و حالتِ آفلاین داده دارد.
                    PanelCache.put(usersKey, page.users)
                    if (monitoringSettings.offlineCacheEnabled) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            // فهرستِ کاملی که MonitoringWorker هر ۱۵ دقیقه می‌نویسد را با یک صفحه
                            // خراب نمی‌کنیم؛ فقط اگر این صفحه خودش کلِ فهرست است یا کشِ دیسک کهنه/غایب است.
                            // و حتی وقتی فهرست کامل است، هر رفرشِ خودکار (شاید هر ۵ ثانیه) دیسک را
                            // بازنویسی نمی‌کند؛ رمزنگاری و نوشتنِ کلِ prefs برای کشِ آفلاین حداکثر دقیقه‌ای یک‌بار کافی است.
                            val age = store.usersCacheAgeMs()
                            val complete = page.users.size >= page.total
                            if (age == null || (complete && age > 60_000L) || age > 30L * 60L * 1000L) store.saveUsersCache(page.users)
                        }
                    }
                }
            }.onFailure {
                if (PanelApi.isUnauthorized(it)) {
                    com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.us_session_expired))
                    onSessionExpired()
                } else {
                    // بدونِ شبکه: فهرستِ ذخیره‌شده با برچسبِ «آفلاین» بهتر از صفحهٔ خطای خالی است.
                    val cache = if (defaultView && users.isEmpty() && monitoringSettings.offlineCacheEnabled) store.readUsersCache() else null
                    if (cache != null) {
                        users = cache.first
                        offlineAt = cache.second
                        endReached = true
                        error = null
                    } else if (!silent) error = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it)
                }
            }
            // شمارنده‌های سربرگ از خودِ پنل، نه از روی صفحهٔ دانلودشده.
            runCatching { PanelApi.systemStats(session) }.onSuccess { counts = it; onlineCount = it.onlineUsers }
            loading = false
        }
    }

    /** صفحهٔ بعدی — با نزدیک‌شدن به تهِ فهرست صدا زده می‌شود. */
    fun loadMore() {
        if (!serverMode || loadingMore || endReached || loading) return
        loadingMore = true
        scope.launch {
            runCatching { PanelApi.usersPage(session, buildQuery(users.size)) }.onSuccess { page ->
                val seen = users.map { it.id }.toSet()
                users = users + page.users.filterNot { seen.contains(it.id) }
                totalMatches = page.total
                endReached = page.users.isEmpty() || users.size >= page.total
            }.onFailure { endReached = true }
            loadingMore = false
        }
    }

    /**
     * بارگذاریِ کاملِ فهرست — فقط برای فیلترهایی که پنل نمی‌شناسد (بدهکار،
     * نزدیک‌به‌سقف) و برای کشِ آفلاین و تشخیصِ تغییرِ وضعیتِ کاربران.
     */
    fun loadAll(resetHeader: Boolean = true, silent: Boolean = false) {
        scope.launch {
            if (!silent) loading = true
            error = null
            runCatching {
                val list = PanelApi.users(session)
                users = list; onlineCount = list.count { it.isOnline }
                PanelCache.put(usersKey, list)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val age = store.usersCacheAgeMs()
                    if (age == null || age > 60_000L) store.saveUsersCache(list)
                }
                offlineAt = null
                val settings = store.readMonitoringSettings()
                val nextStates = list.associate { u ->
                    val usage = if (u.dataLimit > 0L) ((u.usedTraffic * 100L) / u.dataLimit).toInt() else 0
                    val nearExpiry = DateLogic.isNearExpiry(u.expire, settings.nearExpiryDays)
                    u.id to "${u.status}|$usage|$nearExpiry"
                }
                if (lastUserStates.isNotEmpty() && settings.notificationsEnabled) {
                    list.forEach { u ->
                        val previous = lastUserStates[u.id] ?: return@forEach
                        val current = nextStates[u.id] ?: return@forEach
                        if (previous == current) return@forEach
                        fun notify(id: Int, title: String, text: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_EVENTS, title, text, targetUsername = u.username)
                        if (settings.notifyLimited && u.status == "limited" && !previous.startsWith("limited")) notify(("limited" + u.id).hashCode(), context.getString(R.string.us_n_limited), context.getString(R.string.us_n_limited_body, u.username))
                        if (settings.notifyExpired && u.status == "expired" && !previous.startsWith("expired")) notify(("expired" + u.id).hashCode(), context.getString(R.string.us_n_expired), context.getString(R.string.us_n_expired_body, u.username))
                        val usage = if (u.dataLimit > 0L) ((u.usedTraffic * 100L) / u.dataLimit).toInt() else 0
                        val oldUsage = previous.split("|").getOrNull(1)?.toIntOrNull() ?: 0
                        if (settings.notifyNearLimit && usage >= settings.nearLimitPercent && oldUsage < settings.nearLimitPercent) notify(("near_limit" + u.id).hashCode(), context.getString(R.string.us_n_near_limit), context.getString(R.string.us_n_near_limit_body, u.username, usage))
                        val nearExpiry = current.substringAfterLast("|").toBoolean()
                        val wasNearExpiry = previous.substringAfterLast("|").toBoolean()
                        if (settings.notifyNearExpiry && nearExpiry && !wasNearExpiry) notify(("near_expire" + u.id).hashCode(), context.getString(R.string.us_n_near_expiry), context.getString(R.string.us_n_near_expiry_body, u.username))
                    }
                }
                lastUserStates = nextStates
                if (resetHeader) scrollOffset.value = 0f
            }.onFailure {
                if (PanelApi.isUnauthorized(it)) {
                    com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.us_session_expired))
                    onSessionExpired()
                } else {
                    val cache = if (monitoringSettings.offlineCacheEnabled) store.readUsersCache() else null
                    if (cache != null) {
                        users = cache.first
                        onlineCount = 0
                        offlineAt = cache.second
                        error = null
                    } else if (!silent) {
                        // پیامِ انسانی جای متنِ خام: «به پنل دسترسی پیدا نشد…»
                        error = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it)
                    }
                }
            }
            loading = false
        }
    }

    /**
     * پیش‌نمایشِ عملیاتِ گروهیِ عددی (تمددید/افزودنِ حجم) با `dry_run` پنل.
     *
     * چرا لازم است؟ چون پنل فقط کاربرانی را می‌گیرد که واقعاً هدفِ معتبرند
     * (مثلاً کاربرِ بدونِ تاریخِ انقضا در «افزودن روز» بی‌اثر است) و ادمین بدونِ
     * این عدد فکر می‌کند همهٔ انتخاب‌شده‌ها تغییر کرده‌اند.
     */
    fun loadBulkPreview(kind: String, ids: Set<Long>) {
        bulkPreviewCount = null
        if (ids.isEmpty()) { bulkPreviewLoading = false; return }
        bulkPreviewLoading = true
        scope.launch {
            val result = runCatching { PanelApi.bulkPreview(session, ids, kind) }
            bulkPreviewCount = result.getOrDefault(-1)
            bulkPreviewLoading = false
        }
    }

    /** مسیرِ درست را خودش انتخاب می‌کند. */
    fun load(resetHeader: Boolean = true, silent: Boolean = false) {
        if (serverMode) loadPage(silent = silent, resetHeader = resetHeader)
        else loadAll(resetHeader = resetHeader, silent = silent)
    }

    /**
     * **وصلهٔ موضعیِ ردیف‌ها (فاز ۶.۱)** — قلبِ «Optimistic UI».
     *
     * چرا: پیش از این هر عملیات بعد از موفقیت `load()` می‌زد که (الف) اسکلتِ
     * بارگذاری را روشن می‌کرد، (ب) سربرگ را جمع می‌کرد و (ج) لیست را از صفر
     * می‌ساخت. کاربر بعد از تأییدِ هر عمل یک سکوتِ بصری و یک پرشِ اسکرول
     * می‌دید. حالا تغییر **همان لحظه** روی ردیف‌های موجود اعمال می‌شود و بعد
     * یک رفرشِ بی‌صدا فقط برای هم‌ترازی با پنل می‌آید.
     */
    fun patchRows(ids: Set<Long>, transform: (PanelUser) -> PanelUser) {
        if (ids.isEmpty()) return
        users = users.map { if (it.id in ids) transform(it) else it }
    }

    /**
     * برداشتنِ فوریِ ردیف‌ها از لیست (برای حذف).
     *
     * عمداً بازگردانی («rollback») ندارد: اگر پنل نپذیرد، `runAction` پیام می‌دهد
     * و رفرشِ بی‌صدا ردیف را از خودِ پنل برمی‌گرداند. برگرداندنِ خوش‌بینانه‌یِ
     * ردیفی که شاید واقعاً حذف شده باشد، بدترین حالت است (کاربر فکر می‌کند اپ
     * دروغ گفته) — اینجا صداقت را به سرعت ترجیح می‌دهیم.
     */
    fun removeRows(ids: Set<Long>) {
        if (ids.isEmpty()) return
        users = users.filterNot { it.id in ids }
        totalMatches = (totalMatches - ids.size).coerceAtLeast(0)
    }

    /** عکسِ لحظه‌ایِ ردیف‌های هدف — برای بازگردانی در صورتِ خطا. */
    fun snapshotRows(ids: Set<Long>): Map<Long, PanelUser> =
        if (ids.isEmpty()) emptyMap() else users.filter { it.id in ids }.associateBy { it.id }

    /** بازگرداندنِ عکسِ لحظه‌ای (rollback). ردیف‌های حذف‌شده هم برمی‌گردند. */
    fun restoreRows(snapshot: Map<Long, PanelUser>) {
        if (snapshot.isEmpty()) return
        val present = users.map { it.id }.toSet()
        val restored = users.map { snapshot[it.id] ?: it }
        val missing = snapshot.values.filter { it.id !in present }
        // ترتیبِ اصلی حفظ می‌شود: ردیف‌های بازگشته به جای خودشان در انتها اضافه می‌شوند
        // (اگر کاربر در این فاصله فیلتر/مرتب‌سازی را عوض کرده باشد، رفرشِ بعدی راست می‌کند).
        users = restored + missing
    }

    /**
     * اجرای عملیات با پیش‌نمایشِ فوری.
     *
     * ترتیب عمدی است: **اول** وصله، **بعد** درخواست. اگر درخواست شکست بخورد
     * عکسِ لحظه‌ای برمی‌گردد و پنل هم بی‌صدا هم‌تراز می‌شود؛ اگر موفق شود فقط
     * یک رفرشِ بی‌صدا می‌آید (بدونِ اسکلت و بدونِ پرشِ اسکرول).
     */
    fun runOptimistic(
        ids: Set<Long>,
        notification: Pair<String, String>? = null,
        undo: (suspend () -> Unit)? = null,
        /** پیش‌نمایشِ فوری روشن است؛ فقط برای حالتی که می‌خواهیم فقط ثبتِ عکسِ لحظه‌ای کنیم. */
        applyPreview: Boolean = true,
        patch: (PanelUser) -> PanelUser,
        action: suspend () -> Unit
    ) {
        val snapshot = snapshotRows(ids)
        if (applyPreview) patchRows(ids, patch)
        runAction(
            notification = notification,
            undo = undo,
            rollback = { restoreRows(snapshot) }
        ) { action() }
    }

    /**
     * اجرای یک عملیاتِ نوشتاری با بازخوردِ واحد.
     *
     * @param notification (عنوان، متن) — اسنکِ موفقیت + (اختیاری) اعلانِ سیستمی.
     * @param undo عملِ **معکوس** برای «بازگرداندن»؛ اگر داده شود، اسنک یک اکشن
     *   «بازگرداندن» می‌گیرد. فقط برای عملیات‌های برگشت‌پذیر پر می‌شود
     *   (فعال/غیرفعال، افزودن/کاستنِ زمان و حجم). حذف و ریستِ مصرف برگشت‌پذیر نیستند.
     * @param successMessage پیامِ موفقیتِ مستقل (برای اجرای undo که عنوانِ اعلان ندارد).
     */
    fun runAction(
        notification: Pair<String, String>? = null,
        undo: (suspend () -> Unit)? = null,
        successMessage: String? = null,
        /**
         * بازگردانیِ پیش‌نمایشِ خوش‌بینانه در صورتِ خطا (فاز ۶.۱). برای عملیاتِ
         * بدونِ وصله (حذف/ساخت) عمداً خالی می‌ماند — وگرنه ردیفی که پنل واقعاً
         * حذف کرده به لیست برمی‌گشت و کاربر فکر می‌کرد حذف نشده.
         */
        rollback: (() -> Unit)? = null,
        action: suspend () -> Unit
    ) {
        scope.launch {
            // فاز ۵.۳ — صفر تلاشِ نوشتاری در آفلاین: به‌جای ۱۵ ثانیه انتظار و
            // خطای شبکه، همین‌جا نگه می‌داریم و با بازگشتِ اتصال خودکار اجرا می‌کنیم.
            if (!com.mrm.pgmanager.utils.NetworkStatus.isOnline(context)) {
                com.mrm.pgmanager.ui.feedback.AppFeedback.error(
                    context.getString(R.string.err_offline_queued),
                    actionLabel = context.getString(R.string.err_retry),
                    // نام‌دار و نه موقعیتی: در فاز ۶ پارامترِ `rollback` قبل از `action`
                    // اضافه شد؛ آرگومانِ موقعیتیِ چهارم به‌جای `action` روی `rollback`
                    // می‌نشست و کامپایل به «No value passed for parameter action» می‌رسید.
                    onAction = { runAction(
                        notification = notification,
                        undo = undo,
                        successMessage = successMessage,
                        rollback = rollback,
                        action = action
                    ) }
                )
                com.mrm.pgmanager.utils.NetworkStatus.awaitOnline(context)
                if (!com.mrm.pgmanager.utils.NetworkStatus.isOnline(context)) return@launch
            }
            runCatching { action() }.onFailure {
                // پیش‌نمایشِ خوش‌بینانه باید فوراً پس گرفته شود؛ بعدش پیامِ خطا.
                // رفرشِ بی‌صدا هم می‌آید تا اگر بخشی از تغییرات روی پنل اعمال شده
                // بود، لیست به واقعیتِ سرور برگردد (نه به حدسِ ما).
                rollback?.invoke()
                val kind = com.mrm.pgmanager.utils.ApiErrorMapper.kindOf(it)
                error = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it)
                if (kind == com.mrm.pgmanager.utils.ApiErrorKind.UNAUTHORIZED) {
                    com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.us_session_expired))
                    onSessionExpired()
                } else {
                    // خطاهای گذرا «تلاش دوباره» می‌گیرند؛ متنِ خام فقط در جزئیاتِ فنی.
                    val retryable = com.mrm.pgmanager.utils.ApiErrorMapper.isTransient(kind)
                    com.mrm.pgmanager.ui.feedback.AppFeedback.error(
                        message = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it),
                        actionLabel = if (retryable) context.getString(R.string.err_retry) else null,
                        onAction = if (retryable) {
                            { runAction(
                                notification = notification,
                                undo = undo,
                                successMessage = successMessage,
                                rollback = rollback,
                                action = action
                            ) }
                        } else null,
                        detail = com.mrm.pgmanager.utils.ApiErrorMapper.technical(it)
                    )
                }
            }.onSuccess {
                // بازخوردِ در‌جا (اسنک + لمس) فوری است؛ اعلانِ سیستمی فقط وقتی
                // خودِ کاربر در تنظیمات فعالش کرده باشد می‌رود (تنظیمات ← رویدادها).
                com.mrm.pgmanager.utils.Haptics.confirm(context)
                // یک «بازگرداندن» کافی است: undoِ خودِ undo پیشنهاد نمی‌شود.
                val undoRunner: (() -> Unit)? = undo?.let { inverse ->
                    {
                        runAction(successMessage = context.getString(R.string.undo_done)) { inverse() }
                    }
                }
                val headline = notification?.first ?: successMessage
                when {
                    undoRunner != null -> com.mrm.pgmanager.ui.feedback.AppFeedback.success(
                        headline ?: context.getString(R.string.action_done),
                        actionLabel = context.getString(R.string.undo),
                        onAction = undoRunner
                    )
                    headline != null -> com.mrm.pgmanager.ui.feedback.AppFeedback.success(headline)
                }
                notification?.let { (title, message) ->
                    val settings = store.readMonitoringSettings()
                    if (settings.notificationsEnabled && settings.notifyUserActions) NotificationHelper.post(context, (title + message).hashCode(), NotificationHelper.CHANNEL_ACTIONS, title, message)
                }
                // فاز ۶.۲ — رفرشِ بی‌صدا و بدونِ برگشت به بالا: ردیف‌ها همین حالا
                // به‌روز شده‌اند؛ این فقط هم‌ترازیِ نهایی با پنل است.
                load(resetHeader = false, silent = true)
            }
        }
    }

    fun exportFileName(format: String) = "mrm-users-selected-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".$format"

    fun writeExport(uri: android.net.Uri?) {
        val payload = exportPending; exportPending = null
        if (uri == null || payload == null) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                val out = context.contentResolver.openOutputStream(uri) ?: error("no stream")
                out.use { it.write(if (payload.first == "json") com.mrm.pgmanager.utils.usersToJson(payload.second).toByteArray(Charsets.UTF_8) else com.mrm.pgmanager.utils.usersToCsv(payload.second).toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val feedback = com.mrm.pgmanager.ui.feedback.AppFeedback
                if (ok) feedback.success(context.getString(R.string.us_file_saved))
                else feedback.error(context.getString(R.string.us_file_save_failed))
            }
        }
    }

    companion object {
        /**
         * فیلتر + مرتب‌سازیِ سمتِ گوشی — فقط برای فیلترهایی که پنل اعمال نمی‌کند.
         * در حالتِ سمتِ سرور، پنل قبلاً فیلتر و مرتب کرده؛ دوباره‌کاری در گوشی فقط
         * نتیجه را خراب می‌کند (مثلاً صفحهٔ دوم را با معیارِ دیگری مرتب می‌کند).
         */
        fun processUsers(
            users: List<PanelUser>,
            query: String,
            filter: UserFilter,
            sort: UserSort,
            nearLimitPercent: Int,
            debtorByUsername: Map<String, DebtorInfo>,
            serverMode: Boolean
        ): List<PanelUser> {
            if (serverMode) return users
            val q = query.trim()
            var list = if (q.isEmpty()) users else users.filter {
                it.username.contains(q, ignoreCase = true) ||
                    (it.note ?: "").contains(q, ignoreCase = true)
            }
            list = when (filter) {
                UserFilter.ONLINE -> list.filter { it.isOnline }
                UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0L) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= nearLimitPercent / 100.0 }
                UserFilter.DEBTOR -> list.filter { debtorByUsername.containsKey(it.username) }
                else -> filter.panelStatus?.let { st -> list.filter { it.status == st } } ?: list
            }
            return when (sort) {
                UserSort.NAME -> list.sortedBy { it.username.lowercase() }
                UserSort.USAGE -> list.sortedByDescending { it.usedTraffic }
                UserSort.EXPIRY -> list.sortedBy { it.expire ?: "9999" }
                UserSort.CREATED -> list.sortedByDescending { it.id }
                UserSort.LAST_ONLINE -> list.sortedByDescending { com.mrm.pgmanager.utils.DateLogic.parseOnlineAtMillis(it.onlineAt) ?: 0L }
            }
        }
    }
}
