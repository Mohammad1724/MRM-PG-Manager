package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Renders the real dialog content in the test root because Robolectric cannot idle the native window. */
internal class TestChoiceWindow : PGChoiceWindow {
    private var overlay by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var dismissAction: (() -> Unit)? = null

    @Composable
    override fun Show(onDismiss: () -> Unit, content: @Composable () -> Unit) {
        val currentDismiss = rememberUpdatedState(onDismiss)
        val currentContent = rememberUpdatedState(content)
        DisposableEffect(Unit) {
            dismissAction = { currentDismiss.value() }
            overlay = { currentContent.value() }
            onDispose {
                dismissAction = null
                overlay = null
            }
        }
    }

    @Composable
    fun Overlay() {
        val content = overlay
        if (content != null) {
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }

    fun dismiss() {
        requireNotNull(dismissAction) { "No choice window is open" }.invoke()
    }
}
