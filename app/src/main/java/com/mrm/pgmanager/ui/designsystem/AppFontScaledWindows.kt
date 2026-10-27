package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A dialog or popup owns a separate Compose view, which supplies its own density.
 * Carry the already-scaled parent density into the window rather than multiplying
 * the font scale again. This also keeps the original system density at 100%.
 */
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val parentDensity = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalDensity provides parentDensity, content = content)
    }
}

@Composable
internal fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val parentDensity = LocalDensity.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        val menuScope = this
        CompositionLocalProvider(LocalDensity provides parentDensity) {
            menuScope.content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color,
    contentColor: Color,
    tonalElevation: Dp,
    shape: Shape,
    dragHandle: @Composable (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val parentDensity = LocalDensity.current
    val scaledDragHandle: (@Composable () -> Unit)? = if (dragHandle == null) null else {
        {
            CompositionLocalProvider(LocalDensity provides parentDensity) {
                dragHandle()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shape = shape,
        dragHandle = scaledDragHandle
    ) {
        val sheetScope = this
        CompositionLocalProvider(LocalDensity provides parentDensity) {
            sheetScope.content()
        }
    }
}
