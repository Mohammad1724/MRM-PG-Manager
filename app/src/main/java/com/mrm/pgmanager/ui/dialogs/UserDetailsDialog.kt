package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

@Composable
private fun detailDaysText(expire: String?): String {
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return "نامحدود"
    return runCatching {
        val end = try { java.time.Instant.parse(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(expire.take(10)) }
        val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end)
        if (d < 0L) "منقضی" else "$d روز"
    }.getOrDefault("نامحدود")
}

/** دکمهٔ آیکون گرد برای کارت اشتراک و بخش‌های مشابه */
@Composable
private fun IconActionBtn(icon: AppIcon, contentDesc: String, theme: com.mrm.pgmanager.ui.theme.ThemeState, modifier: Modifier = Modifier, onClick: () -> Unit) {
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

/** منوی کشویی کپسولی برای اکشن‌ها (بدهکار/فاکتور) - هماهنگ با design system */
@Composable
private fun CapsuleActionMenu(
    label: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    isDebtor: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalThemeState.current
    val headerColor = if (isDebtor) GlassRed else theme.accentPrimary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // دکمه سربرگ کشویی - هماهنگ با دکمه‌های کپسولی settings
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(DsRadius.Xl)
                .background(headerColor.copy(0.10f))
                .border(BorderStroke(1.2.dp, headerColor.copy(0.30f)), DsRadius.Xl)
                .clickable { onToggleExpand() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedAppIcon(if (isDebtor) AppIcon.Warning else AppIcon.Money, tint = headerColor, size = 18.dp)
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = headerColor, modifier = Modifier.weight(1f))
                // فلش بالا/پایین: وقتی باز است به بالا، وقتی بسته است به پایین
                RoundedAppIcon(
                    AppIcon.Next,
                    tint = headerColor, size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) -90f else 90f }
                )
            }
        }
        // محتوای کشویی با انیمیشن
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(160))
        ) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xl)
                    .background(if (theme.isDark) Color.White.copy(0.06f) else Color.White)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                content = actions
            )
        }
    }
}

/** یک ردیف دکمه کپسولی درون منوی کشویی */
@Composable
private fun CapsuleMenuItem(
    icon: AppIcon,
    label: String,
    accent: Color,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        primary -> accent.copy(0.78f)
        danger -> accent.copy(0.10f)
        else -> LocalThemeState.current.searchBgColor
    }
    val textColor = when {
        primary -> Color(0xFF202124)
        else -> accent
    }
    var borderColor = LocalThemeState.current.borderColor
    if (danger || primary) borderColor = accent.copy(if (primary) 0f else 0.30f)
    Box(
        Modifier.fillMaxWidth().height(42.dp).clip(DsRadius.Lg).background(bg)
            .border(BorderStroke(DsBorder.Hairline, borderColor), DsRadius.Lg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundedAppIcon(icon, tint = textColor, size = 17.dp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
@Composable
fun UserDetailsDialog(
    user: PanelUser,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onResetUsage: () -> Unit,
    onResetExpiry: (Int) -> Unit,
    onApplyTemplate: ((Int, String) -> Unit)? = null,
    session: com.mrm.pgmanager.data.model.Session? = null,
    debtorInfo: com.mrm.pgmanager.data.model.DebtorInfo? = null,
    onMarkDebtor: (() -> Unit)? = null,
    onClearDebt: (() -> Unit)? = null,
    onInvoice: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUser by remember(user) { mutableStateOf(user) }
    var editOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    // دریافت لینک اشتراک به‌صورت lazy (لیست کاربران بدون load_sub واکشی می‌شود تا به پنل فشار نیاید).
    fun ensureSub(onResult: (String) -> Unit) {
        if (currentUser.subUrl.isNotBlank()) {
            onResult(currentUser.subUrl)
        } else if (session != null) {
            scope.launch {
                runCatching { PanelApi.user(session, currentUser.username) }.onSuccess {
                    currentUser = it
                    onResult(it.subUrl)
                }.onFailure {
                    android.widget.Toast.makeText(context, "دریافت لینک اشتراک ناموفق بود", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            onResult(currentUser.subUrl)
        }
    }
    var usageConfirm by remember { mutableStateOf(false) }
    var expiryConfirm by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var availableTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesFailed by remember { mutableStateOf(false) }
    // وضعیت باز/بسته بودن منوی کشویی بدهی/فاکتور
    var debtorMenuExpanded by remember { mutableStateOf(false) }
    val traffic = if (currentUser.dataLimit == 0L) "نامحدود" else formatBytes(currentUser.dataLimit)
    val percentage = if (currentUser.dataLimit > 0L) ((currentUser.usedTraffic * 100f / currentUser.dataLimit).toInt()).coerceIn(0, 100) else 0
    val progressColor = when { percentage < 70 -> GlassGreen; percentage < 90 -> GlassAmber; else -> GlassRed }

    fun section() = Modifier.fillMaxWidth().clip(DsRadius.Lg)
        .background(theme.cardSurfaceColor)
        .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg).padding(12.dp)

    @Composable fun sectionTitle(text: String) = Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
    @Composable fun statTile(label: String, value: String, modifier: Modifier = Modifier) {
        Column(modifier.height(54.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), DsRadius.Sm).padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, modifier = Modifier.offset(y = (-2).dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
