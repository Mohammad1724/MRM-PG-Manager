package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.TrafficPoint
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * نمودار خطیِ مصرف — نسخهٔ مشترک.
 *
 * این کامپوننت از `StatisticsScreen` بیرون کشیده شد تا هم صفحهٔ آمار و هم پنجرهٔ
 * جزئیاتِ کاربر از یک کد استفاده کنند و منطقِ رسم دو جا تکرار نشود.
 *
 * اگر داده‌ای نباشد به‌جای کشیدنِ منحنیِ جعلی، پیامِ «داده‌ای موجود نیست» نشان می‌دهد.
 */
@Composable
fun UsageChart(
    points: List<TrafficPoint>,
    accent: Color,
    themeIsDark: Boolean,
    height: Dp = 110.dp,
    showAxisLabels: Boolean = true,
    valueFormatter: (Long) -> String = { it.toString() }
) {
    val theme = LocalThemeState.current
    val grid = if (themeIsDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    if (points.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_chart_data), fontSize = 10.sp, color = theme.mutedColor)
        }
        return
    }

    val maxValue = remember(points) { points.maxOf { it.totalTraffic }.coerceAtLeast(1L) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(valueFormatter(maxValue), fontSize = 8.sp, color = theme.mutedColor)
        Text(valueFormatter(0L), fontSize = 8.sp, color = theme.mutedColor)
    }
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width; val h = size.height
        for (i in 1..4) drawLine(grid, Offset(0f, h * i / 5f), Offset(w, h * i / 5f), 0.7f)
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { idx, pt ->
                val x = w * idx / (points.size - 1)
                val y = h - (pt.totalTraffic.toFloat() / maxValue * h * 0.78f) - h * 0.08f
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply { addPath(path); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(fill, accent.copy(0.14f))
            drawPath(path, accent, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        } else {
            // تک‌نقطه: یک خطِ افقی در ارتفاعِ همان مقدار
            val only = points.first()
            val y = h - (only.totalTraffic.toFloat() / maxValue * h * 0.78f) - h * 0.08f
            drawLine(accent, Offset(0f, y), Offset(w, y), 1.8f)
        }
    }
    if (showAxisLabels) {
        val labels = remember(points) { chartAxisLabels(points) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, fontSize = 8.sp, color = theme.mutedColor) }
        }
    }
}

/** حداکثر ۵ برچسبِ زمانی از نقاطِ نمودار، با فرمتِ کوتاهِ MM/dd یا HH:mm. */
fun chartAxisLabels(points: List<TrafficPoint>): List<String> {
    if (points.isEmpty()) return emptyList()
    val spanHours = runCatching {
        val first = java.time.Instant.parse(points.first().timestamp)
        val last = java.time.Instant.parse(points.last().timestamp)
        java.time.Duration.between(first, last).toHours()
    }.getOrDefault(24L)
    val pattern = if (spanHours <= 48L) "HH:mm" else "MM/dd"
    val fmt = java.time.format.DateTimeFormatter.ofPattern(pattern)
        .withZone(java.time.ZoneId.systemDefault())

    val step = (points.size / 5).coerceAtLeast(1)
    return points.filterIndexed { i, _ -> i % step == 0 }.take(5).map { p ->
        runCatching { fmt.format(java.time.Instant.parse(p.timestamp)) }
            .getOrDefault(p.timestamp.take(10))
    }
}
