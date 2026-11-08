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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.api.ApiKeyQuotaConflictException
import com.mrm.pgmanager.data.api.ApiKeyQuotaUnsupportedException
import com.mrm.pgmanager.data.api.ApiKeyWriteUnconfirmedException
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.CreatedApiKey
import com.mrm.pgmanager.data.model.ApiKeyQuota
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPageHeader
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
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorKind
import com.mrm.pgmanager.utils.ApiErrorMapper
import com.mrm.pgmanager.utils.DateLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    val cacheKey = remember(session) { PanelCache.apiKeysKey(session) }
    val canRead = AdminAccess.canFor(session, "api_keys", "read")
    var keys by remember(session, canRead) {
        mutableStateOf(if (canRead) PanelCache.get<List<PanelApiKey>>(cacheKey) ?: emptyList() else emptyList())
    }
    var loading by remember(session, canRead) {
        mutableStateOf(canRead && PanelCache.get<List<PanelApiKey>>(cacheKey) == null)
    }
    var loadError by remember(session, canRead) { mutableStateOf<String?>(null) }
    var loadRequest by remember(session) { mutableStateOf(0L) }
    var banner by remember(session, canRead) { mutableStateOf<String?>(null) }
    var toast by remember(session, canRead) { mutableStateOf<String?>(null) }
    var busyId by remember(session, canRead) { mutableStateOf<Int?>(null) }

    var creating by remember(session, canRead) { mutableStateOf(false) }
    var revealed by remember(session, canRead) { mutableStateOf<CreatedApiKey?>(null) }
    var rotating by remember(session, canRead) { mutableStateOf<PanelApiKey?>(null) }
    var deleting by remember(session, canRead) { mutableStateOf<PanelApiKey?>(null) }
    var editingQuota by remember(session, canRead) { mutableStateOf<PanelApiKey?>(null) }

    val canCreate = canRead && AdminAccess.canFor(session, "api_keys", "create")
    val canUpdate = canRead && AdminAccess.canFor(session, "api_keys", "update")
    val canDelete = canRead && AdminAccess.canFor(session, "api_keys", "delete")

    val msgRotated = stringResource(R.string.ak_rotated)
    val msgDeleted = stringResource(R.string.ak_deleted)
    val msgEnabled = stringResource(R.string.ak_enabled_msg)
    val msgDisabled = stringResource(R.string.ak_disabled_msg)
    val msgQuotaSaved = stringResource(R.string.ak_quota_saved)

    fun keyError(error: Throwable): String = when (error) {
        is ApiKeyQuotaConflictException -> context.getString(R.string.ak_quota_conflict)
        is ApiKeyQuotaUnsupportedException -> context.getString(R.string.ak_quota_unavailable)
        is ApiKeyWriteUnconfirmedException -> context.getString(R.string.ak_write_unconfirmed)
        else -> ApiErrorMapper.friendly(context, error)
    }

    suspend fun load() {
        if (!AdminAccess.canFor(session, "api_keys", "read")) return
        val request = ++loadRequest
        try {
            val result = PanelApi.apiKeys(session)
            currentCoroutineContext().ensureActive()
            if (request == loadRequest && AdminAccess.canFor(session, "api_keys", "read")) {
                keys = result
                loadError = null
                banner = null
                PanelCache.put(cacheKey, result)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (request == loadRequest && AdminAccess.canFor(session, "api_keys", "read")) {
                val kind = ApiErrorMapper.kindOf(error)
                if (kind == ApiErrorKind.FORBIDDEN || kind == ApiErrorKind.UNAUTHORIZED) {
                    keys = emptyList()
                    PanelCache.invalidate(cacheKey)
                }
                val message = ApiErrorMapper.friendly(context, error)
                loadError = message
                if (keys.isNotEmpty()) banner = message
            }
        } finally {
            if (request == loadRequest) loading = false
        }
    }

    LaunchedEffect(session, canRead) {
        loadRequest++
        if (!canRead) PanelCache.invalidate(cacheKey)
        else if (!PanelCache.isFresh(cacheKey)) load()
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    fun toggle(key: PanelApiKey) {
        if (!AdminAccess.canFor(session, "api_keys", "update")) return
        busyId = key.id
        scope.launch {
            if (!AdminAccess.canFor(session, "api_keys", "update")) {
                busyId = null
                return@launch
            }
            runCatching { PanelApi.setApiKeyDisabled(session, key.id, disabled = key.isEnabled) }
                .onSuccess {
                    currentCoroutineContext().ensureActive()
                    if (AdminAccess.canFor(session, "api_keys", "read")) {
                        toast = if (key.isEnabled) msgDisabled else msgEnabled
                        load()
                    }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (AdminAccess.canFor(session, "api_keys", "read")) banner = ApiErrorMapper.friendly(context, it)
                }
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
        PGPageHeader(
            title = stringResource(R.string.ak_title),
            subtitle = stringResource(R.string.ak_subtitle),
            onBack = onBack
        ) {
            if (keys.isNotEmpty()) PGBadge("${keys.size}")
            if (canRead) InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = { scope.launch { loading = keys.isEmpty(); load() } }
            )
            if (canCreate) InfraIconButton(
                icon = AppIcon.Add,
                description = stringResource(R.string.ak_create),
                tint = theme.accentTextColor,
                onClick = { creating = true }
            )
        }

        if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
        banner?.let { InfraBanner(text = it, success = false) { banner = null } }

        when {
            !canRead -> Text(stringResource(R.string.err_forbidden_panel), color = theme.mutedColor)
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
                    // نوارِ ناوبریِ پایین: این صفحه تمام‌صفحه است و نوارِ تبِ پایین
                    // ندارد، پس خودِ لیست باید insetِ پایین را کم کند — وگرنه آخرین
                    // ردیف زیرِ پیلِ حرکتِ انگشت می‌ماند (گزارشِ همان بریدگیِ پایین).
                    modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Md),
                    contentPadding = PaddingValues(bottom = DsSpacing.Xl)
                ) {
                    itemsIndexed(keys, key = { _, key -> key.id }) { index, key ->
                        Box(Modifier.listIntro(intro, index)) {
                            ApiKeyRow(
                                key = key,
                                busy = busyId == key.id,
                                onToggle = if (canUpdate) ({ toggle(key) }) else null,
                                onRotate = if (canDelete) ({ rotating = key }) else null,
                                onDelete = if (canDelete) ({ deleting = key }) else null,
                                onEditQuota = if (canUpdate && key.supportsRequestLimits) ({ editingQuota = key }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    // ── ساخت
    if (creating) CreateApiKeyDialog(
        quotaSupported = keys.any { it.supportsRequestLimits },
        onDismiss = { creating = false },
        // نامِ پارامتر `onCreate` نبود: با `override fun onCreate(savedInstanceState)`
        // اکتیویتی هم‌نام می‌شد و ابزارِ `kt-lambdaargs` این فراخوانی را با آن امضا
        // مقایسه می‌کرد؛ نامِ یکتا هم خواناست هم تله را برمی‌دارد.
        submit = { name, note, days, quota, onResult ->
            scope.launch {
                if (!AdminAccess.canFor(session, "api_keys", "create")) return@launch
                runCatching { PanelApi.createApiKey(session, name, note, days, quota) }
                    .onSuccess {
                        currentCoroutineContext().ensureActive()
                        if (AdminAccess.canFor(session, "api_keys", "read")) {
                            creating = false
                            revealed = it
                            load()
                        }
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        if (AdminAccess.canFor(session, "api_keys", "read")) onResult(keyError(e))
                    }
            }
        }
    )

    editingQuota?.let { target ->
        ApiKeyQuotaDialog(
            key = target,
            onDismiss = { editingQuota = null },
            onSave = { quota, onError ->
                scope.launch {
                    if (!AdminAccess.canFor(session, "api_keys", "update")) return@launch
                    runCatching { PanelApi.updateApiKeyQuota(session, target, quota) }
                        .onSuccess {
                            currentCoroutineContext().ensureActive()
                            if (AdminAccess.canFor(session, "api_keys", "read")) {
                                editingQuota = null
                                toast = msgQuotaSaved
                                load()
                            }
                        }
                        .onFailure {
                            if (it is CancellationException) throw it
                            if (AdminAccess.canFor(session, "api_keys", "read")) onError(keyError(it))
                        }
                }
            }
        )
    }

    // ── نمایشِ یک‌بارهٔ متنِ کلید (پس از ساخت یا چرخش)
    // قابِ دیالوگ اینجا است و محتوا بیرون (`ApiKeyRevealContent`)، تا همان محتوا در
    // تستِ Compose بدونِ `Dialog` رندر شود: در Robolectric پنجرهٔ دیالوگ هرگز idle
    // گزارش نمی‌کند و `performClick` روی دکمهٔ بازکردنش کلاس را ۶۰ ثانیه می‌خواباند
    // (`docs/CI.md` §۳).
    revealed?.let { fresh ->
        AppDialog(onDismissRequest = { revealed = null }) {
            // The full API key is a secret; protect this dialog's own window as well.
            com.mrm.pgmanager.ui.designsystem.SecureContent {
                ApiKeyRevealContent(fresh = fresh, onDismiss = { revealed = null })
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
                    if (!AdminAccess.canFor(session, "api_keys", "delete")) {
                        busyId = null
                        return@launch
                    }
                    runCatching { PanelApi.rotateApiKey(session, target.id) }
                        .onSuccess {
                            currentCoroutineContext().ensureActive()
                            if (AdminAccess.canFor(session, "api_keys", "read")) {
                                toast = msgRotated
                                revealed = it
                                load()
                            }
                        }
                        .onFailure {
                            if (it is CancellationException) throw it
                            if (AdminAccess.canFor(session, "api_keys", "read")) banner = ApiErrorMapper.friendly(context, it)
                        }
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
                    if (!AdminAccess.canFor(session, "api_keys", "delete")) {
                        busyId = null
                        return@launch
                    }
                    runCatching { PanelApi.deleteApiKey(session, target.id) }
                        .onSuccess {
                            currentCoroutineContext().ensureActive()
                            if (AdminAccess.canFor(session, "api_keys", "read")) { toast = msgDeleted; load() }
                        }
                        .onFailure {
                            if (it is CancellationException) throw it
                            if (AdminAccess.canFor(session, "api_keys", "read")) banner = ApiErrorMapper.friendly(context, it)
                        }
                    busyId = null
                    deleting = null
                }
            }
        )
    }
}

/**
 * بدنهٔ دیالوگِ «یک‌بار نمایش» — عمداً بیرونِ `Dialog` زندگی می‌کند تا همین گره‌ها در
 * `ApiKeyRevealContentTest` آزموده شوند.
 *
 * دو قرارداد اینجا نباید جابه‌جا شود: متنِ کاملِ کلید فقط همین‌جا دیده می‌شود
 * (پنل بعد از بستنِ این دیالوگ دیگر `api_key` کامل را نمی‌دهد — فهرست فقط
 * `api_key_trimed` است) و «کپی» همان `SettingsInfoRow` خودش است، نه دکمهٔ جدا.
 */
@Composable
internal fun ApiKeyRevealContent(fresh: CreatedApiKey, onDismiss: () -> Unit) {
    val theme = LocalThemeState.current
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
        if (!fresh.requestLimitConfirmed) {
            InfraBanner(text = stringResource(R.string.ak_quota_not_confirmed), success = false)
        }
        SettingsInfoRow(
            label = fresh.item.name,
            value = fresh.key,
            copyable = true
        )
        PGPrimaryButton(
            text = stringResource(R.string.ak_reveal_done),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
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
    onDelete: (() -> Unit)?,
    onEditQuota: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val daysLeft = DateLogic.remainingDays(key.expireDate)
    val statusColor = when {
        key.isExpired || (daysLeft != null && daysLeft < 0) -> DsSemantic.Danger
        key.isEnabled && key.isRequestLimitReached -> DsSemantic.Danger
        key.isActive -> DsSemantic.Success
        else -> theme.mutedColor
    }
    val statusText = when {
        key.isExpired -> stringResource(R.string.ak_status_expired)
        key.isEnabled && key.isRequestLimitReached -> stringResource(R.string.ak_status_limit_reached)
        key.isEnabled && key.requestLimitUnknown -> stringResource(R.string.ak_status_limit_unknown)
        key.isEnabled && key.isRequestCountUnknown -> stringResource(R.string.ak_status_count_unknown)
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
        key.requestCount?.let { count ->
            val summary = key.maxRequests?.let { limit ->
                stringResource(R.string.ak_request_usage_limited, count.toString(), limit.toString())
            } ?: stringResource(R.string.ak_request_usage, count.toString())
            Text(summary, fontSize = 10.sp, color = theme.mutedColor)
        }
        if (key.deleteOnLimit == true) {
            Text(stringResource(R.string.ak_delete_on_limit), fontSize = 10.sp, color = theme.mutedColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            Text(expiryText, fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.weight(1f))
            if (busy) MrmBusyIndicator(color = theme.accentPrimary, size = 16.dp, strokeWidth = 2.dp)
            if (onEditQuota != null) InfraIconButton(
                icon = AppIcon.Tune,
                description = stringResource(R.string.ak_edit_quota),
                tint = theme.accentPrimary,
                onClick = onEditQuota,
                enabled = !busy
            )
            if (onRotate != null) InfraIconButton(
                icon = AppIcon.Random,
                description = stringResource(R.string.ak_rotate),
                tint = theme.accentPrimary,
                onClick = onRotate,
                enabled = !busy
            )
            if (onToggle != null) InfraIconButton(
                icon = if (key.isEnabled) AppIcon.StatusDisabled else AppIcon.StatusActive,
                description = stringResource(
                    if (key.isEnabled) R.string.ak_disable else R.string.ak_enable
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
    quotaSupported: Boolean,
    onDismiss: () -> Unit,
    submit: (name: String, note: String, days: Long?, quota: ApiKeyQuota?, onResult: (String) -> Unit) -> Unit
) {
    val theme = LocalThemeState.current
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("0") }
    var limit by remember { mutableStateOf("") }
    var deleteOnLimit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val invalidQuota = stringResource(R.string.ak_quota_invalid)
    val never = stringResource(R.string.ak_never)
    val labels = listOf(
        never,
        stringResource(R.string.ak_days_30),
        stringResource(R.string.ak_days_90),
        stringResource(R.string.ak_days_365)
    )
    val values = listOf("0", "30", "90", "365")

    AppDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .verticalScroll(rememberScrollState()).imePadding().padding(DsSpacing.Xl),
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
            if (quotaSupported) {
                InfraFieldLabel(stringResource(R.string.ak_max_requests))
                CompactGlassField(value = limit, onValueChange = { limit = it.trim(); error = null },
                    placeholder = stringResource(R.string.ak_quota_unlimited),
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                Text(stringResource(R.string.ak_quota_hint), fontSize = 10.sp, color = theme.mutedColor)
                SettingsSwitchRow(title = stringResource(R.string.ak_delete_on_limit),
                    subtitle = stringResource(R.string.ak_delete_on_limit_hint),
                    checked = deleteOnLimit, enabled = limit.isNotBlank(),
                    onChange = { deleteOnLimit = it })
            } else {
                Text(stringResource(R.string.ak_quota_capability_hint), fontSize = 10.sp, color = theme.mutedColor)
            }
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
                        val parsed = limit.toLongOrNull()
                        val quota = if (quotaSupported && parsed != null) ApiKeyQuota(parsed, deleteOnLimit) else null
                        if (quotaSupported && limit.isNotBlank() && (quota == null || !quota.isValid())) {
                            error = invalidQuota
                        } else {
                            busy = true
                            submit(name.trim(), note.trim(), days.toLongOrNull()?.takeIf { it > 0 }, quota) { message ->
                                busy = false
                                error = message
                            }
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
