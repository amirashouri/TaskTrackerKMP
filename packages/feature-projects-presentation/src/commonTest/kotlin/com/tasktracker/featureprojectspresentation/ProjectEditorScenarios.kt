package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.tasktracker.designsystem.TTBottomSheetScrimTag
import com.tasktracker.designsystem.TTConfirmPopupCancelTag
import com.tasktracker.designsystem.TTConfirmPopupConfirmTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.assertEquals

/**
 * Cancel on the project editor must not throw away a draft without asking.
 *
 * ## What these pin
 *
 * The screen decides nothing about what "dirty" means - the model hands it
 * `hasUnsavedChanges` - so these scenarios pin the two behaviours the flag switches: a clean
 * form closes on Cancel, and a dirty one opens the design-system confirmation instead of
 * calling `onCancel`. The *model* tests pin which fields make a draft dirty; a UI test that
 * guessed dirty from a typed character would pass for the wrong reason.
 *
 * All are structural: the popup is in the same semantics tree (see `ConfirmPopup`), so the
 * assertion does not read pixels.
 */
@OptIn(ExperimentalTestApi::class)
object ProjectEditorScenarios {

    /** Cancel on an unchanged form is not a destructive act, so it must never prompt. */
    val cancellingACleanEditorClosesIt: suspend ComposeUiTest.() -> Unit = {
        var cancels = 0
        setContent {
            ttTheme(ThemeScheme.Light) {
                editor(hasUnsavedChanges = false, onCancel = { cancels += 1 })
            }
        }

        onNodeWithTag(ProjectEditorCancelTag).performClick()

        assertEquals(1, cancels, "a clean form asked before closing and then did not close")
        onNodeWithTag(ProjectEditorDiscardTag).assertDoesNotExist()
    }

    /**
     * Cancel with a draft asks first, and only the confirm button discards it.
     *
     * The intermediate Cancel is the point: a popup that treated any dismissal as consent
     * would be a slower version of not having one.
     */
    val cancellingADirtyEditorAsksBeforeDiscarding: suspend ComposeUiTest.() -> Unit = {
        var cancels = 0
        setContent {
            ttTheme(ThemeScheme.Light) {
                editor(hasUnsavedChanges = true, onCancel = { cancels += 1 })
            }
        }

        onNodeWithTag(ProjectEditorCancelTag).performClick()

        onNodeWithTag(ProjectEditorDiscardTag).assertIsDisplayed()
        assertEquals(0, cancels, "the first tap on Cancel discarded the draft without asking")

        onNodeWithTag(TTConfirmPopupCancelTag).performClick()

        onNodeWithTag(ProjectEditorDiscardTag).assertDoesNotExist()
        assertEquals(0, cancels, "dismissing the prompt closed the editor")

        onNodeWithTag(ProjectEditorCancelTag).performClick()
        onNodeWithTag(TTConfirmPopupConfirmTag).performClick()

        assertEquals(1, cancels, "confirming the discard did not close the editor")
    }

    /**
     * Cancel on an unchanged project sheet closes without a prompt.
     *
     * The sheet is the member's project editor; the full-screen form above is the admin's.
     * Both read the same model flag, so a clean draft must behave identically in both.
     */
    val cancellingACleanProjectSheetClosesIt: suspend ComposeUiTest.() -> Unit = {
        var cancels = 0
        setContent {
            ttTheme(ThemeScheme.Light) {
                projectSheet(
                    hasUnsavedChanges = false,
                    onCancel = { cancels += 1 },
                    onDismissRequest = {},
                )
            }
        }

        onNodeWithTag(NewProjectCancelTag).performClick()

        assertEquals(1, cancels, "a clean sheet asked before closing and then did not close")
        onNodeWithTag(NewProjectDiscardTag).assertDoesNotExist()
    }

    /**
     * Cancel on a dirty project sheet asks, and so does the scrim.
     *
     * The scrim is the sheet's other exit. If only the button were guarded, a tap outside the
     * form would still throw the draft away — and it would be the tap a thumb reaches for by
     * accident. Each confirmation runs the callback that opened it: Cancel closes the editor,
     * the scrim runs the host's dismiss.
     */
    val cancellingADirtyProjectSheetAsksBeforeDiscarding: suspend ComposeUiTest.() -> Unit = {
        var cancels = 0
        var dismisses = 0
        setContent {
            ttTheme(ThemeScheme.Light) {
                projectSheet(
                    hasUnsavedChanges = true,
                    onCancel = { cancels += 1 },
                    onDismissRequest = { dismisses += 1 },
                )
            }
        }

        onNodeWithTag(NewProjectCancelTag).performClick()

        onNodeWithTag(NewProjectDiscardTag).assertIsDisplayed()
        assertEquals(0, cancels, "the first tap on Cancel discarded the draft without asking")

        onNodeWithTag(TTConfirmPopupCancelTag).performClick()
        onNodeWithTag(NewProjectDiscardTag).assertDoesNotExist()
        assertEquals(0, cancels, "dismissing the prompt closed the sheet")

        // The scrim's semantics action, not a tap: the scrim is behind the sheet, so a
        // centre-point click would land on the form and never reach this node. The action is
        // what a tap outside the sheet invokes.
        onNodeWithTag(TTBottomSheetScrimTag)
            .performSemanticsAction(SemanticsActions.OnClick)

        onNodeWithTag(NewProjectDiscardTag).assertIsDisplayed()
        assertEquals(0, dismisses, "the scrim discarded the draft without asking")

        onNodeWithTag(TTConfirmPopupConfirmTag).performClick()

        assertEquals(1, dismisses, "confirming the scrim discard did not dismiss the sheet")
        assertEquals(0, cancels, "the scrim's confirmation ran Cancel's callback instead")
    }

    /**
     * The catalog section edits the stage it was given, and the callbacks carry the facts the
     * model needs: the id that keeps the lane, the category that drives the rules, and the
     * list position that becomes the board order.
     *
     * Structural, not pixel-reading: rows and controls are addressed by their tags and the
     * assertions are the callbacks the section's buttons ran.
     */
    val theStatusCatalogSectionEditsAndRemovesStages: suspend ComposeUiTest.() -> Unit = {
        val todo = StatusDefinition("s-todo", "Todo", WorkItemStatus.Todo, order = 0)
        val build = StatusDefinition(
            "s-build",
            "In build",
            WorkItemStatus.InProgress,
            order = 1,
            color = TagColor.Teal,
        )
        var updates = emptyList<Triple<String, String, WorkItemStatus>>()
        var removes = emptyList<String>()
        var moves = emptyList<Pair<Int, Int>>()
        setContent {
            ttTheme(ThemeScheme.Light) {
                ProjectStatusesSection(
                    statuses = listOf(todo, build),
                    onAdd = { _, _, _ -> true },
                    onUpdate = { id, name, category, _ ->
                        updates = updates + Triple(id, name, category)
                    },
                    onRemove = { removes = removes + it },
                    onMove = { from, to -> moves = moves + (from to to) },
                )
            }
        }

        onNodeWithTag(AdminProjectStatusRowTag(todo)).assertIsDisplayed()
        onNodeWithTag(AdminProjectStatusRowTag(build)).assertIsDisplayed()

        onNodeWithTag(AdminProjectStatusEditTag(build)).performClick()
        onNodeWithTag(AdminProjectStatusNameFieldTag).performTextReplacement("Peer review")
        onNodeWithTag(AdminProjectStatusSaveTag).performClick()

        assertEquals(
            listOf(Triple("s-build", "Peer review", WorkItemStatus.InProgress)),
            updates,
            "a rename keeps the stage id and category, so the lane already on cards survives",
        )

        onNodeWithTag(AdminProjectStatusMoveDownTag(todo)).performClick()
        assertEquals(listOf(0 to 1), moves, "reorder reports the two list positions")

        onNodeWithTag(AdminProjectStatusRemoveTag(todo)).performClick()
        assertEquals(listOf("s-todo"), removes)
    }

    /** The composer refuses a blank stage without calling the model, then adds a named one. */
    val addingAStageSendsTheComposerValues: suspend ComposeUiTest.() -> Unit = {
        var added: Triple<String, WorkItemStatus, TagColor>? = null
        setContent {
            ttTheme(ThemeScheme.Light) {
                ProjectStatusesSection(
                    statuses = emptyList(),
                    onAdd = { name, category, color ->
                        added = Triple(name, category, color)
                        true
                    },
                    onUpdate = { _, _, _, _ -> },
                    onRemove = {},
                    onMove = { _, _ -> },
                )
            }
        }

        onNodeWithTag(AdminProjectStatusAddTag).performClick()
        onNodeWithTag(AdminProjectStatusErrorTag).assertIsDisplayed()
        assertEquals(null, added, "a blank stage must not reach the model")

        onNodeWithTag(AdminProjectStatusNameFieldTag).performTextInput("Ready")
        onNodeWithTag(AdminProjectStatusAddTag).performClick()

        assertEquals(
            Triple("Ready", WorkItemStatus.Todo, TagColor.Blue),
            added,
            "the composer sends its name, the selected category and the selected colour",
        )
    }

    @Composable
    private fun editor(hasUnsavedChanges: Boolean, onCancel: () -> Unit) {
        ProjectEditorScreen(
            name = "Apollo Programme",
            keyPrefix = "AP",
            details = "Two milestones.",
            isCreating = false,
            canSave = true,
            onNameChanged = {},
            onDetailsChanged = {},
            onSave = {},
            onCancel = onCancel,
            hasUnsavedChanges = hasUnsavedChanges,
        )
    }

    @Composable
    private fun projectSheet(
        hasUnsavedChanges: Boolean,
        onCancel: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        NewProjectSheet(
            name = "Apollo Programme",
            keyPrefix = "AP",
            details = "Two milestones.",
            canSave = true,
            onNameChanged = {},
            onKeyPrefixChanged = {},
            onDetailsChanged = {},
            onSave = {},
            onCancel = onCancel,
            hasUnsavedChanges = hasUnsavedChanges,
            visible = true,
            onDismissRequest = onDismissRequest,
        )
    }
}
