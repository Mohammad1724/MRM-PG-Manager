package com.mrm.pgmanager.ui.designsystem

import androidx.compose.ui.unit.dp

/**
 * Standard component dimensions (heights, icon sizes, touch targets).
 * Centralizing these guarantees identical sizing across screens.
 */
object DsComponent {
    // Heights
    val Button = 56.dp
    val ButtonCompact = 38.dp
    val ButtonSmall = 36.dp
    val Field = 64.dp
    val SearchBar = 46.dp
    val FAB = 60.dp
    val Chip = 30.dp
    val MiniChip = 24.dp

    // Icon sizes
    val IconXs = 15.dp
    val IconSm = 18.dp
    val IconMd = 22.dp
    val IconLg = 28.dp
    val IconXl = 36.dp

    // Icon tile wrappers
    val TileSm = 24.dp
    val TileMd = 40.dp
    val TileLg = 44.dp

    // Control sizes
    val Checkbox = 20.dp
    val DotStatus = 8.dp
    val ProgressBarHeight = 4.dp
    val ProgressBarHeightCompact = 3.dp

    // Minimum touch target (accessibility)
    val MinTouchTarget = 48.dp
    val MinTouchTargetSmall = 40.dp
}
