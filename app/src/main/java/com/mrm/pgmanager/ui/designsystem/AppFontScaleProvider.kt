package com.mrm.pgmanager.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mrm.pgmanager.data.model.AppFontScale

/** Scale Compose text while preserving the device's display density and font preference. */
@Composable
internal fun ProvideAppFontScale(percent: Int, content: @Composable () -> Unit) {
    val systemDensity = LocalDensity.current
    val normalized = AppFontScale.normalize(percent)
    val appDensity = if (normalized == AppFontScale.DEFAULT_PERCENT) {
        systemDensity
    } else {
        remember(systemDensity, normalized) {
            Density(systemDensity.density, AppFontScale.applyTo(systemDensity.fontScale, normalized))
        }
    }
    CompositionLocalProvider(LocalDensity provides appDensity, content = content)
}
