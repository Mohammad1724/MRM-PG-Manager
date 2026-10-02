package com.mrm.pgmanager.ui.components

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.dialogs.SettingsCard
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.NotificationPermission

/**
 * وضعیتِ زندهٔ مجوزِ اعلان برای Compose — فاز ۸.۳.
 *
 * @property granted مجوز همین حالا داده شده؟
 * @property canAskAgain اگر false باشد، دیالوگِ سیستمی دوباره ظاهر نمی‌شود و تنها راه
 *   «تنظیماتِ اعلان‌ها» است؛ همین تفاوت، متنِ دکمهٔ اصلی را تعیین می‌کند.
 */
data class NotificationPermissionState(
    val granted: Boolean,
    val canAskAgain: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit
)

/**
 * وضعیتِ مجوز را می‌خواند، درخواست را راه می‌اندازد و با **هر بازگشت به اپ**
 * (ON_RESUME) دوباره می‌خواند؛ وگرنه کاربر از تنظیماتِ اندروید برمی‌گشت و کارت
 * همچنان «خاموش» می‌ماند تا اپ را کامل ببندد.
 *
 * `LifecycleOwner` را از خودِ context می‌گیریم (MainActivity یک FragmentActivity
 * است) تا به `LocalLifecycleOwner` — که در Compose 1.7 از ui به lifecycle منتقل
 * شده — وابسته نباشیم.
 */
@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = context as? LifecycleOwner

    var granted by remember { mutableStateOf(NotificationPermission.granted(context)) }
    var canAsk by remember { mutableStateOf(NotificationPermission.canAskAgain(activity)) }

    fun refresh() {
        granted = NotificationPermission.granted(context)
        canAsk = NotificationPermission.canAskAgain(activity)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    return NotificationPermissionState(
        granted = granted,
        canAskAgain = canAsk,
        request = {
            if (Build.VERSION.SDK_INT >= 33 && canAsk) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationPermission.openSettings(context)
            }
        },
        openSettings = { NotificationPermission.openSettings(context) }
    )
}

/**
 * کارتِ وضعیتِ مجوز برای «تنظیمات ← اعلان‌ها».
 * اگر مجوز داده شده باشد چیزی رندر نمی‌کند تا صفحه شلوغ نشود.
 */
@Composable
fun NotificationPermissionCard(state: NotificationPermissionState) {
    if (state.granted) return
    val theme = LocalThemeState.current
    SettingsCard(title = stringResource(R.string.notif_perm_off_title), icon = AppIcon.Bell) {
        Text(
            stringResource(R.string.notif_perm_off_body),
            fontSize = 12.sp,
            color = theme.mutedColor,
            lineHeight = 18.sp
        )
        PGPrimaryButton(
            text = if (state.canAskAgain) {
                stringResource(R.string.notif_perm_enable)
            } else {
                stringResource(R.string.notif_perm_settings)
            },
            onClick = { state.request() },
            modifier = Modifier.fillMaxWidth(),
            icon = null
        )
    }
}

/**
 * توضیحِ درون‌برنامه‌ای **پیش از** دیالوگِ سیستم.
 *
 * اگر کاربر دو بار «رد» بزند، اندروید دیگر دیالوگ نشان نمی‌دهد؛ پس ارزش دارد اول
 * خودمان بگوییم «چرا»، بعد مجوز را بخواهیم. با ردِ قطعی، دکمهٔ اصلی به تنظیماتِ
 * سیستم می‌برد (درخواستِ دوباره بی‌اثر است).
 */
@Composable
fun NotificationPermissionDialog(
    state: NotificationPermissionState,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    val title = stringResource(R.string.notif_perm_title)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .paneTitle(title)
                .clip(DsRadius.Xxl)
                .background(theme.cardSurfaceColor)
                .padding(DsSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
            ) {
                RoundedAppIcon(AppIcon.Bell, tint = theme.inkColor, size = 22.dp)
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.inkColor
                )
            }
            Text(
                stringResource(R.string.notif_perm_body),
                fontSize = 12.sp,
                color = theme.mutedColor,
                lineHeight = 19.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                PGSecondaryButton(
                    text = stringResource(R.string.notif_perm_later),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PGPrimaryButton(
                    text = if (state.canAskAgain) {
                        stringResource(R.string.notif_perm_enable)
                    } else {
                        stringResource(R.string.notif_perm_settings)
                    },
                    onClick = onAllow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
