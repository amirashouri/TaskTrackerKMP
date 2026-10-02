package com.tasktracker.sharedui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.routing.AppTab
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the tab bar to PNGs so its appearance can be looked at rather than described.
 *
 * ## Why this exists
 *
 * The bar's styling was rejected twice, and both rejections were visual: "the selected tab
 * indicator is missing" and "the styling is really bad and does not look good". Neither is a
 * kind of defect a test asserts. Every test in the suite can be green while the bar looks
 * wrong, and in the absence of anything else, appearance was being argued about in prose — which
 * is a losing game, because two people can describe the same pixels differently and then
 * disagree about what was asked for.
 *
 * So this renders the real composables, through the real shell, to files that can be opened.
 *
 * ## Deliberately not a golden-image assertion
 *
 * A golden locks in whatever was on screen when the golden was recorded. These were recorded
 * against the styling that was *rejected*, so committing them as a baseline would have made the
 * next rejection impossible to express — the test would have failed on the very change that
 * fixed it. These files are for a human, or an agent, to look at. The checks that belong in a
 * suite — inset, anchoring, where the lens sits — are in `ShellScenarios` and run on all three
 * targets: pixels here, structure there.
 *
 * The one thing asserted is that files were actually produced. A harness that silently stops
 * writing is worse than no harness, because it looks like it is working.
 *
 * ## Why JVM-only
 *
 * `captureToImage` is Skia-backed, so this needs the desktop target. The bar is exercised
 * through the iOS simulator and the Android host by the scenarios above; only the *pixels* need
 * a renderer that will run in a plain Gradle invocation.
 *
 * ## Reading the output
 *
 *     ./gradlew :shared-ui:jvmTest --tests '*TabBarScreenshotTest*'
 *     open shared-ui/build/reports/tabbar
 */
@OptIn(ExperimentalTestApi::class)
class TabBarScreenshotTest {

    /**
     * One test per appearance, and not one test looping over both.
     *
     * A `ComposeUiTest` owns a single composition, and each appearance needs its own: the
     * scheme is seeded into preferences *before* the shell is set up, so re-seeding and
     * re-rendering inside one composition would be asking the second frame to be a
     * recomposition of content that was already built against the first scheme's tokens. The
     * frames would still be written, and the second set would be quietly wrong.
     *
     * Separate tests also mean a failure names the appearance it happened in, and that a
     * crashed render cannot leave the other appearance's frames looking current.
     */
    @Test
    fun renderTheTabBarInLightAppearance() = render(ThemeScheme.Light)

    @Test
    fun renderTheTabBarInDarkAppearance() = render(ThemeScheme.Dark)

    private fun render(scheme: ThemeScheme) = runComposeUiTest {
        val output = File("build/reports/tabbar").apply { mkdirs() }

        // A project, so the bar is drawn over a populated list rather than over an empty
        // state. A bar over blank canvas and a bar over content are not the same picture,
        // and the first one flatters the glass.
        val projects = FakeProjectRepository()
        projects.seed(shellTestProject(name = "Apollo"))

        // Seeded rather than toggled in the UI: the stored scheme is what `RootScreen`
        // hands to `ttTheme`, so this goes through the same path the app does instead of
        // wrapping the content in a theme override the app itself would never apply.
        val preferences = UserDefaultsStore(InMemoryPreferences())
        preferences.set(scheme.wireValue, ShellPreferences.THEME_KEY)

        // `with`, not a qualified call: `setUpShell` is an extension *on* `ComposeUiTest`
        // declared inside the object, so it needs both the object as dispatch receiver and
        // the test as extension receiver, and the test receiver is what `runComposeUiTest`
        // supplies.
        with(ShellScenarios) {
            setUpShell(
                session = ScriptedSession(restored = signedIn()),
                projects = projects,
                preferences = preferences,
            )
        }
        waitForIdle()

        val written = mutableListOf<File>()
        for (tab in AppTab.entries) {
            // Through semantics, not `performClick`. This is not a shortcut, it is the only
            // way to get a clean frame: `performClick` sends press and release input, the
            // desktop indication stays down because the virtual pointer is left resting on
            // the node it clicked, and it darkens that one segment by 0.14 of relative
            // luminance — which is more than twice the lens's own contribution, and made the
            // selected tab look *darker* than the unselected ones in every frame.
            // `advanceTimeBy` does not clear it, because it is not an animation in flight.
            onNodeWithTag(TabButtonTag(tab))
                .performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            written += File(output, "tabbar-${scheme.wireValue}-${tab.wireValue}.png").also {
                write(onNodeWithTag(TabBarTag).captureToImage(), it)
            }
        }

        // The landing tab again, for the full-shell frame.
        onNodeWithTag(TabButtonTag(AppTab.Projects))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        written += File(output, "shell-${scheme.wireValue}.png").also {
            write(onRoot().captureToImage(), it)
        }

        // Asserted per appearance, on the files this render was supposed to produce. The
        // previous total-file count was a cross-test check that only held if both tests had
        // already run in the same invocation, so a filtered `--tests` run failed on a count
        // that had nothing to do with the code under test.
        val expected = AppTab.entries.size + 1
        val missing = written.filterNot { it.isFile && it.length() > 0 }
        assertTrue(
            missing.isEmpty(),
            "the ${scheme.name} render wrote $expected frames but these are missing or empty: " +
                missing.map { it.name },
        )
    }

    /**
     * Writes the frame as a PNG.
     *
     * The channel order is forced through Skia's `readPixels` with an explicit `RGBA_8888`
     * info rather than taken from [ImageBitmap.toPixelMap]. This is not a stylistic choice:
     * the first version of this harness used `toPixelMap`, and every frame it produced had a
     * red channel of exactly zero - the composable was fine, the reader was not, and the
     * frames came out uniformly cyan. A surface read without saying what format it is in is
     * the one place in this where a wrong answer still produces a plausible-looking file.
     *
     * `RGBA_8888` + `UNPREMUL` is also what makes the alpha meaningful. The frames are opaque,
     * so a premultiplied read would have quietly divided every colour by 255.
     */
    private fun write(image: ImageBitmap, target: File) {
        val skia = image.asSkiaBitmap()
        val width = skia.width
        val height = skia.height
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val bytes = requireNotNull(skia.readPixels(info)) {
            "could not read ${target.name} back as RGBA_8888"
        }

        // `minRowBytes` rather than `width * 4`: Skia is free to pad rows, and a frame read
        // with the wrong stride is a skewed picture rather than a failed one.
        val stride = info.minRowBytes
        val argb = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val o = y * stride + x * 4
                argb[y * width + x] = (bytes[o + 3].toInt() and 0xFF shl 24) or
                    (bytes[o].toInt() and 0xFF shl 16) or
                    (bytes[o + 1].toInt() and 0xFF shl 8) or
                    (bytes[o + 2].toInt() and 0xFF)
            }
        }
        val raster = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        raster.setRGB(0, 0, width, height, argb, 0, width)
        assertTrue(ImageIO.write(raster, "png", target), "could not encode ${target.name}")
    }
}
