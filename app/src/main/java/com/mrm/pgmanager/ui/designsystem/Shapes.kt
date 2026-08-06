package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner-radius scale. Every radius in the app maps to one of these.
 * Rule of thumb:
 *   Xs   -> tiny tags, checkboxes, small chips
 *   Sm   -> pills/sort, action chips
 *   Md   -> buttons, small cards, icon tiles
 *   Lg   -> standard cards, fields, FAB
 *   Xl   -> large cards, modals
 *   Xxl  -> bottom sheets, big containers
 *   Full -> circular elements
 */
object DsRadius {
    val Xs = RoundedCornerShape(6.dp)
    val Sm = RoundedCornerShape(9.dp)
    val Md = RoundedCornerShape(12.dp)
    val Lg = RoundedCornerShape(16.dp)
    val Xl = RoundedCornerShape(20.dp)
    val Xxl = RoundedCornerShape(24.dp)
    val Full = RoundedCornerShape(50)
}

/** Icon-tile / control tile radii sized relative to the control. */
object DsTileRadius {
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(14.dp)
}
