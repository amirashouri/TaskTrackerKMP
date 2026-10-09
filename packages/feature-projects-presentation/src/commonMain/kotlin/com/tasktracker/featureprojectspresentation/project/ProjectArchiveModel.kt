package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.ArchiveProjection
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.datetime.TimeZone

/**
 * One project's archive: finished work, filed by the month it left the flow.
 *
 * ## Read-only, and that is a decision rather than an omission
 *
 * The archive holds `WorkItemGrouping.resolved` - terminal tasks only - so every action
 * that could change a task's status is already meaningless here. The one that is *not*
 * meaningless is reopening, and it is left out on purpose: reopening needs a target
 * status, `WorkItemRules.canTransition` is deliberately permissive until the product
 * decides which moves are legal, and guessing "back to To Do" would put that decision in
 * a view model where it cannot be reviewed as one. When the policy is written down, this
 * model gains a `reopen` beside its reads and the guard has somewhere to live.
 *
 * So the screen's rule - actions that only make sense for live work are hidden - resolves
 * to "no actions at all", and [allItemsAreFiledAway] states that as a fact a test can
 * assert rather than leaving a screen to discover it by checking each row.
 *
 * ## The zone is a parameter
 *
 * [ArchiveProjection] cuts months in an injected [TimeZone], and this model takes it
 * rather than reading `TimeZone.currentSystemDefault()` itself, for two reasons. A test
 * can then pin it, so an archive assertion does not depend on the machine's zone. And a
 * month boundary is a real answer that depends on the answerer: a task filed at 23:30 UTC
 * on the last day of a month falls in the *next* month for anyone a few hours east, and
 * the same archive would then list one task under two different sections depending on
 * where the reader is.
 */
class ProjectArchiveModel(
    /** The project whose archive this is. */
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    @Suppress("unused") private val dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    /**
     * The archive, or an empty projection until the first read.
     *
     * The default zone is the model's, so an empty projection and a loaded one are cut the
     * same way - which is what keeps equality honest, since the projection carries its
     * zone and a default-constructed one would otherwise claim UTC's answer.
     */
    var projection: ArchiveProjection by mutableStateOf(
        ArchiveProjection(project = null, items = emptyList(), timeZone = timeZone),
    )
        private set

    /**
     * Whether every task in the project is already filed away.
     *
     * True for an empty project too, vacuously. A screen asserts on this to decide whether
     * the "these are live tasks" affordances have anything to hide, and an archive that
     * has not loaded yet must not read as "some of these can be changed".
     *
     * Deliberately [ArchiveProjection.items] - *all* of them - rather than
     * [ArchiveProjection.months]. The months hold terminal work by construction, so
     * checking them asks "are all the archived tasks archived?", which is true by
     * definition and can never be false. The question a screen actually has is "is
     * anything left in the flow?", and the answer to that comes from the whole list: a
     * project with one task still sitting in the backlog has not filed everything away,
     * however much history it has behind it.
     */
    val allItemsAreFiledAway: Boolean
        get() = projection.items.all { it.status.isTerminal }

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        projection = ArchiveProjection(
            project = repository.project(projectID),
            items = repository.workItems(projectID),
            timeZone = timeZone,
        )
    }
}
