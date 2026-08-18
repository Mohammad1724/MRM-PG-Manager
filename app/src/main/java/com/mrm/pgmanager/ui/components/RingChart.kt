package com.mrm.pgmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.designsystem.DsAnim
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.animatedCount

// ─────────────────────────────────────────────────────────────
//  نمودارِ حلقه‌ای (donut)
//
//  چرا: کارت‌های آمارِ سیستم فقط عدد نشان می‌دادند و «چقدر پر است» را باید از
//  روی متن حساب می‌کردی. حلقه، نسبت را در یک نگاه می‌رساند و برخلاف نوارِ افقی
//  فضای عرضی نمی‌خورد — دقیقاً همان چیزی که کارت‌های دوستونی لازم داشتند.
// ─────────────────────────────────────────────────────────────

/** یک تکه از حلقه: سهمش از کلِ دایره (۰..۱) و رنگش. */
data class RingSegment(val fraction: Float, val color: Color)

/**
 * رنگِ حلقه برای یک نسبتِ مصرف.
 *
 * در حالتِ عادی **رنگِ تمِ خودِ کاربر** است (طلایی، بنفش، فیروزه‌ای… هرچه انتخاب
 * کرده)، چون این کارت‌ها بخشی از هویتِ بصریِ اپ‌اند نه چراغِ خطر. فقط وقتی منبع
 * واقعاً دارد پر می‌شود از تم فاصله می‌گیرد: بالای ۸۵٪ کهربایی و بالای ۹۵٪ قرمز.
 * آستانه‌ها عمداً بالاتر از نوارِ مصرفِ کاربران (۷۰/۹۰) گرفته شده‌اند؛ آنجا سهمیهٔ
 * خریداری‌شده تمام می‌شود، اینجا فقط بارِ لحظه‌ایِ سرور است.
 */
@Composable
fun meterColor(fraction: Float): Color {
    val t = LocalThemeState.current
    return when {
        fraction < 0.85f -> t.accentPrimary
        fraction < 0.95f -> DsSemantic.Warning
        else -> DsSemantic.Danger
    }
}

/**
 * دو رنگِ هم‌خانواده برای حلقه‌های دوتکه (مثلاً سهمِ دانلود و آپلود): همان رنگِ
 * تم، یکی پررنگ و یکی کم‌رنگ. با این کار نمودار خوانا می‌ماند بدون اینکه رنگِ
 * بیگانه‌ای وارد صفحه شود.
 */
@Composable
fun accentPair(): Pair<Color, Color> {
    val t = LocalThemeState.current
    return t.accentPrimary to t.accentPrimary.copy(alpha = if (t.isDark) 0.42f else 0.34f)
}

/**
 * حلقهٔ چندتکه با یک شیارِ خالی در پس‌زمینه.
 *
 * هر تکه جداگانه انیمیت می‌شود؛ برای همین وقتی داشبورد رفرش می‌شود حلقه
 * «می‌لغزد» به مقدارِ تازه به‌جای اینکه بپرد.
 *
 * @param gapDegrees فاصلهٔ کوچکِ بینِ تکه‌ها — فقط وقتی بیش از یک تکه باشد.
 */
@Composable
fun RingChart(
    segments: List<RingSegment>,
    modifier: Modifier = Modifier,
    diameter: Dp = 50.dp,
    stroke: Dp = 5.dp,
    trackColor: Color = Color.Unspecified,
    startAngle: Float = -90f,
    gapDegrees: Float = 3f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val t = LocalThemeState.current
    val track = if (trackColor != Color.Unspecified) trackColor
    else if (t.isDark) Color.White.copy(0.10f) else Color(0xFFEDEFF3)

    // forEach درون‌خطی است، پس صدا زدنِ animateFloatAsState داخلش مجاز است.
    val animated = ArrayList<Pair<Float, Color>>(segments.size)
    segments.forEach { seg ->
        val f by animateFloatAsState(
            targetValue = seg.fraction.coerceIn(0f, 1f),
            animationSpec = DsAnim.counter(),
            label = "ringSegment"
        )
        animated.add(f to seg.color)
    }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2f, w / 2f)
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = w)
            )
            val multi = animated.size > 1
            var angle = startAngle
            animated.forEach { (fraction, color) ->
                val raw = fraction.coerceIn(0f, 1f) * 360f
                if (raw > 0.5f) {
                    val gap = if (multi) gapDegrees else 0f
                    val sweep = (raw - gap).coerceAtLeast(1.5f)
                    drawArc(
                        color = color, startAngle = angle, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = w, cap = StrokeCap.Round)
                    )
                }
                angle += raw
            }
        }
        content()
    }
}

/**
 * کارتِ آمار با حلقه — جایگزینِ [PGStatCard] برای معیارهایی که «نسبت» دارند.
 *
 * ارتفاع ثابت نیست (`heightIn`) تا با بزرگ‌کردنِ فونتِ سیستم، متن از کارت
 * بیرون نزند؛ همان اشکالی که در کاشی‌های قدیمی دیده می‌شد.
 *
 * @param percent عددِ وسطِ حلقه. اگر null باشد، به‌جایش [centerIcon] می‌نشیند.
 * @param segments اگر داده شود، حلقه چندتکه می‌شود (مثلاً سهمِ دانلود و آپلود).
 */
@Composable
fun PGRingStatCard(
    label: String,
    value: String,
    icon: AppIcon,
    modifier: Modifier = Modifier,
    fraction: Float = 0f,
    percent: Int? = null,
    segments: List<RingSegment>? = null,
    ringColor: Color = Color.Unspecified,
    centerIcon: AppIcon? = null,
    sub: String? = null,
    minHeight: Dp = 88.dp,
    ringSize: Dp = 50.dp,
    trailing: @Composable (() -> Unit)? = null
) {
    val t = LocalThemeState.current
    val resolved = if (ringColor != Color.Unspecified) ringColor else meterColor(fraction)
    val segs = segments ?: listOf(RingSegment(fraction, resolved))
    val shownPercent = if (percent != null) animatedCount(percent) else null

    Row(
        modifier
            .heightIn(min = minHeight)
            .clip(DsRadius.Lg)
            .background(t.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, t.borderColor), DsRadius.Lg)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RingChart(segments = segs, diameter = ringSize, stroke = 5.dp) {
            when {
                shownPercent != null -> Text(
                    "$shownPercent%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.inkColor,
                    maxLines = 1,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                centerIcon != null -> RoundedAppIcon(centerIcon, tint = resolved, size = 17.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                RoundedAppIcon(icon, tint = resolved, size = 12.dp)
                Text(
                    label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = t.mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (trailing != null) trailing()
            }
            TechnicalContainer {
                Text(
                    value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.inkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sub != null) {
                Text(sub, fontSize = 9.5.sp, color = t.mutedLightColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
