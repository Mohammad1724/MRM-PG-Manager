package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.dialogs.RenewUserDialog
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.NotificationHelper
import com.mrm.pgmanager.utils.RenewalLogic
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.launch

/** فیلترهای بالای صفحهٔ تمدیدها. */
private enum class RenewFilter { ALL, EXPIRED, SOON }

/**
 * صفحهٔ «تمدیدها» — فهرستِ کاربرانی که کارِ مالی دارند:
 * منقضی‌شده‌ها و آن‌هایی که به‌زودی منقضی می‌شوند، مرتب‌شده بر اساسِ فوریت.
 *
 * هدف این است که مدیر مجبور نباشد در فهرستِ کاملِ کاربران دنبالِ آن‌ها بگردد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenewalsScreen(session: Session, onLogout: () -> Unit) {
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { com.mrm.pgmanager.data.storage.SessionStore(context) }

    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(RenewFilter.ALL) }
    var soonDays by remember { mutableStateOf(7) }
    var renewTarget by remember { mutableStateOf<PanelUser?>(null) }
    var busyUser by remember { mutableStateOf<String?>(null) }
    val currency = remember { store.readMonitoringSettings().debtorCurrency }

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        runCatching { PanelApi.users(session) }
            .onSuccess { users = it; error = null }
            .onFailure {
                error = it.message
                if (it.message?.contains("401") == true) onLogout()
            }
        loading = false
    }

    LaunchedEffect(session) { load() }

    // دسته‌بندی یک‌بار انجام می‌شود و بینِ شمارنده‌ها و فهرست مشترک است.
    val buckets = remember(users, soonDays) {
        users.associate { u ->
            u.username to RenewalLogic.bucket(u.expire, u.usedTraffic, u.dataLimit, soonDays)
        }
    }
    val expiredCount = buckets.values.count { it == RenewalLogic.Bucket.EXPIRED }
    val soonCount = buckets.values.count { it == RenewalLogic.Bucket.SOON }

    val visible = remember(users, buckets, filter) {
        users.filter { u ->
            when (filter) {
                RenewFilter.ALL -> buckets[u.username] != RenewalLogic.Bucket.OK
                RenewFilter.EXPIRED -> buckets[u.username] == RenewalLogic.Bucket.EXPIRED
                RenewFilter.SOON -> buckets[u.username] == RenewalLogic.Bucket.SOON
            }
        }.sortedBy { RenewalLogic.urgencyKey(it.expire) }
    }

    fun renew(user: PanelUser, days: Int, mode: RenewalLogic.Mode, amount: Long) {
        val newExpire = RenewalLogic.newExpiryDateString(user.expire, days, mode) ?: return
        busyUser = user.username
        scope.launch {
            runCatching {
                PanelApi.modifyUser(
                    session,
                    user.username,
                    user.dataLimit.toDouble() / 1073741824.0,
                    newExpire,
                    user.note ?: "",
                    user.hwidLimit,
                    user.groupIds
                )
            }.onSuccess {
                // فروش فقط پس از موفقیتِ واقعیِ تمدید ثبت می‌شود تا گزارشِ درآمد دروغ نگوید.
                if (amount > 0L) {
                    val now = System.currentTimeMillis()
                    store.addSale(
                        com.mrm.pgmanager.data.model.SaleRecord(
                            id = "$now-${user.username}",
                            username = user.username,
                            baseUrl = session.baseUrl,
                            amount = amount,
                            currency = currency,
                            days = days,
                            soldAt = now
                        )
                    )
                }
                val ms = store.readMonitoringSettings()
                if (ms.notificationsEnabled && ms.notifyUserActions) {
                    NotificationHelper.post(
                        context,
                        ("renew" + user.username).hashCode(),
                        NotificationHelper.CHANNEL_EVENTS,
                        "تمدید اشتراک",
                        "${user.username} به مدت $days روز تمدید شد",
                        targetUsername = user.username
                    )
                }
                load(silent = true)
            }.onFailure {
                error = it.message
                android.widget.Toast.makeText(
                    context, "خطا: ${it.message?.take(120)}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
            busyUser = null
        }
    }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } },
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                isRefreshing = refreshing, state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = theme.cardSurfaceColor, color = theme.accentPrimary
            )
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
                .padding(horizontal = DsSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = DsSpacing.FabClearance)
        ) {
            // ── Header
            item {
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.renewals), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        Text(stringResource(R.string.renewals_subtitle), fontSize = 10.sp, color = theme.mutedColor)
                    }
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor)
                            .border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp))
                            .clickable { scope.launch { refreshing = true; load(true); refreshing = false } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(14.dp), color = theme.mutedColor, strokeWidth = 1.6.dp)
                        else RoundedAppIcon(AppIcon.Refresh, tint = theme.mutedColor, size = 16.dp)
                    }
                }
            }

            // ── شمارنده‌ها
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountCard(
                        label = stringResource(R.string.renew_expired),
                        value = expiredCount.toString(),
                        accent = GlassRed,
                        icon = AppIcon.StatusExpired,
                        modifier = Modifier.weight(1f)
                    )
                    CountCard(
                        label = stringResource(R.string.renew_soon),
                        value = soonCount.toString(),
                        accent = GlassAmber,
                        icon = AppIcon.Timer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── فیلتر + پنجرهٔ «به‌زودی»
            item {
                Column(
                    Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(stringResource(R.string.renew_all), filter == RenewFilter.ALL, Modifier.weight(1f)) { filter = RenewFilter.ALL }
                        FilterChip(stringResource(R.string.renew_expired), filter == RenewFilter.EXPIRED, Modifier.weight(1f)) { filter = RenewFilter.EXPIRED }
                        FilterChip(stringResource(R.string.renew_soon), filter == RenewFilter.SOON, Modifier.weight(1f)) { filter = RenewFilter.SOON }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.renew_window), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(3, 7, 14, 30).forEach { d ->
                                val sel = soonDays == d
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) theme.accentPrimary.copy(alpha = 0.14f) else theme.searchBgColor)
                                        .border(BorderStroke(DsBorder.Hairline, if (sel) theme.accentPrimary.copy(alpha = 0.45f) else theme.borderSubtle), RoundedCornerShape(6.dp))
                                        .clickable { soonDays = d }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("$d", fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) theme.accentPrimary else theme.mutedColor)
                                }
                            }
                        }
                    }
                }
            }

            if (loading && users.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accentPrimary)
                    }
                }
            } else if (visible.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoundedAppIcon(AppIcon.CheckCircle, tint = GlassGreen, size = 28.dp)
                        Text(stringResource(R.string.renew_empty), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
                        Text(stringResource(R.string.renew_empty_desc), fontSize = 10.sp, color = theme.mutedColor)
                    }
                }
            } else {
                items(visible, key = { it.id }) { u ->
                    RenewalRow(
                        user = u,
                        bucket = buckets[u.username] ?: RenewalLogic.Bucket.OK,
                        busy = busyUser == u.username,
                        onRenew = { renewTarget = u }
                    )
                }
            }
        }
    }

    renewTarget?.let { u ->
        RenewUserDialog(
            user = u,
            onDismiss = { renewTarget = null },
            currency = currency,
            onConfirm = { days, mode, amount -> renewTarget = null; renew(u, days, mode, amount) }
        )
    }
}

/** کارتِ شمارنده در بالای صفحه. */
@Composable
private fun CountCard(label: String, value: String, accent: Color, icon: AppIcon, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Column(
        modifier.clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(26.dp).clip(DsRadius.Sm).background(accent.copy(alpha = 0.12f))
                    .border(BorderStroke(DsBorder.Hairline, accent.copy(alpha = 0.24f)), DsRadius.Sm),
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(icon, tint = accent, size = 13.dp) }
            Text(label, fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
        }
        MrmText(value, isTechnical = true, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.accentPrimary.copy(alpha = 0.14f) else theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(alpha = 0.45f) else theme.borderSubtle), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) theme.accentPrimary else theme.mutedColor)
    }
}

/** یک ردیف از فهرستِ تمدیدها. */
@Composable
private fun RenewalRow(user: PanelUser, bucket: RenewalLogic.Bucket, busy: Boolean, onRenew: () -> Unit) {
    val theme = LocalThemeState.current
    val remaining = DateLogic.remainingDays(user.expire)
    val limitReached = user.dataLimit > 0L && user.usedTraffic >= user.dataLimit
    val accent = when {
        bucket == RenewalLogic.Bucket.EXPIRED -> GlassRed
        remaining != null && remaining <= 3L -> GlassRed
        else -> GlassAmber
    }
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // نوارِ رنگیِ فوریت
        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(accent))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MrmText(user.username, isTechnical = true, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (limitReached) stringResource(R.string.renew_limit_reached) else DateLogic.daysLeftText(user.expire),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent
                )
                Text("•", fontSize = 9.sp, color = theme.mutedColor)
                Text(
                    if (user.dataLimit > 0L) "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}"
                    else formatBytes(user.usedTraffic),
                    fontSize = 10.sp, color = theme.mutedColor
                )
            }
        }

        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
                .background(theme.accentPrimary.copy(alpha = 0.14f))
                .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(alpha = 0.40f)), RoundedCornerShape(8.dp))
                .clickable(enabled = !busy, onClick = onRenew)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(13.dp), color = theme.accentPrimary, strokeWidth = 1.6.dp)
            else Text(stringResource(R.string.renew_action), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary)
        }
    }
}
