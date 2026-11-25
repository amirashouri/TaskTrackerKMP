package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The epic link picker's candidate rule.
 *
 * Eligibility is not "everything in the project": finished and filed-away work is not a new
 * subtask, but an item already linked to this epic has to stay visible so the picker can still
 * unlink it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpicEditorModelTest {

    @Test
    fun `the picker offers eligible items and keeps linked finished ones visible`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo", keyPrefix = "AP")
        val otherStory = testEpic(listOf(project.id), title = "Elsewhere")
        val epic = testEpic(listOf(project.id), title = "Checkout")
        val items = listOf(
            testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog),
            testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Todo),
            testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.InProgress),
            testWorkItem(project.id, key = "AP-4", status = WorkItemStatus.Done),
            testWorkItem(project.id, key = "AP-5", status = WorkItemStatus.Closed),
            testWorkItem(project.id, key = "AP-6", status = WorkItemStatus.Todo)
                .apply { isArchived = true },
            // Linked here while Done: finished, but still part of this epic.
            testWorkItem(project.id, key = "AP-7", status = WorkItemStatus.Done)
                .apply { epicID = epic.id },
            // Linked to another story: offered so the task can be moved, not because it is free.
            testWorkItem(project.id, key = "AP-8", status = WorkItemStatus.Todo)
                .apply { epicID = otherStory.id },
        )
        repository.seed(project)
        repository.seed(epic)
        repository.seed(otherStory)
        items.forEach { repository.seed(it) }

        val model = EpicEditorModel(
            existing = epic,
            repository = repository,
            dateProvider = TestDateProvider(),
        )
        model.loadCandidates()

        assertEquals(
            expected = listOf("AP-1", "AP-2", "AP-3", "AP-7", "AP-8"),
            actual = model.candidateItems.map { it.key.rawValue },
        )
        assertEquals(
            expected = mapOf(epic.id to "Checkout", otherStory.id to "Elsewhere"),
            actual = model.candidateEpicTitles,
        )
    }
}
