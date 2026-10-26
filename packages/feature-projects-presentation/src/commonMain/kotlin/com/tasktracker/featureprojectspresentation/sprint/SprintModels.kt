package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Sprint
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.domain.sprintSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Sprint list row containing window dates, raw point totals, and derived progress fractions.
 */
data class SprintListRow(
    val sprint: Sprint,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val committedStoryPoints: Int,
    val achievedStoryPoints: Int,
) {
    val hasGoal: Boolean get() = sprint.goalStoryPoints > 0

    /** Progress as a ratio (unclamped to 1.0 to show over-achievement), or null if no goal is set. */
    val progressFraction: Float?
        get() = if (!hasGoal) {
            null
        } else {
            achievedStoryPoints.toFloat() / sprint.goalStoryPoints.toFloat()
        }
}

/** Converts database [Instant] to [LocalDate] using given [TimeZone]. */
internal fun sprintLocalDate(instant: Instant, timeZone: TimeZone): LocalDate =
    instant.toLocalDateTime(timeZone).date

/** Converts [LocalDate] to start-of-day [Instant] in given [TimeZone]. */
internal fun sprintInstant(date: LocalDate, timeZone: TimeZone): Instant =
    date.atStartOfDayIn(timeZone)

/**
 * Presentation model partitioning project sprints into active phases (In-Progress, Scheduled, Finished) and Archive.
 */
class SprintListModel(
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    var inProgressRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    var scheduledRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    var finishedRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    var archivedRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    /** Returns null while loading to prevent flashing empty states on cold starts. */
    val activeIsEmpty: Boolean? get() = if (isLoading) null else {
        inProgressRows.isEmpty() && scheduledRows.isEmpty() && finishedRows.isEmpty()
    }

    val archivedIsEmpty: Boolean? get() = if (isLoading) null else archivedRows.isEmpty()

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        val allSprints = repository.sprints(projectID)
        val items = if (allSprints.isEmpty()) emptyList() else repository.workItems(projectID)

        val rowsByID = allSprints.associate { sprint ->
            val summary = sprintSummary(sprint, items)
            sprint.id to SprintListRow(
                sprint = sprint,
                startDate = sprintLocalDate(sprint.startDate, timeZone),
                endDate = sprintLocalDate(sprint.endDate, timeZone),
                committedStoryPoints = summary.committedStoryPoints,
                achievedStoryPoints = summary.achievedStoryPoints,
            )
        }

        val now = dateProvider.now()
        val inProgressList = mutableListOf<SprintListRow>()
        val scheduledList = mutableListOf<SprintListRow>()
        val finishedList = mutableListOf<SprintListRow>()
        val archivedList = mutableListOf<SprintListRow>()

        for (sprint in allSprints) {
            val row = rowsByID.getValue(sprint.id)
            if (sprint.isArchived) {
                archivedList.add(row)
            } else {
                when {
                    sprint.isFinished -> finishedList.add(row)
                    sprint.startDate > now -> scheduledList.add(row)
                    else -> inProgressList.add(row)
                }
            }
        }

        inProgressRows = inProgressList
        scheduledRows = scheduledList
        finishedRows = finishedList
        archivedRows = archivedList
    }
}