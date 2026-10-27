package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The glass API's polish, as the two things polish can silently break: a control's *state* and a
 * control's *pixels*.
 *
 * ## Why `jvmTest`
 *
 * The gap between the two hosts is rasterisation of ink, and nothing here measures ink: the
 * disabled alpha is composited by the platform's own layer, so the two hosts have no room to
 * disagree about it the way they do about a glyph's stroke coverage. What is host-specific is the
 * Android host's Compose environment, which dies before a test body runs unless Robolectric is
 * engaged by a runner annotation `commonTest` cannot carry (see `ErrorTokenTest`'s notes). So this
 * runs where Compose UI tests in this module can run without a simulator.
 *
 * ## What is worth pinning here
 *
 * [ttGlassAddButton] was the one glass control with no `enabled`: it dimmed nothing and fired
 * always. A dimmed-looking button that still fires is the defect that flag exists to prevent, so
 * the first two tests hold the two halves together — the semantics and the click, in both
 * directions, because a test that only asserts that a disabled button does not fire passes just as
 * well against a button that is unreachable.
 *
 * The third test is the pixel half of the same contract, and it is written as an identity rather
 * than as a colour: however the rasteriser composites a translucent glass face, dimming the whole
 * control to [ttGlass.DISABLED_ALPHA] has to leave each pixel exactly that alpha's distance from
 * its own enabled value towards the backdrop. That form is what makes it discriminative — the
 * button is white glass, so measuring it over a white backdrop would have hidden a wrong alpha
 * completely, and measuring it on a black one separates 40% from 60% by a fifth of the range.
 */
@OptIn(ExperimentalTestApi::class)
class GlassApiPolishTest {

    /**
     * The trail's two numbers are tokens, and they still say 500 dp/s and 8 dp.
     *
     * The point is not that these literals are interesting; it is that they are now *shared*
     * vocabulary. `ttGlassLens` is drawn from one and blurred up to the other, and the lens's own
     * velocity — the spring's, in dp/s — is the unit they are written in. A quiet retune of either
     * number moves where a fast tab switch starts smearing, which is not something a screen's own
     * tests would notice.
     */
    @Test
    fun `the trail's threshold and blur are the numbers it was drawn with`() {
        assertEquals(
            500f,
            ttGlass.LENS_VELOCITY_THRESHOLD_DP.value,
            0f,
            "the lens trail's threshold is ${ttGlass.LENS_VELOCITY_THRESHOLD_DP}, not the 500 dp/s " +
                "it was drawn with — below this the trail is invisible, so a raised threshold " +
                "silently retires the effect and a lowered one makes it appear on slower swipes",
        )
        assertEquals(
            8f,
            ttGlass.LENS_MAX_BLUR_DP.value,
            0f,
            "the lens trail's maximum blur is ${ttGlass.LENS_MAX_BLUR_DP}, not the 8 dp it was " +
                "drawn with",
        )
    }

    /**
     * A disabled add button says so and does nothing; an enabled one does the thing.
     *
     * Both halves in one test on purpose. The interesting claim is the *pair* — that [enabled]
     * changes what the button does, not that a button with a flag set to `false` is inert, which a
     * button that never fires anything would also satisfy.
     */
    @Test
    fun `a disabled add button ignores the click that an enabled one takes`() {
        var enabledClicks = 0
        var disabledClicks = 0

        runComposeUiTest {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    Column {
                        ttGlassAddButton(
                            contentDescription = ENABLED_DESCRIPTION,
                            onClick = { enabledClicks++ },
                            modifier = Modifier.testTag(ENABLED_TAG),
                        )
                        ttGlassAddButton(
                            contentDescription = DISABLED_DESCRIPTION,
                            onClick = { disabledClicks++ },
                            modifier = Modifier.testTag(DISABLED_TAG),
                            enabled = false,
                        )
                    }
                }
            }
            waitForIdle()

            // The semantics are the half a screen reader reads: a control that ignores touches but
            // does not announce itself as disabled is a control that looks broken.
            onNodeWithTag(ENABLED_TAG).assertIsEnabled()
            onNodeWithTag(DISABLED_TAG).assertIsNotEnabled()

            onNodeWithTag(ENABLED_TAG).performClick()
            onNodeWithTag(DISABLED_TAG).performClick()
            waitForIdle()

            runOnIdle {
                assertEquals(
                    1,
                    enabledClicks,
                    "the enabled add button did not fire, so the assertion about the disabled one " +
                        "below would hold against a button that never fires at all",
                )
                assertEquals(
                    0,
                    disabledClicks,
                    "the disabled add button fired its click $disabledClicks time(s). Dimming the " +
                        "control without disabling it is the defect the parameter exists to " +
                        "prevent",
                )
            }
        }
    }

    /**
     * The disabled button is its enabled self at [ttGlass.DISABLED_ALPHA] over the backdrop.
     *
     * ## The identity, and why it is the shape of the assertion
     *
     * `Modifier.alpha` composites the whole control as a layer, so at every pixel the disabled
     * render is `alpha * enabled + (1 - alpha) * backdrop`, whatever the face, rim and glyph of the
     * control happen to be made of. Asserting *that* rather than a grey holds for the fill, the
     * rim and the specular at once, and it is sensitive to the alpha itself: the two faces here
     * differ by more than half the range, so 40% and 60% are a fifth of the range apart.
     *
     * ## Why a black backdrop
     *
     * This button is white glass. Over the light canvas a wrong alpha moves the composite by a
     * couple of colour steps and the test would pass against almost any number; over black the
     * enabled face is mid grey and the same mistake is unmistakable. The backdrop probe asserts the
     * black is where the sample thinks it is, so the expectation cannot be quietly computed against
     * the wrong ground.
     */
    @Test
    fun `a disabled add button is drawn at the disabled alpha`() {
        var reading: AlphaReading? = null

        runComposeUiTest {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    Box(Modifier.fillMaxSize().background(BACKDROP)) {
                        Column {
                            ttGlassAddButton(
                                contentDescription = ENABLED_DESCRIPTION,
                                onClick = {},
                                modifier = Modifier.testTag(ENABLED_TAG),
                            )
                            Spacer(Modifier.height(BUTTON_GAP))
                            ttGlassAddButton(
                                contentDescription = DISABLED_DESCRIPTION,
                                onClick = {},
                                modifier = Modifier.testTag(DISABLED_TAG),
                                enabled = false,
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val frame = onRoot().captureToImage()
            val enabled = onNodeWithTag(ENABLED_TAG).getBoundsInRoot()
            val disabled = onNodeWithTag(DISABLED_TAG).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            // Bounds are in dp and the frame is in pixels. Deriving the scale from the measured
            // root keeps this honest at any density instead of assuming the host's.
            val scale = frame.width.toFloat() / max(root.right.value, 1f)
            reading = faces(frame, enabled, disabled, scale)
        }

        val measured = requireNotNull(reading) { "no frame was captured" }
        val alpha = ttGlass.DISABLED_ALPHA
        val expected = Color(
            red = alpha * measured.enabledFace.red + (1f - alpha) * measured.backdrop.red,
            green = alpha * measured.enabledFace.green + (1f - alpha) * measured.backdrop.green,
            blue = alpha * measured.enabledFace.blue + (1f - alpha) * measured.backdrop.blue,
            alpha = 1f,
        )

        assertTrue(
            measured.backdrop.red + measured.backdrop.green + measured.backdrop.blue <=
                BACKDROP_CEILING,
            "the pixel sampled for the backdrop is ${measured.backdrop}, which is not the " +
                "backdrop — every expectation below would then be computed against the wrong ground",
        )
        assertTrue(
            abs(measured.enabledFace.red - measured.disabledFace.red) > ALPHA_TOLERANCE,
            "the enabled and disabled buttons rendered the same face (${measured.enabledFace} " +
                "both), so nothing was dimmed at all",
        )
        for (channel in CHANNELS) {
            val actual = channel.of(measured.disabledFace)
            val want = channel.of(expected)
            assertTrue(
                abs(actual - want) <= ALPHA_TOLERANCE,
                "the disabled add button's ${channel.name.lowercase()} channel is $actual where " +
                    "dimming its " +
                    "own enabled face (${channel.of(measured.enabledFace)}) to $alpha over the " +
                    "backdrop (${channel.of(measured.backdrop)}) gives $want. The whole control is " +
                    "composited as a layer, so this holds at every pixel whatever the face is made " +
                    "of — a miss means the alpha the control is dimmed by is not " +
                    "ttGlass.DISABLED_ALPHA ($alpha)",
            )
        }
    }

    /**
     * The two buttons' faces and the ground behind them, from the rendered frame.
     *
     * A face is a column of the button read top to bottom, summarised per channel by its median. A
     * median rather than a mean or a single centre pixel because a column crosses the specular band
     * at the top, the glyph, and the rim at each end, all of which vary within it; the median is the
     * one summary that does not move when the antialiasing along the circle's edge lands differently
     * between the two buttons.
     */
    private fun faces(
        frame: ImageBitmap,
        enabled: DpRect,
        disabled: DpRect,
        scale: Float,
    ): AlphaReading {
        val pixels = frame.toPixelMap()
        val width = ((enabled.right.value - enabled.left.value) * scale).toInt()
        val height = ((enabled.bottom.value - enabled.top.value) * scale).toInt()
        assertTrue(
            width > 0 && height > 0,
            "the add button laid out as ${width}x$height pixels, so there is no face to read",
        )
        val left = (enabled.left.value * scale).toInt()
        val enabledTop = (enabled.top.value * scale).toInt()
        val disabledTop = (disabled.top.value * scale).toInt()
        val disabledWidth = ((disabled.right.value - disabled.left.value) * scale).toInt()
        assertTrue(
            disabledWidth == width,
            "the two add buttons are not the same width ($width against $disabledWidth), so the " +
                "two faces are not the same shape and the comparison is not a comparison",
        )

        // The ground, taken below the lower button where nothing is drawn. Below rather than above
        // because the buttons start at the top of the frame, and outside their rectangles rather
        // than from a constant because the assertion about the backdrop above is worth more when
        // the backdrop is measured.
        val backdropY = disabledTop + height + BACKDROP_PROBE
        assertTrue(
            backdropY < frame.height,
            "the backdrop probe lands at row $backdropY of a ${frame.height} pixel frame, off the " +
                "bottom of it",
        )

        // A column a quarter of the way across the button, which is inside its circle and clear of
        // the glyph in the middle. The column's top and bottom rows fall outside the circle and on
        // its rim; both belong to the minority of a median.
        val column = left + width * FACE_COLUMN_SHARE / FACE_COLUMN_SPAN

        fun face(top: Int): Color {
            val reds = FloatArray(height)
            val greens = FloatArray(height)
            val blues = FloatArray(height)
            for (y in 0 until height) {
                val pixel = pixels[column, top + y]
                reds[y] = pixel.red
                greens[y] = pixel.green
                blues[y] = pixel.blue
            }
            return Color(
                red = reds.sorted()[height / 2],
                green = greens.sorted()[height / 2],
                blue = blues.sorted()[height / 2],
                alpha = 1f,
            )
        }

        return AlphaReading(
            enabledFace = face(top = enabledTop),
            disabledFace = face(top = disabledTop),
            backdrop = pixels[column, backdropY].let { Color(it.red, it.green, it.blue) },
        )
    }

    /** One channel of a colour, for the identity's three comparisons. */
    private enum class Channel {
        Red,
        Green,
        Blue,
        ;

        fun of(color: Color): Float = when (this) {
            Red -> color.red
            Green -> color.green
            Blue -> color.blue
        }
    }

    private class AlphaReading(
        val enabledFace: Color,
        val disabledFace: Color,
        val backdrop: Color,
    )

    private companion object {
        val CHANNELS = Channel.entries

        const val ENABLED_DESCRIPTION = "Add"
        const val DISABLED_DESCRIPTION = "Add, unavailable"
        const val ENABLED_TAG = "glassApiPolish-enabled"
        const val DISABLED_TAG = "glassApiPolish-disabled"

        /**
         * A backdrop the glass face cannot be mistaken for.
         *
         * The light-canvas alternative is the wrong choice and is worth spelling out: this control
         * is a white wash, so over white the enabled face and its 40% dimming land within a couple
         * of colour steps of each other and the test above would pass against any alpha.
         */
        val BACKDROP = Color.Black

        /** Vertical clearance between the two buttons, in dp: a whole number, so no sub-pixel drift. */
        val BUTTON_GAP = 40.dp

        /** How far below the lower button the backdrop is sampled, in pixels. */
        const val BACKDROP_PROBE = 8

        /**
         * Where in a button's width the face is sampled, as a fraction of it.
         *
         * A quarter of the way across. That column is inside the circle at every row it is read
         * from, and it is away from the glyph's vertical stroke, which sits on the centre line.
         */
        const val FACE_COLUMN_SHARE = 1
        const val FACE_COLUMN_SPAN = 4

        /** The most the backdrop probe may add up to across its three channels. */
        const val BACKDROP_CEILING = 0.1f

        /**
         * The most a channel may miss the identity by.
         *
         * Five colour steps on the 0-to-1 scale, against a measured **one**: `Modifier.alpha` is a
         * layer composite, and over this test's black backdrop the disabled face came back
         * `18/255` where the arithmetic says `19/255`. So the identity is exact and what is left
         * of it is the frame's own 8-bit rounding. Five steps is the module's standing noise floor
         * (see `TabLensContrastTest`), and it is worth leaving wide of the measurement
         * because the two faces are medians over a rectangle: the antialiasing along each circle's
         * edge is the one thing that can differ between two buttons drawn at different rows of the
         * same frame, and a median is what keeps it out of the answer.
         */
        const val ALPHA_TOLERANCE = 0.02f
    }
}
