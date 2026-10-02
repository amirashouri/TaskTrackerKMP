package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The project switcher, and the "new project" form it can open.
 *
 * Two things here are decisions rather than plumbing. The switcher *does not write* when
 * the user picks a project, because routing owns that; and it *ignores* work item
 * changes, which is the one place in this feature that filters a change out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectPickerModelTest {

    // MARK: Listing

    @Test
    fun `the picker lists the projects`() = runTest {
        val repository = FakeProjectRepository()
        repository.seed(testProject(name = "Apollo", keyPrefix = "AP"))
        repository.seed(testProject(name = "Gemini", keyPrefix = "GE"))

        val model = ProjectPickerModel(repository)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(listOf("Apollo", "Gemini"), model.projects.map { it.name })
        assertEquals(listOf("AP", "GE"), model.projects.map { it.keyPrefix })
    }

    @Test
    fun `archived projects are not offered`() = runTest {
        val repository = FakeProjectRepository()
        repository.seed(testProject(name = "Live"))
        repository.seed(testProject(name = "Filed", isArchived = true))

        val model = ProjectPickerModel(repository)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(listOf("Live"), model.projects.map { it.name })
    }

    @Test
    fun `the picker starts with nothing chosen`() = runTest {
        val repository = FakeProjectRepository()
        repository.seed(testProject())

        val model = ProjectPickerModel(repository)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertNull(model.selectedProjectID)
    }

    // MARK: The filter

    /**
     * Work item changes are ignored, and this is the one place in the feature that does it.
     *
     * A work item is never added to or removed from the project list, so a change carrying
     * one cannot change what the picker offers. The switcher is the most-revisited surface
     * in the app and the one with the longest-lived scope, so a board drag waking a project
     * switcher is the most expensive avoidable reload in the feature.
     *
     * The contrast with [ProjectsListModelTest] is the point: that list ignores nothing,
     * because its *counts* move when a work item does.
     */
    @Test
    fun `work item changes are ignored`() = runTest {
        val repository = FakeProjectRepository()
        val model = ProjectPickerModel(repository)

        assertFalse(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, testProject().id)))
        assertTrue(model.isRelevant(ProjectChange(ProjectChange.Kind.Projects, testProject().id)))
    }

    @Test
    fun `a project change refreshes the picker`() = runTest {
        val repository = FakeProjectRepository()
        repository.seed(testProject(name = "Apollo"))
        val model = ProjectPickerModel(repository)
        model.observe(eagerScope())
        advanceUntilIdle()
        assertEquals(listOf("Apollo"), model.projects.map { it.name })

        repository.createProject("Gemini", "GE", "", TestDateProvider().now())
        advanceUntilIdle()

        assertEquals(listOf("Apollo", "Gemini"), model.projects.map { it.name })
    }

    // MARK: Selection

    /**
     * Choosing a project records it locally and writes nothing.
     *
     * Routing decides what a selection means - a tab switch on one tab, a push on another,
     * nothing at all on a third - so a picker that persisted the choice itself would take
     * that decision away, and a test asserting on the store here would be asserting that
     * the wrong layer had taken over.
     */
    @Test
    fun `selecting a project records it without writing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo")
        repository.seed(project)
        val model = ProjectPickerModel(repository)
        model.observe(eagerScope())
        advanceUntilIdle()

        model.select(project.id)

        assertEquals(project.id, model.selectedProjectID)
        assertEquals(emptyList(), repository.writes)
    }

    @Test
    fun `selecting nothing clears the choice`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = ProjectPickerModel(repository)
        model.observe(eagerScope())
        advanceUntilIdle()
        model.select(project.id)

        model.select(null)

        assertNull(model.selectedProjectID)
    }
}

/** The "new project" form the picker can open. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewProjectModelTest {

    @Test
    fun `a new form starts empty and cannot be created`() = runTest {
        val model = NewProjectModel(FakeProjectRepository(), TestDateProvider())

        assertEquals("", model.name)
        assertEquals("", model.keyPrefix)
        assertFalse(model.canCreate)
    }

    /**
     * The prefix is suggested from the name, so the common case is a name and nothing else.
     *
     * Keys are the one thing in this app a user has to look up, and a user who has to
     * invent a prefix will write "PROJECT" or "1" and then have to live with it.
     */
    @Test
    fun `the prefix is suggested from the name`() = runTest {
        val model = NewProjectModel(FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("Task Tracker")

        assertEquals("TT", model.keyPrefix)
        assertTrue(model.canCreate)
    }

    /** Once the user has typed a prefix, a later name change must not overwrite it. */
    @Test
    fun `a typed prefix is not overwritten by a later name`() = runTest {
        val model = NewProjectModel(FakeProjectRepository(), TestDateProvider())
        model.onNameChanged("Task Tracker")
        model.onKeyPrefixChanged("TTK")

        model.onNameChanged("Task Tracker Renamed")

        assertEquals("TTK", model.keyPrefix)
    }

    /**
     * A one-word name falls back to that word's first two characters.
     *
     * "Apollo" has only one letter to take from two words, and a single character would be
     * rejected by `ProjectRules` - so the form would be filled in and unsaveable, with the
     * user to work out why. `AP` is the smallest guess the store will accept.
     */
    @Test
    fun `a one word name suggests its first two letters`() = runTest {
        val model = NewProjectModel(FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("Apollo")

        assertEquals("AP", model.keyPrefix)
    }

    @Test
    fun `digits count as usable characters`() = runTest {
        val model = NewProjectModel(FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("123")

        assertEquals("12", model.keyPrefix)
    }

    /**
     * A name with nothing to take a prefix from suggests nothing, and asks instead.
     *
     * Inventing one would be worse: the field would be filled in with something arbitrary
     * that the user then has to recognise as arbitrary and replace.
     */
    @Test
    fun `a name with no usable characters suggests nothing`() = runTest {
        val model = NewProjectModel(FakeProjectRepository(), TestDateProvider())

        model.onNameChanged("!! ??")

        assertEquals("", model.keyPrefix)
        assertFalse(model.canCreate)
    }

    @Test
    fun `creating returns the new project's id`() = runTest {
        val repository = FakeProjectRepository()
        val model = NewProjectModel(repository, TestDateProvider())
        model.onNameChanged("Task Tracker")

        val id = model.create()

        assertEquals(repository.storedProjects().single().id, id)
        assertEquals(id, model.createdProjectID)
    }

    /**
     * The notes reach the created project.
     *
     * `NewProjectSheet` has always drawn a details field, and a model without one left the
     * shell with two bad options: a field that accepted input and threw it away, or a sheet
     * with the field removed. The store was always willing to record notes — `createProject`
     * takes them — so the gap was the form's, not the store's.
     */
    @Test
    fun `the details are carried into the created project`() = runTest {
        val repository = FakeProjectRepository()
        val model = NewProjectModel(repository, TestDateProvider())
        model.onNameChanged("Task Tracker")
        model.onDetailsChanged("Ships behind the sign-in gate.")

        model.create()

        assertEquals("Ships behind the sign-in gate.", repository.storedProjects().single().details)
    }

    /** A form nobody annotated still creates cleanly, so the new field is not a required one. */
    @Test
    fun `details are optional`() = runTest {
        val repository = FakeProjectRepository()
        val model = NewProjectModel(repository, TestDateProvider())
        model.onNameChanged("Task Tracker")

        assertNotNull(model.create())
        assertEquals("", repository.storedProjects().single().details)
    }

    @Test
    fun `a failed create reports the failure and returns nothing`() = runTest {
        val repository = FakeProjectRepository()
        val model = NewProjectModel(repository, TestDateProvider())
        model.onNameChanged("Task Tracker")
        repository.failure = AppError.Offline

        assertNull(model.create())
        assertNotNull(model.errorMessage)
        assertNull(model.createdProjectID)
    }

    /**
     * A duplicate prefix is refused, because keys would be ambiguous.
     *
     * The store owns this rule rather than the model, so the model's job is to surface the
     * refusal and leave the form intact for a correction - not to pre-empt it with its own
     * weaker check.
     */
    @Test
    fun `a refused create leaves the form intact for a correction`() = runTest {
        val repository = FakeProjectRepository()
        val model = NewProjectModel(repository, TestDateProvider())
        model.onNameChanged("Task Tracker")
        repository.failure = AppError.Conflict("That key prefix is already in use.")

        assertNull(model.create())
        assertEquals("Task Tracker", model.name)
        assertEquals("TT", model.keyPrefix)
        assertNotNull(model.errorMessage)
    }
}
