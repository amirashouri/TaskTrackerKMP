package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.roundToInt

/**
 * [ttErrorFill] and [ttErrorRim] as the call site actually gets them.
 *
 * `PinnedAppearanceTest` covers [ttFixedColours] without composing anything, and that is
 * deliberate — those are plain constants. These two are not: they call [ttError], which
 * resolves through the theme, so pinning their behaviour means composing. This is the only
 * test in the module that does, and it lives in `jvmTest` rather than `commonTest` for the
 * reason the sign-in screen's tests are split per target: a Compose UI test in `commonTest`
 * runs on every target, and the Android host only runs one under Robolectric, which needs a
 * runner annotation `commonTest` cannot carry.
 */
@OptIn(ExperimentalTestApi::class)
class ErrorTokenTest {

    /**
     * The banner's fill and rim are the error at 10% and 25%.
     *
     * Both alphas are the original's, and both are argument defaults — which is exactly why
     * they need pinning. A caller who writes `ttErrorFill()` gets 10% and one who writes
     * `ttErrorFill(0.3f)` gets a banner that has quietly stopped matching its design, and
     * nothing about the screen's own tests would notice: the banner still renders, still
     * says the right thing, and still fails a sign-in.
     */
    @Test
    fun `the fill and rim default to the alphas the original used`() {
        var fillAlpha = 0f
        var rimAlpha = 0f

        runComposeUiTest {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    fillAlpha = ttErrorFill().alpha
                    rimAlpha = ttErrorRim().alpha
                }
            }
        }

        // Compared as 8-bit, not as the float that was asked for. `Color.alpha` is quantised
        // to a byte, so 10% comes back as 26/255 (0.10196) and 25% as 64/255 (0.25098) —
        // the byte is what the platform receives, and asserting the float would fail on a
        // correct implementation and pass on one that was 25.5/255.
        assertEquals(26, fillAlpha.toAlphaByte(), "the banner fill is not 10% of the error")
        assertEquals(64, rimAlpha.toAlphaByte(), "the banner rim is not 25% of the error")
    }

    /**
     * The rim is denser than the fill, so the banner has an edge.
     *
     * Checked as a relationship rather than as two numbers on purpose: a rim at or below the
     * fill's alpha would leave a notice with no outline, which reads as a soft wash rather
     * than as something that failed. Asserting each against its own constant would pass.
     */
    @Test
    fun `the rim is denser than the fill`() {
        var fill = 0f
        var rim = 0f

        runComposeUiTest {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    fill = ttErrorFill().alpha
                    rim = ttErrorRim().alpha
                }
            }
        }

        assertTrue(
            rim.toAlphaByte() > fill.toAlphaByte(),
            "the banner's rim is not denser than its fill: $rim vs $fill",
        )
    }

    /**
     * Both are the *same* red as [ttError], so the glyph and the border cannot drift apart.
     *
     * A banner whose border is a different red from the triangle inside it is a small,
     * entirely silent failure. Comparing the RGB channels is what catches it: two reds at
     * different alphas share RGB, so this also confirms the alpha is the only difference.
     */
    @Test
    fun `the fill and rim are the error red rather than a colour of their own`() {
        var error = androidx.compose.ui.graphics.Color.Unspecified
        var fill = androidx.compose.ui.graphics.Color.Unspecified
        var rim = androidx.compose.ui.graphics.Color.Unspecified

        runComposeUiTest {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    error = ttError()
                    fill = ttErrorFill()
                    rim = ttErrorRim()
                }
            }
        }

        assertEquals(error.red, fill.red)
        assertEquals(error.green, fill.green)
        assertEquals(error.blue, fill.blue)
        assertEquals(error.red, rim.red)
        assertEquals(error.green, rim.green)
        assertEquals(error.blue, rim.blue)
    }
}

/** The alpha a platform actually receives, in 0-255. */
private fun Float.toAlphaByte(): Int = (this * 255f).roundToInt()
