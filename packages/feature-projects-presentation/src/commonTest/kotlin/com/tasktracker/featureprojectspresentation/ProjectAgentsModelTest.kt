package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.randomProjectID
import com.tasktracker.featureprojectsdomain.AgentPairingCode
import com.tasktracker.featureprojectsdomain.AgentPairingPort
import com.tasktracker.featureprojectsdomain.ProjectAgentMachine
import com.tasktracker.featureprojectsdomain.ProjectAgentState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectAgentsModelTest {

    private val projectID: ProjectID = randomProjectID()
    private val now: Instant = Instant.parse("2026-11-03T12:00:30Z")

    @Test
    fun `refresh derives running offline and stopped from one observation`() = runTest {
        val running = machine(
            id = "0195c3a4-0000-7000-8000-0000000000aa",
            name = "desktop",
            lastSeenAt = Instant.parse("2026-11-03T12:00:00Z"),
        )
        val stale = machine(
            id = "0195c3a4-0000-7000-8000-0000000000bb",
            name = "vps",
            lastSeenAt = Instant.parse("2026-11-03T11:50:00Z"),
        )
        val stopped = machine(
            id = "0195c3a4-0000-7000-8000-0000000000cc",
            name = "old-box",
            lastSeenAt = Instant.parse("2026-11-03T12:00:20Z"),
            pausedAt = Instant.parse("2026-11-03T11:00:00Z"),
        )
        val model = ProjectAgentsModel(
            port = FakeAgentPairingPort(listOf(running, stale, stopped)),
            projectID = projectID,
            dateProvider = TestDateProvider(now),
        )

        model.refresh()

        assertFalse(model.isLoading)
        assertEquals(3, model.machines.size)
        assertEquals(ProjectAgentState.Running, model.state(running))
        assertEquals(ProjectAgentState.Offline, model.state(stale))
        assertEquals(ProjectAgentState.Stopped, model.state(stopped))
    }

    @Test
    fun `a failed refresh reports the error and keeps the last good roster`() = runTest {
        val machine = machine(
            id = "0195c3a4-0000-7000-8000-0000000000aa",
            name = "desktop",
            lastSeenAt = now,
        )
        val port = FakeAgentPairingPort(listOf(machine))
        val model = ProjectAgentsModel(port, projectID, TestDateProvider(now))
        model.refresh()

        port.failure = AppError.Validation("the server is unreachable")
        model.refresh()

        assertFalse(model.isLoading)
        assertEquals(listOf(machine), model.machines, "a stale list beats an empty one")
        assertNotNull(model.errorMessage)
    }

    private fun machine(
        id: String,
        name: String,
        lastSeenAt: Instant?,
        pausedAt: Instant? = null,
    ) = ProjectAgentMachine(
        id = AgentRunnerID(id),
        name = name,
        provider = "openai-compatible",
        modelLabel = "llama3.1",
        lastSeenAt = lastSeenAt,
        pausedAt = pausedAt,
    )

    private class FakeAgentPairingPort(
        private val roster: List<ProjectAgentMachine>,
    ) : AgentPairingPort {

        var failure: AppError? = null

        override suspend fun createPairingCode(projectID: ProjectID): AgentPairingCode =
            error("the roster never mints pairing codes")

        override suspend fun machines(projectID: ProjectID): List<ProjectAgentMachine> {
            failure?.let { throw it }
            return roster
        }
    }
}
