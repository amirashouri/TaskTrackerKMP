package com.tasktracker.routing

import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItemStatus

/**
 * The top-level split. A session is in exactly one of these, and only the session
 * layer is allowed to change it.
 */
enum class RootDestination {
    Auth,
    Main,
}

/**
 * The signed-in shell's tabs.
 *
 * Board and Backlog are top-level tabs, not pushes off the Projects stack, because
 * they are the two surfaces a user switches between dozens of times a day; burying
 * one of them under two taps per switch is the navigation mistake this shape exists
 * to fix. Both show the *active* project — the last one the user opened — so a single
 * tap lands on the same board they left, from anywhere in the app.
 *
 * [wireValue] rather than the original's `rawValue`: a Swift enum's raw value was
 * never load-bearing, but here the string is the thing presentation will switch on to
 * look up a label, and naming it makes that role explicit. The original also carried
 * `title` and `systemImage`, which this omits on purpose — see [AppRoute]'s note.
 */
enum class AppTab(val wireValue: String) {
    Projects("projects"),
    Board("board"),
    Backlog("backlog"),
    Sprints("sprints"),
    Settings("settings"),
    ;

    /**
     * The tabs that show one project's view, driven by the router's active project.
     *
     * Kept as a `when` over the enum rather than a `Set<AppTab>` member: adding a tab
     * then leaves a compile error here instead of silently defaulting to false in a
     * property initialiser that no test would catch.
     */
    val isProjectScoped: Boolean
        get() = when (this) {
            Board, Backlog, Sprints -> true
            Projects, Settings -> false
        }

    companion object {
        fun fromWireValue(value: String): AppTab? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * A destination inside the signed-in area.
 *
 * Sealed so presentation can `when` over it exhaustively — a new destination becomes
 * a compile error at every screen that switches on it, which is the check the
 * original got from Swift's compiler. Presentation will also need a label for each
 * case; the original had `systemImage` here, but it belongs in presentation beside
 * the other resources, not in a module that may not depend on the design system.
 */
sealed interface AppRoute {
    data class Board(val projectID: ProjectID) : AppRoute

    data class Backlog(val projectID: ProjectID) : AppRoute

    /** The month-grouped history of a project's finished and closed work. */
    data class Archive(val projectID: ProjectID) : AppRoute

    /**
     * A project's sprints, as the Sprints tab's own root.
     *
     * A route rather than tab-only, for the same reason [Board] and [Backlog] are: the shell
     * renders a tab root and a pushed root identically, so naming the root is what keeps the
     * stack a tab switch, a link and a tapped row all produce the same one. See [sprintStack].
     */
    data class SprintList(val projectID: ProjectID) : AppRoute

    /** One sprint: its window, its tasks, and the summary a finished one is read through. */
    data class SprintDetail(val projectID: ProjectID, val sprintID: SprintID) : AppRoute

    data class WorkItemEditor(
        val projectID: ProjectID,
        val workItemID: WorkItemID?,
        val initialStatus: WorkItemStatus? = null,
        val initialSprintID: SprintID? = null,
    ) : AppRoute

    data object Settings : AppRoute

    /**
     * The members administration section, as a route on the Settings stack.
     *
     * ## Why there is no member in this route
     *
     * There is no member detail screen: the section is a list whose rows carry delete and
     * reactivate, plus a create form. It carried a `memberID` until 2026-10-17 (`D6` in
     * `docs/PLAN.md`), and nothing ever pushed a non-null one — a route parameter no caller can
     * set is a claim that a screen exists behind it, and the wide arrangement spent that claim
     * on an empty right pane.
     *
     * The stack is still `[Settings, AdminMembers]` rather than the list being the tab root, for
     * the reason [adminMembersStack] gives.
     */
    data object AdminMembers : AppRoute

    /**
     * The projects administration section, in the same shape and for the same reasons as
     * [AdminMembers] was: `null` is the active/archived list, non-null is one project's editor.
     */
    data class AdminProjects(val projectID: ProjectID? = null) : AppRoute
}

/** The project a route is scoped to, or null for [AppRoute.Settings] and the admin sections. */
val AppRoute.projectIDOrNull: ProjectID?
    get() = when (this) {
        is AppRoute.Board -> projectID
        is AppRoute.Backlog -> projectID
        is AppRoute.Archive -> projectID
        is AppRoute.SprintList -> projectID
        is AppRoute.SprintDetail -> projectID
        is AppRoute.WorkItemEditor -> projectID
        // Admin sections are not project-scoped even though one names a project: managing a
        // project's settings is not showing that project's work, and `AdminProjects(projectID)`
        // must not become the active project any more than `WorkItemEditor` does.
        is AppRoute.AdminProjects, AppRoute.Settings, AppRoute.AdminMembers -> null
    }

/**
 * The project this route makes *current*, or null if opening it changes nothing.
 *
 * Deliberately narrower than [projectIDOrNull], and the difference is load-bearing:
 * [WorkItemEditor] is project-scoped but must not become the active project. Opening
 * the editor for a project the user has not entered — a deep link to an item in
 * another project, or a search result the Projects list opened — scopes the screen
 * without switching which project's board the tabs show. Collapsing the two into one
 * accessor is the easy mistake, and it would silently move the Board and Backlog tabs
 * out from under the user.
 *
 * **[SprintList] and [SprintDetail] both select**, and not by accident. A sprints tab that
 * did not become the active project would show nothing at all — the screen reads the
 * router's active project, and a deep link that arrived before any project had been opened
 * would leave it with `null` to render. So a link into a sprint is also a decision about
 * which project the other project-scoped tabs are showing, exactly as [Archive] is.
 */
val AppRoute.selectsActiveProject: ProjectID?
    get() = when (this) {
        is AppRoute.Board -> projectID
        is AppRoute.Backlog -> projectID
        is AppRoute.Archive -> projectID
        is AppRoute.SprintList -> projectID
        is AppRoute.SprintDetail -> projectID
        is AppRoute.WorkItemEditor, AppRoute.Settings, is AppRoute.AdminMembers,
        is AppRoute.AdminProjects,
        -> null
    }

/**
 * A parsed deep link, before the router decides whether the session permits applying it.
 */
data class ParsedLink(
    val tab: AppTab,
    /** Destination to present modally (a sheet), or `null` for a plain push. */
    val route: AppRoute?,
    /** The navigation stack to install before presenting [route]. */
    val path: List<AppRoute>,
)

/** Custom URL scheme registered in the iOS `Info.plist` and the Android manifest. */
object DeepLinkScheme {
    const val TASK_TRACKER: String = "tasktracker"
}
