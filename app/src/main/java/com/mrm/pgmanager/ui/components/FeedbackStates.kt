package com.mrm.pgmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.feedback.AppFeedback
import com.mrm.pgmanager.ui.feedback.FeedbackEvent
import com.mrm.pgmanager.ui.feedback.FeedbackTone
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// میزبانِ بازخوردِ سراسری
// ─────────────────────────────────────────────────────────────────────────────

/** رنگِ نقطهٔ هر لحن. */
@Composable
private fun toneColor(tone: FeedbackTone) = when (tone) {
    FeedbackTone.Info -> LocalThemeState.current.accentPrimary
    FeedbackTone.Success -> DsSemantic.Success
    FeedbackTone.Error -> DsSemantic.Danger
}

/**
 * میزبانِ بازخوردِ سراسری — در ریشهٔ `MRMApp`، رویِ همه شاخه‌ها (لاگین/قفل/اصلی).
 *
 * پیامِ جاری با نقطهٔ رنگیِ لحن، متنِ کارتیِ وفادار به پنل و دکمهٔ بستنِ
 * بزرگ‌پسند نمایش داده می‌شود. خطاها ۵ ثانیه می‌مانند؛ بقیه ۳ ثانیه.
 * پیامِ جدید جایگزینِ پیامِ قبلی می‌شود (رفتارِ Toastهای قبلی).
 */
@Composable
fun AppFeedbackHost(modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<FeedbackEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    // بازبودنِ «جزئیات فنی» (فاز ۵.۲) — تا وقتی باز است، پیام بسته نمی‌شود.
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppFeedback.events.collect { event ->
            current = event
            expanded = false
            visible = true
        }
    }
    // تایمرِ بسته‌شدن: با هر پیامِ جدید از نو شروع می‌شود.
    LaunchedEffect(current, visible, expanded) {
        val event = current ?: return@LaunchedEffect
        // خواندنِ جزئیاتِ فنی نباید با ناپدیدشدنِ پیام قطع شود.
        if (!visible || expanded) return@LaunchedEffect
        // پیامِ دارای اکشن (بازگرداندن/تلاش دوباره) وقتِ بیشتری می‌گیرد تا کاربر
        // فرصتِ واکنش داشته باشد؛ بعد از انقضا اکشن هم از دست می‌رود.
        delay(
            when {
                event.onAction != null -> 6500L
                event.tone == FeedbackTone.Error -> 5000L
                else -> 3000L
            }
        )
        visible = false
    }

    Box(modifier) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            current?.let { event ->
                AppSnackbarSurface(
                    event = event,
                    onDismiss = { visible = false },
                    expanded = expanded,
                    onToggleDetail = { expanded = !expanded }
                )
            }
        }
    }
}

/** بدنهٔ کارتِ بازخورد. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSnackbarSurface(
    event: FeedbackEvent,
    onDismiss: () -> Unit,
    expanded: Boolean = false,
    onToggleDetail: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    val dismissDesc = stringResource(R.string.cd_dismiss)
    val detailHint = stringResource(R.string.err_technical_hint)
    Column(
        modifier = Modifier
            .padding(horizontal = DsSpacing.Xl)
            .fillMaxWidth()
            .clip(DsRadius.Lg)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .combinedClickable(
                // نگه‌داشتنِ پیام = بازشدنِ جزئیاتِ فنی؛ لمسِ کوتاه کاری نمی‌کند تا
                // بستنِ ناخواسته پیش نیاید (✕ همچنان کار می‌کند).
                onClick = {},
                onLongClick = { if (event.detail != null) onToggleDetail() }
            )
            .padding(horizontal = DsSpacing.Screen, vertical = DsSpacing.Lg)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // نقطهٔ رنگیِ لحن — صرفاً تزئینی (متنِ پیام خودش کافی است).
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(toneColor(event.tone))
        )
        Text(
            text = event.message,
            style = com.mrm.pgmanager.ui.designsystem.DsTextStyle.SnackMessage,
            color = theme.inkColor,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (event.actionLabel != null && event.onAction != null) {
            val action = event.onAction
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(DsRadius.Sm)
                    .clickable { onDismiss(); action() }
                    .padding(horizontal = DsSpacing.Md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    event.actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalThemeState.current.accentPrimary
                )
            }
        }
        Box(
            Modifier.padding(2.dp)
                .size(36.dp)
                .clip(DsRadius.Sm)
                .clickable { onDismiss() }
                .semantics { contentDescription = dismissDesc }
                ,
            contentAlignment = Alignment.Center
        ) {
            Text("✕", fontSize = 13.sp, color = theme.mutedColor)
        }
    }
        // راهنمای کشف‌پذیری + متنِ فنی. متنِ فنی برای صفحه‌خوان هم در
        // contentDescription خوانده می‌شود (نگه‌داشتنِ لمس برای TalkBack سخت است).
        val detail = event.detail
        if (!detail.isNullOrBlank()) {
            if (expanded) {
                Text(
                    stringResource(R.string.err_technical),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.mutedColor
                )
                Text(
                    detail,
                    fontSize = 11.sp,
                    color = theme.mutedColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            } else {
                Text(
                    detailHint,
                    fontSize = 10.sp,
                    color = theme.mutedColor,
                    modifier = Modifier.semantics { contentDescription = detailHint + ": " + detail }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// حالت‌های خطا / خالی — الگوی واحد برای همه صفحات
// ─────────────────────────────────────────────────────────────────────────────

/**
 * کارتِ خطای شبکه با دکمهٔ «تلاش مجدد» — الگوی واحدِ F2.
 * پیامِ خامِ استثنا (در صورت وجود) زیرِ عنوان نمایش داده می‌شود.
 */
@Composable
fun MrmErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    val theme = LocalThemeState.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsRadius.Lg)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(DsSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        RoundedAppIcon(AppIcon.Warning, tint = DsSemantic.Danger, size = 26.dp)
        Text(
            stringResource(R.string.load_failed),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = theme.inkColor
        )
        if (!message.isNullOrBlank()) {
            Text(
                message,
                fontSize = DsFont.Caption,
                color = theme.mutedColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        PGSecondaryButton(stringResource(R.string.retry), onClick = onRetry)
    }
}

/**
 * کارتِ حالتِ خالی — آیکون + عنوان + توضیح + اکشن‌های دلخواه.
 * (مثلاً «کاربری یافت نشد» + «پاک‌کردن فیلتر» + «کاربر جدید»)
 */
@Composable
fun MrmEmptyState(
    title: String,
    subtitle: String,
    icon: AppIcon,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val theme = LocalThemeState.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsRadius.Lg)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(DsSpacing.X4l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(DsRadius.Md)
                .background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md),
            contentAlignment = Alignment.Center
        ) {
            RoundedAppIcon(icon, tint = theme.mutedColor, size = 28.dp)
        }
        Text(title, fontWeight = FontWeight.Bold, color = theme.inkColor, fontSize = 15.sp)
        Text(
            subtitle,
            fontSize = DsFont.Caption,
            color = theme.mutedColor,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { actions() }
    }
}
