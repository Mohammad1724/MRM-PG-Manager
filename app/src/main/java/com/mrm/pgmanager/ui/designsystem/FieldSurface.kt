package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.mrm.pgmanager.ui.theme.ThemeState

/** The same rounded, high-contrast field surface used by search and form controls. */
internal fun Modifier.pgFieldSurface(theme: ThemeState, focused: Boolean = false): Modifier =
    clip(DsRadius.Md)
        .background(theme.searchBgColor)
        .border(
            BorderStroke(
                if (focused) DsBorder.Focus else DsBorder.Hairline,
                if (focused) theme.accentTextColor else theme.controlBorderColor
            ),
            DsRadius.Md
        )
