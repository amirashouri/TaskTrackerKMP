package com.tasktracker.featureprojectsdata

import com.tasktracker.core.Broadcaster
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlinx.coroutines.flow.Flow

/**
 * The one invalidation channel the projects feature publishes on.
 *
 * Deliberately its own small type rather than a bare [Broadcaster] passed around, for two
 * reasons. It gives the repository and the sync sink one obvious way to reach the same
 * channel, which is the whole point: two separate broadcasters mean pulled changes arrive
 * somewhere no screen is listening, and the board quietly shows stale data until the user
 * happens to navigate away and back. And it is the one place that knows the fan-out mechanics,
 * so neither of the two things that publish has to.
 *
 * ## The publish rules
 *
 * Both are load-bearing, and both are the reason this is not a `MutableSharedFlow`:
 *
 * 1. **A fresh stream per subscriber.** The dashboard, the project screen and the board can
 *    all be open at once; a single shared cursor would hand each event to whichever collector
 *    got there first and the others would never hear about the write.
 * 2. **A send with nobody listening is dropped, not buffered.** A screen that opens later
 *    re-reads on start, so it cannot have missed anything — and buffering would mean a write
 *    from a background sync was replayed to a screen that had already read past it. Dropping
 *    is the only answer that keeps "collect, then read" correct.
 *
 * Events are not replayed to a late subscriber, and cannot be: [Broadcaster] registers on
 * collection rather than on stream creation, so `subscriberCount` counts live collectors and a
 * test has to have one running to observe anything.
 */
class ProjectChangeBus {

    private val broadcaster = Broadcaster<ProjectChange>()

    /** A fresh stream per subscriber. See the class documentation. */
    fun changes(): Flow<ProjectChange> = broadcaster.stream()

    /**
     * Publishes a change. Dropped when nobody is listening, which is correct rather than a
     * limitation — see the class documentation.
     */
    fun send(change: ProjectChange) {
        broadcaster.send(change)
    }

    /**
     * Live collector count. A stream is only a stream once something is collecting it, so this
     * is zero for a screen that has taken a [changes] reference and not yet started.
     */
    val subscriberCount: Int get() = broadcaster.subscriberCount

    /**
     * Closes every open stream.
     *
     * Exists for tests and for teardown. A collector that has simply stopped — its screen
     * went away, or the test finished — unregisters itself when its coroutine is cancelled, so
     * the only queues left open are the ones whose collector was never cancelled. Finishing is
     * the deliberate way to say "nothing more is coming" to everyone at once.
     *
     * Not reversible, and not meant to be. One bus lives for one session; restarting it would
     * resurrect queues that a cancelled collector had already abandoned.
     */
    fun finish() {
        broadcaster.finish()
    }
}

/**
 * Announces a project change, in both directions.
 *
 * Only for a write that genuinely moved **both** aggregates, and there are exactly two of
 * them: creating a work item, which advances the project's key counter, and deleting a project,
 * which takes its work items with it. A board that ignored the `.projects` half of the first
 * would keep showing a stale key; a board that ignored the `.workItems` half of the second
 * would show a column of items that no longer exist.
 *
 * One call rather than two `send`s at each of those sites, so the two-kinds rule is stated
 * once. The other write paths use [announceProjects] or [announceWorkItems] instead — an
 * announcement that names an aggregate which did not move is not a harmless over-broadcast,
 * it is the thing that makes a test assert a board woke up for the wrong reason.
 */
internal fun ProjectChangeBus.announceProjectChange(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
}

/** Announces that one project moved. For a project-only write: a create, a rename, an archive. */
internal fun ProjectChangeBus.announceProjects(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID))
}

/**
 * Announces that work items moved, for a write that did not touch the project.
 *
 * An edit, a move, a delete of one card. [RoomProjectRepository.createWorkItem] is *not* one
 * of these, despite also being a work-item write, because it advances the key counter on the
 * way through.
 */
internal fun ProjectChangeBus.announceWorkItems(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
}

/**
 * Announces that sprints moved, and separately that work items moved.
 *
 * Both, always, because **every** sprint write moves items or will move them:
 * `setWorkItemSprint` and `deleteSprint` move them immediately, and the rest change what a
 * sprint *contains* even though no card row changed. A subscriber that heard only the
 * sprints half would keep showing the old membership; one that heard only the items half
 * would never learn a sprint was created at all.
 *
 * Two sends rather than one combined kind, because the two subscribers are different and
 * re-reading is the expensive part: the Sprints tab needs the sprint list, and the board
 * needs the cards. One kind naming both would make each do the other's work on every write.
 */
internal fun ProjectChangeBus.announceSprints(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
}

/**
 * Announces a project deletion: all three kinds, each exactly once.
 *
 * Its own helper rather than [announceProjectChange] plus [announceSprints], because both
 * of those send the `.workItems` half — calling them together announced work items twice
 * for every deletion, which is harmless in production and indistinguishable from correct in
 * a test that counts announcements. That is exactly the kind of duplicate this bus's
 * "announce what actually moved" rule exists to prevent, so the combination is named here
 * instead of assembled at the call site.
 */
internal fun ProjectChangeBus.announceProjectDeletion(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = projectID))
}
