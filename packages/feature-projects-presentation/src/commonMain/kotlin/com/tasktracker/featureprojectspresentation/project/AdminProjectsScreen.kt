package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTGlassBackButton
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_projects_empty
import com.tasktracker.resources.admin_projects_loading
import com.tasktracker.resources.admin_projects_segment_active
import com.tasktracker.resources.admin_projects_segment_archived
import com.tasktracker.resources.admin_projects_title
import com.tasktracker.resources.project_new
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.compose.resources.stringResource

/**
 * One bucket of every project on the server, with the way out and the way to add one.
 *
 * ## Why this screen does not know about the create form
 *
 * The form is the section's, not this list's: on a phone it is a sheet over this screen, and in a
 * wide window it is the pane beside it — and in neither arrangement is the draft this screen's
 * business. What the list does need to know is that one landed, which is what [refreshKey] says.
 * The same reasoning as `MembersAdminScreen`, whose form is a parameter of its own for the same
 * reason.
 *
 * ## Why the bucket is local state and the list is not
 *
 * A bucket switch and a create both re-read the server, and nothing outside this screen chooses
 * either, so both are remembered here. The rows are the server's answer, held only long enough to
 * draw: an admin list that cached would show a project the next admin has already archived.
 */
@Composable
fun AdminProjectsScreen(
    port: AdminProjectsPort,
    onProjectSelected: (ProjectID) -> Unit,
    onBack: () -> Unit,
    onOpenAddForm: () -> Unit,
    modifier: Modifier = Modifier,
    showsAddAction: Boolean = true,
    /**
     * The number of creates that have landed in the section's own form.
     *
     * A key rather than a callback, because what the list has to do about one is read the server
     * again — never append: the buckets come back newest first, so a project created a moment ago
     * belongs at the top of the active one, which only the server's order puts it at. The
     * component says what happened by moving this number; the read is this screen's.
     */
    refreshKey: Int = 0,
) {
    var status by remember { mutableStateOf(ProjectAdminStatus.Active) }
    var projects by remember { mutableStateOf<List<AdminProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(status, refreshKey) {
        isLoading = true
        failure = null
        projects = try {
            port.projects(status)
        } catch (cancelled: CancellationException) {
            // Leaving the screen cancels this effect; that is not a load that failed, and
            // holding it as one both leaks a stale error onto the next status and stops the
            // cancellation propagating.
            throw cancelled
        } catch (error: AppError) {
            failure = error
            emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(AdminProjectsScreenTag)
            .padding(ttSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        AdminProjectsHeader(
            onBack = onBack,
            onOpenAddForm = onOpenAddForm,
            showsAddAction = showsAddAction,
        )

        TTGlassSegmentedPicker(
            options = ProjectAdminStatus.entries,
            selection = status,
            onSelect = { next -> status = next },
            title = { option ->
                when (option) {
                    ProjectAdminStatus.Active ->
                        stringResource(Res.string.admin_projects_segment_active)
                    ProjectAdminStatus.Archived ->
                        stringResource(Res.string.admin_projects_segment_archived)
                }
            },
        )

        // Held as the AppError rather than as a sentence, and read through this local: `AppError`
        // owns the wording of every failure in the app, so re-wording it here would make the same
        // error read two ways. The local is what lets the `when` below smart-cast it.
        val error = failure
        when {
            isLoading -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_loading),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            error != null -> {
                ttText(
                    text = error.message.orEmpty(),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            projects.isEmpty() -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_empty, status.wireValue),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = projects,
                        key = { adminProject -> adminProject.project.id.stringValue },
                    ) { adminProject ->
                        ProjectRowView(
                            project = ProjectSummary(adminProject.project, itemCount = 0),
                            onOpen = { onProjectSelected(adminProject.project.id) },
                            onRemove = { /* admin project deletion is not in scope for this screen */ },
                            onEdit = { onProjectSelected(adminProject.project.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The way out, the page title, and the way to add a project — in that order across the header.
 *
 * The same header the members screen draws, for the reason it gives there: a back control the shell
 * cannot supply (there is no system back on a desktop window, and re-selecting the active tab does
 * nothing), and an action in the header rather than a floating button, which would have to be paid
 * for in reserved layout as well as in the touch target that has to aim past it.
 *
 * The create action can be absent. In a wide window the form is the pane beside this list and is on
 * screen for as long as the section is open, so a button that opened it would open what is already
 * open; the shell says so with [showsAddAction].
 */
@Composable
private fun AdminProjectsHeader(
    onBack: () -> Unit,
    onOpenAddForm: () -> Unit,
    showsAddAction: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        TTGlassBackButton(onBack = onBack)
        ttText(
            text = stringResource(Res.string.admin_projects_title),
            style = ttTypography.titleSmall,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        if (showsAddAction) {
            ttGlassIconButton(
                image = ttActionIcons.add,
                contentDescription = stringResource(Res.string.project_new),
                onClick = onOpenAddForm,
                modifier = Modifier.testTag(AdminProjectsAddTag),
            )
        }
    }
}

/**
 * The sections screen's root, for a shell test asking which pane it landed in.
 *
 * The members twin is `MembersAdminScreenTag`, and both exist for the same reason: the shell's
 * placeholder pages are tagged, so a scenario that bound a real port would otherwise have had no
 * anchor at all for "the real section is in this pane" — the tag would have to come out of the
 * placeholder when the port was bound, which is the wrong way round.
 */
const val AdminProjectsScreenTag = "adminProjects.screen"

/** The header's add button. */
const val AdminProjectsAddTag = "adminProjects.add"
