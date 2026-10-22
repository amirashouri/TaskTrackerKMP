package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomTagID
import com.tasktracker.core.randomUserID
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.domain.TagRules
import com.tasktracker.featureprojectsdomain.AdminProject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The admin project editor's model.
 *
 * The behaviour worth pinning is not the happy path: it is that the save carries the version the
 * row was read at, and that a project the server no longer has reads as *missing* rather than as
 * a failure or an empty new project. Both are invisible in a screenshot and one of them is
 * silent data loss when it regresses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminProjectEditorModelTest {

    private val id: ProjectID = randomProjectID()
    private val memberID = randomUserID()

    private fun project(name: String = "Apollo Programme") = Project(
        id = id,
        keyPrefix = "AP",
        name = name,
        details = "Two milestones.",
        memberIDs = listOf(memberID),
        isArchived = true,
        nextItemNumber = 7,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun port(version: String = "v1") = RecordingAdminProjectsPort(
        projectByID = mapOf(id to AdminProject(project(), version)),
    )

    @Test
    fun `the editor seeds itself from the admin API`() = runTest {
        val port = port()
        val model = AdminProjectEditorModel(id, port)

        model.load()

        assertEquals("Apollo Programme", model.name)
        assertEquals("AP", model.keyPrefix)
        assertEquals("Two milestones.", model.details)
        assertFalse(model.isLoading)
        assertFalse(model.isMissing)
        assertEquals(listOf(id), port.projectCalls)
    }

    @Test
    fun `a project the server no longer has is missing rather than a failure or a blank draft`() =
        runTest {
            val model = AdminProjectEditorModel(id, RecordingAdminProjectsPort())

            model.load()

            assertTrue(model.isMissing)
            assertFalse(model.isLoading)
            assertEquals(null, model.errorMessage)
            assertFalse(model.canSave)
        }

    @Test
    fun `an unchanged form cannot be saved`() = runTest {
        val model = AdminProjectEditorModel(id, port())
        model.load()

        assertFalse(model.hasChanges)
        assertFalse(model.canSave)
    }

    @Test
    fun `saving sends the version it was seeded with and only the fields it changed`() = runTest {
        val port = port(version = "seeded-v1")
        val model = AdminProjectEditorModel(id, port)
        model.load()

        model.onNameChanged("Apollo II")
        model.onDetailsChanged("Three milestones.")
        assertTrue(model.canSave)
        assertTrue(model.save())

        val (sent, version) = port.updateCalls.single()
        assertEquals("seeded-v1", version)
        assertEquals("Apollo II", sent.name)
        assertEquals("Three milestones.", sent.details)
        // The body is the whole project, so everything the form does not offer has to travel
        // through from the seed: a dropped field here is a field the server clears.
        assertEquals(listOf(memberID), sent.memberIDs)
        assertTrue(sent.isArchived)
        assertEquals(7, sent.nextItemNumber)
        assertEquals(createdAt, sent.createdAt)
    }

    @Test
    fun `a second save carries the version the first one returned`() = runTest {
        val port = RecordingAdminProjectsPort(
            projectByID = mapOf(id to AdminProject(project(), "v1")),
            updateResult = { updated, _ -> AdminProject(updated, "v2") },
        )
        val model = AdminProjectEditorModel(id, port)
        model.load()

        model.onNameChanged("Apollo II")
        assertTrue(model.save())

        // The server now holds v2, so a form still sending v1 would be rejected as a conflict
        // on an edit nobody else made.
        model.onDetailsChanged("Edited again.")
        assertTrue(model.save())

        assertEquals(listOf("v1", "v2"), port.updateCalls.map { it.second })
    }

    @Test
    fun `a save the server refuses reports the error instead of claiming success`() = runTest {
        val port = RecordingAdminProjectsPort(
            projectByID = mapOf(id to AdminProject(project(), "v1")),
            updateResult = { _, _ -> throw AppError.Conflict("project") },
        )
        val model = AdminProjectEditorModel(id, port)
        model.load()
        model.onNameChanged("Apollo II")

        assertFalse(model.save())
        assertEquals(AppError.Conflict("project").message, model.errorMessage)
        assertFalse(model.isSaving)
    }

    @Test
    fun `adding a tag marks the editor dirty and the save carries the catalog`() = runTest {
        val port = port()
        val model = AdminProjectEditorModel(id, port)
        model.load()
        assertFalse(model.hasChanges)

        assertTrue(model.addTag("  Bug  ", TagColor.Red, TagIcon.Bug))

        assertTrue(model.hasChanges, "a catalog edit is a reason to enable Save")
        val tag = model.tags.single()
        assertEquals("Bug", tag.name, "the name is normalised as it enters the draft")
        assertEquals(TagColor.Red, tag.color)
        assertEquals(TagIcon.Bug, tag.icon)

        assertTrue(model.save())
        assertEquals(listOf(tag), port.updateCalls.single().first.tags)
    }

    @Test
    fun `the tag composer refuses a blank name a duplicate and a full catalog`() = runTest {
        val model = AdminProjectEditorModel(id, port())
        model.load()

        assertFalse(model.addTag("   ", TagColor.Red, TagIcon.Bug), "a blank tag is not a tag")
        assertTrue(model.addTag("Bug", TagColor.Red, TagIcon.Bug))
        assertFalse(
            model.addTag("bug", TagColor.Blue, TagIcon.Bell),
            "case-insensitive: two Bug chips are indistinguishable at 12dp",
        )
        repeat(TagRules.MAX_PER_PROJECT - 1) { index ->
            assertTrue(model.addTag("Tag $index", TagColor.Blue, TagIcon.Book))
        }
        assertFalse(model.addTag("One too many", TagColor.Gray, TagIcon.Star))
        assertEquals(TagRules.MAX_PER_PROJECT, model.tags.size)
    }

    @Test
    fun `removing a tag marks the editor dirty and the save carries the removal`() = runTest {
        val seeded = project().apply {
            tags = listOf(
                ProjectTag(randomTagID(), "Bug", TagColor.Red, TagIcon.Bug),
                ProjectTag(randomTagID(), "Mobile", TagColor.Blue, TagIcon.Mobile),
            )
        }
        val port = RecordingAdminProjectsPort(
            projectByID = mapOf(id to AdminProject(seeded, "v1")),
            updateResult = { updated, _ -> AdminProject(updated, "v2") },
        )
        val model = AdminProjectEditorModel(id, port)
        model.load()

        model.removeTag(seeded.tags.first().id)

        assertTrue(model.hasChanges)
        assertTrue(model.save())
        val sent = port.updateCalls.single().first
        assertEquals(listOf("Mobile"), sent.tags.map { it.name })
    }

    private companion object {
        val createdAt: Instant = Instant.fromEpochSeconds(1_700_000_000)
    }
}
