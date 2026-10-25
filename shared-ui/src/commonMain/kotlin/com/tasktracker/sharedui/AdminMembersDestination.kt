package com.tasktracker.sharedui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.ttSpacing
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
 * The two arrangements differ in two things: what the add-member form is drawn *in*, and whether
 * the list carries a button to open it. A phone draws the form over a scrim, full screen, opened
 * by that button; a wide window draws the very same panel in the pane beside the list, where it is
 * on screen from the moment the section opens. Everything else — the model, the list, every
 * callback — is identical, so a second composable would be a second copy of this wiring, and the
 * two would drift in the way that costs the most here: one arm wired to a model the other one is
 * not.
 *
 * The decision stays in [NavigationHost], which is the only place that knows the layout class;
 * this composable draws what it is told.
 *
 * ## Why `isAddFormOpen` is forced down when wide, and the button with it
 *
 * [MembersAdminScreen] draws the form as its own overlay when it is open, and carries the button
 * that opens it. In a wide window the form is the right pane for as long as the section is open,
 * so the list is told `false` — otherwise the panel would be composed twice, once over a list the
 * form is not covering — and `showsAddAction = false`, because a button that opened a form
 * already on screen would be a button that does nothing. The model's flag stays the phone's: the
 * wide pane never reads it.
 *
 * ## Why the pane is given a key
 *
 * A pane that is never torn down keeps whatever the form holds, so the fields the admin typed
 * would survive a successful create and the next tap on Create would be a duplicate account for
 * one address. `key`ing the form on the model's count of finished creates composes a new one —
 * empty — after each success. On the phone the form is cleared by being removed, which is the
 * same thing arrived at from the other side.
 *
 * ## Why the form sits on the list's own top gutter
 *
 * Both panes are one section and their first lines should agree; the members screen pads its top
 * by `ttSpacing.screenGutterLarge`, so the form is given the same number rather than a literal.
 * Not centring: the form grows when a create fails and shows its banner, and a centred panel
 * would move the whole form under the cursor typing into it.
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
            scope.launch {
                // `createMember` answers whether the account was made, and its doc says the caller
                // closes the form on `true` for exactly this reason: a form left open over a list
                // that now shows the new row reads as a create that did not happen.
                if (model.createMember(email, password, role, position)) model.closeAddForm()
            }
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
            resetCode = model.resetCode,
            isGeneratingResetCode = model.isGeneratingResetCode,
            showsAddAction = !isWide,
            onBack = onBack,
            onSelectBucket = { bucket -> scope.launch { model.selectBucket(bucket) } },
            onRequestDelete = { user -> model.requestDelete(user) },
            onCancelDelete = { model.cancelDelete() },
            onConfirmDelete = { scope.launch { model.confirmDelete() } },
            onReactivate = { user -> scope.launch { model.reactivate(user) } },
            onGenerateResetCode = { user -> scope.launch { model.generateResetCode(user) } },
            onDismissResetCode = { model.dismissResetCode() },
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
            key(model.completedCreates) {
                AddMemberForm(
                    isCreating = model.isCreating,
                    errorMessage = model.createFailure?.message,
                    onCreate = onCreate,
                    onDismiss = null,
                    modifier = Modifier.padding(top = ttSpacing.screenGutterLarge),
                )
            }
        },
    )
}
