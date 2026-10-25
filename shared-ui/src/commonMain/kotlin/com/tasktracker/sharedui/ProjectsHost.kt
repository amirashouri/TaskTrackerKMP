package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectspresentation.NewProjectSheet
import com.tasktracker.featureprojectspresentation.ProjectBoardModel
import com.tasktracker.featureprojectspresentation.ProjectBoardScreen
import com.tasktracker.featureprojectspresentation.ProjectEditorModel
import com.tasktracker.featureprojectspresentation.ProjectEnvironment
import com.tasktracker.featureprojectspresentation.ProjectPickerModel
import com.tasktracker.featureprojectspresentation.ProjectsListModel
import com.tasktracker.featureprojectspresentation.ProjectsListScreen
import com.tasktracker.routing.AppRoute
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The signed-in destination: a project list, and the board a project opens.
 *
 * ## Why the router is the only navigation state, and nothing here keeps its own
 *
 * [ProjectsHost] holds no "which screen am I showing" flag. It reads [Router.state] and
 * renders the top of the Projects stack, which is exactly the rule `AppTab.Projects` with an
 * empty path already encodes: no path is the list, and one path is a board. A local
 * `var showingBoard` would be a second answer to the same question, and the two would
 * disagree on the one case that matters — a deep link or a restored session arriving with a
 * stack the host never built.
 *
 * Collected by hand rather than through a lifecycle-aware `collectAsState`, for the same
 * reason the session is: the [state] flow is a [kotlinx.coroutines.flow.StateFlow] and so
 * replays its current value to a new collector, so a plain `launch` here cannot miss the
 * first emission the way the session's broadcaster could. Seeded from `state.value` as well,
 * so the first frame is the real one rather than the default [com.tasktracker.routing.RouterState].
 *
 * ## Two destinations, and why the board is a push rather than a tab switch
 *
 * `openProject` on the Projects tab *pushes* the board onto this stack, because a dashboard's
 * job is to open things in place. That is the router's decision and this host's
 * consequence: the board is a route, so it needs a way back, and the way back is
 * [Router.popToRoot] on this tab — which is also what [onOpenSwitcher] does, so the
 * project's own name in the toolbar is the control that returns to the list.
 *
 * ## The project creation sheet is local, and the reason is scope rather than preference
 *
 * Creating a project is a *sheet* drawn over this tab and is therefore local state. Task
 * editing is a routed sheet so saves and dismissals return to the destination that opened it.
 * That is a deliberate limit:
 * the tab bar, the Backlog and Archive destinations and a routable editor are Phase 13.13,
 * and a half-built navigation host that claims to render the Projects tab while quietly
 * swallowing a deep link to the Archive is worse than a host that draws what it can reach.
 */
@Composable
fun ProjectsHost(
    router: Router,
    environment: ProjectEnvironment,
    modifier: Modifier = Modifier,
    /**
     * Who is signed in, and what to call them, as flows rather than plain values.
     *
     * The session arrives after the first frame on a cold launch, so a plain `UserID?`
     * would freeze `null` into every model and sheet built before it landed — the same
     * reason [ShellPreferences] exposes these as [StateFlow]s and the board treats the id
     * as a model key. A defaulted flow of `null` keeps preview and test callers honest:
     * no session, no "me" affordance.
     */
    currentUserID: StateFlow<UserID?> = MutableStateFlow(null),
    currentUserName: StateFlow<String?> = MutableStateFlow(null),
    isWide: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var navigation by remember(router) { mutableStateOf(router.state.value) }

    LaunchedEffect(router) {
        router.state.collect { navigation = it }
    }

    // Held here rather than inside the list destination, so opening the sheet survives the
    // list being replaced by a board behind it: the sheet is a modal over the *tab*, not over
    // one screen, and a state that lived in the list would take the sheet down with it.
    var isProjectEditorOpen by remember { mutableStateOf(false) }
    var projectEditorModel by remember(environment) { mutableStateOf<ProjectEditorModel?>(null) }
    // On a wide window the project editor is the right pane rather than a sheet: the
    // list stays in place beside it, matching the Backlog editor and the admin projects
    // section. Compact windows keep the sheet (below), unchanged.
//    if (isWide && isProjectEditorOpen && projectEditorModel != null && navigation.projectsPath.lastOrNull() == null) {
//        Box(
//            modifier = modifier
//                .fillMaxSize()
//                .testTag(ProjectsHostTag),
//        ) {
//            AdminTwoPanes(
//                listPane = {
//                    ProjectsListDestination(
//                        environment = environment,
//                        scope = scope,
//                        onOpenProject = { router.openProject(it, AppTab.Board) },
//                        onNewProject = {
//                            projectEditorModel = ProjectEditorModel(null, environment.repository, environment.dateProvider)
//                            isProjectEditorOpen = true
//                        },
//                        onEditProject = { id ->
//                            scope.launch {
//                                val project = environment.repository.project(id) ?: return@launch
//                                projectEditorModel = ProjectEditorModel(project, environment.repository, environment.dateProvider)
//                                isProjectEditorOpen = true
//                            }
//                        },
//                    )
//                },
//                detailPane = {
//                    projectEditorModel?.let { editor ->
//                        Box(
//                            modifier = Modifier.fillMaxSize(),
//                            contentAlignment = Alignment.Center,
//                        ) {
//                            NewProjectForm(
//                                name = editor.name,
//                                keyPrefix = editor.keyPrefix,
//                                details = editor.details,
//                                canSave = editor.canSave,
//                                isCreating = editor.isCreating,
//                                selectedMemberIDs = editor.memberIDs,
//                                onMemberIDsChanged = editor::onMemberIDsChanged,
//                                onNameChanged = editor::onNameChanged,
//                                onKeyPrefixChanged = editor::onKeyPrefixChanged,
//                                onDetailsChanged = editor::onDetailsChanged,
//                                searchMembers = { query ->
//                                    environment.teamPort?.searchMembers(query) ?: emptyList()
//                                },
//                                resolveSelected = { ids ->
//                                    environment.teamPort?.users(ids) ?: emptyList()
//                                },
//                                errorMessage = editor.errorMessage,
//                                onSave = {
//                                    scope.launch {
//                                        if (editor.save()) {
//                                            isProjectEditorOpen = false
//                                            projectEditorModel = null
//                                        }
//                                    }
//                                },
//                                onDismiss = {
//                                    isProjectEditorOpen = false
//                                    projectEditorModel = null
//                                },
//                                modifier = Modifier.fillMaxSize().padding(ttSpacing.lg),
//                            )
//                        }
//                    }
//                },
//            )
//        }
//        return
//    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProjectsHostTag),
    ) {
        // Exhaustive over [AppRoute] on purpose. A sixth case has to be a compile error
        // here, not a runtime fall-through — this is the same guarantee the sealed interface
        // was built to give.
        //
        // Only `Board` is a destination of *this* tab: `openProject` pushes nothing else, and
        // the routes that do belong to the Board, Backlog, Sprints and Archive tabs are not
        // pushed onto Projects by anything the shell does. The list is the tab's root, so an
        // unrecognised or not-yet-implemented case falls back to the root rather than to a
        // blank frame — visible, and honest about not being that screen.
        when (val route = navigation.projectsPath.lastOrNull()) {
            is AppRoute.Board -> ProjectBoardDestination(
                projectID = route.projectID,
                environment = environment,
                scope = scope,
                onSelectProject = { router.openProject(it, AppTab.Projects) },
                onBackToProjects = { router.popToRoot(AppTab.Projects) },
                // The board's model owns the answer (which sprint is running), so it rides as a
                // callback argument here — the same reason the board carries `our onFileAway` as
                // a function rather than a bare route: the host holding the closure cannot see
                // the model, and pushing a stale or absent sprint id from here would recreate
                // the same invisible-save the active-sprint wiring fixes.
                onAddTask = { status, sprintID ->
                    router.push(
                        AppRoute.WorkItemEditor(
                            projectID = route.projectID,
                            workItemID = null,
                            initialStatus = status,
                            initialSprintID = sprintID,
                        ),
                        AppTab.Projects,
                    )
                },
            )

            null,
            is AppRoute.Backlog,
            is AppRoute.Archive,
            is AppRoute.SprintList,
            is AppRoute.SprintDetail,
            is AppRoute.WorkItemEditor,
            AppRoute.Settings,
            // The admin sections are pushed onto the *Settings* stack and never onto this one,
            // so these two are unreachable here rather than unimplemented: `openAdminMembers`
            // and `openAdminProjects` write `settingsPath`. They are named rather than covered
            // by an `else` so that the next route added to the interface still fails to compile
            // until somebody decides which of these two answers it gets.
            is AppRoute.AdminMembers,
            is AppRoute.AdminProjects,
            -> ProjectsListDestination(
                environment = environment,
                scope = scope,
                onOpenProject = { router.openProject(it, AppTab.Board) },
                onNewProject = {
                    projectEditorModel = ProjectEditorModel(null, environment.repository, environment.dateProvider)
                    isProjectEditorOpen = true
                },
                onEditProject = { id ->
                    scope.launch {
                        val project = environment.repository.project(id) ?: return@launch
                        projectEditorModel = ProjectEditorModel(project, environment.repository, environment.dateProvider)
                        isProjectEditorOpen = true
                    }
                },
            )
        }

        projectEditorModel?.let { editor ->
            NewProjectSheet(
                name = editor.name,
                keyPrefix = editor.keyPrefix,
                details = editor.details,
                canSave = editor.canSave,
                isCreating = editor.isCreating,
                selectedMemberIDs = editor.memberIDs,
                onMemberIDsChanged = editor::onMemberIDsChanged,
                onNameChanged = editor::onNameChanged,
                onKeyPrefixChanged = editor::onKeyPrefixChanged,
                onDetailsChanged = editor::onDetailsChanged,
                // The project editor's picker is NOT scoped to the project's members — it is the control
                // that edits that membership, so scoping it would make every new member
                // unreachable. `assignableMembersFor` belongs to the pickers that consume
                // membership, not the one that writes it.
                searchMembers = { query ->
                    environment.teamPort?.searchMembers(query) ?: emptyList()
                },
                resolveSelected = { ids ->
                    environment.teamPort?.users(ids) ?: emptyList()
                },
                errorMessage = editor.errorMessage,
                onSave = {
                    scope.launch {
                        if (editor.save()) isProjectEditorOpen = false
                    }
                },
                onCancel = { isProjectEditorOpen = false },
                hasUnsavedChanges = editor.hasChanges,
                visible = isProjectEditorOpen,
                onDismissRequest = { isProjectEditorOpen = false },
                onDismissed = {
                    isProjectEditorOpen = false
                    projectEditorModel = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

    }
}

/**
 * The tab root: every unarchived project, and the actions that manage one.
 *
 * The model is `remember`ed against the environment and observed for as long as it is in the
 * composition, with [ObservingProjectModel.stopObserving] in `onDispose`. That pairing is the
 * contract the model's own KDoc spells out and the reason it is not left to the caller to
 * remember: a list that keeps observing after the board has replaced it re-reads the whole
 * store on every write in the feature, forever, with nothing on screen to show for it.
 */
@Composable
private fun ProjectsListDestination(
    environment: ProjectEnvironment,
    scope: CoroutineScope,
    onOpenProject: (ProjectID) -> Unit,
    onNewProject: () -> Unit,
    onEditProject: (ProjectID) -> Unit,
) {
    val model = remember(environment) {
        ProjectsListModel(environment.repository, environment.dateProvider)
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    ProjectsListScreen(
        rows = model.rows,
        notice = model.notice,
        isLoading = model.isLoading,
        errorMessage = model.errorMessage,
        onOpen = onOpenProject,
        onConfirmRemove = { projectID -> scope.launch { model.remove(projectID) } },
        // The screen keeps the pending removal itself and only calls this when the project
        // it was confirming has left the list. There is no state here to dismiss: the
        // confirmation is a local flag in the screen, not a dialog owned by this host.
        onDismissRemove = {},
        onDismissNotice = model::dismissNotice,
        onDismissError = model::dismissError,
        onNewProject = onNewProject,
        onEditProject = onEditProject,
    )
}

/**
 * One project's board.
 *
 * Keyed on the project id through `remember` and `DisposableEffect`, so opening a different
 * project builds a new model and *cancels the previous observation* rather than leaving two
 * boards watching the same store. That is the exact leak `ObservingProjectModel` documents,
 * and this is the place it would happen: a host that navigated between boards in place.
 */
@Composable
private fun ProjectBoardDestination(
    projectID: ProjectID,
    environment: ProjectEnvironment,
    scope: CoroutineScope,
    onSelectProject: (ProjectID) -> Unit,
    onBackToProjects: () -> Unit,
    /** The status, and the sprint a new task joins — the board model's active sprint or null. */
    onAddTask: (WorkItemStatus, SprintID?) -> Unit,
) {
    val model = remember(projectID, environment) {
        ProjectBoardModel(
            projectID = projectID,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
            // No session in this host, so the My Tasks filter honestly matches nothing —
            // the same `null` the board destination starts with on a cold launch.
            currentUserID = null,
        )
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }
    val picker = remember(environment) { ProjectPickerModel(environment.repository) }
    DisposableEffect(picker) {
        picker.observe(scope)
        onDispose { picker.stopObserving() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProjectBoardScreen(
            projection = model.projection,
            isLoading = model.isLoading,
            errorMessage = model.errorMessage,
            movingItemID = model.movingItemID,
            // Not wired, and the reason is scope: a work item's editor has no route in this
            // host yet. The board's *other* verbs are wired because they have destinations
            // that need none.
            onSelect = {},
            onFileAway = { itemID -> scope.launch { model.fileAway(itemID) } },
            onMove = { itemID, status -> scope.launch { model.move(itemID, status) } },
            onDismissError = model::dismissError,
            onOpenSwitcher = onBackToProjects,
            projects = picker.projects,
            onSelectProject = onSelectProject,
            onAddTask = { status -> onAddTask(status, model.activeSprint?.id) },
            showOnlyMyTasks = model.showOnlyMyTasks,
            onToggleMyTasks = model::toggleShowOnlyMyTasks,
            activeSprint = model.activeSprint,
            canAddTask = !model.addsLocked,
        )
    }
}

/** The whole projects area, so a test asserts on the host rather than on one of its screens. */
const val ProjectsHostTag = "shell.projects"
