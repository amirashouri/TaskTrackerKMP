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
                    assignee = "Amir",
                    dueInDays = 3,
                ),
                ProjectSeed.Item(
                    title = "Build the outbox",
                    details = "Every local write queues a row in the same transaction.",
                    status = WorkItemStatus.Done,
                    priority = Priority.High,
                    assignee = "Amir",
                    dueInDays = 5,
                ),
                ProjectSeed.Item(
                    title = "Wire the board to the local store",
                    status = WorkItemStatus.InProgress,
                    priority = Priority.High,
                    assignee = "Amir",
                    dueInDays = 1,
                ),
                ProjectSeed.Item(
                    title = "Show sync state in Settings",
                    details = "Queue depth, dead letters, last failure.",
                    status = WorkItemStatus.InReview,
                    priority = Priority.Medium,
                    assignee = "Ada",
                    dueInDays = 2,
                ),
                ProjectSeed.Item(
                    title = "Pull changes without pushing them back",
                    status = WorkItemStatus.Todo,
                    priority = Priority.Critical,
                    assignee = "Amir",
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
                    assignee = "Ada",
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
                    assignee = "Amir",
                ),
                ProjectSeed.Item(
                    title = "Deep links into a board",
                    status = WorkItemStatus.Done,
                    priority = Priority.Low,
                    assignee = "Amir",
                ),
                ProjectSeed.Item(
                    title = "Push notifications for assignments",
                    status = WorkItemStatus.Backlog,
                    priority = Priority.Lowest,
                ),
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
) {

    /**
     * One seeded work item.
     *
     * `details` and `assignee` default to empty rather than being required, because most
     * items on a real board have neither, and a fixture that has to spell out `details = ""`
     * thirteen times stops being readable as a board.
     *
     * @param status the column the item lands in. Terminal statuses are dated by the seeder,
     *   because the archive files by resolution date and an undated `Done` item is invisible
     *   in it.
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
        val assignee: String? = null,
        val dueInDays: Int? = null,
        val rank: Int = 0,
    )
}
