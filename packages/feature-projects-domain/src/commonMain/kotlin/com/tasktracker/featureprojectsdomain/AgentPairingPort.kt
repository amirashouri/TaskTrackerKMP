package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A short-lived code that lets one machine join this project.
 *
 * The code is a one-time credential, not a runner token: the server stores only its
 * hash, redeeming it mints the machine's long-lived token, and it expires. It is held
 * in memory for the pairing sheet and never written to the project row, the outbox or
 * a log.
 */
data class AgentPairingCode(
    val code: String,
    val projectID: ProjectID,
    val expiresAt: Instant,
)

/**
 * One live machine paired to a project, as the project's agent roster shows it.
 *
 * The server's runner row also carries revocation, but a revoked machine is history
 * and never reaches this value. Health is derived from [lastSeenAt] because the
 * server does not push a connection state: a runner touches its row on every claim
 * poll, so a stale timestamp is what "not connected" means in practice.
 */
data class ProjectAgentMachine(
    val id: AgentRunnerID,
    val name: String,
    val provider: String = "",
    val modelLabel: String = "",
    val lastSeenAt: Instant? = null,
    val pausedAt: Instant? = null,
) {
    /** What the row shows under the machine name, e.g. `openai-compatible · llama3.1`. */
    val modelSummary: String
        get() = listOf(provider, modelLabel).filter { it.isNotBlank() }.joinToString(" · ")

    /** Whether this machine looks reachable at [now]. A paused machine is stopped by its admin. */
    fun state(now: Instant): ProjectAgentState = when {
        pausedAt != null -> ProjectAgentState.Stopped
        lastSeenAt != null && now - lastSeenAt <= ONLINE_WINDOW -> ProjectAgentState.Running
        else -> ProjectAgentState.Offline
    }

    companion object {
        /**
         * How stale a heartbeat may be before the roster stops calling the machine running.
         * Two minutes is several missed claim polls, not one slow request.
         */
        val ONLINE_WINDOW: Duration = 2.minutes
    }
}

/** The roster's three words; the screen owns the localized sentence. */
enum class ProjectAgentState { Running, Offline, Stopped }

/**
 * The project-scoped half of pairing: asking the server for a code an admin can hand
 * to the machine that holds the repository, and reading back the machines already
 * bound to the project.
 *
 * Deliberately separate from [AgentRunsPort] and from the Settings machine list. A
 * run is scoped by membership, while pairing is a project-administration act: the
 * server requires the caller to be a member of [createPairingCode]'s project and
 * binds the resulting runner to it, so a machine cannot choose a different project
 * for itself.
 */
interface AgentPairingPort {

    /**
     * Issues one single-use code for [projectID].
     *
     * A lost response means asking for a new code, not recovering the old one — the
     * server keeps only the hash.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun createPairingCode(projectID: ProjectID): AgentPairingCode

    /**
     * The live machines paired to [projectID], newest server order preserved.
     *
     * The server's runner route is account-scoped, so the project filter is applied
     * where the rows are mapped. Revoked machines are omitted: the roster is an
     * operating surface, not an audit trail.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun machines(projectID: ProjectID): List<ProjectAgentMachine>
}
