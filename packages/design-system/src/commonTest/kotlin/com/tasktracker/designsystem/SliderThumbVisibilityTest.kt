package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The knob must stay visible at 100%, where it sits on a fully accent-filled track.
 *
 * An accent disc on an accent fill has no edge at all, so this is a rendered assertion: it
 * reads the thumb's own top rim and requires it to be the contrast-tested on-accent ink.
 */
@OptIn(ExperimentalTestApi::class)
class SliderThumbVisibilityTest {

    @Test
    fun aFullyFilledTrackStillShowsTheThumbsContrastRim() = rasterising {
        var rim: Color? = null
        var ink = Color.Unspecified
        var thumb: DpRect? = null
        var rootWidthDp = 0f

        setContent {
            ttTheme(ThemeScheme.Light) {
                ink = ttOnAccent
                Box(Modifier.fillMaxSize().background(ttCanvas())) {
                    ttSlider(
                        value = 1f,
                        onValueChange = {},
                        onValueChangeFinished = {},
                    )
                }
            }
        }
        waitForIdle()

        rootWidthDp = onRoot().getBoundsInRoot().width.value
        thumb = onNodeWithTag(SliderThumbTag).getBoundsInRoot()
        val frame = onRoot().captureToImage().toPixelMap()
        val scale = frame.width.toFloat() / rootWidthDp
        val measured = requireNotNull(thumb) { "no thumb bounds were captured" }
        val x = ((measured.left.value + measured.width.value / 2f) * scale).roundToInt()
        val y = (measured.top.value * scale).roundToInt() + 1
        rim = frame[x, y]

        val sampled = requireNotNull(rim) { "no rim pixel was captured" }
        val distance = maxChannelDistance(sampled, ink)
        assertTrue(
            distance < 0.08f,
            "the thumb's top rim is $sampled, ${distance} away from the on-accent ink $ink; " +
                "an accent knob on a fully accent-filled track has no visible edge",
        )
    }

    private fun maxChannelDistance(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
}
