package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGStartAlign
import com.mrm.pgmanager.ui.components.PGBadge
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * اجزای مشترکِ سه بخشِ «زیرساختِ پنل» (هاست/کور/قالبِ کلاینت).
 *
 * چرا یک فایلِ جدا: هر سه بخش سربرگ و نوارِ پیامِ یکسان دارند و اگر هر کدام
 * نسخهٔ خودش را می‌داشت، بعد از اولین تغییرِ سبک، سه ظاهرِ کمی متفاوت می‌شد.
 * این‌ها `internal` هستند و بیرون از این پکیج استفاده نمی‌شوند.
 */

/**
 * سرِبخشِ «زیرساختِ پنل»: عنوان + توضیح + شمارش + تازه‌سازی + ساخت.
 *
 * ناحیهٔ لمسیِ دکمه‌ها ۴۰dp است (کمینهٔ استانداردِ خودِ اپ) و برای صفحه‌خوان
 * `contentDescription` می‌گیرند — آیکونِ تنها بدونِ برچسب برای کاربرِ صفحه‌خوان
 * بی‌معناست.
 *
 * توضیح عمداً در خطِ *دوم* و با سراسرِ عرض است: قبلاً داخلِ همان Columnی بود که
 * کنارِ بجِ شمارش و دو دکمهٔ ۴۰dp جا می‌شد، پس به ~۲/۳ عرض می‌رسید و کلمهٔ آخرِ
 * جمله تنهایی به خطِ بعد می‌افتاد («…behind subscription / links»).
 */
@Composable
internal fun InfraSectionHeader(
    title: String,
    subtitle: String,
    count: Int?,
    onRefresh: () -> Unit,
    onAdd: (() -> Unit)?,
    addLabel: String
) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
        ) {
            // weight با fill=false: عنوان تا جا دارد طبیعی اندازه می‌گیرد و اگر جا
            // نبود سه‌نقطه می‌خورد — وگرنه (به‌جای Spacerِ قبلی) عنوانِ بلند دکمه‌های
            // تازه‌سازی/افزودن را از ردیف بیرون می‌انداخت.
            Text(
                title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = PGStartAlign(),
                modifier = Modifier.weight(1f, fill = false)
            )
            if (count != null && count > 0) PGBadge("$count")
            InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = onRefresh
            )
            if (onAdd != null) InfraIconButton(
                icon = AppIcon.Add,
                description = addLabel,
                tint = theme.accentPrimary,
                onClick = onAdd
            )
        }
        Text(
            subtitle, fontSize = 11.sp, color = theme.mutedColor, maxLines = 2,
            textAlign = PGStartAlign()
        )
    }
}

/** دکمهٔ آیکونیِ کوچک با ناحیهٔ لمسیِ ۴۰dp. */
@Composable
internal fun InfraIconButton(
    icon: AppIcon,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val theme = LocalThemeState.current
    Box(
        Modifier.size(40.dp).clip(DsRadius.Sm)
            .clickable(enabled = enabled) { onClick() }
            .background(theme.searchBgColor)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { RoundedAppIcon(icon, tint = if (enabled) tint else theme.mutedColor.copy(0.5f), size = 16.dp) }
}

/** برچسبِ کوچکِ بالای فیلدهای فرم (هم‌سبکِ بقیهٔ دیالوگ‌های اپ). */
@Composable
internal fun InfraFieldLabel(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        color = LocalThemeState.current.mutedColor
    )
}

/**
 * نوارِ پیامِ درون‌خطی (موفقیت/خطا) برای بخش‌هایی که Scaffold و pull-to-refresh
 * ندارند. `onClose` فقط برای خطا معنا دارد تا کاربر بتواند ببنددش.
 */
@Composable
internal fun InfraBanner(text: String, success: Boolean, onClose: (() -> Unit)? = null) {
    val theme = LocalThemeState.current
    // برچسب باید بیرون از `semantics {}` خوانده شود؛ آن لامبدا @Composable نیست.
    val closeLabel = stringResource(R.string.cd_close)
    val color = if (success) DsSemantic.Success else DsSemantic.Danger
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Md).background(color.copy(0.12f))
            .border(BorderStroke(DsBorder.Hairline, color.copy(0.26f)), DsRadius.Md)
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        RoundedAppIcon(
            if (success) AppIcon.CheckCircle else AppIcon.Warning,
            tint = color, size = 14.dp
        )
        Text(
            text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (success) theme.successTextColor else color,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (onClose != null) Box(
            Modifier.size(28.dp).clip(DsRadius.Xs).clickable { onClose() }
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center
        ) { Text("×", fontSize = 13.sp, color = color) }
    }
}

// ─────────────────────────────────────────────────────────────
//  جدولِ فشردهٔ ردیف‌ها — سرتیتر و بدنه از یک منبعِ وزن می‌گیرند
// ─────────────────────────────────────────────────────────────

/**
 * یک ستونِ جدولِ «زیرساخت».
 *
 * سرتیتر و هر ردیف **همین لیست** را می‌گیرند، پس لبهٔ چپِ هر خانه از تعریفِ
 * ستون می‌آید — نه از جهتِ خودِ متن. کارت‌هایِ قبلی دقیقاً همین را نداشتند:
 * نامِ فارسیِ یک هاست به راستِ ستون می‌چسبید و نامِ لاتینِ هاستِ بعدی به چپ،
 * و ردیف‌ها «چینش» نداشتند.
 */
internal data class InfraColumn(val labelRes: Int, val weight: Float)

/** یک آیتمِ منوی ⋮ . `danger` فقط رنگِ متن را عوض می‌کند، نه رفتار را. */
internal class InfraRowAction(val labelRes: Int, val danger: Boolean = false, val run: () -> Unit)

/**
 * دو خانهٔ ثابتِ دو سرِ ردیف: جایِ نقطهٔ وضعیت و جایِ دکمهٔ منو.
 * سرتیتر هم همین دو را رزرو می‌کند، وگرنه ستون‌ها از هم باز می‌شوند.
 */
private val InfraDotSlot = 12.dp
private val InfraActionSlot = 40.dp

/** سرِ ستون‌ها — یک‌بار بالای لیست، بیرونِ LazyColumn (همیشه دیدنی). */
@Composable
internal fun InfraTableHeader(columns: List<InfraColumn>) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth()
            .padding(start = DsSpacing.Card, end = DsSpacing.Card, bottom = DsSpacing.Xxs),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(Modifier.size(InfraDotSlot))
        columns.forEach { c ->
            Text(
                stringResource(c.labelRes),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = PGStartAlign(),
                modifier = Modifier.weight(c.weight)
            )
        }
        Box(Modifier.size(InfraActionSlot))
    }
}

/**
 * یک سطرِ تک‌خطی از جدول: [نام با نقطهٔ وضعیت] [ستون‌ها…] [⋮].
 *
 * چرا این شکلی: ردیفِ قبلی سه برچسبِ رنگی را در ستونی به عرضِ ~۱۸۰dp می‌ریخت
 * (۱۷۶dp از ۳۶۰dp را آیکونِ تزئینی و دو دکمهٔ ۴۰dpی می‌گرفت) و کارت ~۱۳۰dp
 * می‌شد؛ پنل همان اطلاعات را در یک سطرِ ~۲۸dpی می‌دهد. این‌جا هم همان اطلاعات
 * هست، فقط در ستون: اولویت و ینباند و نوع به‌جای چیپ، و عملگرها داخلِ منو.
 *
 * جایِ عملگرها **ثابت** است (`InfraActionSlot`)؛ پس `busy` بودن هم دیگر عرضِ
 * متن را عوض نمی‌کند (قبلاً اسپینر جای دو دکمه می‌نشست و ستون ~۶۲dp می‌پرید).
 */
@Composable
internal fun InfraTableRow(
    columns: List<InfraColumn>,
    name: String,
    cells: List<String>,
    dot: Color? = null,
    muted: Boolean = false,
    busy: Boolean = false,
    actions: List<InfraRowAction> = emptyList(),
    onEdit: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    val menuOpen = remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .then(if (onEdit != null) Modifier.clickable { onEdit() } else Modifier)
            .heightIn(min = 44.dp)
            .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Box(Modifier.size(InfraDotSlot), contentAlignment = Alignment.Center) {
            if (dot != null) Box(Modifier.size(7.dp).clip(DsRadius.Full).background(dot))
        }
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (muted) theme.mutedColor else theme.inkColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = PGStartAlign(),
            modifier = Modifier.weight(columns.first().weight)
        )
        // ستونِ کم‌مقدار «—» می‌گیرد، نه خطا: تعدادِ cells کارِ فراخوان است.
        columns.drop(1).forEachIndexed { i, c ->
            Text(
                cells.getOrElse(i) { "—" },
                fontSize = 11.sp,
                color = theme.mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = PGStartAlign(),
                modifier = Modifier.weight(c.weight)
            )
        }
        Box(Modifier.size(InfraActionSlot), contentAlignment = Alignment.Center) {
            if (busy) {
                MrmBusyIndicator(color = theme.accentPrimary, size = 16.dp, strokeWidth = 2.dp)
            } else if (actions.isNotEmpty()) {
                Box {
                    InfraIconButton(
                        icon = AppIcon.More,
                        description = stringResource(R.string.act_more),
                        tint = theme.inkColor,
                        onClick = { menuOpen.value = true }
                    )
                    DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                        actions.forEach { a ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(a.labelRes),
                                        fontSize = 12.sp,
                                        color = if (a.danger) DsSemantic.Danger else theme.inkColor
                                    )
                                },
                                onClick = { menuOpen.value = false; a.run() }
                            )
                        }
                    }
                }
            }
        }
    }
}
