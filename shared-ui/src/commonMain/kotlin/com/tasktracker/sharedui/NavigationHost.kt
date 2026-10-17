@file:OptIn(ExperimentalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalSheetPresentation
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.SheetPresentation
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassNavigationRail
import com.tasktracker.designsystem.TTGlassTabBar
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamspresentation.MemberAdminModel
import com.tasktracker.featureteamspresentation.MembersAdminScreen
import com.tasktracker.featureprojectspresentation.assignableMembersFor
import com.tasktracker.featureprojectspresentation.currentProjectEnvironment
import com.tasktracker.featureprojectspresentation.ProjectArchiveModel
import com.tasktracker.featureprojectspresentation.ProjectArchiveScreen
import com.tasktracker.featureprojectspresentation.ProjectBacklogModel
import com.tasktracker.featureprojectspresentation.SprintDetailModel
import com.tasktracker.featureprojectspresentation.SprintDetailPlaceholder
import com.tasktracker.featureprojectspresentation.SprintDetailScreen
import com.tasktracker.featureprojectspresentation.SprintEditorModel
import com.tasktracker.featureprojectspresentation.SprintEditorSheet
import com.tasktracker.featureprojectspresentation.SprintItemPickerSheet
import com.tasktracker.featureprojectspresentation.SprintListModel
import com.tasktracker.featureprojectspresentation.WorkItemDependencyPickerSheet
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

    // The admin ports are public properties in other modules, so the compiler cannot smart-cast
    // their null checks across `if` branches. Capture them once as locals so the branches below
    // can narrow their types freely.
    val memberAdminPort = environment.memberAdminPort
    val adminProjectsPort = environment.adminProjectsPort

    // Read once, used twice: the chrome below and the pane arrangement further down ask the same
    // question, and two reads of the same local is two chances for them to disagree about what
    // layout this shell is in.
    val layoutClass = LocalShellLayoutClass.current

    // Editors that have been dismissed but are still playing their exit transition.
    // Kept composed until the animation finishes so the exit can be seen.
    val dismissingEditors = remember { mutableStateListOf<AppRoute.WorkItemEditor>() }
    // Exit transition durations. The pop waits EDITOR_EXIT_MS after dismissal so the slide
    // can finish; it is derived from EDITOR_EXIT_SLIDE_MS + slack so raising the spec cannot
    // silently cut the animation mid-flight. The +50ms slack matches the previous hand-tuned
    // 350ms over a 300ms slide.
    //
    // These are the `Compact` arm's alone. Under `Expanded` there is no slide to finish, so
    // dismissal is immediate (see `dismissEditor`) and none of this is read.
    val EDITOR_EXIT_FADE_MS = 200L
    val EDITOR_EXIT_SLIDE_MS = 300L
    val EDITOR_EXIT_MS = EDITOR_EXIT_SLIDE_MS + 50L
    // The windowed enter's starting scale. The same number as design-system's sheet
    // `WINDOW_SCALE` on purpose — one desktop vocabulary for every modal — but a separate
    // declaration because the modules cannot share it: this one sits above design-system
    // and reads its constants no more than it reads its types.
    val EDITOR_WINDOW_SCALE = 0.94f

    // Dismissal is one function for both exits — the editor's own Cancel/back chevron and
    // system back — because two copies is how the two drift into different answers for what
    // "closing" means, which is the same failure shape as a second piece of navigation state.
    //
    // Under `Compact` the slide is the event being dismissed. The route stays on the path and
    // the editor stays composed (via `dismissingEditors`) until EDITOR_EXIT_MS has given the
    // slide its full run, and only then does the pop happen — popping first would tear the
    // surface down mid-flight and the user would never see it leave.
    //
    // Under `Expanded` there is no slide: the window scale-fades in, and dismissing it drops
    // the route in the click itself. Holding it for a delay that animates nothing would be
    // dead time, and `dismissingEditors` would be state nothing reads — which is exactly what
    // it becomes, since this arm never writes it.
    //
    // Returns whether it took the dismissal: `false` means "already playing its exit", which
    // the back handler needs in order to fall through to a plain pop rather than queue a
    // second dismissal behind the first one's delay.
    fun dismissEditor(editorRoute: AppRoute.WorkItemEditor): Boolean {
        if (layoutClass == ShellLayoutClass.Expanded) {
            val currentPath = router.path(tab)
            if (currentPath.lastOrNull() == editorRoute) {
                router.setPath(currentPath.dropLast(1), tab)
            }
            return true
        }
        if (editorRoute in dismissingEditors) return false
        dismissingEditors.add(editorRoute)
        scope.launch {
            delay(EDITOR_EXIT_MS)
            dismissingEditors.remove(editorRoute)
            val latestPath = router.path(tab)
            if (latestPath.lastOrNull() == editorRoute) {
                router.setPath(latestPath.dropLast(1), tab)
            }
        }
        return true
    }

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
    // The rail's own width, published for the same reason `tabBarInset` is published and by the
    // same mechanism — see the overlay note above for what the inset buys back.
    //
    // It is measured rather than known because a rail is as wide as its widest caption, and a
    // caption is a typography measurement rather than a token. There is no number to put here,
    // which is the same finding that kept a rail-width token out of the design system.
    //
    // Under `Compact` this stays 0.dp for the life of the composition, because only the expanded
    // arm writes it. The padding below is therefore a no-op on a phone rather than a branch that
    // has to be re-verified there.
    val railInset = remember { mutableStateOf(0.dp) }
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
        // What a modal does follows the same class the chrome follows: anchored and sliding
        // under `Compact`, a centred window under `Expanded`. Derived here from the one read
        // above, so the sheets cannot disagree with the chrome about which layout the shell
        // is in — the local is the only channel that reaches design-system from down here.
        LocalSheetPresentation provides if (layoutClass == ShellLayoutClass.Expanded) {
            SheetPresentation.Window
        } else {
            SheetPresentation.BottomSheet
        },
        LocalNavigationEventDispatcherOwner provides (hostDispatcherOwner ?: fallbackDispatcherOwner),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // Starts beside the rail under `Expanded` and flush under `Compact`, for the same
            // reason the bar is overlaid rather than a second child of a `Column`: the rail floats
            // over the content and the content has to be given back the room it covers.
            Box(modifier = Modifier.fillMaxSize().padding(start = railInset.value)) {
                val path = navigation.pathFor(tab)
                val pushed = path.lastOrNull()

                // A pushed editor is an *overlay*, not a destination of its own right: the
                // layer below renders the exact path it was pushed onto, which keeps the
                // screen that opened it in the composition slot it held before the push.
                // Rendering the editor inside its own PushedRoute branch used to dispose and
                // rebuild the destination underneath — the sprint detail's model was torn
                // down and replayed its repository load, so the list behind the sheet
                // flashed on open and flashed again on close. The Board/Backlog root kept
                // under an editor (`backgroundPath` empty below) is the same rule at depth
                // zero, applied here to every tab: a deep-linked editor now sits over that
                // tab's real root instead of a fabricated Board.
                val editorOverlay = pushed is AppRoute.WorkItemEditor
                val backgroundPath = if (editorOverlay) path.dropLast(1) else path
                val backgroundPushed = backgroundPath.lastOrNull()

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
                        val editorRoute = currentPath.lastOrNull() as? AppRoute.WorkItemEditor
                        if (editorRoute == null) {
                            if (currentPath.size > 1) {
                                router.setPath(currentPath.dropLast(1), tab)
                            }
                        } else if (!dismissEditor(editorRoute) && currentPath.size > 1) {
                            // `dismissEditor` declined because the editor is already playing
                            // its exit: a second back press pops now rather than queueing
                            // behind the first one's delay.
                            router.setPath(currentPath.dropLast(1), tab)
                        }
                    },
                )

                // The tab's root and whatever is pushed onto it, as two values rather than two
                // call sites, because there are now three arrangements and writing a ten-argument
                // call three times is three places for the wiring to drift apart.
                val tabRoot: @Composable () -> Unit = {
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
                val pushedPane: @Composable () -> Unit = {
                    // `backgroundPushed` is non-null on every path that reaches this lambda. It is
                    // read through `requireNotNull` rather than captured as a nullable parameter
                    // so that a second answer to "is there a pushed route" does not exist for a
                    // reader to keep in step with the branch below.
                    PushedRoute(
                        route = requireNotNull(backgroundPushed),
                        tab = tab,
                        router = router,
                        environment = environment,
                        settings = settings,
                        preferences = preferences,
                        themeScheme = themeScheme,
                        scope = scope,
                        memberAdminPort = memberAdminPort,
                        adminProjectsPort = adminProjectsPort,
                    )
                }
                // The detail pane's own empty state, a value for the same reason `tabRoot` and
                // `pushedPane` are: it is a whole pane of one arrangement arm, and inlining the
                // lambda would make this the one pane of the three with no name.
                val noSprintSelected: @Composable () -> Unit = { SprintDetailPlaceholder() }
                // The projects section's two panes with no port bound: the tagged placeholder the
                // routing is exercised through, and a right pane that stays empty because a
                // section with no list has nothing to put in it. Values rather than inline
                // lambdas, and for the same reason `tabRoot` and `pushedPane` are: each is a
                // whole pane of one arm.
                val adminProjectsPlaceholderPane: @Composable () -> Unit = {
                    AdminProjectsPlaceholder(projectID = null)
                }
                val noAdminSelected: @Composable () -> Unit = { AdminDetailPlaceholder() }

                // Which of the arrangements. Read off the layout class, never off the
                // platform — see `ShellLayoutClass` for why — and off the tab, because a
                // list-detail pair whose second pane is already a route in the same stack is
                // what lets it be widened without new state.
                //
                // `Projects` is excluded deliberately and that is a finding rather than a
                // deferral: its root is the projects list and its "detail" — the board — is a
                // *different tab*, so a two-pane Projects would be drawing two panes belonging to
                // two tabs and would need a second notion of "the project under the cursor" to
                // reconcile them. Backlog's three sections are one screen, not pushes.
                //
                // The Settings tab's projects section is the second pair, and it qualifies for
                // the same reason Sprints does: the selection is a route in this stack. The
                // difference is only which element the left pane draws — the tab root for
                // Sprints, the section's list route for admin — and the branch says so.
                //
                // Both admin sections are the exception among these four, and for the same
                // reason: the right pane is the *section's* rather than the stack's. Members has
                // no detail screen at all (D6) — its one screen is a list whose rows carry delete
                // and reactivate — so its second pane is the create form the section owns, which
                // is neither a route nor a selection. Projects has the editor as well, and its
                // pane holds that when a project is selected and the same create form when none
                // is. Hence one pane here when no port is bound, and a single destination — which
                // draws its own two panes, and remembers the create model above both of them so
                // the list can be told to re-read when a create lands — when one is.
                //
                // Nothing here is animated and nothing is remembered. The panes are the same
                // composables the single-pane path renders, so a tap, a deep link and a restored
                // stack all land on the same screen and differ only in where it is drawn — and
                // `BackHandler` above is untouched, because popping the route removes the detail
                // pane and the list goes back to full width, which is the right answer in both
                // arrangements.
                when {
                    backgroundPushed == null -> tabRoot()

                    // The Sprints list-detail pair. Its root stack is `[SprintList]` and never
                    // empty — see `Router.openProject` — so `backgroundPushed` is non-null even
                    // with no sprint selected, and rendering `pushedPane` unconditionally drew
                    // the *list* in the right pane: the same screen on both sides. The right pane
                    // is the detail only when the stack actually holds a detail route; otherwise
                    // nothing is selected and the pane says so.
                    layoutClass == ShellLayoutClass.Expanded && tab == AppTab.Sprints ->
                        SprintTwoPanes(
                            listPane = tabRoot,
                            detailPane = if (backgroundPushed is AppRoute.SprintDetail) {
                                pushedPane
                            } else {
                                noSprintSelected
                            },
                        )

                    layoutClass == ShellLayoutClass.Expanded && tab == AppTab.Settings &&
                        backgroundPushed is AppRoute.AdminMembers ->
                        if (memberAdminPort == null) {
                            AdminMembersPlaceholder()
                        } else {
                            AdminMembersDestination(
                                router = router,
                                memberAdminPort = memberAdminPort,
                                scope = scope,
                                isWide = true,
                            )
                        }

                    layoutClass == ShellLayoutClass.Expanded && tab == AppTab.Settings &&
                        backgroundPushed is AppRoute.AdminProjects ->
                        if (adminProjectsPort == null) {
                            AdminTwoPanes(
                                listPane = adminProjectsPlaceholderPane,
                                detailPane = if (backgroundPushed.projectID == null) {
                                    noAdminSelected
                                } else {
                                    // A lambda, not the call itself, for the reason the type checker
                                    // gave when this was first written: the call returns `Unit`,
                                    // which widened this `if` to `Any` and failed against the
                                    // `@Composable () -> Unit` the pane slot wants.
                                    { AdminProjectsPlaceholder(projectID = backgroundPushed.projectID) }
                                },
                            )
                        } else {
                            // The bound-port arm draws the whole section, both panes, for the
                            // reason the comment above gives: the create model it remembers sits
                            // above the list and the pane the form is drawn in.
                            AdminProjectsDestination(
                                projectID = backgroundPushed.projectID,
                                router = router,
                                adminProjectsPort = adminProjectsPort,
                                memberAdminPort = memberAdminPort,
                                scope = scope,
                                isWide = true,
                            )
                        }

                    else -> pushedPane()
                }

                // Render all editors in the path with proper enter/exit animations.
                // Each editor gets an AnimatedVisibility driven by a MutableTransitionState
                // that is born invisible so the enter transition actually plays. Dismissed
                // editors stay composed until their exit transition finishes.
                val editorsInPath = path.filterIsInstance<AppRoute.WorkItemEditor>()
                // Keep dismissing editors composed so their exit transition can finish.
                val editorsToRender = editorsInPath + dismissingEditors.filter { it !in editorsInPath }
                Box(Modifier.fillMaxSize()) {
                    editorsToRender.forEach { editorRoute ->
                        key(editorRoute) {
                            // Born invisible so that flipping targetState to true triggers the enter transition.
                            val transitionState = remember(editorRoute) { MutableTransitionState(false) }
                            transitionState.targetState = editorRoute !in dismissingEditors

                            // Under `Expanded` the editor arrives as a window: the same fade,
                            // plus a scale where the slide was — a surface travelling the full
                            // height of a wide window to reach the middle is animation spent
                            // going nowhere. The scale matches the sheet's on purpose (0.94f):
                            // one desktop vocabulary for every modal, not one per component.
                            //
                            // The window's exit is a plain fade because it never plays:
                            // dismissal drops the route (see `dismissEditor`), the node leaves
                            // the composition at once, and a window's close has no travel to
                            // finish either.
                            val isWindow = layoutClass == ShellLayoutClass.Expanded
                            AnimatedVisibility(
                                visibleState = transitionState,
                                enter = if (isWindow) {
                                    fadeIn(animationSpec = tween(300, easing = { 1f - (1f - it) * (1f - it) })) +
                                        scaleIn(
                                            animationSpec = tween(300, easing = { 1f - (1f - it) * (1f - it) }),
                                            initialScale = EDITOR_WINDOW_SCALE,
                                        )
                                } else {
                                    fadeIn(animationSpec = tween(300, easing = { 1f - (1f - it) * (1f - it) })) +
                                        slideInVertically(
                                            animationSpec = tween(400, easing = { 1f - (1f - it) * (1f - it) }),
                                            initialOffsetY = { height -> height },
                                        )
                                },
                                // Exits the way it entered: downward, not sideways. A sheet
                                // that leaves on the horizontal axis reads as a screen push.
                                exit = if (isWindow) {
                                    fadeOut(animationSpec = tween(EDITOR_EXIT_FADE_MS.toInt()))
                                } else {
                                    fadeOut(animationSpec = tween(EDITOR_EXIT_FADE_MS.toInt())) +
                                        slideOutVertically(
                                            animationSpec = tween(EDITOR_EXIT_SLIDE_MS.toInt(), easing = { 1f - (1f - it) * (1f - it) }),
                                            targetOffsetY = { height -> height },
                                        )
                                },
                                label = editorRoute.workItemID?.stringValue ?: "new-${editorRoute.projectID}",
                            ) {
                                WorkItemEditorDestination(
                                    projectID = editorRoute.projectID,
                                    workItemID = editorRoute.workItemID,
                                    initialStatus = editorRoute.initialStatus,
                                    initialSprintID = editorRoute.initialSprintID,
                                    repository = environment.repository,
                                    dateProvider = environment.dateProvider,
                                    scope = scope,
                                    router = router,
                                    tab = tab,
                                    preferences = preferences,
                                    teamPort = environment.teamPort,
                                    onDismiss = { dismissEditor(editorRoute) },
                                )
                            }
                        }
                    }
                }
            }

            // Which chrome is drawn is the only question the layout class is asked here, and it is
            // asked of the width the shell was offered — never of the platform. See
            // `ShellLayoutClass` for why `isDesktopApp` is the wrong source for that answer.
            //
            // The load-bearing half of this branch is not the `when` but what the expanded arm does
            // *not* do: it never writes `tabBarInset`, so `LocalTabBarScrollUnderInset` keeps its
            // `0.dp` default and every scrolling list stops padding for a bar that is not
            // composed. Reusing the default is the point — a second spelling of "there is no bar"
            // is a second thing to keep true, and `TTBottomSheetScaffold` reads the same local for
            // the same reason.
            //
            // The bar's own `WindowInsets.ime` guard and its `navigationBarsPadding()` are left
            // exactly as they are rather than made conditional. They are already correct at zero
            // on a desktop window, and a branch that says so is a branch that has to be
            // re-verified on the phone.
            when (layoutClass) {
                ShellLayoutClass.Compact -> TabBar(
                    selected = tab,
                    onSelect = router::selectTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { size: IntSize ->
                            tabBarInset.value = with(tabBarDensity) { size.height.toDp() }
                        },
                )

                ShellLayoutClass.Expanded -> NavigationRail(
                    selected = tab,
                    onSelect = router::selectTab,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { size: IntSize ->
                            railInset.value = with(tabBarDensity) { size.width.toDp() }
                        },
                )
            }
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
        AppTab.Projects -> ProjectsHost(
            router = router,
            environment = environment,
            currentUserID = preferences.currentUserID,
            currentUserName = preferences.currentUserName,
        )

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
                router = router,
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
 * Exhaustive over [AppRoute] with no `else`, deliberately: a new route has to be a compile
 * error here rather than a runtime fall-through. The sealed interface exists to give exactly
 * that, and this switch is where it pays off.
 */
@Composable
private fun PushedRoute(
    route: AppRoute,
    tab: AppTab,
    router: Router,
    environment: ProjectEnvironment,
    settings: SettingsPort?,
    preferences: ShellPreferences,
    themeScheme: ThemeScheme,
    scope: CoroutineScope,
    memberAdminPort: MemberAdminPort?,
    adminProjectsPort: AdminProjectsPort?,
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
            teamPort = environment.teamPort,
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

        // The outer host lifts every editor on top of the path that produced it, so this
        // branch is unreachable through anything the app builds: an editor could only sit
        // here as the background of a *second*, stacked editor, which nothing pushes. The
        // sealed `when` still demands a rule, and a bare Board keeps it from imaging a
        // second sheet for a case that has none.
        is AppRoute.WorkItemEditor -> BoardDestination(
            projectID = route.projectID,
            router = router,
            environment = environment,
            scope = scope,
            preferences = preferences,
        )

        // The Settings tab root *is* the settings screen, so pushing `AppRoute.Settings` onto
        // the Settings stack renders the same screen again. That is kept on purpose: a deep
        // link to `tasktracker://settings` must land on settings, and a separate "pushed
        // settings" screen would give that URL two answers depending on how it got there.
        AppRoute.Settings -> if (settings == null) {
            MissingSettingsServiceView()
        } else {
            SettingsDestination(
                port = settings,
                router = router,
                preferences = preferences,
                themeScheme = themeScheme,
                scope = scope,
            )
        }

        // The two admin sections. Unlike every other route here they are reached only from a
        // screen rather than from a link, and the Settings tab's own root is the settings
        // screen — so these are the *first pushed* routes on its stack, never tab roots. Each
        // renders its real section when the app bound that section's port, and a bare
        // placeholder when it did not, which is what the branch below chooses between.
        AppRoute.AdminMembers -> {
            if (memberAdminPort == null) {
                AdminMembersPlaceholder()
            } else {
                AdminMembersDestination(
                    router = router,
                    memberAdminPort = memberAdminPort,
                    scope = scope,
                )
            }
        }

        is AppRoute.AdminProjects ->
            if (adminProjectsPort == null) {
                AdminProjectsPlaceholder(projectID = route.projectID)
            } else {
                AdminProjectsDestination(
                    projectID = route.projectID,
                    router = router,
                    adminProjectsPort = adminProjectsPort,
                    memberAdminPort = memberAdminPort,
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

/**
 * Display names for a set of ids, resolved through the directory in one batch.
 *
 * Empty when there is no [TeamPort] — a host that wired none simply draws no pills rather
 * than inventing labels — and unknown ids are omitted by [TeamPort.users] itself.
 */
@Composable
private fun rememberAssigneeNames(
    ids: List<UserID>,
    teamPort: TeamPort?,
): Map<UserID, String> {
    var names by remember(ids, teamPort) { mutableStateOf<Map<UserID, String>>(emptyMap()) }
    LaunchedEffect(ids, teamPort) {
        names = if (teamPort == null || ids.isEmpty()) {
            emptyMap()
        } else {
            runCatching { teamPort.users(ids) }
                .getOrDefault(emptyList())
                .associate { it.id to it.preferredGreeting }
        }
    }
    return names
}

/** One id's display name, or `null` when the directory cannot produce one. */
@Composable
private fun rememberUserName(id: UserID?, teamPort: TeamPort?): String? {
    var name by remember(id, teamPort) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, teamPort) {
        name = if (id == null || teamPort == null) {
            null
        } else {
            runCatching { teamPort.user(id) }.getOrNull()?.preferredGreeting
        }
    }
    return name
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
    val currentUserID by preferences.currentUserID.collectAsState()
    // `currentUserID` is a `remember` key on purpose. The board's filter freezes the id into
    // the model, so an id that arrived after this screen was composed has to build a new model
    // rather than leave the first one filtering on a stale or absent id — the same
    // rebuild-and-cancel-the-previous-observation `ProjectBoardDestination` documents, and the
    // `DisposableEffect` below cancels the old observation, so this cannot leave two boards
    // watching the store. On a cold launch this does build the model twice: once with `null`,
    // then again once the session is known.
    val model = remember(projectID, environment, preferences, currentUserID) {
        ProjectBoardModel(
            projectID = projectID,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
            currentUserID = currentUserID,
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

    val assigneeNames = rememberAssigneeNames(
        ids = model.projection.items.mapNotNull { it.assigneeID },
        teamPort = environment.teamPort,
    )

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
        canAddTask = !model.addsLocked,
        assigneeNames = assigneeNames,
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
        onUnarchive = { itemID -> scope.launch { model.unarchive(itemID) } },
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
    teamPort: TeamPort? = null,
) {
    val model = remember(projectID, repository, dateProvider) {
        ProjectArchiveModel(projectID, repository, dateProvider)
    }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    val assigneeNames = rememberAssigneeNames(
        ids = model.projection.months.flatMap { month -> month.items }
            .mapNotNull { it.assigneeID }
            .distinct(),
        teamPort = teamPort,
    )

    ProjectArchiveScreen(
        projection = model.projection,
        isLoading = model.isLoading,
        errorMessage = model.errorMessage,
        onSelect = { itemID ->
            router.push(AppRoute.WorkItemEditor(projectID = projectID, workItemID = itemID))
        },
        onDismissError = model::dismissError,
        onOpenSwitcher = { router.popToRoot() },
        assigneeNames = assigneeNames,
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
 * ## The create button is unconditional; the editor names a blocker instead
 *
 * The rule is overlapping windows, not a single open sprint, so "there is already one" is
 * no longer a reason to withhold the button — a project that plans ahead has two open
 * sprints and that is legal. The form is where a conflict is caught: it is handed the
 * project's sprints read at open time, and `blockedBySprintName` names the unfinished
 * sibling whose window the draft would collide with, under the field that has to change.
 * The store's refusal is still there for the race, and it arrives as the usual banner.
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
        SprintListModel(projectID, environment.repository, environment.dateProvider)
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
        inProgressRows = model.inProgressRows,
        scheduledRows = model.scheduledRows,
        finishedRows = model.finishedRows,
        archivedRows = model.archivedRows,
        activeIsEmpty = model.activeIsEmpty,
        archivedIsEmpty = model.archivedIsEmpty,
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
    // The project's sprints as they stood when the editor opened, so the form can name a
    // blocker before the write is attempted. Read on every open rather than held across
    // them: a sibling created by another device while the list was on screen is exactly the
    // case the form exists to catch, and a cached read would miss it.
    val siblingSprints by produceState<List<Sprint>>(
        emptyList(),
        isEditorVisible,
        projectID,
        environment,
    ) {
        value = if (isEditorVisible) {
            runCatching { environment.repository.sprints(projectID) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
    val editor = remember(
        projectID,
        editorSprintID,
        environment,
        existingSprint,
        siblingSprints,
    ) {
        SprintEditorModel(
            projectID = projectID,
            existing = existingSprint,
            projectSprints = siblingSprints,
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
            goal = editor.goal,
            startDate = editor.startDate,
            endDate = editor.endDate,
            ownerID = editor.ownerID,
            isCreating = editor.isCreating,
            canSave = editor.canSave,
            endIsBeforeStart = editor.endIsBeforeStart,
            blockedBySprintName = editor.blockedBySprintName,
            windowLocked = editor.windowLocked,
            onNameChanged = editor::onNameChanged,
            onGoalStoryPointsChanged = editor::onGoalStoryPointsChanged,
            onGoalChanged = editor::onGoalChanged,
            onStartDateChanged = editor::onStartDateChanged,
            onEndDateChanged = editor::onEndDateChanged,
            onOwnerChanged = editor::onOwnerChanged,
            searchMembers = { query ->
                // 1.6: a sprint's owner may only be one of this project's members. The
                // directory knows nothing about projects, so the intersection is applied here,
                // where the project and the directory are both to hand.
                assignableMembersFor(
                    project = currentProject,
                    directory = environment.teamPort?.searchMembers(query) ?: emptyList(),
                )
            },
            // Unfiltered by design: see MemberPicker.resolveSelected. The owner a sprint already
            // has must keep its name even if that person has since left the project.
            resolveSelected = { ids -> environment.teamPort?.users(ids) ?: emptyList() },
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
 * ## The viewer's id is a `remember` key, for the same reason the board's is
 *
 * `SprintDetail.canFinish` depends on who is looking, and the board's filter has already
 * established the pattern: an id that arrives after this screen was composed has to build
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
    val currentUserID by preferences.currentUserID.collectAsState()
    val currentUserName by preferences.currentUserName.collectAsState()
    val model = remember(sprintID, environment, preferences, currentUserID) {
        SprintDetailModel(
            sprintID = sprintID,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
            currentUserID = currentUserID,
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

    val ownerName = rememberUserName(detail.sprint.ownerID, environment.teamPort)

    // The project, read for 1.6's member filter — the same read `SprintsDestination` makes, and
    // for the same reason: only the host holds both the project and the directory.
    val currentProject by produceState<Project?>(initialValue = null, projectID, environment) {
        value = runCatching { environment.repository.project(projectID) }.getOrNull()
    }

    // The editor over this sprint, opened by the card's edit button. Declared before the
    // screen because the screen's [SprintDetailScreen.onEdit] is what sets it.
    var isEditorVisible by remember(sprintID) { mutableStateOf(false) }
    var isEditorBusy by remember(sprintID) { mutableStateOf(false) }

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
        isBusy = model.isBusy,
        errorMessage = model.errorMessage,
        onFinish = { scope.launch { model.finish() } },
        onReopen = { scope.launch { model.reopen() } },
        onViewSummary = { isSummaryOpen = true },
        onDismissError = model::dismissError,
        onRemove = { scope.launch { model.removeSprint() } },
        onArchive = { scope.launch { model.archive() } },
        // The gate on the "+" is the screen's (`editable && !isBusy`); [SprintDetailModel.openPicker]
        // re-checks it, because a disabled button is only a courtesy and the write it guards
        // is the one that has to hold.
        onAddTasks = model::openPicker,
        onEdit = { isEditorVisible = true },
        onOpenTask = { itemID ->
            router.push(AppRoute.WorkItemEditor(projectID = projectID, workItemID = itemID))
        },
        onRemoveTask = { itemID -> scope.launch { model.removeItem(itemID) } },
        ownerName = ownerName,
    )

    // The editor over this sprint, opened by the card's edit button. Unlike
    // [SprintsDestination], no read is needed before the model can be built: the subject is
    // already in hand — `detail.sprint` *is* [SprintDetailModel]'s own store read — so
    // building the editor on open hands it the sprint rather than a `null` that would turn
    // the edit into a create.
    //
    // Keyed on visibility and the sibling answer, not on `detail.sprint`: a reload while
    // the sheet is open (a sync write landing, say) rebuilds the model under the user's
    // fingers and drops the draft, which is exactly the mid-edit state
    // [SprintEditorModel] exists to keep out. The snapshot is taken when the button is
    // pressed, which is when the user asked to edit *this* sprint.
    val siblingSprints by produceState<List<Sprint>>(
        emptyList(),
        isEditorVisible,
        projectID,
        environment,
    ) {
        value = if (isEditorVisible) {
            runCatching { environment.repository.sprints(projectID) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
    val editor = remember(isEditorVisible, siblingSprints, projectID, environment) {
        SprintEditorModel(
            projectID = projectID,
            existing = detail.sprint,
            projectSprints = siblingSprints,
            repository = environment.repository,
            dateProvider = environment.dateProvider,
        )
    }
    if (isEditorVisible) {
        SprintEditorSheet(
            name = editor.name,
            goalStoryPoints = editor.goalStoryPoints,
            goal = editor.goal,
            startDate = editor.startDate,
            endDate = editor.endDate,
            ownerID = editor.ownerID,
            isCreating = editor.isCreating,
            canSave = editor.canSave,
            endIsBeforeStart = editor.endIsBeforeStart,
            blockedBySprintName = editor.blockedBySprintName,
            windowLocked = editor.windowLocked,
            onNameChanged = editor::onNameChanged,
            onGoalStoryPointsChanged = editor::onGoalStoryPointsChanged,
            onGoalChanged = editor::onGoalChanged,
            onStartDateChanged = editor::onStartDateChanged,
            onEndDateChanged = editor::onEndDateChanged,
            onOwnerChanged = editor::onOwnerChanged,
            searchMembers = { query ->
                // 1.6: a sprint's owner may only be one of this project's members. The
                // directory knows nothing about projects, so the intersection is applied here,
                // where the project and the directory are both to hand.
                assignableMembersFor(
                    project = currentProject,
                    directory = environment.teamPort?.searchMembers(query) ?: emptyList(),
                )
            },
            // Unfiltered by design: see MemberPicker.resolveSelected. The owner a sprint already
            // has must keep its name even if that person has since left the project.
            resolveSelected = { ids -> environment.teamPort?.users(ids) ?: emptyList() },
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

    // Composed unconditionally and hidden with `visible`, the way the project picker and the
    // editors are hosted: gating on `if (model.isPickerOpen)` would unmount the sheet the
    // instant it closed and take its exit transition with it.
    SprintItemPickerSheet(
        items = model.pickableItems,
        selectedIDs = model.selectedPickables,
        // Toggling during the write would edit a selection the model is already sending;
        // the sheet closes on success and the gate keeps the two from racing.
        onToggle = { if (!model.isBusy) model.togglePickable(it) },
        onConfirm = { scope.launch { model.addSelected() } },
        isBusy = model.isBusy,
        visible = model.isPickerOpen,
        // A scrim tap mid-write would drop a selection that is being saved; the scrim is
        // ignored until the write lands, then the sheet closes normally.
        onDismissRequest = { if (!model.isBusy) model.closePicker() },
        onDismissed = model::closePicker,
        modifier = Modifier.fillMaxSize(),
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
    router: Router,
    tab: AppTab,
    preferences: ShellPreferences,
    teamPort: TeamPort? = null,
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
    // than have the remembered one hold the old sprint forever. `currentUserID` is a key for
    // the same reason the board's is: the draft's assignee picker labels "me" from it, and an
    // id that arrives mid-edit must relabel rather than leave a stale name on the row.
    val currentUserID by preferences.currentUserID.collectAsState()
    val currentUserName by preferences.currentUserName.collectAsState()
    val filePicker = currentProjectEnvironment?.filePicker
    val model = remember(
        projectID,
        workItemID,
        repository,
        existing,
        initialStatus,
        initialSprintID,
        currentUserID,
    ) {
        WorkItemEditorModel(
            projectID,
            existing.item,
            repository,
            dateProvider,
            initialStatus,
            initialSprintID,
            currentUserID = currentUserID,
        )
    }

    // The thread and the attachments load as soon as the model exists. A create has no
    // id yet, and both use cases no-op there, so one effect covers both routes.
    LaunchedEffect(model) {
        model.refreshComments()
        model.refreshAttachments()
    }

    // Tracked here rather than on the model because the model has no in-flight flag, and an
    // editor without one answers a double tap with two writes. Named rather than defaulted at
    // the call site so the omission is visible: adding a flag is a model change.
    // Tracked here rather than on the model because the model has no in-flight flag, and an
    // editor without one answers a double tap with two writes. Named rather than defaulted at
    // the call site so the omission is visible: adding a flag is a model change.
    var isBusy by remember(model) { mutableStateOf(false) }

    // The project's tasks, read once for two jobs: resolving the draft's blocker ids into
    // pills, and offering candidates to the picker. Backlog and board work are the same
    // query — `workItems(projectID)` is every task in the project whatever its status.
    val projectItems by produceState<List<WorkItem>>(
        initialValue = emptyList(),
        projectID,
        repository,
    ) {
        value = runCatching { repository.workItems(projectID) }.getOrDefault(emptyList())
    }

    // The project itself, read for the same reason the sprint editor reads it: 1.6 scopes the
    // assignee picker to this project's members, and only the host holds both the project and
    // the directory. `null` until it answers, which `assignableMembersFor` treats as
    // "unscoped" — so the picker shows everyone for a frame and narrows, rather than showing
    // nobody and widening.
    val currentProject by produceState<Project?>(initialValue = null, projectID, repository) {
        value = runCatching { repository.project(projectID) }.getOrNull()
    }

    // Resolved here rather than on the model: the model is a draft and must not re-read the
    // store, and an id whose task has since been deleted simply drops out of the row.
    val blockers = model.blockedByIDs.mapNotNull { id -> projectItems.firstOrNull { it.id == id } }
    // The task itself is never its own blocker. A create has no id yet, and there is nothing
    // to exclude until the item exists in the store.
    val candidates = projectItems.filter { it.id != workItemID }

    var isPickerVisible by remember(model) { mutableStateOf(false) }
    var pickerSelectedIDs by remember(model) { mutableStateOf<Set<WorkItemID>>(emptySet()) }

    // An assignee the directory cannot name shows as its id in the picker's third option —
    // the selection stays visible, and the id is the only honest label left.
    val otherAssigneeLabel = rememberUserName(model.assigneeID, teamPort)

    // Author labels for the thread, resolved through the same directory as the assignee
    // row; an id that appears after a send re-keys the remembered map and re-resolves.
    val commentAuthorNames = rememberAssigneeNames(
        ids = model.comments.map { it.authorID }.distinct(),
        teamPort = teamPort,
    )

    WorkItemEditorSheet(
        title = model.title,
        details = model.details,
        storyPointsText = model.storyPointsText,
        status = model.status,
        priority = model.priority,
        assigneeID = model.assigneeID,
        currentUserID = currentUserID,
        currentUserName = currentUserName,
        otherAssigneeLabel = otherAssigneeLabel,
        hasDueDate = model.dueDate != null,
        recurrence = model.recurrence,
        // The repeat row takes its day from the due date, so the draft's due date is the
        // anchor; `null` there means the row falls back to today's date.
        repeatAnchor = model.dueDate,
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
        onRecurrenceChanged = model::onRecurrenceChanged,
        // File away is a close, like save: the task is gone from the board, so a sheet left
        // standing would keep offering edits to a task the graph no longer shows. It dismisses
        // only on success, so an archive the store refused keeps the error banner readable.
        onFileAway = {
            scope.launch {
                if (model.fileAway()) {
                    onDismiss()
                }
            }
        },
        // The route stays mounted through the sheet's exit transition, and a failed save
        // leaves the draft visible with its error banner.
        onSave = {
            scope.launch {
                isBusy = true
                val outcome = model.save()
                isBusy = false
                if (outcome != null) {
                    onDismiss()
                }
            }
        },
        onCancel = {
            onDismiss()
        },
        onDismissError = model::dismissError,
        // Blocker/dependency management
        blockers = blockers,
        onOpenBlocker = { itemID ->
            router.push(
                AppRoute.WorkItemEditor(
                    projectID = projectID,
                    workItemID = itemID,
                ),
                tab,
            )
        },
        onRemoveBlocker = model::removeBlockedBy,
        // 1.6: an assignee may only be one of this project's members. `currentProject` is null
        // until its read answers, which `assignableMembersFor` reads as "unscoped" — so the
        // picker briefly offers everyone and then narrows, rather than offering nobody.
        searchMembers = { query ->
            assignableMembersFor(
                project = currentProject,
                directory = teamPort?.searchMembers(query) ?: emptyList(),
            )
        },
        // Unfiltered by design: see MemberPicker.resolveSelected. A task assigned to someone
        // who has since left the project must still show who it is assigned to, rather than
        // reading Unassigned while the saved model still holds an assignee.
        resolveSelected = { ids -> teamPort?.users(ids) ?: emptyList() },
        // Seeded from the draft so a reopen shows what is already there rather than an empty
        // selection — the sheet is a picker over a list the form already holds.
        onAddBlockerClick = {
            pickerSelectedIDs = model.blockedByIDs.toSet()
            isPickerVisible = true
        },
        // A create renders no thread at all — `null` rather than an empty list, so the
        // section does not flash an empty Comments block above the save row on a new item.
        comments = if (model.isCreating) null else model.comments,
        commentAuthorNames = commentAuthorNames,
        commentDraft = model.commentDraft,
        onCommentDraftChanged = model::onCommentDraftChanged,
        commentReplyTo = model.commentReplyTo,
        onCommentReply = model::onCommentReply,
        onCommentCancelReply = model::onCommentCancelReply,
        onCommentSubmit = {
            scope.launch { model.submitComment() }
        },
        // The attachments section. `attachments` is the model's list, appended to after
        // each attach — the write returns the stored row, so the view updates from the
        // value it just created rather than re-reading the store.
        attachments = model.attachments,
        isAttaching = model.isAttaching,
        onAttachClick = {
            // The picker belongs to the environment, not the screen: the graph owns the
            // platform host (an activity-result registration on Android, a document
            // picker on iOS), and the screen tree has no platform-specific object to hand
            // a factory — which is why `LocalContext` does not exist in Compose
            // Multiplatform 1.12.1's common code and why this seam lives in the
            // composition root. The `isAttaching` guard is the double-tap answer: a
            // second launch while one is in flight would pair the second pick with the
            // first pick's file, and the model has no other in-flight flag.
            val picker = filePicker
            if (picker != null && !model.isAttaching) {
                scope.launch {
                    picker.pick()?.let { picked -> model.attachFile(picked) }
                }
            }
        },
        onRemoveAttachment = { id ->
            scope.launch { model.removeAttachment(id) }
        },
        onDismissRequest = {
            if (!isBusy) {
                onDismiss()
            }
        },
        onDismissed = onDismiss,
        modifier = Modifier.fillMaxSize().testTag(WorkItemEditorDestinationTag),
    )

    // Dependency picker sheet — composed in a Box so it overlays the editor properly.
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        WorkItemDependencyPickerSheet(
            candidates = candidates,
            selectedIDs = pickerSelectedIDs,
            onToggle = { id ->
                pickerSelectedIDs =
                    if (id in pickerSelectedIDs) pickerSelectedIDs - id else pickerSelectedIDs + id
            },
            onConfirm = {
                model.addBlockedBy(pickerSelectedIDs)
                pickerSelectedIDs = emptySet()
                isPickerVisible = false
            },
            isBusy = isBusy,
            visible = isPickerVisible,
            onDismissRequest = {
                if (!isBusy) {
                    pickerSelectedIDs = emptySet()
                    isPickerVisible = false
                }
            },
            onDismissed = {
                pickerSelectedIDs = emptySet()
                isPickerVisible = false
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
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
    router: Router,
    preferences: ShellPreferences,
    themeScheme: ThemeScheme,
    scope: CoroutineScope,
) {
    val model = remember(port) { SettingsModel(port) }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    val accentPalette by preferences.accentPalette.collectAsState()
    val glassIntensity by preferences.glassIntensity.collectAsState()
    // The admin gate, read from the profile the shell already caches rather than from a new
    // feature edge. `UserRole.Admin` is the only role that opens the Admin section, and a
    // `null` role — nobody signed in — is not one: fail closed on the screen that leads to
    // member and project management.
    val isAdmin = preferences.currentUserRole.collectAsState().value == UserRole.Admin

    SettingsScreen(
        connectivity = model.connectivity,
        activity = model.activity,
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
        onSyncNow = { scope.launch { model.requestSync() } },
        // The way out of the "Could not be sent" row. It ends by asking for a cycle of its own,
        // which is why the model reports its outcome into the same state Sync Now uses rather
        // than into a second one.
        onRetryStuckChanges = { scope.launch { model.retryStuckChanges() } },
        // `signOut` goes through the port rather than to an auth API: the screen is not allowed
        // to know what a session is. The graph binds that call to the session coordinator, so
        // this is the tap on the control that did not exist when the header was removed.
        onSignOut = { scope.launch { model.signOut() } },
        // The other half of the same pair. `signOut` can fail — it asks the server to revoke the
        // refresh token family first — and the failure is what raises the popup, so the model's
        // state is read straight through and the two answers are dispatched back to it. The
        // screen owns the wording; the model owns the failure and the choice.
        signOutFailure = model.signOutFailure,
        onForceSignOut = { scope.launch { model.forceSignOut() } },
        onDismissSignOutFailure = { model.dismissSignOutFailure() },
        // Read from the model's observed state and written through `applyUserDetails`, which
        // both updates the state and dispatches to the port — a write that only touched the
        // state would look correct until the next reload put the stored value back.
        userDetails = model.userDetails,
        onUserDetailsChange = { details -> scope.launch { model.applyUserDetails(details) } },
        isAdmin = isAdmin,
        // The two admin rows. The screen reports the tap; where it lands is navigation, so it
        // goes through the router rather than holding a flag here — and through
        // `openAdminMembers`/`openAdminProjects` rather than a `push`, because a section's
        // entrance is the list *and* whatever selection the section was last left on, which is
        // one stack and therefore one transition. See `adminMembersStack` for why the settings
        // screen itself is part of that stack.
        onManageMembers = { router.openAdminMembers() },
        onManageProjects = { router.openAdminProjects() },
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
 * The expanded shell's chrome: a [TTGlassNavigationRail] down the leading edge.
 *
 * The same split as [TabBar] and for the same stated reason: the design system owns the capsule,
 * the lens and the travel, and what is left here is the five [AppTab]s, their labels and their
 * glyphs — the part that is about *this* app. `tabTitle` and `tabIcon` are the same two functions
 * the bar uses, so a sixth tab is a compile error in one place rather than a blank square in two.
 *
 * ## Why it runs the full height of the window, from the top
 *
 * The rail fills the height it is offered and is anchored to the top edge, which is what a desktop
 * sidebar is. It was centred and content-sized first, on the argument that stretching five
 * destinations over a tall window would space them too far apart — and measured, that rail was
 * 216dp of a 768dp window. A glass capsule a quarter of the window's height, floating in the
 * middle of the left edge, does not read as navigation; it reads as a floating capsule, which is
 * what the section set out to replace. The bar's own note about a `768pt-tall wrapper with the
 * tabs parked dead centre at y=363` is about a bar that filled its screen *and centred its tabs*,
 * and the fix there was to stop centring the content, not to refuse the height: the destinations
 * here are laid out from the top, and the trailing space belongs below them.
 *
 * ## Why the tag is on the wrapper
 *
 * The same call [TabBar] makes about its own tag: the wrapper carries the edge inset, and a tag
 * below it would measure the inset rather than the rail. `railInset` is read from *this* node's
 * size, so the two have to agree about what is being measured — the rail and the room it takes,
 * edge inset included, because that is the space the content has to start after.
 */
@Composable
private fun NavigationRail(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = AppTab.entries

    Box(
        modifier = modifier
            // The edge insets are the shell's, not the rail's, for the same reason the bar's are:
            // the component owns the capsule and the caller owns where it sits in the tree.
            //
            // Vertically as well as horizontally, which is the bar's own first documented rule —
            // "it floats" — applied to the other axis. A rail running to the window's top and
            // bottom edges is a full-bleed slab rather than a floating surface, and glass that
            // touches every edge has nothing to float over. The bar gets its bottom inset from the
            // system safe area; the leading edge has no system inset to borrow, so the shell
            // supplies the same number it already uses horizontally and the rail reads as the same
            // kind of object as the bar.
            .padding(
                start = ttSpacing.tabBarEdgeInset,
                top = ttSpacing.tabBarEdgeInset,
                bottom = ttSpacing.tabBarEdgeInset,
            )
            .testTag(NavigationRailTag),
    ) {
        TTGlassNavigationRail(
            destinations = tabs,
            selectedIndex = tabs.indexOf(selected),
            onSelect = onSelect,
            title = { tabTitle(it) },
            icon = { tabIcon(it) },
            destinationModifier = { Modifier.testTag(NavigationRailButtonTag(it)) },
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

/**
 * The navigation rail as a whole, under an expanded layout.
 *
 * Distinct from [TabBarTag] rather than shared with it, because exactly one of the two is composed
 * at any width and the scenarios assert which. A test that asked for a single "the shell's chrome"
 * tag could not tell a phone that had grown a rail from a rail that had lost its bar.
 */
const val NavigationRailTag = "shell.rail"

/** The "choose a project" button on a scoped tab with nothing active. */
const val ScopedChooseProjectTag = "shell.scoped.chooseProject"

/** The editor's container, so a test can assert the route is being rendered. */
const val WorkItemEditorDestinationTag = "shell.workItemEditor"

/** One tab's button, so a test taps a tab by name rather than by position. */
fun TabButtonTag(tab: AppTab): String = "shell.tab.${tab.wireValue}"

/**
 * One destination's button on the rail, so a test taps by name rather than by position.
 *
 * Prefixed `shell.rail.` rather than reusing [TabButtonTag]'s `shell.tab.` because the two tag
 * spaces are both live in a wide shell's semantics tree only if a mistake ever composes both — and
 * `onAllNodesWithTag(TabButtonTag)` should never match a rail. A distinct prefix means such a
 * mistake is a test that finds nothing rather than one that quietly drives the wrong control.
 */
fun NavigationRailButtonTag(tab: AppTab): String = "shell.rail.${tab.wireValue}"

/**
 * The projects admin destination tag, so a test can assert the real screen rendered.
 */
const val AdminProjectsDestinationTag = "shell.admin.projects.destination"
