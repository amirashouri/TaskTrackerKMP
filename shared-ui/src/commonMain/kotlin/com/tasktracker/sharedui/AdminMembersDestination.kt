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
                if (model.createMember(email, password, role, position)) model.closeAddForm()
            }
        }

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
