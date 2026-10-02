package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.featureprojectsdomain.ProjectChange
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

    @Test
    fun `a name and a prefix make a new form saveable`() = runTest {
        val model = ProjectEditorModel(null, FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("Apollo")
        assertFalse(model.canSave, "a name alone is not enough - the prefix is not derivable yet")
        model.onKeyPrefixChanged("AP")
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
