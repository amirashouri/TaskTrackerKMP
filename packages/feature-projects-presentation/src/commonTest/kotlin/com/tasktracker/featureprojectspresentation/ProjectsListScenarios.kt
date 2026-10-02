package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.SwipeDefaults
import com.tasktracker.featureprojectsdomain.ProjectSummary
import org.jetbrains.compose.resources.getString
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_delete
import com.tasktracker.resources.project_remove_archive
import com.tasktracker.resources.project_remove_body_clear
import com.tasktracker.resources.project_remove_body_outstanding
import com.tasktracker.resources.project_remove_title
import com.tasktracker.resources.projects_empty_body
import com.tasktracker.resources.projects_empty_title
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The projects list and the confirmation in front of removing one, as scenarios.
 *
 * ## The one rule here that is really a rule
 *
 * **The confirmation says what will happen, and the button says the same thing.**
 *
 * Removing a project with unfinished work archives it; removing an empty one deletes it and
 * its tasks. So the dialog's body and its confirm button both switch on
 * [ProjectRow.outstandingCount] - the body says the tasks will be filed in the archive, and
 * the button reads "Archive"; with nothing outstanding, the body says the tasks go for good
 * and the button reads "Delete".
 *
 * A confirmation reading "Delete?" above a button that archives is the app disagreeing with
 * itself, and the user finds out after the fact. The scenarios below assert the *button's*
 * wording as well as the body's, because the body alone is the version that gets written and
 * the button is the version that gets left.
 *
 * ## The count is re-read, not remembered
 *
 * The list looks the row up when the dialog opens rather than holding the row it was handed.
 * A count can change between the press and the confirmation - a task finished on another
 * device - and a dialog quoting a stale count would promise the wrong outcome.
 */
@OptIn(ExperimentalTestApi::class)
object ProjectsListScenarios {

    /** Every project is listed with its name, prefix and count. */
    val listsEveryProject: suspend ComposeUiTest.() -> Unit = {
        val rows = rowsWith(
            ProjectSummary(ViewPreviewData.seededProject, itemCount = 7),
            ProjectSummary(ViewPreviewData.secondProject, itemCount = 1),
        )
        listShowing(rows)

        onNodeWithTag(ProjectRowTag(rows[0].summary)).assertIsDisplayed()
        onNodeWithTag(ProjectRowTag(rows[1].summary)).assertIsDisplayed()
        onNodeWithText(ViewPreviewData.seededProject.name).assertIsDisplayed()
        onNodeWithText(ViewPreviewData.secondProject.name).assertIsDisplayed()
    }

    /** An empty list says so, rather than showing a blank screen. */
    val anEmptyListSaysSo: suspend ComposeUiTest.() -> Unit = {
        listShowing(emptyList())

        onNodeWithText(getString(Res.string.projects_empty_title)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.projects_empty_body)).assertIsDisplayed()
    }

    /** No confirmation until something is pressed. */
    val noConfirmationUntilSomethingIsPressed: suspend ComposeUiTest.() -> Unit = {
        listShowing(rowsWith(ProjectSummary(ViewPreviewData.seededProject, itemCount = 7)))

        onNodeWithTag(RemoveProjectConfirmationTag).assertDoesNotExist()
    }

    /** A project with unfinished work: the dialog promises the archive, and the button says so. */
    val unfinishedWorkIsArchivedNotDeleted: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.seededProject, itemCount = 7)
        listShowing(rowsWith(summary, outstanding = 3))

        onNodeWithTag(ProjectRemoveTag(summary)).performClick()

        onNodeWithTag(RemoveProjectConfirmationTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.project_remove_title, summary.name)).assertIsDisplayed()
        onNodeWithText(
            getString(Res.string.project_remove_body_outstanding, 3),
        ).assertIsDisplayed()
        // The *button's* wording, not the body's: a dialog that promises the archive and
        // offers "Delete" is the app disagreeing with itself, and the button is the half
        // that gets forgotten when the body is edited.
        assertText(RemoveProjectConfirmTag, getString(Res.string.project_remove_archive))
    }

    /** Nothing outstanding: the dialog warns of deletion, and the button agrees. */
    val noUnfinishedWorkIsDeletedForGood: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.seededProject, itemCount = 0)
        listShowing(rowsWith(summary, outstanding = 0))

        onNodeWithTag(ProjectRemoveTag(summary)).performClick()

        onNodeWithTag(RemoveProjectConfirmationTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.project_remove_body_clear)).assertIsDisplayed()
        assertText(RemoveProjectConfirmTag, getString(Res.string.common_delete))
    }

    /** Confirming reports the project that was actually pressed. */
    val confirmingReportsThatProject: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.secondProject, itemCount = 0)
        var confirmed: ProjectID? = null
        listShowing(rowsWith(summary, outstanding = 0), onConfirmRemove = { confirmed = it })

        onNodeWithTag(ProjectRemoveTag(summary)).performClick()
        onNodeWithTag(RemoveProjectConfirmTag).performClick()

        assertEquals(summary.id, confirmed)
    }

    /** Cancelling reports nothing and closes the dialog. */
    val cancellingReportsNothing: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.secondProject, itemCount = 0)
        var confirmed: ProjectID? = null
        var dismissed = false
        listShowing(
            rowsWith(summary, outstanding = 0),
            onConfirmRemove = { confirmed = it },
            onDismissRemove = { dismissed = true },
        )

        onNodeWithTag(ProjectRemoveTag(summary)).performClick()
        onNodeWithTag(RemoveProjectCancelTag).performClick()

        assertNull(confirmed, "cancelling must not remove anything")
        assertTrue(dismissed, "cancelling is reported, so a caller can reset any of its own state")
        onNodeWithTag(RemoveProjectConfirmationTag).assertDoesNotExist()
    }

    /**
     * A swipe past the threshold asks before it removes.
     *
     * The swipe is the fast path, not a shortcut past the confirmation: the outcome of
     * removing a project depends on whether it has unfinished work, and only the
     * confirmation can say which. So the swipe's whole job is to open the same dialog the
     * button opens, and this asserts it opens the *same* one - by finding the dialog that
     * only `RemoveProjectConfirmationTag` marks.
     */
    val swipingARowAsksBeforeRemovingIt: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.seededProject, itemCount = 7)
        listShowing(rowsWith(summary, outstanding = 3))

        onNodeWithTag(ProjectRowTag(summary)).performTouchInput { swipeRight() }

        onNodeWithTag(RemoveProjectConfirmationTag).assertIsDisplayed()
        onNodeWithText(
            getString(Res.string.project_remove_body_outstanding, 3),
        ).assertIsDisplayed()
    }

    /**
     * A drag that does not reach the threshold does nothing at all.
     *
     * The half of the gesture that matters most is the one that is *refused*, and it is
     * untestable by eye: a row that springs back looks identical whether it sprang back from
     * just under the threshold or from almost nothing, so a threshold that had quietly become
     * 4dp would still look right and would leave a deletion one twitch away. See
     * [SHORT_SWIPE_PX] for why the distance is where it is.
     */
    val aSwipeThatStopsShortDoesNothing: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.seededProject, itemCount = 7)
        listShowing(rowsWith(summary, outstanding = 3))

        onNodeWithTag(ProjectRowTag(summary)).performTouchInput { swipeRight(endX = SHORT_SWIPE_PX) }

        onNodeWithTag(RemoveProjectConfirmationTag).assertDoesNotExist()
    }

    /**
     * A refused drag does not also open the project.
     *
     * The row is clickable and the gesture wraps it, so the two compete for the same touch.
     * If the drag did not claim the pointer, a user who pushed a row sideways and let go
     * would land in that project instead - so a refusal has to be a complete refusal, not
     * just "the dialog did not open".
     *
     * Measured at 30px and not lower, because that is where the boundary is: below the
     * slop the platform is right to treat the touch as a tap, and the row opens. That is
     * worth knowing but not worth asserting, since it is Compose's slop value rather than
     * this app's decision.
     */
    val aShortDragDoesNotOpenTheProject: suspend ComposeUiTest.() -> Unit = {
        val summary = ProjectSummary(ViewPreviewData.seededProject, itemCount = 7)
        var opened: ProjectID? = null
        listShowing(rowsWith(summary, outstanding = 3), onOpen = { opened = it })

        onNodeWithTag(ProjectRowTag(summary)).performTouchInput { swipeRight(endX = SHORT_SWIPE_PX) }

        assertNull(opened, "a drag is not a tap")
    }

    /** The whole list is one scrolling container, and every row is in it. */
    val everyRowIsInOneScrollingList: suspend ComposeUiTest.() -> Unit = {
        val rows = rowsWith(
            ProjectSummary(ViewPreviewData.seededProject, itemCount = 7),
            ProjectSummary(ViewPreviewData.secondProject, itemCount = 1),
        )
        listShowing(rows)

        onNodeWithTag(ProjectsListTag).assertIsDisplayed()
        rows.forEach { onNodeWithTag(ProjectRowTag(it.summary)).assertIsDisplayed() }
    }

    /**
     * The new-project button survives a list of rows, and still works.
     *
     * The button used to be measured at **zero height** whenever the list had anything in it:
     * the rows were a `fillMaxSize` child of a `Column`, so they took the whole height and
     * left the button nothing. It was still in the tree, still in every screenshot taken of
     * an empty list, and completely unhittable on a real one — with a single project seeded,
     * the one action that creates a project could not be pressed.
     *
     * It went unnoticed because the empty state is the only case the old scenarios rendered,
     * and the empty state draws no list. Asserted by pressing it, not by looking for it: a
     * zero-height node is `assertIsDisplayed`-clean on some bounds, and a button that cannot
     * be reached is the entire bug.
     */
    val theNewProjectButtonWorksWithRowsPresent: suspend ComposeUiTest.() -> Unit = {
        var asked = false
        listShowing(
            rows = rowsWith(ProjectSummary(ViewPreviewData.seededProject, itemCount = 7)),
            onNewProject = { asked = true },
        )

        onNodeWithTag(ProjectsListNewTag).assertIsDisplayed()
        onNodeWithTag(ProjectsListNewTag).performClick()

        assertTrue(asked, "expected the new-project button to be reachable with rows present")
    }
}

/**
 * A drag that starts but does not commit.
 *
 * Two things make this number what it is.
 *
 * It has to be **past the touch slop**, around 18px, or the gesture never begins and the
 * swipe is indistinguishable from a tap - the first version of this used 12px and the test
 * was passing for the wrong reason, asserting that a tap still works. And it has to be
 * **well short of the commit distance**, which is [SwipeDefaults.threshold] plus that same
 * slop, or a loosened threshold would quietly turn a refusal test into a commit test.
 *
 * Written as a literal rather than derived from either number, so that changing the threshold
 * fails this loudly instead of quietly moving what it checks.
 */
private const val SHORT_SWIPE_PX = 30f

/** Builds rows, so a scenario can say what it is about rather than how a row is shaped. */
private fun rowsWith(
    vararg summaries: ProjectSummary,
    outstanding: Int = 1,
): List<ProjectRow> = summaries.map { ProjectRow(summary = it, outstandingCount = outstanding) }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.listShowing(
    rows: List<ProjectRow>,
    onConfirmRemove: (ProjectID) -> Unit = {},
    onDismissRemove: () -> Unit = {},
    onOpen: (ProjectID) -> Unit = {},
    onNewProject: (() -> Unit)? = null,
) {
    setContent {
        Themed {
            ProjectsListScreen(
                rows = rows,
                notice = null,
                isLoading = false,
                errorMessage = null,
                onOpen = onOpen,
                onConfirmRemove = onConfirmRemove,
                onDismissRemove = onDismissRemove,
                onDismissNotice = {},
                onDismissError = {},
                onNewProject = onNewProject,
            )
        }
    }
}
