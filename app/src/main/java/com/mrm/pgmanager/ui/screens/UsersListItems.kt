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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 *  کارت‌ها و ردیف‌های فهرستِ کاربران
 *
 *  سه نمای فهرست (شبکه‌ای / فشرده / ریز) و نشان‌های کوچکی که داخلشان تکرار
 *  می‌شوند. قبلاً همه‌شان با خودِ صفحه در یک فایلِ ۱۵۰۰ خطی بودند و پیدا کردنِ
 *  اینکه یک بَج کجا رندر می‌شود، اسکرول می‌خواست.
 *
 *  دیده‌شدن از فایلِ صفحه: `internal` (نه `private`) چون هم‌پکیج‌اند ولی
 *  هم‌فایل نیستند.
 * ────────────────────────────────────────────────────────────────────────── */


@Composable
internal fun daysLeftText(expire: String?): String = com.mrm.pgmanager.utils.daysLeftText(expire)

/** نشانگر کوچک بدهکار (سکه قرمز) کنار نام کاربری */
@Composable
internal fun DebtorBadge(compact: Boolean = false) {
    val size = if (compact) 16.dp else 18.dp
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50))
            .background(GlassRed.copy(0.14f))
            .border(BorderStroke(0.8.dp, GlassRed.copy(0.32f)), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(AppIcon.Money, tint = GlassRed, size = if (compact) 10.dp else 11.dp)
    }
}

/** متنِ وضعیت برای کارت: اول وضعیت (غیرفعال/منقضی/محدود)، بعد روزِ مانده. */
@Composable
internal fun cardStatusText(user: PanelUser): String = when (user.status) {
    "disabled" -> stringResource(R.string.disabled)
    "expired" -> stringResource(R.string.expired)
    "limited" -> stringResource(R.string.limited)
    "on_hold" -> stringResource(R.string.on_hold_users)
    else -> daysLeftText(user.expire)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuxuryGridCard(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onCopySub: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}, debtorInfo: DebtorInfo? = null) {
    // PasarGuard-faithful: compact, no glass, thin progress, subtle status. Matches reference list screenshot.
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0L) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt().coerceIn(0,100) else 0
    val actualProgress = if (user.dataLimit > 0L) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val displayProgress = if (user.dataLimit == 0L) 0f else actualProgress
    val progressColor = when { user.dataLimit <= 0L -> Color(0xFF9CA3AF); progressPercent < 70 -> Color(0xFF16A34A); progressPercent < 90 -> Color(0xFFD97706); else -> Color(0xFFDC2626) }
    val shape = DsRadius.Lg

    // نمای گرید: کارت شیشه‌ای با سایهٔ نرم و مرز ظریف design system جدید.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) theme.accentPrimary.copy(alpha = 0.12f) else theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(alpha = 0.24f) else theme.borderColor), shape)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MrmText(
                            text = user.username,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            isTechnical = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (debtorInfo != null) DebtorBadge(compact = true)
                    }
                    OnlineOrLastSeen(user, fontSize = 10.sp, iconSize = 12.dp)
                }
                if (user.note?.isNotBlank() == true) Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(DsSemantic.Info.copy(0.16f)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Note, tint = DsSemantic.Info, size = 11.dp) }
            }
            MrmText(
                text = if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / " + stringResource(R.string.unlimited) else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}", 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                isTechnical = true
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${cardStatusText(user)}", fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.weight(1f), maxLines = 1)
                MrmText(
                    text = if (user.dataLimit == 0L) "∞" else "$progressPercent%", 
                    fontSize = 9.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = progressColor,
                    isTechnical = true
                )
            }
            // thin PG progress — 4dp, rounded, green fill, neutral track
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                if (displayProgress > 0f) Box(Modifier.fillMaxWidth(displayProgress).fillMaxHeight().clip(RoundedCornerShape(50)).background(progressColor))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconGridAction(AppIcon.Copy, contentDesc = stringResource(R.string.us_copy_sub_link)) { onCopySub(user) }
                IconGridAction(AppIcon.Qr, contentDesc = stringResource(R.string.us_show_qr)) { onQrClick(user) }
                Box(Modifier.height(24.dp).clip(DsRadius.Sm).background(if (user.isOnline) GlassGreen.copy(0.12f) else Color.Gray.copy(0.10f)).border(BorderStroke(DsBorder.Hairline, if (user.isOnline) GlassGreen.copy(0.18f) else Color.Gray.copy(0.12f)), DsRadius.Sm).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    OnlineOrLastSeen(user, fontSize = 10.sp, iconSize = 12.dp)
                }
                if (user.groupNames.isNotEmpty()) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF8B5CF6).copy(0.10f)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                        Text(user.groupNames.first(), fontSize = 10.sp, color = Color(0xFF8B5CF6), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * اگر کاربر آنلاین باشد فقط آیکونِ آنلاین نشان داده می‌شود (بدون کلمهٔ «آنلاین»)؛
 * در غیر این صورت زمانِ آخرین بازدید نمایش داده می‌شود.
 */
@Composable
internal fun OnlineOrLastSeen(
    user: PanelUser,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    iconSize: androidx.compose.ui.unit.Dp = 12.dp
) {
    val theme = LocalThemeState.current
    if (user.isOnline) {
        RoundedAppIcon(
            AppIcon.Wifi,
            contentDescription = stringResource(R.string.online),
            tint = GlassGreen,
            size = iconSize
        )
    } else {
        val seen = lastSeenShort(user.onlineAt, false)
        MrmText(
            text = if (seen.isBlank()) "—" else seen,
            fontSize = fontSize,
            color = theme.mutedColor,
            maxLines = 1,
            isTechnical = true
        )
    }
}

/**
 * وضعیت کاربر به‌صورت آیکونِ گرد — به‌جای بجِ متنی که فضای زیادی می‌گرفت.
 * هر وضعیت شکلِ آیکونِ متفاوتی دارد (نه فقط رنگ) تا برای کاربرانِ کوررنگ هم قابل تشخیص باشد.
 * توضیحِ متنی در `contentDescription` می‌ماند تا TalkBack بخواند و با نگه‌داشتنِ انگشت هم دیده شود.
 */
@Composable
internal fun UserStatusBadge(user: PanelUser, modifier: Modifier = Modifier, compact: Boolean = false) {
    val theme = LocalThemeState.current
    val (icon, color) = when (user.status) {
        "active" -> AppIcon.StatusActive to GlassGreen
        "disabled" -> AppIcon.StatusDisabled to Color(0xFF8A8A8A)
        "expired" -> AppIcon.StatusExpired to GlassRed
        "limited" -> AppIcon.StatusLimited to GlassAmber
        "on_hold" -> AppIcon.StatusOnHold to DsSemantic.Violet
        else -> AppIcon.StatusActive to theme.mutedColor
    }
    val label = when (user.status) {
        "active" -> stringResource(R.string.active)
        "disabled" -> stringResource(R.string.disabled)
        "expired" -> stringResource(R.string.expired)
        "limited" -> stringResource(R.string.limited)
        "on_hold" -> stringResource(R.string.on_hold_users)
        else -> cardStatusText(user)
    }
    // آیکونِ کلید (ToggleOn/Off) پهن است، پس کادر بیضی‌شکل است نه دایره تا آیکون بریده نشود
    val h = if (compact) 18.dp else 22.dp
    val w = if (compact) 24.dp else 28.dp
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .width(w)
            .height(h)
            .clip(shape)
            .background(color.copy(alpha = 0.13f))
            .border(BorderStroke(DsBorder.Hairline, color.copy(alpha = 0.28f)), shape),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, contentDescription = label, tint = color, size = if (compact) 13.dp else 15.dp)
    }
}

internal fun copySubscription(context: Context, user: PanelUser) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
    android.widget.Toast.makeText(context, context.getString(R.string.us_sub_copied), android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
internal fun IconCardAction(icon: AppIcon, modifier: Modifier = Modifier, contentDesc: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier.clip(DsRadius.Sm)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 16.dp)
    }
}

@Composable
internal fun IconRowAction(icon: AppIcon, modifier: Modifier = Modifier, contentDesc: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier.clip(DsRadius.Sm)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 13.dp)
    }
}

@Composable
internal fun IconGridAction(icon: AppIcon, contentDesc: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        RoundedAppIcon(icon, contentDescription = contentDesc, tint = theme.inkColor, size = 14.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuxuryCompactRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onCopySub: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}, debtorInfo: DebtorInfo? = null) {
    val theme = LocalThemeState.current
    val actualProgress = if (user.dataLimit > 0L) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val shownProgress = if (user.dataLimit > 0L) actualProgress else .035f
    val progressPercent = (actualProgress * 100).roundToInt()
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen
        progressPercent < 90 -> GlassAmber
        else -> GlassRed
    }
    val traffic = if (user.dataLimit == 0L) "${formatBytes(user.usedTraffic)} / " + stringResource(R.string.unlimited) else "${formatBytes(user.usedTraffic)} / ${formatBytes(user.dataLimit)}"
    val shape = DsRadius.Lg

    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) theme.accentPrimary.copy(alpha = 0.12f) else theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(alpha = 0.24f) else theme.borderColor), shape)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckboxIcon(selected = selected, onToggle = onSelectToggle)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MrmText(
                        user.username,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        isTechnical = true
                    )
                    OnlineOrLastSeen(user, fontSize = 10.sp, iconSize = 13.dp)
                }
                UserStatusBadge(user)
                if (debtorInfo != null) DebtorBadge()
                IconCardAction(AppIcon.Copy, Modifier.size(34.dp), contentDesc = stringResource(R.string.us_copy)) { onCopySub(user) }
                IconCardAction(AppIcon.Qr, Modifier.size(34.dp), contentDesc = "QR") { onQrClick(user) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.traffic_usage_label), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                    MrmText(traffic, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.remaining_credit), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor)
                    MrmText(daysLeftText(user.expire), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, isTechnical = false)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                    if (shownProgress > 0.01f) Box(Modifier.fillMaxWidth(shownProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(50)))
                }
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuxuryMicroRow(user: PanelUser, selected: Boolean = false, onSelectToggle: () -> Unit = {}, onClick: () -> Unit, onQrClick: (PanelUser) -> Unit = {}, onCopySub: (PanelUser) -> Unit = {}, onLongClick: (PanelUser) -> Unit = {}, debtorInfo: DebtorInfo? = null) {
    val theme = LocalThemeState.current
    val actualProgress = if (user.dataLimit > 0L) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else .035f
    val progressColor = when { user.dataLimit <= 0L || actualProgress < .70f -> GlassGreen; actualProgress < .90f -> GlassAmber; else -> GlassRed }
    val traffic = "${formatBytes(user.usedTraffic)}/${if (user.dataLimit == 0L) "∞" else formatBytes(user.dataLimit)}"
    val shape = DsRadius.Lg

    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) theme.accentPrimary.copy(alpha = 0.12f) else theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, if (selected) theme.accentPrimary.copy(alpha = 0.24f) else theme.borderColor), shape)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(user) })
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckboxIcon(selected = selected, onToggle = onSelectToggle)
            Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MrmText(user.username, fontSize = 11.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                OnlineOrLastSeen(user, fontSize = 10.sp, iconSize = 11.dp)
            }
            UserStatusBadge(user, compact = true)
            if (debtorInfo != null) DebtorBadge(compact = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MrmText(traffic, fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1, isTechnical = true)
                    MrmText(daysLeftText(user.expire), fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, isTechnical = false)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(if (LocalThemeState.current.isDark) Color.White.copy(0.12f) else Color(0xFFF3F4F6))) {
                    if (actualProgress > 0.01f) Box(Modifier.fillMaxWidth(actualProgress).fillMaxHeight().background(progressColor, RoundedCornerShape(50)))
                }
            }
            IconRowAction(AppIcon.Copy, Modifier.size(24.dp), contentDesc = stringResource(R.string.us_copy)) { onCopySub(user) }
            IconRowAction(AppIcon.Qr, Modifier.size(24.dp), contentDesc = "QR") { onQrClick(user) }
        }
    }
}
