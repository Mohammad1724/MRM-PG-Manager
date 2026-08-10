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
        }
    }
    // دکمهٔ اکشن دیالوگ جزئیات: primary = کپسول اکسنت ۷۸٪ + متن تیره (بدون مرز)، neutral = کاشی خاکستری، destructive = قرمز کم‌رنگ.
    @Composable fun action(text: String, modifier: Modifier = Modifier, destructive: Boolean = false, primary: Boolean = false, height: androidx.compose.ui.unit.Dp = 44.dp, click: () -> Unit) {
        val bg = when { primary -> theme.accentPrimary; destructive -> GlassRed.copy(.10f); else -> theme.searchBgColor }
        val color = when { primary -> Color(0xFF202124); destructive -> GlassRed; else -> theme.inkColor }
        // حالت primary مرز نامرئی دارد (اکسنت با آلفای صفر) تا فقط پس‌زمینهٔ توپُر دیده شود؛ چیدمان ثابت می‌ماند.
        var borderColor = theme.borderColor
        if (destructive) borderColor = GlassRed.copy(.30f)
        if (primary) borderColor = theme.accentPrimary
        Box(
            modifier.height(height).clip(DsRadius.Md).background(bg)
                .border(BorderStroke(0.7.dp, borderColor), DsRadius.Md)
                .clickable(onClick = click),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontSize = if (height <= 30.dp) 9.sp else 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme) {
            Box(Modifier.fillMaxWidth().heightIn(max = 760.dp).clip(DsRadius.Xxl).background(theme.cardSurfaceColor).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl)) {
            Column(Modifier.fillMaxWidth().padding(17.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.user_details), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

                // هدر کاربر عمداً فشرده است: فقط یک ردیف کوتاه برای هویت، فعالیت و وضعیت.
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Lg)
                        .background(theme.cardSurfaceColor)
                        .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(Modifier.size(28.dp).clip(DsRadius.Xl).background(if (currentUser.isOnline) GlassGreen.copy(.14f) else Color.Gray.copy(.12f)), contentAlignment = Alignment.Center) { Box(Modifier.size(9.dp).clip(DsRadius.Xs).background(if (currentUser.isOnline) GlassGreen else Color.Gray)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        MrmText(currentUser.username, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                        MrmText(lastSeenText(currentUser.onlineAt, currentUser.isOnline), fontSize = 10.sp, color = theme.mutedColor, maxLines = 1, isTechnical = true)
                    }
                    val active = currentUser.status != "disabled"
                    Box(Modifier.height(26.dp).width(50.dp).clip(DsRadius.Sm).background((if (active) GlassGreen else GlassRed).copy(.13f)), contentAlignment = Alignment.Center) { Text(if (active) "فعال" else "غیرفعال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) GlassGreen else GlassRed) }
                }

                // توضیحات/یادداشت کاربر مستقیماً در پنجرهٔ جزئیات قابل مشاهده است.
                if (!currentUser.note.isNullOrBlank()) {
                    Row(
                        Modifier.fillMaxWidth().clip(DsRadius.Md)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(0.7.dp, theme.borderSubtle), DsRadius.Md)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        RoundedAppIcon(AppIcon.Note, tint = theme.mutedColor, size = 16.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("توضیحات", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                            Text(currentUser.note.orEmpty(), fontSize = 11.sp, color = theme.inkColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // سه آمار ضروری در یک ردیف؛ محدودیت دستگاه از این نمای خلاصه حذف شده است.
                Column(
                    Modifier.fillMaxWidth().clip(DsRadius.Lg)
                        .background(theme.cardSurfaceColor)
                        .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Lg)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.user_status), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        statTile("مصرف‌شده", formatBytes(currentUser.usedTraffic), Modifier.weight(1f))
                        statTile("حجم کل", traffic, Modifier.weight(1f))
                        statTile("زمان باقی‌مانده", detailDaysText(currentUser.expire), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("مصرف", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFFF3F4F6))) { if (percentage>0) Box(Modifier.fillMaxWidth(percentage / 100f).fillMaxHeight().background(progressColor, RoundedCornerShape(50))) }
                        Text("$percentage%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = progressColor)
                    }
                }

                // کارت اشتراک - فقط آیکون‌ها بدون متن
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Md)
                        .background(theme.cardSurfaceColor)
                        .border(BorderStroke(0.7.dp, theme.borderColor), DsRadius.Md)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("اشتراک", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    // دکمه‌های آیکون کپی و QR (لینک اشتراک در صورت نبود، lazy دریافت می‌شود)
                    IconActionBtn(AppIcon.Copy, "کپی", theme, Modifier.size(32.dp)) {
                        ensureSub { url ->
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("Sub", url))
                            android.widget.Toast.makeText(context, "لینک اشتراک کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    IconActionBtn(AppIcon.Qr, "QR", theme, Modifier.size(32.dp)) { ensureSub { _ -> qrOpen = true } }
                }

                // === بخش بدهی/فاکتور: منوی کشویی یکپارچه ===
                if (debtorInfo != null) {
                    CapsuleActionMenu(
                        label = "بخش مالی · ${debtorInfo.amount} ${debtorInfo.currency}",
                        expanded = debtorMenuExpanded,
                        onToggleExpand = { debtorMenuExpanded = !debtorMenuExpanded },
                        isDebtor = true
                    ) {
                        // اطلاعات بدهی
                        Text(
                            "از ${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(debtorInfo.markedAt))}" +
                                    if (debtorInfo.notes.isNotBlank()) " - ${debtorInfo.notes}" else " - بدون یادداشت",
                            fontSize = 10.sp, color = theme.mutedColor
                        )
                        if (debtorInfo.autoDisabled) {
                            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(GlassRed.copy(0.14f)).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 14.dp)
                                Text("به صورت خودکار به دلیل بدهی قطع شده است", fontSize = 10.sp, color = GlassRed, fontWeight = FontWeight.Bold)
                            }
                        }
                        CapsuleMenuItem(AppIcon.CheckCircle, "تسویه بدهی", GlassGreen, primary = true) { onClearDebt?.invoke() }
                        CapsuleMenuItem(AppIcon.Edit, "ویرایش بدهی", GlassRed, danger = true) { onMarkDebtor?.invoke() }
                        CapsuleMenuItem(AppIcon.Receipt, "صدور فاکتور", theme.accentPrimary) { onInvoice?.invoke() }
                    }
                } else {
                    CapsuleActionMenu(
                        label = "بخش مالی",
                        expanded = debtorMenuExpanded,
                        onToggleExpand = { debtorMenuExpanded = !debtorMenuExpanded }
                    ) {
                        CapsuleMenuItem(AppIcon.Warning, "ثبت بدهکار", GlassRed, danger = true) { onMarkDebtor?.invoke() }
                        CapsuleMenuItem(AppIcon.Receipt, "صدور فاکتور", theme.accentPrimary) { onInvoice?.invoke() }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    action("تمپلت‌ها", Modifier.weight(1f)) { templatePickerOpen = true }
                    action("ویرایش کاربر", Modifier.weight(2f), primary = true) { editOpen = true }
                }

                Column(section(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sectionTitle(stringResource(R.string.quick_actions))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        action("ریست حجم", Modifier.weight(1f)) { usageConfirm = true }
                        action("ریست زمان", Modifier.weight(1f)) { expiryConfirm = true }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        action(if (currentUser.status == "disabled") "فعال‌کردن" else "غیرفعال‌کردن", Modifier.weight(1f)) { onToggle() }
                        action("حذف کاربر", Modifier.weight(1f), destructive = true) { onDelete() }
                    }
                }
                // دکمه بستن در پایین پنجره
                SecondaryButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
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
    if (editOpen) UserEditorDialog(currentUser, { editOpen = false }, onSave, onToggle, onDelete, onResetUsage, { expiryConfirm = true }, onApplyTemplateToUser = onApplyTemplate, session = session)
    if (qrOpen) SubscriptionQrDialog(currentUser, { qrOpen = false })
    if (usageConfirm) ConfirmActionDialog("ریست حجم مصرف‌شده؟", "مصرف این کاربر صفر می‌شود.", onDismiss = { usageConfirm = false }, onConfirm = { usageConfirm = false; currentUser = currentUser.copy(usedTraffic = 0L); onResetUsage() })
    if (expiryConfirm) ResetExpiryDurationDialog(onDismiss = { expiryConfirm = false }, onConfirm = { days -> expiryConfirm = false; onResetExpiry(days) })
}
