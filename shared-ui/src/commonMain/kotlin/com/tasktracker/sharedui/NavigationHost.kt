@file:OptIn(ExperimentalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package com.tasktracker.sharedui

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
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
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectspresentation.ProjectArchiveModel
import com.tasktracker.featureprojectspresentation.ProjectArchiveScreen
import com.tasktracker.featureprojectspresentation.ProjectBacklogModel
import com.tasktracker.featureprojectspresentation.SprintDetailModel
import com.tasktracker.featureprojectspresentation.SprintDetailScreen
import com.tasktracker.featureprojectspresentation.SprintEditorModel
import com.tasktracker.featureprojectspresentation.SprintEditorSheet
import com.tasktracker.featureprojectspresentation.SprintListModel
import com.tasktracker.featureprojectspresentation.SprintListScreen
import com.tasktracker.featureprojectspresentation.SprintSummaryScreen
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
import com.tasktracker.resources.tab_sprints
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
    // `BackHandler` needs a `NavigationEventDispatcherOwner` in this composition
    // local. The Android host supplies one as the local's *host default* — the
    // activity's own dispatcher, so the shell's handler rides the real system-back
    // chain with no further wiring. The JVM and iOS hosts supply none, and the
    // handler throws without one, so a root dispatcher is installed there: with no
    // `NavigationEventInput` attached it is detached and receives no events, which
    // is exactly right for a host with no system back. The system-back tests drive
    // that dispatcher with a synthetic input.
    val hostDispatcherOwner = LocalNavigationEventDispatcherOwner.current
    val fallbackDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
    CompositionLocalProvider(
        LocalTabBarScrollUnderInset provides tabBarInset.value,
        LocalNavigationEventDispatcherOwner provides (hostDispatcherOwner ?: fallbackDispatcherOwner),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val path = navigation.pathFor(tab)
                val pushed = path.lastOrNull()
                val previousRoute = path.dropLast(1).lastOrNull()
                val editorOverProjectRoot = pushed is AppRoute.WorkItemEditor &&
                    previousRoute == null && (tab == AppTab.Board || tab == AppTab.Backlog)

                // System back mirrors the chevron every pushed screen carries: pop
                // exactly one route, never leave the tab. The gate is `path.size > 1`,
                // not `pushed != null` — the Sprints tab's *root* stack is
                // `[SprintList]`, so `pushed` is non-null even at the tab root and
                // would wrongly enable the handler there and eat the OS default (on
                // Android, finishing the activity) as a silent no-op. At the root the
                // handler stays disabled and the OS default governs. The lambda
                // re-reads `router.path(tab)` rather than closing over `path`, the
                // same guard the editor's `onDismiss` below uses: the lambda runs
                // after the gesture, on a stack that may have moved.
                BackHandler(
                    enabled = path.size > 1,
                    onBack = {
                        val currentPath = router.path(tab)
                        if (currentPath.size > 1) {
                            router.setPath(currentPath.dropLast(1), tab)
                        }
                    },
                )

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
                        initialSprintID = editorRoute.initialSprintID,
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
                preferences = preferences,
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
        // Sprints is project-scoped like Board and Backlog, and for the same reason: it shows one
        // project's work, so a project switcher here is navigation rather than an extra
        // affordance. `seedSampleDataIfNeeded` comes from `ProjectScopedTabScreen`, so
        // landing on this tab first does not show an empty store.
        AppTab.Sprints -> ProjectScopedTabScreen(
            router = router,
            repository = environment.repository,
            scope = scope,
        ) { projectID ->
            SprintsDestination(
                projectID = projectID,
                router = router,
                environment = environment,
                scope = scope,
                preferences = preferences,
            )
        }

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
            preferences = preferences,
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

        is AppRoute.SprintList -> SprintsDestination(
            projectID = route.projectID,
            router = router,
            environment = environment,
            scope = scope,
            preferences = preferences,
        )

        is AppRoute.SprintDetail -> SprintDetailDestination(
            projectID = route.projectID,
            sprintID = route.sprintID,
            router = router,
            environment = environment,
            scope = scope,
            preferences = preferences,
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
                    preferences = preferences,
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
                is AppRoute.SprintList,
                is AppRoute.SprintDetail,
                AppRoute.Settings,
                -> BoardDestination(
                    projectID = route.projectID,
                    router = router,
                    environment = environment,
                    scope = scope,
                    preferences = preferences,
                )
            }
            WorkItemEditorDestination(
                projectID = route.projectID,
                workItemID = route.workItemID,
                initialStatus = route.initialStatus,
                initialSprintID = route.initialSprintID,
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

    // Composed unconditionally and hidden with `visible`, which is how the project and task
    // editors are hosted. Gating on `if (isPickerOpen)` would unmount the sheet the instant
    // the flag flipped and take its exit transition with it.
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
        visible = isPickerOpen,
    )
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
    visible: Boolean,
) {
    val model = remember(repository) { ProjectPickerModel(repository) }
    // Keyed on `visible` as well as the model. The sheet has to stay composed through its exit
    // transition or the slide is cut off mid-flight, and this used to be handled by an
    // `if (isPickerOpen)` gate that unmounted the whole subtree — which is why the picker had
    // no transition at all while the two editors next to it did. Keeping it composed costs a
    // live model, so observation is scoped to the window in which the sheet is on screen.
    DisposableEffect(model, visible) {
        if (!visible) return@DisposableEffect onDispose { }
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    // The model's own choice is recorded on every selection, including the "all projects"
    // row, so the sheet knows what to mark without the shell keeping a second copy.
    val onPick: (ProjectID?) -> Unit = { projectID ->
        model.select(projectID)
        onSelect(projectID)
    }

    // No scrim and no padding here any more: the scaffold owns the frame, the outside-tap
    // handling and the insets. The tab bar's scroll-under inset in particular was being
    // applied by hand below, from a copy of the scaffold's own reasoning, which is exactly the
    // kind of second spelling that goes stale.
    ProjectPickerSheet(
        projects = model.projects,
        selectedProjectID = model.selectedProjectID ?: selectedProjectID,
        onSelect = onPick,
        visible = visible,
        // A scrim tap means "no project selected" — the same thing the hand-rolled scrim did,
        // and the reason this sheet needs no OK button to dismiss it.
        onDismissRequest = { onSelect(null) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** One project's board. */
@Composable
private fun BoardDestination(
    projectID: ProjectID,
    router: Router,
    environment: ProjectEnvironment,
    scope: CoroutineScope,
    preferences: ShellPreferences,
) {
    val currentUserName by preferences.currentUserName.collectAsState()
    // `currentUserName` is a `remember` key on purpose. The board's filter freezes the name into
    // the model, so a name that arrived after this screen was composed has to build a new model
    // rather than leave the first one filtering on a stale or absent name — the same
    // rebuild-and-cancel-the-previous-observation `ProjectBoardDestination` documents, and the
    // `DisposableEffect` below cancels the old observation, so this cannot leave two boards
    // watching the store. On a cold launch this does build the model twice: once with `null`,
    // then again once the session is known.
    val model = remember(projectID, environment, preferences, currentUserName) {
        ProjectBoardModel(
            projectID = projectID,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
            currentUserName = currentUserName,
        )
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
                    // The board shows the active sprint's items only, so a task added from it
                    // joins that sprint — otherwise it saves into the store and never appears:
                    // the sprint filter silently drops it the frame after the save. `null` is
                    // correct only when no sprint is running, which is what the model holds.
                    initialSprintID = model.activeSprint?.id,
                ),
            )
        },
        // Leaving the board is `popToRoot`, which on the Board tab is a no-op - the tab's root
        // *is* the board, and there is nothing to pop to. Kept wired on the deep-link path,
        // where `AppRoute.Board` can sit on a stack that does have something below it.
        onOpenSwitcher = { router.popToRoot() },
        projects = picker.projects,
        onSelectProject = { router.openProject(it, AppTab.Board) },
        showOnlyMyTasks = model.showOnlyMyTasks,
        onToggleMyTasks = model::toggleShowOnlyMyTasks,
        // 24.2: the Board tab was the one board host that dropped the sprint, so its header
        // never named a running sprint. It must, for the title to say which sprint is active.
        activeSprint = model.activeSprint,
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
 * One project's sprints, and the sheet that creates or edits one.
 *
 * ## The editor is a sheet over the list, not a route
 *
 * Creating a sprint takes five fields and leaves the user looking at the list afterwards —
 * the same shape as [NewProjectSheet] over the board, and the same reason. Pushing a route
 * would mean a navigation round trip for something that does not need one, and — worse — a
 * *cancel* would have to pop a stack, which is a second thing to get wrong.
 *
 * So the sheet's visibility is local state here, exactly as [WorkItemEditorDestination]'s is
 * for the task form.
 *
 * ## The editor's existing sprint is read before the model is built
 *
 * `SprintEditorModel` reads its `existing` sprint exactly once, in its constructor, and uses
 * the result as the change snapshot. Building the model before the read answers would hand it
 * a `null` and turn every edit into a create — the same trap
 * [WorkItemEditorDestination] documents for the task form, and the same `produceState` shape
 * that avoids it.
 *
 * ## The list's own "can I create one" answer gates the button, not the store
 *
 * `hasOpenSprint` comes from the same read the rows came from, so the create affordance is
 * absent exactly when the store would refuse. The store's refusal is still there for the
 * race, and it arrives as the usual banner.
 */
@Composable
private fun SprintsDestination(
    projectID: ProjectID,
    router: Router,
    environment: ProjectEnvironment,
    scope: CoroutineScope,
    preferences: ShellPreferences,
) {
    val model = remember(projectID, environment) {
        SprintListModel(projectID, environment.repository)
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    // The switcher reads the project list so the dropdown can name every project. Sitting
    // here rather than on `SprintListModel` because the list is owned by the shell's picker,
    // not by the sprint list model — the same split `BoardDestination` draws.
    val picker = remember(environment.repository) { ProjectPickerModel(environment.repository) }
    DisposableEffect(picker) {
        picker.observe(scope)
        onDispose { picker.stopObserving() }
    }

    // The current project's name and prefix, shown in the switcher pill. Loaded here rather
    // than on `SprintListModel` because the model's `isRelevant` only watches sprint-level
    // changes and would miss a project rename; this read is independent of the sprint list.
    val currentProject by produceState<Project?>(initialValue = null, projectID, environment) {
        value = runCatching { environment.repository.project(projectID) }.getOrNull()
    }

    // `null` for creating, a `SprintID` for editing. Named rather than a separate boolean
    // because "creating" and "editing sprint X" are one state with two shapes, and two
    // booleans would allow the impossible third.
    var editorSprintID by remember(projectID) { mutableStateOf<SprintID?>(null) }
    var isEditorVisible by remember(projectID) { mutableStateOf(false) }
    var isEditorBusy by remember(projectID) { mutableStateOf(false) }

    SprintListScreen(
        rows = model.rows,
        isLoading = model.isLoading,
        isEmpty = model.isEmpty,
        canCreate = !model.hasOpenSprint,
        errorMessage = model.errorMessage,
        project = currentProject,
        onOpen = { sprintID ->
            // `openSprint` rather than `push`, because the list belongs *underneath* the detail and a
            // `push` onto an empty stack — which is what a plain tab switch leaves — would
            // describe a sprint with nothing beneath it. One transition, so the stack is
            // never published with the list missing.
            router.openSprint(projectID = projectID, sprintID = sprintID)
        },
        onNewSprint = {
            editorSprintID = null
            isEditorVisible = true
        },
        onDismissError = model::dismissError,
        onOpenSwitcher = { router.popToRoot() },
        projects = picker.projects,
        onSelectProject = { router.openProject(it, AppTab.Sprints) },
    )

    // Open the editor for a sprint by id, reading it first. `null` when it has gone — the
    // store deletes a sprint independently — and a create rather than an edit is the honest
    // answer for a form whose subject no longer exists, because it is not silently editing
    // a blank.
    val existingSprint by produceState<Sprint?>(initialValue = null, editorSprintID, environment) {
        value = if (isEditorVisible && editorSprintID != null) {
            runCatching { environment.repository.sprint(editorSprintID!!) }.getOrNull()
        } else {
            null
        }
    }
    val editor = remember(projectID, editorSprintID, environment, existingSprint) {
        SprintEditorModel(
            projectID = projectID,
            existing = existingSprint,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
        )
    }
    // Same reason the task form has one: the model has no in-flight flag, and a save button
    // that can be pressed twice answers a double tap with two writes.
    if (isEditorVisible) {
        SprintEditorSheet(
            name = editor.name,
            goalStoryPoints = editor.goalStoryPoints,
            startDate = editor.startDate,
            endDate = editor.endDate,
            owner = editor.owner,
            isCreating = editor.isCreating,
            canSave = editor.canSave,
            endIsBeforeStart = editor.endIsBeforeStart,
            onNameChanged = editor::onNameChanged,
            onGoalChanged = editor::onGoalChanged,
            onStartDateChanged = editor::onStartDateChanged,
            onEndDateChanged = editor::onEndDateChanged,
            onOwnerChanged = editor::onOwnerChanged,
            onSave = {
                scope.launch {
                    isEditorBusy = true
                    val saved = editor.save()
                    isEditorBusy = false
                    if (saved) isEditorVisible = false
                }
            },
            onCancel = { isEditorVisible = false },
            isBusy = isEditorBusy,
            visible = true,
            onDismissRequest = { isEditorVisible = false },
            onDismissed = {},
        )
    }
}

/**
 * One sprint's own screen.
 *
 * ## The viewer's name is a `remember` key, for the same reason the board's is
 *
 * `SprintDetail.canFinish` depends on who is looking, and the board's filter has already
 * established the pattern: a name that arrives after this screen was composed has to build
 * a new model rather than leave the first one answering "is this mine" from a stale `null`.
 * On a cold launch that builds the model twice, which is the cost of the alternative being
 * a Finish button that appears a moment late.
 *
 * ## A deleted sprint reads as "nothing", not as a blank screen
 *
 * The store deletes a sprint on its own — [com.tasktracker.featureprojectsdomain.
 * DeleteSprint] returns its tasks to the backlog — so a sprint on screen can vanish between
 * the read and the render. `SprintDetailModel` answers that with `null`, and this returns
 * rather than rendering a detail made of empty fields, which would be a screen the user
 * could dismiss but not interpret.
 */
@Composable
private fun SprintDetailDestination(
    projectID: ProjectID,
    sprintID: SprintID,
    router: Router,
    environment: ProjectEnvironment,
    scope: CoroutineScope,
    preferences: ShellPreferences,
) {
    val currentUserName by preferences.currentUserName.collectAsState()
    val model = remember(sprintID, environment, preferences, currentUserName) {
        SprintDetailModel(
            sprintID = sprintID,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
            currentUserName = currentUserName,
        )
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    // The summary is the model's own value rather than a second read: the detail screen has
    // already loaded the items, and re-reading them at the moment the summary is opened is
    // how the two screens end up reporting different totals for the same sprint.
    var isSummaryOpen by remember(sprintID) { mutableStateOf(false) }

    val detail = model.detail
    if (detail == null) {
        // Deliberately empty rather than an error banner: this is a sprint that is gone, not
        // a failure, and a banner saying "could not load" would be inventing a cause.
        return
    }

    if (isSummaryOpen) {
        SprintSummaryScreen(
            summary = detail.summary,
            onBack = { isSummaryOpen = false },
            modifier = Modifier.testTag(SprintSummaryDestinationTag),
        )
        return
    }

    SprintDetailScreen(
        detail = detail,
        isBusy = model.isFinishing,
        errorMessage = model.errorMessage,
        onFinish = { scope.launch { model.finish() } },
        onReopen = { scope.launch { model.reopen() } },
        onViewSummary = { isSummaryOpen = true },
        onDismissError = model::dismissError,
    )
}

/** The summary, rendered in place of the detail screen. See [SprintDetailDestination]. */
const val SprintSummaryDestinationTag = "shell.sprintSummary"

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
    initialSprintID: SprintID?,
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

    // `initialSprintID` is a model key alongside `initialStatus`: both are create-time facts
    // frozen into the draft, so a different sprint pair must build a different model rather
    // than have the remembered one hold the old sprint forever.
    val model = remember(projectID, workItemID, repository, existing, initialStatus, initialSprintID) {
        WorkItemEditorModel(projectID, existing.item, repository, dateProvider, initialStatus, initialSprintID)
    }

    // Tracked here rather than on the model because the model has no in-flight flag, and an
    // editor without one answers a double tap with two writes. Named rather than defaulted at
    // the call site so the omission is visible: adding a flag is a model change.
    var isBusy by remember(model) { mutableStateOf(false) }
    var isSheetVisible by remember(projectID, workItemID, initialStatus) { mutableStateOf(true) }
    var isSheetExpanded by remember(projectID, workItemID, initialStatus) { mutableStateOf(false) }

    val toggleExpanded = { expanded: Boolean ->
        kotlin.run {
            scope.launch {
                isSheetExpanded = expanded
            }
            Unit
        }
    }

    WorkItemEditorSheet(
        title = model.title,
        details = model.details,
        storyPointsText = model.storyPointsText,
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
        onStoryPointsChanged = model::onStoryPointsChanged,
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
                if (outcome != null) {
                    if (isSheetExpanded) {
                        onDismiss()
                    } else {
                        isSheetVisible = false
                    }
                }
            }
        },
        onCancel = {
            if (isSheetExpanded) {
                onDismiss()
            } else {
                isSheetVisible = false
            }
        },
        onDismissError = model::dismissError,
        visible = isSheetVisible,
        onDismissRequest = {
            if (!isBusy) {
                if (isSheetExpanded) {
                    onDismiss()
                } else {
                    isSheetVisible = false
                }
            }
        },
        onDismissed = onDismiss,
        expanded = isSheetExpanded,
        onToggleExpanded = toggleExpanded,
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

/** One tab's label. Exhaustive over [AppTab], so a sixth tab is a compile error here. */
@Composable
private fun tabTitle(tab: AppTab): String = when (tab) {
    AppTab.Projects -> stringResource(Res.string.tab_projects)
    AppTab.Board -> stringResource(Res.string.tab_board)
    AppTab.Backlog -> stringResource(Res.string.tab_backlog)
    AppTab.Sprints -> stringResource(Res.string.tab_sprints)
    AppTab.Settings -> stringResource(Res.string.tab_settings)
}

/**
 * One tab's glyph, beside [tabTitle] rather than inside the design system.
 *
 * The drawings live in `ttTabIcons` because they are visual assets; *which* tab gets which one
 * is the shell's decision, and it is a `when` over [AppTab] for the same reason [tabTitle] is
 * one — a sixth tab becomes a compile error here rather than a blank square.
 *
 * The mapping matches the native app's SF Symbols one for one, and `ttTabIcons.mapping` records
 * that correspondence so the two cannot drift apart without a test noticing.
 *
 * `internal` rather than `private` because nothing else asserted *this* choice. The design
 * system tests its own drawings and `everyTabIsNamedAndCarriesTheTabRole` asserts the labels,
 * so a tab handed its neighbour's glyph passed everything - found by mutation U1, which went
 * green. The function is pure and takes no composable scope, so a test can call it directly
 * and assert the whole set rather than a restated copy of the mapping.
 */
internal fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.Projects -> ttTabIcons.projects
    AppTab.Board -> ttTabIcons.board
    AppTab.Backlog -> ttTabIcons.backlog
    AppTab.Sprints -> ttTabIcons.sprints
    AppTab.Settings -> ttTabIcons.settings
}

// MARK: - Tags

/** The tab bar as a whole. */
const val TabBarTag = "shell.tabs"

/** The "choose a project" button on a scoped tab with nothing active. */
const val ScopedChooseProjectTag = "shell.scoped.chooseProject"

/** The editor's container, so a test can assert the route is being rendered. */
const val WorkItemEditorDestinationTag = "shell.workItemEditor"

/** One tab's button, so a test taps a tab by name rather than by position. */
fun TabButtonTag(tab: AppTab): String = "shell.tab.${tab.wireValue}"
