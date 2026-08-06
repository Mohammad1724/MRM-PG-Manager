package com.mrm.pgmanager.ui.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * =====================================================================
 *  MRM Design Tokens — 2026 Flagship Visual Language
 * =====================================================================
 *  Single source of truth for the entire design system.
 *  Nothing in the app should hard-code colors, radii, elevations, blur,
 *  durations or spacing. Reference this file (and siblings in this
 *  package) instead so every surface, control and animation stays
 *  perfectly consistent.
 *
 *  Layers (each in its own file):
 *    DesignTokens.kt   -> color palette, gradients, elevation, blur,
 *                         borders, durations, motion, ripple
 *    Spacing.kt        -> spacing & padding scale
 *    Shapes.kt         -> corner-radius scale
 *    Typography.kt     -> type scale, sizes, weights, line-heights
 *    ComponentDefaults.kt -> standard heights, icon sizes, hit targets
 * =====================================================================
 */

/** Core neutral palette (Material-3 inspired surface ramp). */
object DsNeutral {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF1C1D21)        // primary text on light
    val InkSoft = Color(0xFF3A3C42)    // secondary text on light
    val Muted = Color(0xFF71737B)      // tertiary text on light
    val MutedOnDark = Color(0xFFA6A7AD)
    val HairlineLight = Color(0xFFE6E7EA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceMutedLight = Color(0xFFF5F6F8)
    val BackgroundLight = Color(0xFFF8F9FA)

    val SurfaceDark = Color(0xFF191A1E)
    val SurfaceSoftDark = Color(0xFF222327)
    val SurfaceMutedDark = Color(0xFF2A2B30)
    val BackgroundDark = Color(0xFF0E0F12)
    val AmoledBlack = Color(0xFF000000)
    val InkDark = Color(0xFFF2F3F5)
}

/** Semantic status colors — used SPARINGLY and always meaningfully. */
object DsSemantic {
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)
    val DangerSoft = Color(0xFF7A7886)
    val Violet = Color(0xFF8B5CF6)     // templates, special actions
}

/** Brand accent ramp. Primary accent is reserved for the PRIMARY action only. */
object DsAccent {
    val Gold = Color(0xFFF4C928)
    val GoldLight = Color(0xFFFFF3BD)
    val GoldSpotHigh = Color(0x55F4C928)
    val GoldSpotLow = Color(0x12F4C928)
    val OnAccent = Color(0xFF1A1A1A)   // readable text/icon on accent fills
}

/** Standard glass/translucency & border alpha values. */
object DsGlass {
    const val SurfaceAlphaHigh = 0.96f
    const val SurfaceAlphaMid = 0.80f
    const val SurfaceAlphaLow = 0.60f
    const val BorderStrong = 0.28f
    const val BorderDefault = 0.16f
    const val BorderFaint = 0.09f
    const val BorderDarkStrong = 0.32f
    const val BorderDarkDefault = 0.18f
    const val BorderDarkFaint = 0.10f
    const val RippleContentAlpha = 0.22f
    const val DisabledAlpha = 0.50f
}

/** Elevation (z-depth) for the two shadow channels. */
object DsElevation {
    // ambient = soft, diffuse outer glow; spot = tighter directional shadow
    data class Shadow(val ambient: Float, val spot: Float)
    val Flat = Shadow(0f, 0f)
    val Low = Shadow(2f, 4f)          // resting cards
    val Medium = Shadow(6f, 12f)      // FAB, focused fields
    val High = Shadow(10f, 20f)       // primary buttons, modals
}

/** Blur radii for the aurora / glass backgrounds. */
object DsBlur {
    val Subtle = 18.dp
    val Soft = 36.dp
    val Ambient = 48.dp
    val Halo = 60.dp
}

/** Border widths. */
object DsBorder {
    val Hairline = 0.8.dp
    val Thin = 1.dp
    val Default = 1.2.dp
    val Focus = 2.dp
}

/** Motion — single timing curve + durations for whole-app consistency. */
object DsMotion {
    const val PressBounceDamping = 0.6f
    const val PressBounceStiffness = 700f
    const val SpringBouncyDamping = 0.45f
    const val SpringBouncyStiffness = 400f

    val Instant = androidx.compose.animation.core.tween(60)
    val Fast = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val Normal = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val Slow = androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val Shimmer = androidx.compose.animation.core.tween(900)
    val Pulse = androidx.compose.animation.core.tween(850)
    val ScaleSpring = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f)
}

/** Gradients reused across surfaces and headers. */
object DsGradients {
    fun accentVertical(primary: Color, light: Color): Brush =
        Brush.verticalGradient(listOf(primary, light))
    fun accentSoft(primary: Color, light: Color): Brush =
        Brush.verticalGradient(listOf(primary.copy(alpha = 0.16f), light.copy(alpha = 0.05f)))
    fun gloss(): Brush =
        Brush.verticalGradient(
            listOf(
                0.0f to Color.White.copy(0.34f),
                0.42f to Color.White.copy(0.10f),
                1.0f to Color.Transparent
            )
        )
}
