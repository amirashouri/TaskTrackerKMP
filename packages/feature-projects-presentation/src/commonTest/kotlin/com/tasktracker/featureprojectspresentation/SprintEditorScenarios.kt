package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.tasktracker.designsystem.TTBannerTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTDateFieldTag
import com.tasktracker.designsystem.ttTheme
import kotlinx.datetime.LocalDate

/**
 * The sprint editor sheet's lock, as two renderings of one form.
 *
 * The lock itself is [SprintEditorModel.windowLocked]'s business and is asserted there,
 * against the draft and the write. What only a composed form can show is that the flag
 * reaches the *controls*: the three that freeze, the three that must not, and the note
 * that says why. A model that locks correctly while the sheet renders everything live is
 * a form that invites edits it will silently drop — the defect pair has to be tested from
 * both ends.
 *
 * The frozen fields are addressed on the node a tap would land on, which is the convention
 * both components' KDoc states and neither sheet tag satisfies: `SprintEditorGoalPointsTag`
 * rides the labelled wrapper, and `TTDateFieldTag` is on the clickable row under the label.
 * The wrapper is never disabled — only the input inside it — so `assertIsNotEnabled` on the
 * sheet's own tag would be reading a node that has nothing to say. The live fields keep the
 * sheet's tags: `isEnabled()` is the *absence* of `Disabled`, which a plain wrapper answers
 * truthfully.
 */
@OptIn(ExperimentalTestApi::class)
object SprintEditorScenarios {

    /** The sheet, filled with one fixed draft; [windowLocked] is the only thing that moves. */
    private fun ComposeUiTest.renderSheet(
        windowLocked: Boolean,
        errorMessage: String? = null,
        onDismissError: () -> Unit = {},
    ) {
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                SprintEditorSheet(
                    name = "Sprint 1",
                    goalStoryPoints = 10,
                    goal = "Ship the migration",
                    startDate = LocalDate(2026, 3, 2),
                    endDate = LocalDate(2026, 3, 16),
                    ownerID = testUserID,
                    isCreating = false,
                    canSave = true,
                    endIsBeforeStart = false,
                    blockedBySprintName = null,
                    windowLocked = windowLocked,
                    errorMessage = errorMessage,
                    onDismissError = onDismissError,
                    onNameChanged = {},
                    onGoalStoryPointsChanged = {},
                    onGoalChanged = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onOwnerChanged = {},
                    onSave = {},
                    onCancel = {},
                    today = LocalDate(2026, 3, 16),
                )
            }
        }
    }

    /**
     * The points field's inner tag: `ProjectsTextField` derives it from the field's own
     * label, `"Goal (pts)"` -> `"goal.pts"`, so that a test asks for the field the same way
     * the screen names it. The sheet's tag cannot carry `Disabled` — see the object KDoc.
     */
    private const val GoalPointsFieldTag = "goal.pts"

    val theLockedWindowFreezesTheCapAndTheDates: suspend ComposeUiTest.() -> Unit = {
        renderSheet(windowLocked = true)

        // The note first: it is the form's only statement that anything is frozen, so the
        // disabled fields below are not a mystery to the user reading them.
        onNodeWithTag(SprintEditorLockedNoteTag).assertIsDisplayed()

        onNodeWithTag(GoalPointsFieldTag).assertIsNotEnabled()
        // Both date fields share the design-system tag; document order puts start first.
        // Both must answer — a lock that froze only the start day is the half-finished
        // kind this scenario exists to catch.
        onAllNodesWithTag(TTDateFieldTag)[0].assertIsNotEnabled()
        onAllNodesWithTag(TTDateFieldTag)[1].assertIsNotEnabled()

        // The half that describes the sprint rather than committing it stays live — this is
        // the assertion a lock implemented as "disable the sheet" would fail.
        onNodeWithTag(SprintEditorNameTag).assertIsEnabled()
        onNodeWithTag(SprintEditorGoalTag).assertIsEnabled()
        onNodeWithTag(SprintEditorOwnerTag).assertIsEnabled()
    }

    val anUnlockedWindowDrawsNoNoteAndEverythingLive: suspend ComposeUiTest.() -> Unit = {
        renderSheet(windowLocked = false)

        // Absent rather than hidden: the note's absence is what says the sheet is in its
        // planning state, and a note left in the tree with alpha 0 would pass an
        // `assertIsDisplayed` on some hosts.
        assertTagCount(SprintEditorLockedNoteTag, 0)

        // The same nodes the locked rendering freezes, here asserted live: `windowLocked`
        // is one flag, so these two scenarios are one assertion pair — a sheet that froze
        // its fields without being asked fails here even if the locked one passes.
        onNodeWithTag(GoalPointsFieldTag).assertIsEnabled()
        onAllNodesWithTag(TTDateFieldTag)[0].assertIsEnabled()
        onAllNodesWithTag(TTDateFieldTag)[1].assertIsEnabled()
        onNodeWithTag(SprintEditorNameTag).assertIsEnabled()
        onNodeWithTag(SprintEditorGoalTag).assertIsEnabled()
        onNodeWithTag(SprintEditorOwnerTag).assertIsEnabled()
    }

    val aPassedErrorMessageRendersAsABannerAboveSave: suspend ComposeUiTest.() -> Unit = {
        renderSheet(
            windowLocked = false,
            errorMessage = "Window overlaps with existing sprint",
        )

        onNodeWithTag(SprintEditorErrorTag).assertIsDisplayed()
    }
}

