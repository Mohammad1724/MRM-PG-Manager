package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelAdmin
import com.mrm.pgmanager.data.model.PanelAdminRole
import com.mrm.pgmanager.data.model.PermissionScope
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PGBadge
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.primarySurface
import com.mrm.pgmanager.utils.ApiErrorMapper
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.launch

/**
 * **«اپراتورها و نقش‌ها»** — دو تبِ خواندنی: فهرستِ ادمین‌های پنل و نقش‌هایشان.
 *
 * ### چرا این صفحه
 * هر دکمه‌ای که در اپ با `AdminAccess.can(...)` پنهان می‌شود، ریشه‌اش در همین
 * جدول است؛ ولی از گوشی هیچ راهی برای دیدنِ آن نبود. نتیجه دو مشکوکِ همیشگی:
 * «چرا دکمهٔ حذف برای من نیست؟» و «این نقش دقیقاً چه می‌تواند بکند؟». حالا
 * هر دو از همین‌جا جواب می‌گیرند، بدونِ باز کردنِ پنلِ وب.
 *
 * ### چرا خواندنی
 * ساخت/ویرایشِ ادمین و نقش در پنل همراهِ رمز، سقفِ حجم و سقفِ تعداد است و
 * فرمِ وب است؛ ساختنِ فرمِ نیمه‌کاره در گوشی فقط ۴۲۲ و «من چه کردم؟» می‌سازد.
 * این صفحه **نقشه** است، نه ویرایشگر.
 *
 * ### دسترسی‌ها
 *  • تبِ اپراتورها: `admins.read` (بدونِ آن کارتِ صفحه در «پنلِ من» هم نیست).
 *  • تبِ نقش‌ها: `admin_roles.read` — اگر نباشد تب ساخته نمی‌شود (پنل ۴۰۳ می‌داد).
 */
@Composable
fun AdminsRolesScreen(session: Session, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val backLabel = stringResource(R.string.cd_back)

    val canReadRoles = AdminAccess.can("admin_roles", "read")
    var tab by rememberSaveable { mutableStateOf(0) }

    val adminsCacheKey = PanelCache.adminsKey(session.baseUrl)
    val rolesCacheKey = PanelCache.adminRolesKey(session.baseUrl)

    var admins by remember(session) { mutableStateOf(PanelCache.get<List<PanelAdmin>>(adminsCacheKey) ?: emptyList()) }
    var roles by remember(session) { mutableStateOf(PanelCache.get<List<PanelAdminRole>>(rolesCacheKey) ?: emptyList()) }
    var loading by remember(session) { mutableStateOf(admins.isEmpty() && roles.isEmpty()) }
    var loadError by remember(session) { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<PanelAdminRole?>(null) }

    suspend fun load() {
        var failure: Throwable? = null
        runCatching { PanelApi.admins(session) }
            .onSuccess { admins = it; PanelCache.put(adminsCacheKey, it) }
            .onFailure { failure = it }
        if (canReadRoles) {
            runCatching { PanelApi.adminRoles(session) }
                .onSuccess { roles = it.roles; PanelCache.put(rolesCacheKey, it.roles) }
                .onFailure { if (failure == null) failure = it }
        }
        loadError = failure?.let { ApiErrorMapper.friendly(context, it) }
        loading = false
    }

    LaunchedEffect(session) { if (!PanelCache.isFresh(adminsCacheKey) || !PanelCache.isFresh(rolesCacheKey)) load() }

    val tabs = buildList {
        add(R.string.ar_tab_admins)
        if (canReadRoles) add(R.string.ar_tab_roles)
    }
    // اگر تبِ نقش‌ها نبود ولی `tab` روی ۱ مانده بود (مجوزها دیر رسیدند)،
    // به تبِ موجود برمی‌گردیم تا صفحهٔ خالی نماند.
    if (tab !in tabs.indices) tab = 0

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
        ) {
            Box(
                Modifier.padding(3.dp).size(34.dp).clip(DsRadius.Sm).clickable { onBack() }
                    .background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                    .semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Prev, tint = theme.mutedColor, size = 16.dp) }

            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ar_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.ar_subtitle),
                    fontSize = 11.sp, color = theme.mutedColor, maxLines = 2
                )
            }
            InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = { scope.launch { loading = admins.isEmpty() && roles.isEmpty(); load() } }
            )
        }

        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Xl).background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                .padding(DsSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
        ) {
            tabs.forEachIndexed { index, labelRes ->
                val selected = tab == index
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(DsRadius.Full)
                        .clickable { tab = index }
                        .primarySurface(theme, selected, DsRadius.Full, idle = Color.Transparent)
                        .pressScale(0.96f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(labelRes), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) theme.onPrimary else theme.mutedColor,
                        maxLines = 1
                    )
                }
            }
        }

        loadError?.let { InfraBanner(text = it, success = false) { loadError = null } }

        when {
            loading -> Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = theme.accentPrimary) }

            loadError != null && admins.isEmpty() && roles.isEmpty() -> MrmErrorState(
                onRetry = { scope.launch { loading = true; load() } },
                message = loadError
            )

            tab == 0 -> if (admins.isEmpty()) MrmEmptyState(
                title = stringResource(R.string.ar_admins_empty),
                subtitle = stringResource(R.string.ar_admins_empty_hint),
                icon = AppIcon.Users
            ) else {
                val intro = rememberListIntro()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Md),
                    contentPadding = PaddingValues(bottom = DsSpacing.Xl)
                ) {
                    itemsIndexed(admins, key = { _, a -> a.id }) { index, admin ->
                        Box(Modifier.listIntro(intro, index)) { AdminRow(admin) }
                    }
                }
            }

            else -> if (roles.isEmpty()) MrmEmptyState(
                title = stringResource(R.string.ar_roles_empty),
                subtitle = stringResource(R.string.ar_roles_empty_hint),
                icon = AppIcon.Lock
            ) else {
                val intro = rememberListIntro()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Md),
                    contentPadding = PaddingValues(bottom = DsSpacing.Xl)
                ) {
                    itemsIndexed(roles, key = { _, r -> r.id }) { index, role ->
                        Box(Modifier.listIntro(intro, index)) {
                            RoleRow(role = role, onClick = { detail = role })
                        }
                    }
                }
            }
        }
    }

    detail?.let { role ->
        RoleDetailsDialog(role = role, onDismiss = { detail = null })
    }
}

/** یک اپراتور: نام، نقش، وضعیت، تعداد کاربر و مصرف. */
@Composable
private fun AdminRow(admin: PanelAdmin) {
    val theme = LocalThemeState.current
    val statusText = when {
        admin.isDisabled -> stringResource(R.string.ar_status_disabled)
        admin.isLimited -> stringResource(R.string.ar_status_limited)
        else -> stringResource(R.string.ar_status_active)
    }
    val statusColor = when {
        admin.isDisabled -> DsSemantic.Danger
        admin.isLimited -> DsSemantic.Warning
        else -> DsSemantic.Success
    }
    Column(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(DsSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            RoundedAppIcon(
                icon = if (admin.isOwner) AppIcon.CheckCircle else AppIcon.User,
                tint = if (admin.isOwner) theme.accentPrimary else theme.mutedColor,
                size = 16.dp
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    admin.username, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (admin.roleName.isNotBlank()) MrmText(
                    admin.roleName, fontSize = 10.sp, color = theme.mutedColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (admin.isOwner) PGBadge(stringResource(R.string.ar_owner), color = theme.accentPrimary)
            PGBadge(statusText, color = statusColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            Text(
                stringResource(R.string.ar_users_count, admin.totalUsers),
                fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.weight(1f)
            )
            if (admin.dataLimit != null) Text(
                stringResource(
                    R.string.ar_traffic_fmt,
                    formatBytes(admin.usedTraffic),
                    formatBytes(requireNotNull(admin.dataLimit))
                ),
                fontSize = 10.sp, color = theme.mutedColor
            ) else Text(
                stringResource(R.string.ar_traffic_used_fmt, formatBytes(admin.usedTraffic)),
                fontSize = 10.sp, color = theme.mutedColor
            )
        }
    }
}

/** یک نقش: نام، نشانِ مالک، سقفِ کاربران و شمارِ اکشن‌های مجاز. */
@Composable
private fun RoleRow(role: PanelAdminRole, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .clickable { onClick() }
            .heightIn(min = 56.dp)
            .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Box(
            Modifier.size(30.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) { RoundedAppIcon(AppIcon.Lock, tint = theme.accentPrimary, size = 15.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                role.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(stringResource(R.string.ar_role_actions, role.allowedActionCount))
                    role.maxUsers?.let { append(" · "); append(stringResource(R.string.ar_role_max_users, it)) }
                },
                fontSize = 10.sp, color = theme.mutedColor, maxLines = 1
            )
        }
        if (role.isOwner) PGBadge(stringResource(R.string.ar_owner), color = theme.accentPrimary)
        RoundedAppIcon(AppIcon.Next, tint = theme.mutedColor, size = 14.dp)
    }
}

/**
 * جدولِ مجوزهای یک نقش — `resource → action`، با نشانِ دامنه.
 *
 * دامنه (`scope`) مهم است: `users.read` با دامنهٔ ۱ یعنی «فقط کاربرانِ خودش»،
 * و همان اکشن با دامنهٔ ۲ یعنی «همه». نادیده‌گرفتنش باعث می‌شد نقشِ محدود
 * با نقشِ مالک یکی به نظر برسد.
 */
@Composable
private fun RoleDetailsDialog(role: PanelAdminRole, onDismiss: () -> Unit) {
    val theme = LocalThemeState.current
    // `context.getString` عمدی است: داخلِ `forEach`/`itemsIndexed` نمی‌شود
    // `stringResource` صدا زد (آن لامبداها @Composable نیستند).
    val context = LocalContext.current
    val resources = remember(role.id) { role.permissions.keys.sortedBy { resourceLabelOrder(it) } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(DsRadius.Xxl)
                .background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
        ) {
            Text(
                role.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                color = theme.inkColor, maxLines = 2
            )
            if (role.isOwner) Text(
                stringResource(R.string.ar_owner_hint),
                fontSize = 10.sp, color = theme.mutedColor
            )
            if (resources.isEmpty()) Text(
                stringResource(R.string.ar_role_no_perms),
                fontSize = 11.sp, color = theme.mutedColor
            ) else LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                itemsIndexed(resources, key = { _, r -> r }) { _, resource ->
                    val actions = role.permissions[resource].orEmpty()
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
                            .padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
                    ) {
                        Text(
                            resourceLabelText(context, resource),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                            actions.keys.sortedBy { actionLabelOrder(it) }.forEach { action ->
                                val own = actions[action] == PermissionScope.OWN
                                PGBadge(
                                    text = actionLabelText(context, action) +
                                        if (own) " · " + context.getString(R.string.ar_scope_own) else "",
                                    color = if (own) DsSemantic.Warning else theme.accentPrimary
                                )
                            }
                        }
                    }
                }
            }
            PGSecondaryButton(
                text = stringResource(R.string.cd_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── برچسب‌های فارسیِ منابع و اکشن‌ها ─────────────────────────────────────
//
// کلیدهای خامِ API («client_templates»، «read_simple») برای ادمین بی‌معناست و
// ترجمه‌شان هم در replace‌ها نمی‌گنجد؛ پس یک نگاشتِ صریح داریم و هر کلیدِ
// ناشناخته (نسخهٔ تازه‌ترِ پنل) به همان نامِ خام برمی‌گردد — بدونِ کرش.

private fun resourceLabelRes(resource: String): Int? = when (resource) {
    "users" -> R.string.ar_res_users
    "admins" -> R.string.ar_res_admins
    "nodes" -> R.string.ar_res_nodes
    "groups" -> R.string.ar_res_groups
    "hosts" -> R.string.ar_res_hosts
    "templates" -> R.string.ar_res_templates
    "client_templates" -> R.string.ar_res_client_templates
    "cores" -> R.string.ar_res_cores
    "settings" -> R.string.ar_res_settings
    "system" -> R.string.ar_res_system
    "hwids" -> R.string.ar_res_hwids
    "admin_roles" -> R.string.ar_res_admin_roles
    "api_keys" -> R.string.ar_res_api_keys
    // کلیدِ ناشناخته (پنلِ تازه‌تر) به همان نامِ خام نشان داده می‌شود.
    else -> null
}

private fun resourceLabelText(context: android.content.Context, resource: String): String =
    resourceLabelRes(resource)?.let { context.getString(it) } ?: resource

private fun actionLabelText(context: android.content.Context, action: String): String =
    actionLabelRes(action)?.let { context.getString(it) } ?: action

private fun resourceLabelOrder(resource: String): Int {
    val known = listOf(
        "users", "admins", "admin_roles", "groups", "hosts", "nodes", "cores",
        "templates", "client_templates", "hwids", "api_keys", "settings", "system"
    )
    return known.indexOf(resource).let { if (it < 0) known.size else it }
}

private fun actionLabelRes(action: String): Int? = when (action) {
    "create" -> R.string.ar_act_create
    "read" -> R.string.ar_act_read
    "read_simple" -> R.string.ar_act_read_simple
    "read_general" -> R.string.ar_act_read_general
    "update" -> R.string.ar_act_update
    "delete" -> R.string.ar_act_delete
    "reset_usage" -> R.string.ar_act_reset_usage
    "revoke_sub" -> R.string.ar_act_revoke_sub
    "set_owner" -> R.string.ar_act_set_owner
    "activate_next_plan" -> R.string.ar_act_activate_next_plan
    "passkeys" -> R.string.ar_act_passkeys
    "reconnect" -> R.string.ar_act_reconnect
    "update_core" -> R.string.ar_act_update_core
    "logs" -> R.string.ar_act_logs
    "stats" -> R.string.ar_act_stats
    else -> null
}

private fun actionLabelOrder(action: String): Int {
    val known = listOf("create", "read", "read_simple", "read_general", "update", "delete")
    return known.indexOf(action).let { if (it < 0) known.size else it }
}
