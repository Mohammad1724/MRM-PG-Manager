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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.BuildConfig
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.dialogs.QuickActionSheet
import com.mrm.pgmanager.ui.dialogs.SubscriptionQrDialog
import com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog
import com.mrm.pgmanager.ui.dialogs.UserEditorDialog
import com.mrm.pgmanager.ui.dialogs.UserDetailsDialog
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.lastSeenText
import com.mrm.pgmanager.utils.lastSeenShort
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.NotificationHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.mrm.pgmanager.ui.theme.glassBg
import com.mrm.pgmanager.ui.theme.glassBorder

/** یک عملیاتِ گروهیِ در انتظارِ تأییدِ کاربر. */
private data class PendingBulk(val title: String, val message: String, val confirmLabel: String, val action: () -> Unit)

// Track more gray and visible
private fun trackBg(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.26f) else Color(0xFF6B7280).copy(alpha = 0.28f)

private fun daysLeftText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    // تلاش برای پارس به‌عنوان لحظهٔ زمانی (ISO با ساعت) تا مثل پنل، روز را دقیق و سازگار محاسبه کنیم
    return try {
        val inst = java.time.Instant.parse(expire)
        val diffSec = inst.epochSecond - java.time.Instant.now().epochSecond
        when {
            diffSec <= 0 -> "منقضی"
            diffSec < 86400 -> "امروز"
            else -> "${(diffSec + 86399L) / 86400L} روز" // گردکردنِ رو‌به‌بالا = هم‌خوان با پنل
        }
    } catch (e: Exception) {
        try {
            val exp = LocalDate.parse(expire.take(10))
            val diff = ChronoUnit.DAYS.between(LocalDate.now(), exp)
            when {
                diff < 0 -> "منقضی"
                diff == 0L -> "امروز"
                diff == 1L -> "۱ روز"
                diff <= 7 -> "$diff روز"
                diff <= 30 -> "${diff} روز"
                else -> "${diff} روز"
            }
        } catch (e2: Exception) { JalaliCalendar.isoToShamsi(expire).ifEmpty { "نامحدود" } }
    }
}

private fun daysLeftFull(expire: String?): String = daysLeftText(expire)

/** متنِ وضعیت برای کارت: اول وضعیت (غیرفعال/منقضی/محدود)، بعد روزِ مانده. */
private fun cardStatusText(user: PanelUser): String = when (user.status) {
    "disabled" -> "غیرفعال"
    "expired" -> "منقضی"
    "limited" -> "محدود"
    "on_hold" -> "⏸ در انتظار"
    else -> daysLeftText(user.expire)
}

@Composable
private fun StatGlassCard(icon: String, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.height(72.dp).clip(RoundedCornerShape(14.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(23.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(.12f)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 11.sp) }
                Text(label, fontSize = 10.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
            }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.18f, targetValue = 0.42f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(glassBg(theme.isDark).copy(alpha = alpha)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(20.dp)).height(120.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(AppIcon.Search, tint = theme.mutedColor, size = 18.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("جستجو کاربر...", color = theme.mutedColor.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } }
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.14f)).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) { Text("×", color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TopBarHeader(
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    loading: Boolean
) {
    val theme = LocalThemeState.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("کاربران", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text("مدیریت و نظارت بر حساب‌های کاربری", fontSize = 10.5.sp, color = theme.mutedColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(icon = { RoundedAppIcon(AppIcon.Settings, tint = theme.inkColor, size = 19.dp) }, onClick = onOpenThemeDialog)
