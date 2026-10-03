package com.tasktracker.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassTabBar
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectspresentation.ProjectArchiveModel
import com.tasktracker.featureprojectspresentation.ProjectArchiveScreen
import com.tasktracker.featureprojectspresentation.ProjectBacklogModel
import com.tasktracker.featureprojectspresentation.ProjectBacklogScreen
import com.tasktracker.featureprojectspresentation.ProjectBoardModel
import com.tasktracker.featureprojectspresentation.ProjectBoardScreen
import com.tasktracker.featureprojectspresentation.ProjectEnvironment
import com.tasktracker.featureprojectspresentation.ProjectPickerModel
import com.tasktracker.featureprojectspresentation.ProjectPickerSheet
import com.tasktracker.featureprojectspresentation.WorkItemEditorModel
import com.tasktracker.featureprojectspresentation.WorkItemEditorSheet
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingspresentation.MissingSettingsServiceView
import com.tasktracker.featuresettingspresentation.SettingsModel
import com.tasktracker.featuresettingspresentation.SettingsScreen
import com.tasktracker.resources.Res
import com.tasktracker.resources.scoped_choose_project
import com.tasktracker.resources.scoped_no_project_body
import com.tasktracker.resources.scoped_no_project_title
import com.tasktracker.resources.tab_backlog
import com.tasktracker.resources.tab_board
import com.tasktracker.resources.tab_projects
import com.tasktracker.resources.tab_settings
import com.tasktracker.routing.AppRoute
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The signed-in shell: four tabs, each owning its own navigation stack.
 *
 * ## Why this reads the router by hand instead of wrapping a `NavHost`
 *
 * [Router] *is* the navigation state: one immutable [RouterState] behind a single
 * `StateFlow`, with no public setter and no way to change the root except through a session
 * transition. A `NavHost` would hold a second copy of that state, and a bridge writing back
 * is where the two would disagree - a back press, a deep link, or a session change applied
 * to one and not the other. So the stack is read, the top of it is rendered, and every
 * navigation goes back through the router.
 *
 * The **top** of a stack decides the active destination. The task editor is the exception to
 * replacement: it keeps the prior project screen as its backdrop and presents as a sheet.
 *
 * ## Board and Backlog are tabs, not pushes
 *
 * They are the surfaces a user switches between dozens of times a day, and both are scoped
 * to the shared active project rather than to a stack. That is why [ProjectScopedTabScreen]
 * takes a content lambda receiving a **non-optional** id: a scoped screen cannot be built
 * without a project, so "nothing is active" is a different composable that never constructs
 * one.
 *
 * ## Every project-scoped screen is keyed on the project id
 *
 * `remember(projectID, ...)` plus `DisposableEffect(model)` in each destination, so switching
 * projects mid-view builds a new model and *cancels the previous one*. This is the exact leak
 * `ObservingProjectModel` documents, and a shell that swapped boards in place is where it
 * happens: two models watching one store, one of them invisible, both re-reading on every
 * write forever.
 */
@Composable
fun NavigationHost(
    router: Router,
    environment: ProjectEnvironment,
    settings: SettingsPort?,
    preferences: ShellPreferences,
    themeScheme: ThemeScheme,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var navigation by remember(router) { mutableStateOf(router.state.value) }
    LaunchedEffect(router) { router.state.collect { navigation = it } }

    val tab = navigation.selectedTab

    // No `testTag` of this composable's own, deliberately. The caller supplies one — see
    // `SignedInShellTag` — and `testTag` *overwrites* rather than merges, so a tag here would
    // either lose to the caller's or quietly replace it, and which of the two happens depends on
    // modifier ordering rather than on anything readable. There was a `NavigationHostTag` here
    // that was therefore present in no semantics tree at all, and the only reason it was found
    // is that a test finally asked for it. The container is identified by the caller's tag.
    //
    // The bar is *overlaid*, not a second child of a `Column`. It used to be, which meant the
    // content box ended where the bar began and no list could ever put an item behind the glass —
    // a floating capsule with an opaque band of canvas under it reads as a band with a capsule on
    // top, not as glass. So the content takes the whole window and the bar is aligned over it,
    // and the one thing that has to be given back is reachability: `tabBarInset` below.
    //
    // That measurement is also the whole cost of the overlay. Anything drawn in the content box
    // now reaches the bottom of the screen, so a surface that used to be *above* the bar by
    // accident is now *under* it and the bar takes the touch. `TTBottomSheetScaffold` is the one
    // that bit: a sheet's action row sat on the bar and clicking Create dismissed the form
    // instead of submitting it, which is why the sheet reads this inset too.
    val tabBarInset = remember { mutableStateOf(0.dp) }
    // Read here rather than inside the `onSizeChanged` lambda, which is not a composable scope.
    val tabBarDensity = LocalDensity.current
    CompositionLocalProvider(LocalTabBarScrollUnderInset provides tabBarInset.value) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val path = navigation.pathFor(tab)
                val pushed = path.lastOrNull()
                val previousRoute = path.dropLast(1).lastOrNull()
                val editorOverProjectRoot = pushed is AppRoute.WorkItemEditor &&
                    previousRoute == null && (tab == AppTab.Board || tab == AppTab.Backlog)

                // Keep the root in this same composition slot while a task editor is shown over
                // a project tab. Routing into a separate PushedRoute branch used to dispose and
                // rebuild BacklogDestination, replaying its repository load and flashing the list.
                if (pushed == null || editorOverProjectRoot) {
                    TabRoot(
                        tab = tab,
                        router = router,
                        environment = environment,
                        settings = settings,
                        preferences = preferences,
                        themeScheme = themeScheme,
                        scope = scope,
                    )
                }

                if (editorOverProjectRoot) {
                    val editorRoute = pushed
                    WorkItemEditorDestination(
                        projectID = editorRoute.projectID,
                        workItemID = editorRoute.workItemID,
                        initialStatus = editorRoute.initialStatus,
                        repository = environment.repository,
                        dateProvider = environment.dateProvider,
                        scope = scope,
                        onDismiss = {
                            val currentPath = router.path(tab)
                            if (currentPath.lastOrNull() == editorRoute) {
                                router.setPath(currentPath.dropLast(1), tab)
                            }
                        },
                    )
                } else if (pushed != null) {
                    PushedRoute(
                        route = pushed,
                        tab = tab,
                        previousRoute = previousRoute,
                        router = router,
                        environment = environment,
                        settings = settings,
                        preferences = preferences,
                        themeScheme = themeScheme,
                        scope = scope,
                    )
                }
            }

            TabBar(
                selected = tab,
                onSelect = router::selectTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size: IntSize ->
                        tabBarInset.value = with(tabBarDensity) { size.height.toDp() }
                    },
            )
        }
    }
}
/** The tab's own root, with nothing pushed onto it. Exhaustive over [AppTab]. */
@Composable
private fun TabRoot(
    tab: AppTab,
    router: Router,
    environment: ProjectEnvironment,
    settings: SettingsPort?,
    preferences: ShellPreferences,
    themeScheme: ThemeScheme,
    scope: CoroutineScope,
) {
    when (tab) {
        AppTab.Projects -> ProjectsHost(router = router, environment = environment)

        AppTab.Board -> ProjectScopedTabScreen(
            router = router,
            repository = environment.repository,
            scope = scope,
        ) { projectID ->
            BoardDestination(
                projectID = projectID,
                router = router,
                environment = environment,
                scope = scope,
            )
        }

        AppTab.Backlog -> ProjectScopedTabScreen(
            router = router,
            repository = environment.repository,
            scope = scope,
        ) { projectID ->
            BacklogDestination(
                projectID = projectID,
                router = router,
                environment = environment,
                scope = scope,
            )
        }

        // The gate replaces *this tab* and not the signed-in area. Projects, Board and Backlog
        // all work without a settings port, so breaking three screens to explain a fourth
        // would be a worse answer than one sentence on one tab.
        AppTab.Settings -> if (settings == null) {
            MissingSettingsServiceView()
        } else {
            SettingsDestination(
                port = settings,
                preferences = preferences,
                themeScheme = themeScheme,
                scope = scope,
            )
        }
    }
}

/**
 * A pushed destination.
 *
 * Exhaustive over [AppRoute] with no `else`, deliberately: a sixth route has to be a compile
 * error here rather than a runtime fall-through. The sealed interface exists to give exactly
 * that, and this switch is where it pays off.
 */
@Composable
private fun PushedRoute(
    route: AppRoute,
    tab: AppTab,
    previousRoute: AppRoute?,
    router: Router,
    environment: ProjectEnvironment,
    settings: SettingsPort?,
    preferences: ShellPreferences,
    themeScheme: ThemeScheme,
    scope: CoroutineScope,
) {
    when (route) {
        // The Board as a destination in its own right, reachable through a deep link and
        // through `openProject` on the Projects tab.
        is AppRoute.Board -> BoardDestination(
            projectID = route.projectID,
            router = router,
            environment = environment,
            scope = scope,
        )

        is AppRoute.Backlog -> BacklogDestination(
            projectID = route.projectID,
            router = router,
            environment = environment,
            scope = scope,
        )

        is AppRoute.Archive -> ArchiveDestination(
            projectID = route.projectID,
            router = router,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
            scope = scope,
        )

        is AppRoute.WorkItemEditor -> Box(modifier = Modifier.fillMaxSize()) {
            val backgroundRoute = previousRoute ?: when (tab) {
                AppTab.Board -> AppRoute.Board(route.projectID)
                AppTab.Backlog -> AppRoute.Backlog(route.projectID)
                else -> AppRoute.Board(route.projectID)
            }
            when (backgroundRoute) {
                is AppRoute.Board -> BoardDestination(
                    projectID = backgroundRoute.projectID,
                    router = router,
                    environment = environment,
                    scope = scope,
                )

                is AppRoute.Backlog -> BacklogDestination(
                    projectID = backgroundRoute.projectID,
                    router = router,
                    environment = environment,
                    scope = scope,
                )

                is AppRoute.Archive -> ArchiveDestination(
                    projectID = backgroundRoute.projectID,
                    router = router,
                    repository = environment.repository,
                    dateProvider = environment.dateProvider,
                    scope = scope,
                )

                is AppRoute.WorkItemEditor,
                AppRoute.Settings,
                -> BoardDestination(
                    projectID = route.projectID,
                    router = router,
                    environment = environment,
                    scope = scope,
                )
            }
            WorkItemEditorDestination(
                projectID = route.projectID,
                workItemID = route.workItemID,
                initialStatus = route.initialStatus,
                repository = environment.repository,
                dateProvider = environment.dateProvider,
                scope = scope,
                onDismiss = {
                    val path = router.path(tab)
                    if (path.lastOrNull() == route) router.setPath(path.dropLast(1), tab)
                },
            )
        }

        // The Settings tab root *is* the settings screen, so pushing `AppRoute.Settings` onto
        // the Settings stack renders the same screen again. That is kept on purpose: a deep
        // link to `tasktracker://settings` must land on settings, and a separate "pushed
        // settings" screen would give that URL two answers depending on how it got there.
        AppRoute.Settings -> if (settings == null) {
            MissingSettingsServiceView()
        } else {
            SettingsDestination(
                port = settings,
                preferences = preferences,
                themeScheme = themeScheme,
                scope = scope,
            )
        }
    }
}

/**
 * The root of a project-scoped tab: the active project's screen, or a picker prompt when none
 * is active.
 *
 * One composable serves Board and Backlog so the "no project yet" state, the picker, and the
 * tracking of which project is on screen are written once. [content] receives the active id
 * *only* after one has been chosen, which is what lets the scoped screens keep taking a
 * non-optional `ProjectID`.
 *
 * Sample data is seeded here as well as on the dashboard, because a user can land on this tab
 * first and an empty store makes both scoped screens look broken. The seed is idempotent by
 * the repository's own contract: it does nothing once anything exists.
 */
@Composable
private fun ProjectScopedTabScreen(
    router: Router,
    repository: ProjectRepositoryPort,
    scope: CoroutineScope,
    content: @Composable (ProjectID) -> Unit,
) {
    var isPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        // Best effort: seeding is a convenience for an empty demo store, so a failure here
        // must not take the screen down. Deliberately not gated on the picker being open -
        // the empty state is not something to wait for.
        runCatching { repository.seedSampleDataIfNeeded() }
    }

    // Read the active id from Compose-observed router state. Reading Router.activeProjectID
    // directly is only a snapshot; with stable parameters Compose may skip this destination
    // when the selected project changes, leaving the prior project's model on screen.
    val routerState by router.state.collectAsState()
    val activeProjectID = routerState.activeProjectID

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeProjectID == null) {
            ScopedEmptyState(
                onChooseProject = { isPickerOpen = true },
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            content(activeProjectID)
        }
    }

    if (isPickerOpen) {
        ProjectPicker(
            repository = repository,
            selectedProjectID = activeProjectID,
            // `openProject` rather than a direct assignment, because choosing a project is a
            // navigation decision with per-tab rules - it pushes on Projects, switches on
            // Board and Backlog, and is rejected on Settings - and a picker that wrote the
            // active id itself would bypass every one of them.
            onSelect = { projectID ->
                if (projectID != null) router.openProject(projectID)
                isPickerOpen = false
            },
            scope = scope,
        )
    }
}

/**
 * "No project selected" for a scoped tab.
 *
 * Written here rather than reusing the feature's `ProjectsEmptyState`, which is `internal` and
 * means something different anyway: "there are no projects at all" versus "there are projects,
 * none chosen here". Reusing one copy for both would make a user with nine projects and none
 * selected read as though their account were empty.
 */
@Composable
private fun ScopedEmptyState(
    onChooseProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(ttSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ttText(
            text = stringResource(Res.string.scoped_no_project_title),
            style = ttTypography.callout,
            color = ttLabel(),
            textAlign = TextAlign.Center,
        )
        ttText(
            text = stringResource(Res.string.scoped_no_project_body),
            style = ttTypography.body,
            color = ttLabelSecondary(),
            textAlign = TextAlign.Center,
        )
        ttGlassButton(
            label = stringResource(Res.string.scoped_choose_project),
            onClick = onChooseProject,
            // The accent left to the button: `TTGlassButtonStyle.prominent()` is the design
            // system's factory, but it is `internal` there, and this module is not the design
            // system. Naming the tint explicitly is the same style expressed without reaching
            // across a visibility boundary.
            style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
            modifier = Modifier.testTag(ScopedChooseProjectTag),
        )
    }
}

/** The project picker over the signed-in area, observed for as long as it is showing. */
@Composable
private fun ProjectPicker(
    repository: ProjectRepositoryPort,
    selectedProjectID: ProjectID?,
    onSelect: (ProjectID?) -> Unit,
    scope: CoroutineScope,
) {
    val model = remember(repository) { ProjectPickerModel(repository) }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    // The model's own choice is recorded on every selection, including the "all projects"
    // row, so the sheet knows what to mark without the shell keeping a second copy.
    val onPick: (ProjectID?) -> Unit = { projectID ->
        model.select(projectID)
        onSelect(projectID)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                // No indication: this is a scrim, not a control, and a ripple that reads as a
                // tap target under the sheet is a hint that something else is clickable there.
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onSelect(null) },
            )
            .testTag(ProjectPickerScrimTag),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ProjectPickerSheet(
            projects = model.projects,
            selectedProjectID = model.selectedProjectID ?: selectedProjectID,
            onSelect = onPick,
            modifier = Modifier.padding(
                top = ttSpacing.md,
                start = ttSpacing.md,
                end = ttSpacing.md,
                bottom = ttSpacing.md + LocalTabBarScrollUnderInset.current,
            ),
        )
    }
}

/** One project's board. */
@Composable
private fun BoardDestination(
    projectID: ProjectID,
    router: Router,
    environment: ProjectEnvironment,
    scope: CoroutineScope,
) {
    val model = remember(projectID, environment) {
        ProjectBoardModel(projectID, environment.repository, environment.dateProvider)
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }
    val picker = remember(environment.repository) { ProjectPickerModel(environment.repository) }
    DisposableEffect(picker) {
        picker.observe(scope)
        onDispose { picker.stopObserving() }
    }

    ProjectBoardScreen(
        projection = model.projection,
        isLoading = model.isLoading,
        errorMessage = model.errorMessage,
        movingItemID = model.movingItemID,
        onSelect = { itemID ->
            router.push(AppRoute.WorkItemEditor(projectID = projectID, workItemID = itemID))
        },
        onFileAway = { itemID -> scope.launch { model.fileAway(itemID) } },
        onMove = { itemID, status -> scope.launch { model.move(itemID, status) } },
        onDismissError = model::dismissError,
        onAddTask = { status ->
            router.push(
                AppRoute.WorkItemEditor(
                    projectID = projectID,
                    workItemID = null,
                    initialStatus = status,
                ),
            )
        },
        // Leaving the board is `popToRoot`, which on the Board tab is a no-op - the tab's root
        // *is* the board, and there is nothing to pop to. Kept wired on the deep-link path,
        // where `AppRoute.Board` can sit on a stack that does have something below it.
        onOpenSwitcher = { router.popToRoot() },
        projects = picker.projects,
        onSelectProject = { router.openProject(it, AppTab.Board) },
    )
}

/** One project's backlog. */
@Composable
private fun BacklogDestination(
    projectID: ProjectID,
    router: Router,
    environment: ProjectEnvironment,
    scope: CoroutineScope,
) {
    val model = remember(projectID, environment) {
        ProjectBacklogModel(projectID, environment.repository, environment.dateProvider)
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }
    val picker = remember(environment.repository) { ProjectPickerModel(environment.repository) }
    DisposableEffect(picker) {
        picker.observe(scope)
        onDispose { picker.stopObserving() }
    }

    ProjectBacklogScreen(
        projection = model.projection,
        isLoading = model.isLoading,
        errorMessage = model.errorMessage,
        busyItemID = model.busyItemID,
        onSelect = { itemID ->
            router.push(AppRoute.WorkItemEditor(projectID = projectID, workItemID = itemID))
        },
        onPutOnBoard = { itemID -> scope.launch { model.putOnBoard(itemID) } },
        onFileAway = { itemID -> scope.launch { model.fileAway(itemID) } },
        onDismissError = model::dismissError,
        onOpenSwitcher = { router.popToRoot() },
        projects = picker.projects,
        onSelectProject = { router.openProject(it, AppTab.Backlog) },
        onAddTask = {
            router.push(
                AppRoute.WorkItemEditor(
                    projectID = projectID,
                    workItemID = null,
                    initialStatus = WorkItemStatus.Backlog,
                ),
                AppTab.Backlog,
            )
        },
    )
}

/** One project's archive. Reachable by pushing, so `onOpenSwitcher` is how it is left. */
@Composable
private fun ArchiveDestination(
    projectID: ProjectID,
    router: Router,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    scope: CoroutineScope,
) {
    val model = remember(projectID, repository, dateProvider) {
        ProjectArchiveModel(projectID, repository, dateProvider)
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    ProjectArchiveScreen(
        projection = model.projection,
        isLoading = model.isLoading,
        errorMessage = model.errorMessage,
        onSelect = { itemID ->
            router.push(AppRoute.WorkItemEditor(projectID = projectID, workItemID = itemID))
        },
        onDismissError = model::dismissError,
        onOpenSwitcher = { router.popToRoot() },
    )
}

/**
 * The work item editor, over whatever opened it.
 *
 * One route covers create and edit, because `AppRoute.WorkItemEditor.workItemID` is nullable
 * and the deep-link grammar's `workitem/<pid>/new` needs no second route.
 *
 * The existing item is read **once**, here, before the model is built, because the model reads
 * it exactly once in its own constructor and treats the result as the change snapshot. Deferring
 * the read into a `LaunchedEffect` would build the model first and hand it nothing.
 */
@Composable
private fun WorkItemEditorDestination(
    projectID: ProjectID,
    workItemID: WorkItemID?,
    initialStatus: WorkItemStatus?,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    // A suspend read, so it cannot be a `remember` block: the model has to be built *after*
    // the item is in hand, and building it earlier would hand it a null and turn an edit into
    // a create. `null` as the initial value is therefore "not read yet", not "no item" — which
    // is why the loaded answer is wrapped rather than left bare.
    val loaded by produceState<LoadedWorkItem?>(initialValue = null, projectID, workItemID, repository) {
        // A create link pays for no lookup, and a lookup that fails becomes a blank editor
        // rather than a crash: the editor's own error banner is for saves, so failing here
        // would leave a form with nothing to report on.
        value = LoadedWorkItem(
            workItemID?.let { id -> runCatching { repository.workItem(id) }.getOrNull() },
        )
    }

    // Nothing rendered until the read answers. A one-frame gap rather than a spinner, because
    // the only work involved is a local store read on a route the user just navigated to.
    val existing = loaded ?: return

    val model = remember(projectID, workItemID, repository, existing, initialStatus) {
        WorkItemEditorModel(projectID, existing.item, repository, dateProvider, initialStatus)
    }

    // Tracked here rather than on the model because the model has no in-flight flag, and an
    // editor without one answers a double tap with two writes. Named rather than defaulted at
    // the call site so the omission is visible: adding a flag is a model change.
    var isBusy by remember(model) { mutableStateOf(false) }
    var isSheetVisible by remember(projectID, workItemID, initialStatus) { mutableStateOf(true) }

    WorkItemEditorSheet(
        title = model.title,
            details = model.details,
            status = model.status,
            priority = model.priority,
            assignee = model.assignee,
            hasDueDate = model.dueDate != null,
            isCreating = model.isCreating,
            issuedKey = model.issuedKey,
            selectableStatuses = if (workItemID == null && initialStatus == WorkItemStatus.Backlog) {
                emptyList()
            } else {
                model.selectableStatuses
            },
            isFiledAway = model.isFiledAway,
            canSave = model.canSave,
            isBusy = isBusy,
            errorMessage = model.errorMessage,
            onTitleChanged = model::onTitleChanged,
            onDetailsChanged = model::onDetailsChanged,
            onStatusChanged = model::onStatusChanged,
            onPriorityChanged = model::onPriorityChanged,
            onAssigneeChanged = model::onAssigneeChanged,
            // The toggle only has an on/off shape, so the model needs an instant and the clock
            // is the only honest source for one. `now()` is suspend, hence the launch.
            onToggleDueDate = { on ->
                scope.launch {
                    model.onDueDateChanged(if (on) dateProvider.now() else null)
                }
            },
            onFileAway = { scope.launch { model.fileAway() } },
            // The route stays mounted through the sheet's exit transition, and a failed save
            // leaves the draft visible with its error banner.
            onSave = {
                scope.launch {
                    isBusy = true
                    val outcome = model.save()
                    isBusy = false
                    if (outcome != null) isSheetVisible = false
                }
            },
            onCancel = { isSheetVisible = false },
            onDismissError = model::dismissError,
            visible = isSheetVisible,
            onDismissRequest = { if (!isBusy) isSheetVisible = false },
            onDismissed = onDismiss,
        modifier = Modifier.fillMaxSize().testTag(WorkItemEditorDestinationTag),
    )
}

/**
 * The Settings tab.
 *
 * The model is remembered against the port and observed with [DisposableEffect], so leaving
 * the tab stops its two stream collectors rather than leaving them answering connectivity
 * questions for a screen nobody can see.
 *
 * The shell-owned concerns - appearance and the forced-offline intent - are routed through
 * [ShellPreferences] rather than held here. That is what makes a theme change apply to the
 * *whole app* on the next frame instead of only to this tab: the screen reports the choice and
 * the shell owns it.
 */
@Composable
private fun SettingsDestination(
    port: SettingsPort,
    preferences: ShellPreferences,
    themeScheme: ThemeScheme,
    scope: CoroutineScope,
) {
    val model = remember(port) { SettingsModel(port) }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    // The remembered intent is applied to the model *without* dispatching it, which is the
    // entire reason `restoreForceOffline` exists: the monitor is already overridden, and this
    // only stops a rebuilt model from rendering the switch off while the app is offline.
    val forceOffline by preferences.forceOffline.collectAsState()
    val accentPalette by preferences.accentPalette.collectAsState()
    val glassIntensity by preferences.glassIntensity.collectAsState()
    LaunchedEffect(model, forceOffline) { model.restoreForceOffline(forceOffline) }

    SettingsScreen(
        connectivity = model.connectivity,
        activity = model.activity,
        isForceOffline = model.isForceOffline,
        themeScheme = themeScheme,
        accentPalette = accentPalette,
        version = model.version,
        commandOutcome = model.commandOutcome,
        onThemeChange = { scheme -> scope.launch { preferences.setThemeScheme(scheme) } },
        onAccentPaletteChange = { palette ->
            scope.launch { preferences.setAccentPalette(palette) }
        },
        glassIntensity = glassIntensity,
        // Preview is not a coroutine: it only touches composition state, and a drag fires it
        // every frame. The commit is the one that writes, and it waits for the finger to lift.
        onGlassIntensityPreview = { intensity -> preferences.previewGlassIntensity(intensity) },
        onGlassIntensityCommit = { intensity ->
            scope.launch { preferences.commitGlassIntensity(intensity) }
        },
        // Both halves, and neither alone: the model drives the switch, and the remembered
        // intent is what the next rebuilt model reads. Recording only one of them is how a
        // switch ends up disagreeing with the thing it controls.
        onForceOfflineChange = { forced ->
            scope.launch {
                preferences.setForceOffline(forced)
                model.applyForceOffline(forced)
            }
        },
        onSyncNow = { scope.launch { model.requestSync() } },
        // `signOut` goes through the port rather than to an auth API: the screen is not allowed
        // to know what a session is. The graph binds that call to the session coordinator, so
        // this is the tap on the control that did not exist when the header was removed.
        onSignOut = { scope.launch { model.signOut() } },
    )
}

/**
 * The four tabs, as a floating glass capsule above the bottom edge.
 *
 * ## Why the bar floats rather than reaching the bottom of the screen
 *
 * This is the change that matches iOS 26's `TabView`, and it inverts the two decisions the
 * previous version of this file argued for at length. The bar is inset from the screen edges
 * by [ttSpacing.tabBarEdgeInset] and carries a drop shadow, so it has a silhouette and
 * something to cast it onto.
 *
 * The safe-area inset moves with it, from inside the material to outside — onto this wrapper,
 * above the glass. The old reasoning ("the native material runs under the home indicator so the
 * indicator looks drawn *on* the bar") was right for a full-bleed bar and is simply not
 * applicable to a floating one: a capsule that ran under the home indicator would have the
 * indicator drawn across its lower third, which is not floating, it is being sat on. The inset
 * belongs outside for the same reason it always has been claimed by the tab bar rather than the
 * root — the root deliberately leaves the bottom inset alone for scrolling content, and this is
 * not scrolling content.
 *
 * ## Why selection is a lens and not a filled pill
 *
 * The middle version of this bar filled the selected tab with the accent and re-coloured its
 * label to [ttOnAccent]. That is [TTGlassSegmentedPicker]'s arrangement, and it is wrong here for
 * a reason that is about navigation rather than about contrast: the selected tab's accent glyph
 * and label are *what the user is navigating by*. Filling the tab with the accent means the
 * glyph's colour now has to be re-learned at every tab, and the bar stops reading as four
 * destinations one of which is lit.
 *
 * So the selection is a neutral white lens drawn *under* the icon and label, sliding between the
 * segments and leaning into the direction of travel as it goes, and the accent keeps doing the
 * identifying. The lens's body is capped very low ([ttGlass.TAB_LENS_ALPHA]) for a second
 * reason on top of that: it sits under accent-coloured text, and every increment of white in
 * it spends that text's contrast margin.
 *
 * ## Why the bar itself lives in the design system
 *
 * [TTGlassTabBar] owns the capsule, the lens, the travel animation, and the per-tab tint and
 * glyph scaling. What is left here is the four [AppTab]s, their labels, and their glyphs — the
 * part that is about *this* app. The earlier split, where the geometry and the animation lived
 * in this file and the tokens lived in the design system, meant the lens's geometry had to be
 * re-derived by anything else that wanted a tab bar, and there is only ever going to be one tab
 * bar, so that is a split that buys nothing.
 */
@Composable
private fun TabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = AppTab.entries
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Inset and safe area outside the glass, not inside it. See this function's KDoc:
            // on a floating capsule the two do opposite jobs, and only this one floats.
            // Keep the capsule above the system navigation area when the keyboard is closed;
            // when the IME is open, let the keyboard cover its bottom area.
            .then(if (keyboardVisible) Modifier else Modifier.navigationBarsPadding())
            .padding(horizontal = ttSpacing.tabBarEdgeInset)
            // The tag is on the wrapper rather than on the bar, so that it measures the capsule
            // itself. Were it on the bar, the test would measure the same box either way and
            // would not be able to tell a floating bar from an edge-anchored one.
            .testTag(TabBarTag),
    ) {
        TTGlassTabBar(
            tabs = tabs,
            selectedIndex = tabs.indexOf(selected),
            onSelect = onSelect,
            title = { tabTitle(it) },
            icon = { tabIcon(it) },
            tabModifier = { Modifier.testTag(TabButtonTag(it)) },
        )
    }
}

/**
 * A work item that has been read, wrapped so "not read yet" is distinguishable from "no such
 * item". The two mean opposite things to the editor: one waits, the other opens a blank form.
 */
private class LoadedWorkItem(val item: WorkItem?)

/** One tab's label. Exhaustive over [AppTab], so a fifth tab is a compile error here. */
@Composable
private fun tabTitle(tab: AppTab): String = when (tab) {
    AppTab.Projects -> stringResource(Res.string.tab_projects)
    AppTab.Board -> stringResource(Res.string.tab_board)
    AppTab.Backlog -> stringResource(Res.string.tab_backlog)
    AppTab.Settings -> stringResource(Res.string.tab_settings)
}

/**
 * One tab's glyph, beside [tabTitle] rather than inside the design system.
 *
 * The drawings live in `ttTabIcons` because they are visual assets; *which* tab gets which one
 * is the shell's decision, and it is a `when` over [AppTab] for the same reason [tabTitle] is
 * one — a fifth tab becomes a compile error here rather than a blank square.
 *
 * The mapping matches the native app's SF Symbols one for one, and `ttTabIcons.mapping` records
 * that correspondence so the two cannot drift apart without a test noticing.
 */
private fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.Projects -> ttTabIcons.projects
    AppTab.Board -> ttTabIcons.board
    AppTab.Backlog -> ttTabIcons.backlog
    AppTab.Settings -> ttTabIcons.settings
}

// MARK: - Tags

/** The tab bar as a whole. */
const val TabBarTag = "shell.tabs"

/** The "choose a project" button on a scoped tab with nothing active. */
const val ScopedChooseProjectTag = "shell.scoped.chooseProject"

/** The picker's scrim. Tapping it dismisses the sheet. */
const val ProjectPickerScrimTag = "shell.picker.scrim"

/** The editor's container, so a test can assert the route is being rendered. */
const val WorkItemEditorDestinationTag = "shell.workItemEditor"

/** One tab's button, so a test taps a tab by name rather than by position. */
fun TabButtonTag(tab: AppTab): String = "shell.tab.${tab.wireValue}"
