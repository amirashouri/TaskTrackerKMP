package com.tasktracker.featureprojectsdata

import com.tasktracker.core.parseWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The board a first launch starts with.
 *
 * Seeding is the one write with no caller, so every rule the rest of the repository follows
 * has to be re-argued for it, and each of the assertions below is one of those rules applied
 * to a path that does not go through a port method.
 */
class SeedingTest {

    @Test
    fun anEmptyStoreIsSeededAndReportsThatItDid() = runTest {
        val harness = Harness()

        assertTrue(harness.repository.seedSampleDataIfNeeded())
        assertFalse(harness.repository.isEmpty())
    }

    @Test
    fun aStoreThatAlreadyHasSomethingIsLeftExactlyAsItWas() = runTest {
        // The property the whole method exists for. It is called on every appearance of a
        // dashboard, so "if needed" is not an optimisation — it is the difference between a
        // first launch and a second one turning into a duplicate board.
        val harness = Harness()
        harness.repository.createProject("Mine", "MINE", "", Moments.start)
        val before = harness.database.projectDao().count()

        assertFalse(harness.repository.seedSampleDataIfNeeded())

        assertEquals(before, harness.database.projectDao().count())
        assertEquals("Mine", harness.repository.projects(includeArchived = true)[0].name)
    }

    @Test
    fun seedingTwiceIsTheSameAsSeedingOnce() = runTest {
        val harness = Harness()

        assertTrue(harness.repository.seedSampleDataIfNeeded())
        val afterFirst = harness.database.projectDao().count()
        val itemsAfterFirst = harness.database.workItemDao().count()

        assertFalse(harness.repository.seedSampleDataIfNeeded())

        assertEquals(afterFirst, harness.database.projectDao().count())
        assertEquals(itemsAfterFirst, harness.database.workItemDao().count())
    }

    @Test
    fun theSampleBoardHasMoreThanOneProject() = runTest {
        // One project makes every list, picker and navigation test look like it passed when
        // it only proved the single-item case.
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        assertTrue(harness.repository.projects(includeArchived = true).size > 1)
    }

    @Test
    fun everySeededProjectNumbersItsOwnItemsFromOne() = runTest {
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        for (project in harness.repository.projects(includeArchived = true)) {
            // Sorted, because the read order is not the seed order: every seeded item shares
            // a rank on purpose, so the tie-break chain decides — and that is exactly the chain
            // this fixture exists to exercise.
            val keys = harness.repository.workItems(project.id).map { it.key.rawValue }.sorted()
            assertEquals(
                keys.indices.map { "${project.keyPrefix}-${it + 1}" },
                keys,
                "${project.keyPrefix} keys",
            )
            assertEquals(keys.size + 1, project.nextItemNumber, "${project.keyPrefix} counter")
        }
    }

    @Test
    fun theFirstItemAfterSeedingTakesTheNextKeyRatherThanStartingAtOne() = runTest {
        // The seeded board has already issued its keys, so a card added to it must not collide
        // with the sample card sitting in front of it.
        val harness = Harness()
        harness.repository.seedSampleDataIfNeeded()
        val project = harness.repository.projects(includeArchived = true).first { it.keyPrefix == "APO" }

        val item = harness.repository.createWorkItem(
            project.id, "Mine", "", WorkItemStatus.Todo,
            Priority.Medium, null, null, null, null, Moments.later,
        )

        assertEquals("APO-9", item.key.rawValue)
        // Compared as numbers rather than as strings, because "APO-10" < "APO-9" — and a board
        // with ten cards is the first place that would show.
        val numbers = harness.repository.workItems(project.id).map { it.key.rawValue.substringAfterLast('-').toInt() }
        assertEquals((1..9).toList(), numbers.sorted(), "the keys are a gapless run from one")
    }

    @Test
    fun theSeededProjectsAreNotArchived() = runTest {
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        assertEquals(
            harness.repository.projects(includeArchived = true).size,
            harness.repository.projects(includeArchived = false).size,
        )
    }

    @Test
    fun seededDueDatesAreCountedInDaysFromTheSeedingInstantRatherThanHardCoded() = runTest {
        // The reason [ProjectSeed.Item.dueInDays] is an offset. A sample board with fixed
        // dates is a wall of overdue cards a week after install, which teaches a new user that
        // "overdue" is noise before they have written anything of their own.
        val harness = Harness()
        harness.clock.set(Moments.muchLater)

        harness.repository.seedSampleDataIfNeeded()

        val project = harness.repository.projects(includeArchived = true).first { it.keyPrefix == "APO" }
        val dated = harness.repository.workItems(project.id).mapNotNull { it.dueDate }
        assertTrue(dated.isNotEmpty())
        assertTrue(dated.all { it >= Moments.muchLater }, "a sample due date is already in the past")
    }

    @Test
    fun anItemSeededIntoADoneColumnIsDatedSoTheArchiveCanFileIt() = runTest {
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        val project = harness.repository.projects(includeArchived = true).first { it.keyPrefix == "APO" }
        val done = harness.repository.workItems(project.id).filter { it.status == WorkItemStatus.Done }
        assertTrue(done.isNotEmpty())
        assertTrue(done.all { it.resolvedAt != null }, "an undated Done item is invisible in the archive")
    }

    @Test
    fun seededItemsHaveDistinctCreationInstantsSoTheBoardHasAStableOrder() = runTest {
        // All thirteen at the same instant left ordering to the key tie-break alone, and a
        // board that reshuffles when two cards happen to share a rank is the bug this avoids.
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        val project = harness.repository.projects(includeArchived = true).first { it.keyPrefix == "APO" }
        val created = harness.repository.workItems(project.id).map { it.createdAt }
        assertEquals(created.size, created.distinct().size)
    }

    @Test
    fun everySeededItemHasACreationHistoryRow() = runTest {
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        val items = harness.database.workItemDao().all()
        assertTrue(items.isNotEmpty())
        for (item in items) {
            val history = harness.repository.statusChanges(assertNotNull(parseWorkItemID(item.id)))
            assertEquals(1, history.size, "${item.key} history")
        }
    }

    @Test
    fun seedingQueuesEveryRowItWrote() = runTest {
        // Sample data is not a back door. If it were, a seeded board would be local-only and
        // the first real edit on another device would conflict with rows the server never saw.
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        val projects = harness.database.projectDao().count()
        val items = harness.database.workItemDao().count()
        assertEquals(projects, harness.queued().count { it.entityKind == "project" })
        assertEquals(items, harness.queued().count { it.entityKind == "workItem" })
    }

    @Test
    fun oneProjectRowIsQueuedPerSeededProjectRatherThanOnePerItem() = runTest {
        // The counter is already advanced when the project row is written, so a seeder that
        // patched it per item would queue fourteen rows to describe eight cards.
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        val projectRows = harness.queued().filter { it.entityKind == "project" }
        assertEquals(harness.database.projectDao().count(), projectRows.size)
        assertTrue(projectRows.all { harness.decodeProject(it).nextItemNumber > 1 })
    }

    @Test
    fun seedingAnnouncesBothKindsUnscoped() = runTest {
        // Unscoped because a seed writes several projects at once and there is no single one
        // to name; both kinds because a board open anywhere needs to hear about it.
        val harness = Harness()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.seedSampleDataIfNeeded()

        assertEquals(
            listOf(
                ProjectChange(kind = ProjectChange.Kind.Projects, projectID = null),
                ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = null),
            ),
            seen,
        )
    }

    @Test
    fun aStoreThatIsNotSeededAnnouncesNothing() = runTest {
        // The announcement is the visible half of the write, and sending it for a seed that did
        // not happen wakes every open board to re-read a store that has not changed.
        val harness = Harness()
        harness.repository.createProject("Mine", "MINE", "", Moments.start)
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        assertFalse(harness.repository.seedSampleDataIfNeeded())

        assertEquals(emptyList(), seen)
    }

    @Test
    fun seedingNothingIsNotAnError() = runTest {
        // A caller passing no seeds gets a clean zero rather than a failure — and, because the
        // emptiness check and the insert share a transaction, a store that is still empty.
        val harness = Harness()

        assertEquals(0, harness.repository.seedIfNeeded(emptyList(), Moments.start))

        assertTrue(harness.repository.isEmpty())
    }

    @Test
    fun aSeedWithNoItemsStillProducesAUsableProject() = runTest {
        // A project with nothing in it is legal, and a seeder that assumed at least one item
        // would leave a row whose counter and queue entries never got written.
        val harness = Harness()

        val created = harness.repository.seedIfNeeded(
            listOf(ProjectSeed(keyPrefix = "NEW", name = "Fresh", details = "")),
            Moments.start,
        )

        assertEquals(1, created)
        val project = harness.repository.projects(includeArchived = false).single()
        assertEquals("Fresh", project.name)
        assertEquals(1, project.nextItemNumber)
        assertEquals(1, harness.queueDepth())
    }

    @Test
    fun theSeededBoardIsTheFixtureTheOriginalShipped() = runTest {
        // Pinned, because a sample board is documentation: it is what a reader uses to see
        // what a column is meant to hold. Reordering or rewording it without meaning to is a
        // change to the docs.
        val harness = Harness()

        harness.repository.seedSampleDataIfNeeded()

        val project = harness.repository.projects(includeArchived = true).first { it.keyPrefix == "APO" }
        assertEquals("Apollo", project.name)
        // Keyed rather than positional, for the reason in the numbering test: read order is
        // the tie-break chain's answer, not the order the fixture lists them in.
        assertEquals(
            listOf(
                "APO-1" to WorkItemStatus.Done,
                "APO-2" to WorkItemStatus.Done,
                "APO-3" to WorkItemStatus.InProgress,
                "APO-4" to WorkItemStatus.InReview,
                "APO-5" to WorkItemStatus.Todo,
                "APO-6" to WorkItemStatus.Backlog,
                "APO-7" to WorkItemStatus.Backlog,
                "APO-8" to WorkItemStatus.Backlog,
            ),
            harness.repository.workItems(project.id)
                .map { it.key.rawValue to it.status }
                .sortedBy { (key, _) -> key.substringAfterLast('-').toInt() },
        )
    }

    @Test
    fun aSeededProjectWithNoSessionHasNoAuthorRatherThanAFabricatedOne() = runTest {
        // Seeding runs before sign-in on a real first launch, so this is the *expected* path
        // rather than an edge case.
        val harness = Harness()

        harness.repository.seedIfNeeded(SampleData.projects(), Moments.start)

        val history = harness.database.statusChangeDao().all()
        assertTrue(history.isNotEmpty())
        assertTrue(history.all { it.changedByUserID == null })
    }

    @Test
    fun aSeededProjectIsAuthoredByWhoeverWasSignedInWhenItWasSeeded() = runTest {
        val harness = Harness()
        val userID = harness.session.signIn()

        harness.repository.seedIfNeeded(SampleData.projects(), Moments.start)

        val history = harness.database.statusChangeDao().all()
        assertTrue(history.isNotEmpty())
        assertTrue(history.all { it.changedByUserID == userID })
    }
}
