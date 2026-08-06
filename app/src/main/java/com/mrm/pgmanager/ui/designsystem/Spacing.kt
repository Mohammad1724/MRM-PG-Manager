package com.mrm.pgmanager.ui.designsystem

import androidx.compose.ui.unit.dp

/**
 * Spacing & padding scale — every margin, padding and gap in the app
 * should come from this scale so vertical/horizontal rhythm is identical
 * everywhere. Use the named accessors for intent, the numeric base only
 * when you need an off-scale value.
 */
object DsSpacing {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 6.dp
    val Md = 8.dp
    val Lg = 12.dp
    val Xl = 16.dp
    val Xxl = 20.dp
    val Xxxl = 24.dp
    val X4l = 32.dp

    /** Standard screen horizontal gutter. */
    val Screen = 16.dp
    /** Standard content card padding. */
    val Card = 14.dp
    /** Dialog padding. */
    val Dialog = 16.dp
    /** Input field horizontal padding. */
    val FieldHorizontal = 14.dp

    /** Bottom content inset that leaves room for the FAB. */
    val FabClearance = 140.dp
}
