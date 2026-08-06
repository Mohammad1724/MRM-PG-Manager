package com.mrm.pgmanager.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class LampColor(
    val label: String,
    val labelFa: String,
    val primary: Color,
    val light: Color,
    val spotHigh: Color,
    val spotLow: Color
) {
    // رنگ پیش‌فرض مطابق accent پنل PasarGuard: زرد شفاف و خوانا، نه طلایی گرادینتی.
    GOLD("PasarGuard Yellow", "زرد پاسارگارد", Color(0xFFF4C928), Color(0xFFFFF3BD), Color(0x55F4C928), Color(0x12F4C928)),
    MAGENTA("Berry Rose", "رز بری", Color(0xFFD64D8C), Color(0xFFFFD9E8), Color(0x66D64D8C), Color(0x14D64D8C)),
    TURQUOISE("Aegean Teal", "تیل اژه", Color(0xFF16A99A), Color(0xFFC8F3ED), Color(0x6616A99A), Color(0x1416A99A)),
    SKY_BLUE("Azure Blue", "آبی آزور", Color(0xFF3B82F6), Color(0xFFD8E8FF), Color(0x663B82F6), Color(0x143B82F6)),
    VIOLET("Orchid Violet", "بنفش ارکیده", Color(0xFF8B5CF6), Color(0xFFE8DEFF), Color(0x668B5CF6), Color(0x148B5CF6)),
    EMERALD("Jade Green", "سبز یشمی", Color(0xFF20A36B), Color(0xFFD1F5E2), Color(0x6620A36B), Color(0x1420A36B))
}

/** کمک‌کننده برای ساخت نسخهٔ روشنِ یک رنگ سفارشی (ترکیب با سفید). */
private fun Color.lightened(factor: Float = 0.80f): Color =
    Color(red + (1f - red) * factor, green + (1f - green) * factor, blue + (1f - blue) * factor, alpha)

data class ThemeState(
    val lamp: LampColor = LampColor.GOLD,
    /** وقتی غیر null باشد، رنگ سفارشی کاربر جایگزین پالت آماده می‌شود. */
    val customColor: Color? = null,
    val isDark: Boolean = false,
    val followSystem: Boolean = false,
    /** تم تیرهٔ خالص (AMOLED): پس‌زمینهٔ مشکی مطلق برای صرفه‌جویی باتری. */
    val amoledDark: Boolean = false
) {
    // اکسنتِ مؤثر: اگر رنگ سفارشی فعال باشد از آن استفاده می‌شود، در غیر این صورت از پالت آماده.
    val accentPrimary: Color get() = customColor ?: lamp.primary
    val accentLight: Color get() = customColor?.lightened() ?: lamp.light
    val accentSpotHigh: Color get() = customColor?.copy(alpha = 0.34f) ?: lamp.spotHigh
    val accentSpotLow: Color get() = customColor?.copy(alpha = 0.08f) ?: lamp.spotLow
    val accentLabelFa: String get() = if (customColor != null) "سفارشی" else lamp.labelFa

    // پایهٔ تم روشن: سطح‌های خنثی و مرزبندی ملایم مشابه پنل وب PasarGuard.
    val inkColor: Color get() = if (isDark) Color(0xFFF4F4F6) else Color(0xFF202124)
    val mutedColor: Color get() = if (isDark) Color(0xFFA09C94) else Color(0xFF74757B)
    val cardBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF121214)
        isDark -> Color(0xFF222226)
        else -> Color(0xFFFFFFFF)
    }
    val cardSurfaceColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF131316)
        isDark -> Color(0xFF202128)
        else -> Color(0xFFFFFFFF)
    }
    /** سطحِ نوارهای کرومی (هدر صفحه، تب‌بار شناور). */
    val chromeBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF09090B)
        isDark -> Color(0xFF141418)
        else -> Color(0xFFFFFFFF)
    }
    val cardBorderBrush: Brush
        get() = if (isDark) Brush.linearGradient(listOf(Color.White.copy(0.25f), Color.White.copy(0.05f)))
        else Brush.linearGradient(listOf(Color(0xFFE2E4E9), Color(0xFFD1D3D9)))
    val dialogBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF080808)
        isDark -> Color(0xFF16161A)
        else -> Color(0xFFFFFFFF)
    }
    val searchBgColor: Color get() = when {
        isDark && amoledDark -> Color(0xFF121212)
        isDark -> Color(0xFF1F1F24)
        else -> Color(0xFFF1F2F6)
    }
}

val LocalThemeState = compositionLocalOf { ThemeState() }

val GlassGreen = Color(0xFF22C55E)
val GlassAmber = Color(0xFFF59E0B)
val GlassRed = Color(0xFFEF4444)
val GlassShape = RoundedCornerShape(20.dp)
val PremiumCardShape = RoundedCornerShape(18.dp)

fun glassBg(isDark: Boolean, amoled: Boolean = false) = when {
    isDark && amoled -> Color(0xFF0F0F0F).copy(alpha = 0.8f)
    isDark -> Color(0xFF1E1E24).copy(alpha = 0.85f)
    else -> Color(0xFFFFFFFF).copy(alpha = 0.9f)
}

fun glassBorder(isDark: Boolean, amoled: Boolean = false) = when {
    isDark && amoled -> Color(0xFFFFFFFF).copy(alpha = 0.15f)
    isDark -> Color(0xFFFFFFFF).copy(alpha = 0.12f)
    else -> Color(0xFF000000).copy(alpha = 0.08f)
}

@Composable
fun LiquidGlassTheme(themeState: ThemeState, content: @Composable () -> Unit) {
    val colors = if (themeState.isDark) {
        darkColorScheme(
            primary = themeState.accentPrimary,
            onPrimary = Color(0xFF1A1A1A),
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
            background = Color(0xFFF8F9FA),
            surface = Color.White,
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    }

    val typography = androidx.compose.material3.Typography(
        displayLarge = androidx.compose.material3.Typography().displayLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif),
        // می‌توان در اینجا فونت‌های سفارشی را نیز ست کرد
    )

    val bgGradient = when {
        themeState.isDark && themeState.amoledDark -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
        themeState.isDark -> Brush.verticalGradient(listOf(Color(0xFF0F0F13), Color(0xFF08080A)))
        else -> Brush.verticalGradient(listOf(Color(0xFFF8F9FA), Color(0xFFF2F3F7)))
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !themeState.isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !themeState.isDark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalThemeState provides themeState) {
        // جهت چیدمان را برای کل اپ اجباری چپ‌به‌راست (LTR) می‌کنیم؛
        // در غیر این صورت Compose از لوکیل دستگاه پیروی می‌کند و در لوکیلِ فارسی کل اپ راست‌چین/میرور می‌شود.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
            MaterialTheme(colorScheme = colors) {
                Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
                // در تم روشن، پس‌زمینه کاملاً خنثی و بدون هاله است؛ همان زبان بصری پنل وب.
                // هاله‌ها فقط برای تم تیره نگه داشته شده‌اند (در AMOLED هم ملایم باقی می‌مانند).
                if (themeState.isDark) {
                    Box(
                        Modifier.size(600.dp).align(Alignment.TopStart).offset(x = (-180).dp, y = (-120).dp)
                            .background(Brush.radialGradient(listOf(themeState.accentSpotHigh.copy(alpha = 0.25f), Color.Transparent)), RoundedCornerShape(300.dp))
                            .blur(40.dp)
                    )
                    Box(
                        Modifier.size(450.dp).align(Alignment.BottomEnd).offset(x = 140.dp, y = 100.dp)
                            .background(Brush.radialGradient(listOf(themeState.accentLight.copy(alpha = 0.15f), Color.Transparent)), RoundedCornerShape(300.dp))
                            .blur(45.dp)
                    )
                }
                content()
            }
            }
        }
    }
}
