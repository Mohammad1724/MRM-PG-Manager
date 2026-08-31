package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 *  دیالوگ جزئیات کاربر — بازطراحی‌شده
 *
 *  مشکلِ نسخهٔ قبل «شلوغی» بود: هفت جعبهٔ هم‌وزن پشتِ سر هم، همه با یک اندازه
 *  و یک رنگ، بدونِ اینکه معلوم باشد کدام مهم‌تر است؛ و دکمه‌ها فقط متن بودند
 *  که اسکن‌کردنشان با چشم کند است.
 *
 *  چیدمانِ جدید بر پایهٔ سه لایه است:
 *
 *   ۱. سربرگِ ثابت      — هویتِ کاربر (آواتار، نام، آخرین بازدید، وضعیت)
 *   ۲. کارتِ قهرمان     — مصرف: بزرگ‌ترین و پررنگ‌ترین چیز، چون همان است که
 *                          کاربر برای دیدنش این صفحه را باز می‌کند
 *   ۳. بقیه به ترتیبِ    — لینک اشتراک ← عملیات ← مالی
 *      اهمیت، هرکدام با
 *      عنوانِ کوچکِ محو
 *
 *  هیچ دکمه‌ای حذف نشده؛ فقط گروه‌بندی و وزنِ بصری‌شان عوض شده و همه آیکون
 *  گرفته‌اند تا با یک نگاه پیدا شوند.
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

/** عنوانِ کوچکِ بالای هر بخش — لنگرِ بصری برای چشم، بدونِ اشغال فضا. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = LocalThemeState.current.mutedColor,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/**
 * کاشیِ عملیات: آیکونِ رنگی روی برچسب.
 *
 * جایگزینِ دکمه‌های فقط‌متنیِ قبلی؛ آیکون باعث می‌شود عملیات با یک نگاه
 * تشخیص داده شود و برچسبِ زیرش ابهام را برمی‌دارد.
 */
@Composable
private fun ActionTile(
    icon: AppIcon,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val bg = if (filled) accent else accent.copy(0.10f)
    val fg = if (filled) Color(0xFF422006) else accent
    Column(
        // ارتفاعِ کف به‌جای ارتفاعِ ثابت: با فونتِ بزرگِ سیستم، برچسب بریده نشود.
        modifier
            .heightIn(min = 46.dp)
            .clip(DsRadius.Lg)
            .background(bg)
            .border(
                BorderStroke(DsBorder.Hairline, if (filled) Color.Transparent else accent.copy(0.24f)),
                DsRadius.Lg
            )
            .semantics { contentDescription = label }
            .pressScale(0.95f)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RoundedAppIcon(icon, tint = fg, size = 17.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) fg else theme.inkColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** ردیفِ عملیات درونِ بخشِ مالی. */
@Composable
private fun BillingRow(
    icon: AppIcon,
    label: String,
    accent: Color,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val bg = if (filled) accent.copy(0.85f) else theme.searchBgColor
    val fg = if (filled) Color(0xFF202124) else accent
    Row(
        Modifier.fillMaxWidth().heightIn(min = 38.dp).clip(DsRadius.Lg)
            .background(bg)
            .border(
                BorderStroke(DsBorder.Hairline, if (filled) Color.Transparent else accent.copy(0.22f)),
                DsRadius.Lg
            )
            .pressScale(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        RoundedAppIcon(icon, tint = fg, size = 16.dp)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** چیپِ کوچکِ کنارِ لینک اشتراک. */
@Composable
private fun SubChip(icon: AppIcon, label: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    // قبلاً کپسولِ کاملاً گِرد با پُرکنندهٔ طلاییِ کم‌رنگ و متنِ طلایی بود: دو لکهٔ
    // طلایی روی طلایی که نه با کارت‌های این دیالوگ می‌خواند نه با دکمه‌های
    // بقیهٔ اپ. حالا همان زبانِ بصریِ کاشی‌های اپ را دارد — گوشهٔ ۱۰، سطحِ کارت
    // روی زمینهٔ خاکستریِ فیلد، حاشیهٔ مویی، و رنگِ تم فقط روی آیکون.
    Row(
        Modifier.heightIn(min = 30.dp).clip(DsRadius.Md)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
            .semantics { contentDescription = label }
            .pressScale(0.94f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        RoundedAppIcon(icon, tint = theme.accentPrimary, size = 13.dp)
        Text(
            label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
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
    var usageConfirm by remember { mutableStateOf(false) }
    var expiryConfirm by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }
    var availableTemplates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesFailed by remember { mutableStateOf(false) }
    var billingOpen by remember { mutableStateOf(false) }
    var revokeConfirm by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var notesSheetOpen by remember { mutableStateOf(false) }
    var devicesResetConfirm by remember { mutableStateOf(false) }
    var nextPlanConfirm by remember { mutableStateOf(false) }
    // دستگاه‌های ثبت‌شده (HWID). اپ تا حالا فقط سقفِ تعداد را می‌گرفت و خودِ
    // دستگاه‌ها را نه می‌شد دید نه پاک کرد.
    var devices by remember(user.id) { mutableStateOf<List<com.mrm.pgmanager.data.model.UserDevice>>(emptyList()) }
    fun reloadDevices() {
        if (session == null) return
        scope.launch { runCatching { PanelApi.userDevices(session, currentUser.id) }.onSuccess { devices = it } }
    }
    LaunchedEffect(user.id, session) { reloadDevices() }

    val copiedMsg = stringResource(R.string.ud_copied)
    val closeLabel = stringResource(R.string.ud_close)
    val subFailedMsg = stringResource(R.string.ud_sub_failed)
    val unlimitedLabel = stringResource(R.string.ud_unlimited)
    val groupsLabel = stringResource(R.string.ud_group_names)
    val createdLabel = stringResource(R.string.ud_created_at)
    val lifetimeLabel = stringResource(R.string.ud_lifetime)
    val ownerLabel = stringResource(R.string.ud_owner)
    val revokedMsg = stringResource(R.string.ud_revoked)
    val devicesLabel = stringResource(R.string.ud_devices)
    val moreLabel = stringResource(R.string.ud_more)
    val nextPlanLabel = stringResource(R.string.ud_next_plan)

    // دریافتِ لینکِ اشتراک به‌صورت lazy (بعضی پاسخ‌های پنل subUrl ندارند).
    fun ensureSub(onResult: (String) -> Unit) {
        if (currentUser.subUrl.isNotBlank()) {
            onResult(currentUser.subUrl)
        } else if (session != null) {
            scope.launch {
                runCatching { PanelApi.user(session, currentUser.username) }.onSuccess {
                    currentUser = it
                    onResult(it.subUrl)
                }.onFailure {
                    android.widget.Toast.makeText(context, subFailedMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            onResult(currentUser.subUrl)
        }
    }

    val unlimitedData = currentUser.dataLimit == 0L
    val totalLabel = if (unlimitedData) unlimitedLabel else formatBytes(currentUser.dataLimit)
    val percentage = if (currentUser.dataLimit > 0L)
        ((currentUser.usedTraffic * 100f / currentUser.dataLimit).toInt()).coerceIn(0, 100) else 0
    val usageColor = when { percentage < 70 -> GlassGreen; percentage < 90 -> GlassAmber; else -> GlassRed }
    val remainingData = (currentUser.dataLimit - currentUser.usedTraffic).coerceAtLeast(0L)
    // نوارِ مصرف به‌جای پرش، پر می‌شود.
    val animatedFraction by animateFloatAsState(
        targetValue = if (unlimitedData) 1f else percentage / 100f,
        animationSpec = DsAnim.enter(),
        label = "usageBar"
    )
    // وضعیت واقعی کاربر - قبلاً فقط disabled چک می‌شد و expired/limited هم فعال نشان داده می‌شد
    val isDisabled = currentUser.status == "disabled"
    val isActive = currentUser.status == "active"
    val statusColor = when (currentUser.status) {
        "active" -> GlassGreen
        "expired" -> GlassRed
        "limited" -> GlassAmber
        "disabled" -> Color(0xFF8A8A8A)
        "on_hold" -> DsSemantic.Violet
        else -> theme.mutedColor
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
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
            ) {
                // ── ۱) سربرگِ ثابت: بیرونِ ناحیهٔ اسکرول می‌ماند تا هنگام پایین
                //     رفتن هم معلوم باشد داری کدام کاربر را می‌بینی.
                Row(
                    Modifier.fillMaxWidth()
                        .background(theme.cardSurfaceColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // آواتار: حرفِ اولِ نام.
                    //
                    // وضعیتِ آنلاین قبلاً یک حلقهٔ سبزِ ۲dp دورِ کلِ آواتار بود؛ سبزِ
                    // اشباع دورِ دایرهٔ طلایی با هیچ‌جای دیگرِ اپ نمی‌خواند و بیشتر
                    // شبیهِ خطا دیده می‌شد تا نشانه. حالا همان نقطهٔ کوچکی است که
                    // در فهرستِ کاربران هم استفاده می‌شود، با یک حلقهٔ هم‌رنگِ
                    // پس‌زمینه که از لبهٔ آواتار جدایش می‌کند.
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(36.dp).clip(DsRadius.Full)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(theme.accentPrimary.copy(0.30f), theme.accentPrimary.copy(0.12f))
                                    )
                                )
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Full),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                currentUser.username.take(1).uppercase(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = theme.inkColor
                            )
                        }
                        if (currentUser.isOnline) {
                            Box(
                                Modifier.align(Alignment.BottomEnd)
                                    .size(12.dp).clip(DsRadius.Full)
                                    .background(theme.cardSurfaceColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(8.dp).clip(DsRadius.Full).background(GlassGreen))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MrmText(
                            currentUser.username,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            isTechnical = true
                        )
                        MrmText(
                            lastSeenText(currentUser.onlineAt, currentUser.isOnline),
                            fontSize = 10.5.sp,
                            color = theme.mutedColor,
                            maxLines = 1,
                            isTechnical = true
                        )
                    }
                    // وضعیت به‌صورت نقطه + متن؛ حالا وضعیت واقعی (منقضی/محدود/غیرفعال) را نشان می‌دهد نه فقط فعال/غیرفعال
                    Row(
                        Modifier.height(26.dp).clip(DsRadius.Full)
                            .background(statusColor.copy(0.13f))
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier.size(6.dp).clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                        Text(
                            statusLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Box(
                        Modifier.size(28.dp).clip(DsRadius.Full)
                            .background(theme.searchBgColor)
                            .semantics { contentDescription = closeLabel }
                            .pressScale(0.9f)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("×", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
                }
                Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── ۲) کارتِ قهرمان: مصرف
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Xl)
                            .background(theme.cardSurfaceColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.ud_used),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor
                                )
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    MrmText(
                                        formatBytes(currentUser.usedTraffic),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        isTechnical = true
                                    )
                                    MrmText(
                                        "/ $totalLabel",
                                        fontSize = 11.sp,
                                        color = theme.mutedColor,
                                        fontWeight = FontWeight.Bold,
                                        isTechnical = true,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                            }
                            // درصد، بزرگ و هم‌رنگِ وضعیتِ مصرف.
                            Text(
                                if (unlimitedData) "∞" else "$percentage%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = usageColor,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        }
                        // نوارِ ضخیم‌تر از قبل (۸ به‌جای ۴) تا نقشِ «قهرمان» را بازی کند.
                        Box(
                            Modifier.fillMaxWidth().height(7.dp).clip(DsRadius.Full)
                                .background(if (theme.isDark) Color.White.copy(0.10f) else Color(0xFFF1F2F4))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(animatedFraction).fillMaxHeight()
                                    .clip(DsRadius.Full)
                                    .background(
                                        if (unlimitedData) Brush.horizontalGradient(
                                            listOf(theme.accentPrimary.copy(0.55f), theme.accentPrimary)
                                        ) else Brush.horizontalGradient(listOf(usageColor.copy(0.65f), usageColor))
                                    )
                            )
                        }
                        // دو معیارِ باقی‌مانده، هم‌وزن و کنارِ هم.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill(
                                icon = AppIcon.Timer,
                                label = stringResource(R.string.ud_remaining_time),
                                value = daysLeftLabel(currentUser.expire),
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                icon = AppIcon.Storage,
                                label = stringResource(R.string.ud_remaining_data),
                                value = if (unlimitedData) unlimitedLabel else formatBytes(remainingData),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── ۳) لینک اشتراک
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SectionLabel(stringResource(R.string.ud_subscription))
                        Row(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                .background(theme.searchBgColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Link, tint = theme.mutedColor, size = 15.dp)
                            MrmText(
                                currentUser.subUrl.ifBlank { "—" },
                                fontSize = 10.sp,
                                color = theme.mutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                isTechnical = true,
                                modifier = Modifier.weight(1f)
                            )
                            SubChip(AppIcon.Copy, stringResource(R.string.ud_copy)) {
                                ensureSub { url ->
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", url))
                                    android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            SubChip(AppIcon.Qr, stringResource(R.string.ud_qr)) { ensureSub { qrOpen = true } }
                            // باطل‌کردنِ لینک: تنها واکنشِ درست به لو رفتنِ لینک.
                            if (session != null) {
                                SubChip(AppIcon.Reset, stringResource(R.string.ud_revoke)) { revokeConfirm = true }
                            }
                        }
                    }

                    // ── ۳.۵) یادداشت — کارتِ جمع‌وجور در صفحه‌ی اصلی، بدونِ شلوغی
                    // فقط یک ردیف: آیکون + پیش‌نمایشِ ۲ خطی + دکمه‌ی نمایش کامل
                    // تپ -> شیتِ کشوییِ تمام‌صفحه با متنِ کامل، کپی و ویرایش
                    NotePreviewCard(
                        note = currentUser.note,
                        onOpen = { notesSheetOpen = true },
                        onAdd = { editOpen = true }
                    )

                    // ── ۴) عملیات: ویرایش پررنگ‌ترین است، بقیه هم‌وزن در یک شبکه
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(stringResource(R.string.ud_manage))
                        // سه ردیفِ دوتایی. یک‌بار سه‌تایی شد تا صفحه کوتاه‌تر شود،
                        // ولی کاشیِ باریک برچسبش را می‌خورد و خوانا نبود؛ ارتفاعی
                        // که آنجا صرفه‌جویی می‌شد، از جای دیگر (حذفِ نمودار و
                        // جمع‌شدنِ جزئیات) درآمد.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(
                                icon = AppIcon.Edit,
                                label = stringResource(R.string.ud_edit),
                                accent = theme.accentPrimary,
                                filled = true,
                                modifier = Modifier.weight(2f)
                            ) { editOpen = true }
                            ActionTile(
                                icon = AppIcon.Template,
                                label = stringResource(R.string.ud_template),
                                accent = theme.accentPrimary,
                                modifier = Modifier.weight(1f)
                            ) { templatePickerOpen = true }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(
                                icon = AppIcon.Reset,
                                label = stringResource(R.string.ud_reset_data),
                                accent = DsSemantic.Violet,
                                modifier = Modifier.weight(1f)
                            ) { usageConfirm = true }
                            ActionTile(
                                icon = AppIcon.Calendar,
                                label = stringResource(R.string.ud_reset_time),
                                accent = DsSemantic.Violet,
                                modifier = Modifier.weight(1f)
                            ) { expiryConfirm = true }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(
                                icon = if (isDisabled) AppIcon.CheckCircle else AppIcon.StatusDisabled,
                                label = stringResource(if (isDisabled) R.string.ud_enable else R.string.ud_disable),
                                accent = if (isDisabled) GlassGreen else GlassAmber,
                                modifier = Modifier.weight(1f)
                            ) { onToggle() }
                            ActionTile(
                                icon = AppIcon.Delete,
                                label = stringResource(R.string.ud_delete),
                                accent = GlassRed,
                                modifier = Modifier.weight(1f)
                            ) { onDelete() }
                        }
                    }

                    // ── جزئیاتِ بیشتر: جمع‌شونده
                    //
                    // این شیت داشت بلند می‌شد و همه‌چیز پشتِ اسکرول می‌رفت. چیزی که
                    // کاربر برایش این صفحه را باز می‌کند (مصرف، لینک، دکمه‌های
                    // مدیریت) بالا و بدونِ اسکرول می‌ماند؛ گروه‌ها، تاریخ، دستگاه‌ها
                    // و یادداشت که گاه‌به‌گاه لازم‌اند، اینجا جمع شده‌اند.
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Lg)
                            .background(theme.cardSurfaceColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(DsRadius.Md)
                                .pressScale(0.985f)
                                .clickable { moreOpen = !moreOpen }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Tune, tint = theme.mutedColor, size = 15.dp)
                            Text(
                                moreLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                color = theme.inkColor, modifier = Modifier.weight(1f)
                            )
                            Text(if (moreOpen) "▴" else "▾", fontSize = 11.sp, color = theme.mutedColor)
                        }
                        AnimatedVisibility(
                            visible = moreOpen,
                            enter = DsTransition.expandEnter,
                            exit = DsTransition.expandExit
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                    // ── حقایقِ کاربر: گروه‌ها، تاریخِ ساخت، مصرفِ مادام‌العمر، مالک
                    //
                    // هیچ‌کدامِ این‌ها قبلاً نشان داده نمی‌شدند، در حالی که پنل
                    // همه‌شان را در همان پاسخِ کاربر برمی‌گرداند.
                    run {
                        val facts = buildList {
                            if (currentUser.groupNames.isNotEmpty()) {
                                add(Triple(AppIcon.Folder, groupsLabel, currentUser.groupNames.joinToString("، ")))
                            }
                            currentUser.createdAt?.takeIf { it.isNotBlank() }?.let {
                                add(Triple(AppIcon.Calendar, createdLabel, JalaliCalendar.isoToShamsi(it).ifBlank { it.take(10) }))
                            }
                            if (currentUser.lifetimeUsedTraffic > currentUser.usedTraffic) {
                                add(Triple(AppIcon.Storage, lifetimeLabel, formatBytes(currentUser.lifetimeUsedTraffic)))
                            }
                            currentUser.ownerAdmin?.let { add(Triple(AppIcon.User, ownerLabel, it)) }
                        }
                        if (facts.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                    .background(theme.searchBgColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                facts.forEach { (icon, label, value) ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RoundedAppIcon(icon, tint = theme.mutedColor, size = 13.dp)
                                        Text(label, fontSize = 10.sp, color = theme.mutedColor, maxLines = 1)
                                        Spacer(Modifier.weight(1f))
                                        MrmText(
                                            value, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(2f, fill = false),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── پلنِ بعدی (اگر در صف باشد)
                    currentUser.nextPlan?.let { np ->
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                .background(theme.accentPrimary.copy(0.08f))
                                .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.22f)), DsRadius.Lg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Template, tint = theme.accentPrimary, size = 13.dp)
                                Text(nextPlanLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                Spacer(Modifier.weight(1f))
                                if (session != null) {
                                    SubChip(AppIcon.Check, stringResource(R.string.ud_next_plan_activate)) { nextPlanConfirm = true }
                                }
                            }
                            val desc = when {
                                np.templateId != null -> stringResource(
                                    R.string.ud_next_plan_template,
                                    availableTemplates.firstOrNull { it.id == np.templateId }?.name ?: "#${np.templateId}"
                                )
                                else -> stringResource(
                                    R.string.ud_next_plan_manual,
                                    np.dataLimit?.let { formatBytes(it) } ?: unlimitedLabel,
                                    ((np.expireSeconds ?: 0L) / 86400L).toInt()
                                )
                            }
                            Text(desc, fontSize = 10.sp, color = theme.mutedColor)
                            if (np.addRemainingTraffic) {
                                Text(stringResource(R.string.ud_next_plan_carry), fontSize = 9.5.sp, color = theme.mutedLightColor)
                            }
                        }
                    }

                    // ── دستگاه‌های ثبت‌شده
                    if (session != null && (devices.isNotEmpty() || currentUser.hwidLimit != null)) {
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                .background(theme.cardSurfaceColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Device, tint = theme.accentPrimary, size = 13.dp)
                                Text(devicesLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                                Spacer(Modifier.weight(1f))
                                currentUser.hwidLimit?.let {
                                    PGBadge(stringResource(R.string.ud_devices_count, devices.size, it))
                                }
                                if (devices.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    SubChip(AppIcon.Delete, stringResource(R.string.ud_devices_reset)) { devicesResetConfirm = true }
                                }
                            }
                            if (devices.isEmpty()) {
                                Text(stringResource(R.string.ud_devices_empty), fontSize = 10.sp, color = theme.mutedColor)
                            }
                            devices.forEach { d ->
                                Row(
                                    Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor)
                                        .border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm)
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        MrmText(
                                            listOfNotNull(d.deviceModel, d.deviceOs, d.osVersion)
                                                .joinToString(" · ").ifBlank { d.hwid.take(16) },
                                            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        d.lastUsedAt?.let {
                                            Text(
                                                stringResource(R.string.ud_device_last_used, JalaliCalendar.isoToShamsi(it).ifBlank { it.take(10) }),
                                                fontSize = 9.sp, color = theme.mutedColor, maxLines = 1
                                            )
                                        }
                                    }
                                    Box(
                                        Modifier.clip(DsRadius.Sm).background(theme.cardSurfaceColor)
                                            .border(BorderStroke(DsBorder.Hairline, GlassRed.copy(0.28f)), DsRadius.Sm)
                                            .pressScale(0.94f)
                                            .clickable {
                                                scope.launch {
                                                    runCatching { PanelApi.deleteUserDevice(session, currentUser.id, d.hwid) }
                                                    reloadDevices()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(stringResource(R.string.ud_device_forget), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                                    }
                                }
                            }
                        }
                    }

                    // یادداشت از اینجا حذف شد و به کارتِ مستقلِ صفحه‌ی اصلی منتقل شد (NotePreviewCard) — جایگزینِ نسخه‌ی expandableِ قبلی
                            }
                        }
                    }

                    // ── ۵) مالی: جمع‌شونده، چون همیشه لازم نیست
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SectionLabel(stringResource(R.string.ud_financial))
                        val hasDebt = debtorInfo != null
                        val billingAccent = if (hasDebt) GlassRed else theme.accentPrimary
                        Column(
                            Modifier.fillMaxWidth().clip(DsRadius.Xl)
                                .background(if (hasDebt) billingAccent.copy(0.07f) else theme.cardSurfaceColor)
                                .border(
                                    BorderStroke(DsBorder.Hairline, if (hasDebt) billingAccent.copy(0.28f) else theme.borderColor),
                                    DsRadius.Xl
                                )
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(DsRadius.Lg)
                                    .pressScale(0.985f)
                                    .clickable { billingOpen = !billingOpen }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                RoundedAppIcon(
                                    if (hasDebt) AppIcon.Warning else AppIcon.Money,
                                    tint = billingAccent, size = 17.dp
                                )
                                Text(
                                    if (hasDebt) stringResource(
                                        R.string.ud_debt_of,
                                        debtorInfo!!.amount.toString(), debtorInfo.currency
                                    ) else stringResource(R.string.ud_invoice),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (hasDebt) billingAccent else theme.inkColor,
                                    modifier = Modifier.weight(1f)
                                )
                                val rotation by animateFloatAsState(
                                    targetValue = if (billingOpen) 180f else 0f,
                                    animationSpec = DsAnim.normal(),
                                    label = "billingChevron"
                                )
                                RoundedAppIcon(
                                    AppIcon.ChevronDown, tint = billingAccent, size = 15.dp,
                                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                                )
                            }
                            AnimatedVisibility(
                                visible = billingOpen,
                                enter = DsTransition.expandEnter,
                                exit = DsTransition.expandExit
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (debtorInfo != null) {
                                        val stamp = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                                            .format(java.util.Date(debtorInfo.markedAt))
                                        Text(
                                            stringResource(R.string.ud_marked_at, stamp) +
                                                if (debtorInfo.notes.isNotBlank()) " · ${debtorInfo.notes}" else "",
                                            fontSize = 10.sp, color = theme.mutedColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                        if (debtorInfo.autoDisabled) {
                                            Row(
                                                Modifier.fillMaxWidth().clip(DsRadius.Md)
                                                    .background(GlassRed.copy(0.12f)).padding(9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                                            ) {
                                                RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 13.dp)
                                                Text(
                                                    stringResource(R.string.ud_auto_disabled),
                                                    fontSize = 10.sp, color = GlassRed, fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        BillingRow(AppIcon.CheckCircle, stringResource(R.string.ud_clear_debt), GlassGreen, filled = true) { onClearDebt?.invoke() }
                                        BillingRow(AppIcon.Edit, stringResource(R.string.ud_edit_debt), GlassRed) { onMarkDebtor?.invoke() }
                                    } else {
                                        BillingRow(AppIcon.Warning, stringResource(R.string.ud_mark_debtor), GlassRed) { onMarkDebtor?.invoke() }
                                    }
                                    BillingRow(AppIcon.Receipt, stringResource(R.string.ud_invoice), theme.accentPrimary) { onInvoice?.invoke() }
                                }
                            }
                        }
                    }

                    SecondaryButton(
                        closeLabel,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (templatePickerOpen) {
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            val result = runCatching { session?.let { PanelApi.userTemplates(it) } ?: emptyList() }
            availableTemplates = result.getOrDefault(emptyList())
            templatesFailed = result.isFailure
            templatesLoading = false
        }
        BulkApplyTemplateDialog(
            templates = availableTemplates,
            selectedCount = 1,
            onDismiss = { templatePickerOpen = false },
            onApply = { templateId, note -> templatePickerOpen = false; onApplyTemplate?.invoke(templateId, note) },
            isLoading = templatesLoading,
            loadFailed = templatesFailed
        )
    }

    if (editOpen) {
        UserEditorDialog(
            initial = currentUser,
            onDismiss = { editOpen = false },
            onSave = onSave,
            onToggle = onToggle,
            onApplyTemplateToUser = onApplyTemplate,
            session = session
        )
    }

    if (notesSheetOpen) {
        NotesSheetDialog(
            note = currentUser.note.orEmpty(),
            onDismiss = { notesSheetOpen = false },
            onEdit = { editOpen = true }
        )
    }

    if (qrOpen) {
        SubscriptionQrDialog(user = currentUser, onDismiss = { qrOpen = false })
    }

    if (usageConfirm) {
        ConfirmActionDialog(
            title = stringResource(R.string.ud_reset_data_title),
            message = stringResource(R.string.ud_reset_data_msg),
            onDismiss = { usageConfirm = false },
            onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0L); onResetUsage() }
        )
    }

    if (devicesResetConfirm && session != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.ud_devices_reset_title),
            message = stringResource(R.string.ud_devices_reset_msg),
            onDismiss = { devicesResetConfirm = false },
            onConfirm = {
                devicesResetConfirm = false
                scope.launch {
                    runCatching { PanelApi.resetUserDevices(session, currentUser.id) }
                    reloadDevices()
                }
            }
        )
    }

    if (nextPlanConfirm && session != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.ud_next_plan_activate_title),
            message = stringResource(R.string.ud_next_plan_activate_msg),
            onDismiss = { nextPlanConfirm = false },
            onConfirm = {
                nextPlanConfirm = false
                scope.launch {
                    runCatching { PanelApi.activateNextPlan(session, currentUser.username) }
                        .onSuccess {
                            runCatching { PanelApi.user(session, currentUser.username) }.onSuccess { currentUser = it }
                        }
                }
            }
        )
    }

    if (revokeConfirm && session != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.ud_revoke_title),
            message = stringResource(R.string.ud_revoke_msg),
            onDismiss = { revokeConfirm = false },
            onConfirm = {
                revokeConfirm = false
                scope.launch {
                    runCatching { PanelApi.revokeSubscription(session, currentUser.username) }
                        .onSuccess {
                            currentUser = it
                            android.widget.Toast.makeText(context, revokedMsg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            android.widget.Toast.makeText(context, subFailedMsg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
        )
    }

    if (expiryConfirm) {
        ResetExpiryDurationDialog(
            onDismiss = { expiryConfirm = false },
            onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) }
        )
    }
}

/**
 * کارتِ جمع‌وجورِ یادداشت در صفحه‌ی اصلیِ جزئیات
 * - اگر یادداشت دارد: آیکون + عنوان + پیش‌نمایشِ ۲ خطی + چیپِ «نمایش کامل»
 * - اگر ندارد: آیکون + «بدون یادداشت» + دکمه‌ی «افزودن»
 * - کلیک روی کلِ کارت -> باز کردنِ شیتِ کشویی
 * طراحی: هم‌زبان با بقیه‌ی کارت‌ها، بدونِ شلوغی، ارتفاعِ کم
 */
@Composable
private fun NotePreviewCard(
    note: String?,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val theme = LocalThemeState.current
    val hasNote = !note.isNullOrBlank()
    Column(
        Modifier.fillMaxWidth().clip(DsRadius.Lg)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .pressScale(0.985f)
            .clickable { if (hasNote) onOpen() else onAdd() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 15.dp)
            Text(
                stringResource(R.string.ud_note),
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor,
                modifier = Modifier.weight(1f)
            )
            if (hasNote) {
                // چیپِ کوچکِ «نمایش کامل» — هم‌رنگِ تم، بدونِ شلوغی
                Row(
                    Modifier.clip(DsRadius.Full).background(theme.accentPrimary.copy(0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.ud_note_view_full),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary
                    )
                    Text("↗", fontSize = 9.sp, color = theme.accentPrimary)
                }
            } else {
                Text(
                    stringResource(R.string.ud_note_empty),
                    fontSize = 10.sp, color = theme.mutedColor
                )
            }
        }
        if (hasNote) {
            // پیش‌نمایشِ حداکثر ۲ خط، با ellipsis
            Text(
                note!!.trim(),
                fontSize = 11.5.sp, color = theme.inkColor,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        } else {
            // حالتِ خالی: دعوت به افزودن، بدونِ شلوغی
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ud_note_empty),
                    fontSize = 11.sp, color = theme.mutedColor,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier.clip(DsRadius.Md).background(theme.accentPrimary.copy(0.14f))
                        .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.24f)), DsRadius.Md)
                        .clickable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RoundedAppIcon(AppIcon.Edit, tint = theme.accentPrimary, size = 12.dp)
                    Text(
                        stringResource(R.string.ud_note_add),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.accentPrimary
                    )
                }
            }
        }
    }
}

/**
 * شیتِ کشوییِ یادداشت — صفحه‌ی تمام‌صفحه‌ی پایین‌رو
 * - پس‌زمینه‌ی تیره‌ی scrim، کارتِ گردِ بالا
 * - متنِ کاملِ یادداشت با قابلیتِ انتخاب و اسکرول
 * - دکمه‌های کپی و ویرایش
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheetDialog(
    note: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.ud_note_copied)
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    // ModalBottomSheet استاندارد Material3 — اسکریم، درگ، اینست‌ها خودکار
    // مشکلِ عکسِ قبلی (فضای سیاهِ خالیِ بالا + دکمه‌های بریده) به خاطرِ Dialog تودرتو بود
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.dialogBgColor,
        contentColor = theme.inkColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full)
                        .background(theme.borderColor)
                )
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 8.dp)
        ) {
            // هدر
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(32.dp).clip(DsRadius.Md)
                        .background(theme.accentPrimary.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 16.dp)
                }
                Text(
                    stringResource(R.string.ud_note_sheet_title),
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.size(32.dp).clip(DsRadius.Full)
                        .background(theme.searchBgColor)
                        .pressScale(0.9f)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text("×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor) }
            }
            Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
            // متنِ کامل — قابلِ اسکرول و انتخاب، بدونِ weightِ مشکل‌ساز
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        note.trim(),
                        fontSize = 13.5.sp, color = theme.inkColor,
                        lineHeight = 20.sp
                    )
                }
            }
            // اکشن‌ها — همیشه دیده می‌شوند، بریده نمی‌شوند
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // کپی
                Row(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(DsRadius.Lg)
                        .background(theme.searchBgColor)
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                        .pressScale(0.97f)
                        .clickable {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("note", note))
                            android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoundedAppIcon(AppIcon.Copy, tint = theme.mutedColor, size = 16.dp)
                    Text(stringResource(R.string.ud_note_copy), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                }
                // ویرایش
                Row(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(DsRadius.Lg)
                        .background(theme.accentPrimary)
                        .pressScale(0.97f)
                        .clickable { onDismiss(); onEdit() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoundedAppIcon(AppIcon.Edit, tint = Color(0xFF422006), size = 16.dp)
                    Text(stringResource(R.string.ud_note_edit), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF422006))
                }
            }
        }
    }
}

/** معیارِ کوچکِ داخلِ کارتِ مصرف (زمان/حجمِ باقی‌مانده). */
@Composable
private fun MetricPill(
    icon: AppIcon,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeState.current
    // ارتفاع قبلاً ثابت (۳۸dp) بود؛ روی دستگاه‌هایی که مقیاسِ فونتِ سیستم را
    // بزرگ کرده‌اند، دو خطِ متن از کپسول بیرون می‌زد و روی نوارِ مصرف می‌افتاد.
    // حالا کپسول با محتوا رشد می‌کند و متن هم وزن‌دار است تا بریده شود نه اینکه
    // از قاب بزند بیرون.
    Row(
        modifier.heightIn(min = 38.dp).clip(DsRadius.Lg)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundedAppIcon(icon, tint = theme.mutedColor, size = 15.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                label,
                fontSize = 9.sp,
                color = theme.mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
            MrmText(
                value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                isTechnical = true
            )
        }
    }
}