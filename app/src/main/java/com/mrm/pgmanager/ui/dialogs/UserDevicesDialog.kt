package com.mrm.pgmanager.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserDevice
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.feedback.AppFeedback
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorMapper
import com.mrm.pgmanager.utils.lastSeenText
import kotlinx.coroutines.launch

/**
 * **مدیریتِ دستگاه‌های یک کاربر** — `GET /api/user/{id}/hwids` (مجوزِ `hwids.read`)
 * به‌همراه حذفِ تک‌تک (`hwids.delete`) و «حذفِ همه».
 *
 * ### چرا دیالوگِ مستقل، نه دو ردیف در ورقهٔ جزئیات
 * ورقهٔ جزئیات فقط **دو** دستگاهِ اول را نشان می‌داد و راهی برای دیدنِ بقیه نداشت؛
 * یعنی کاربری با پنج دستگاه عملاً قابلِ مدیریت نبود. سقفِ دستگاه (`hwid_limit`)
 * هم وقتی پر می‌شود تنها کاری که ادمین می‌تواند بکند همین‌جاست: یک دستگاه را
 * فراموش کند تا کاربرِ عوض‌کرده‌گوشی بتواند وصل شود.
 *
 * ### چیزهایی که عمداً هستند
 *  • `last_used_at` (که در ورقهٔ قبلی خوانده می‌شد ولی نمایش داده نمی‌شد) —
 *    همان چیزی که «کدام دستگاه مُرده است» را جواب می‌دهد.
 *  • خطاها با پیامِ خوانا نشان داده می‌شوند؛ قبلاً `runCatching` بی‌صدا بود و
 *    ادمین فکر می‌کرد حذف شده در حالی که پنل ۴۰۳/۴۰۴ داده بود.
 *  • هر اکشنِ مخرب پشتِ `hwids.delete` است؛ نسخهٔ قبلی بدونِ بررسیِ مجوز
 *    دکمه‌ها را نشان می‌داد.
 */
@Composable
fun UserDevicesDialog(
    session: Session,
    user: PanelUser,
    onDismiss: () -> Unit,
    /** پس از هر تغییر موفق (برای خالی‌کردنِ کشِ ردیف‌ها در صفحهٔ زیر). */
    onChanged: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember(user.id) { mutableStateOf<List<UserDevice>>(emptyList()) }
    var loading by remember(user.id) { mutableStateOf(true) }
    var loadError by remember(user.id) { mutableStateOf<String?>(null) }
    var busyHwid by remember(user.id) { mutableStateOf<String?>(null) }
    var resetting by remember(user.id) { mutableStateOf(false) }
    var resetConfirm by remember(user.id) { mutableStateOf(false) }
    var banner by remember(user.id) { mutableStateOf<String?>(null) }

    val canDelete = AdminAccess.can("hwids", "delete")
    val limit = user.hwidLimit

    suspend fun load() {
        runCatching { PanelApi.userDevices(session, user.id) }
            .onSuccess { devices = it; loadError = null }
            .onFailure { loadError = ApiErrorMapper.friendly(context, it) }
        loading = false
    }

    LaunchedEffect(user.id) { load() }

    fun forget(device: UserDevice) {
        busyHwid = device.hwid
        banner = null
        scope.launch {
            runCatching { PanelApi.deleteUserDevice(session, user.id, device.hwid) }
                .onSuccess {
                    // حذفِ موضعی: صبرکردن برای رفرشِ کامل لازم نیست و ردیف
                    // همان لحظه می‌رود.
                    devices = devices.filterNot { it.hwid == device.hwid }
                    AppFeedback.success(context.getString(R.string.ud_device_removed))
                    onChanged()
                }
                .onFailure { banner = ApiErrorMapper.friendly(context, it) }
            busyHwid = null
        }
    }

    fun forgetAll() {
        resetting = true
        banner = null
        scope.launch {
            runCatching { PanelApi.resetUserDevices(session, user.id) }
                .onSuccess {
                    devices = emptyList()
                    AppFeedback.success(context.getString(R.string.ud_devices_reset_done))
                    onChanged()
                }
                .onFailure { banner = ApiErrorMapper.friendly(context, it) }
            resetting = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp)
                .clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                RoundedAppIcon(AppIcon.Device, tint = theme.accentPrimary, size = 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ud_devices), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text(
                        user.username, fontSize = 11.sp, color = theme.mutedColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    stringResource(R.string.ud_devices_count, devices.size, limit ?: 0),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor
                )
            }

            banner?.let {
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Md)
                        .background(DsSemantic.Danger.copy(0.12f))
                        .border(BorderStroke(DsBorder.Hairline, DsSemantic.Danger.copy(0.26f)), DsRadius.Md)
                        .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(it, fontSize = 11.sp, color = DsSemantic.Danger, modifier = Modifier.weight(1f))
                }
            }

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = theme.accentPrimary) }

                loadError != null -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
                ) {
                    Text(
                        loadError.orEmpty(), fontSize = 11.sp,
                        color = theme.mutedColor, maxLines = 3
                    )
                    PGSecondaryButton(
                        text = stringResource(R.string.retry),
                        onClick = { loading = true; scope.launch { load() } }
                    )
                }

                devices.isEmpty() -> Text(
                    stringResource(R.string.ud_devices_empty),
                    fontSize = 12.sp, color = theme.mutedColor
                )

                else -> LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(devices, key = { it.hwid }) { device ->
                        DeviceRow(
                            device = device,
                            busy = busyHwid == device.hwid,
                            canDelete = canDelete && !resetting,
                            onForget = { forget(device) }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
            ) {
                PGSecondaryButton(
                    text = stringResource(R.string.ud_devices_reset),
                    onClick = { resetConfirm = true },
                    enabled = canDelete && devices.isNotEmpty() && !resetting && busyHwid == null,
                    loading = resetting,
                    modifier = Modifier.weight(1f)
                )
                PGSecondaryButton(
                    text = stringResource(R.string.cd_close),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (resetConfirm) {
        ConfirmActionDialog(
            title = stringResource(R.string.ud_devices_reset_title),
            message = stringResource(R.string.ud_devices_reset_msg),
            confirmLabel = stringResource(R.string.ud_devices_reset),
            danger = true,
            onDismiss = { resetConfirm = false },
            onConfirm = { resetConfirm = false; forgetAll() }
        )
    }
}

/** یک دستگاه: مدل/سیستم‌عامل، شناسهٔ فنیِ کوتاه، آخرین استفاده و دکمهٔ حذف. */
@Composable
private fun DeviceRow(
    device: UserDevice,
    busy: Boolean,
    canDelete: Boolean,
    onForget: () -> Unit
) {
    val theme = LocalThemeState.current
    val title = listOfNotNull(device.deviceModel, device.deviceOs)
        .joinToString(" · ")
        .ifBlank { device.hwid.take(12) }
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
            .padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            MrmText(
                device.hwid, fontSize = 10.sp, color = theme.mutedColor,
                isTechnical = true, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            device.lastUsedAt?.let {
                Text(
                    stringResource(R.string.ud_device_last_used, lastSeenText(it, false)),
                    fontSize = 10.sp, color = theme.mutedColor, maxLines = 1
                )
            }
        }
        if (busy) CircularProgressIndicator(
            modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = theme.accentPrimary
        )
        if (canDelete) Box(
            Modifier.heightIn(min = 40.dp).clip(DsRadius.Sm).clickable { onForget() }
                .background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, DsSemantic.Danger.copy(0.2f)), DsRadius.Sm)
                .pressScale(0.9f)
                .padding(horizontal = DsSpacing.Sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.ud_device_forget), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = DsSemantic.Danger
            )
        }
    }
}
