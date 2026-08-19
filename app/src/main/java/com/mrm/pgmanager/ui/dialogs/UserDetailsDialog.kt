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
            .heightIn(min = 50.dp)
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

    val copiedMsg = stringResource(R.string.ud_copied)
    val closeLabel = stringResource(R.string.ud_close)
    val subFailedMsg = stringResource(R.string.ud_sub_failed)
    val unlimitedLabel = stringResource(R.string.ud_unlimited)

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
    val isActive = currentUser.status != "disabled"

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
                    // وضعیت به‌صورت نقطه + متن؛ کوچک ولی خوانا.
                    Row(
                        Modifier.height(26.dp).clip(DsRadius.Full)
                            .background((if (isActive) GlassGreen else GlassRed).copy(0.13f))
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier.size(6.dp).clip(RoundedCornerShape(50))
                                .background(if (isActive) GlassGreen else GlassRed)
                        )
                        Text(
                            stringResource(if (isActive) R.string.active else R.string.disabled),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) GlassGreen else GlassRed
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
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── ۲) کارتِ قهرمان: مصرف
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Xl)
                            .background(theme.cardSurfaceColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                            .padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
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

                    // ── توضیحاتِ کاربر (فقط اگر وجود داشته باشد)
                    if (!currentUser.note.isNullOrBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(DsRadius.Lg)
                                .background(theme.searchBgColor)
                                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoundedAppIcon(AppIcon.Note, tint = theme.accentPrimary, size = 15.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    stringResource(R.string.ud_note),
                                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor
                                )
                                Text(
                                    currentUser.note.orEmpty(),
                                    fontSize = 11.5.sp, color = theme.inkColor,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
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
                        }
                    }

                    // ── ۴) عملیات: ویرایش پررنگ‌ترین است، بقیه هم‌وزن در یک شبکه
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(stringResource(R.string.ud_manage))
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
                                icon = if (isActive) AppIcon.StatusDisabled else AppIcon.CheckCircle,
                                label = stringResource(if (isActive) R.string.ud_disable else R.string.ud_enable),
                                accent = if (isActive) GlassAmber else GlassGreen,
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

    if (expiryConfirm) {
        ResetExpiryDurationDialog(
            onDismiss = { expiryConfirm = false },
            onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) }
        )
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
