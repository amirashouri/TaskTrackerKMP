package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tasktracker.designsystem.TTToastTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme

/**
 * What the board does after a card is copied, as scenarios.
 *
 * ## Why this is a board scenario and not a card one
 *
 * The card owns the button and the text; the *confirmation* is the board's, because it floats
 * over the lanes rather than over one card. So the two halves are tested where they live: the
 * card's payload is in `WorkItemCardScenarios`, and the toast's appearance and expiry are here.
 *
 * The clipboard write itself is deliberately not asserted. There is no portable way to read the
 * platform clipboard from `commonTest` on three hosts, and the rule that decides what goes on
 * it is [clipboardText] — unit-tested — while what the card hands over is asserted at the
 * callback. A test that reached into the real clipboard would be asserting the platform rather
 * than this screen.
 */
@OptIn(ExperimentalTestApi::class)
object BoardCopyScenarios {

    /**
     * Copying a card raises the confirmation, and it leaves on its own.
     *
     * The clock is stopped for the click so the two states can both be observed: with
     * auto-advance on, `waitForIdle` would run out the toast's two seconds before the first
     * assertion and the scenario would assert its absence twice.
     */
    val copyingACardConfirmsItOnTheBoard: suspend ComposeUiTest.() -> Unit = {
        setContent {
            ttTheme(ThemeScheme.Dark) {
                ProjectBoardScreen(
                    projection = ViewPreviewData.boardProjection(),
                    isLoading = false,
                    errorMessage = null,
                    movingItemID = null,
                    onSelect = {},
                    onFileAway = {},
                    onMove = { _, _ -> },
                    onDismissError = {},
                )
            }
        }
        waitForIdle()

        mainClock.autoAdvance = false
        // The first card's control: lane 0 is the first thing drawn, so this is on screen at
        // the surface sizes the three hosts run. Indexed because the tag names the control, not
        // the card — see the tag's own docs for why it is not suffixed with a key.
        onAllNodesWithTag(WorkItemCardCopyTag, useUnmergedTree = true)[0].performClick()
        mainClock.advanceTimeByFrame()

        onNodeWithTag(TTToastTag).assertIsDisplayed()

        mainClock.advanceTimeBy(COPIED_TOAST_MILLIS + 1)
        mainClock.advanceTimeByFrame()
        onNodeWithTag(TTToastTag).assertDoesNotExist()

        mainClock.autoAdvance = true
        waitForIdle()
    }
}
