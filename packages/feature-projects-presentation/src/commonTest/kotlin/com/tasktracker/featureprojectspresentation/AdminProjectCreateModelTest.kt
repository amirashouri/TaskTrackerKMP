package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomUserID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.AdminProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The admin projects section's create model.
 *
 * The claims worth pinning are the ones a screenshot cannot see. The create lands on the server
 * rather than in the offline-first outbox, so what arrives there is the *normalised* form — the
 * same rules the board's create reaches through the `CreateProject` use case, which this path has
 * no use case of its own to pass through. The wide window's pane is never torn down, so a create
 * has to empty the draft itself; a model that did not would send the project just made a second
 * time on the next tap. And a refusal has to keep the draft, because the admin's next move is to
 * correct it rather than retype it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminProjectCreateModelTest {

    private val memberID = randomUserID()

    private fun storedProject(): AdminProject = AdminProject(
        project = Project(
            id = randomProjectID(),
            keyPrefix = "GE",
            name = "Gemini",
            details = "",
            memberIDs = emptyList(),
            isArchived = false,
            nextItemNumber = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
        version = "v1",
    )

    @Test
    fun `typing a name fills the key prefix in and typing over it stops the name overwriting it`() =
        runTest {
            val model = AdminProjectCreateModel(RecordingAdminProjectsPort())

            model.onNameChanged("Apollo Programme")
            assertEquals("AP", model.keyPrefix)

            model.onKeyPrefixChanged("MOON")
            model.onNameChanged("Apollo Mission")
            assertEquals(
                "MOON",
                model.keyPrefix,
                "the prefix was typed over and the next keystroke in the name overwrote it",
            )
        }

    @Test
    fun `a create sends the normalised fields and the members the picker chose`() = runTest {
        val port = RecordingAdminProjectsPort(created = storedProject())
        val model = AdminProjectCreateModel(port)

        model.onNameChanged("  gemini  ")
        model.onDetailsChanged("  Two milestones.  ")
        model.onMemberIDsChanged(listOf(memberID, memberID))

        assertTrue(model.create())

        val sent = port.createCalls.single()
        assertEquals("gemini", sent.name)
        // The prefix is upper-cased by the rule rather than by the field, which is read-only: the
        // value the pane shows is the suggestion, and this is what is actually created.
        assertEquals("GE", sent.keyPrefix)
        assertEquals("Two milestones.", sent.details)
        assertEquals(
            listOf(memberID),
            sent.memberIDs,
            "the same member was selected twice and travelled to the server as two rows",
        )

        assertEquals(1, model.completedCreates)
        assertNull(model.errorMessage)
        assertFalse(model.isCreating)
    }

    @Test
    fun `a successful create empties the draft the pane it was drawn in will not empty`() = runTest {
        val model = AdminProjectCreateModel(RecordingAdminProjectsPort(created = storedProject()))
        model.onNameChanged("Gemini")
        model.onDetailsChanged("Two milestones.")
        model.onMemberIDsChanged(listOf(memberID))

        model.create()

        assertEquals(
            "",
            model.name,
            "the pane the form is drawn in is never torn down, so a create that leaves the name " +
                "behind makes the next tap on Create send the project just made a second time",
        )
        assertEquals("", model.keyPrefix)
        assertEquals("", model.details)
        assertTrue(model.memberIDs.isEmpty())
        assertFalse(
            model.canSave,
            "an emptied form still reports something to save, so the next tap is a second create",
        )
    }

    @Test
    fun `a create the server refuses keeps the draft and reports the error`() = runTest {
        val port = RecordingAdminProjectsPort(createFailure = AppError.Conflict("project"))
        val model = AdminProjectCreateModel(port)
        model.onNameChanged("Gemini")

        assertFalse(model.create())

        assertEquals(AppError.Conflict("project").message, model.errorMessage)
        assertEquals(0, model.completedCreates)
        assertEquals("Gemini", model.name)
        assertTrue(
            model.canSave,
            "a refused create left the form unsaveable, so correcting the name is impossible",
        )

        model.closeAddForm()
        assertNull(
            model.errorMessage,
            "the form was closed with a failure still on it, so reopening it shows the message " +
                "for a create nobody is attempting",
        )
    }

    @Test
    fun `a form with nothing in it cannot create anything`() = runTest {
        val port = RecordingAdminProjectsPort(created = storedProject())
        val model = AdminProjectCreateModel(port)

        assertFalse(model.canSave)
        assertFalse(model.create())
        assertTrue(port.createCalls.isEmpty())
    }

    @Test
    fun `a name too short to make a key prefix is refused before the port sees it`() = runTest {
        val port = RecordingAdminProjectsPort(created = storedProject())
        val model = AdminProjectCreateModel(port)

        model.onNameChanged("A")
        assertEquals("A", model.keyPrefix)
        assertTrue(model.canSave, "the form has a name, so Create is offered")

        assertFalse(model.create())

        assertEquals(
            "A key prefix must be 2-10 letters or digits.",
            model.errorMessage,
            "the one-character prefix reached the port, so this pane would create a project whose " +
                "keys the board's own create refuses to issue",
        )
        assertTrue(port.createCalls.isEmpty())
    }

    @Test
    fun `cancellation is not reported as a failure`() = runTest {
        val model = AdminProjectCreateModel(
            RecordingAdminProjectsPort(createFailure = CancellationException("left the screen")),
        )
        model.onNameChanged("Gemini")

        assertFailsWith<CancellationException> { model.create() }

        assertNull(
            model.errorMessage,
            "the coroutine was cancelled and the model reported it as a create that failed",
        )
    }

    private companion object {
        val createdAt: Instant = Instant.fromEpochSeconds(1_700_000_000)
    }
}
