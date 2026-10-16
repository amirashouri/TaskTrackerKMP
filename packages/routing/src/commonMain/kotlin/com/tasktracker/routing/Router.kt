package com.tasktracker.routing

import com.tasktracker.core.LogPrivacy
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TTLogger
import com.tasktracker.core.UserID
import com.tasktracker.core.logMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All of the router's state in one immutable value.
 *
 * A field per navigation concern would have been the more direct translation of the
 * original's stored properties, but the router's transitions change several at once —
 * [Router.push] moves the tab, the stack and the active project in a single step. With
 * separate mutable properties a reader could observe a tab switched to a project whose
 * board had not been installed yet, which is exactly the kind of half-applied state
 * that renders the wrong screen with no way to tell from the outside. Publishing one
 * [RouterState] through one [StateFlow] makes each transition atomic, and leaves the
 * UI with a single thing to collect.
 */
data class RouterState(
    val root: RootDestination = RootDestination.Auth,
    val isRestoringSession: Boolean = true,
    val selectedTab: AppTab = AppTab.Projects,
    val projectsPath: List<AppRoute> = emptyList(),
    val boardPath: List<AppRoute> = emptyList(),
    val backlogPath: List<AppRoute> = emptyList(),
    val sprintsPath: List<AppRoute> = emptyList(),
    val settingsPath: List<AppRoute> = emptyList(),
    val activeProjectID: ProjectID? = null,
) {
    /**
     * The navigation stack belonging to [onTab].
     *
     * One field per tab rather than a `Map<AppTab, List<AppRoute>>`, and the reason is that
     * a map needs a key for every tab before any of them has been visited — so the state
     * either grows a null per unused tab or a `getOrDefault(emptyList())` at every read,
     * and both put "what is the stack for a tab nobody has opened" into the type instead of
     * leaving it as the empty list it actually is. The cost is that a new tab is a compile
     * error here, which is the price worth paying.
     */
    fun pathFor(tab: AppTab): List<AppRoute> = when (tab) {
        AppTab.Projects -> projectsPath
        AppTab.Board -> boardPath
        AppTab.Backlog -> backlogPath
        AppTab.Sprints -> sprintsPath
        AppTab.Settings -> settingsPath
    }
}

/**
 * Owns navigation state for the whole app.
 *
 * `root` is private to this class by construction rather than by convention — the
 * original documented "only `SessionCoordinator` may assign this" on a `private(set)`
 * var, which a same-module caller could still reach into. Here there is no setter at
 * all, so the only way in is a transition on this class.
 *
 * Free of any UI dependency by design: navigation state is a domain-ish concern and
 * stays unit-testable without a view. The original's `@Observable` gets replaced by
 * the [state] flow, which presentation collects.
 */
class Router {
    private val _state = MutableStateFlow(RouterState())

    /** The current state, and the only thing the UI needs to observe. */
    val state: StateFlow<RouterState> = _state.asStateFlow()

    /**
     * The key the app persists the active project under, so a relaunch restores the
     * Board and Backlog tabs onto the project the user was last in. Named here rather
     * than in the app because the router owns the concept.
     */
    val lastProjectDefaultsKey: String = "routing.lastVisitedProjectID"

    /** The session split. Only the session layer may move this, via [enterMain]/[enterAuth]. */
    val root: RootDestination get() = _state.value.root

    /**
     * Whether the session is still being read.
     *
     * Starts `true`, because at launch nothing has answered yet, and both terminal
     * transitions clear it. It exists because [root] cannot tell "no session" apart from
     * "not asked yet" — both are [RootDestination.Auth] — and the app renders its launch
     * screen for as long as this is `true`. Without it, a relaunch with a valid session
     * shows the sign-in form for a beat before the shell replaces it.
     */
    val isRestoringSession: Boolean get() = _state.value.isRestoringSession

    val selectedTab: AppTab get() = _state.value.selectedTab

    val activeProjectID: ProjectID? get() = _state.value.activeProjectID

    // MARK: - Session transitions

    /**
     * The session is being read. The counterpart to the two `enter…` calls: a read that
     * has started and not yet landed is a state the launch screen needs to see, and only
     * the session layer knows when it begins.
     */
    fun beginRestoringSession() = update { it.copy(isRestoringSession = true) }

    /**
     * Moves to the signed-in area and clears any navigation left over from the previous
     * session.
     */
    fun enterMain() = update { state ->
        // Cleared before the guard: this answers "has the session been read", which is
        // true whether or not this particular call is the one that moves the screen.
        if (state.root == RootDestination.Main) {
            state.copy(isRestoringSession = false)
        } else {
            logNotice("routing -> main")
            state.clearedNavigation().copy(root = RootDestination.Main, isRestoringSession = false)
        }
    }

    /**
     * Sends the user back to sign-in and wipes every trace of the previous session from
     * the navigation stack.
     *
     * The active project is deliberately kept: it belongs to the device, not to the
     * session, and a sign-out-then-in should land the tabs where the user last was — the
     * same reason the app persists it across launches.
     */
    fun enterAuth() = update { state ->
        if (state.root == RootDestination.Auth) {
            state.copy(isRestoringSession = false)
        } else {
            logNotice("routing -> auth")
            state.clearedNavigation().copy(
                root = RootDestination.Auth,
                isRestoringSession = false,
                selectedTab = AppTab.Projects,
            )
        }
    }

    // MARK: - Project selection

    /**
     * Makes a project the one the Board and Backlog tabs show, and returns to its root.
     *
     * One entry point for every surface that opens a project — a project row, a deep
     * link, a "switch project" control — so the tab roots, the stacks and the
     * last-visited bookkeeping cannot drift apart.
     *
     * On the Projects tab the board is *pushed*, because the dashboard's job is to open
     * things in place. On Board or Backlog the tab *switches*, because those tabs are
     * already scoped to the active project — there is nothing to push onto an empty
     * stack whose root is the screen itself.
     *
     * Settings is rejected outright: it is not project-scoped, so "open this project"
     * has no meaning there and silently ignoring it is better than a half-apply.
     */
    fun openProject(projectID: ProjectID, inTab: AppTab? = null) = update { state ->
        when (val target = inTab ?: state.selectedTab) {
            AppTab.Projects -> state.copy(activeProjectID = projectID)
                .withPath(AppTab.Projects, listOf(AppRoute.Board(projectID)))

            AppTab.Board -> state.copy(activeProjectID = projectID, selectedTab = AppTab.Board)
                .withPath(AppTab.Board, emptyList())

            AppTab.Backlog -> state.copy(activeProjectID = projectID, selectedTab = AppTab.Backlog)
                .withPath(AppTab.Backlog, emptyList())

            // Switches, like Board and Backlog, and lands on the list rather than on a
            // particular sprint: choosing a project says *which* project, never *which*
            // sprint inside it.
            //
            // The root is named as a **route** rather than left empty, which is what
            // `tasktracker://sprints/<pid>` installs too. The reason is state, not pixels:
            // nothing in this app has a back affordance — `NavigationHost` registers no
            // `BackHandler` and `SprintDetailScreen` has no back button, so the tab bar is
            // how every project-scoped screen is left — and the shell renders a tab root and
            // a pushed root identically. So an empty stack here would render the same
            // screen while describing a different history from the one a link describes, and
            // [openSprint] and [DeepLinkParser] would then be free to drift apart. One
            // definition ([sprintListStack]) for every way in is cheaper than reasoning about
            // which paths happen to look alike.
            AppTab.Sprints -> state.copy(activeProjectID = projectID, selectedTab = AppTab.Sprints)
                .withPath(AppTab.Sprints, sprintListStack(projectID))

            AppTab.Settings -> state
        }
    }

    // MARK: - Navigation

    /** The navigation stack belonging to [onTab]. */
    fun path(onTab: AppTab): List<AppRoute> = _state.value.pathFor(onTab)

    /**
     * Switches tabs, and nothing else.
     *
     * The original's `selectedTab` was a public `var` with a `didSet` that logged, so the
     * only rule it could state was "don't log a no-op". Here the field has no setter at all,
     * which makes this the *only* way to move between tabs — and that is the property worth
     * having: a tab bar that wrote the field could also write it while wiping a stack or
     * changing the active project, which is how a tab and its stack drift apart.
     *
     * Deliberately does not touch any stack or [activeProjectID]. Flipping tabs is a
     * question about *which* surface, not about what is on it: Board and Backlog are scoped
     * to the shared active project precisely so that switching between them lands on the same
     * work, and switching to Projects must leave the board the user pushed still on the
     * Projects stack for when they switch back.
     *
     * A no-op switch returns without touching the flow, so `distinctUntilChanged` upstream
     * of a tab switch does not see an emission that says nothing.
     */
    fun selectTab(tab: AppTab) = update { state ->
        if (state.selectedTab == tab) return@update state
        logDebug("tab -> ${tab.wireValue}")
        state.copy(selectedTab = tab)
    }

    /** Replaces [onTab]'s stack wholesale. Used by the shell to restore persisted state. */
    fun setPath(path: List<AppRoute>, onTab: AppTab) = update { it.withPath(onTab, path) }

    /**
     * Opens one sprint, and the list it sits on, in a single transition.
     *
     * Exists because the two ways into a sprint — tapping a row and following
     * `tasktracker://sprint/<pid>/<sid>` — had each grown their own stack, and they
     * disagreed: the link installed `[SprintList, SprintDetail]` while the tap pushed
     * `SprintDetail` onto whatever was there, which after a tab switch is nothing. The
     * rendering was identical either way, so the drift was invisible until a test asserted
     * the stack. [DeepLinkParser] now builds its link from the same [sprintStack] function,
     * so the two cannot drift again without both changing.
     *
     * One `update` rather than a `push` of the list followed by a `push` of the detail,
     * because [RouterState] publishes through a single [StateFlow] precisely so that a
     * transition moving several fields at once is never observable half-applied — and two
     * pushes would publish a state in which the list is a *pushed* route for one frame.
     */
    fun openSprint(projectID: ProjectID, sprintID: SprintID) = update { state ->
        state.copy(
            selectedTab = AppTab.Sprints,
            // `AppRoute.SprintDetail.selectsActiveProject` is `projectID`, and the tab is not
            // Settings, so this is the same assignment `push` would have made.
            activeProjectID = projectID,
        ).withPath(AppTab.Sprints, sprintStack(projectID, sprintID))
    }

    // MARK: - Admin sections

    /**
     * Opens the members administration section, or one member inside it, in a single
     * transition.
     *
     * One entry point for both the list and a selection, exactly as [openSprint] is for a
     * sprint list and a sprint, and for the same reason: the row tap and the shell's
     * two-pane selection have to install the *same* stack or the wide and narrow
     * arrangements disagree about what is behind the screen the user is looking at. A
     * `push` of the selection would grow the stack by one per member visited — the drift
     * `openSprint` exists to remove, and here it would also mean a wide window's left pane
     * showed the first member selected rather than the list.
     *
     * A `null` [memberID] with no section open is the list; a non-null one is that member
     * with the list beneath it. A `null` one *with* a member open is the selection being
     * cleared, so it returns to the list rather than being ignored.
     *
     * The active project is deliberately untouched: the admin sections are not
     * project-scoped, so entering them must not move the Board and Backlog tabs — the same
     * rule [AppRoute.WorkItemEditor] follows.
     */
    fun openAdminMembers(memberID: UserID? = null) = update { state ->
        state.copy(selectedTab = AppTab.Settings)
            .withPath(AppTab.Settings, adminMembersStack(memberID))
    }

    /**
     * Opens the projects administration section, or one project inside it.
     *
     * Same shape and same reasoning as [openAdminMembers], including why the active project
     * does not move: this manages a project's settings, which is not the same act as
     * showing that project's work.
     */
    fun openAdminProjects(projectID: ProjectID? = null) = update { state ->
        state.copy(selectedTab = AppTab.Settings)
            .withPath(AppTab.Settings, adminProjectsStack(projectID))
    }

    /** Pushes onto [onTab], defaulting to the selected tab; pops if already on top. */
    fun push(route: AppRoute, onTab: AppTab? = null) = update { state ->
        val target = onTab ?: state.selectedTab
        val current = state.pathFor(target)
        // Tapping the same row twice should go back, not stack duplicates.
        val next = if (current.lastOrNull() == route) {
            if (current.size <= 1) return@update state
            current.dropLast(1)
        } else {
            current + route
        }
        state.copy(
            selectedTab = target,
            activeProjectID = route.selectsActiveProject?.takeIf { target != AppTab.Settings }
                ?: state.activeProjectID,
        ).withPath(target, next)
    }

    /** Returns [tab] to its root, discarding whatever was pushed onto it. */
    fun popToRoot(onTab: AppTab? = null) = update { state ->
        state.withPath(onTab ?: state.selectedTab, emptyList())
    }

    /**
     * The project the on-screen tab is scoped to: an explicit route if the stack has
     * one, otherwise the shared active project that scopes the Board and Backlog tabs.
     */
    val currentProjectID: ProjectID?
        get() {
            val state = _state.value
            state.pathFor(state.selectedTab).asReversed().forEach { route ->
                route.projectIDOrNull?.let { return it }
            }
            return state.activeProjectID
        }

    // MARK: - Deep links

    /**
     * Applies a deep link. Returns `false` for an unrecognised or out-of-session link,
     * which the caller should treat as "ignore".
     */
    fun open(url: String): Boolean {
        val link = DeepLinkParser.route(url) ?: run {
            TTLogger.routing.warning {
                logMessage {
                    append("routing: ignoring unrecognised link ")
                    append(url, LogPrivacy.Public)
                }
            }
            return false
        }
        // A link must not be a way around the auth gate.
        if (_state.value.root != RootDestination.Main) {
            TTLogger.routing.warning { logMessage { append("routing: ignoring link while signed out") } }
            return false
        }
        update { state ->
            state.copy(
                selectedTab = link.tab,
                activeProjectID = link.path.firstOrNull()?.selectsActiveProject ?: state.activeProjectID,
            ).withPath(link.tab, link.path)
        }
        logNotice("routing: opened deep link")
        return true
    }

    /**
     * Restoration hook, called at launch and again when a session begins, so a relaunch
     * returns the user to the project they left rather than the dashboard.
     *
     * [isValid] is the app's chance to veto: a project deleted on another device while
     * this one was away must not come back as the default, or every board visit starts
     * with a fetch that finds nothing. It answers `Boolean?` because existence is a store
     * question and the read itself can fail: `true` restores, `false` drops the stored id,
     * and `null` — a store that could not answer — keeps the id and skips the restore,
     * which is the safe outcome for data that is merely unreadable rather than gone.
     */
    suspend fun restoreLastVisitedProject(
        projectID: ProjectID?,
        isValid: (suspend (ProjectID) -> Boolean?)? = null,
    ) {
        if (_state.value.root != RootDestination.Main) return
        val projectID = projectID ?: return
        if (isValid != null && isValid(projectID) != true) return
        update { it.copy(activeProjectID = projectID) }
    }

    // MARK: - Plumbing

    /**
     * The single mutation point. Routing is main-thread-confined by design — the
     * original's `@MainActor` — and a `compareAndSet` loop keeps that from being merely
     * conventional: two callers racing from different threads resolve to one state, with
     * no transition reading a value another one had already replaced.
     */
    private inline fun update(transform: (RouterState) -> RouterState) {
        while (true) {
            val current = _state.value
            val next = transform(current)
            if (next == current) return
            if (_state.compareAndSet(current, next)) return
        }
    }

    private fun RouterState.clearedNavigation(): RouterState = copy(
        projectsPath = emptyList(),
        boardPath = emptyList(),
        backlogPath = emptyList(),
        sprintsPath = emptyList(),
        settingsPath = emptyList(),
    )

    private fun RouterState.withPath(tab: AppTab, path: List<AppRoute>): RouterState = when (tab) {
        AppTab.Projects -> copy(projectsPath = path)
        AppTab.Board -> copy(boardPath = path)
        AppTab.Backlog -> copy(backlogPath = path)
        AppTab.Sprints -> copy(sprintsPath = path)
        AppTab.Settings -> copy(settingsPath = path)
    }

    /** A tab switch is routine navigation rather than a session boundary, so it is a debug. */
    private fun logDebug(text: String) {
        TTLogger.routing.debug { logMessage { append(text) } }
    }

    private fun logNotice(text: String) {
        TTLogger.routing.info { logMessage { append(text) } }
    }
}

/**
 * The Sprints tab's root stack for [projectID]: the list, and nothing on top of it.
 *
 * Shared by [Router.openProject] and [DeepLinkParser] so that choosing a project and
 * following `tasktracker://sprints/<pid>` produce the same state.
 */
fun sprintListStack(projectID: ProjectID): List<AppRoute> = listOf(AppRoute.SprintList(projectID))

/**
 * The Sprints tab's stack for one open sprint: [sprintListStack] with the detail on top.
 *
 * One definition for [Router.openSprint] and for the `sprint/<pid>/<sid>` link. The two
 * disagreed until a shell test asserted the stack after a tap, which is the only reason
 * to trust a single source here: the shell renders a tab root and a pushed root
 * identically, so nothing about the pixels would ever have shown the drift.
 */
fun sprintStack(projectID: ProjectID, sprintID: SprintID): List<AppRoute> =
    sprintListStack(projectID) + AppRoute.SprintDetail(projectID, sprintID)

/**
 * The Settings tab's stack for the members administration section: the settings screen, the
 * list, and the member being edited if there is one.
 *
 * ## Why the settings screen is *in* the stack
 *
 * This is the one place the admin sections differ from sprints, and it is forced by where
 * the tab root sits. The Sprints tab's root is the sprint list, so a list with nothing
 * pushed onto it is still a screen with a tab to leave by. The Settings tab's root is the
 * settings screen, and an admin list pushed onto an *empty* stack would be the bottom of
 * its own stack: system back would be disabled there (the shell enables it only above depth
 * one) and the Settings tab button would be a no-op because it is already selected — so the
 * only way out of the section would be a different tab, and coming back would land in the
 * admin list again. Naming the settings screen makes back from the list a pop to the screen
 * the section was opened from.
 *
 * `AppRoute.Settings` is already a legitimate element of this tab's stack: a
 * `tasktracker://settings` link installs exactly that, so the shell renders the settings
 * screen from a pushed route and from the tab root identically.
 *
 * ## Why one function for three stacks
 *
 * The list, a selection and a cleared selection are the same question asked three times, and
 * the growth the two-pane arrangement depends on — a selection replaces rather than stacks —
 * is a property of this function rather than of any caller. [Router.openAdminMembers] is its
 * only writer, so the two cannot drift.
 */
fun adminMembersStack(memberID: UserID? = null): List<AppRoute> =
    listOf(AppRoute.Settings, AppRoute.AdminMembers()) +
        if (memberID == null) {
            emptyList()
        } else {
            listOf(AppRoute.AdminMembers(memberID))
        }

/**
 * The Settings tab's stack for the projects administration section.
 *
 * The same shape as [adminMembersStack], and the same argument for it — see that function's
 * note rather than a second copy here.
 */
fun adminProjectsStack(projectID: ProjectID? = null): List<AppRoute> =
    listOf(AppRoute.Settings, AppRoute.AdminProjects()) +
        if (projectID == null) {
            emptyList()
        } else {
            listOf(AppRoute.AdminProjects(projectID))
        }
