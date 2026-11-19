package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.domain.SprintParticipation
import com.tasktracker.domain.sprintParticipation
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureteamsdomain.TeamPort

/** Everything the progress screen needs, with ids already resolved to display names. */
data class SprintMemberProgressView(
    val sprintName: String,
    val participation: SprintParticipation,
    val memberNames: Map<UserID, String>,
)

/**
 * Reads a sprint's roster and work once per refresh and derives participation from them.
 *
 * The model deliberately holds no clock and no write use cases: progress is a read of what
 * was already decided, and making it editable here would blur the line between the detail
 * screen (where the roster is managed before lock) and this report.
 */
class SprintMemberProgressModel(
    val sprintID: SprintID,
    repository: ProjectRepositoryPort,
    private val teamPort: TeamPort?,
) : ObservingProjectModel(repository) {

    var view: SprintMemberProgressView? by mutableStateOf(null)
        private set

    override fun isRelevant(change: ProjectChange): Boolean = true

    override suspend fun reload() {
        val sprint = repository.sprint(sprintID) ?: run {
            view = null
            return
        }
        val items = repository.sprintWorkItems(sprintID)
        val participation = sprintParticipation(repository.sprintMembers(sprintID), items)
        val ids = participation.members.map { it.member.userID }
        val names = if (ids.isEmpty() || teamPort == null) {
            emptyMap()
        } else {
            teamPort.users(ids).associate { it.id to it.preferredGreeting }
        }
        view = SprintMemberProgressView(
            sprintName = sprint.name,
            participation = participation,
            memberNames = names,
        )
    }
}
