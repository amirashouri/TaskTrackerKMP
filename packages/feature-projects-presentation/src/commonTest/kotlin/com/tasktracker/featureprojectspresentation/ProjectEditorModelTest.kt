package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The project create/edit form.
 *
 * The behaviour worth pinning here is that the key prefix is immutable after creation, and
 * that a form with nothing in it cannot be saved - neither of which is visible in a happy
 * path screenshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditorModelTest {

    // MARK: Creating

    @Test
    fun `a new form starts empty and cannot be saved`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())

        assertTrue(model.isCreating)
        assertEquals("", model.name)
        assertEquals("", model.keyPrefix)
        assertFalse(model.canSave)
    }

    /**
     * An empty create form is clean, so Cancel closes it without a discard prompt.
     *
     * The prompt reads this flag, and an untouched form that claimed a change would ask the
     * user to confirm losing nothing on every accidental Cancel.
     */
    @Test
    fun `an untouched new form has nothing to discard`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())

        assertFalse(model.hasChanges)
    }

    /** A picked member is work the create form would lose, so it counts as a change. */
    @Test
    fun `a member picked on a new form counts as a change`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())

        model.onMemberIDsChanged(listOf(testUserID))

        assertTrue(model.hasChanges)
    }

    @Test
    fun `a new project derives its prefix from the name`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("Apollo")
        assertEquals("AP", model.keyPrefix)
        assertTrue(model.canSave)
    }

    @Test
    fun `whitespace is not a name`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())
        model.onNameChanged("   ")
        model.onKeyPrefixChanged("AP")

        assertFalse(model.canSave)
    }

    @Test
    fun `saving a new project creates it and remembers its id`() = runTest {
        val repository = FakeProjectRepository()
        val model = ProjectEditorModel(null, repository, TestDateProvider())
        model.onNameChanged("Apollo")
        model.onKeyPrefixChanged("AP")
        model.onDetailsChanged("Launch work")

        assertTrue(model.save())

        assertEquals(listOf("createProject"), repository.writes)
        val created = repository.storedProjects().single()
        assertEquals("Apollo", created.name)
        assertEquals("AP", created.keyPrefix)
        assertEquals("Launch work", created.details)
        assertEquals(created.id, model.savedProjectID)
    }

    @Test
    fun `members are saved on create and duplicates collapse`() = runTest {
        val repository = FakeProjectRepository()
        val model = ProjectEditorModel(null, repository, TestDateProvider())
        model.onNameChanged("Apollo")
        model.onMemberIDsChanged(listOf(testUserID, testUserID))

        assertTrue(model.save())
        assertEquals(listOf(testUserID), repository.storedProjects().single().memberIDs)
    }

    @Test
    fun `editing member selection marks the draft changed and persists it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo")
        repository.seed(project)
        val model = ProjectEditorModel(project, repository, TestDateProvider())
        val grace = UserID("77777777-7777-4777-8777-777777777777")

        model.onMemberIDsChanged(listOf(grace))
        assertTrue(model.hasChanges)
        assertTrue(model.save())
        assertEquals(listOf(grace), repository.storedProjects().single().memberIDs)
    }

    /**
     * A created form stays a created form.
     *
     * `existing` is read once, at construction, so a second save would create a second
     * project rather than update the first. That is safe only because the form is expected
     * to close on a successful create - so this test documents the contract instead of
     * leaving it to be discovered in a bug report.
     */
    @Test
    fun `a created form is expected to close rather than save twice`() = runTest {
        val repository = FakeProjectRepository()
        val model = ProjectEditorModel(null, repository, TestDateProvider())
        model.onNameChanged("Apollo")
        model.onKeyPrefixChanged("AP")
        model.save()

        assertTrue(
            model.isCreating,
            "Creating is a one-shot action: the form is expected to close on success, and " +
                "saving it a second time would create a second project rather than update " +
                "the first. This test documents that contract.",
        )
    }

    // MARK: Editing

    @Test
    fun `an existing form starts with the project's fields and no changes`() = runTest {
        val project = testProject(name = "Apollo", details = "Old")
        val model = ProjectEditorModel(project, FakeProjectRepository(), TestDateProvider())

        assertFalse(model.isCreating)
        assertEquals("Apollo", model.name)
        assertEquals("Old", model.details)
        assertEquals("AP", model.keyPrefix)
        assertFalse(model.hasChanges)
    }

    @Test
    fun `the key prefix is read-only once a project exists`() = runTest {
        val project = testProject()
        val model = ProjectEditorModel(project, FakeProjectRepository(), TestDateProvider())

        assertFalse(model.isKeyPrefixEditable)
        assertEquals("AP", model.keyPrefix)
    }

    @Test
    fun `the prefix is editable while creating`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())

        assertTrue(model.isKeyPrefixEditable)
    }

    /**
     * A rename that is only whitespace is not a change.
     *
     * Without this, tapping save after opening a project to look at it would write a new
     * `updatedAt`, which reorders the projects list - so a no-op save visibly moves the
     * row the user is looking at.
     */
    @Test
    fun `whitespace-only edits are not changes`() = runTest {
        val project = testProject(name = "Apollo", details = "Notes")
        val model = ProjectEditorModel(project, FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("Apollo   ")
        model.onDetailsChanged("Notes ")

        assertFalse(model.hasChanges)
    }

    @Test
    fun `a real edit is a change`() = runTest {
        val project = testProject(name = "Apollo")
        val model = ProjectEditorModel(project, FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("Apollo 2")

        assertTrue(model.hasChanges)
    }

    @Test
    fun `saving an edit updates the name and details and leaves the prefix alone`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject(name = "Apollo", keyPrefix = "AP", details = "Old")
        repository.seed(project)
        val model = ProjectEditorModel(project, repository, clock)

        model.onNameChanged("Apollo 2")
        model.onDetailsChanged("New")
        assertTrue(model.save())

        assertEquals(listOf("updateProject"), repository.writes)
        val stored = repository.storedProjects().single()
        assertEquals("Apollo 2", stored.name)
        assertEquals("New", stored.details)
        assertEquals("AP", stored.keyPrefix, "the prefix is not a parameter of UpdateProject at all")
    }

    @Test
    fun `a changed prefix is ignored rather than applied`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo", keyPrefix = "AP")
        repository.seed(project)
        val model = ProjectEditorModel(project, repository, TestDateProvider())

        // A read-only field can still be driven by a test, a restored state, or a
        // keyboard that did not respect the read-only flag. The value must not be applied.
        model.onKeyPrefixChanged("ZZ")
        model.onNameChanged("Renamed")
        model.save()

        assertEquals("AP", repository.storedProjects().single().keyPrefix)
    }

    // MARK: Status catalog

    /**
     * The admin can build an ordered catalog, and every edit counts as unsaved work.
     *
     * Add, rename, recategorise and reorder are the four verbs the board lane and picker
     * features depend on; a draft that edited the list without marking the form dirty would
     * close on Cancel without a prompt and silently discard the catalog.
     */
    @Test
    fun `the status catalog can be added renamed recategorised and reordered`() = runTest {
        val project = testProject()
        val model = ProjectEditorModel(project, FakeProjectRepository(), TestDateProvider())

        assertTrue(model.addStatus("  Ready ", WorkItemStatus.Todo, TagColor.Gray))
        assertTrue(model.addStatus("In build", WorkItemStatus.InProgress, TagColor.Blue))
        assertTrue(model.hasChanges)
        assertEquals(
            listOf("Ready", "In build"),
            model.statuses.map { it.name },
            "the name is trimmed and the catalog keeps creation order",
        )

        val ready = model.statuses.first { it.name == "Ready" }
        model.updateStatus(ready.id, name = "Queued", category = WorkItemStatus.Backlog, color = TagColor.Amber)
        val updated = model.statuses.first { it.id == ready.id }
        assertEquals("Queued", updated.name)
        assertEquals(WorkItemStatus.Backlog, updated.category)
        assertEquals(TagColor.Amber, updated.color)

        model.reorderStatus(from = 1, to = 0)
        assertEquals(listOf("In build", "Queued"), model.statuses.map { it.name })

        model.removeStatus(updated.id)
        assertEquals(listOf("In build"), model.statuses.map { it.name })
    }

    /** A blank or case-insensitively duplicate name would make the board ambiguous. */
    @Test
    fun `a blank or duplicate status name is refused`() = runTest {
        val model = ProjectEditorModel(testProject(), FakeProjectRepository(), TestDateProvider())

        assertFalse(model.addStatus("   ", WorkItemStatus.Todo, TagColor.Gray))
        assertTrue(model.addStatus("Ready", WorkItemStatus.Todo, TagColor.Gray))
        assertFalse(model.addStatus(" ready ", WorkItemStatus.Todo, TagColor.Blue))
        assertEquals(1, model.statuses.size)
    }

    @Test
    fun `saving the catalog persists it on the project`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = ProjectEditorModel(project, repository, TestDateProvider())

        assertTrue(model.addStatus("Ready", WorkItemStatus.Todo, TagColor.Gray))
        assertTrue(model.addStatus("In build", WorkItemStatus.InProgress, TagColor.Blue))
        assertTrue(model.save())

        val stored = repository.storedProjects().single()
        assertEquals(
            listOf(
                StatusDefinition("x", "Ready", WorkItemStatus.Todo, order = 0, color = TagColor.Gray),
                StatusDefinition("y", "In build", WorkItemStatus.InProgress, order = 1, color = TagColor.Blue),
            ).map { Triple(it.name, it.category, it.order) },
            stored.statuses.map { Triple(it.name, it.category, it.order) },
            "the catalog, its categories and its order reach the store; ids are generated",
        )
    }

    /** An untouched catalog is not sent, so opening and saving a project does not rewrite it. */
    @Test
    fun `an unchanged catalog is not part of the save`() = runTest {
        val catalog = listOf(
            StatusDefinition(id = "s-ready", name = "Ready", category = WorkItemStatus.Todo, order = 0),
        )
        val repository = FakeProjectRepository()
        val project = testProject(statuses = catalog)
        repository.seed(project)
        val model = ProjectEditorModel(project, repository, TestDateProvider())

        assertFalse(model.hasChanges)
        model.onNameChanged("Apollo 2")
        assertTrue(model.save())

        assertEquals(catalog, repository.storedProjects().single().statuses)
    }

    // MARK: Failure

    @Test
    fun `a failed save reports the failure and saves nothing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo")
        repository.seed(project)
        val model = ProjectEditorModel(project, repository, TestDateProvider())
        model.onNameChanged("Renamed")

        repository.failure = AppError.Offline

        assertFalse(model.save())
        assertEquals(AppError.Offline.message, model.errorMessage)
        assertEquals("Apollo", repository.storedProjects().single().name)
    }

    @Test
    fun `a failure that is not an AppError is reported as unknown rather than thrown`() =
        runTest {
            val repository = ThrowingProjectRepository()
            val model = ProjectEditorModel(null, repository, TestDateProvider())
            model.onNameChanged("Apollo")
            model.onKeyPrefixChanged("AP")

            assertFalse(model.save())
            assertNotNull(model.errorMessage)
            assertTrue(
                model.errorMessage!!.contains("IllegalStateException"),
                "the type is named so the failure is diagnosable from the banner, got: " +
                    model.errorMessage,
            )
        }

    // MARK: The snapshot

    /**
     * The form is a snapshot, so a store change under the user cannot rewrite their edits.
     *
     * A project switcher or a sync can rename a project while the form is open. The form
     * reads `existing` once, at construction, and compares against that - so the user sees
     * "renamed on another device" against the name they typed rather than a diff against a
     * value that moved while they were reading it. A form that re-read the project would
     * report a change the user never made and offer to discard typing they did.
     */
    @Test
    fun `a store change under the form does not rewrite the fields or the snapshot`() =
        runTest {
            val repository = FakeProjectRepository()
            val clock = TestDateProvider()
            val project = testProject(name = "Apollo")
            repository.seed(project)
            val model = ProjectEditorModel(project, repository, clock)

            repository.updateProject(project.id, "Renamed elsewhere", null, clock.now())

            assertEquals("Apollo", model.name)
            assertFalse(model.hasChanges)
            // And saving writes the user's version, not the one that landed underneath it.
            model.onNameChanged("Apollo 2")
            model.save()
            assertEquals("Apollo 2", repository.storedProjects().single().name)
        }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val repository = FakeProjectRepository()
        val model = ProjectEditorModel(null, repository, TestDateProvider())
        model.onNameChanged("Apollo")
        model.onKeyPrefixChanged("AP")
        repository.failure = AppError.Offline
        model.save()
        assertNotNull(model.errorMessage)

        model.dismissError()
        assertNull(model.errorMessage)
    }
}
