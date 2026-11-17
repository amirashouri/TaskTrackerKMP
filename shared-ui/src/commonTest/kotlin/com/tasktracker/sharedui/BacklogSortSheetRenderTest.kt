package com.tasktracker.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.BacklogSortBasis
import com.tasktracker.domain.BacklogSortKey
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.featureprojectspresentation.BacklogSortPositionLabelTag
import com.tasktracker.featureprojectspresentation.BacklogSortPositionTag
import com.tasktracker.featureprojectspresentation.BacklogSortSheet
import kotlin.math.max
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The position chip's label is read from the rendered frame, not from its modifier chain.
 *
 * ## The defect this is here to catch
 *
 * The chip used to paint `ttAccent()` ink on an *untinted* glass surface. Untinted glass takes
 * the accent as its top fill stop, so ink and backdrop were the same hue — on the muted dark
 * Classic accent the word read as a faint smudge. A source assertion can see `ttAccent()` in
 * both places and call that consistent; only the pixels answer whether the word is legible.
 *
 * ## Why the fill sample is a strip, not the chip's centre
 *
 * The centre holds glyphs. The chip's horizontal padding is outside them, so the midpoint
 * between the chip's leading edge and the label's leading edge is fill and nothing else. The
 * specular highlight lives in the top few pixels and the vertical fill ramp fades toward the
 * bottom, so the sample is taken at the chip's vertical centre.
 *
 * ## The ink sample is a maximum, not a median
 *
 * Anti-aliased glyphs have a range of coverage; the median is mostly partial strokes and reads
 * darker than the token. The brightest pixel in the label's own bounds is the stroke at full
 * coverage, which is the pairing a reader actually sees at the word's thickest point. The
 * threshold is the WCAG normal-text bar, held on both rasterisers by the design system's
 * white-on-accent tests; this test checks that the sheet *uses* that pairing.
 */
@OptIn(ExperimentalTestApi::class)
class BacklogSortSheetRenderTest {

    @Test
    fun theFirstChipKeepsItsLabelLegibleInEveryThemeTested() {
        // The two extremes of the palette: Classic dark is a near-neutral grey, Music is a
        // saturated red. A pairing that clears both has no hue left to hide in.
        for (palette in listOf(AccentPalette.Classic, AccentPalette.Music)) {
            for (scheme in listOf(ThemeScheme.Light, ThemeScheme.Dark)) {
                var reading: Reading? = null
                rasterising {
                    reading = measureLabel(scheme, palette)
                }
                val measured = requireNotNull(reading) { "no reading in $scheme / ${palette.title}" }
                assertTrue(
                    measured.contrast >= MIN_LABEL_CONTRAST,
                    "in ${scheme.name}/${palette.title} the First chip's label measured " +
                        "${measured.contrast.ratio()} against its own fill — ${measured.ink.hex()} " +
                        "on ${measured.fill.hex()}. The chip must carry ttOnAccent ink on an " +
                        "opaque ttAccent fill; accent ink on the untinted glass wash was the " +
                        "same hue as its backdrop.",
                )
            }
        }
    }

    private fun ComposeUiTest.measureLabel(scheme: ThemeScheme, palette: AccentPalette): Reading {
        setContent {
            ttTheme(scheme = scheme, accentPalette = palette) {
                Box(Modifier.fillMaxSize().background(ttCanvas())) {
                    BacklogSortSheet(
                        order = BacklogSortOrder(
                            keys = listOf(
                                BacklogSortKey(BacklogSortBasis.Priority),
                                BacklogSortKey(BacklogSortBasis.CreatedAt),
                            ),
                        ),
                        onOrderChange = {},
                        onDismissRequest = {},
                        visible = true,
                    )
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage().toPixelMap()
        val root = onRoot().getBoundsInRoot()
        val scale = frame.width.toFloat() / max(root.right.value, 1f)
        val chip = onNodeWithTag(
            BacklogSortPositionTag(BacklogSortBasis.Priority),
            useUnmergedTree = true,
        ).getBoundsInRoot()
        val label = onNodeWithTag(
            BacklogSortPositionLabelTag(BacklogSortBasis.Priority),
            useUnmergedTree = true,
        ).getBoundsInRoot()

        val fillX = (((chip.left.value + label.left.value) / 2f) * scale).toInt()
            .coerceIn(0, frame.width - 1)
        val fillY = (((chip.top.value + chip.bottom.value) / 2f) * scale).toInt()
            .coerceIn(0, frame.height - 1)
        val fill = frame[fillX, fillY]

        val brightest = brightestInk(frame, label, scale)
        val inkLuminance = luminance(brightest)
        val fillLuminance = luminance(fill)
        val contrast = (maxOf(inkLuminance, fillLuminance) + 0.05) /
            (minOf(inkLuminance, fillLuminance) + 0.05)
        return Reading(brightest, fill, contrast.toFloat())
    }

    /** The brightest pixel inside the label's own box, inset far enough to miss its neighbours. */
    private fun brightestInk(frame: PixelMap, bounds: DpRect, scale: Float): Color {
        val left = (bounds.left.value * scale).toInt().coerceIn(0, frame.width - 1)
        val right = (bounds.right.value * scale).toInt().coerceIn(left + 1, frame.width)
        val top = (bounds.top.value * scale).toInt().coerceIn(0, frame.height - 1)
        val bottom = (bounds.bottom.value * scale).toInt().coerceIn(top + 1, frame.height)
        var brightest = frame[left, top]
        var best = -1.0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = frame[x, y]
                val value = luminance(pixel)
                if (value > best) {
                    best = value
                    brightest = pixel
                }
            }
        }
        return brightest
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble().coerceIn(0.0, 1.0)
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private data class Reading(val ink: Color, val fill: Color, val contrast: Float)
}

/** WCAG AA for normal text, the bar the design system holds for white ink on accent. */
private const val MIN_LABEL_CONTRAST = 4.5f

private fun Float.ratio(): String {
    val hundredths = (this * 100f).toInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}:1"
}

private fun Color.hex(): String = buildString {
    append('#')
    append((red * 255).toInt().toString(16).padStart(2, '0').uppercase())
    append((green * 255).toInt().toString(16).padStart(2, '0').uppercase())
    append((blue * 255).toInt().toString(16).padStart(2, '0').uppercase())
}
