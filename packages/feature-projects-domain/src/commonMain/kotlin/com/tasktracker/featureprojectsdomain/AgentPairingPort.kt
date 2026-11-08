package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import kotlin.coroutines.cancellation.CancellationException
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
 * The project-scoped half of pairing: asking the server for a code an admin can hand
 * to the machine that holds the repository.
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
}
