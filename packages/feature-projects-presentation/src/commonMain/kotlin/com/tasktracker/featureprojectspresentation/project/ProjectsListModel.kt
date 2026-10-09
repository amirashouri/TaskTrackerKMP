package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRemovalOutcome
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.featureprojectsdomain.RemoveProject

/**
 * One row of the projects list, and the decision its delete button is about to make.
 *
 * The outcome is a *derived* property of data the list already holds, not a question the
 * list has to ask the store before the confirmation can be worded. That matters because
 * the confirmation's two bodies are different sentences: one says the project will be
 * archived, the other says it will be deleted along with its tasks. Wording that from a
 * count the list is already carrying means the dialog cannot disagree with the action
 * that follows it.
 */
data class ProjectRow(
    val summary: ProjectSummary,
    /**
     * Items that would survive as an archive rather than be destroyed.
     *
     * Everything except [WorkItemStatus.Done], which is [WorkItemStatus.isComplete]'s
     * definition - and deliberately *not* [WorkItemStatus.isTerminal]'s. A closed task is
     * finished with, but it is also a decision somebody took and recorded, so a project
     * holding one is archived rather than deleted. This mirrors `RemoveProject` exactly;
     * [ProjectsListModelTest] asserts the two agree rather than trusting the comment.
     */
    val outstandingCount: Int,
) {

    /**
     * What pressing delete will actually do, for a project this list is showing.
     *
     * [ProjectRemovalOutcome.AlreadyArchived] is unreachable from here, and that is worth
     * stating rather than handling: the list only shows unarchived projects, so the only
     * way to get it is a second device archiving the project between this read and the
     * press. The model still handles that outcome, because "still not deleted" is a real
     * answer and dropping it on the floor would report a no-op as a success.
     */
    val removalOutcome: ProjectRemovalOutcome
        get() = if (outstandingCount > 0) {
            ProjectRemovalOutcome.Archived
        } else {
            ProjectRemovalOutcome.Deleted
        }
}

/**
 * What removing a project did, as a fact rather than a sentence.
 *
 * A sealed interface of named outcomes, not a `String?`, and the reason is the string
 * layer. `Res` is read through `stringResource`, which is `@Composable`, so a model -
 * which is a plain class and must stay testable without a composition - cannot read a
 * resource. If the model formatted the notice it would have to hardcode the English, and
 * the wording would end up in a second place from every other user-facing sentence. So
 * the model reports *which* outcome and *which project*, and the screen turns that into
 * text with the string it owns.
 */
sealed interface RemovalNotice {

    /** The project had unfinished work, so it was archived instead of destroyed. */
    data class ArchivedInstead(
        val projectName: String,
        val outstandingCount: Int,
    ) : RemovalNotice

    /** The project was already archived - by another device, between the read and the press. */
    data class AlreadyArchived(val projectName: String) : RemovalNotice

    /** The project had nothing outstanding, so it and its tasks were deleted. */
    data class Deleted(val projectName: String) : RemovalNotice
}

/**
 * The projects list: every unarchived project, with enough counts to word its removal.
 *
 * ## The removal decision is computed here rather than asked for
 *
 * `RemoveProject` decides archive-or-delete by reading the project's work items. This
 * model needs the same answer *before* the user presses the button, because the
 * confirmation has to say which of the two is about to happen. Asking `RemoveProject`
 * would mean either running the destructive half to find out, or duplicating its rule
 * here - and a duplicated rule is a rule that drifts, at which point the dialog promises
 * an archive and the store performs a delete.
 *
 * So the rule is derived from [ProjectRepositoryPort.counts] instead, which is a read, and
 * [ProjectRow.removalOutcome] applies the same `!isComplete` test the use case does. The
 * test suite pins the two together.
 *
 * ## Seeding
 *
 * The first read also asks the store to insert its first-launch sample board, which
 * [ProjectRepositoryPort.seedSampleDataIfNeeded] documents as safe to call on every
 * appearance of the dashboard. It is guarded by [didSeed] so a change-driven reload does
 * not re-ask: the call is a no-op once anything exists, but the store still has to open a
 * transaction to find that out, and this list reloads on every write anywhere in the
 * feature.
 */
class ProjectsListModel(
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) : ObservingProjectModel(repository) {

    private val removeProject = RemoveProject(repository, dateProvider)

    /** The rows, newest activity first. Empty until the first read finishes. */
    var rows: List<ProjectRow> by mutableStateOf(emptyList())
        private set

    /** The last removal outcome, for a banner. Cleared by [dismissNotice] or the next press. */
    var notice: RemovalNotice? by mutableStateOf(null)
        private set

    private var didSeed: Boolean = false

    /**
     * Whether a project-only change matters here.
     *
     * Both kinds do, and the distinction still earns its keep: this list is scoped to no
     * project, so a `ProjectChange.projectID` is a *project's* id and there is nothing to
     * match it against. Writing `true` for both is honest, where matching the id would be
     * a filter that silently dropped every change in the feature.
     */
    override fun isRelevant(change: ProjectChange): Boolean = true

    override suspend fun reload() {
        if (!didSeed) {
            repository.seedSampleDataIfNeeded()
            didSeed = true
        }

        val projects = repository.projects(includeArchived = false)
            // Newest activity first, and the store does not promise an order. Sorting the
            // `Project` rather than the summary because `updatedAt` is not a field
            // `ProjectSummary` carries - a summary is a row, and a row does not need to
            // know when its project was last touched. The name tiebreak stops two
            // projects saved in the same tick from swapping places between reloads.
            .sortedWith(
                compareByDescending<Project> { it.updatedAt }.thenBy { it.name },
            )

        this.rows = projects.map { project ->
            ProjectRow(
                summary = ProjectSummary(project, itemCountOf(project.id)),
                outstandingCount = outstandingCountOf(project.id),
            )
        }
    }

    /**
     * Removes a project, or archives it if that would throw unfinished work away.
     *
     * Returns whether anything actually changed, so a caller can skip a navigation it
     * only wanted on success.
     */
    suspend fun remove(projectID: ProjectID): Boolean {
        // Read before the write. After an archive the project leaves the list, so a lookup
        // afterwards would find nothing and the notice would have no name in it.
        val row = rows.firstOrNull { it.summary.id == projectID }
        // The row is the first choice because the list is already holding it, but it is not
        // always there. `AlreadyArchived` means the project left the list between this read
        // and the press - a second device archived it - and a notice built from the row
        // alone would announce "already archived" with a blank name after it. Archived
        // projects are kept rather than deleted, so one read of the store still has the
        // name; this is that read, and the `orEmpty` is only reachable if the project was
        // genuinely deleted from underneath us.
        val name = row?.summary?.name
            ?: repository.project(projectID)?.name
            ?: ""

        val outcome = reporting { removeProject(projectID) } ?: return false
        notice = when (outcome) {
            ProjectRemovalOutcome.Archived -> RemovalNotice.ArchivedInstead(
                projectName = name,
                // The row normally supplies this, and it is the cheaper read. The fallback
                // is the same arithmetic [reload] does, so a notice that arrives without a
                // row still counts the way the list would have.
                outstandingCount = row?.outstandingCount ?: outstandingCountOf(projectID),
            )

            ProjectRemovalOutcome.AlreadyArchived -> RemovalNotice.AlreadyArchived(name)

            ProjectRemovalOutcome.Deleted -> RemovalNotice.Deleted(name)
        }
        return outcome != ProjectRemovalOutcome.AlreadyArchived
    }

    /** Items that would survive an archive rather than be destroyed. See [ProjectRow]. */
    private suspend fun outstandingCountOf(projectID: ProjectID): Int {
        val counts = repository.counts(projectID)
        // `isComplete` is Done alone, so subtracting it is the same filter `RemoveProject`
        // applies - see ProjectRow.outstandingCount.
        return counts.values.sum() - (counts[WorkItemStatus.Done] ?: 0)
    }

    private suspend fun itemCountOf(projectID: ProjectID): Int = repository.counts(projectID).values.sum()

    /** Clears [notice]. */
    fun dismissNotice() {
        notice = null
    }
}
