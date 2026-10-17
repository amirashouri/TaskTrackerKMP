package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamspresentation.AddMemberForm
import com.tasktracker.featureteamspresentation.MemberAdminModel
import com.tasktracker.featureteamspresentation.MembersAdminScreen
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The members administration section, in whichever arrangement the shell called it for.
 *
 * ## Why the arrangement is a parameter and not two composables
 *
 * The two arrangements differ in one thing: what the add-member form is drawn *in*. A phone
 * draws it over a scrim, full screen; a wide window draws the very same panel in the pane beside
 * the list. Everything else — the model, the list, every callback — is identical, so a second
 * composable would be a second copy of this wiring, and the two would drift in the way that
 * costs the most here: one arm wired to a model the other one is not.
 *
 * The decision stays in [NavigationHost], which is the only place that knows the layout class;
 * this composable draws what it is told.
 *
 * ## Why `isAddFormOpen` is forced down when wide
 *
 * [MembersAdminScreen] draws the form as its own overlay when it is open. In a wide window the
 * form is the right pane instead, so the list is told `false` — otherwise the panel would be
 * composed twice, once over a list the form is not covering. The model's flag is still the one
 * state: the list's "Add member" button opens the pane, and the pane's Cancel closes it.
 *
 * ## There is no member detail, and no `memberID`
 *
 * The section is a list with delete and reactivate on the row, plus this form (D6). It took a
 * `memberID` until 2026-10-17 and never read it; the route no longer carries one.
 */
@Composable
fun AdminMembersDestination(
    router: Router,
    memberAdminPort: MemberAdminPort,
    scope: CoroutineScope = rememberCoroutineScope(),
    isWide: Boolean = false,
) {
    val model = remember(memberAdminPort) { MemberAdminModel(memberAdminPort) }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    val tab = router.state.value.selectedTab
    val currentPath = router.path(tab)
    val onBack = {
        if (currentPath.size > 1) {
            router.setPath(currentPath.dropLast(1), tab)
        }
    }
    val onCreate: (String, String, MemberRole, String?) -> Unit =
        { email, password, role, position ->
            scope.launch { model.createMember(email, password, role, position) }
        }

    // A name for the list, because the wide arrangement hands it to a pane slot and the narrow
    // one calls it — the same shape `NavigationHost` uses for `tabRoot` and `pushedPane`.
    val listPane: @Composable () -> Unit = {
        MembersAdminScreen(
            bucket = model.bucket,
            members = model.members,
            isLoading = model.isLoading,
            errorMessage = model.listFailure?.message ?: model.createFailure?.message,
            pendingDeletion = model.pendingDeletion,
            isAddFormOpen = !isWide && model.isAddFormOpen,
            isCreating = model.isCreating,
            createErrorMessage = model.createFailure?.message,
            onBack = onBack,
            onSelectBucket = { bucket -> scope.launch { model.selectBucket(bucket) } },
            onRequestDelete = { user -> model.requestDelete(user) },
            onCancelDelete = { model.cancelDelete() },
            onConfirmDelete = { scope.launch { model.confirmDelete() } },
            onReactivate = { user -> scope.launch { model.reactivate(user) } },
            onOpenAddForm = { model.openAddForm() },
            onCloseAddForm = { model.closeAddForm() },
            onCreate = onCreate,
            onDismissError = { model.dismissListFailure() },
        )
    }

    if (!isWide) {
        listPane()
        return
    }

    AdminTwoPanes(
        listPane = listPane,
        detailPane = {
            if (model.isAddFormOpen) {
                AddMemberForm(
                    isCreating = model.isCreating,
                    errorMessage = model.createFailure?.message,
                    onCreate = onCreate,
                    onDismiss = { model.closeAddForm() },
                )
            } else {
                // The pane is empty rather than worded while nothing is being added: the
                // sentence belongs with the section's own strings, and this pane's job is to
                // say "nothing here yet" without inventing copy for a state that has a button.
                AdminDetailPlaceholder()
            }
        },
    )
}
