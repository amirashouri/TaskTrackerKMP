package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamspresentation.MemberAdminModel
import com.tasktracker.featureteamspresentation.MembersAdminScreen
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AdminMembersDestination(
    memberID: UserID?,
    router: Router,
    memberAdminPort: MemberAdminPort,
    scope: CoroutineScope = rememberCoroutineScope(),
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

    MembersAdminScreen(
        bucket = model.bucket,
        members = model.members,
        isLoading = model.isLoading,
        errorMessage = model.listFailure?.message ?: model.createFailure?.message,
        pendingDeletion = model.pendingDeletion,
        isAddFormOpen = model.isAddFormOpen,
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
        onCreate = { email, password, role, position ->
            scope.launch { model.createMember(email, password, role, position) }
        },
        onDismissError = { model.dismissListFailure() },
    )
}
