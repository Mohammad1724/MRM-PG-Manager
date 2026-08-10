package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mrm.pgmanager.ui.theme.LocalThemeState

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
    accent: Color = DsAccent.Gold,
    trailing: @Composable (() -> Unit)? = null
) {
    val t = LocalThemeState.current
    val shape = DsRadius.Lg
    val isGold = accent == DsAccent.Gold
    val iconBg = if (isGold) { if (t.isDark) Color(0xFF3A3000).copy(0.45f) else Color(0xFFFFFBEB) } else accent.copy(0.10f)
    val iconBorder = if (isGold) { if (t.isDark) Color(0xFFFACC15).copy(0.22f) else Color(0xFFFDE68A) } else accent.copy(0.18f)
    val iconTint = if (isGold) { if (t.isDark) DsAccent.Gold else Color(0xFFCA8A04) } else accent
    Column(
        modifier
            .height(92.dp)
            .clip(shape)
            .background(t.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, t.borderColor), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TechnicalContainer {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (valueSub != null) {
                Text(valueSub, fontSize = 10.sp, color = t.mutedLightColor, maxLines = 1)
            }
        }
    }
}

@Composable
fun PGBadge(text: String, color: Color = DsAccent.Gold) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(0.12f))
            .border(BorderStroke(0.5.dp, color.copy(0.18f)), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
    }
}

// ─────────────────────────────────────────────────────────────
//  PGSectionHeader — small title row
// ─────────────────────────────────────────────────────────────
@Composable
fun PGSectionHeader(title: String, icon: AppIcon? = null, action: @Composable (() -> Unit)? = null) {
    val t = LocalThemeState.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) {
                RoundedAppIcon(icon, tint = Color(0xFFCA8A04), size = 14.dp)
            }
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.inkColor)
        }
        if (action != null) action()
    }
}

// ─────────────────────────────────────────────────────────────
//  PGPrimaryButton — yellow warm button
// ─────────────────────────────────────────────────────────────
@Composable
fun PGPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon? = null, enabled: Boolean = true) {
    val shape = DsRadius.Md
    Box(
        modifier
            .height(DsComponent.ButtonCompact)
            .clip(shape)
            .background(if (enabled) DsAccent.Gold else DsNeutral.HairlineLight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) RoundedAppIcon(icon, tint = Color(0xFF422006), size = 14.dp)
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF422006))
        }
    }
}

@Composable
fun PGSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalThemeState.current
    val shape = DsRadius.Md
    Box(
        modifier.clip(shape).background(t.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, t.borderColor), shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.inkColor)
    }
}

// ─────────────────────────────────────────────────────────────
//  PGSearchBar — light gray bg, subtle border, rounded
// ─────────────────────────────────────────────────────────────
@Composable
fun PGSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String = "جست‌وجو", modifier: Modifier = Modifier) {
    val t = LocalThemeState.current
    val shape = DsRadius.Md
    Box(
        modifier.fillMaxWidth().height(40.dp).clip(shape).background(t.searchBgColor).border(BorderStroke(DsBorder.Hairline, t.borderColor), shape).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
@Composable
fun PGProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color? = null, fill: Color = DsSemantic.Success) {
    val t = LocalThemeState.current
    val resolvedTrack = track ?: if (t.isDark) Color.White.copy(0.10f) else Color(0xFFF3F4F6)
    val shape = RoundedCornerShape(50)
    Box(modifier.clip(shape).background(resolvedTrack).height(height)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(fill))
    }
}

@Composable
fun PGStatusChip(text: String, dot: Color = DsSemantic.Success) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFDCFCE7)).border(BorderStroke(0.5.dp, Color(0xFFBBF7D0)), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(dot))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
    }
}

@Composable
fun PGTopBar(title: String, subtitle: String? = null, onMenu: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val t = LocalThemeState.current
    Column(
        Modifier.fillMaxWidth().background(t.cardSurfaceColor).padding(horizontal = DsSpacing.Screen, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.borderSubtle))
}

@Composable
fun PGDivider() {
    val t = LocalThemeState.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.borderSubtle))
}
