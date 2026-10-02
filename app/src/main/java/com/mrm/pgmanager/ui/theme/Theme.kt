package com.mrm.pgmanager.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import com.mrm.pgmanager.ui.designsystem.DsBorder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.mrm.pgmanager.ui.designsystem.WcagContrast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import kotlin.math.abs
import com.mrm.pgmanager.ui.designsystem.DsAccent
import com.mrm.pgmanager.ui.designsystem.DsNeutral
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic

/**
 * رنگ‌های چراغِ اپ. نامِ نمایشی عمداً اینجا نگه داشته نمی‌شود: از منابع
 * (کلیدهای lamp در strings.xml) خوانده می‌شود تا با زبانِ انتخابیِ کاربر هماهنگ بماند،
 * نه با جهتِ چیدمان.
 */
enum class LampColor(
    val primary: Color,
    val light: Color,
    val spotHigh: Color,
    val spotLow: Color
) {
    GOLD(DsAccent.Gold, DsAccent.GoldLight, DsAccent.GoldSpotHigh, DsAccent.GoldSpotLow),
    MAGENTA(Color(0xFFD64D8C), Color(0xFFFFD9E8), Color(0x66D64D8C), Color(0x14D64D8C)),
    TURQUOISE(Color(0xFF16A99A), Color(0xFFC8F3ED), Color(0x6616A99A), Color(0x1416A99A)),
    SKY_BLUE(Color(0xFF3B82F6), Color(0xFFD8E8FF), Color(0x663B82F6), Color(0x143B82F6)),
    VIOLET(Color(0xFF8B5CF6), Color(0xFFE8DEFF), Color(0x668B5CF6), Color(0x148B5CF6)),
    EMERALD(Color(0xFF20A36B), Color(0xFFD1F5E2), Color(0x6620A36B), Color(0x1420A36B))
}

private fun Color.lightened(factor: Float = 0.80f): Color =
    Color(red + (1f - red) * factor, green + (1f - green) * factor, blue + (1f - blue) * factor, alpha)

/** کمی تیره‌تر — برای آیکونِ رنگی روی سطحِ روشنِ حالتِ شب تا شسته‌رفته نشود. */
private fun Color.darkened(factor: Float = 0.18f): Color =
    Color(red * (1f - factor), green * (1f - factor), blue * (1f - factor), alpha)

/** RGB → HSL؛ h بر حسبِ درجه (۰..۳۶۰)، s و l در بازهٔ ۰..۱. */
private fun Color.toHsl(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val d = max - min
    if (d < 1e-4f) return floatArrayOf(0f, 0f, l)
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = 60f * when (max) {
        red -> (green - blue) / d + (if (green < blue) 6f else 0f)
        green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    }
    return floatArrayOf(h, s, l)
}

private fun hslColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        hh < 60f -> Triple(c, x, 0f)
        hh < 120f -> Triple(x, c, 0f)
        hh < 180f -> Triple(0f, c, x)
        hh < 240f -> Triple(0f, x, c)
        hh < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f), alpha)
}

/** ابتدای گرادیانِ سطحِ اصلی: همان فام، کمی تیره‌تر و پُررنگ‌تر. */
private fun Color.gradientStart(): Color {
    val (h, s, l) = toHsl()
    if (s < 0.08f) return darkened(0.10f)
    return hslColor(h, (s + 0.03f).coerceAtMost(1f), (l - 0.06f).coerceAtLeast(0.16f), alpha)
}

/**
 * انتهای گرادیانِ سطحِ اصلی: کمی روشن‌تر و تا ۱۲ درجه چرخیده به‌سمتِ نزدیک‌ترین
 * رنگِ درخشانِ چرخهٔ رنگ (زرد ۶۰°، فیروزه‌ای ۱۸۰°، سرخابی ۳۰۰°). برای خاکستری‌ها
 * فقط روشن‌تر می‌شود.
 */
private fun Color.gradientEnd(): Color {
    val (h, s, l) = toHsl()
    if (s < 0.08f) return lightened(0.18f)
    var shift = 0f
    var best = 361f
    for (target in floatArrayOf(60f, 180f, 300f)) {
        val delta = ((target - h + 540f) % 360f) - 180f
        if (abs(delta) < best) { best = abs(delta); shift = delta }
    }
    shift = shift.coerceIn(-12f, 12f)
    return hslColor(h + shift, s, (l + 0.08f).coerceAtMost(0.78f), alpha)
}

data class ThemeState(
    val lamp: LampColor = LampColor.GOLD,
    val customColor: Color? = null,
    val isDark: Boolean = false,
    val followSystem: Boolean = false,
    val amoledDark: Boolean = false
) {
    val accentPrimary: Color get() = customColor ?: lamp.primary
    val accentLight: Color get() = customColor?.lightened() ?: lamp.light
    val accentSpotHigh: Color get() = customColor?.copy(alpha = 0.34f) ?: lamp.spotHigh
    val accentSpotLow: Color get() = customColor?.copy(alpha = 0.08f) ?: lamp.spotLow

    /** رنگِ متن/آیکونی که مستقیم روی [accentPrimary] می‌نشیند (زرد → تیره، آبی/سبز → سفید). */
    val onAccent: Color get() = if (accentPrimary.luminance() > 0.45f) DsAccent.OnAccentWarm else Color.White

    /**
     * ابتدا/انتهای گرادیانِ سطوحِ اصلی (دکمهٔ اصلی، FAB، تبِ فعال، چیپ‌های انتخاب‌شده).
     * از خودِ رنگِ تم ساخته می‌شوند تا با عوض‌کردنِ تم، دکمه‌ها هم عوض شوند:
     * ابتدا کمی تیره‌تر، انتها کمی روشن‌تر و چرخیده به‌سمتِ نزدیک‌ترین رنگِ «درخشان»
     * (آبی → فیروزه‌ای، طلایی → زرد، بنفش → سرخابی) — همان حسِ شیشه‌ایِ مرجع.
     */
    val primaryStart: Color get() = accentPrimary.gradientStart()
    val primaryEnd: Color get() = accentPrimary.gradientEnd()

    /** رنگِ تختِ معادلِ گرادیان — برای سایه/هاله و جاهایی که Brush نمی‌پذیرند. */
    val primaryFill: Color get() = accentPrimary

    /** گرادیانِ افقیِ سطوحِ اصلی (چیپ‌ها، تبِ فعال، دکمه‌های کوچک). */
    val primaryBrush: Brush get() = Brush.horizontalGradient(listOf(primaryStart, primaryEnd))

    /** رنگِ محتوا روی سطوحِ اصلی (زرد → تیره، آبی/سبز → سفید). */
    val onPrimary: Color get() = onAccent

    /** لبهٔ شیشه‌ایِ سفیدِ نیمه‌شفافِ دورِ سطوحِ اصلی. */
    val primaryEdge: Color get() = Color.White.copy(alpha = if (onPrimary == Color.White) 0.55f else 0.70f)

    /** رنگِ هالهٔ نورِ دکمهٔ اصلی و FAB. */
    val primaryGlow: Color get() = accentPrimary

    val inkColor: Color get() = if (isDark) DsNeutral.InkDark else DsNeutral.Ink
    val mutedColor: Color get() = if (isDark) DsNeutral.MutedOnDark else DsNeutral.Muted
    /** نامِ قدیمی؛ برای متنِ خواندنی از [tertiaryColor] استفاده کنید (فاز ۷.۱). */
    val mutedLightColor: Color get() = if (isDark) DsNeutral.MutedOnDark else DsNeutral.MutedLight
    /**
     * متنِ ثالثیه (زیرنویس/راهنمای ریز). روشن: `#5B6472` (5.98) · تیره: `MutedOnDark` (7.25).
     * جانشینِ `MutedLight` در نقشِ متن — همان مقداری که در §۳ سند اندازه‌گیری شده بود.
     */
    val tertiaryColor: Color get() = if (isDark) DsNeutral.MutedOnDark else DsNeutral.Tertiary
    /** خاکستریِ «غیرفعال» — عمداً کم‌کنتراست و فقط برای عناصرِ غیرفعال. */
    val disabledTextColor: Color get() = DsNeutral.MutedLight
    /** مرزِ کنترلِ فرم (AA ≥۳:۱ روی سطحِ مجاور). */
    val controlBorderColor: Color get() = if (isDark) DsNeutral.ControlBorderDark else DsNeutral.ControlBorder
    /** متنِ موفقیت/هشدار روی سطحِ روشن تیره‌تر می‌شود؛ روی سطحِ تیره همان تنِ روشن AA است. */
    val successTextColor: Color get() = if (isDark) DsSemantic.Success else DsSemantic.SuccessText
    val warningTextColor: Color get() = if (isDark) DsSemantic.Warning else DsSemantic.WarningText
    /**
     * رنگِ **متنیِ** اکسنت کارِ جاری.
     *
     * طلایی (`#FACC15`) روی سفید فقط 1.53 می‌دهد؛ این ویژگی همان فام را
     * گام‌به‌گام تیره می‌کند تا به AA برسد (برای طلایی ≈ `#826803`، 5.3).
     * مزیت نسبت به هاردکد: برای هر ۶ چراغ و **رنگِ سفارشیِ کاربر** هم درست کار می‌کند.
     * برای پرشدگی/انتخاب همچنان [accentPrimary] استفاده می‌شود (متن روی آن).
     */
    val accentTextColor: Color
        get() = Color(
            WcagContrast.forTextOn(
                accentPrimary.toArgb(),
                (if (isDark) DsNeutral.BackgroundDark else DsNeutral.BackgroundLight).toArgb()
            )
        )
    val cardBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF121214)
        isDark -> DsNeutral.SurfaceSoftDark
        else -> DsNeutral.SurfaceLight
    }
    val cardSurfaceColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF131316)
        isDark -> DsNeutral.SurfaceDark
        else -> DsNeutral.SurfaceLight
    }
    val chromeBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF09090B)
        isDark -> Color(0xFF141418)
        else -> DsNeutral.SurfaceLight
    }
    val borderColor: Color get() = if (isDark) Color.White.copy(0.14f) else DsNeutral.HairlineLight
    val borderSubtle: Color get() = if (isDark) Color.White.copy(0.10f) else DsNeutral.HairlineSubtle
    // kept for backward-compat: old code used Brush border — now maps to flat borderColor
    val cardBorderBrush: androidx.compose.ui.graphics.Brush get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(borderColor, borderColor))
    val cardBorderColor: Color get() = borderColor
    val backgroundColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF000000)
        isDark -> DsNeutral.BackgroundDark
        else -> DsNeutral.BackgroundLight
    }
    val searchBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF121212)
        isDark -> DsNeutral.SurfaceMutedDark
        else -> DsNeutral.SurfaceMutedLight
    }
    val dialogBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF080808)
        isDark -> Color(0xFF16161A)
        else -> DsNeutral.SurfaceLight
    }
}

val LocalThemeState = compositionLocalOf { ThemeState() }

/**
 * پس‌زمینهٔ یک سطحِ «انتخاب‌شدنی» (چیپِ فیلتر، سگمنت، تبِ فعال، روزِ تقویم…).
 *
 * انتخاب‌شده: گرادیانِ سطحِ اصلی + لبهٔ شیشه‌ایِ سفید — همان زبانِ دکمهٔ اصلی.
 * عادی: رنگِ تختِ [idle] و در صورتِ وجود حاشیهٔ [idleBorder].
 * تابعِ معمولی است (نه Composable) تا وسطِ زنجیرهٔ Modifier بنشیند.
 */
fun Modifier.primarySurface(
    theme: ThemeState,
    selected: Boolean,
    shape: Shape,
    idle: Color,
    idleBorder: Color? = null,
    idleBorderWidth: Dp = DsBorder.Hairline
): Modifier =
    if (selected) {
        this.background(theme.primaryBrush, shape).border(BorderStroke(1.dp, theme.primaryEdge), shape)
    } else {
        this.background(idle, shape).let { if (idleBorder != null) it.border(BorderStroke(idleBorderWidth, idleBorder), shape) else it }
    }

val GlassGreen = DsSemantic.Success
val GlassAmber = DsSemantic.Warning
val GlassRed = DsSemantic.Danger
val GlassViolet = DsSemantic.Violet
val GlassShape = DsRadius.Lg
val PremiumCardShape = DsRadius.Lg

@Composable
fun LiquidGlassTheme(
    themeState: ThemeState,
    drawBackground: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (themeState.isDark) {
        darkColorScheme(
            primary = themeState.accentPrimary,
            onPrimary = DsAccent.OnAccent,
            secondary = themeState.accentLight,
            background = if (themeState.amoledDark) Color(0xFF000000) else Color(0xFF0D0D10),
            surface = if (themeState.amoledDark) Color(0xFF080808) else Color(0xFF141418),
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    } else {
        lightColorScheme(
            primary = themeState.accentPrimary,
            onPrimary = Color.White,
            secondary = themeState.accentLight,
            background = DsNeutral.BackgroundLight,
            surface = DsNeutral.SurfaceLight,
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // رنگِ نوارها دیگر اینجا ست نمی‌شود؛ شفافیت را `enableEdgeToEdge()`
                // در MainActivity می‌دهد. اینجا فقط روشن/تیره بودنِ آیکون‌های
                // نوار را با تمِ خودِ اپ (نه تمِ سیستم) هماهنگ می‌کنیم.
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !themeState.isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !themeState.isDark
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val store = androidx.compose.runtime.remember { com.mrm.pgmanager.data.storage.SessionStore(context) }
    val savedLang = store.readAppLanguage()
    val systemLocale = android.os.Build.VERSION.SDK_INT.let {
        if (it >= 24) context.resources.configuration.locales.get(0)
        else @Suppress("DEPRECATION") context.resources.configuration.locale
    } ?: java.util.Locale.getDefault()
    val isRtl = com.mrm.pgmanager.utils.LocaleHelper.isRtl(savedLang, systemLocale)
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    androidx.compose.runtime.CompositionLocalProvider(
        LocalThemeState provides themeState,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(colorScheme = colors) {
            if (drawBackground) {
                Box(modifier = Modifier.fillMaxSize().background(themeState.backgroundColor)) {
                    content()
                }
            } else {
                Box(modifier = Modifier, contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
}
