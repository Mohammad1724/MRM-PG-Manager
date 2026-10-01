package com.mrm.pgmanager.ui.components

import androidx.compose.ui.res.stringResource

import com.mrm.pgmanager.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import com.mrm.pgmanager.ui.designsystem.DsAnim
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsNeutral
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.spinWhile
import com.mrm.pgmanager.ui.theme.LocalThemeState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// ─────────────────────────────────────────────────────────────
//  PGCard — white card with subtle border, 12dp radius, tiny shadow
// ─────────────────────────────────────────────────────────────
@Composable
fun PGCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalThemeState.current
    val shape = DsRadius.Lg
    val bg = t.cardSurfaceColor
    val border = t.borderColor
    val m = if (onClick != null) modifier.clip(shape).background(bg).border(BorderStroke(DsBorder.Hairline, border), shape).clickable(onClick = onClick) else modifier.clip(shape).background(bg).border(BorderStroke(DsBorder.Hairline, border), shape)
    Column(modifier = m.padding(DsSpacing.Card), content = content)
}

// ─────────────────────────────────────────────────────────────
//  PGStatCard — compact stat card (CPU, RAM, etc.)
// ─────────────────────────────────────────────────────────────
@Composable
fun PGStatCard(
    label: String,
    value: String,
    icon: AppIcon,
    modifier: Modifier = Modifier,
    valueSub: String? = null,
    accent: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null
) {
    val t = LocalThemeState.current
    val resolvedAccent = if (accent == Color.Unspecified) t.accentPrimary else accent
    val shape = DsRadius.Lg
    val isGold = resolvedAccent == DsAccent.Gold || resolvedAccent == t.accentPrimary
    val iconBg = if (isGold) { if (t.isDark) t.accentPrimary.copy(0.15f) else t.accentPrimary.copy(0.10f) } else resolvedAccent.copy(0.10f)
    val iconBorder = if (isGold) { if (t.isDark) t.accentPrimary.copy(0.25f) else t.accentPrimary.copy(0.22f) } else resolvedAccent.copy(0.18f)
    val iconTint = if (isGold) { t.accentPrimary } else resolvedAccent
    Column(
        modifier
            .height(92.dp)
            .clip(shape)
            .background(t.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, t.borderColor), shape)
            .padding(DsSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(28.dp).clip(DsRadius.Sm)
                    .background(iconBg)
                    .border(BorderStroke(DsBorder.Hairline, iconBorder), DsRadius.Sm),
                contentAlignment = Alignment.Center
            ) {
                RoundedAppIcon(icon, tint = iconTint, size = 15.dp)
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = t.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
        }
        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
            TechnicalContainer {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (valueSub != null) {
                Text(valueSub, fontSize = 11.sp, color = t.mutedLightColor, maxLines = 1)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PGScreenHeader — سربرگِ مشترکِ صفحه‌های اصلی
//
//  چرا مشترک شد: پنج صفحه هرکدام سربرگِ خودشان را داشتند و با هر تغییر، یکی
//  عقب می‌ماند. صفحهٔ کاربران کاملاً از بقیه جدا افتاده بود — بدون کارت و
//  حاشیه، دکمه‌های ۳۲dp به‌جای ۳۴dp، اسپینرِ پرشی به‌جای آیکونِ چرخان، و عنوانِ
//  «Users» که اصلاً ترجمه نمی‌شد. حالا یک تعریف داریم و پنج مصرف.
// ─────────────────────────────────────────────────────────────
@Composable
fun PGScreenHeader(
    title: String,
    subtitle: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsLabel: String,
    refreshLabel: String,
    badge: @Composable (() -> Unit)? = null
) {
    val t = LocalThemeState.current
    Row(
        modifier.fillMaxWidth().clip(DsRadius.Lg).background(t.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, t.borderColor), DsRadius.Lg)
            .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
            ) {
                Text(
                    title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.inkColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (badge != null) badge()
            }
            Text(
                subtitle, fontSize = 11.sp, color = t.mutedColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(t.searchBgColor)
                    .border(BorderStroke(1.dp, t.borderColor), RoundedCornerShape(8.dp))
                    .semantics { contentDescription = refreshLabel }
                    .pressScale(0.92f)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                // آیکون خودش می‌چرخد؛ جایگزین‌کردنش با اسپینر پرش داشت.
                RoundedAppIcon(
                    AppIcon.Refresh,
                    tint = if (refreshing) t.accentPrimary else t.mutedColor,
                    size = 16.dp,
                    modifier = Modifier.spinWhile(refreshing)
                )
            }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(t.searchBgColor)
                    .border(BorderStroke(1.dp, t.borderColor), RoundedCornerShape(8.dp))
                    .semantics { contentDescription = settingsLabel }
                    .pressScale(0.92f)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Settings, tint = t.mutedColor, size = 16.dp) }
        }
    }
}

@Composable
fun PGBadge(text: String, color: Color = Color.Unspecified) {
    val t = LocalThemeState.current
    val resolvedColor = if (color == Color.Unspecified) t.accentPrimary else color
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(resolvedColor.copy(0.12f))
            .border(BorderStroke(0.5.dp, resolvedColor.copy(0.18f)), RoundedCornerShape(6.dp))
            .padding(horizontal = DsSpacing.Sm, vertical = DsSpacing.Xxs)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = resolvedColor)
    }
}

// ─────────────────────────────────────────────────────────────
//  PGSectionHeader — small title row
// ─────────────────────────────────────────────────────────────
@Composable
fun PGSectionHeader(title: String, icon: AppIcon? = null, action: @Composable (() -> Unit)? = null) {
    val t = LocalThemeState.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            if (icon != null) {
                RoundedAppIcon(icon, tint = DsAccent.GoldDeep, size = 14.dp)
            }
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.inkColor)
        }
        if (action != null) action()
    }
}

// ─────────────────────────────────────────────────────────────
//  PGPrimaryButton / PGSecondaryButton — همان MrmButton در اندازهٔ فشرده
//  (کپسولِ شیشه‌ای؛ قبلاً نسخهٔ جداگانه‌ای با پرکنندهٔ تختِ زرد بودند)
// ─────────────────────────────────────────────────────────────
@Composable
fun PGPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon? = AppIcon.Check, enabled: Boolean = true, loading: Boolean = false) {
    MrmButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, loading = loading, icon = icon, style = MrmButtonStyle.Primary, compact = true)
}

@Composable
fun PGSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon? = null) {
    MrmButton(text = text, onClick = onClick, modifier = modifier, icon = icon, style = MrmButtonStyle.Secondary, compact = true)
}

@Composable
fun PGDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    MrmButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, style = MrmButtonStyle.Danger, compact = true)
}

/** دکمهٔ شناورِ ساخت — همان پیاده‌سازی `MrmFab` با نامِ خانوادهٔ PG. */
@Composable
fun PGFAB(icon: AppIcon, contentDescription: String, modifier: Modifier = Modifier, visible: Boolean = true, onClick: () -> Unit) =
    MrmFab(icon = icon, contentDescription = contentDescription, modifier = modifier, visible = visible, onClick = onClick)

// ─────────────────────────────────────────────────────────────
//  PGSearchBar — light gray bg, subtle border, rounded
// ─────────────────────────────────────────────────────────────
@Composable
fun PGSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String = stringResource(R.string.search_cd), modifier: Modifier = Modifier) {
    val t = LocalThemeState.current
    val shape = DsRadius.Md
    Box(
        modifier.fillMaxWidth().height(DsComponent.SearchBar).clip(shape).background(t.searchBgColor).border(BorderStroke(DsBorder.Hairline, t.borderColor), shape).padding(horizontal = DsSpacing.FieldHorizontal),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), modifier = Modifier.fillMaxWidth()) {
            RoundedAppIcon(AppIcon.Search, tint = t.mutedColor, size = 16.dp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = t.inkColor),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(placeholder, fontSize = 13.sp, color = t.mutedLightColor)
                    inner()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PGProgressBar — thin rounded track, green fill
// ─────────────────────────────────────────────────────────────
/**
 * نوارِ مصرفِ نازک. پُرشدنش انیمیت می‌شود (تغییرِ مقدار بعد از تمدید/ریست به‌جای
 * پرش، سُر می‌خورد) و چون فقط در فازِ draw کشیده می‌شود، هر فریمِ انیمیشن
 * نه recomposition دارد نه relayout — برای صدها ردیفِ لیست مهم است.
 * از راست‌چین پیروی می‌کند (در RTL از راست پُر می‌شود).
 */
@Composable
fun PGProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color? = null, fill: Color = DsSemantic.Success) {
    val t = LocalThemeState.current
    val resolvedTrack = track ?: if (t.isDark) Color.White.copy(0.10f) else DsNeutral.BackgroundAlt
    val target = progress.coerceIn(0f, 1f)
    val animated = animateFloatAsState(targetValue = target, animationSpec = DsAnim.counter(), label = "usageBar")
    Box(
        modifier
            .height(height)
            .drawBehind {
                val r = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = resolvedTrack, cornerRadius = r)
                val w = size.width * animated.value
                if (w > 0.5f) {
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                    drawRoundRect(color = fill, topLeft = Offset(left, 0f), size = Size(w, size.height), cornerRadius = r)
                }
            }
    )
}

@Composable
fun PGStatusChip(text: String, dot: Color = DsSemantic.Success) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(DsSemantic.SuccessBg).border(BorderStroke(0.5.dp, DsSemantic.SuccessBorder), RoundedCornerShape(50)).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(dot))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DsSemantic.OnSuccess)
    }
}

@Composable
fun PGTopBar(title: String, subtitle: String? = null, onMenu: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val t = LocalThemeState.current
    Column(
        Modifier.fillMaxWidth().background(t.cardSurfaceColor).padding(horizontal = DsSpacing.Screen, vertical = DsSpacing.Mid)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                if (onMenu != null) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                        Text("☰", fontSize = 16.sp, color = t.inkColor)
                    }
                }
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.inkColor)
                    if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = t.mutedColor)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), content = actions)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.borderSubtle))
}

@Composable
fun PGDivider() {
    val t = LocalThemeState.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.borderSubtle))
}
