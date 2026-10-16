package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectspresentation.AdminProjectsScreen
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope

/**
 * One project in the admin projects section, or the section's list.
 *
 * This is the real screen behind [AppRoute.AdminProjects]. The shell renders the placeholder
 * until the composition root binds an [AdminProjectsPort], and once it does this destination is
 * what the wide and narrow arrangements alike render into.
 *
 * Selecting a project in the list does not edit it inline here: the admin projects screen is a
 * list with an edit affordance, and editing is the existing `ProjectEditorScreen`. So a tap hands
 * the project id back to the caller, which opens the editor the same way the projects list does.
 */
@Composable
fun AdminProjectsDestination(
    projectID: ProjectID?,
    router: Router,
    adminProjectsPort: AdminProjectsPort,
    scope: CoroutineScope,
) {
    AdminProjectsScreen(
        port = adminProjectsPort,
        onProjectSelected = { id ->
            router.openAdminProjects(id)
        },
        modifier = Modifier,
    )
}
