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
import com.mrm.pgmanager.BuildConfig
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

import com.mrm.pgmanager.ui.theme.glassBg
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsMotion
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.DsTileRadius

/** یک عملیاتِ گروهیِ در انتظارِ تأییدِ کاربر. */
private data class PendingBulk(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit, val danger: Boolean = false)

// Track more gray and visible
private fun trackBg(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.26f) else Color(0xFF6B7280).copy(alpha = 0.28f)

private fun daysLeftText(expire: String?): String = DateLogic.daysLeftText(expire)

private fun daysLeftFull(expire: String?): String = daysLeftText(expire)

private fun formatDebtorAmount(amount: Long): String {
    return when {
        amount >= 1_000_000_000L -> "${amount/1_000_000_000L}B"
        amount >= 1_000_000L -> "${amount/1_000_000L}M"
        amount >= 1_000L -> "${amount/1_000L}k"
        else -> "$amount"
    }
}

/** نشانگر کوچک بدهکار (سکه قرمز) کنار نام کاربری */
@Composable
private fun DebtorBadge(compact: Boolean = false) {
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
private fun cardStatusText(user: PanelUser): String = when (user.status) {
    "disabled" -> "غیرفعال"
    "expired" -> "منقضی"
    "limited" -> "محدود"
    "on_hold" -> "در انتظار"
    else -> daysLeftText(user.expire)
}

@Composable
private fun StatGlassCard(icon: AppIcon, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val shape = DsRadius.Lg
    Box(
        modifier = modifier
            .height(82.dp)
            .clip(shape)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), shape)
            .padding(12.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isGold = accent == theme.accentPrimary || accent == DsAccent.Gold
                val iconBg = if (isGold) { if (theme.isDark) DsAccent.Gold.copy(0.15f) else Color(0xFFFFFBEB) } else accent.copy(0.10f)
                val iconBorder = if (isGold) { if (theme.isDark) DsAccent.Gold.copy(0.22f) else Color(0xFFFDE68A) } else accent.copy(0.18f)
                val iconTint = if (isGold) { if (theme.isDark) DsAccent.Gold else Color(0xFFCA8A04) } else accent
                Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(iconBg).border(BorderStroke(DsBorder.Hairline, iconBorder), DsRadius.Sm), contentAlignment = Alignment.Center) {
                    RoundedAppIcon(icon, tint = iconTint, size = 15.dp)
                }
                Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TechnicalContainer {
                Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.35f, targetValue = 0.65f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(DsRadius.Lg).background(theme.cardBgColor.copy(alpha = alpha)).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).height(120.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = DsRadius.Md
    Box(modifier = modifier
        .fillMaxWidth()
        .height(40.dp)
        .clip(shape)
        .background(theme.searchBgColor)
        .border(BorderStroke(DsBorder.Hairline, if (isFocused) theme.accentPrimary.copy(0.4f) else theme.borderColor), shape)
        .padding(horizontal = 12.dp)
        .onFocusChanged { isFocused = it.isFocused }
        , contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(AppIcon.Search, contentDescription = "جستجو", tint = theme.mutedColor, size = 16.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search", color = theme.mutedColor.copy(0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
