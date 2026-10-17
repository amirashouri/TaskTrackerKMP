package com.tasktracker.sharedui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectspresentation.AdminProjectEditorModel
import com.tasktracker.featureprojectspresentation.AdminProjectsScreen
import com.tasktracker.featureprojectspresentation.ProjectEditorScreen
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_projects_loading
import com.tasktracker.resources.admin_projects_missing
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * One project in the admin projects section, or the section's list.
 *
 * This is the real screen behind [AppRoute.AdminProjects]. The shell renders the placeholder
 * until the composition root binds an [AdminProjectsPort], and once it does this destination is
 * what the wide and narrow arrangements alike render into.
 *
 * A selected [projectID] is the edit page: the list's rows hand the id back to the router, the
 * router puts it on the pushed route, and this destination is what reads it. Editing reuses the
 * stateless [ProjectEditorScreen] the ordinary projects list edits through — an admin edits
 * projects they are not a member of, so the draft comes from [AdminProjectsPort] instead of the
 * local store, and that is the only difference between the two editors.
 *
 * Closing returns to the list rather than popping a level: [Router.openAdminProjects] with no id
 * is the list, and it is the same call the section's entry row makes.
 */
@Composable
fun AdminProjectsDestination(
    projectID: ProjectID?,
    router: Router,
    adminProjectsPort: AdminProjectsPort,
    scope: CoroutineScope,
) {
    if (projectID == null) {
        AdminProjectsScreen(
            port = adminProjectsPort,
            onProjectSelected = { id ->
                router.openAdminProjects(id)
            },
            modifier = Modifier,
        )
        return
    }

    AdminProjectEditor(
        projectID = projectID,
        adminProjectsPort = adminProjectsPort,
        onClose = { router.openAdminProjects() },
        scope = scope,
    )
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
