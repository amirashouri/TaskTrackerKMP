package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The editor's own fields must show the draft, not a constant.
 *
 * ## The defect these exist for
 *
 * The title field was bound to the surface's header string — "New Task" on a create, "Edit
 * TT-1" on an edit — instead of the draft. The model updated correctly on every keystroke,
 * so no model test could see it; the field simply kept rendering the header, which made the
 * editor look dead. The header and the field share a width and a word for word name only
 * by accident, and one shared parameter is exactly how they came to.
 *
 * The field is found by the tag `ProjectsComponents` derives from its own label, the same
 * way the project-creator's name field is.
 *
 * Each value is a `suspend ComposeUiTest.() -> Unit` so per-target entry points in
 * `ProjectsUiJvmTest`/`ProjectsUiIosTest`/`ProjectsUiAndroidHostTest` run an identical body.
 */
@OptIn(ExperimentalTestApi::class)
object WorkItemEditorScenarios {

    /**
     * Type into the title field; it must render what was typed.
     *
     * A green here means the draft reached the field. Before the fix it went red: the field
     * still read "New Task" after the input, because it rendered the header string.
     */
    val theTitleFieldShowsWhatYouType: suspend ComposeUiTest.() -> Unit = {
        setContent { editorSheet() }

        onNodeWithTag("title").performTextInput("Ship the migration")

        onNodeWithTag("title").assertTextEquals("Ship the migration")
    }

    /**
     * Save, Cancel and File Away float over the form instead of ending it.
     *
     * ## The defect this exists for
     *
     * The row used to be the last child of the scrolling column. On a phone the details, the
     * comment thread and the attachments put it several screens down, so saving a draft meant
     * hunting for the button — and a save that has to be found is a draft the user abandons.
     * The row is now a sibling of the scroll, pinned above the tab-bar inset: it is on screen
     * before the first scroll and its bounds do not move while the form does.
     *
     * A real four-comment thread (`ViewPreviewData.previewComments`) forces the overflow,
     * rather than a spacer, so the test measures the shape the editor actually renders. The
     * title's bounds are checked as well: without that, a scroll that never happened would
     * make "same bounds" pass for the wrong reason.
     */
    val theActionsFloatOverTheScrollingForm: suspend ComposeUiTest.() -> Unit = {
        setContent {
            editorSheet(isCreating = false, comments = ViewPreviewData.previewComments)
        }

        val save = onNodeWithTag(WorkItemSaveTag)
        val cancel = onNodeWithTag(WorkItemCancelTag)
        val fileAway = onNodeWithTag(WorkItemFileAwayTag)
        save.assertIsDisplayed()
        cancel.assertIsDisplayed()
        fileAway.assertIsDisplayed()

        val saveBefore = save.fetchSemanticsNode().boundsInRoot
        val titleBefore = onNodeWithTag("title").fetchSemanticsNode().boundsInRoot

        // The deepest node the editor composes, so bringing it in is a larger move than a
        // fixed swipe would guarantee on a host with a different window.
        onNodeWithTag(WorkItemCommentTag(ViewPreviewData.previewComments.last().id))
            .performScrollTo()
        waitForIdle()

        val titleAfter = onNodeWithTag("title").fetchSemanticsNode().boundsInRoot
        assertTrue(
            titleAfter.top < titleBefore.top,
            "The form did not scroll: the title's top is ${titleAfter.top}px after and " +
                "${titleBefore.top}px before, so the action assertion would be vacuous.",
        )

        save.assertIsDisplayed()
        cancel.assertIsDisplayed()
        fileAway.assertIsDisplayed()
        val saveAfter = save.fetchSemanticsNode().boundsInRoot
        assertEquals(
            expected = saveBefore.top,
            actual = saveAfter.top,
            absoluteTolerance = 0.5f,
            message = "Save moved with the form: top ${saveBefore.top}px before, " +
                "${saveAfter.top}px after. A floating action must not scroll.",
        )
        assertEquals(
            expected = saveBefore.left,
            actual = saveAfter.left,
            absoluteTolerance = 0.5f,
            message = "Save moved sideways: left ${saveBefore.left}px before, " +
                "${saveAfter.left}px after.",
        )
    }

    /**
     * The create editor, with the title as real state the way the host wires it.
     *
     * Every other field is a fixed value: this scenario is about the draft reaching the
     * field, so the title is the only thing that moves. The thread defaults to absent, as
     * the create editor passes it; the floating-action scenario passes a thread to overflow
     * the fold.
     */
    @Composable
    private fun editorSheet(
        isCreating: Boolean = true,
        comments: List<Comment>? = null,
    ) {
        var title by remember { mutableStateOf("") }
        ttTheme(ThemeScheme.Light) {
            WorkItemEditorSheet(
                title = title,
                details = "",
                storyPointsText = "",
                status = WorkItemStatus.Backlog,
                priority = Priority.Medium,
                assigneeID = null,
                currentUserID = null,
                currentUserName = null,
                otherAssigneeLabel = null,
                hasDueDate = false,
                recurrence = null,
                repeatAnchor = null,
                isCreating = isCreating,
                issuedKey = null,
                selectableStatuses = emptyList(),
                isFiledAway = false,
                canSave = title.isNotBlank(),
                isBusy = false,
                errorMessage = null,
                onTitleChanged = { title = it },
                onDetailsChanged = {},
                onStoryPointsChanged = {},
                onStatusChanged = {},
                onPriorityChanged = {},
                onAssigneeChanged = {},
                onToggleDueDate = {},
                onRecurrenceChanged = {},
                onFileAway = {},
                onSave = {},
                onCancel = {},
                onDismissError = {},
                comments = comments,
            )
        }
    }
}
