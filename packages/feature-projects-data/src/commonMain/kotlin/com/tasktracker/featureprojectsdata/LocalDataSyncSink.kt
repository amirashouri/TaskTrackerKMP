package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.parseProjectID
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.database.inTransaction
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.sync.RemoteChange
import com.tasktracker.sync.SyncApplySink
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer

/**
 * Applies server changes to the local store, server-wins.
 *
 * ## The one rule that matters: this never writes to the outbox
 *
 * A sink that queued what it applied would make every pull generate a push, and a server that
 * echoed the change back would leave the two sides pushing at each other for as long as the
 * app is open. Pulls land straight in the store; only local edits go through
 * [RoomProjectRepository], and only it writes to the outbox DAO. The asymmetry is the design,
 * not an oversight — the queue has exactly one writer, and it is not this class.
 *
 * Idempotency is the other requirement, and it is structural rather than checked: applying a
 * payload twice writes the same field values and upserts on the same primary key, so it is
 * indistinguishable from applying it once. A cursor can only advance after a page is fully
 * applied, so an interrupted cycle deliberately re-delivers that page and this has to survive
 * it. The one thing that would *not* survive it is appending a history row — see
 * [applyWorkItem].
 *
 * ## Server-wins, without losing local identity
 *
 * The payload is applied over whatever is stored, but only for the fields a payload can
 * legitimately change: [ProjectRecord.apply] and [WorkItemRecord.apply] keep the stored id, the
 * stored project, and the stored creation time. That is what makes an *upsert* an upsert — a
 * payload naming a different parent is a server bug, and applying it would move somebody's
 * card between projects with no delete and no history.
 *
 * ## It announces what it applied, on the same channel
 *
 * A pull that does not invalidate the open screens leaves the board showing the pre-pull state
 * with nothing pending to explain it, and nothing in the app will ever correct it until the
 * user navigates away and back. So this shares a [ProjectChangeBus] with the repository rather
 * than owning one — a second bus would mean two channels, and a screen listening to one of
 * them would be wrong for exactly half its events.
 */
class LocalDataSyncSink(
    private val database: TaskTrackerDatabase,
    private val changeBus: ProjectChangeBus,
) : SyncApplySink {

    /**
     * Which kinds a change announces, decided by what it *did* rather than by what it was.
     *
     * Only a project **delete** touches work items — it takes them with it — so only that one
     * announces both. A project upsert writes one row, and waking a board for it would make
     * every open board re-read every card in the project because somebody renamed it. A
     * work-item change announces one kind, and deleting an item cannot move a project.
     *
     * The original announced both from all seven of its write paths. That is the
     * over-broadcast [com.tasktracker.featureprojectsdomain.ProjectChange] exists to avoid,
     * and it is also why a board could not be tested for waking up for the *right* reason.
     */
    override suspend fun apply(change: RemoteChange) {
        when (change.entity) {
            OutboxEntity.Project -> {
                val owner = applyProject(change)
                changeBus.send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = owner))
                if (change.operation == OutboxOperation.Delete) {
                    changeBus.send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = owner))
                }
            }

            OutboxEntity.WorkItem -> {
                val owner = applyWorkItem(change)
                changeBus.send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = owner))
            }

            // A sprint write announces both kinds, for the reason
            // `ProjectChangeBus.announceSprints` gives, and announced here rather than
            // through that helper because the project id is read out of the change rather
            // than supplied by a caller.
            OutboxEntity.Sprint -> {
                val owner = applySprint(change)
                changeBus.send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = owner))
                changeBus.send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = owner))
            }
        }
    }

    /**
     * @return the owning project, read **before** the delete — afterwards there is no row
     *   left to ask. `null` when the row was already absent, which is the same idempotency
     *   requirement as [applyWorkItem]: a re-delivered delete must succeed.
     */
    private suspend fun applySprint(change: RemoteChange): ProjectID? {
        if (change.operation == OutboxOperation.Delete) {
            val id = change.entityID
            val owner = inTransaction(database) {
                val existing = database.sprintDao().byID(id) ?: return@inTransaction null
                // The items are released rather than left pointing at a sprint this device
                // no longer has. Nothing local would ever clean that up, and the next pull
                // would find a card belonging to a sprint that does not exist.
                database.workItemDao().releaseFromSprint(id)
                val projectID = parseProjectID(existing.projectID)
                database.sprintDao().deleteByID(id)
                projectID
            }
            return owner
        }

        val sprint = decode(Sprint.serializer(), change.payload, change.entity)
        inTransaction(database) {
            val existing = database.sprintDao().byID(sprint.id.stringValue)
            if (existing == null) {
                database.sprintDao().insert(sprint.toRecord())
            } else {
                // `SprintRecord.apply` copies `isFinished` — unlike `WorkItemRecord.apply`,
                // which never copies a status — because closing a sprint happens on another
                // device and the summary has to be able to arrive.
                database.sprintDao().update(existing.apply(sprint))
            }
        }
        return sprint.projectID
    }

    /**
     * @return the project this change touched, or `null` when it could not be named: a
     *   malformed id, or a delete for a project that was never here. Both are announced
     *   unscoped rather than not at all — a subscriber that ignores an id it does not match
     *   and one that never receives the event behave identically, so a missing announcement
     *   is the only version of this that can look like a bug.
     */
    private suspend fun applyProject(change: RemoteChange): ProjectID? {
        if (change.operation == OutboxOperation.Delete) {
            val id = change.entityID
            inTransaction(database) {
                // The work items go too, and this is a correction rather than a port. The
                // original deleted the project and its history and left the items, which
                // orphans every card under it: nothing references them, no board can show
                // them, and a later pull re-sending one of those ids would resurrect a card
                // into a project that no longer exists.
                database.workItemDao().deleteByProject(id)
                // And the sprints, for the same reason. This is the *pulled* delete; the
                // local one is `RoomProjectRepository.deleteProject`, which queues a delete
                // row per sprint for exactly this path to receive.
                for (sprint in database.sprintDao().byProject(id)) {
                    database.sprintDao().deleteByID(sprint.id)
                }
                database.statusChangeDao().deleteByProject(id)
                database.projectDao().deleteByID(id)
            }
            return parseProjectID(id)
        }

        val project = decode(Project.serializer(), change.payload, change.entity)
        inTransaction(database) {
            val existing = database.projectDao().byID(project.id.stringValue)
            if (existing == null) {
                database.projectDao().insert(project.toRecord())
            } else {
                // `apply` returns the updated row rather than mutating it — see
                // `ProjectRecord.apply` — so the write is one statement and there is no window
                // in which the row on disk and the row in memory disagree.
                database.projectDao().update(existing.apply(project))
            }
        }
        return project.id
    }

    /**
     * @return the owning project, read **before** the delete — afterwards there is no record
     *   left to ask, and an announcement that cannot name its project cannot wake a board.
     *   `null` when the row was already absent, which is a normal consequence of the
     *   idempotency requirement rather than an error: a re-delivered delete must succeed.
     */
    private suspend fun applyWorkItem(change: RemoteChange): ProjectID? {
        if (change.operation == OutboxOperation.Delete) {
            val id = change.entityID
            val owner = inTransaction(database) {
                val existing = database.workItemDao().byID(id) ?: return@inTransaction null
                val projectID = parseProjectID(existing.projectID)
                database.statusChangeDao().deleteByWorkItem(id)
                database.workItemDao().deleteByID(id)
                projectID
            }
            return owner
        }

        // No history row here, deliberately. A pull has to stay idempotent — an interrupted
        // cycle re-delivers the page it could not finish — and appending a row is not, so a
        // sink that recorded history would fill an item's history with the same transition
        // once per retry. [StatusChange] says the same thing from the domain's side, and adds
        // the second reason: there is no server-side author to attribute the row to.
        //
        // `WorkItemRecord.apply` reconciles `resolvedAt` on the way through, which is the part
        // of a status change the board and the archive actually depend on.
        val item = decode(WorkItem.serializer(), change.payload, change.entity)
        inTransaction(database) {
            val existing = database.workItemDao().byID(item.id.stringValue)
            if (existing == null) {
                database.workItemDao().insert(item.toRecord())
            } else {
                database.workItemDao().update(existing.apply(item))
            }
        }
        return item.projectID
    }

    /**
     * Throwing here aborts the page and leaves the cursor where it was, so a payload this
     * build cannot read is retried on the next cycle rather than skipped past.
     *
     * Retrying is right, and it is deliberately the **opposite** of what the record read path
     * does with a column it cannot read. A bad column is in a store that already has the row,
     * so dropping it costs one entry. A bad payload is the only copy of somebody's edit, and
     * dropping it loses that permanently — which is also why the domain's
     * [ProjectRepositoryPort] contract for a failed write is an error and never a silent skip.
     * A decode failure is usually version skew that a later release understands.
     */
    private fun <T> decode(
        serializer: KSerializer<T>,
        payload: ByteArray,
        entity: OutboxEntity,
    ): T = try {
        JSONCoding.json.decodeFromString(serializer, payload.decodeToString())
    } catch (cancellation: CancellationException) {
        // Never. This function is not suspending, so there is nothing here that *should* be
        // able to cancel it — but the catch below is broad on purpose, and a broad catch that
        // rewrites a cancellation into a decoding error is how a cancelled pull comes back as a
        // red error banner rather than as silence.
        throw cancellation
    } catch (failure: Exception) {
        throw AppError.Decoding(
            "Unreadable ${entity.wireValue} payload: ${failure.message ?: failure::class.simpleName}",
        )
    }
}
