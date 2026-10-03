package com.tasktracker.featuresettingspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import org.jetbrains.compose.resources.getString
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_glass_label
import com.tasktracker.resources.settings_glass_reduced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The glass slider, as scenarios.
 *
 * ## What is being asserted, and what is deliberately not
 *
 * That the control reports and accepts a value, that the two callbacks are separated, and that
 * the bottom of its travel is *named*. What is not asserted here is how much glass any given
 * intensity puts on screen — that is a compositing measurement and it lives in
 * `GlassIntensityRenderTest` in the design system, next to the fill it is about.
 *
 * The separation of preview from commit is the part worth a test at all. It is the difference
 * between a slider that follows the thumb and one that writes to disk sixty times a second,
 * and a bug in it is invisible: the screen still looks right and only the write volume is
 * wrong. So the drag scenario asserts the *count* of commits, not just that one happened.
 */
@OptIn(ExperimentalTestApi::class)
object GlassIntensityScenarios {

    /** The row is there, labelled, and opens at the amount the app is already showing. */
    val theSliderOpensAtTheAmountTheAppIsShowing: suspend ComposeUiTest.() -> Unit = {
        sliderShowing(intensity = 0.4f)

        onNodeWithText(getString(Res.string.settings_glass_label)).assertExists()
        onNodeWithTag(GlassSliderTag).assertExists()
        assertEquals(
            0.4f,
            onNodeWithTag(GlassSliderTag).sliderProgress(),
            "the slider must open at the persisted amount, not at its own default",
        )
    }

    /**
     * The readout is a percentage, and it is a *rounded* one.
     *
     * Asserted on the text rather than on a progress value because the number the user reads
     * is the text, and a readout that showed `41.666664%` while the thumb sat at 0.4166 would
     * be a defect the accessibility value would happily agree with.
     */
    val theReadoutIsTheRoundedPercentage: suspend ComposeUiTest.() -> Unit = {
        sliderShowing(intensity = 0.4166f)

        onNodeWithText("42%").assertExists()
    }

    /**
     * The bottom of the travel is Reduce Transparency, by name.
     *
     * The threshold in `glassAmountLabel` is checked from both sides, because the failure this
     * guards against is a band of values near zero that render the opaque cross-fade while
     * still reading as a bare "0%" — the user would be told they have some glass when they
     * have none.
     */
    val theBottomOfTheTravelIsNamedReduceTransparency: suspend ComposeUiTest.() -> Unit = {
        sliderShowing(intensity = 0f)
        onNodeWithText(getString(Res.string.settings_glass_reduced)).assertExists()

        sliderShowing(intensity = 0.005f)
        onNodeWithText(getString(Res.string.settings_glass_reduced)).assertExists()

        sliderShowing(intensity = 0.02f)
        onNodeWithText("2%").assertExists()
    }

    /**
     * Assistive technology can move it without a pointer.
     *
     * The `setProgress` action is the only way a screen-reader user can operate a slider at
     * all, and it is also the handle the test host uses. Both halves matter, and they are the
     * same code path.
     */
    val assistiveTechnologyCanSetTheAmount: suspend ComposeUiTest.() -> Unit = {
        val commits = mutableListOf<Float>()
        sliderShowing(intensity = 1f, onCommit = { commits += it })

        onNodeWithTag(GlassSliderTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }

        assertEquals(
            listOf(0.25f),
            commits.map { rounded(it) },
            "setting the progress must land on the requested value and persist exactly that",
        )
    }

    /**
     * A drag previews every frame and persists once.
     *
     * The counts are the assertion. A slider that committed on every `moveTo` would satisfy any
     * test that only checked "the final value was committed", and would write to the store once
     * per frame — so this holds the number of commits at one across a drag with several
     * intermediate positions in it.
     */
    val aDragPreviewsEveryStepAndPersistsOnce: suspend ComposeUiTest.() -> Unit = {
        val previews = mutableListOf<Float>()
        val commits = mutableListOf<Float>()
        sliderShowing(intensity = 1f, onPreview = { previews += it }, onCommit = { commits += it })

        val slider = onNodeWithTag(GlassSliderTag)
        slider.performTouchInput {
            down(centerLeft)
            moveTo(center + Offset(width * 0.25f, 0f))
            moveTo(center + Offset(width * 0.5f, 0f))
            moveTo(centerRight)
            up()
        }

        assertTrue(
            previews.size >= 4,
            "a drag across the track should report each step, saw ${previews.size}: $previews",
        )
        assertEquals(
            1,
            commits.size,
            "exactly one write for a whole drag, saw ${commits.size}: $commits",
        )
        // Compared against the last *previewed* value rather than against 1.0. `centerRight`
        // is one pixel inside the node's right edge, so a drag that stops there legitimately
        // rests just short of the top of the range; asserting an exact 1.0 would be asserting
        // the test's own arithmetic instead of the control's behaviour. What must hold is that
        // the write is the frame the thumb stopped on and not some earlier one.
        assertEquals(
            rounded(previews.last()),
            rounded(commits.single()),
            "the one write is the frame the thumb came to rest on, saw previews=$previews " +
                "commits=$commits",
        )
        assertTrue(
            commits.single() > 0.95f,
            "a drag to the right end of the track should rest near that end, got ${commits.single()}",
        )
    }

    /**
     * A tap alone moves it.
     *
     * Present because it was a real bug: the gesture was first written with
     * `detectHorizontalDragGestures`, whose `onDragStart` fires only after the touch slop is
     * exceeded, so tapping the track did nothing at all until you dragged. No assertion about
     * values would have found it — the control looked complete and answered only one of the two
     * things a finger does to it.
     */
    val aTapOnTheTrackMovesItWithoutADrag: suspend ComposeUiTest.() -> Unit = {
        val commits = mutableListOf<Float>()
        sliderShowing(intensity = 1f, onCommit = { commits += it })

        onNodeWithTag(GlassSliderTag)
            .performTouchInput {
                down(centerLeft)
                up()
            }

        assertEquals(
            1,
            commits.size,
            "a tap is a complete interaction and must persist on its own",
        )
        assertTrue(
            commits.single() < 0.2f,
            "a tap at the left end of the track should land near the left end, got ${commits.single()}",
        )
    }

    private fun rounded(value: Float): Float = (value * 100f).toInt() / 100f

    private fun SemanticsNodeInteraction.sliderProgress(): Float =
        fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.sliderShowing(
    intensity: Float,
    onPreview: (Float) -> Unit = {},
    onCommit: (Float) -> Unit = {},
) {
    setContent {
        Themed {
            SettingsScreen(
                connectivity = ConnectivitySummary.Online,
                activity = SyncActivity(),
                isForceOffline = false,
                themeScheme = ThemeScheme.System,
                version = SettingsPreviewData.version,
                commandOutcome = null,
                onThemeChange = {},
                onForceOfflineChange = {},
                onSyncNow = {},
                onSignOut = {},
                glassIntensity = intensity,
                onGlassIntensityPreview = onPreview,
                onGlassIntensityCommit = onCommit,
            )
        }
    }
}