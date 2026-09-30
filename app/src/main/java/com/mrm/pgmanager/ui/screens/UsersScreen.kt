package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.DsTileRadius

/* ──────────────────────────────────────────────────────────────────────────
 *  صفحهٔ کاربران — فقطِ ترکیبِ UI (فاز ۱.۳: تفکیک بدونِ تغییرِ رفتار)
 *
 *  state و منطقِ load/فیلتر/اکشن‌ها بیرون کشیده شده‌اند؛ اجزای دیگر:
 *    · UsersUiState.kt        → state-holder + بارگذاری + ساختِ کوئری + خروجی فایل
 *    · UsersScreenDialogs.kt  → دیالوگ‌ها/ورقه‌ها (جزئیات، ساخت، حذف، فاکتور، …)
 *    · UsersListItems.kt      → کارت/ردیف‌های فهرست
 *    · UsersScreenControls.kt → سربرگ، آمار، جست‌وجو، فیلترها
 * ────────────────────────────────────────────────────────────────────────── */



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UsersScreen(
    session: Session,
    /** پنل ۴۰۱ داد: نشست منقضی شده — حساب حفظ می‌شود و به صفحهٔ ورود می‌رویم. */
    onSessionExpired: () -> Unit,
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
    // ── فاز ۱.۳: state + منطقِ load/فیلتر در UsersUiState؛ اینجا فقطِ ترکیبِ UI ──
    val ui = remember(session) { UsersUiState(scope, context, session, store, monitoringSettings, onSessionExpired) }
    // مقدارِ تازهٔ تنظیماتِ مانیتورینگ (که در composition عوض می‌شود) به state-holder می‌رسد.
    SideEffect { ui.monitoringSettings = monitoringSettings }
    /** آیا فیلترِ فعلی را پنل می‌تواند اعمال کند؟ (بدهکار و نزدیک‌به‌سقف محلی‌اند) */
    val serverMode = ui.serverMode

    val density = androidx.compose.ui.platform.LocalDensity.current
    val statsCardsHeightPx = remember { mutableStateOf(0f) }
    val totalHeaderHeightPx = remember { mutableStateOf(0f) }

    val fallbackStatsPx = remember(density) { with(density) { 114.dp.toPx() } }
    val headerHeight = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else fallbackStatsPx
    val fallbackTotalDp = 200.dp
    val totalHeaderDp = if (totalHeaderHeightPx.value > 0f) with(density) { totalHeaderHeightPx.value.toDp() } else fallbackTotalDp
    // با اسکرول به پایین دکمهٔ «کاربر جدید» پنهان و با اسکرول به بالا دوباره ظاهر می‌شود
    val fabVisible = remember { mutableStateOf(true) }

    val exportCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { ui.writeExport(it) }
    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { ui.writeExport(it) }
    fun beginExport(format: String) {
        val chosen = ui.users.filter { ui.selectedUserIds.contains(it.id) }
        if (chosen.isEmpty()) { com.mrm.pgmanager.ui.feedback.AppFeedback.info(context.getString(R.string.us_select_first)); return }
        ui.exportPending = format to chosen
        if (format == "json") exportJsonLauncher.launch(ui.exportFileName("json")) else exportCsvLauncher.launch(ui.exportFileName("csv"))
    }
    // فاز ۲.۵ — نشانهٔ یک‌بارهٔ long-press: نخستین باری که کارتی در فهرست هست، یک‌بار نمایش داده می‌شود.
    val longPressHint = stringResource(R.string.us_hint_long_press)
    LaunchedEffect(ui.users.isNotEmpty()) {
        if (ui.users.isNotEmpty() && !store.hintShown(SessionStore.HINT_LONG_PRESS)) {
            kotlinx.coroutines.delay(900)
            com.mrm.pgmanager.ui.feedback.AppFeedback.info(longPressHint)
            store.markHintShown(SessionStore.HINT_LONG_PRESS)
        }
    }
    LaunchedEffect(session, ui.query, ui.currentFilter, ui.currentSort, ui.groupFilterId, ui.ownerFilter) {
        if (ui.firstLoad) {
            ui.firstLoad = false
            // فقط وقتی داده کهنه است سراغِ پنل می‌رویم؛ وگرنه سوایپ بینِ تب‌ها هر
            // بار یک درخواست می‌شد و همان‌جا انیمیشن می‌پرید.
            if (!PanelCache.isFresh(ui.usersKey)) ui.load(silent = ui.users.isNotEmpty())
            return@LaunchedEffect
        }
        // دیبانس: با هر حرفی که تایپ می‌شود درخواست نفرست.
        kotlinx.coroutines.delay(350)
        ui.load(resetHeader = false, silent = ui.users.isNotEmpty())
    }
    // فهرستِ گروه‌ها برای فیلتر — یک‌بار و سبک.
    LaunchedEffect(session) {
        runCatching { PanelApi.groups(session) }.onSuccess { ui.groupOptions = it }
        // فهرستِ ادمین‌ها برای فیلترِ مالک — فقط اگر نقش اجازهٔ دیدنِ ادمین‌ها را بدهد (وگرنه ۴۰۳ می‌گیرد).
        if (com.mrm.pgmanager.data.AdminAccess.can("admins", "read")) {
            runCatching { PanelApi.admins(session) }.onSuccess { ui.adminOptions = it }
        }
    }
    LaunchedEffect(deepLinkUsername, session) {
        val name = deepLinkUsername ?: return@LaunchedEffect
        // با صفحه‌بندیِ سمتِ سرور فقط ۶۰ کاربرِ اول در حافظه‌اند؛ اگر کاربرِ اعلان
        // بینشان نبود، همان یک نفر را از پنل می‌پرسیم — قبلاً لمسِ اعلان بی‌صدا هیچ کاری نمی‌کرد.
        val local = ui.users.find { it.username == name }
        val target = local ?: runCatching {
            PanelApi.usersPage(session, com.mrm.pgmanager.data.model.UserQuery(search = name, limit = 5))
                .users.firstOrNull { it.username == name }
        }.getOrNull()
        if (target != null) {
            ui.query = ""
            ui.currentFilter = UserFilter.ALL
            ui.selectedUser = target
        }
        onDeepLinkHandled()
    }
    // درخواستِ ساخت گروهی از صفحهٔ تنظیمات: دیالوگ را همین‌جا باز می‌کنیم تا
    // پس از پایان، لیست کاربران رفرش شود.
    LaunchedEffect(openBulkCreate) {
        if (openBulkCreate) {
            ui.bulkCreateOpen = true
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
    // فقط وقتی این تب واقعاً جلوی چشم است رفرشِ دوره‌ای می‌فرستیم. Pager صفحه‌های
    // همسایه را هم ساخته نگه می‌دارد، و قبلاً داشبورد و کاربران هم‌زمان هر چند
    // ثانیه درخواست می‌زدند و JSON پارس می‌کردند — در حالی که فقط یکی دیده می‌شد.
    val pageActive = rememberUpdatedState(com.mrm.pgmanager.ui.components.LocalPageActive.current)
    LaunchedEffect(session, monitoringSettings.autoRefreshEnabled, monitoringSettings.refreshWhileAppOpen, monitoringSettings.refreshIntervalSeconds) {
        if (monitoringSettings.autoRefreshEnabled && monitoringSettings.refreshWhileAppOpen) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (inForeground && pageActive.value) ui.load(resetHeader = false, silent = true)
                kotlinx.coroutines.delay(monitoringSettings.refreshIntervalSeconds.coerceIn(5, 3600) * 1_000L)
            }
        }
    }
    // برگشتن به این تب بعد از مدتی: اگر داده کهنه شده، بی‌صدا تازه‌اش کن
    // (نه اسکلت، نه پرش) تا رفرشِ دوره‌ای که در غیابِ تب خاموش بود جبران شود.
    // فقط در گذارِ «غایب → حاضر»؛ نه در اولین ساخت (آن را firstLoad پوشش می‌دهد).
    var wasInactive by remember { mutableStateOf(false) }
    LaunchedEffect(pageActive.value) {
        if (!pageActive.value) { wasInactive = true; return@LaunchedEffect }
        if (!wasInactive) return@LaunchedEffect
        wasInactive = false
        if (ui.users.isNotEmpty() && !PanelCache.isFresh(ui.usersKey)) ui.load(resetHeader = false, silent = true)
    }

    // در حالتِ سمتِ سرور، پنل قبلاً فیلتر و مرتب کرده؛ دوباره‌کاری در گوشی فقط
    // نتیجه را خراب می‌کند (مثلاً صفحهٔ دوم را با معیارِ دیگری مرتب می‌کند).
    // منطقِ فیلتر/مرتب‌سازیِ سمتِ گوشی در `UsersUiState.processUsers` است؛
    // اینجا فقط با همان کلیدها صدا زده می‌شود (بدونِ تغییرِ رفتار).
    val processedUsers = remember(ui.users, ui.query, ui.currentFilter, ui.currentSort, monitoringSettings.nearLimitPercent, ui.debtorByUsername, serverMode) {
        UsersUiState.processUsers(
            users = ui.users,
            query = ui.query,
            filter = ui.currentFilter,
            sort = ui.currentSort,
            nearLimitPercent = monitoringSettings.nearLimitPercent,
            debtorByUsername = ui.debtorByUsername,
            serverMode = serverMode
        )
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
                val current = ui.scrollOffset.value
                if (delta > 0f && current < headerHeight) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    ui.scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                else if (delta < 0f && current > 0f) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    ui.scrollOffset.value = newOffset
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
        // ادمینی که مجوزِ users.create ندارد، دکمهٔ ساخت را نمی‌بیند (پنل ۴۰۳ می‌داد).
        if (ui.selectedUserIds.isEmpty() && com.mrm.pgmanager.data.AdminAccess.can("users", "create")) {
            // هنگام اسکرول به پایین محو می‌شود تا جلوی ردیف‌ها را نگیرد (MrmFab).
            PGFAB(
                icon = AppIcon.UserAdd,
                contentDescription = stringResource(R.string.create_user),
                modifier = Modifier.padding(bottom = 72.dp, end = DsSpacing.Xs),
                visible = fabVisible.value
            ) { ui.createMenuOpen = true }
        }
    }) { padding ->
        val topInsets = padding.calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // فاصلهٔ بالای فهرست = ارتفاعِ کاملِ سربرگ (ثابت). جمع‌شدنِ سربرگ با
            // اسکرول، دیگر این مقدار را عوض نمی‌کند؛ به‌جایش کلِ ناحیهٔ فهرست در
            // مرحلهٔ layout به اندازهٔ scrollOffset بالا کشیده می‌شود (پایین‌تر).
            // قبلاً scrollOffset همین‌جا در composition خوانده می‌شد و هر پیکسلِ
            // جمع‌شدنِ سربرگ، فهرست را با contentPadding جدید دوباره می‌ساخت —
            // یعنی recomposition + اندازه‌گیریِ دوباره در هر فریمِ اسکرول.
            val listTopPad = totalHeaderDp + topInsets + 4.dp
            val collapseShift = Modifier.layout { measurable, constraints ->
                // خواندنِ state فقط در همین لامبدا → تغییرش فقط layout را تکرار می‌کند.
                val shift = ui.scrollOffset.value.roundToInt().coerceAtLeast(0)
                val extended = if (constraints.hasBoundedHeight)
                    constraints.copy(maxHeight = constraints.maxHeight + shift)
                else constraints
                val placeable = measurable.measure(extended)
                val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
                layout(placeable.width, height) { placeable.placeRelative(0, -shift) }
            }
            val ptrState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = ui.loading,
                onRefresh = { ui.load() },
                modifier = Modifier.fillMaxSize(),
                state = ptrState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        isRefreshing = ui.loading,
                        state = ptrState,
                        containerColor = themeState.cardSurfaceColor,
                        color = themeState.accentPrimary,
                        modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, -ui.scrollOffset.value.roundToInt()) }.padding(top = listTopPad)
                    )
                }
            ) {
                Box(Modifier.fillMaxSize().then(collapseShift)) {
                when {
                    ui.loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) { items(6) { SkeletonCard() } }
                    ui.error != null -> MrmErrorState(
                        modifier = Modifier.padding(top = listTopPad),
                        onRetry = { ui.load() },
                        message = ui.error
                    )
                    processedUsers.isEmpty() -> {
                        val noMatches = ui.query.isNotBlank() || ui.currentFilter != com.mrm.pgmanager.data.model.UserFilter.ALL
                        MrmEmptyState(
                            modifier = Modifier.padding(top = listTopPad),
                            title = if (noMatches) stringResource(R.string.no_results) else stringResource(R.string.no_user_found),
                            subtitle = if (noMatches) stringResource(R.string.clear_filter_or_create) else stringResource(R.string.create_first_user),
                            icon = if (noMatches) AppIcon.Search else AppIcon.Users
                        ) {
                            if (noMatches) {
                                com.mrm.pgmanager.ui.components.PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { ui.query = ""; ui.currentFilter = com.mrm.pgmanager.data.model.UserFilter.ALL }, modifier = Modifier.height(36.dp))
                            }
                            if (com.mrm.pgmanager.data.AdminAccess.can("users", "create")) com.mrm.pgmanager.ui.components.PGPrimaryButton(stringResource(R.string.create_user), onClick = { ui.createUser = true }, icon = AppIcon.UserAdd)
                        }
                    }
                    else -> {
                    // ورودِ پلکانیِ ردیف‌های اول — فقط در اولین نمایش بعد از بارگذاری.
                    val listIntro = rememberListIntro()
                    androidx.compose.animation.AnimatedContent(targetState = ui.viewMode, label = "viewModeSwitch") { mode ->
                        when (mode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            itemsIndexed(processedUsers, key = { _, u -> u.id }) { index, user ->
                                if (index >= processedUsers.lastIndex - 4) {
                                    LaunchedEffect(index, processedUsers.size) { ui.loadMore() }
                                }
                                Box(Modifier.animateItem().listIntro(listIntro, index)) { LuxuryGridCard(user, selected = ui.selectedUserIds.contains(user.id), onSelectToggle = { ui.selectedUserIds = if (ui.selectedUserIds.contains(user.id)) ui.selectedUserIds - user.id else ui.selectedUserIds + user.id }, onClick = { ui.selectedUser = user }, onQrClick = { ui.qrWithFetch(it) }, onCopySub = { ui.copySubWithFetch(it) }, onLongClick = { ui.quickActionUser = user }, debtorInfo = ui.debtorByUsername[user.username]) }
                            }
                            if (ui.loadingMore) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(DsSpacing.Lg), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.us_loading_more), fontSize = 11.sp, color = themeState.mutedColor)
                                    }
                                }
                            }
                        }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            itemsIndexed(processedUsers, key = { _, u -> u.id }) { index, user ->
                                if (index >= processedUsers.lastIndex - 4) {
                                    LaunchedEffect(index, processedUsers.size) { ui.loadMore() }
                                }
                                Box(Modifier.animateItem().listIntro(listIntro, index)) { LuxuryCompactRow(user, selected = ui.selectedUserIds.contains(user.id), onSelectToggle = { ui.selectedUserIds = if (ui.selectedUserIds.contains(user.id)) ui.selectedUserIds - user.id else ui.selectedUserIds + user.id }, onClick = { ui.selectedUser = user }, onQrClick = { ui.qrWithFetch(it) }, onCopySub = { ui.copySubWithFetch(it) }, onLongClick = { ui.quickActionUser = user }, debtorInfo = ui.debtorByUsername[user.username]) }
                            }
                            if (ui.loadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(DsSpacing.Lg), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.us_loading_more), fontSize = 11.sp, color = themeState.mutedColor)
                                    }
                                }
                            }
                        }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(DsSpacing.Md), contentPadding = PaddingValues(top = listTopPad, bottom = 140.dp)) {
                            itemsIndexed(processedUsers, key = { _, u -> u.id }) { index, user ->
                                if (index >= processedUsers.lastIndex - 4) {
                                    LaunchedEffect(index, processedUsers.size) { ui.loadMore() }
                                }
                                Box(Modifier.animateItem().listIntro(listIntro, index)) { LuxuryMicroRow(user, selected = ui.selectedUserIds.contains(user.id), onSelectToggle = { ui.selectedUserIds = if (ui.selectedUserIds.contains(user.id)) ui.selectedUserIds - user.id else ui.selectedUserIds + user.id }, onClick = { ui.selectedUser = user }, onQrClick = { ui.qrWithFetch(it) }, onCopySub = { ui.copySubWithFetch(it) }, onLongClick = { ui.quickActionUser = user }, debtorInfo = ui.debtorByUsername[user.username]) }
                            }
                            if (ui.loadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(DsSpacing.Lg), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.us_loading_more), fontSize = 11.sp, color = themeState.mutedColor)
                                    }
                                }
                            }
                        }
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
                        if (ui.scrollOffset.value == 0f && coords.size.height > 0) {
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
                    .padding(top = DsSpacing.Sm)
                    .padding(horizontal = DsSpacing.Xl)
                    .padding(bottom = DsSpacing.Lg)
            ) {
                TopBarHeader(onRefresh = { ui.load() }, loading = ui.loading, onOpenSettings = onOpenSettings)

                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (ui.scrollOffset.value == 0f && coords.size.height > 0) {
                                if (statsCardsHeightPx.value != coords.size.height.toFloat()) {
                                    statsCardsHeightPx.value = coords.size.height.toFloat()
                                }
                            }
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else placeable.height.toFloat()
                            val progress = if (maxH > 0f) (ui.scrollOffset.value / maxH).coerceIn(0f, 1f) else 0f
                            val currentH = (placeable.height * (1f - progress)).roundToInt().coerceAtLeast(0)
                            layout(placeable.width, currentH) {
                                placeable.placeRelative(0, (-progress * placeable.height * 0.38f).roundToInt())
                            }
                        }
                        .graphicsLayer {
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else 1f
                            val progress = (ui.scrollOffset.value / maxH).coerceIn(0f, 1f)
                            this.alpha = (1f - progress * 1.3f).coerceIn(0f, 1f)
                        }
                        // فاصله از سربرگ. عمداً *داخلِ* زنجیرهٔ جمع‌شونده است (بعد از
                        // layout و graphicsLayer)، تا با اسکرول همراهِ خودِ کارت‌ها جمع
                        // شود؛ اگر بیرون بود، بعد از جمع‌شدنِ کارت‌ها یک نوارِ خالی
                        // زیرِ سربرگ باقی می‌ماند.
                        .padding(top = DsSpacing.Mid)
                ) {
                            StatsCardsRow(
                            // از خودِ پنل، نه از روی صفحهٔ دانلودشده — وگرنه با
                            // صفحه‌بندی، «۷۳ کاربر» می‌شد «۶۰ کاربر».
                            totalUsers = ui.counts?.totalUsers ?: ui.users.size,
                            activeUsers = ui.counts?.activeUsers ?: ui.users.count { it.status == "active" },
                            onlineUsers = ui.counts?.onlineUsers ?: ui.onlineCount,
                            debtorCount = ui.debtorCount
                        )
                }

                Spacer(Modifier.height(DsSpacing.Sm))
                GlassSearchBar(query = ui.query, onQueryChange = { ui.query = it })
                Spacer(Modifier.height(DsSpacing.Md))
                FilterAndControlBar(
                    currentFilter = ui.currentFilter,
                    onFilterChange = { ui.currentFilter = it },
                    currentSort = ui.currentSort,
                    onSortChange = { ui.currentSort = it },
                    viewMode = ui.viewMode,
                    onViewModeChange = { ui.viewMode = it; store.saveViewMode(it) },
                    debtorCount = ui.debtorCount,
                    groups = ui.groupOptions,
                    groupFilterId = ui.groupFilterId,
                    onGroupFilterChange = { ui.groupFilterId = it },
                    admins = ui.adminOptions,
                    ownerFilter = ui.ownerFilter,
                    onOwnerFilterChange = { ui.ownerFilter = it },
                    expiringWindowDays = com.mrm.pgmanager.data.model.UserQuery.expiringWindowDays(monitoringSettings.nearExpiryDays)
                )
                // چند تا از چند تا — با صفحه‌بندی، دانستنش لازم است.
                if (serverMode && ui.totalMatches > processedUsers.size) {
                    Text(
                        stringResource(R.string.us_showing_count, processedUsers.size, ui.totalMatches),
                        fontSize = 11.sp, color = themeState.mutedColor,
                        modifier = Modifier.padding(top = DsSpacing.Sm, start = DsSpacing.Xxs)
                    )
                }
                // پاک‌سازیِ منقضی‌ها: فقط وقتی فیلترِ «منقضی» فعال است پیدایش می‌شود،
                // چون همان‌جاست که به آدم فکرِ حذفِ دسته‌جمعی می‌رسد. اول فهرست را
                // از پنل می‌گیریم تا کاربر ببیند چه کسانی حذف می‌شوند.
                // `/api/users/expired` در پنل فقط با دامنهٔ «همهٔ کاربران» مجاز است (require_scope_all).
                if (ui.currentFilter == UserFilter.EXPIRED && com.mrm.pgmanager.data.AdminAccess.scopeAll("users", "read") && com.mrm.pgmanager.data.AdminAccess.scopeAll("users", "delete")) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = DsSpacing.Md).clip(DsRadius.Sm)
                            .background(GlassRed.copy(0.10f))
                            .border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.26f)), DsRadius.Sm)
                            .pressScale(0.98f)
                            .clickable {
                                scope.launch {
                                    val names = runCatching { PanelApi.cleanupCandidates(session) }.getOrDefault(emptyList())
                                    if (names.isEmpty()) {
                                        com.mrm.pgmanager.ui.feedback.AppFeedback.info(context.getString(R.string.us_cleanup_none))
                                    } else ui.cleanupNames = names
                                }
                            }
                            .padding(horizontal = DsSpacing.Mid, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                    ) {
                        RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 13.dp)
                        Text(stringResource(R.string.us_cleanup), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                    }
                }
                ui.offlineAt?.let { cachedAt ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = DsSpacing.Md).clip(DsRadius.Sm).background(GlassAmber.copy(.12f)).border(BorderStroke(DsBorder.Hairline, GlassAmber.copy(.30f)), DsRadius.Sm).padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                    ) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassAmber, size = 14.dp)
                        Text(stringResource(R.string.offline_data, java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(cachedAt))), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassAmber, maxLines = 1)
                    }
                }
            }

            if (ui.selectedUserIds.isNotEmpty()) {
                // فاز ۲.۵ — راهنمای یک‌بارهٔ انتخاب چندتایی: نخستین باری که نوار انتخاب باز می‌شود.
                val multiHint = stringResource(R.string.us_hint_multi_select)
                LaunchedEffect(Unit) {
                    if (!store.hintShown(SessionStore.HINT_MULTI_SELECT)) {
                        kotlinx.coroutines.delay(600)
                        com.mrm.pgmanager.ui.feedback.AppFeedback.info(multiHint)
                        store.markHintShown(SessionStore.HINT_MULTI_SELECT)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        // ۴۶dp کمتر از قبل، چون فضای رزروشدهٔ همبرگری آزاد شد.
                        .padding(top = 64.dp)
                ) {
                    BulkActionsBar(
                        selectedCount = ui.selectedUserIds.size,
                        onClear = { ui.selectedUserIds = emptySet() },
                        onSelectAll = { ui.selectedUserIds = processedUsers.map { it.id }.toSet() },
                        onExport = { ui.exportChooserOpen = true },
                        onDelete = { val ids = ui.selectedUserIds.toSet(); ui.selectedUserIds = emptySet(); ui.pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_delete_title, ids.size), message = context.getString(R.string.us_bulk_delete_msg), confirmLabel = context.getString(R.string.us_delete), danger = true, action = { ui.runAction(notification = context.getString(R.string.us_n_bulk_delete) to context.getString(R.string.us_n_bulk_delete_body, ids.size)) { PanelApi.bulkDeleteUsers(session, ids) } }) },
                        onResetUsage = { val ids = ui.selectedUserIds.toSet(); ui.selectedUserIds = emptySet(); ui.pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_reset_title, ids.size), message = context.getString(R.string.us_bulk_reset_msg), confirmLabel = context.getString(R.string.us_confirm), action = { ui.runAction(notification = context.getString(R.string.us_n_bulk_reset) to context.getString(R.string.us_n_bulk_reset_body, ids.size)) { PanelApi.bulkResetUsersUsage(session, ids) } }) },
                        onDisable = { val ids = ui.selectedUserIds.toSet(); ui.selectedUserIds = emptySet(); ui.pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_disable_title, ids.size), message = context.getString(R.string.us_bulk_disable_msg), confirmLabel = context.getString(R.string.us_confirm), action = { ui.runAction(notification = context.getString(R.string.us_n_bulk_disable) to context.getString(R.string.us_n_bulk_disable_body, ids.size)) { PanelApi.bulkDisableUsers(session, ids) } }) },
                        onEnable = { val ids = ui.selectedUserIds.toSet(); ui.selectedUserIds = emptySet(); ui.pendingBulk = PendingBulk(title = context.getString(R.string.us_bulk_enable_title, ids.size), message = context.getString(R.string.us_bulk_enable_msg), confirmLabel = context.getString(R.string.us_confirm), action = { ui.runAction(notification = context.getString(R.string.us_n_bulk_enable) to context.getString(R.string.us_n_bulk_enable_body, ids.size)) { PanelApi.bulkEnableUsers(session, ids) } }) },
                        onApplyTemplate = {
                            ui.showBulkTemplateDialog = true
                        },
                        onGroupAdd = { ui.bulkGroupAdd = true; ui.bulkGroupPicker = true },
                        onGroupRemove = { ui.bulkGroupAdd = false; ui.bulkGroupPicker = true },
                        onAddDays = { ui.bulkAmountText = ""; ui.bulkAmountKind = "days" },
                        onAddData = { ui.bulkAmountText = ""; ui.bulkAmountKind = "data" },
                        onRevokeSubs = { ui.bulkRevokeConfirm = true }
                    )
                }
            }
        }
    }

    UsersScreenDialogs(
        ui = ui,
        session = session,
        monitoringSettings = monitoringSettings,
        defaultCurrency = defaultCurrency,
        themeState = themeState,
        onBeginExport = { beginExport(it) }
    )

    LaunchedEffect(ui.users, monitoringSettings.debtorAutoDisableEnabled, monitoringSettings.debtorAutoDisableAfterHours) {
        if (!monitoringSettings.debtorAutoDisableEnabled) return@LaunchedEffect
        if (ui.users.isEmpty()) return@LaunchedEffect
        ui.debtorsForCurrentPanel.forEach { d ->
            if (!d.isOverdue(monitoringSettings.debtorAutoDisableAfterHours)) return@forEach
            if (d.autoDisabled) return@forEach
            val pu = ui.users.find { it.username == d.username } ?: return@forEach
            if (pu.status == "disabled") {
                val updated = d.copy(autoDisabled = true)
                store.setDebtor(updated)
                ui.reloadDebtors()
                return@forEach
            }
            runCatching { PanelApi.setDisabled(session, pu, true) }.onSuccess {
                val updated = d.copy(autoDisabled = true)
                store.setDebtor(updated)
                ui.reloadDebtors()
                if (monitoringSettings.notificationsEnabled && monitoringSettings.notifyDebtorOverdue) {
                    NotificationHelper.post(context, ("debtor_overdue_"+d.username).hashCode(), NotificationHelper.CHANNEL_EVENTS, context.getString(R.string.us_n_auto_disable), context.getString(R.string.us_n_auto_disable_body, d.username, monitoringSettings.debtorAutoDisableAfterHours, d.amount.toString(), d.currency), targetUsername = d.username)
                }
            }
        }
    }
}

