package com.tasktracker.featuresettingsdomain

import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * One paired agent machine, as Settings shows it.
 *
 * This is the app's own vocabulary for the server's runner row. `pausedAt` and
 * `revokedAt` are deliberately distinct: stopped is reversible and keeps the
 * machine's token, while revoked retires it for good. Settings only ever offers a
 * command for a live machine.
 */
data class AgentMachine(
    val id: AgentRunnerID,
    val name: String,
    val provider: String = "",
    val modelLabel: String = "",
    val lastSeenAt: Instant? = null,
    val pausedAt: Instant? = null,
    val revokedAt: Instant? = null,
    /**
     * The project this machine was paired to, or `null` for a legacy machine-wide
     * registration or a server that predates the key.
     */
    val projectID: ProjectID? = null,
) {
    /** A revoked machine is history, not something the agent section can operate. */
    val isLive: Boolean get() = revokedAt == null

    val isPaused: Boolean get() = pausedAt != null

    /** What the row shows under the machine name, e.g. `openai-compatible · llama3.1`. */
    val modelSummary: String
        get() = listOf(provider, modelLabel).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * What the Settings agent block renders.
 *
 * ## The state is derived, and the observation time is part of it
 *
 * The server owns whether a machine is stopped, but "running" is a freshness
 * question the server does not answer: a runner touches its row on every claim
 * long poll, so a machine that has not been seen for [ONLINE_WINDOW] is idle or
 * gone. Carrying [observedAt] with the list makes that comparison testable without
 * a clock inside this value, and keeps a stopped machine from ever rendering as
 * running.
 */
data class AgentOverview(
    val machines: List<AgentMachine> = emptyList(),
    val observedAt: Instant = Instant.fromEpochSeconds(0),
) {
    /** Machines the section can name and operate on. Revoked rows are history. */
    val liveMachines: List<AgentMachine>
        get() = machines.filter { it.isLive }

    /**
     * The one word beside the Agent row.
     *
     * `NotConfigured` wins over everything: a machine cannot be outdated before it
     * has been paired. `Stopped` is all of the account's live machines paused, so a
     * partly stopped fleet still reads as running/offline rather than as stopped.
     */
    val state: AgentState
        get() = when {
            liveMachines.isEmpty() -> AgentState.NotConfigured
            liveMachines.all { it.isPaused } -> AgentState.Stopped
            liveMachines.any { !it.isPaused && it.isSeenWithin(observedAt) } -> AgentState.Running
            else -> AgentState.Offline
        }

    fun canStop(machine: AgentMachine): Boolean = machine.isLive && !machine.isPaused

    fun canStart(machine: AgentMachine): Boolean = machine.isLive && machine.isPaused

    /**
     * Replaces one machine after a stop/start, so the row moves without a reload.
     *
     * Appended when it is not already in the snapshot: a command can be the first
     * thing a screen does after opening, and dropping the row the server just
     * confirmed would show a stop that appears not to have happened.
     */
    fun withMachine(machine: AgentMachine, at: Instant): AgentOverview = copy(
        machines = if (machines.any { it.id == machine.id }) {
            machines.map { if (it.id == machine.id) machine else it }
        } else {
            machines + machine
        },
        observedAt = at,
    )

    private fun AgentMachine.isSeenWithin(now: Instant): Boolean {
        val seen = lastSeenAt ?: return false
        return now - seen <= ONLINE_WINDOW
    }

    companion object {
        /**
         * How stale a heartbeat may be before the section stops calling the agent
         * running. A worker long-polls its claim route and touches `last_seen_at` on
         * every wake-up, so two minutes is several missed polls, not one slow request.
         */
        val ONLINE_WINDOW: Duration = 2.minutes
    }
}

/** The Agent row's state, in the model's words; the screen owns the sentence. */
enum class AgentState { NotConfigured, Running, Offline, Stopped }

/**
 * The agent surface's network half, or `null` in a build with no API root.
 *
 * A gateway rather than a port on the data class for the reason [UserProfileGateway]
 * exists: this module may name the shape, and only the data module may know that the
 * shape is answered over HTTP.
 */
interface AgentSettingsGateway {

    /** The signed-in account's paired machines, revoked ones included. */
    @Throws(AppError::class, CancellationException::class)
    suspend fun loadMachines(): List<AgentMachine>

    /**
     * Stops or starts one machine.
     *
     * This is a server-side gate, not process control: the worker runs on the
     * customer's machine and observes the change on its next claim. The returned
     * value is the server's confirmed row, which is what the screen renders.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun setPaused(machineID: AgentRunnerID, paused: Boolean): AgentMachine
}
