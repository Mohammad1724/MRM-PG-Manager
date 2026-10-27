package com.mrm.pgmanager.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGScreenHeader
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** A deterministic indication makes both its presence and its clipping observable. */
private object SolidPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SolidPressNode(interactionSource)
    override fun hashCode(): Int = 7901
    override fun equals(other: Any?): Boolean = other === this
}

private class SolidPressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(Color.Red)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactPressIndicationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var density = 1f
    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Before fun setUp() { MotionPrefs.reduceMotion = true }
    @After fun tearDown() { MotionPrefs.reduceMotion = false }

    private fun bitmap(): Bitmap {
        lateinit var result: Bitmap
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            require(view.width > 0 && view.height > 0)
            result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(result))
        }
        return result
    }

    private fun assertFeedbackIsClipped(target: SemanticsNodeInteraction, visualTag: String) {
        val targetBounds = target.fetchSemanticsNode().boundsInWindow
        val visualBounds = rule.onNodeWithTag(visualTag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val outsideX = targetBounds.center.x.roundToInt()
        val outsideY = (targetBounds.top + density).roundToInt()
        val insideX = visualBounds.center.x.roundToInt()
        val insideY = visualBounds.center.y.roundToInt()
        val before = bitmap()
        target.performTouchInput { down(center) }
        rule.waitForIdle()
        val pressed = bitmap()
        assertEquals("Press feedback must appear on the visual surface", android.graphics.Color.RED, pressed.getPixel(insideX, insideY))
        assertEquals("Press feedback must not fill the surrounding touch target", before.getPixel(outsideX, outsideY), pressed.getPixel(outsideX, outsideY))
        target.performTouchInput { cancel() }
        before.recycle()
        pressed.recycle()
    }

    @Test fun `main header indications stay inside their rounded surfaces`() {
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalIndication provides SolidPressIndication) {
                PGScreenHeader(
                    title = "Users", subtitle = "Manage users", refreshing = false,
                    onRefresh = {}, onOpenSettings = {}, refreshLabel = "Refresh", settingsLabel = "Settings"
                )
            }
        }
        assertFeedbackIsClipped(rule.onNodeWithContentDescription("Refresh"), "header_action_visual_Refresh")
        assertFeedbackIsClipped(rule.onNodeWithContentDescription("Settings"), "header_action_visual_Settings")
    }

    @Test fun `users toolbar indications stay inside each control`() {
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalIndication provides SolidPressIndication) {
                Column(Modifier.width(360.dp)) {
                    FilterAndControlBar(
                        currentFilter = UserFilter.ALL, onFilterChange = {},
                        currentSort = UserSort.CREATED, onSortChange = {},
                        viewMode = ViewMode.GRID, onViewModeChange = {},
                        groups = listOf(Group(1, "VIP"))
                    )
                }
            }
        }
        assertFeedbackIsClipped(
            rule.onNodeWithContentDescription(str(R.string.filter) + ": " + str(R.string.all)),
            "users_bar_visual_Filter"
        )
        assertFeedbackIsClipped(rule.onNodeWithText(str(R.string.us_group_filter)), "users_group_visual")
        assertFeedbackIsClipped(rule.onNodeWithContentDescription(str(R.string.us_view_compact)), "users_view_visual_ListRows")
    }

    @Test fun `detail refresh indication stays inside its visual surface`() {
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalIndication provides SolidPressIndication) {
                InfraIconButton(icon = AppIcon.Refresh, description = "Refresh details", tint = Color.Blue, onClick = {})
            }
        }
        assertFeedbackIsClipped(rule.onNodeWithContentDescription("Refresh details"), "infra_refresh_visual")
    }
}
