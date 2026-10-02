package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The port through which the projects feature reads and writes its data.
 *
 * Declared in the domain layer and implemented in the data layer, so the dependency
 * points inwards. Presentation depends on this interface and never on the Room
 * implementation, which is what stops an `@Entity` from leaking into a view — and in a
 * multiplatform port that is enforced rather than merely intended: a
 * `ProjectRecordEntity` is in `:packages:database`, which presentation is not allowed to
 * depend on at all, so a signature written here could not name one even by accident.
 *
 * Two things are deliberate about the shape:
 *
 * 1. **Every value crossing this interface is immutable.** A Room entity is mutable and
 *    the `ModelContext` equivalent explicitly refuses to be shared, so a record can never
 *    appear in a signature here. Domain values in, domain values out.
 * 2. **Writes return the value they produced.** A caller that creates a work item gets
 *    the stored version back, including the per-project key it was issued, rather than
 *    having to re-read to find out what it got. The alternative — a `suspend fun
 *    createWorkItem(…): Unit` — makes every caller a second round trip and lets the
 *    number it displays drift from the number it holds.
 *
 * `now` is a parameter on every write rather than something the implementation reads from
 * a clock. A store that calls `Instant.now()` itself is a store whose tests depend on
 * wall-clock time, and a store that takes the instant from its caller can be exercised
 * with a fixed one — which is what makes the `updatedAt` assertions in
 * `:packages:database` possible at all.
 */
interface ProjectRepositoryPort {

    // MARK: Reads

    suspend fun projects(includeArchived: Boolean): List<Project>

    suspend fun project(id: ProjectID): Project?

    suspend fun workItems(projectID: ProjectID): List<WorkItem>

    suspend fun workItem(id: WorkItemID): WorkItem?

    suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int>

    suspend fun isEmpty(): Boolean

    /**
     * The recorded status history of one work item, newest first.
     *
     * Newest first because that is the order the editor shows it in, and asking every
     * caller to remember to reverse a chronological list is how a history ends up reading
     * backwards in one screen and forwards in another.
     *
     * "Newest" means the instant, with the later-recorded change winning a tie. A card
     * created and triaged inside one clock tick produces two rows with the same
     * `changedAt`, and a sort on the timestamp alone would hand those back in insertion
     * order — which is the oldest order — so the order has to be made total deliberately
     * rather than inherited from the stability of the sort.
     *
     * Empty rather than throwing for an item with no history, which is the normal case for
     * one that has never been moved.
     */
    suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange>

    /**
     * Inserts the first-launch sample board if the store is completely empty.
     *
     * Returns whether anything was inserted, and does nothing once a single project
     * exists — so it is safe to call on every appearance of the dashboard, which is
     * exactly what the view layer does. It writes through the same path as any other
     * edit, so sample data is queued for sync like real work rather than being a back
     * door that behaves differently once a server is attached.
     */
    suspend fun seedSampleDataIfNeeded(): Boolean

    // MARK: Writes

    suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID> = emptyList(),
    ): Project

    /**
     * Applies a partial edit to a project.
     *
     * A `null` [name], [details] or [memberIDs] means "leave this field alone", never "set it to
     * null" — project fields are non-nullable, so an absent argument means no intention to
     * change that field. The
     * "clear the notes" affordance is therefore spelled as an empty string, which
     * [com.tasktracker.domain.ProjectRules.normaliseDetails] accepts. An empty member list
     * explicitly removes all members.
     *
     * The project key is deliberately absent. It is the prefix every work item key in the
     * project is built from, so rewriting it would orphan keys that other devices and
     * every server reference already use.
     */
    suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>? = null,
    ): Project

    suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project

    suspend fun deleteProject(id: ProjectID)

    suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        priority: Priority,
        assignee: String?,
        dueDate: Instant?,
        now: Instant,
    ): WorkItem

    /**
     * Applies a partial edit to a work item.
     *
     * [title], [details] and [priority] are plain optionals — a `null` leaves the field
     * alone, and none of the three can be meaningfully emptied.
     *
     * [assignee] and [dueDate] are [Patch]es, because both *can* be emptied and "not
     * mentioned" is a different instruction from "set it to nothing". See [Patch] for why
     * a plain `String?` could not carry this.
     */
    suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        now: Instant,
    ): WorkItem

    /**
     * Moves a work item to [status], optionally at a specific [rank].
     *
     * A `null` [rank] appends to the end of the destination column rather than
     * preserving the item's current one, which would put a task dragged from the middle
     * of one lane at an arbitrary place in the next.
     */
    suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem

    suspend fun deleteWorkItem(id: WorkItemID)

    // MARK: Change notifications

    /**
     * Emits once after every committed write — local edits and pulled remote changes
     * alike.
     *
     * This replaces SwiftUI's `@Query` in the view layer. `@Query` would mean importing
     * SwiftData into presentation and handing it model types, which is exactly the
     * coupling the module split exists to prevent. An invalidation signal plus a re-read
     * keeps the store an implementation detail: the view model holds plain `List<WorkItem>`
     * values and this is the only way it finds out they are stale.
     *
     * **A cold [Flow]**, with one subscriber per collection — which is what
     * `com.tasktracker.core.Broadcaster` provides, and why that class exists in `:core`
     * rather than a `MutableSharedFlow` at each call site. A hot shared flow would be
     * wrong here: the dashboard, the project screen and the board can each be collecting
     * at once, and a single shared cursor would hand the same event to whichever
     * collector got there first while the others never heard about the write.
     *
     * Events that arrive before a caller starts collecting are not replayed, and that is
     * correct: the first thing a collector does is read the current state, so a replay
     * would only ever tell it about something it has already accounted for.
     */
    fun changes(): Flow<ProjectChange>
}

/**
 * What changed, so a subscriber can decide whether it cares.
 *
 * A unit payload would say only "something, somewhere". That forces every view model to
 * re-read on every write anywhere in the feature — and it makes the invalidation contract
 * untestable, because a test cannot assert that a project-only change woke the board
 * model. Naming the entities is what lets both happen.
 *
 * @param projectID scopes the change to one project when it can be named, and is `null`
 *   when it cannot: a create has no prior project to scope to, and a pull that touched
 *   several projects at once is not more expensive to handle for being unscoped. A
 *   subscriber compares this against the project it is showing and ignores anything else.
 */
data class ProjectChange(
    val kind: Kind,
    val projectID: ProjectID? = null,
) {
    /** Which aggregate moved. */
    enum class Kind {
        Projects,
        WorkItems,
    }
}

/**
 * A field's new value, distinguishing "not mentioned" from "set it to nothing".
 *
 * The original spells this `String??` and `Date??` — an optional optional, where the
 * outer `nil` means *leave this alone* and an inner `nil` means *clear it*. Kotlin has
 * no second level of nullability, and the two obvious replacements are both wrong:
 *
 * - A plain `String?` collapses the two instructions. A caller clearing an assignee
 *   passes `null`, the store reads it as "no instruction", and the field silently keeps
 *   its old value. The failure is invisible: the edit is reported as saved and the value
 *   is wrong, which is the one combination a user reports as a bug with no reproduction.
 * - A sentinel such as `""` for unassigned, or a magic `Instant`, puts a fake value
 *   into the domain to mean "no value". A work item then has a due date of `Instant.MIN`
 *   that has to be filtered out of every query, every sort and every test.
 *
 * So the three states are named rather than encoded in nesting. [Unmentioned] is the
 * absent instruction, [Of] is a value, and an [Of] whose value is `null` is the clear —
 * a real instruction, carried as one, and readable at the call site rather than inferred
 * from a type's parentheses.
 *
 * ```
 * repo.updateWorkItem(id, assignee = Patch.of("ada"))   // assigned to Ada
 * repo.updateWorkItem(id, assignee = Patch.of(null))    // unassigned
 * repo.updateWorkItem(id, assignee = Patch.Unmentioned) // dragged from the board
 * ```
 */
sealed interface Patch<out T> {

    /** The caller said nothing about this field. Leave whatever is stored alone. */
    data object Unmentioned : Patch<Nothing>

    /**
     * The caller set this field, possibly to `null`.
     *
     * The parameter is the nullable type it is assigned to, so [Patch.Of] over `String?`
     * is what clearing an assignee is built from — a `null` here is an instruction, not
     * an absence.
     */
    data class Of<out T>(val value: T) : Patch<T>
}

/**
 * The projects the default list shows.
 *
 * A default argument rather than the original's protocol extension, so a caller cannot
 * accidentally pass `includeArchived` positionally the wrong way round — `projects(true)`
 * reads as "give me projects, archived" until you go looking for what the `true` means.
 */
suspend fun ProjectRepositoryPort.projects(): List<Project> = projects(includeArchived = false)
