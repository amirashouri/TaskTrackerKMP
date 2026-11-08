package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.AgentPairingPort
import com.tasktracker.featureprojectsdomain.ProjectAgentMachine
import com.tasktracker.featureprojectsdomain.ProjectAgentState
import kotlin.time.Instant

/**
 * The machines paired to one project, as the admin editor's agent block shows them.
 *
 * Health is derived at [refresh] from one observation time, the same way Settings
 * derives its aggregate: carrying the time with the list keeps two rows on one
 * screen from disagreeing about what "recently seen" means.
 *
 * This is a read surface only. Pairing mints a code and dispatch starts a run; neither
 * belongs to a roster refresh.
 */
class ProjectAgentsModel(
    private val port: AgentPairingPort,
    private val projectID: ProjectID,
    private val dateProvider: DateProvider,
) : ProjectModel() {

    var machines by mutableStateOf<List<ProjectAgentMachine>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var observedAt: Instant = Instant.fromEpochSeconds(0)

    /** Re-reads the roster. A failed refresh keeps the last good list on screen. */
    suspend fun refresh() {
        isLoading = true
        try {
            val loaded = reporting { port.machines(projectID) } ?: return
            machines = loaded
            observedAt = dateProvider.now()
        } finally {
            isLoading = false
        }
    }

    /** The state of [machine] at the last successful read. */
    fun state(machine: ProjectAgentMachine): ProjectAgentState = machine.state(observedAt)
}
