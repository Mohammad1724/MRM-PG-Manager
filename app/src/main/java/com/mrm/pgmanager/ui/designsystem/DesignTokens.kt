package com.mrm.pgmanager.ui.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PasarGuard faithful design tokens — clean, minimal, light, professional.
 * Background: off-white, Surface: white, Border: very subtle gray.
 * Accent: warm golden yellow, used sparingly.
 */

/** Core neutral palette — matched to PasarGuard screenshots. */
object DsNeutral {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF1A1F2E)           // primary text - very dark gray, not black
    val InkSoft = Color(0xFF2E3440)
    val Muted = Color(0xFF6B7280)         // secondary — medium gray
    val MutedLight = Color(0xFF9CA3AF)    // tertiary — light gray
    val MutedOnDark = Color(0xFFA6A7AD)
    val HairlineLight = Color(0xFFE5E7EB) // border
    val HairlineSubtle = Color(0xFFEEF0F3)
    val SurfaceLight = Color(0xFFFFFFFF)  // cards
    val SurfaceMutedLight = Color(0xFFF9FAFB) // inputs, subtle bg
    val BackgroundLight = Color(0xFFF8F9FA)   // page bg — very light gray
    val BackgroundAlt = Color(0xFFF3F4F6)

    val SurfaceDark = Color(0xFF191A1E)
    val SurfaceSoftDark = Color(0xFF222327)
    val SurfaceMutedDark = Color(0xFF2A2B30)
    val BackgroundDark = Color(0xFF0E0F12)
    val AmoledBlack = Color(0xFF000000)
    val InkDark = Color(0xFFF2F3F5)
}

/** Semantic status — soft, desaturated to match PG. */
object DsSemantic {
    val Success = Color(0xFF16A34A)        // green for active/traffic
    val SuccessBg = Color(0xFFDCFCE7)
    val SuccessBorder = Color(0xFFBBF7D0)
    val Warning = Color(0xFFD97706)
    val WarningBg = Color(0xFFFEF3C7)
    val Danger = Color(0xFFDC2626)
    val DangerBg = Color(0xFFFEE2E2)
    val Info = Color(0xFF2563EB)
    val DangerSoft = Color(0xFF7A7886)
    val Violet = Color(0xFF7C3AED)
}

/** Brand accent — PasarGuard golden yellow. */
object DsAccent {
    val Gold = Color(0xFFFACC15)          // primary button, selected states
    val GoldDeep = Color(0xFFEAB308)      // pressed
    val GoldLight = Color(0xFFFEF9C3)     // light tint bg
    val GoldSoft = Color(0xFFFEF08A)
    val GoldSpotHigh = Color(0x33FACC15)
    val GoldSpotLow = Color(0x0FFACC15)
    val OnAccent = Color(0xFF1A1A1A)
    val IconBg = Color(0xFFFFFBEB)        // faint yellow icon container bg
}

/** Border & surface helpers */
object DsGlass {
    const val SurfaceAlphaHigh = 1f
    const val SurfaceAlphaMid = 1f
    const val SurfaceAlphaLow = 1f
    const val BorderStrong = 1f
    const val BorderDefault = 1f
    const val BorderFaint = 1f
    const val BorderDarkStrong = 0.32f
    const val BorderDarkDefault = 0.18f
    const val BorderDarkFaint = 0.10f
    const val RippleContentAlpha = 0.22f
    const val DisabledAlpha = 0.50f
}

/** Elevation — extremely subtle, barely there. */
object DsElevation {
    data class Shadow(val ambient: Float, val spot: Float)
    val Flat = Shadow(0f, 0f)
    val Low = Shadow(0.5f, 1f)
    val Medium = Shadow(1f, 3f)
    val High = Shadow(2f, 6f)
}

/** Blur — keep but not used in light theme */
object DsBlur {
    val Subtle = 0.dp
    val Soft = 0.dp
    val Ambient = 0.dp
    val Halo = 0.dp
}

/** Border widths — thin & precise. */
object DsBorder {
    val Hairline = 0.7.dp
    val Thin = 1.dp
    val Default = 1.dp
    val Focus = 1.2.dp
}

/** Motion — fast & subtle */
object DsMotion {
    const val PressBounceDamping = 0.6f
    const val PressBounceStiffness = 700f
    const val SpringBouncyDamping = 0.45f
    const val SpringBouncyStiffness = 400f
    val Instant = androidx.compose.animation.core.tween<Float>(60)
    val Fast = androidx.compose.animation.core.tween<Float>(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val Normal = androidx.compose.animation.core.tween<Float>(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val Slow = androidx.compose.animation.core.tween<Float>(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val Shimmer = androidx.compose.animation.core.tween<Float>(900)
    val Pulse = androidx.compose.animation.core.tween<Float>(850)
    val ScaleSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.55f, stiffness = 500f)
}

object DsGradients {
    fun accentVertical(primary: Color, light: Color): Brush =
        Brush.verticalGradient(listOf(primary, light))
    fun accentSoft(primary: Color, light: Color): Brush =
        Brush.verticalGradient(listOf(primary.copy(alpha = 0.08f), light.copy(alpha = 0.02f)))
    fun gloss(): Brush = Brush.verticalGradient(0.0f to Color.Transparent, 1.0f to Color.Transparent)
}
