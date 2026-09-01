package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/* ──────────────────────────────────────────────────────────────────────────
 *  دیالوگ جزئیات کاربر — نسخه داشبوردی (v0.8.0)
 *
 *  هدف: خلوت کردن صفحه شلوغ قبلی
 *  - قبل: 6 کاشی رنگی + 2 بخش جمع‌شونده + هیرو + اشتراک همه با یک وزن
 *  - الان: یک قهرمان دایره‌ای وسط، بقیه سلسله‌مراتب‌دار
 *
 *  لایه‌ها:
 *   1. هدر مینیمال (آواتار 40dp، نام، وضعیت)
 *   2. هیرو دایره‌ای: دایره مصرف 140dp وسط، داخلش درصد + استفاده شده
 *   3. دو آمار کوچک زیر دایره (زمان باقی‌مانده، حجم باقی‌مانده)
 *   4. کارت اشتراک جمع‌وجور (یک ردیف)
 *   5. نوت اگر باشد (کارت کوچک)
 *   6. اکشن اصلی: ویرایش تمام عرض، پررنگ
 *   7. اکشن‌های ثانویه: گرید 4 تایی با آیکون خاکستری، پس‌زمینه خنثی
 *   8. اطلاعات تکمیلی + دستگاه‌ها + مالی: لیست ساده، بدون رنگ اضافه
 * ────────────────────────────────────────────────────────────────────────── */

@Composable
private fun daysLeftLabel(expire: String?): String {
    val unlimited = stringResource(R.string.ud_unlimited)
    val expired = stringResource(R.string.ud_expired)
    val daysTemplate = stringResource(R.string.ud_days)
    if (expire.isNullOrBlank() || expire == "0" || expire == "null") return unlimited
    return runCatching {
        val end = try {
            java.time.Instant.parse(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) {
            LocalDate.parse(expire.take(10))
        }
        val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end)
        if (d < 0L) expired else String.format(daysTemplate, d.toString())
    }.getOrDefault(unlimited)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = LocalThemeState.current.mutedColor.copy(alpha = 0.8f),
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun CircularUsage(
    percentage: Int,
    usedLabel: String,
    totalLabel: String,
    unlimited: Boolean,
    color: Color
) {
    val theme = LocalThemeState.current
    val animated by animateFloatAsState(
        targetValue = if (unlimited) 1f else percentage / 100f,
        animationSpec = DsAnim.enter(),
        label = "circular"
    )
    Box(
        modifier = Modifier.size(148.dp),
        contentAlignment = Alignment.Center
    ) {
        // پس‌زمینه دایره
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(
                color = if (theme.isDark) Color.White.copy(0.08f) else Color(0xFFE9EBEF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(color.copy(0.7f), color, color.copy(0.9f))
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        // محتوای وسط
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (unlimited) "∞" else "$percentage%",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            MrmText(
                usedLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.inkColor,
                isTechnical = true
            )
            MrmText(
                "/ $totalLabel",
                fontSize = 9.5.sp,
                color = theme.mutedColor,
                isTechnical = true
            )
        }
    }
}

@Composable
private fun MiniStat(
    icon: AppIcon,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeState.current
    Column(
        modifier
            .clip(DsRadius.Lg)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RoundedAppIcon(icon, tint = theme.mutedColor, size = 14.dp)
        Text(label, fontSize = 9.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MrmText(value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompactAction(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val theme = LocalThemeState.current
    Column(
        modifier
            .clip(DsRadius.Lg)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .pressScale(0.96f)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(theme.cardSurfaceColor),
            contentAlignment = Alignment.Center
        ) {
            RoundedAppIcon(icon, tint = tint ?: theme.inkColor, size = 16.dp)
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoRow(icon: AppIcon, label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RoundedAppIcon(icon, tint = theme.mutedColor, size = 14.dp)
        Text(label, fontSize = 10.5.sp, color = theme.mutedColor, modifier = Modifier.width(72.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        MrmText(value, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

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
    session: Session? = null,
    debtorInfo: DebtorInfo? = null,
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
    var usageConfirm by remember { mutableStateOf(false) }
    var expiryConfirm by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var availableTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesFailed by remember { mutableStateOf(false) }
    var revokeConfirm by remember { mutableStateOf(false) }
    var notesSheetOpen by remember { mutableStateOf(false) }
    var devicesResetConfirm by remember { mutableStateOf(false) }
    var nextPlanConfirm by remember { mutableStateOf(false) }
    var devices by remember(user.id) { mutableStateOf<List<UserDevice>>(emptyList()) }
    var showMore by remember { mutableStateOf(false) }

    fun reloadDevices() {
        if (session == null) return
        scope.launch { runCatching { PanelApi.userDevices(session, currentUser.id) }.onSuccess { devices = it } }
    }
    LaunchedEffect(user.id, session) { reloadDevices() }

    val copiedMsg = stringResource(R.string.ud_copied)
    val closeLabel = stringResource(R.string.ud_close)
    val subFailedMsg = stringResource(R.string.ud_sub_failed)
    val unlimitedLabel = stringResource(R.string.ud_unlimited)
    val revokedMsg = stringResource(R.string.ud_revoked)

    fun ensureSub(onResult: (String) -> Unit) {
        if (currentUser.subUrl.isNotBlank()) onResult(currentUser.subUrl)
        else if (session != null) {
            scope.launch {
                runCatching { PanelApi.user(session, currentUser.username) }.onSuccess {
                    currentUser = it; onResult(it.subUrl)
                }.onFailure {
                    android.widget.Toast.makeText(context, subFailedMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else onResult(currentUser.subUrl)
    }

    val unlimitedData = currentUser.dataLimit == 0L
    val totalLabel = if (unlimitedData) unlimitedLabel else formatBytes(currentUser.dataLimit)
    val percentage = if (currentUser.dataLimit > 0L) ((currentUser.usedTraffic * 100f / currentUser.dataLimit).toInt()).coerceIn(0, 100) else 0
    val usageColor = when { percentage < 70 -> GlassGreen; percentage < 90 -> GlassAmber; else -> GlassRed }
    val remainingData = (currentUser.dataLimit - currentUser.usedTraffic).coerceAtLeast(0L)
    val isDisabled = currentUser.status == "disabled"
    val statusColor = when (currentUser.status) {
        "active" -> GlassGreen; "expired" -> GlassRed; "limited" -> GlassAmber
        "disabled" -> Color(0xFF8A8A8A); "on_hold" -> DsSemantic.Violet; else -> theme.mutedColor
    }
    val statusLabel = when (currentUser.status) {
        "active" -> stringResource(R.string.active)
        "expired" -> stringResource(R.string.expired)
        "limited" -> stringResource(R.string.limited)
        "disabled" -> stringResource(R.string.disabled)
        "on_hold" -> stringResource(R.string.on_hold)
        else -> currentUser.status
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
            ) {
                // ── هدر مینیمال
                Row(
                    Modifier.fillMaxWidth().background(theme.cardSurfaceColor).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(theme.accentPrimary.copy(0.28f), theme.accentPrimary.copy(0.10f))))
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(currentUser.username.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        }
                        if (currentUser.isOnline) {
                            Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(theme.cardSurfaceColor), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(GlassGreen))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MrmText(currentUser.username, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                            Text(statusLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = statusColor)
                            Text("·", fontSize = 10.sp, color = theme.mutedColor)
                            MrmText(lastSeenText(currentUser.onlineAt, currentUser.isOnline), fontSize = 10.5.sp, color = theme.mutedColor, maxLines = 1, isTechnical = true)
                        }
                    }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(theme.searchBgColor)
                            .semantics { contentDescription = closeLabel }.pressScale(0.9f).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
                }
                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))

                // ── محتوا اسکرول
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 18.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── هیرو دایره‌ای
                    CircularUsage(
                        percentage = percentage,
                        usedLabel = formatBytes(currentUser.usedTraffic),
                        totalLabel = totalLabel,
                        unlimited = unlimitedData,
                        color = if (unlimitedData) theme.accentPrimary else usageColor
                    )

                    // ── دو آمار کوچک
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat(icon = AppIcon.Timer, label = stringResource(R.string.ud_remaining_time), value = daysLeftLabel(currentUser.expire), modifier = Modifier.weight(1f))
                        MiniStat(icon = AppIcon.Storage, label = stringResource(R.string.ud_remaining_data), value = if (unlimitedData) unlimitedLabel else formatBytes(remainingData), modifier = Modifier.weight(1f))
                    }

                    // ── اشتراک - یک ردیف جمع‌وجور
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(stringResource(R.string.ud_subscription))
                        Row(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Link, tint = theme.mutedColor, size = 16.dp)
                            MrmText(currentUser.subUrl.ifBlank { "—" }, fontSize = 10.5.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(theme.searchBgColor)
                                        .pressScale(0.9f).clickable { ensureSub { url -> val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", url)); android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show() } },
                                    contentAlignment = Alignment.Center
                                ) { RoundedAppIcon(AppIcon.Copy, tint = theme.inkColor, size = 14.dp) }
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(theme.searchBgColor)
                                        .pressScale(0.9f).clickable { ensureSub { qrOpen = true } },
                                    contentAlignment = Alignment.Center
                                ) { RoundedAppIcon(AppIcon.Qr, tint = theme.inkColor, size = 14.dp) }
                            }
                        }
                    }

                    // ── نوت
                    if (!currentUser.note.isNullOrBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.accentPrimary.copy(0.07f))
                                .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.15f)), DsRadius.Lg)
                                .pressScale(0.98f).clickable { notesSheetOpen = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 14.dp)
                            Text(currentUser.note!!.trim(), fontSize = 11.5.sp, color = theme.inkColor, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), lineHeight = 15.sp)
                            Text("↗", fontSize = 11.sp, color = theme.accentPrimary)
                        }
                    }

                    // ── اکشن اصلی
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stringResource(R.string.ud_manage))
                        // ویرایش - تمام عرض، پررنگ
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Lg).background(theme.accentPrimary)
                                .pressScale(0.98f).clickable { editOpen = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Edit, tint = Color(0xFF422006), size = 18.dp)
                            Text(stringResource(R.string.ud_edit), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF422006), modifier = Modifier.weight(1f))
                            Text("›", fontSize = 18.sp, color = Color(0xFF422006).copy(0.7f))
                        }
                        // گرید ثانویه 4 تایی
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactAction(icon = AppIcon.Template, label = stringResource(R.string.ud_template), onClick = { templatePickerOpen = true }, modifier = Modifier.weight(1f))
                            CompactAction(icon = AppIcon.Reset, label = stringResource(R.string.ud_reset_data), onClick = { usageConfirm = true }, modifier = Modifier.weight(1f))
                            CompactAction(icon = AppIcon.Calendar, label = stringResource(R.string.ud_reset_time), onClick = { expiryConfirm = true }, modifier = Modifier.weight(1f))
                            CompactAction(icon = if (isDisabled) AppIcon.CheckCircle else AppIcon.StatusDisabled, label = stringResource(if (isDisabled) R.string.ud_enable else R.string.ud_disable), onClick = { onToggle() }, modifier = Modifier.weight(1f), tint = if (isDisabled) GlassGreen else GlassAmber)
                        }
                    }

                    // ── اطلاعات تکمیلی - لیست ساده
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(stringResource(R.string.ud_more))
                            Spacer(Modifier.weight(1f))
                            Text(if (showMore) "▴" else "▾", fontSize = 11.sp, color = theme.mutedColor, modifier = Modifier.pressScale(0.9f).clickable { showMore = !showMore }.padding(4.dp))
                        }
                        AnimatedVisibility(visible = showMore, enter = DsTransition.expandEnter, exit = DsTransition.expandExit) {
                            Column(
                                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                if (currentUser.groupNames.isNotEmpty()) InfoRow(AppIcon.Folder, stringResource(R.string.ud_group_names), currentUser.groupNames.joinToString("، "))
                                currentUser.createdAt?.takeIf { it.isNotBlank() }?.let { InfoRow(AppIcon.Calendar, stringResource(R.string.ud_created_at), JalaliCalendar.isoToShamsi(it).ifBlank { it.take(10) }) }
                                if (currentUser.lifetimeUsedTraffic > currentUser.usedTraffic) InfoRow(AppIcon.Storage, stringResource(R.string.ud_lifetime), formatBytes(currentUser.lifetimeUsedTraffic))
                                currentUser.ownerAdmin?.let { InfoRow(AppIcon.User, stringResource(R.string.ud_owner), it) }
                                if (currentUser.groupNames.isEmpty() && currentUser.createdAt.isNullOrBlank() && currentUser.ownerAdmin == null) {
                                    Text("—", fontSize = 11.sp, color = theme.mutedColor, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }

                    // ── دستگاه‌ها اگر باشد
                    if (session != null && (devices.isNotEmpty() || currentUser.hwidLimit != null)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(stringResource(R.string.ud_devices))
                            Column(
                                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.ud_devices_count, devices.size, currentUser.hwidLimit ?: 0), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
                                    if (devices.isNotEmpty()) {
                                        Box(
                                            Modifier.clip(DsRadius.Full).background(GlassRed.copy(0.12f)).pressScale(0.95f)
                                                .clickable { devicesResetConfirm = true }.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) { Text(stringResource(R.string.ud_devices_reset), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = GlassRed) }
                                    }
                                }
                                if (devices.isEmpty()) Text(stringResource(R.string.ud_devices_empty), fontSize = 10.sp, color = theme.mutedColor)
                                devices.take(3).forEach { d ->
                                    Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            MrmText(listOfNotNull(d.deviceModel, d.deviceOs).joinToString(" · ").ifBlank { d.hwid.take(12) }, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Box(
                                            Modifier.clip(DsRadius.Sm).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.25f)), DsRadius.Sm)
                                                .pressScale(0.9f).clickable { scope.launch { runCatching { PanelApi.deleteUserDevice(session, currentUser.id, d.hwid) }; reloadDevices() } }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) { Text(stringResource(R.string.ud_device_forget), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlassRed) }
                                    }
                                }
                            }
                        }
                    }

                    // ── پلن بعدی
                    currentUser.nextPlan?.let { np ->
                        Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.accentPrimary.copy(0.07f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.18f)), DsRadius.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Template, tint = theme.accentPrimary, size = 13.dp)
                                Text(stringResource(R.string.ud_next_plan), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
                                if (session != null) {
                                    Box(Modifier.clip(DsRadius.Full).background(theme.accentPrimary).pressScale(0.95f).clickable { nextPlanConfirm = true }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                                        Text(stringResource(R.string.ud_next_plan_activate), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF422006))
                                    }
                                }
                            }
                            val desc = when {
                                np.templateId != null -> "Template #${np.templateId}"
                                else -> "${np.dataLimit?.let { formatBytes(it) } ?: unlimitedLabel} · ${(np.expireSeconds ?: 0L) / 86400L}d"
                            }
                            Text(desc, fontSize = 10.sp, color = theme.mutedColor)
                        }
                    }

                    // ── مالی جمع‌وجور
                    if (debtorInfo != null || onMarkDebtor != null) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(stringResource(R.string.ud_financial))
                            Row(
                                Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                    .background(if (debtorInfo != null) GlassRed.copy(0.08f) else theme.cardSurfaceColor)
                                    .border(BorderStroke(DsBorder.Hairline, if (debtorInfo != null) GlassRed.copy(0.2f) else theme.borderColor), DsRadius.Lg)
                                    .pressScale(0.98f).clickable { if (debtorInfo != null) onClearDebt?.invoke() else onMarkDebtor?.invoke() }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RoundedAppIcon(if (debtorInfo != null) AppIcon.Warning else AppIcon.Money, tint = if (debtorInfo != null) GlassRed else theme.accentPrimary, size = 16.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(if (debtorInfo != null) stringResource(R.string.ud_debt_of, debtorInfo.amount.toString(), debtorInfo.currency) else stringResource(R.string.ud_invoice), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (debtorInfo != null) GlassRed else theme.inkColor)
                                    if (debtorInfo != null && debtorInfo.notes.isNotBlank()) Text(debtorInfo.notes, fontSize = 9.5.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("›", fontSize = 16.sp, color = theme.mutedColor)
                            }
                        }
                    }

                    // ── حذف - کم‌رنگ
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(DsRadius.Lg).background(theme.searchBgColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .pressScale(0.98f).clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) { Text(closeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(DsRadius.Lg).background(GlassRed.copy(0.10f))
                                .border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.20f)), DsRadius.Lg)
                                .pressScale(0.98f).clickable { onDelete() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 14.dp)
                                Text(stringResource(R.string.ud_delete), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (templatePickerOpen) {
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            val result = runCatching { session?.let { PanelApi.userTemplates(it) } ?: emptyList() }
            availableTemplates = result.getOrDefault(emptyList()); templatesFailed = result.isFailure; templatesLoading = false
        }
        BulkApplyTemplateDialog(templates = availableTemplates, selectedCount = 1, onDismiss = { templatePickerOpen = false }, onApply = { id, note -> templatePickerOpen = false; onApplyTemplate?.invoke(id, note) }, isLoading = templatesLoading, loadFailed = templatesFailed)
    }
    if (editOpen) UserEditorDialog(initial = currentUser, onDismiss = { editOpen = false }, onSave = onSave, onToggle = onToggle, onApplyTemplateToUser = onApplyTemplate, session = session)
    if (notesSheetOpen) NotesSheetDialog(note = currentUser.note.orEmpty(), onDismiss = { notesSheetOpen = false }, onEdit = { editOpen = true })
    if (qrOpen) SubscriptionQrDialog(user = currentUser, onDismiss = { qrOpen = false })
    if (usageConfirm) ConfirmActionDialog(title = stringResource(R.string.ud_reset_data_title), message = stringResource(R.string.ud_reset_data_msg), onDismiss = { usageConfirm = false }, onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0L); onResetUsage() })
    if (devicesResetConfirm && session != null) ConfirmActionDialog(title = stringResource(R.string.ud_devices_reset_title), message = stringResource(R.string.ud_devices_reset_msg), onDismiss = { devicesResetConfirm = false }, onConfirm = { devicesResetConfirm = false; scope.launch { runCatching { PanelApi.resetUserDevices(session, currentUser.id) }; reloadDevices() } })
    if (nextPlanConfirm && session != null) ConfirmActionDialog(title = stringResource(R.string.ud_next_plan_activate_title), message = stringResource(R.string.ud_next_plan_activate_msg), onDismiss = { nextPlanConfirm = false }, onConfirm = { nextPlanConfirm = false; scope.launch { runCatching { PanelApi.activateNextPlan(session, currentUser.username) }.onSuccess { runCatching { PanelApi.user(session, currentUser.username) }.onSuccess { currentUser = it } } } })
    if (revokeConfirm && session != null) ConfirmActionDialog(title = stringResource(R.string.ud_revoke_title), message = stringResource(R.string.ud_revoke_msg), onDismiss = { revokeConfirm = false }, onConfirm = { revokeConfirm = false; scope.launch { runCatching { PanelApi.revokeSubscription(session, currentUser.username) }.onSuccess { currentUser = it; android.widget.Toast.makeText(context, revokedMsg, android.widget.Toast.LENGTH_SHORT).show() }.onFailure { android.widget.Toast.makeText(context, subFailedMsg, android.widget.Toast.LENGTH_SHORT).show() } } })
    if (expiryConfirm) ResetExpiryDurationDialog(onDismiss = { expiryConfirm = false }, onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) })
}

@Composable
private fun NotePreviewCard(note: String?, onOpen: () -> Unit, onAdd: () -> Unit) {
    // kept for compatibility but not used in dashboard version - inline handled
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheetDialog(note: String, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.ud_note_copied)
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = theme.dialogBgColor, contentColor = theme.inkColor, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor)) } }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(0.12f)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 16.dp) }
                Text(stringResource(R.string.ud_note_sheet_title), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(DsRadius.Full).background(theme.searchBgColor).pressScale(0.9f).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Text("×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
            }
            Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.text.selection.SelectionContainer { Text(note.trim(), fontSize = 13.5.sp, color = theme.inkColor, lineHeight = 20.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(DsRadius.Lg).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).pressScale(0.97f).clickable { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("note", note)); android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundedAppIcon(AppIcon.Copy, tint = theme.mutedColor, size = 16.dp); Text(stringResource(R.string.ud_note_copy), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                }
                Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(DsRadius.Lg).background(theme.accentPrimary).pressScale(0.97f).clickable { onDismiss(); onEdit() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundedAppIcon(AppIcon.Edit, tint = Color(0xFF422006), size = 16.dp); Text(stringResource(R.string.ud_note_edit), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF422006))
                }
            }
        }
    }
}
