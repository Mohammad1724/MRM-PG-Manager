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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mrm.pgmanager.ui.designsystem.DsNeutral
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.launch

/**
 * همان منطقِ فهرست (`DateLogic.daysLeft` + `daysLeftText`) تا «امروز/منقضی/N روز»
 * در کارت و در جزئیات یکی باشد؛ قبلاً اینجا یک پارسرِ جداگانه داشت که صفر روز را
 * «۰ روز» نشان می‌داد و offsetهای غیر از Z را به‌درستی نمی‌خواند.
 */
@Composable
private fun daysLeftLabel(expire: String?): String = daysLeftText(expire)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = LocalThemeState.current.mutedColor.copy(alpha = 0.7f),
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = DsSpacing.Xxs, bottom = 1.dp)
    )
}

// دایره کوچک‌تر: 100dp به‌جای 148dp
@Composable
private fun CircularUsage(
    percentage: Int,
    usedLabel: String,
    totalLabel: String,
    unlimited: Boolean,
    color: Color
) {
    val theme = LocalThemeState.current
    // با بازشدنِ دیالوگ، کمان از صفر تا مقدارش جارو می‌شود؛ تغییراتِ بعدی (تمدید/ریست) از مقدارِ قبلی می‌لغزند.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (!started) 0f else if (unlimited) 1f else percentage / 100f,
        animationSpec = DsAnim.counter(),
        label = "circular"
    )
    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            drawArc(
                color = if (theme.isDark) Color.White.copy(0.07f) else DsNeutral.HairlineSubtle,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(0.7f), color)),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (unlimited) "∞" else "$percentage%", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            MrmText(usedLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, isTechnical = true)
            MrmText("/ $totalLabel", fontSize = 11.sp, color = theme.mutedColor, isTechnical = true)
        }
    }
}

// خیلی کوچک‌تر: چیپ 32dp ارتفاع، ردیفی
@Composable
private fun MiniStat(
    icon: AppIcon,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeState.current
    Row(
        modifier
            .height(32.dp)
            .clip(DsRadius.Md)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
            .padding(horizontal = DsSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        RoundedAppIcon(icon, tint = theme.mutedColor, size = 11.dp)
        Column(verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 11.sp, color = theme.mutedColor, maxLines = 1, lineHeight = 13.sp)
            MrmText(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
        }
    }
}

@Composable
private fun CompactAction(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isDestructive: Boolean = false
) {
    val theme = LocalThemeState.current
    val bg = when {
        isDestructive -> GlassRed.copy(0.10f)
        else -> theme.searchBgColor
    }
    val border = when {
        isDestructive -> GlassRed.copy(0.18f)
        else -> theme.borderColor
    }
    Column(
        modifier
            .clip(DsRadius.Md)
            .background(bg)
            .border(BorderStroke(DsBorder.Hairline, border), DsRadius.Md)
            .pressScale(0.96f)
            .clickable(onClick = onClick)
            .padding(vertical = DsSpacing.Md, horizontal = DsSpacing.Xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
    ) {
        RoundedAppIcon(icon, tint = tint ?: if (isDestructive) GlassRed else theme.inkColor, size = 14.dp)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDestructive) GlassRed else theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoRow(icon: AppIcon, label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Row(modifier.fillMaxWidth().padding(vertical = DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
        RoundedAppIcon(icon, tint = theme.mutedColor, size = 12.dp)
        Text(label, fontSize = 11.sp, color = theme.mutedColor, modifier = Modifier.width(64.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        MrmText(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    // IPهای فعال (nodes.stats) — null یعنی هنوز پرسیده نشده؛ فقط برای کاربرِ آنلاین خودکار پرسیده می‌شود
    // چون پنل باید از همهٔ نودهای سالم بپرسد.
    val canNodeStats = com.mrm.pgmanager.data.AdminAccess.can("nodes", "stats")
    var onlineIps by remember(user.id) { mutableStateOf<List<OnlineIp>?>(null) }
    var onlineIpsLoading by remember(user.id) { mutableStateOf(false) }
    // تاریخچهٔ گرفتنِ لینکِ اشتراک (users.read) — ۵ موردِ آخر + شمارِ کل.
    var subUpdates by remember(user.id) { mutableStateOf<SubUpdateList?>(null) }
    val nodeNames = remember(session) {
        session?.let { com.mrm.pgmanager.data.cache.PanelCache.get<List<PanelNode>>(com.mrm.pgmanager.data.cache.PanelCache.nodesKey(it.baseUrl)) }
            ?.associate { it.id to it.name } ?: emptyMap()
    }

    fun reloadDevices() {
        if (session == null) return
        scope.launch { runCatching { PanelApi.userDevices(session, currentUser.id) }.onSuccess { devices = it } }
    }
    fun loadOnlineIps() {
        if (session == null || !canNodeStats || onlineIpsLoading) return
        onlineIpsLoading = true
        scope.launch {
            runCatching { PanelApi.userOnlineIps(session, currentUser.id) }.onSuccess { onlineIps = it }
            onlineIpsLoading = false
        }
    }
    LaunchedEffect(user.id, session) {
        reloadDevices()
        if (session != null) runCatching { PanelApi.userSubUpdates(session, currentUser.id, limit = 5) }.onSuccess { subUpdates = it }
        if (user.isOnline) loadOnlineIps()
    }

    val copiedMsg = stringResource(R.string.ud_copied)
    val closeLabel = stringResource(R.string.ud_close)
    val subFailedMsg = stringResource(R.string.ud_sub_failed)
    val unlimitedLabel = stringResource(R.string.ud_unlimited)
    val revokedMsg = stringResource(R.string.ud_revoked)

    fun ensureSub(onResult: (String) -> Unit) {
        if (currentUser.subUrl.isNotBlank()) onResult(currentUser.subUrl)
        else if (session != null) {
            scope.launch {
                runCatching { PanelApi.user(session, currentUser) }.onSuccess { currentUser = it; onResult(it.subUrl) }
                    .onFailure { com.mrm.pgmanager.ui.feedback.AppFeedback.error(subFailedMsg) }
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
        "disabled" -> DsSemantic.Disabled; "on_hold" -> DsSemantic.Violet; else -> theme.mutedColor
    }
    val statusLabel = when (currentUser.status) {
        "active" -> stringResource(R.string.active)
        "expired" -> stringResource(R.string.expired)
        "limited" -> stringResource(R.string.limited)
        "disabled" -> stringResource(R.string.disabled)
        "on_hold" -> stringResource(R.string.on_hold)
        else -> currentUser.status
    }

    // تا وقتی ویرایشگر باز است، ورقهٔ جزئیات پنهان می‌شود تا در مسیر
    // «کارت ← جزئیات ← ویرایش» همیشه حداکثر یک لایه باز باشد (معیار UX).
    if (!editOpen) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = theme.dialogBgColor, contentColor = theme.inkColor, tonalElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = DsSpacing.Mid, bottom = DsSpacing.Sm), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor)) } }
    ) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).navigationBarsPadding().imePadding().padding(bottom = DsSpacing.Md)) {
                // ── هدر جدید: آواتار مینیمال 28dp بدون گرادینت
                Row(
                    Modifier.fillMaxWidth().background(theme.cardSurfaceColor).padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
                ) {
                    // آواتار جدید: مربع گرد 28dp با پس‌زمینه خنثی + حرف اول کوچک
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text(currentUser.username.take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                            MrmText(currentUser.username, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                            if (currentUser.isOnline) Box(Modifier.size(6.dp).clip(CircleShape).background(GlassGreen))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(statusColor))
                            Text(statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                            Text("·", fontSize = 11.sp, color = theme.mutedColor)
                            MrmText(lastSeenText(currentUser.onlineAt, currentUser.isOnline), fontSize = 11.sp, color = theme.mutedColor, maxLines = 1, isTechnical = true)
                        }
                    }
                    Box(Modifier.padding(6.dp).size(28.dp).clip(CircleShape).clickable(onClick = onDismiss).background(theme.searchBgColor).semantics { contentDescription = closeLabel }.pressScale(0.9f), contentAlignment = Alignment.Center) {
                        Text("×", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    }
                }
                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Lg).padding(top = DsSpacing.Lg, bottom = DsSpacing.Mid),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // دایره کوچک‌تر
                    CircularUsage(percentage = percentage, usedLabel = formatBytes(currentUser.usedTraffic), totalLabel = totalLabel, unlimited = unlimitedData, color = if (unlimitedData) theme.accentPrimary else usageColor)

                    // کادرهای خیلی کوچک‌تر
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                        MiniStat(icon = AppIcon.Timer, label = stringResource(R.string.ud_remaining_time), value = currentUser.onHoldDays?.let { stringResource(R.string.dl_on_hold_days, it) } ?: daysLeftLabel(currentUser.expire), modifier = Modifier.weight(1f))
                        MiniStat(icon = AppIcon.Storage, label = stringResource(R.string.ud_remaining_data), value = if (unlimitedData) unlimitedLabel else formatBytes(remainingData), modifier = Modifier.weight(1f))
                    }

                    // اشتراک جمع‌وجور
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                        SectionLabel(stringResource(R.string.ud_subscription))
                        Row(
                            Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
                        ) {
                            RoundedAppIcon(AppIcon.Link, tint = theme.mutedColor, size = 13.dp)
                            MrmText(currentUser.subUrl.ifBlank { "—" }, fontSize = 11.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true, modifier = Modifier.weight(1f))
                            Row {
                                Box(Modifier.padding(7.dp).size(26.dp).clip(CircleShape).clickable { ensureSub { url -> val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", url)); com.mrm.pgmanager.ui.feedback.AppFeedback.success(copiedMsg) } }.background(theme.searchBgColor).pressScale(0.9f), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Copy, tint = theme.inkColor, size = 12.dp) }
                                Box(Modifier.padding(7.dp).size(26.dp).clip(CircleShape).clickable { ensureSub { qrOpen = true } }.background(theme.searchBgColor).pressScale(0.9f), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Qr, tint = theme.inkColor, size = 12.dp) }
                            }
                        }
                    }

                    if (!currentUser.note.isNullOrBlank()) {
                        Row(
                            Modifier.heightIn(min = 40.dp).fillMaxWidth().clip(DsRadius.Md).clickable { notesSheetOpen = true }.background(theme.accentPrimary.copy(0.06f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.12f)), DsRadius.Md)
                                .pressScale(0.98f).padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
                        ) {
                            RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 12.dp)
                            Text(currentUser.note!!.trim(), fontSize = 11.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("↗", fontSize = 11.sp, color = theme.accentPrimary)
                        }
                    }

                    // ── اکشن‌ها با چینش جدید + delete داخل گرید
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                        SectionLabel(stringResource(R.string.ud_manage))
                        // ویرایش تمام عرض — همان کپسولِ شیشه‌ایِ اصلی (سکهٔ آیکون + فلش).
                        PGPrimaryButton(
                            text = stringResource(R.string.ud_edit),
                            onClick = { editOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = AppIcon.Edit
                        )
                        // ردیف اول: 3 تایی
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                            CompactAction(icon = AppIcon.Template, label = stringResource(R.string.ud_template), onClick = { templatePickerOpen = true }, modifier = Modifier.weight(1f))
                            CompactAction(icon = AppIcon.Reset, label = stringResource(R.string.ud_reset_data), onClick = { usageConfirm = true }, modifier = Modifier.weight(1f))
                            CompactAction(icon = AppIcon.Calendar, label = stringResource(R.string.ud_reset_time), onClick = { expiryConfirm = true }, modifier = Modifier.weight(1f))
                        }
                        // ردیف دوم: فعال/غیرفعال + حذف (چینش جدید)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                            CompactAction(
                                icon = if (isDisabled) AppIcon.CheckCircle else AppIcon.StatusDisabled,
                                label = stringResource(if (isDisabled) R.string.ud_enable else R.string.ud_disable),
                                onClick = { onToggle() }, modifier = Modifier.weight(1f),
                                tint = if (isDisabled) GlassGreen else GlassAmber
                            )
                            CompactAction(icon = AppIcon.Delete, label = stringResource(R.string.ud_delete), onClick = { onDelete() }, modifier = Modifier.weight(1f), isDestructive = true)
                        }
                    }

                    // اطلاعات بیشتر
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.ud_more))
                            Spacer(Modifier.weight(1f))
                            Text(if (showMore) "▴" else "▾", fontSize = 11.sp, color = theme.mutedColor, modifier = Modifier.pressScale(0.9f).clickable { showMore = !showMore }.padding(start = 30.dp, top = 13.dp, end = DsSpacing.Xs, bottom = 13.dp))
                        }
                        AnimatedVisibility(visible = showMore, enter = DsTransition.expandEnter, exit = DsTransition.expandExit) {
                            Column(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Xxs)) {
                                if (currentUser.groupNames.isNotEmpty()) InfoRow(AppIcon.Folder, stringResource(R.string.ud_group_names), currentUser.groupNames.joinToString("، "))
                                currentUser.createdAt?.takeIf { it.isNotBlank() }?.let { InfoRow(AppIcon.Calendar, stringResource(R.string.ud_created_at), JalaliCalendar.isoToShamsi(it).ifBlank { it.take(10) }) }
                                if (currentUser.lifetimeUsedTraffic > currentUser.usedTraffic) InfoRow(AppIcon.Storage, stringResource(R.string.ud_lifetime), formatBytes(currentUser.lifetimeUsedTraffic))
                                currentUser.ownerAdmin?.let { InfoRow(AppIcon.User, stringResource(R.string.ud_owner), it) }
                            }
                        }
                    }

                    if (session != null && (devices.isNotEmpty() || currentUser.hwidLimit != null)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                            SectionLabel(stringResource(R.string.ud_devices))
                            Column(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(DsSpacing.Md), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                    Text(stringResource(R.string.ud_devices_count, devices.size, currentUser.hwidLimit ?: 0), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
                                    if (devices.isNotEmpty()) Box(Modifier.heightIn(min = 40.dp).clip(DsRadius.Full).clickable { devicesResetConfirm = true }.background(GlassRed.copy(0.10f)).pressScale(0.95f).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Xs)) { Text(stringResource(R.string.ud_devices_reset), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassRed) }
                                }
                                if (devices.isEmpty()) Text(stringResource(R.string.ud_devices_empty), fontSize = 11.sp, color = theme.mutedColor)
                                devices.take(2).forEach { d ->
                                    Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                        MrmText(listOfNotNull(d.deviceModel, d.deviceOs).joinToString(" · ").ifBlank { d.hwid.take(10) }, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Box(Modifier.heightIn(min = 40.dp).clip(DsRadius.Sm).clickable { scope.launch { runCatching { PanelApi.deleteUserDevice(session, currentUser.id, d.hwid) }; reloadDevices() } }.background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.2f)), DsRadius.Sm).pressScale(0.9f).padding(horizontal = DsSpacing.Sm, vertical = DsSpacing.Xs)) { Text(stringResource(R.string.ud_device_forget), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassRed) }
                                    }
                                }
                            }
                        }
                    }

                    // ── IPهای آنلاین (فقط با مجوزِ nodes.stats)
                    if (session != null && canNodeStats) {
                        val ips = onlineIps
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                            SectionLabel(stringResource(R.string.ud_online_ips))
                            Column(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(DsSpacing.Md), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                    Text(
                                        when {
                                            ips == null -> stringResource(R.string.ud_online_ips_unknown)
                                            ips.isEmpty() -> stringResource(R.string.ud_online_ips_empty)
                                            else -> stringResource(R.string.ud_online_ips_count, ips.size, ips.sumOf { it.connections })
                                        },
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f)
                                    )
                                    Box(Modifier.heightIn(min = 40.dp).clip(DsRadius.Full).clickable(enabled = !onlineIpsLoading) { loadOnlineIps() }.background(theme.accentPrimary.copy(0.10f)).pressScale(0.95f).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Xs)) {
                                        Text(stringResource(if (onlineIpsLoading) R.string.ud_checking else R.string.ud_check), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary)
                                    }
                                }
                                ips?.take(6)?.forEach { entry ->
                                    Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                        MrmText(entry.ip, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true, modifier = Modifier.weight(1f))
                                        MrmText(nodeNames[entry.nodeId] ?: stringResource(R.string.st_node_unknown, entry.nodeId), fontSize = 11.sp, color = theme.mutedColor, maxLines = 1)
                                        Text(stringResource(R.string.ud_connections, entry.connections), fontSize = 11.sp, color = theme.mutedColor, maxLines = 1)
                                    }
                                }
                                if (ips != null && ips.size > 6) Text(stringResource(R.string.ud_more_items, ips.size - 6), fontSize = 11.sp, color = theme.mutedColor)
                            }
                        }
                    }

                    // ── تاریخچهٔ گرفتنِ اشتراک
                    subUpdates?.let { list ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                            SectionLabel(stringResource(R.string.ud_sub_updates))
                            Column(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md).padding(DsSpacing.Md), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                Text(
                                    if (list.count == 0) stringResource(R.string.ud_sub_updates_empty) else stringResource(R.string.ud_sub_updates_count, list.count),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                                )
                                list.updates.forEach { u ->
                                    Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            MrmText(u.client.ifBlank { "—" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                                            u.ip?.let { MrmText(it, fontSize = 11.sp, color = theme.mutedColor, maxLines = 1, isTechnical = true) }
                                        }
                                        MrmText(lastSeenShort(u.createdAt, false).ifBlank { JalaliCalendar.isoToShamsi(u.createdAt) }, fontSize = 11.sp, color = theme.mutedColor, maxLines = 1, isTechnical = true)
                                    }
                                }
                            }
                        }
                    }

                    currentUser.nextPlan?.let { np ->
                        Column(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.accentPrimary.copy(0.06f)).border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.15f)), DsRadius.Md).padding(DsSpacing.Md), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                                RoundedAppIcon(AppIcon.Template, tint = theme.accentPrimary, size = 11.dp)
                                Text(stringResource(R.string.ud_next_plan), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, modifier = Modifier.weight(1f))
                                if (session != null) Box(Modifier.heightIn(min = 40.dp).clip(DsRadius.Full).clickable { nextPlanConfirm = true }.background(theme.primaryBrush).border(BorderStroke(1.dp, theme.primaryEdge), DsRadius.Full).pressScale(0.95f).padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Xs)) { Text(stringResource(R.string.ud_next_plan_activate), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.onPrimary) }
                            }
                            Text("${np.dataLimit?.let { formatBytes(it) } ?: unlimitedLabel} · ${(np.expireSeconds ?: 0L) / 86400L}d", fontSize = 11.sp, color = theme.mutedColor)
                        }
                    }

                    if (debtorInfo != null || onMarkDebtor != null) {
                        Row(
                            Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(DsRadius.Md).clickable { if (debtorInfo != null) onClearDebt?.invoke() else onMarkDebtor?.invoke() }.background(if (debtorInfo != null) GlassRed.copy(0.06f) else theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, if (debtorInfo != null) GlassRed.copy(0.15f) else theme.borderColor), DsRadius.Md)
                                .pressScale(0.98f).padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Mid),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
                        ) {
                            RoundedAppIcon(if (debtorInfo != null) AppIcon.Warning else AppIcon.Money, tint = if (debtorInfo != null) GlassRed else theme.accentPrimary, size = 14.dp)
                            Text(if (debtorInfo != null) stringResource(R.string.ud_debt_of, debtorInfo.amount.toString(), debtorInfo.currency) else stringResource(R.string.ud_invoice), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (debtorInfo != null) GlassRed else theme.inkColor, modifier = Modifier.weight(1f))
                            Text("›", fontSize = 14.sp, color = theme.mutedColor)
                        }
                    }

                    // بستن تنها
                    Box(
                        Modifier.padding(vertical = 1.dp).fillMaxWidth().height(38.dp).clip(DsRadius.Md).clickable(onClick = onDismiss).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                            .pressScale(0.98f), contentAlignment = Alignment.Center
                    ) { Text(closeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
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
    if (notesSheetOpen) NotesSheetDialog(note = currentUser.note.orEmpty(), onDismiss = { notesSheetOpen = false }, onEdit = { notesSheetOpen = false; editOpen = true })
    if (qrOpen) SubscriptionQrDialog(user = currentUser, onDismiss = { qrOpen = false })
    if (usageConfirm) ConfirmActionDialog(title = stringResource(R.string.ud_reset_data_title), message = stringResource(R.string.ud_reset_data_msg), onDismiss = { usageConfirm = false }, onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0L); onResetUsage() })
    if (devicesResetConfirm && session != null) ConfirmActionDialog(title = stringResource(R.string.ud_devices_reset_title), message = stringResource(R.string.ud_devices_reset_msg), onDismiss = { devicesResetConfirm = false }, onConfirm = { devicesResetConfirm = false; scope.launch { runCatching { PanelApi.resetUserDevices(session, currentUser.id) }; reloadDevices() } })
    if (nextPlanConfirm && session != null) ConfirmActionDialog(title = stringResource(R.string.ud_next_plan_activate_title), message = stringResource(R.string.ud_next_plan_activate_msg), onDismiss = { nextPlanConfirm = false }, onConfirm = { nextPlanConfirm = false; scope.launch { runCatching { PanelApi.activateNextPlan(session, currentUser) }.onSuccess { runCatching { PanelApi.user(session, currentUser) }.onSuccess { currentUser = it } } } })
    if (revokeConfirm && session != null) ConfirmActionDialog(title = stringResource(R.string.ud_revoke_title), message = stringResource(R.string.ud_revoke_msg), onDismiss = { revokeConfirm = false }, onConfirm = { revokeConfirm = false; scope.launch { runCatching { PanelApi.revokeSubscription(session, currentUser) }.onSuccess { currentUser = it; com.mrm.pgmanager.ui.feedback.AppFeedback.success(revokedMsg) }.onFailure { com.mrm.pgmanager.ui.feedback.AppFeedback.error(subFailedMsg) } } })
    if (expiryConfirm) ResetExpiryDurationDialog(onDismiss = { expiryConfirm = false }, onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) })
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
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = DsSpacing.Mid, bottom = DsSpacing.Sm), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor)) } }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = DsSpacing.Md)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.Xl, vertical = DsSpacing.Md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                Box(Modifier.size(32.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(0.12f)), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 16.dp) }
                Text(stringResource(R.string.ud_note_sheet_title), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, modifier = Modifier.weight(1f))
                Box(Modifier.padding(4.dp).size(32.dp).clip(DsRadius.Full).clickable(onClick = onDismiss).background(theme.searchBgColor).pressScale(0.9f), contentAlignment = Alignment.Center) { Text("×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
            }
            Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Xl, vertical = DsSpacing.Screen), verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)) {
                androidx.compose.foundation.text.selection.SelectionContainer { Text(note.trim(), fontSize = 13.5.sp, color = theme.inkColor, lineHeight = 20.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.Lg, vertical = DsSpacing.Lg), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                PGSecondaryButton(
                    text = stringResource(R.string.ud_note_copy),
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("note", note))
                        com.mrm.pgmanager.ui.feedback.AppFeedback.success(copiedMsg)
                    },
                    modifier = Modifier.weight(1f),
                    icon = AppIcon.Copy
                )
                PGPrimaryButton(
                    text = stringResource(R.string.ud_note_edit),
                    onClick = { onDismiss(); onEdit() },
                    modifier = Modifier.weight(1f),
                    icon = AppIcon.Edit
                )
            }
        }
    }
}
