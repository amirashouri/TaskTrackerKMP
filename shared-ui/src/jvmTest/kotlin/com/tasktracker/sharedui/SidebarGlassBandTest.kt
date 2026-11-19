package com.tasktracker.sharedui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sidebar's glass has to be the rectangle its shadow is cast from.
 *
 * ## The defect this exists for
 *
 * The panel's content inset used to sit *between* `.shadow(…)` and `TTGlassSurface`'s fill, and
 * both are draw modifiers on one node, so they became two different rectangles: the shadow was
 * cast by a panel about 16dp wider and taller than the glass. Its unblurred middle was then left
 * uncovered along the panel's left, top and bottom edges, and that band — not the page, not the
 * panel — is what showed through. On the desktop window it read as a strip of colour hugging an
 * otherwise floating panel, which is what "a weird area around the left and the bottom" was.
 *
 * ## What is asserted, and why it is a colour comparison rather than a golden
 *
 * A band is exactly the question "is the pixel just inside the panel's edge the panel's own
 * fill?". So the fill is sampled 16dp in — past the band's width and away from the rounded
 * corners — and compared with the pixel 2dp in at the same row and column. On a fixed panel the
 * two agree; with a band between them they do not.
 *
 * The tolerance is deliberately a few units: the fill has a vertical gradient across the panel,
 * so two points a few dp apart are near-identical rather than identical, and a threshold tight
 * enough to catch the gradient would fail on its own.
 *
 * The two scheme runs are separate tests, for the reason [TabBarScreenshotTest] gives: the scheme
 * is seeded into preferences before the shell is built, so one composition cannot be rendered
 * twice and still be asking about the second appearance.
 */
@OptIn(ExperimentalTestApi::class)
class SidebarGlassBandTest {

    @Test
    fun theGlassReachesThePanelsEdgesInLightAppearance() = assertNoBareShadowBand(ThemeScheme.Light)

    @Test
    fun theGlassReachesThePanelsEdgesInDarkAppearance() = assertNoBareShadowBand(ThemeScheme.Dark)

    private fun assertNoBareShadowBand(scheme: ThemeScheme) = runComposeUiTest {
        // The same viewport the wide/sidebar scenarios use: the sidebar class is only composed at
        // 1120dp and up, and the 1024px virtual display can only carry that width at 0.75.
        val width = 1280.dp
        val density = Density(0.75f)

        val projects = FakeProjectRepository()
        projects.seed(shellTestProject(name = "Apollo"))

        val preferences = UserDefaultsStore(InMemoryPreferences())
        preferences.set(scheme.wireValue, ShellPreferences.THEME_KEY)

        with(ShellScenarios) {
            setUpShell(
                session = ScriptedSession(restored = signedIn()),
                projects = projects,
                preferences = preferences,
                width = width,
                density = density,
            )
        }
        waitForIdle()

        val frame = onNodeWithTag(SidebarTag).captureToImage().pixels()
        val inset = with(density) { 16.dp.roundToPx() }
        val probe = with(density) { 2.dp.roundToPx() }

        // Mid-span, so neither the rounded corner nor the row content is under the sample.
        val midY = frame.height / 2
        val midX = frame.width / 2

        val left = frame.colorAt(probe, midY)
        val leftFill = frame.colorAt(inset, midY)
        val bottom = frame.colorAt(midX, frame.height - 1 - probe)
        val bottomFill = frame.colorAt(midX, frame.height - 1 - inset)

        assertTrue(
            left.closeTo(leftFill),
            "${scheme.name}: a bare shadow band is showing along the sidebar's left edge — " +
                "the pixel ${probe}px in is $left but the panel's own fill ${inset}px in is " +
                "$leftFill, so the glass does not reach the rectangle the shadow is cast from",
        )
        assertTrue(
            bottom.closeTo(bottomFill),
            "${scheme.name}: a bare shadow band is showing along the sidebar's bottom edge — " +
                "the pixel ${probe}px up is $bottom but the panel's own fill ${inset}px up is " +
                "$bottomFill, so the glass does not reach the rectangle the shadow is cast from",
        )
    }

    /** `true` when every channel agrees to within [TOLERANCE]. */
    private fun Rgb.closeTo(other: Rgb): Boolean =
        abs(red - other.red) <= TOLERANCE &&
            abs(green - other.green) <= TOLERANCE &&
            abs(blue - other.blue) <= TOLERANCE

    private data class Rgb(val red: Int, val green: Int, val blue: Int) {
        override fun toString(): String = "($red,$green,$blue)"
    }

    /**
     * The frame as colour samples, read through Skia with an explicit `RGBA_8888` info.
     *
     * The channel order is forced rather than taken from `toPixelMap`, for the reason
     * [TabBarScreenshotTest.write] records: a surface read without saying what format it is in
     * produces a file that looks plausible and is wrong.
     */
    private fun ImageBitmap.pixels(): Frame {
        val skia = asSkiaBitmap()
        val info = ImageInfo(skia.width, skia.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val bytes = requireNotNull(skia.readPixels(info)) { "could not read the sidebar back" }
        return Frame(skia.width, skia.height, info.minRowBytes, bytes)
    }

    private class Frame(
        val width: Int,
        val height: Int,
        private val stride: Int,
        private val bytes: ByteArray,
    ) {
        fun colorAt(x: Int, y: Int): Rgb {
            val offset = y * stride + x * 4
            return Rgb(
                red = bytes[offset].toInt() and 0xFF,
                green = bytes[offset + 1].toInt() and 0xFF,
                blue = bytes[offset + 2].toInt() and 0xFF,
            )
        }
    }

    private companion object {
        /**
         * How far two samples of the same fill may drift.
         *
         * The panel's fill is a gradient down its height, so the left-edge pair differ only by the
         * gradient over 14dp and the bottom-edge pair by the gradient over the same span. Measured
         * on both appearances, the largest drift is under 4 units; the band this test is for reads
         * as 11 units in light and 25 in dark, so the threshold sits between the two rather than
         * at either end.
         */
        const val TOLERANCE = 8
    }
}
