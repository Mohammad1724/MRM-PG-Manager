package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * PasarGuard radius scale — soft, consistent, not overly rounded.
 */
object DsRadius {
    val Xs = RoundedCornerShape(6.dp)      // tiny chips, status
    val Sm = RoundedCornerShape(8.dp)      // small controls, icon tiles
    val Md = RoundedCornerShape(10.dp)     // inputs, buttons
    val Lg = RoundedCornerShape(12.dp)     // standard cards
    val Xl = RoundedCornerShape(14.dp)     // large sections
    val Xxl = RoundedCornerShape(16.dp)    // modals
    val Full = RoundedCornerShape(50)
}

object DsTileRadius {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(10.dp)
}
