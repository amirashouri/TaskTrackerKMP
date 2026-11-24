package com.tasktracker.featureprojectsdata

import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.Patch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The difference between putting a story away and putting its work away.
 *
 * `isArchived` on an item is a filing choice, not a workflow fact — a filed item is still
 * visible in an archived view and its status is untouched — so these tests assert on the
 * flag and on the queued payloads rather than on the board.
 */
class EpicArchiveTest {

    private class Fixture(
        val harness: Harness,
        val epic: com.tasktracker.domain.Epic,
        val kept: WorkItem,
        val filed: WorkItem,
        val outsider: WorkItem,
    )

    private suspend fun fixture(): Fixture {
        val harness = Harness()
        val (apollo, borealis) = repositoryProjects(harness)
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )
        val kept: WorkItem = harness.repository.createWorkItem(
            projectID = apollo.id,
            title = "Kept",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = Moments.start,
        )
        val filed: WorkItem = harness.repository.createWorkItem(
            projectID = borealis.id,
            title = "Filed",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = Moments.start,
        )
        val outsider: WorkItem = harness.repository.createWorkItem(
            projectID = apollo.id,
            title = "Outsider",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = Moments.start,
        )
        harness.repository.setWorkItemEpic(kept.id, Patch.Of(epic.id), Moments.later)
        harness.repository.setWorkItemEpic(filed.id, Patch.Of(epic.id), Moments.later)
        return Fixture(harness, epic, kept, filed, outsider)
    }

    private suspend fun repositoryProjects(harness: Harness): Pair<Project, Project> =
        harness.repository.createProject("Apollo", "APO", "", Moments.start) to
            harness.repository.createProject("Borealis", "BOR", "", Moments.start)

    @Test
    fun archiveOnlyFlipsTheEpicAndLeavesItsSubtasksUntouched() = runTest {
        val fixture = fixture()
        val depthBefore = fixture.harness.queueDepth()

        val archived = fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = true, archiveSubtasks = false, now = Moments.muchLater,
        )

        assertTrue(archived.isArchived)
        assertFalse(fixture.harness.repository.workItem(fixture.kept.id)!!.isArchived)
        assertFalse(fixture.harness.repository.workItem(fixture.filed.id)!!.isArchived)
        // The epic's own upsert supersedes the one its creation queued, and no subtask gains
        // a row: the count is what distinguishes "touched no subtask" from "wrote the same
        // subtasks back".
        assertEquals(depthBefore, fixture.harness.queueDepth(), "archive-only must not queue an item change")
    }

    @Test
    fun archiveAllFilesEveryLinkedSubtaskAndQueuesEachOne() = runTest {
        val fixture = fixture()

        fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = true, archiveSubtasks = true, now = Moments.muchLater,
        )

        assertTrue(fixture.harness.repository.workItem(fixture.kept.id)!!.isArchived)
        assertTrue(fixture.harness.repository.workItem(fixture.filed.id)!!.isArchived)
        assertFalse(
            fixture.harness.repository.workItem(fixture.outsider.id)!!.isArchived,
            "an item with no epic is not the epic's to file",
        )
        for (item in listOf(fixture.kept, fixture.filed)) {
            val queued = fixture.harness.queued()
                .last { it.entityKind == "workItem" && it.entityID == item.id.stringValue }
            assertTrue(
                fixture.harness.decodeWorkItem(queued).isArchived,
                "a filed item must be queued, or the other device never learns",
            )
        }
    }

    @Test
    fun unarchiveRestoresTheItemsTheEpicFiled() = runTest {
        val fixture = fixture()
        fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = true, archiveSubtasks = true, now = Moments.muchLater,
        )

        val restored = fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = false, archiveSubtasks = true, now = Moments.muchLater,
        )

        assertFalse(restored.isArchived)
        assertFalse(fixture.harness.repository.workItem(fixture.kept.id)!!.isArchived)
        assertFalse(fixture.harness.repository.workItem(fixture.filed.id)!!.isArchived)
    }

    @Test
    fun unarchivingTheEpicAloneLeavesTheItemsFiled() = runTest {
        val fixture = fixture()
        fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = true, archiveSubtasks = true, now = Moments.muchLater,
        )

        fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = false, archiveSubtasks = false, now = Moments.muchLater,
        )

        assertTrue(
            fixture.harness.repository.workItem(fixture.kept.id)!!.isArchived,
            "restoring the story without its work is a choice the dialog offers",
        )
    }

    @Test
    fun anArchivedEpicIsStillListed() = runTest {
        val fixture = fixture()

        fixture.harness.repository.setEpicArchived(
            fixture.epic.id, isArchived = true, archiveSubtasks = false, now = Moments.muchLater,
        )

        // No `includeArchived` flag on `epics()`: the Epic segment's job is everything the
        // device holds, and hiding a story would also hide the action that restores it.
        assertEquals(
            listOf(fixture.epic.id),
            fixture.harness.repository.epics().map { it.id },
        )
    }
}
