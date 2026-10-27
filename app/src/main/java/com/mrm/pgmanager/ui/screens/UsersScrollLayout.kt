package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout

/** Forward header gestures using the list's scroll lock and the opposite delta convention. */
@Composable
internal fun rememberUsersHeaderScrollState(activeList: ScrollableState?): ScrollableState {
    val currentList by rememberUpdatedState(activeList)
    return remember { UsersHeaderScrollState { currentList } }
}

private class UsersHeaderScrollState(
    private val activeList: () -> ScrollableState?
) : ScrollableState {
    private val empty = ScrollableState { 0f }
    private val current get() = activeList() ?: empty

    override val isScrollInProgress: Boolean get() = current.isScrollInProgress

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) {
        current.scroll(scrollPriority) {
            val listScope = this
            val headerScope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float = -listScope.scrollBy(-pixels)
            }
            headerScope.block()
        }
    }

    override fun dispatchRawDelta(delta: Float): Float = -current.dispatchRawDelta(-delta)
}

/** Intercept header gestures without blocking its child controls or vertical scrolling. */
internal fun Modifier.usersHeaderDrag(state: ScrollableState): Modifier =
    scrollable(state = state, orientation = Orientation.Vertical)

/** Keep list drawing, hit testing and semantics inside the area below the header. */
internal fun Modifier.usersListViewport(headerBottomPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        } else {
            val top = headerBottomPx().coerceIn(0, constraints.maxHeight)
            val viewportHeight = constraints.maxHeight - top
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = viewportHeight))
            val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
            layout(width, constraints.maxHeight) { placeable.placeRelative(0, top) }
        }
    }.clipToBounds()
