package com.mrm.pgmanager.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.data.model.AppFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class AppFontScaledWindowsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `dialog and nested popup inherit the current density without multiplying it again`() {
        var percent by mutableIntStateOf(AppFontScale.DEFAULT_PERCENT)
        val deviceDensity = Density(density = 2f, fontScale = 1.25f)
        var root: Density? = null
        var dialog: Density? = null
        var popup: Density? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides deviceDensity) {
                ProvideAppFontScale(percent) {
                    val rootDensity = LocalDensity.current
                    SideEffect { root = rootDensity }
                    AppDialog(onDismissRequest = {}) {
                        val dialogDensity = LocalDensity.current
                        SideEffect { dialog = dialogDensity }
                        Box {
                            Text("Anchor")
                            AppDropdownMenu(expanded = true, onDismissRequest = {}) {
                                val popupDensity = LocalDensity.current
                                SideEffect { popup = popupDensity }
                                Text("Menu item")
                            }
                        }
                    }
                }
            }
        }
        rule.runOnIdle {
            assertSame(deviceDensity, root)
            assertSame(root, dialog)
            assertSame(root, popup)
            percent = 140
        }
        rule.runOnIdle {
            assertEquals(2f, root!!.density, 0.001f)
            assertEquals(1.75f, root!!.fontScale, 0.001f)
            assertSame(root, dialog)
            assertSame(root, popup)
            percent = 80
        }
        rule.runOnIdle {
            assertEquals(1f, root!!.fontScale, 0.001f)
            assertSame(root, dialog)
            assertSame(root, popup)
            percent = AppFontScale.DEFAULT_PERCENT
        }
        rule.runOnIdle {
            assertSame(deviceDensity, root)
            assertSame(root, dialog)
            assertSame(root, popup)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun `bottom sheet content and handle inherit density as the size changes`() {
        var percent by mutableIntStateOf(AppFontScale.DEFAULT_PERCENT)
        val deviceDensity = Density(density = 2f, fontScale = 1.25f)
        var root: Density? = null
        var sheet: Density? = null
        var handle: Density? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides deviceDensity) {
                ProvideAppFontScale(percent) {
                    val rootDensity = LocalDensity.current
                    SideEffect { root = rootDensity }
                    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    AppModalBottomSheet(
                        onDismissRequest = {},
                        sheetState = state,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        tonalElevation = 0.dp,
                        shape = RectangleShape,
                        dragHandle = {
                            val handleDensity = LocalDensity.current
                            SideEffect { handle = handleDensity }
                            Text("Handle")
                        }
                    ) {
                        val sheetDensity = LocalDensity.current
                        SideEffect { sheet = sheetDensity }
                        Text("Sheet content")
                    }
                }
            }
        }
        rule.runOnIdle {
            assertSame(deviceDensity, root)
            assertSame(root, sheet)
            assertSame(root, handle)
            percent = 140
        }
        rule.runOnIdle {
            assertEquals(2f, root!!.density, 0.001f)
            assertEquals(1.75f, root!!.fontScale, 0.001f)
            assertSame(root, sheet)
            assertSame(root, handle)
            percent = AppFontScale.DEFAULT_PERCENT
        }
        rule.runOnIdle {
            assertSame(deviceDensity, root)
            assertSame(root, sheet)
            assertSame(root, handle)
        }
    }
}
