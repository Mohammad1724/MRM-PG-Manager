package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.*
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.lastSeenText
import com.mrm.pgmanager.utils.lastSeenShort
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.NotificationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsMotion
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.DsTileRadius

/* ──────────────────────────────────────────────────────────────────────────
 *  صفحهٔ کاربران — منطقِ صفحه
 *
 *  بارگذاری، صفحه‌بندی، جست‌وجو، انتخابِ گروهی و دیالوگ‌ها. اجزای ظاهری در دو
 *  فایلِ کنارِ همین فایل‌اند:
 *    · UsersListItems.kt      → کارت/ردیف‌های فهرست
 *    · UsersScreenControls.kt → سربرگ، آمار، جست‌وجو، فیلترها
 * ────────────────────────────────────────────────────────────────────────── */


/** یک عملیاتِ گروهیِ در انتظارِ تأییدِ کاربر. */
private data class PendingBulk(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit, val danger: Boolean = false)

@Composable
fun DebtorEditDialog(
    user: PanelUser,
    existing: DebtorInfo?,
    currency: String = stringResource(R.string.us_currency),
    onDismiss: () -> Unit,
    onSave: (amount: Long, notes: String) -> Unit,
    onClear: () -> Unit
) {
    val theme = LocalThemeState.current
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val amountLong = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (existing != null) stringResource(R.string.us_debt_edit_title, user.username) else stringResource(R.string.us_debt_add_title, user.username), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                if (existing != null) {
                    Text(stringResource(R.string.us_debt_marked_at, java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(existing.markedAt))), fontSize = 10.sp, color = theme.mutedColor)
                }
                Box(Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(currency, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        androidx.compose.foundation.text.BasicTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            textStyle = TextStyle(color = theme.inkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (amountText.isEmpty()) Text(stringResource(R.string.us_debt_amount_hint), color = theme.mutedColor.copy(0.6f), fontSize = 12.sp)
                                inner()
                            }
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = notes,
                        onValueChange = { notes = it.take(200) },
                        singleLine = false,
                        textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (notes.isEmpty()) Text(stringResource(R.string.us_debt_note_hint), color = theme.mutedColor.copy(0.6f), fontSize = 11.sp)
                            inner()
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(R.string.us_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    if (existing != null) {
                        PrimaryButton(stringResource(R.string.us_debt_settle), onClick = { onClear() }, modifier = Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
                PrimaryButton(
                    text = if (existing != null) stringResource(R.string.us_debt_save) else stringResource(R.string.us_debt_mark),
                    enabled = amountLong > 0L,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSave(amountLong, notes) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UsersScreen(
    session: Session,
    onLogout: () -> Unit,
    themeState: ThemeState,
    monitoringSettings: com.mrm.pgmanager.data.model.MonitoringSettings = com.mrm.pgmanager.data.model.MonitoringSettings(),
    deepLinkUsername: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    /** درخواستِ بازکردنِ دیالوگ «ساخت گروهی» از بیرون (صفحهٔ تنظیمات). */
    openBulkCreate: Boolean = false,
    onBulkCreateHandled: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val defaultCurrency = stringResource(R.string.us_currency)
    val store = remember { SessionStore(context) }
    // فهرست از حافظهٔ برنامه شروع می‌شود: برگشتن به تبِ کاربران دیگر یعنی
    // «همان فهرست، فوراً»، نه یک صفحهٔ اسکلتی و بعد یک پرش.
    val usersKey = PanelCache.usersKey(session.baseUrl)
    var users by remember(session) {
        mutableStateOf(PanelCache.get<List<PanelUser>>(usersKey) ?: emptyList())
    }
    var query by remember { mutableStateOf("") }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<PanelUser>>(usersKey) == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineAt by remember { mutableStateOf<Long?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var qrUser by remember { mutableStateOf<PanelUser?>(null) }
    var onlineCount by remember(session) {
        mutableStateOf(PanelCache.get<List<PanelUser>>(usersKey)?.count { it.isOnline } ?: 0)
    }
    var lastUserStates by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    // فیلترِ گروه — پنل خودش با پارامترِ `group` اعمالش می‌کند.
    var groupFilterId by remember { mutableStateOf<Int?>(null) }
    var groupOptions by remember(session) { mutableStateOf<List<com.mrm.pgmanager.data.model.Group>>(emptyList()) }
    // صفحه‌بندیِ سمتِ سرور
    var totalMatches by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    // شمارنده‌های سربرگ دیگر از روی فهرستِ دانلودشده حساب نمی‌شوند (چون حالا فقط
    // یک صفحه دانلود می‌شود)، بلکه از خودِ پنل می‌آیند.
    var counts by remember(session) { mutableStateOf<com.mrm.pgmanager.data.model.SystemStats?>(null) }
    // انتخابگرِ گروه برای عملیاتِ گروهی
    var bulkGroupPicker by remember { mutableStateOf(false) }
    var bulkGroupAdd by remember { mutableStateOf(true) }
    var currentSort by remember { mutableStateOf(UserSort.CREATED) }
    var viewMode by remember { mutableStateOf(store.readViewMode()) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var bulkCreateOpen by remember { mutableStateOf(false) }
    var exportChooserOpen by remember { mutableStateOf(false) }
    var exportPending by remember { mutableStateOf<Pair<String, List<PanelUser>>?>(null) }
    var selectedUserIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkTemplateDialog by remember { mutableStateOf(false) }
    var pendingBulk by remember { mutableStateOf<PendingBulk?>(null) }
    var quickActionUser by remember { mutableStateOf<PanelUser?>(null) }
    var quickTemplateUser by remember { mutableStateOf<PanelUser?>(null) }
    var quickTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var quickTemplatesLoading by remember { mutableStateOf(true) }
    var quickTemplatesFailed by remember { mutableStateOf(false) }

    var debtors by remember { mutableStateOf<Map<String, DebtorInfo>>(store.readDebtors()) }
    var debtorDialogUser by remember { mutableStateOf<PanelUser?>(null) }
    var invoiceDialogUser by remember { mutableStateOf<PanelUser?>(null) }
    var resetExpiryTarget by remember { mutableStateOf<PanelUser?>(null) }

    fun reloadDebtors() { debtors = store.readDebtors() }
    fun fetchSub(user: PanelUser, onResult: (PanelUser) -> Unit) {
        scope.launch {
            runCatching { PanelApi.user(session, user.username) }.onSuccess(onResult)
                .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.ud_sub_failed), android.widget.Toast.LENGTH_SHORT).show() }
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
    val debtorsForCurrentPanel = remember(debtors, session.baseUrl) { debtors.values.filter { it.baseUrl == session.baseUrl } }
    val debtorByUsername = remember(debtorsForCurrentPanel) { debtorsForCurrentPanel.associateBy { it.username } }
    val debtorCount = debtorsForCurrentPanel.size

    val density = androidx.compose.ui.platform.LocalDensity.current
    val statsCardsHeightPx = remember { mutableStateOf(0f) }
    val totalHeaderHeightPx = remember { mutableStateOf(0f) }

    val fallbackStatsPx = remember(density) { with(density) { 114.dp.toPx() } }
    val headerHeight = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else fallbackStatsPx
    val fallbackTotalDp = 200.dp
    val totalHeaderDp = if (totalHeaderHeightPx.value > 0f) with(density) { totalHeaderHeightPx.value.toDp() } else fallbackTotalDp
    val scrollOffset = remember { mutableStateOf(0f) }
    // با اسکرول به پایین دکمهٔ «کاربر جدید» پنهان و با اسکرول به بالا دوباره ظاهر می‌شود
    val fabVisible = remember { mutableStateOf(true) }

    /** آیا فیلترِ فعلی را پنل می‌تواند اعمال کند؟ (بدهکار و نزدیک‌به‌سقف محلی‌اند) */
    val serverMode = currentFilter.serverSide

    fun buildQuery(offset: Int) = com.mrm.pgmanager.data.model.UserQuery(
        search = query.trim().takeIf { it.isNotBlank() },
        status = currentFilter.panelStatus,
        groupId = groupFilterId,
        sort = currentSort.panelSort,
        offset = offset,
        limit = 60
    )

    /**
     * بارگذاریِ صفحه‌ایِ سمتِ سرور — حالتِ عادی.
     * فقط همان چند ده کاربری که دیده می‌شوند از شبکه می‌آیند.
     */
    fun loadPage(silent: Boolean = false, resetHeader: Boolean = true) {
        scope.launch {
            if (!silent) loading = true
            error = null
            endReached = false
            runCatching { PanelApi.usersPage(session, buildQuery(0)) }.onSuccess { page ->
                users = page.users
                totalMatches = page.total
                endReached = page.users.isEmpty() || page.users.size >= page.total
                offlineAt = null
                if (resetHeader) scrollOffset.value = 0f
            }.onFailure {
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, context.getString(R.string.us_session_expired), android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                } else if (!silent) error = it.message
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.saveUsersCache(list) }
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
                        fun notify(id: Int, title: String, text: String) = NotificationHelper.post(context, id, NotificationHelper.CHANNEL_EVENTS, title, text)
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
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, context.getString(R.string.us_session_expired), android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                } else {
                    val cache = if (monitoringSettings.offlineCacheEnabled) store.readUsersCache() else null
                    if (cache != null) {
                        users = cache.first
                        onlineCount = 0
                        offlineAt = cache.second
                        error = null
                    } else if (!silent) {
                        error = it.message
                    }
                }
            }
            loading = false
        }
    }

    /** مسیرِ درست را خودش انتخاب می‌کند. */
    fun load(resetHeader: Boolean = true, silent: Boolean = false) {
        if (serverMode) loadPage(silent = silent, resetHeader = resetHeader)
        else loadAll(resetHeader = resetHeader, silent = silent)
    }

    fun runAction(notification: Pair<String, String>? = null, action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }.onFailure {
                error = it.message
                if (it.message?.contains("401") == true) {
                    android.widget.Toast.makeText(context, context.getString(R.string.us_session_expired), android.widget.Toast.LENGTH_LONG).show()
                    onLogout()
                } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.us_error_fmt, it.message?.take(120).orEmpty()), android.widget.Toast.LENGTH_LONG).show()
                }
            }.onSuccess { notification?.let { (title, message) -> val settings = store.readMonitoringSettings(); if (settings.notificationsEnabled && settings.notifyUserActions) NotificationHelper.post(context, (title + message).hashCode(), NotificationHelper.CHANNEL_EVENTS, title, message) }; load() }
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
                android.widget.Toast.makeText(context, context.getString(if (ok) R.string.us_file_saved else R.string.us_file_save_failed), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { writeExport(it) }
    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { writeExport(it) }
    fun beginExport(format: String) {
        val chosen = users.filter { selectedUserIds.contains(it.id) }
        if (chosen.isEmpty()) { android.widget.Toast.makeText(context, context.getString(R.string.us_select_first), android.widget.Toast.LENGTH_SHORT).show(); return }
        exportPending = format to chosen
        if (format == "json") exportJsonLauncher.launch(exportFileName("json")) else exportCsvLauncher.launch(exportFileName("csv"))
    }
    var firstLoad by remember(session) { mutableStateOf(true) }
    LaunchedEffect(session, query, currentFilter, currentSort, groupFilterId) {
        if (firstLoad) {
            firstLoad = false
            // فقط وقتی داده کهنه است سراغِ پنل می‌رویم؛ وگرنه سوایپ بینِ تب‌ها هر
            // بار یک درخواست می‌شد و همان‌جا انیمیشن می‌پرید.
            if (!PanelCache.isFresh(usersKey)) load(silent = users.isNotEmpty())
            return@LaunchedEffect
        }
        // دیبانس: با هر حرفی که تایپ می‌شود درخواست نفرست.
        kotlinx.coroutines.delay(350)
        load(resetHeader = false, silent = users.isNotEmpty())
    }
    // فهرستِ گروه‌ها برای فیلتر — یک‌بار و سبک.
    LaunchedEffect(session) {
        runCatching { PanelApi.groups(session) }.onSuccess { groupOptions = it }
    }
    LaunchedEffect(deepLinkUsername, users) {
        val name = deepLinkUsername ?: return@LaunchedEffect
        if (users.isEmpty()) return@LaunchedEffect
        users.find { it.username == name }?.let {
            query = ""
            currentFilter = UserFilter.ALL
            selectedUser = it
        }
        onDeepLinkHandled()
    }
    // درخواستِ ساخت گروهی از صفحهٔ تنظیمات: دیالوگ را همین‌جا باز می‌کنیم تا
    // پس از پایان، لیست کاربران رفرش شود.
    LaunchedEffect(openBulkCreate) {
        if (openBulkCreate) {
            bulkCreateOpen = true
            onBulkCreateHandled()
        }
    }
    var inForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            inForeground = event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(session, monitoringSettings.autoRefreshEnabled, monitoringSettings.refreshWhileAppOpen, monitoringSettings.refreshIntervalSeconds) {
        if (monitoringSettings.autoRefreshEnabled && monitoringSettings.refreshWhileAppOpen) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (inForeground) load(resetHeader = false, silent = true)
                kotlinx.coroutines.delay(monitoringSettings.refreshIntervalSeconds.coerceIn(5, 3600) * 1_000L)
            }
        }
    }

    // در حالتِ سمتِ سرور، پنل قبلاً فیلتر و مرتب کرده؛ دوباره‌کاری در گوشی فقط
    // نتیجه را خراب می‌کند (مثلاً صفحهٔ دوم را با معیارِ دیگری مرتب می‌کند).
    val processedUsers = remember(users, query, currentFilter, currentSort, monitoringSettings.nearLimitPercent, debtorByUsername, serverMode) {
        if (serverMode) return@remember users
        val q = query.trim()
        var list = if (q.isEmpty()) users else users.filter {
            it.username.contains(q, ignoreCase = true) ||
            (it.note ?: "").contains(q, ignoreCase = true)
        }
        list = when (currentFilter) {
            UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0L) it.usedTraffic.toDouble() / it.dataLimit else 0.0; p >= monitoringSettings.nearLimitPercent / 100.0 }
            UserFilter.DEBTOR -> list.filter { debtorByUsername.containsKey(it.username) }
            else -> currentFilter.panelStatus?.let { st -> list.filter { it.status == st } } ?: list
        }
        when (currentSort) {
            UserSort.NAME -> list.sortedBy { it.username.lowercase() }
            UserSort.USAGE -> list.sortedByDescending { it.usedTraffic }
            UserSort.EXPIRY -> list.sortedBy { it.expire ?: "9999" }
            UserSort.CREATED -> list.sortedByDescending { it.id }
        }
    }

    val nestedScrollConnection = remember(headerHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // جهتِ اسکرول را بگیر و دکمهٔ شناور را پنهان/آشکار کن
                // (مستقل از هدر، چون هدر بعد از چند پیکسل جمع می‌شود و دیگر رویداد نمی‌دهد)
                if (available.y < -2f) fabVisible.value = false
                else if (available.y > 2f) fabVisible.value = true

                if (headerHeight <= 0f) return Offset.Zero

                val delta = -available.y
                val current = scrollOffset.value
                if (delta > 0f && current < headerHeight) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                else if (delta < 0f && current > 0f) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return Offset.Zero
            }
        }
    }

    Scaffold(containerColor = Color.Transparent, floatingActionButton = {
        if (selectedUserIds.isEmpty()) {
            val fabShape = DsRadius.Lg
            val fabInteraction = remember { MutableInteractionSource() }
            val isFabPressed by fabInteraction.collectIsPressedAsState()
            val fabScale by animateFloatAsState(targetValue = if (isFabPressed) 0.95f else 1f, animationSpec = DsMotion.ScaleSpring, label = "fabScale")
            // هنگام اسکرول به پایین، دکمه به‌آرامی کوچک و محو می‌شود تا جلوی ردیف‌ها را نگیرد
            val fabShown by animateFloatAsState(
                targetValue = if (fabVisible.value) 1f else 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "fabShown"
            )
            Box(
                modifier = Modifier
                    .padding(bottom = 74.dp, end = 4.dp)
                    .size(44.dp)
                    .graphicsLayer(
                        scaleX = fabScale * fabShown,
                        scaleY = fabScale * fabShown,
                        alpha = fabShown
                    )
                    .clip(fabShape)
                    .background(themeState.accentPrimary)
                    .border(BorderStroke(DsBorder.Hairline, themeState.accentPrimary), fabShape)
                    // وقتی دکمه محو است نباید لمس را بگیرد
                    .clickable(enabled = fabVisible.value, interactionSource = fabInteraction, indication = null) { createMenuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                RoundedAppIcon(AppIcon.UserAdd, tint = DsAccent.OnAccent, size = 20.dp)
            }
        }
    }) { padding ->
        val topInsets = padding.calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            val scrollOffsetDp = with(density) { scrollOffset.value.toDp() }
            val listTopPad = (totalHeaderDp - scrollOffsetDp).coerceAtLeast(0.dp) + topInsets + 4.dp
            val ptrState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { load() },
                modifier = Modifier.fillMaxSize(),
                state = ptrState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        isRefreshing = loading,
                        state = ptrState,
                        containerColor = themeState.cardSurfaceColor,
                        color = themeState.accentPrimary,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = listTopPad)
                    )
                }
            ) {
                when {
                    loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(6) { SkeletonCard() } }
                    error != null -> Box(Modifier.fillMaxWidth().padding(top = listTopPad).clip(DsRadius.Lg).background(themeState.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.18f)), DsRadius.Lg).padding(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.us_error), fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                            Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                            SecondaryButton(stringResource(R.string.us_retry), onClick = { load() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    processedUsers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = listTopPad).clip(DsRadius.Lg).background(themeState.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Lg).padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(56.dp).clip(DsRadius.Md).background(themeState.searchBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Md), contentAlignment = Alignment.Center) {
                                com.mrm.pgmanager.ui.components.RoundedAppIcon(com.mrm.pgmanager.ui.components.AppIcon.Search, tint = themeState.mutedColor, size = 28.dp)
                            }
                            Text(stringResource(R.string.no_user_found), fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                            Text(if (query.isNotBlank() || currentFilter != com.mrm.pgmanager.data.model.UserFilter.ALL) stringResource(R.string.clear_filter_or_create) else stringResource(R.string.create_first_user), fontSize = 11.sp, color = themeState.mutedColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (query.isNotBlank() || currentFilter != com.mrm.pgmanager.data.model.UserFilter.ALL) {
                                    com.mrm.pgmanager.ui.components.SecondaryButton(stringResource(R.string.clear_filter), onClick = { query = ""; currentFilter = com.mrm.pgmanager.data.model.UserFilter.ALL }, modifier = Modifier.height(36.dp))
                                }
                                com.mrm.pgmanager.ui.components.PrimaryButton(stringResource(R.string.create_user), onClick = { createUser = true })
                            }
                        }
                    }
                    else -> androidx.compose.animation.AnimatedContent(targetState = viewMode, label = "viewModeSwitch") { mode ->
                        when (mode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            itemsIndexed(processedUsers, key = { _, u -> u.id }) { index, user ->
                                if (index >= processedUsers.lastIndex - 4) {
                                    LaunchedEffect(index, processedUsers.size) { loadMore() }
                                }
                                Box(Modifier.animateItem()) { LuxuryGridCard(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrWithFetch(it) }, onCopySub = { copySubWithFetch(it) }, onLongClick = { quickActionUser = user }, debtorInfo = debtorByUsername[user.username]) }
                            }
                        }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            itemsIndexed(processedUsers, key = { _, u -> u.id }) { index, user ->
                                if (index >= processedUsers.lastIndex - 4) {
                                    LaunchedEffect(index, processedUsers.size) { loadMore() }
                                }
                                Box(Modifier.animateItem()) { LuxuryCompactRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrWithFetch(it) }, onCopySub = { copySubWithFetch(it) }, onLongClick = { quickActionUser = user }, debtorInfo = debtorByUsername[user.username]) }
                            }
                        }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            itemsIndexed(processedUsers, key = { _, u -> u.id }) { index, user ->
                                if (index >= processedUsers.lastIndex - 4) {
                                    LaunchedEffect(index, processedUsers.size) { loadMore() }
                                }
                                Box(Modifier.animateItem()) { LuxuryMicroRow(user, selected = selectedUserIds.contains(user.id), onSelectToggle = { selectedUserIds = if (selectedUserIds.contains(user.id)) selectedUserIds - user.id else selectedUserIds + user.id }, onClick = { selectedUser = user }, onQrClick = { qrWithFetch(it) }, onCopySub = { copySubWithFetch(it) }, onLongClick = { quickActionUser = user }, debtorInfo = debtorByUsername[user.username]) }
                            }
                        }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        if (scrollOffset.value == 0f && coords.size.height > 0) {
                            val h = (coords.size.height.toFloat() - with(density) { topInsets.toPx() }).coerceAtLeast(0f)
                            if (totalHeaderHeightPx.value != h) {
                                totalHeaderHeightPx.value = h
                            }
                        }
                    }
                    .background(themeState.chromeBgColor)
                    .border(BorderStroke(DsBorder.Hairline, themeState.borderColor))
                    .padding(top = topInsets)
                    // دکمهٔ همبرگری حذف شده؛ فقط یک فاصلهٔ نفس‌کشیدن زیرِ نوارِ وضعیت.
                    .padding(top = 6.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                TopBarHeader(onRefresh = { load() }, loading = loading, onOpenSettings = onOpenSettings)

                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (scrollOffset.value == 0f && coords.size.height > 0) {
                                if (statsCardsHeightPx.value != coords.size.height.toFloat()) {
                                    statsCardsHeightPx.value = coords.size.height.toFloat()
                                }
                            }
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else placeable.height.toFloat()
                            val progress = if (maxH > 0f) (scrollOffset.value / maxH).coerceIn(0f, 1f) else 0f
                            val currentH = (placeable.height * (1f - progress)).roundToInt().coerceAtLeast(0)
                            layout(placeable.width, currentH) {
                                placeable.placeRelative(0, (-progress * placeable.height * 0.38f).roundToInt())
                            }
                        }
                        .graphicsLayer {
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else 1f
                            val progress = (scrollOffset.value / maxH).coerceIn(0f, 1f)
                            this.alpha = (1f - progress * 1.3f).coerceIn(0f, 1f)
                        }
                        // فاصله از سربرگ. عمداً *داخلِ* زنجیرهٔ جمع‌شونده است (بعد از
                        // layout و graphicsLayer)، تا با اسکرول همراهِ خودِ کارت‌ها جمع
                        // شود؛ اگر بیرون بود، بعد از جمع‌شدنِ کارت‌ها یک نوارِ خالی
                        // زیرِ سربرگ باقی می‌ماند.
                        .padding(top = 10.dp)
                ) {
                            StatsCardsRow(
                            // از خودِ پنل، نه از روی صفحهٔ دانلودشده — وگرنه با
                            // صفحه‌بندی، «۷۳ کاربر» می‌شد «۶۰ کاربر».
                            totalUsers = counts?.totalUsers ?: users.size,
                            activeUsers = counts?.activeUsers ?: users.count { it.status == "active" },
                            onlineUsers = counts?.onlineUsers ?: onlineCount,
                            debtorCount = debtorCount
                        )
                }

                Spacer(Modifier.height(6.dp))
                GlassSearchBar(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(8.dp))
                FilterAndControlBar(
                    currentFilter = currentFilter,
                    onFilterChange = { currentFilter = it },
                    currentSort = currentSort,
                    onSortChange = { currentSort = it },
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it; store.saveViewMode(it) },
                    debtorCount = debtorCount,
                    groups = groupOptions,
                    groupFilterId = groupFilterId,
                    onGroupFilterChange = { groupFilterId = it }
                )
                offlineAt?.let { cachedAt ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(DsRadius.Sm).background(GlassAmber.copy(.12f)).border(BorderStroke(DsBorder.Hairline, GlassAmber.copy(.30f)), DsRadius.Sm).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassAmber, size = 14.dp)
                        Text(stringResource(R.string.offline_data, java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassAmber, maxLines = 1)
                    }
                }
            }

            if (selectedUserIds.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        // ۴۶dp کمتر از قبل، چون فضای رزروشدهٔ همبرگری آزاد شد.
                        .padding(top = 64.dp)
                ) {
                    BulkActionsBar(
                        selectedCount = selectedUserIds.size,
                        onClear = { selectedUserIds = emptySet() },
                        onSelectAll = { selectedUserIds = processedUsers.map { it.id }.toSet() },
                        onExport = { exportChooserOpen = true },
                        onDelete = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_delete_title, ids.size), message = context.getString(R.string.us_bulk_delete_msg), confirmLabel = context.getString(R.string.us_delete), danger = true, action = { runAction(notification = context.getString(R.string.us_n_bulk_delete) to context.getString(R.string.us_n_bulk_delete_body, ids.size)) { PanelApi.bulkDeleteUsers(session, ids) } }) },
                        onResetUsage = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_reset_title, ids.size), message = context.getString(R.string.us_bulk_reset_msg), confirmLabel = context.getString(R.string.us_confirm), action = { runAction(notification = context.getString(R.string.us_n_bulk_reset) to context.getString(R.string.us_n_bulk_reset_body, ids.size)) { PanelApi.bulkResetUsersUsage(session, ids) } }) },
                        onDisable = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_disable_title, ids.size), message = context.getString(R.string.us_bulk_disable_msg), confirmLabel = context.getString(R.string.us_confirm), action = { runAction(notification = context.getString(R.string.us_n_bulk_disable) to context.getString(R.string.us_n_bulk_disable_body, ids.size)) { PanelApi.bulkDisableUsers(session, ids) } }) },
                        onEnable = { val ids = selectedUserIds.toSet(); selectedUserIds = emptySet(); pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_enable_title, ids.size), message = context.getString(R.string.us_bulk_enable_msg), confirmLabel = context.getString(R.string.us_confirm), action = { runAction(notification = context.getString(R.string.us_n_bulk_enable) to context.getString(R.string.us_n_bulk_enable_body, ids.size)) { PanelApi.bulkEnableUsers(session, ids) } }) },
                        onApplyTemplate = {
                            showBulkTemplateDialog = true
                        },
                        onGroupAdd = { bulkGroupAdd = true; bulkGroupPicker = true },
                        onGroupRemove = { bulkGroupAdd = false; bulkGroupPicker = true }
                    )
                }
            }
        }
    }

    quickTemplateUser?.let { u ->
        LaunchedEffect(u) {
            quickTemplatesLoading = true; quickTemplatesFailed = false
            var list: List<UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            list?.let { quickTemplates = it } ?: run { quickTemplatesFailed = true }
            quickTemplatesLoading = false
        }
        com.mrm.pgmanager.ui.dialogs.BulkApplyTemplateDialog(
            templates = quickTemplates,
            selectedCount = 1,
            onDismiss = { quickTemplateUser = null },
            onApply = { templateId, note ->
                val id = u.id
                quickTemplateUser = null
                runAction { PanelApi.bulkApplyTemplate(session, setOf(id), templateId, note) }
            },
            isLoading = quickTemplatesLoading,
            loadFailed = quickTemplatesFailed
        )
    }

    if (bulkGroupPicker) {
        val ids = selectedUserIds.toSet()
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { bulkGroupPicker = false }) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(if (bulkGroupAdd) R.string.us_bulk_group_add else R.string.us_bulk_group_remove),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.us_bulk_group_title, ids.size) + " · " + stringResource(R.string.us_bulk_group_pick),
                    fontSize = 11.sp, color = theme.mutedColor
                )
                if (groupOptions.isEmpty()) {
                    Text(stringResource(R.string.ue_no_groups), fontSize = 11.sp, color = theme.mutedColor)
                }
                groupOptions.forEach { g ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(DsRadius.Sm)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                            .pressScale(0.98f)
                            .clickable {
                                bulkGroupPicker = false
                                selectedUserIds = emptySet()
                                val add = bulkGroupAdd
                                runAction(
                                    notification = null
                                ) { PanelApi.bulkGroupMembership(session, setOf(g.id), ids, add) }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(
                                        if (add) R.string.us_bulk_group_added else R.string.us_bulk_group_removed,
                                        ids.size
                                    ),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundedAppIcon(AppIcon.Folder, tint = theme.accentPrimary, size = 14.dp)
                            Text(g.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = theme.inkColor)
                        }
                    }
                }
                SecondaryButton(stringResource(R.string.us_cancel), onClick = { bulkGroupPicker = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showBulkTemplateDialog) {
        var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
        var templatesLoading by remember { mutableStateOf(true) }
        var templatesFailed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            var list: List<UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            list?.let { templates = it } ?: run { templatesFailed = true }
            templatesLoading = false
        }
        com.mrm.pgmanager.ui.dialogs.BulkApplyTemplateDialog(
            templates = templates,
            selectedCount = selectedUserIds.size,
            onDismiss = { showBulkTemplateDialog = false },
            onApply = { templateId, note ->
                val ids = selectedUserIds.toSet()
                selectedUserIds = emptySet()
                showBulkTemplateDialog = false
                runAction { PanelApi.bulkApplyTemplate(session, ids, templateId, note) }
            },
            isLoading = templatesLoading,
            loadFailed = templatesFailed
        )
    }

    pendingBulk?.let { p ->
        ConfirmActionDialog(
            title = p.title,
            message = p.message,
            confirmLabel = p.confirmLabel,
            danger = p.danger,
            onDismiss = { pendingBulk = null },
            onConfirm = { p.action(); pendingBulk = null }
        )
    }

    quickActionUser?.let { u ->
        val isDebtor = debtorByUsername.containsKey(u.username)
        QuickActionSheet(
            user = u,
            onDismiss = { quickActionUser = null },
            onUseTemplate = { quickTemplateUser = u },
            onToggle = { runAction(notification = context.getString(R.string.us_n_status) to context.getString(R.string.us_n_status_body, u.username)) { PanelApi.setDisabled(session, u.username, u.status != "disabled") } },
            onCopySub = { copySubWithFetch(u) },
            onQr = { qrUser = u },
            onEdit = { selectedUser = u },
            onResetUsage = { runAction(notification = context.getString(R.string.us_n_reset_usage) to context.getString(R.string.us_n_reset_usage_body, u.username)) { PanelApi.resetUsage(session, u.username) } },
            onResetExpiry = { resetExpiryTarget = u },
            onDelete = { deleteUser = u },
            onDebtor = { debtorDialogUser = u },
            isDebtor = isDebtor,
            onInvoice = { invoiceDialogUser = u }
        )
    }

    selectedUser?.let { user ->
        val dInfo = debtorByUsername[user.username]
        UserDetailsDialog(
            user = user,
            onDismiss = { selectedUser = null },
            onSave = { limitGb, expireShamsi ->
                selectedUser = null; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
            },
            onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } },
            onDelete = { deleteUser = user; selectedUser = null },
            onResetUsage = {
                selectedUser = null; runAction(notification = context.getString(R.string.us_n_reset_usage) to context.getString(R.string.us_n_reset_usage_body, user.username)) { PanelApi.resetUsage(session, user.username) }
            },
            onResetExpiry = { days ->
                selectedUser = null; runAction(notification = context.getString(R.string.us_n_reset_time) to context.getString(R.string.us_n_reset_time_body, user.username, days)) {
                    val newExpire = LocalDate.now().plusDays(days.toLong()).toString()
                    PanelApi.modifyUser(session, user.username, user.dataLimit.toDouble() / 1073741824.0, newExpire, user.note ?: "", user.hwidLimit, user.groupIds)
                }
            },
            onApplyTemplate = { templateId, note ->
                selectedUser = null; runAction { PanelApi.bulkApplyTemplate(session, setOf(user.id), templateId, note) }
            },
            session = session,
            debtorInfo = dInfo,
            onMarkDebtor = { selectedUser = null; debtorDialogUser = user },
            onClearDebt = {
                val wasAutoDisabled = dInfo?.autoDisabled ?: false
                store.removeDebtor(session.baseUrl, user.username)
                reloadDebtors()
                selectedUser = null
                android.widget.Toast.makeText(context, context.getString(R.string.us_debt_cleared), android.widget.Toast.LENGTH_SHORT).show()
                if (wasAutoDisabled) {
                    scope.launch {
                        runCatching { PanelApi.setDisabled(session, user.username, false) }.onSuccess { load() }
                    }
                }
            },
            onInvoice = {
                invoiceDialogUser = user
                selectedUser = null
            }
        )
    }
    if (createMenuOpen) {
        Dialog(onDismissRequest = { createMenuOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(themeState.dialogBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.us_create_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                SettingsActionRow(stringResource(R.string.us_create_single), stringResource(R.string.us_create_single_desc), AppIcon.UserAdd, themeState.accentPrimary) { createMenuOpen = false; createUser = true }
                SettingsActionRow(stringResource(R.string.us_create_bulk), stringResource(R.string.us_create_bulk_desc), AppIcon.Users, GlassGreen) { createMenuOpen = false; bulkCreateOpen = true }
                SecondaryButton(stringResource(R.string.us_cancel), onClick = { createMenuOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (bulkCreateOpen) {
        BulkCreateUsersDialog(session = session, onDismiss = { bulkCreateOpen = false }, onFinished = { n -> bulkCreateOpen = false; if (n > 0) load(resetHeader = false, silent = true) })
    }
    if (exportChooserOpen) {
        Dialog(onDismissRequest = { exportChooserOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(themeState.dialogBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Xxl).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.us_export_title, selectedUserIds.size), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text(stringResource(R.string.us_export_desc), fontSize = 10.sp, color = themeState.mutedColor)
                SettingsActionRow(stringResource(R.string.us_export_csv), stringResource(R.string.us_export_csv_desc), AppIcon.Download, GlassGreen) { exportChooserOpen = false; beginExport("csv") }
                SettingsActionRow(stringResource(R.string.us_export_json), stringResource(R.string.us_export_json_desc), AppIcon.Download, themeState.accentPrimary) { exportChooserOpen = false; beginExport("json") }
                SecondaryButton(stringResource(R.string.us_cancel), onClick = { exportChooserOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (createUser) UserEditorDialog(initial = null, onDismiss = { createUser = false }, onSave = { limitGb, expireShamsi ->
        createUser = false; runAction(notification = context.getString(R.string.us_n_created) to context.getString(R.string.us_n_created_body, limitGb.username)) { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.createUser(session, limitGb.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds) }
    }, onToggle = null, onSaveWithTemplate = { username, templateId, note ->
        createUser = false; runAction(notification = context.getString(R.string.us_n_created) to context.getString(R.string.us_n_created_tpl_body, username)) { PanelApi.createUserFromTemplate(session, username, templateId, note) }
    }, session = session)
    deleteUser?.let { user ->
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(DsRadius.Lg).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.us_delete_user_title, user.username), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text(stringResource(R.string.us_delete_user_msg), color = theme.mutedColor, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton(stringResource(R.string.us_cancel), onClick = { deleteUser = null }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        DangerButton(stringResource(R.string.us_delete), onClick = { deleteUser = null; runAction(notification = context.getString(R.string.us_n_deleted) to context.getString(R.string.us_n_deleted_body, user.username)) { PanelApi.deleteUser(session, user.username) } }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    qrUser?.let { user ->
        SubscriptionQrDialog(user = user, onDismiss = { qrUser = null })
    }
    invoiceDialogUser?.let { u ->
        InvoiceDialog(
            user = u,
            debtorInfo = debtorByUsername[u.username],
            currency = monitoringSettings.debtorCurrency.ifBlank { defaultCurrency },
            onDismiss = { invoiceDialogUser = null }
        )
    }
    debtorDialogUser?.let { u ->
        val existing = debtorByUsername[u.username]
        DebtorEditDialog(
            user = u,
            existing = existing,
            currency = monitoringSettings.debtorCurrency.ifBlank { defaultCurrency },
            onDismiss = { debtorDialogUser = null },
            onSave = { amount, notes ->
                val info = DebtorInfo(
                    username = u.username,
                    baseUrl = session.baseUrl,
                    amount = amount,
                    currency = monitoringSettings.debtorCurrency.ifBlank { defaultCurrency },
                    markedAt = existing?.markedAt ?: System.currentTimeMillis(),
                    notes = notes,
                    autoDisabled = existing?.autoDisabled ?: false,
                    userId = u.id
                )
                store.setDebtor(info)
                reloadDebtors()
                debtorDialogUser = null
                android.widget.Toast.makeText(context, context.getString(if (existing == null) R.string.us_debt_added else R.string.us_debt_updated), android.widget.Toast.LENGTH_SHORT).show()
                if (monitoringSettings.debtorAutoDisableEnabled) {
                    val over = info.isOverdue(monitoringSettings.debtorAutoDisableAfterHours)
                    if (over && u.status != "disabled") {
                        scope.launch {
                            runCatching { PanelApi.setDisabled(session, u.username, true) }.onSuccess {
                                val updated = info.copy(autoDisabled = true)
                                store.setDebtor(updated)
                                reloadDebtors()
                                android.widget.Toast.makeText(context, context.getString(R.string.us_debt_auto_disabled), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onClear = {
                val wasAutoDisabled = debtorByUsername[u.username]?.autoDisabled ?: false
                store.removeDebtor(session.baseUrl, u.username)
                reloadDebtors()
                debtorDialogUser = null
                android.widget.Toast.makeText(context, context.getString(R.string.us_debt_cleared), android.widget.Toast.LENGTH_SHORT).show()
                if (wasAutoDisabled) {
                    scope.launch {
                        runCatching { PanelApi.setDisabled(session, u.username, false) }.onSuccess {
                            load()
                            android.widget.Toast.makeText(context, context.getString(R.string.us_user_enabled), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
    resetExpiryTarget?.let { u ->
        ResetExpiryDurationDialog(
            onDismiss = { resetExpiryTarget = null },
            onConfirm = { days ->
                val targetUser = u; resetExpiryTarget = null
                runAction(notification = context.getString(R.string.us_n_reset_time) to context.getString(R.string.us_n_reset_time_body, targetUser.username, days)) {
                    val newExpire = LocalDate.now().plusDays(days.toLong()).toString()
                    PanelApi.modifyUser(session, targetUser.username, targetUser.dataLimit.toDouble() / 1073741824.0, newExpire, targetUser.note ?: "", targetUser.hwidLimit, targetUser.groupIds)
                }
            }
        )
    }

    LaunchedEffect(users, monitoringSettings.debtorAutoDisableEnabled, monitoringSettings.debtorAutoDisableAfterHours) {
        if (!monitoringSettings.debtorAutoDisableEnabled) return@LaunchedEffect
        if (users.isEmpty()) return@LaunchedEffect
        debtorsForCurrentPanel.forEach { d ->
            if (!d.isOverdue(monitoringSettings.debtorAutoDisableAfterHours)) return@forEach
            if (d.autoDisabled) return@forEach
            val pu = users.find { it.username == d.username } ?: return@forEach
            if (pu.status == "disabled") {
                val updated = d.copy(autoDisabled = true)
                store.setDebtor(updated)
                reloadDebtors()
                return@forEach
            }
            runCatching { PanelApi.setDisabled(session, d.username, true) }.onSuccess {
                val updated = d.copy(autoDisabled = true)
                store.setDebtor(updated)
                reloadDebtors()
                if (monitoringSettings.notificationsEnabled && monitoringSettings.notifyDebtorOverdue) {
                    NotificationHelper.post(context, ("debtor_overdue_"+d.username).hashCode(), NotificationHelper.CHANNEL_EVENTS, context.getString(R.string.us_n_auto_disable), context.getString(R.string.us_n_auto_disable_body, d.username, monitoringSettings.debtorAutoDisableAfterHours, d.amount.toString(), d.currency))
                }
            }
        }
    }
}

