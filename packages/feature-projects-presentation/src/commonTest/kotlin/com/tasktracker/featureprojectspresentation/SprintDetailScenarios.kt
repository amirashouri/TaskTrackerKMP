package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tasktracker.core.randomProjectID
import com.tasktracker.designsystem.ThemeScheme
import kotlin.time.Instant
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.sprintSummary
import kotlinx.datetime.TimeZone
import kotlin.test.assertTrue

/**
 * The sprint detail card's edit affordance and the goal sentence it sits above.
 *
 * Both are new with the split goal: the sentence only exists when the sprint has one, and
 * the edit button is the door to the editor over *this* sprint. Neither survives a model
 * test — an `onEdit` that is never wired reads the same in a unit assertion as one the
 * button fires — so they are asserted on the composed card.
 */
@OptIn(ExperimentalTestApi::class)
object SprintDetailScenarios {

    /** A finished-or-open detail over a sprint with the given goal sentence. */
    private fun aDetail(goal: String?): SprintDetail {
        val sprint = testSprint(projectID = randomProjectID(), goal = goal)
        val items = emptyList<WorkItem>()
        // The fixture sprint is March 2026; the test clock is October 2025, so the sprint
        // is scheduled. Status follows from the same instant the model would read.
        val now = Instant.fromEpochSeconds(1_760_000_000)
        return SprintDetail(
            sprint = sprint,
            startDate = sprintLocalDate(sprint.startDate, TimeZone.UTC),
            endDate = sprintLocalDate(sprint.endDate, TimeZone.UTC),
            items = items,
            summary = sprintSummary(sprint, items),
            isViewerTheOwner = true,
            isActive = sprint.isActiveOn(now),
            status = sprint.statusAt(now),
        )
    }

    private fun ComposeUiTest.renderDetail(goal: String?, isBusy: Boolean, onEdit: () -> Unit) {
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                SprintDetailScreen(
                    detail = aDetail(goal),
                    isBusy = isBusy,
                    errorMessage = null,
                    onFinish = {},
                    onReopen = {},
                    onViewSummary = {},
                    onDismissError = {},
                    onRemove = {},
                    onArchive = {},
                    onEdit = onEdit,
                    isAdmin = true,
                )
            }
        }
    }

    val theCardShowsTheGoalAndItsEditButtonOpensTheEditor: suspend ComposeUiTest.() -> Unit = {
        var pressed = false
        renderDetail(goal = "Ship the migration", isBusy = false, onEdit = { pressed = true })

        onNodeWithTag(SprintDetailCardTag).assertIsDisplayed()
        assertText(SprintDetailGoalTag, "Ship the migration")
        onNodeWithTag(SprintDetailEditTag)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertTrue(pressed, "the card's edit button should open the editor over this sprint")
    }

    val theCardOmitsTheGoalLineWhenThereIsNone: suspend ComposeUiTest.() -> Unit = {
        renderDetail(goal = null, isBusy = false, onEdit = {})

        // Absent, not a placeholder: a dash where a sentence would be reads as a missing
        // value, and a sprint with no goal sentence never had one to show.
        assertTagCount(SprintDetailGoalTag, 0)
        onNodeWithTag(SprintDetailEditTag).assertIsDisplayed().assertIsEnabled()
    }

    val aBusyCardOffersNoEdit: suspend ComposeUiTest.() -> Unit = {
        renderDetail(goal = "Ship the migration", isBusy = true, onEdit = {})

        // A write in flight and a second sheet opening are a race, so the door closes the
        // same way Finish's does — and it is still *there*, dimmed, rather than vanishing
        // mid-tap.
        onNodeWithTag(SprintDetailEditTag).assertIsDisplayed().assertIsNotEnabled()
    }
}
