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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mrm.pgmanager.data.model.CreatedApiKey
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PGBadge
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.dialogs.ChipSelector
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.SettingsInfoRow
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorMapper
import com.mrm.pgmanager.utils.DateLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **کلیدهای API** — ساخت/چرخش/غیرفعال‌سازی/حذفِ کلیدهای پنل.
 *
 * ### چرا این صفحه ارزشِ یک صفحهٔ کامل را دارد
 * ورودِ اپ با کلیدِ API تنها راهی است که توکن هر ۲۴ ساعت نمی‌میرد، پس ورکرِ
 * مانیتورینگ و ویجت با آن زنده می‌مانند. تا امروز ساختنِ کلید فقط در پنلِ وب ممکن
 * بود و ادمینی که فقط گوشی داشت، مانیتورینگش یک روز پس از نصب خاموش می‌شد.
 *
 * ### قراردادهایی که از پنل می‌آیند
 *  • متنِ کاملِ کلید **فقط یک‌بار** (در پاسخِ ساخت یا چرخش) برمی‌گردد؛ در اپ
 *    ذخیره نمی‌شود و بعد از بستنِ دیالوگ فقط `api_key_trimmed` می‌ماند.
 *  • `POST …/{id}/revoke` در واقع **چرخش** است: همان کلید با متنِ تازه، و کلیدِ
 *    قبلی بی‌درنگ باطل می‌شود. برای خاموش‌کردن از `PATCH status=disabled` استفاده
 *    می‌کنیم و برای «دیگر هیچ‌وقت کار نکند» از حذف.
 *  • مجوزها جدا هستند (`api_keys.read/create/update/delete`) و هر اکشن با همان
 *    گیت می‌شود؛ ۴۰۳ پنل هم با `detail` خودش نشان داده می‌شود.
 */
@Composable
fun ApiKeysScreen(session: Session, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val backLabel = stringResource(R.string.cd_back)

    val cacheKey = PanelCache.apiKeysKey(session.baseUrl)
    var keys by remember(session) { mutableStateOf(PanelCache.get<List<PanelApiKey>>(cacheKey) ?: emptyList()) }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<PanelApiKey>>(cacheKey) == null) }
    var loadError by remember(session) { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }

    var creating by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<CreatedApiKey?>(null) }
    var rotating by remember { mutableStateOf<PanelApiKey?>(null) }
    var deleting by remember { mutableStateOf<PanelApiKey?>(null) }

    val canCreate = AdminAccess.can("api_keys", "create")
    val canUpdate = AdminAccess.can("api_keys", "update")
    val canDelete = AdminAccess.can("api_keys", "delete")

    val msgRotated = stringResource(R.string.ak_rotated)
    val msgDeleted = stringResource(R.string.ak_deleted)
    val msgEnabled = stringResource(R.string.ak_enabled_msg)
    val msgDisabled = stringResource(R.string.ak_disabled_msg)

    suspend fun load() {
        runCatching { PanelApi.apiKeys(session) }
            .onSuccess {
                keys = it
                loadError = null
                banner = null
                PanelCache.put(cacheKey, it)
            }
            .onFailure {
                val message = ApiErrorMapper.friendly(context, it)
                loadError = message
                if (keys.isNotEmpty()) banner = message
            }
        loading = false
    }

    // مثلِ بقیهٔ صفحه‌های سطح‌سرور: با کشِ تازه دوباره کشیده نمی‌شود. برگشتن از
    // دیالوگِ چرخش/حذف خودش `load()` را صدا می‌زند، پس این گیت هیچ دادهٔ
    // کهنه‌ای روی صفحه نگه نمی‌دارد.
    LaunchedEffect(session) { if (!PanelCache.isFresh(cacheKey)) load() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    fun toggle(key: PanelApiKey) {
        busyId = key.id
        scope.launch {
            runCatching { PanelApi.setApiKeyDisabled(session, key.id, disabled = key.isActive) }
                .onSuccess {
                    toast = if (key.isActive) msgDisabled else msgEnabled
                    load()
                }
                .onFailure { banner = ApiErrorMapper.friendly(context, it) }
            busyId = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ
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
                    stringResource(R.string.ak_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.ak_subtitle),
                    fontSize = 11.sp, color = theme.mutedColor, maxLines = 2
                )
            }
            if (keys.isNotEmpty()) PGBadge("${keys.size}")
            InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = { scope.launch { loading = keys.isEmpty(); load() } }
            )
            if (canCreate) InfraIconButton(
                icon = AppIcon.Add,
                description = stringResource(R.string.ak_create),
                tint = theme.accentPrimary,
                onClick = { creating = true }
            )
        }

        if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
        banner?.let { InfraBanner(text = it, success = false) { banner = null } }

        when {
            loading && keys.isEmpty() -> Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp),
                contentAlignment = Alignment.Center
            ) { MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp) }

            loadError != null && keys.isEmpty() -> MrmErrorState(
                onRetry = { scope.launch { loading = true; load() } },
                message = loadError
            )

            keys.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.ak_empty),
                subtitle = stringResource(R.string.ak_empty_hint),
                icon = AppIcon.Lock
            ) {
                if (canCreate) PGPrimaryButton(
                    text = stringResource(R.string.ak_create),
                    onClick = { creating = true }
                )
            }

            else -> {
                val intro = rememberListIntro()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Md),
                    contentPadding = PaddingValues(bottom = DsSpacing.Xl)
                ) {
                    itemsIndexed(keys, key = { _, key -> key.id }) { index, key ->
                        Box(Modifier.listIntro(intro, index)) {
                            ApiKeyRow(
                                key = key,
                                busy = busyId == key.id,
                                onToggle = if (canUpdate) ({ toggle(key) }) else null,
                                onRotate = if (canUpdate) ({ rotating = key }) else null,
                                onDelete = if (canDelete) ({ deleting = key }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    // ── ساخت
    if (creating) CreateApiKeyDialog(
        onDismiss = { creating = false },
        // نامِ پارامتر `onCreate` نبود: با `override fun onCreate(savedInstanceState)`
        // اکتیویتی هم‌نام می‌شد و ابزارِ `kt-lambdaargs` این فراخوانی را با آن امضا
        // مقایسه می‌کرد؛ نامِ یکتا هم خواناست هم تله را برمی‌دارد.
        submit = { name, note, days, onResult ->
            scope.launch {
                runCatching { PanelApi.createApiKey(session, name, note, days) }
                    .onSuccess {
                        creating = false
                        revealed = it
                        load()
                    }
                    .onFailure { e -> onResult(ApiErrorMapper.friendly(context, e)) }
            }
        }
    )

    // ── نمایشِ یک‌بارهٔ متنِ کلید (پس از ساخت یا چرخش)
    revealed?.let { fresh ->
        Dialog(onDismissRequest = { revealed = null }) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                    .padding(DsSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
            ) {
                Text(
                    stringResource(R.string.ak_reveal_title), fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.ak_reveal_hint), fontSize = 11.sp, color = theme.mutedColor
                )
                SettingsInfoRow(
                    label = fresh.item.name,
                    value = fresh.key,
                    copyable = true
                )
                PGPrimaryButton(
                    text = stringResource(R.string.ak_reveal_done),
                    onClick = { revealed = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // ── چرخش
    rotating?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.ak_rotate),
            message = stringResource(R.string.ak_rotate_confirm, target.name),
            confirmLabel = stringResource(R.string.ak_rotate),
            onDismiss = { rotating = null },
            onConfirm = {
                busyId = target.id
                scope.launch {
                    runCatching { PanelApi.rotateApiKey(session, target.id) }
                        .onSuccess {
                            toast = msgRotated
                            revealed = it
                            load()
                        }
                        .onFailure { banner = ApiErrorMapper.friendly(context, it) }
                    busyId = null
                    rotating = null
                }
            }
        )
    }

    // ── حذف
    deleting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.ak_delete),
            message = stringResource(R.string.ak_delete_confirm, target.name),
            confirmLabel = stringResource(R.string.ak_delete),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                busyId = target.id
                scope.launch {
                    runCatching { PanelApi.deleteApiKey(session, target.id) }
                        .onSuccess { toast = msgDeleted; load() }
                        .onFailure { banner = ApiErrorMapper.friendly(context, it) }
                    busyId = null
                    deleting = null
                }
            }
        )
    }
}

/** یک ردیفِ کلید: نام، متنِ ماسک‌شده، وضعیت، انقضا و سه اکشن. */
@Composable
private fun ApiKeyRow(
    key: PanelApiKey,
    busy: Boolean,
    onToggle: (() -> Unit)?,
    onRotate: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val daysLeft = DateLogic.remainingDays(key.expireDate)
    val statusColor = when {
        key.isExpired || (daysLeft != null && daysLeft < 0) -> DsSemantic.Danger
        key.isActive -> DsSemantic.Success
        else -> theme.mutedColor
    }
    val statusText = when {
        key.isExpired -> stringResource(R.string.ak_status_expired)
        key.isActive -> stringResource(R.string.ak_status_active)
        else -> stringResource(R.string.ak_status_disabled)
    }
    val expiryText = when {
        key.expireDate == null -> stringResource(R.string.ak_no_expiry)
        daysLeft != null && daysLeft < 0 -> stringResource(R.string.ak_status_expired)
        daysLeft != null -> stringResource(R.string.ak_expires_in_fmt, daysLeft.toString())
        else -> key.expireDate.orEmpty()
    }

    Column(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(DsSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    key.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                MrmText(
                    key.trimmed, fontSize = 11.sp, color = theme.mutedColor,
                    isTechnical = true, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            PGBadge(statusText, color = statusColor)
        }
        if (!key.note.isNullOrBlank()) Text(
            key.note.orEmpty(), fontSize = 10.sp, color = theme.mutedColor, maxLines = 2
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            Text(expiryText, fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.weight(1f))
            if (busy) MrmBusyIndicator(color = theme.accentPrimary, size = 16.dp, strokeWidth = 2.dp)
            if (onRotate != null) InfraIconButton(
                icon = AppIcon.Random,
                description = stringResource(R.string.ak_rotate),
                tint = theme.accentPrimary,
                onClick = onRotate,
                enabled = !busy
            )
            if (onToggle != null) InfraIconButton(
                icon = if (key.isActive) AppIcon.StatusDisabled else AppIcon.StatusActive,
                description = stringResource(
                    if (key.isActive) R.string.ak_disable else R.string.ak_enable
                ),
                tint = theme.mutedColor,
                onClick = onToggle,
                enabled = !busy
            )
            if (onDelete != null) InfraIconButton(
                icon = AppIcon.Delete,
                description = stringResource(R.string.ak_delete),
                tint = DsSemantic.Danger,
                onClick = onDelete,
                enabled = !busy
            )
        }
    }
}

/**
 * دیالوگِ ساخت: نام (الزامی)، یادداشت و انقضا.
 *
 * انقضا با چیپ انتخاب می‌شود نه تاریخ‌شمار: کلیدِ API معمولاً برای یک دستگاه یا
 * یک اسکریپت ساخته می‌شود و «۳۰/۹۰/۳۶۵ روز یا هرگز» تمامِ نیازِ واقعی است.
 */
@Composable
private fun CreateApiKeyDialog(
    onDismiss: () -> Unit,
    submit: (name: String, note: String, days: Long?, onResult: (String) -> Unit) -> Unit
) {
    val theme = LocalThemeState.current
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val never = stringResource(R.string.ak_never)
    val labels = listOf(
        never,
        stringResource(R.string.ak_days_30),
        stringResource(R.string.ak_days_90),
        stringResource(R.string.ak_days_365)
    )
    val values = listOf("0", "30", "90", "365")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
        ) {
            Text(
                stringResource(R.string.ak_create), fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = theme.inkColor
            )
            Text(
                stringResource(R.string.ak_create_hint), fontSize = 11.sp, color = theme.mutedColor
            )
            InfraFieldLabel(stringResource(R.string.ak_name))
            CompactGlassField(
                value = name,
                onValueChange = { name = it; error = null },
                placeholder = stringResource(R.string.ak_name),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
            )
            InfraFieldLabel(stringResource(R.string.ak_note))
            CompactGlassField(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(R.string.ak_note),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
            )
            InfraFieldLabel(stringResource(R.string.ak_expiry))
            ChipSelector(values = values, labels = labels, selected = days, onSelect = { days = it })
            error?.let { InfraBanner(text = it, success = false) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
            ) {
                PGSecondaryButton(
                    text = stringResource(R.string.cd_close),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PGPrimaryButton(
                    // برچسبِ تأیید جدا از عنوانِ دیالوگ: «ساخت کلید» عنوان است،
                    // «بساز» کاری که با لمس انجام می‌شود (و تستِ Compose هم
                    // دیگر بینِ عنوان و دکمه اشتباه نمی‌کند).
                    text = stringResource(R.string.ak_create_action),
                    onClick = {
                        busy = true
                        submit(name.trim(), note.trim(), days.toLongOrNull()?.takeIf { it > 0 }) { message ->
                            busy = false
                            error = message
                        }
                    },
                    enabled = name.isNotBlank() && !busy,
                    loading = busy,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
