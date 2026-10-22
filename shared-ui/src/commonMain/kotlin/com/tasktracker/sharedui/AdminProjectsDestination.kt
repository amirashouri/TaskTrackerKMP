package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectspresentation.AdminProjectCreateModel
import com.tasktracker.featureprojectspresentation.AdminProjectEditorModel
import com.tasktracker.featureprojectspresentation.AdminProjectsScreen
import com.tasktracker.featureprojectspresentation.NewProjectForm
import com.tasktracker.featureprojectspresentation.NewProjectSheet
import com.tasktracker.featureprojectspresentation.ProjectEditorScreen
import com.tasktracker.featureprojectspresentation.ProjectTagsSection
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_projects_loading
import com.tasktracker.resources.admin_projects_missing
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The projects administration section, in whichever arrangement the shell called it for: the list
 * with one project's edit page, and the create form the section owns.
 *
 * ## Why the arrangement is a parameter and not two composables
 *
 * The two arrangements differ in what the create form is drawn *in* and whether the list carries a
 * button to open it — a phone draws the form as a sheet over the list, opened by that button; a wide
 * window draws the very same panel in the pane beside the list, where it is on screen from the
 * moment the section opens. Everything else — the ports, the list, every callback — is identical,
 * so a second composable would be a second copy of this wiring, and the two would drift in the way
 * that costs the most here: one arm wired to a model the other one is not. The same argument
 * [AdminMembersDestination] makes, and the same [isWide] parameter.
 *
 * The decision stays in [NavigationHost], which is the only place that knows the layout class;
 * this composable draws what it is told.
 *
 * ## Why the wide pane carries the editor as well as the form
 *
 * The section has both (the members section has only the form): a project selected in the list is
 * its edit page, which is a route, and with nothing selected the pane is the create form. Ordering
 * them as a selection first and a form second is what the pane's own placeholder used to say by
 * being empty — a wide window opens the section ready to make a project, which is what an admin
 * opening this section is usually there for.
 *
 * ## Why the create model lives here and not in the list screen
 *
 * The form is drawn outside the list in both arrangements — a sheet over it, a pane beside it — and
 * its draft has to survive neither the list re-reading nor the bucket changing. A model inside the
 * list screen could not be handed to the pane, which is the same reason the members section keeps
 * its own model here.
 *
 * ## Why the phone's form is a sheet and the pane's is bare
 *
 * [NewProjectSheet] is what the board already opens a project form in, so the phone reuses it
 * rather than growing a second container for the same fields. The wide pane *is* the form: there is
 * nothing to slide over and nothing to dismiss, so [NewProjectForm] is drawn directly, with `null`
 * for its dismiss and no Cancel to imply otherwise.
 */
@Composable
fun AdminProjectsDestination(
    projectID: ProjectID?,
    router: Router,
    adminProjectsPort: AdminProjectsPort,
    memberAdminPort: MemberAdminPort?,
    scope: CoroutineScope,
    isWide: Boolean = false,
) {
    val create = remember(adminProjectsPort) { AdminProjectCreateModel(adminProjectsPort) }

    val tab = router.state.value.selectedTab
    val currentPath = router.path(tab)
    val onBack = {
        if (currentPath.size > 1) {
            router.setPath(currentPath.dropLast(1), tab)
        }
    }
    val closeEditor: () -> Unit = { router.openAdminProjects() }

    // The picker's directory is the admin users list rather than the local team store: an account
    // an admin created a moment ago is on the server and not yet in this device's synced directory,
    // so a picker reading the store would not offer the colleague the admin just added.
    // `MemberAdminPort.searchMembers` is not used for this, because it still answers from that same
    // local directory; the active bucket is fetched and matched here instead, by the rule the
    // directory itself documents — a case-insensitive substring of the name or the address, sorted
    // by what the row is called.
    val activeMembers: suspend () -> List<User> = { memberAdminPort?.members(UserStatus.Active).orEmpty() }
    val searchMembers: suspend (String) -> List<User> = { query ->
        val needle = query.trim().lowercase()
        activeMembers()
            .filter { user ->
                needle.isEmpty() ||
                    user.displayName.orEmpty().lowercase().contains(needle) ||
                    user.email.lowercase().contains(needle)
            }
            .sortedBy { it.preferredGreeting.lowercase() }
    }
    val resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = activeMembers()
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    }

    val listPane: @Composable () -> Unit = {
        AdminProjectsScreen(
            port = adminProjectsPort,
            onProjectSelected = { id -> router.openAdminProjects(id) },
            onBack = onBack,
            onOpenAddForm = { create.openAddForm() },
            // A wide window has the form on screen for as long as the section is open, so a button
            // that opened it would open what is already open.
            showsAddAction = !isWide,
            refreshKey = create.completedCreates,
        )
    }

    // Keyed on the model's count of finished creates, because the pane is never torn down: a new
    // form after each success is what empties the member picker's own search box. The fields
    // themselves are the model's, and it empties those itself — see `AdminProjectCreateModel`.
    val createForm: @Composable () -> Unit = {
        key(create.completedCreates) {
            NewProjectForm(
                name = create.name,
                keyPrefix = create.keyPrefix,
                details = create.details,
                canSave = create.canSave,
                onNameChanged = create::onNameChanged,
                onKeyPrefixChanged = create::onKeyPrefixChanged,
                onDetailsChanged = create::onDetailsChanged,
                onSave = { scope.launch { create.create() } },
                onDismiss = null,
                modifier = Modifier.padding(ttSpacing.lg),
                isBusy = create.isCreating,
                selectedMemberIDs = create.memberIDs,
                onMemberIDsChanged = create::onMemberIDsChanged,
                searchMembers = searchMembers,
                resolveSelected = resolveSelected,
                errorMessage = create.errorMessage,
            )
        }
    }

    if (isWide) {
        AdminTwoPanes(
            listPane = listPane,
            detailPane = if (projectID == null) {
                createForm
            } else {
                {
                    AdminProjectEditor(
                        projectID = projectID,
                        adminProjectsPort = adminProjectsPort,
                        onClose = closeEditor,
                        scope = scope,
                    )
                }
            },
        )
        return
    }

    if (projectID != null) {
        AdminProjectEditor(
            projectID = projectID,
            adminProjectsPort = adminProjectsPort,
            onClose = closeEditor,
            scope = scope,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        listPane()
        // Composed whether or not it is open, the same shape `ProjectsHost` uses for the board's
        // sheet: the scaffold owns the hide transition, so the form is not torn out from under a
        // dismissal in flight. Its content is removed once the exit finishes, which is what gives
        // the phone a fresh picker on the next open.
        NewProjectSheet(
            name = create.name,
            keyPrefix = create.keyPrefix,
            details = create.details,
            canSave = create.canSave,
            onNameChanged = create::onNameChanged,
            onKeyPrefixChanged = create::onKeyPrefixChanged,
            onDetailsChanged = create::onDetailsChanged,
            onSave = {
                scope.launch {
                    // Closing on `true` only: a failed create leaves the form open with its
                    // values, so the admin corrects a typo rather than retyping the form.
                    if (create.create()) create.closeAddForm()
                }
            },
            onCancel = create::closeAddForm,
            visible = create.isAddFormOpen,
            onDismissRequest = create::closeAddForm,
            isBusy = create.isCreating,
            selectedMemberIDs = create.memberIDs,
            onMemberIDsChanged = create::onMemberIDsChanged,
            searchMembers = searchMembers,
            resolveSelected = resolveSelected,
            errorMessage = create.errorMessage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The edit page for one admin project.
 *
 * Private to this file because it is not a destination of its own: the router never names it, and
 * the id it edits is the same route parameter the list above lives beside.
 */
@Composable
private fun AdminProjectEditor(
    projectID: ProjectID,
    adminProjectsPort: AdminProjectsPort,
    onClose: () -> Unit,
    scope: CoroutineScope,
) {
    // Keyed on the id as well as the port: a second project selected without leaving the section
    // is a different draft, and reusing the model would seed the new page from the old row.
    val model = remember(projectID, adminProjectsPort) {
        AdminProjectEditorModel(projectID, adminProjectsPort)
    }
    LaunchedEffect(model) { model.load() }

    when {
        model.isLoading -> {
            AdminProjectNotice(stringResource(Res.string.admin_projects_loading))
            return
        }
        model.isMissing -> {
            // Distinct from the form's own error footnote and from a failed read: the row is
            // gone, so there is nothing to edit and no retry worth offering.
            AdminProjectNotice(stringResource(Res.string.admin_projects_missing))
            return
        }
    }

    ProjectEditorScreen(
        name = model.name,
        keyPrefix = model.keyPrefix,
        details = model.details,
        isCreating = false,
        canSave = model.canSave,
        onNameChanged = model::onNameChanged,
        onDetailsChanged = model::onDetailsChanged,
        onSave = { scope.launch { if (model.save()) onClose() } },
        onCancel = onClose,
        errorMessage = model.errorMessage,
        isBusy = model.isSaving,
        // Only the admin editor gets the catalog composer; the member editor passes no slot at
        // all. See ProjectEditorScreen's `tagsSection`.
        tagsSection = {
            ProjectTagsSection(
                tags = model.tags,
                onAdd = model::addTag,
                onRemove = model::removeTag,
                isBusy = model.isSaving,
            )
        },
    )
}

@Composable
private fun AdminProjectNotice(text: String) {
    ttText(
        text = text,
        style = ttTypography.body,
        color = ttLabel(),
        modifier = Modifier.padding(ttSpacing.xl),
    )
}
