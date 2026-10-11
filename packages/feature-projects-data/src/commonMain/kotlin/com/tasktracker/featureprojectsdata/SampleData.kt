package com.tasktracker.featureprojectsdata

import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus

/**
 * The board a first launch starts with.
 *
 * ## Relative due dates, not fixed ones
 *
 * [ProjectSeed.Item.dueInDays] is an offset rather than a date, and it has to be: a sample
 * board with hard-coded due dates is a wall of overdue cards the moment the app has been
 * installed for a week, which teaches a new user that "overdue" is noise before they have
 * written a single item of their own.
 *
 * The offsets are mostly positive and a couple are near, so the seeded board shows a due-date
 * badge in a plausible state rather than a uniform one.
 *
 * ## A value, not a function of a clock
 *
 * [projects] is a pure function of nothing. The offsets are applied by
 * [RoomProjectRepository.seed], which has a `now` from its caller. A `SampleData.seeds(now)`
 * signature would put the arithmetic in two places and make the fixture's shape depend on the
 * instant, which is exactly the kind of coupling a fixture exists to avoid.
 */
object SampleData {

    /**
     * Two projects, so the dashboard, the deep links and the project switcher have more than
     * one thing to point at — a single-project sample board makes every list, picker and
     * navigation test look like it has passed when it has only proved the single-item case.
     */
    fun projects(): List<ProjectSeed> = listOf(
        ProjectSeed(
            keyPrefix = "APO",
            name = "Apollo",
            details = "The first vertical slice: local-first edits with an outbox behind them.",
            items = listOf(
                ProjectSeed.Item(
                    title = "Decide the sync conflict policy",
                    details = "Server wins. The client replays its last local state and lets the server decide.",
                    status = WorkItemStatus.Done,
                    priority = Priority.Critical,
                    assignee = true,
                    dueInDays = 3,
                ),
                ProjectSeed.Item(
                    title = "Build the outbox",
                    details = "Every local write queues a row in the same transaction.",
                    status = WorkItemStatus.Done,
                    priority = Priority.High,
                    assignee = true,
                    dueInDays = 5,
                ),
                ProjectSeed.Item(
                    title = "Wire the board to the local store",
                    status = WorkItemStatus.InProgress,
                    priority = Priority.High,
                    assignee = true,
                    dueInDays = 1,
                ),
                ProjectSeed.Item(
                    title = "Show sync state in Settings",
                    details = "Queue depth, dead letters, last failure.",
                    status = WorkItemStatus.InReview,
                    priority = Priority.Medium,
                    assignee = true,
                    dueInDays = 2,
                ),
                ProjectSeed.Item(
                    title = "Pull changes without pushing them back",
                    status = WorkItemStatus.Todo,
                    priority = Priority.Critical,
                    assignee = true,
                    dueInDays = 1,
                ),
                ProjectSeed.Item(
                    title = "Add a real sign-in flow",
                    details = "Token vault, refresh, and a delegate for the auth interceptor.",
                    status = WorkItemStatus.Backlog,
                    priority = Priority.High,
                ),
                ProjectSeed.Item(
                    title = "Support offline work item creation",
                    status = WorkItemStatus.Backlog,
                    priority = Priority.Medium,
                ),
                ProjectSeed.Item(
                    title = "Add project archiving",
                    status = WorkItemStatus.Backlog,
                    priority = Priority.Lowest,
                ),
                ProjectSeed.Item("Review sync retry behavior", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Add conflict resolution history", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Improve offline error messaging", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add project member roles", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Support task labels", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add a task activity timeline", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Show the last successful sync time", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Add bulk task selection", status = WorkItemStatus.Todo, priority = Priority.Lowest),
                ProjectSeed.Item("Allow projects to be reordered", status = WorkItemStatus.Todo, priority = Priority.Lowest),
                ProjectSeed.Item("Add an account recovery flow", status = WorkItemStatus.Todo, priority = Priority.High),
            ),
        ),
        ProjectSeed(
            keyPrefix = "GEM",
            name = "Gemini",
            details = "A second project, so the dashboard and deep links have something to point at.",
            items = listOf(
                ProjectSeed.Item(
                    title = "Keychain-backed token storage",
                    status = WorkItemStatus.InProgress,
                    priority = Priority.High,
                    assignee = true,
                    dueInDays = 4,
                ),
                ProjectSeed.Item(
                    title = "One forced refresh after a 401",
                    details = "Then one replay. A second 401 ends the session.",
                    status = WorkItemStatus.Todo,
                    priority = Priority.Critical,
                    dueInDays = 2,
                ),
                ProjectSeed.Item(
                    title = "Backlog view with a stable order",
                    status = WorkItemStatus.InReview,
                    priority = Priority.Medium,
                    assignee = true,
                ),
                ProjectSeed.Item(
                    title = "Deep links into a board",
                    status = WorkItemStatus.Done,
                    priority = Priority.Low,
                    assignee = true,
                ),
                ProjectSeed.Item(
                    title = "Push notifications for assignments",
                    status = WorkItemStatus.Backlog,
                    priority = Priority.Lowest,
                ),
                ProjectSeed.Item("Add a notification preferences screen", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Group notifications by project", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add quiet hours", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Support notification deep links", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Show unread notification count", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add task due date reminders", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Let users mute a project", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Add notification delivery history", status = WorkItemStatus.Todo, priority = Priority.Lowest),
                ProjectSeed.Item("Handle revoked notification permission", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add an in-app notification inbox", status = WorkItemStatus.Todo, priority = Priority.High),
            ),
            // One sprint per status, and every window disjoint from every other — no two share
            // an instant regardless of status. The finished/archived flags alone would already
            // keep the stores from refusing each other, but a fixture whose finished sprints
            // overlap teaches that overlap is only a live-sprint problem; disjoint windows keep
            // the seeded history unambiguous too. Offsets, not dates, so the statuses hold at
            // any install time: InProgress always contains the seed instant.
            sprints = listOf(
                ProjectSeed.SprintSeed(
                    name = "Gemini Sprint 3",
                    goalStoryPoints = 13,
                    goal = "Notification inbox and preferences.",
                    startsInDays = -7,
                    endsInDays = 7,
                    owner = true,
                ),
                ProjectSeed.SprintSeed(
                    name = "Gemini Sprint 4",
                    goalStoryPoints = 8,
                    goal = "Quiet hours and reminders.",
                    startsInDays = 30,
                    endsInDays = 44,
                    owner = true,
                ),
                ProjectSeed.SprintSeed(
                    name = "Gemini Sprint 2",
                    goalStoryPoints = 10,
                    startsInDays = -44,
                    endsInDays = -30,
                    isFinished = true,
                ),
                ProjectSeed.SprintSeed(
                    name = "Gemini Sprint 1",
                    goalStoryPoints = 5,
                    startsInDays = -74,
                    endsInDays = -60,
                    isFinished = true,
                    isArchived = true,
                ),
            ),
        ),
        ProjectSeed(
            keyPrefix = "ORI",
            name = "Orion",
            details = "A mobile experience project for planning, polish, and accessibility.",
            items = listOf(
                ProjectSeed.Item("Audit screen reader labels", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Improve empty state guidance", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add tablet board layout", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Tune sheet keyboard behavior", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Add dynamic type coverage", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Review dark theme contrast", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Add haptic feedback for task moves", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Improve project switching animation", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Add reduced motion support", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Polish loading placeholders", status = WorkItemStatus.Todo, priority = Priority.Lowest),
            ),
        ),
        ProjectSeed(
            keyPrefix = "ATL",
            name = "Atlas",
            details = "A workspace for search, organization, and project insights.",
            items = listOf(
                ProjectSeed.Item("Add search across all projects", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Filter tasks by assignee", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Add priority filters", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Sort tasks by due date", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Show project task counts", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Add project activity summary", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Support task labels in search", status = WorkItemStatus.Todo, priority = Priority.Medium),
                ProjectSeed.Item("Remember the selected board filter", status = WorkItemStatus.Todo, priority = Priority.Low),
                ProjectSeed.Item("Add overdue task filter", status = WorkItemStatus.Todo, priority = Priority.High),
                ProjectSeed.Item("Add a recently viewed projects section", status = WorkItemStatus.Todo, priority = Priority.Lowest),
            ),
        ),
    )
}

/**
 * One project to seed, and the items filed under it.
 *
 * No ids and no timestamps: both are generated at insert time, and baking them into the
 * fixture would mean a seeded board that looks identical on two devices — which is precisely
 * the situation the local-first design has to be tested in.
 *
 * @param keyPrefix the project key, e.g. `APO` produces `APO-1`, `APO-2`.
 * @param items seeded in list order, and the position in this list is the number in the
 *   item's key — so the third entry is `APO-3` and a test can assert on key numbering without
 *   counting rows.
 */
class ProjectSeed(
    val keyPrefix: String,
    val name: String,
    val details: String = "",
    val items: List<Item> = emptyList(),
    val sprints: List<SprintSeed> = emptyList(),
) {

    /**
     * One seeded work item.
     *
     * `details` defaults to empty rather than being required, because most items on a real
     * board have neither, and a fixture that has to spell out `details = ""` thirteen times
     * stops being readable as a board.
     *
     * @param status the column the item lands in. Terminal statuses are dated by the seeder,
     *   because the archive files by resolution date and an undated `Done` item is invisible
     *   in it.
     * @param assignee seeds the item to the *signed-in user* when true, and leaves it
     *   unassigned when false. A flag and not a name, because a sample name cannot resolve
     *   to a `UserID` — the sample board is the user's own board, so "mine" is the only
     *   assignment a fixture can honestly make until a member directory exists.
     * @param dueInDays days from the seeding instant, or `null` for no due date — which is
     *   the majority, and a board where everything has a due date is its own kind of noise.
     * @param rank position within its own column, which is why every seeded item leaves this
     *   at 0. They all share a rank on purpose: it is the case the read path's tie-break
     *   exists for, and a fixture that gave each card its own rank would never exercise it.
     */
    class Item(
        val title: String,
        val details: String = "",
        val status: WorkItemStatus = WorkItemStatus.Todo,
        val priority: Priority = Priority.Medium,
        val assignee: Boolean = false,
        val dueInDays: Int? = null,
        val rank: Int = 0,
    )

    /**
     * One seeded sprint.
     *
     * Windows are day offsets from the seeding instant, for the same reason
     * [Item.dueInDays] is: a fixture with fixed dates has a different status every month it
     * is installed in. Offsets keep the seeded statuses stable — the InProgress window always
     * contains the seed instant, the Scheduled one always starts after it, and the two
     * finished ones always ended before it.
     *
     * @param startsInDays first day of the window, relative to the seeding instant.
     * @param endsInDays last day, inclusive, relative to the seeding instant.
     * @param owner seeds the sprint to the *signed-in user* when true — the same rule as
     *   [Item.assignee], and for the same reason.
     */
    class SprintSeed(
        val name: String,
        val goalStoryPoints: Int = 0,
        val goal: String? = null,
        val startsInDays: Int,
        val endsInDays: Int,
        val owner: Boolean = false,
        val isFinished: Boolean = false,
        val isArchived: Boolean = false,
    )
}
