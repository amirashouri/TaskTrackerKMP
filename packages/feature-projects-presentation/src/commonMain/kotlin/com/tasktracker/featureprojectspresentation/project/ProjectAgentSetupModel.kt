package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.AgentPairingCode
import com.tasktracker.featureprojectsdomain.AgentPairingPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * The state of one project's pairing sheet: the code the server just issued, whether a
 * request is in flight, and the failure to retry.
 *
 * The code is the only value here that is sensitive, and it lives in this model for as
 * long as the sheet is open. It is never written to the store, the outbox or a log —
 * closing the sheet drops it, and reopening asks for a new one because the server kept
 * only the hash.
 */
class ProjectAgentSetupModel(
    private val port: AgentPairingPort,
    private val projectID: ProjectID,
) : ProjectModel() {

    var pairingCode by mutableStateOf<AgentPairingCode?>(null)
        private set

    var isRequesting by mutableStateOf(false)
        private set

    /**
     * Issues a new code, replacing any previous one.
     *
     * A second tap while the first request is in flight is ignored: two codes for one
     * sheet would leave the displayed one ambiguous, and only one of them can be the
     * code the admin read out.
     */
    suspend fun request() {
        if (isRequesting) return
        isRequesting = true
        dismissError()
        try {
            pairingCode = port.createPairingCode(projectID)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            onFailure(failure)
        } finally {
            isRequesting = false
        }
    }

    /** Drops the code when the sheet closes, so it does not outlive the screen. */
    fun clear() {
        pairingCode = null
        dismissError()
    }
}
