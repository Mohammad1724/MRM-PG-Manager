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
    GOLD("Champagne Gold", "طلایی شامپاین", Color(0xFFC59B27), Color(0xFFF3E5AB), Color(0xBBF5D061), Color(0x44E5B84B), "✨"),
    MAGENTA("Royal Magenta", "سرخابی سلطنتی", Color(0xFFC8327E), Color(0xFFFAD1E6), Color(0xBBC8327E), Color(0x44E86FA8), "💖"),
    TURQUOISE("Neon Turquoise", "فیروزه‌ای نئون", Color(0xFF0EA89B), Color(0xFFB5F2EC), Color(0xBB2AD4C5), Color(0x4414A094), "🌊"),
    SKY_BLUE("Sky Blue", "آبی آسمانی", Color(0xFF1982C4), Color(0xFFBAE1FF), Color(0xBB3BA3EC), Color(0x441982C4), "💎"),
    VIOLET("Cyber Violet", "بنفش سایبری", Color(0xFF7A42D4), Color(0xFFE2D1FC), Color(0xBB9862F5), Color(0x447A42D4), "🔮"),
    EMERALD("Emerald Glow", "زمرد درخشان", Color(0xFF1A8C5B), Color(0xFFC2F2DC), Color(0xBB2EC486), Color(0x441A8C5B), "🍀")
}

data class ThemeState(
    val lamp: LampColor = LampColor.SKY_BLUE,
    val isDark: Boolean = false
) {
    val inkColor: Color get() = if (isDark) Color(0xFFF4F4F6) else Color(0xFF1C1B18)
    val mutedColor: Color get() = if (isDark) Color(0xFFA09C94) else Color(0xFF6A655B)
    val cardBgColor: Color get() = if (isDark) Color(0xFF222226).copy(alpha = 0.68f) else Color.White.copy(alpha = 0.56f)
    val cardBorderBrush: Brush
        get() = if (isDark) Brush.linearGradient(listOf(Color.White.copy(0.38f), Color.White.copy(0.08f)))
        else Brush.linearGradient(listOf(Color.White.copy(0.96f), Color.White.copy(0.22f)))
    val dialogBgColor: Color get() = if (isDark) Color(0xFF18181C).copy(alpha = 0.96f) else Color(0xFFFFFDF8).copy(alpha = 0.94f)
    val searchBgColor: Color get() = if (isDark) Color(0xFF2C2C32).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.68f)
}

val LocalThemeState = compositionLocalOf { ThemeState() }

val GlassGreen = Color(0xFF1A8C5B)
val GlassAmber = Color(0xFFD9822B)
val GlassRed = Color(0xFFC93B3B)
val GlassShape = RoundedCornerShape(24.dp)

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
            background = Color(0xFFFAF6EE),
            surface = Color.White.copy(alpha = 0.60f),
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    }

    val bgGradient = if (themeState.isDark)
        Brush.verticalGradient(listOf(Color(0xFF15151A), Color(0xFF0E0E12), Color(0xFF08080A)))
    else
        Brush.verticalGradient(listOf(Color(0xFFFFFDF9), Color(0xFFFFF7E6), Color(0xFFFFF4DC)))

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
                        .background(Brush.radialGradient(listOf(Color.White.copy(alpha = if (themeState.isDark) 0.05f else 0.20f), Color.Transparent)), RoundedCornerShape(300.dp))
                )
                content()
            }
        }
    }
}
