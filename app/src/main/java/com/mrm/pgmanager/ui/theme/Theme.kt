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
    val spotLow: Color,
    val emoji: String
) {
    // رنگ پیش‌فرض مطابق accent پنل PasarGuard: زرد شفاف و خوانا، نه طلایی گرادینتی.
    GOLD("PasarGuard Yellow", "زرد پاسارگارد", Color(0xFFF4C928), Color(0xFFFFF3BD), Color(0x55F4C928), Color(0x12F4C928), "✨"),
    MAGENTA("Berry Rose", "رز بری", Color(0xFFD64D8C), Color(0xFFFFD9E8), Color(0x66D64D8C), Color(0x14D64D8C), ""),
    TURQUOISE("Aegean Teal", "تیل اژه", Color(0xFF16A99A), Color(0xFFC8F3ED), Color(0x6616A99A), Color(0x1416A99A), ""),
    SKY_BLUE("Azure Blue", "آبی آزور", Color(0xFF3B82F6), Color(0xFFD8E8FF), Color(0x663B82F6), Color(0x143B82F6), ""),
    VIOLET("Orchid Violet", "بنفش ارکیده", Color(0xFF8B5CF6), Color(0xFFE8DEFF), Color(0x668B5CF6), Color(0x148B5CF6), ""),
    EMERALD("Jade Green", "سبز یشمی", Color(0xFF20A36B), Color(0xFFD1F5E2), Color(0x6620A36B), Color(0x1420A36B), "")
}

data class ThemeState(
    val lamp: LampColor = LampColor.GOLD,
    val isDark: Boolean = false,
    val followSystem: Boolean = false
) {
    // پایهٔ تم روشن: سطح‌های خنثی و مرزبندی ملایم مشابه پنل وب PasarGuard.
    val inkColor: Color get() = if (isDark) Color(0xFFF4F4F6) else Color(0xFF202124)
    val mutedColor: Color get() = if (isDark) Color(0xFFA09C94) else Color(0xFF74757B)
    val cardBgColor: Color get() = if (isDark) Color(0xFF222226) else Color(0xFFFFFFFF)
    val cardBorderBrush: Brush
        get() = if (isDark) Brush.linearGradient(listOf(Color.White.copy(0.22f), Color.White.copy(0.08f)))
        else Brush.linearGradient(listOf(Color(0xFFDCDDE1), Color(0xFFDCDDE1)))
    val dialogBgColor: Color get() = if (isDark) Color(0xFF18181C) else Color(0xFFFFFFFF)
    val searchBgColor: Color get() = if (isDark) Color(0xFF2C2C32) else Color(0xFFF2F2F4)
}

val LocalThemeState = compositionLocalOf { ThemeState() }

val GlassGreen = Color(0xFF1A8C5B)
val GlassAmber = Color(0xFFD9822B)
val GlassRed = Color(0xFFC93B3B)
val GlassShape = RoundedCornerShape(24.dp)

// نام‌های قبلی حفظ شده‌اند تا مهاجرت صفحه‌ها مرحله‌ای باشد، اما ظاهر «glass» در تم روشن
// اکنون همان سطح سفید و border ظریف design system جدید را تولید می‌کند.
fun glassBg(isDark: Boolean) = if (isDark) Color(0xFF1E1E24) else Color(0xFFFFFFFF)
fun glassBorder(isDark: Boolean) = if (isDark) Color(0xFF9E9E9E).copy(alpha = 0.32f) else Color(0xFFD7D8DD)

@Composable
fun LiquidGlassTheme(themeState: ThemeState, content: @Composable () -> Unit) {
    val colors = if (themeState.isDark) {
        darkColorScheme(
            primary = themeState.lamp.primary,
            onPrimary = Color.White,
            secondary = themeState.lamp.light,
            background = Color(0xFF101012),
            surface = Color(0xFF1E1E22).copy(alpha = 0.70f),
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    } else {
        lightColorScheme(
            primary = themeState.lamp.primary,
            onPrimary = Color.White,
            secondary = themeState.lamp.light,
            background = Color(0xFFF7F7F8),
            surface = Color.White,
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    }

    val bgGradient = if (themeState.isDark)
        Brush.verticalGradient(listOf(Color(0xFF15151A), Color(0xFF0E0E12), Color(0xFF08080A)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF7F7F8), Color(0xFFF7F7F8)))

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
        MaterialTheme(colorScheme = colors) {
            Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
                // در تم روشن، پس‌زمینه کاملاً خنثی و بدون هاله است؛ همان زبان بصری پنل وب.
                // هاله‌ها فعلاً فقط برای تم تیره نگه داشته شده‌اند.
                if (themeState.isDark) {
                Box(
                    Modifier.size(600.dp).align(Alignment.TopStart).offset(x = (-160).dp, y = (-80).dp)
                        .background(Brush.radialGradient(listOf(themeState.lamp.spotHigh, themeState.lamp.spotLow, Color.Transparent)), RoundedCornerShape(300.dp))
                        .blur(22.dp)
                )
                Box(
                    Modifier.size(440.dp).align(Alignment.TopEnd).offset(x = 120.dp, y = (-60).dp)
                        .background(Brush.radialGradient(listOf(themeState.lamp.light.copy(alpha = 0.32f), Color.Transparent)), RoundedCornerShape(300.dp))
                        .blur(26.dp)
                )
                Box(
                    Modifier.size(520.dp).align(Alignment.BottomStart).offset(x = (-150).dp, y = 120.dp)
                        .background(Brush.radialGradient(listOf(themeState.lamp.spotHigh.copy(alpha = 0.28f), Color.Transparent)), RoundedCornerShape(300.dp))
                        .blur(32.dp)
                )
                Box(
                    Modifier.size(380.dp).align(Alignment.Center).offset(x = 80.dp, y = (-20).dp)
                        .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)), RoundedCornerShape(300.dp))
                )
                }
                content()
            }
        }
    }
}
