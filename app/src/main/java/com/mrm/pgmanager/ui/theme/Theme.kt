package com.mrm.pgmanager.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
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

    val inkColor: Color get() = if (isDark) DsNeutral.InkDark else DsNeutral.Ink
    val mutedColor: Color get() = if (isDark) DsNeutral.MutedOnDark else DsNeutral.Muted
    val mutedLightColor: Color get() = if (isDark) DsNeutral.MutedOnDark.copy(0.7f) else DsNeutral.MutedLight
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
