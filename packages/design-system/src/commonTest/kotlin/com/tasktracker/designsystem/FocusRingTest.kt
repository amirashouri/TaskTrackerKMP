package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The focus ring, read off the frame rather than asserted structurally.
 *
 * A semantics test can prove a control is focusable; it cannot prove focus is *visible*,
 * which was the whole defect: the glass controls set `indication = null` and had no focus
 * treatment at all. So this composes a real `ttGlassButton`, captures it before and after
 * focus, and reads the pixel at the centre of the top stroke.
 *
 * The button is the case worth rendering because it is the most-used control and its
 * `CircleShape` means the top edge is a straight run at the horizontal centre — a sample
 * window that is unambiguous rather than on a corner's curve.
 */
@OptIn(ExperimentalTestApi::class)
class FocusRingTest {

    @Test
    fun `a focused glass button paints an accent ring and an unfocused one does not`() {
        var before: PixelMap? = null
        var after: PixelMap? = null
        var bounds: DpRect? = null
        var boundsAfterFocus: DpRect? = null
        var blurred: PixelMap? = null
        var rootWidthDp = 0f
        var accent = Color.Unspecified
        var clearFocus: () -> Unit = {}

        rasterising {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    accent = ttAccent()
                    val focusManager = LocalFocusManager.current
                    clearFocus = { focusManager.clearFocus(force = true) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ttCanvas()),
                    ) {
                        ttGlassButton(
                            label = "Focus",
                            onClick = {},
                            modifier = Modifier.testTag(BUTTON_TAG),
                        )
                    }
                }
            }
            waitForIdle()

            val rootBounds = onRoot().getBoundsInRoot()
            rootWidthDp = rootBounds.width.value
            bounds = onNodeWithTag(BUTTON_TAG).getBoundsInRoot()
            before = onRoot().captureToImage().toPixelMap()

            onNodeWithTag(BUTTON_TAG).performSemanticsAction(SemanticsActions.RequestFocus)
            waitForIdle()
            boundsAfterFocus = onNodeWithTag(BUTTON_TAG).getBoundsInRoot()
            after = onRoot().captureToImage().toPixelMap()

            clearFocus()
            waitForIdle()
            blurred = onRoot().captureToImage().toPixelMap()

            assertTrue(rootBounds.width.value > 0f, "the frame has no width")
        }

        val frameBefore = requireNotNull(before) { "no unfocused frame was captured" }
        val frameAfter = requireNotNull(after) { "no focused frame was captured" }
        val frameBlurred = requireNotNull(blurred) { "no blurred frame was captured" }
        val button = requireNotNull(bounds) { "no button bounds were captured" }
        val focusedButton = requireNotNull(boundsAfterFocus) { "no focused bounds were captured" }
        assertTrue(
            button.left == focusedButton.left &&
                button.top == focusedButton.top &&
                button.width == focusedButton.width &&
                button.height == focusedButton.height,
            "focus moved the button from $button to $focusedButton; the ring must be drawn " +
                "inside the control bounds, not padded around them",
        )

        // One pixel inside the top edge, horizontally centred. `ttFocusRingWidth` is 2dp, so
        // this is inside the stroke on a 1x raster and still inside it at 2x.
        val scale = frameBefore.width.toFloat() / rootWidthDp
        val x = ((button.left + button.width / 2).value * scale).roundToInt()
        val y = (button.top.value * scale).roundToInt() + 1
        val unfocused = frameBefore[x, y]
        val focused = frameAfter[x, y]
        val afterBlur = frameBlurred[x, y]

        val unfocusedToAccent = channelDistance(unfocused, accent)
        val focusedToAccent = channelDistance(focused, accent)
        val changed = channelDistance(unfocused, focused)
        println(
            "button top edge: unfocused ${unfocused.hex()}, focused ${focused.hex()}, " +
                "accent ${accent.hex()}, distance $changed",
        )

        assertTrue(
            unfocusedToAccent > 0.15f,
            "the unfocused button already paints its edge close to the accent " +
                "($unfocusedToAccent); the ring is not what changed",
        )
        assertTrue(
            changed > 0.10f,
            "requesting focus changed the top edge by only $changed - no focus ring was " +
                "drawn. The button's modifier chain has lost its ttFocusRing call, or the " +
                "ring is behind the label/background.",
        )
        assertTrue(
            focusedToAccent < 0.05f,
            "the focused edge is $focusedToAccent away from ttAccent(); the ring should be " +
                "the accent stroke, not the glass rim",
        )
        assertTrue(
            channelDistance(afterBlur, unfocused) < 0.02f,
            "the ring did not disappear on blur: $afterBlur is still painted where the " +
                "unfocused frame had $unfocused",
        )
    }

    private fun channelDistance(a: Color, b: Color): Float {
        return maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
    }

    private companion object {
        const val BUTTON_TAG = "focus-ring.button"
    }
}
