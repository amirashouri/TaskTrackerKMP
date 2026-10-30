package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.BacklogSortBasis
import com.tasktracker.domain.BacklogSortKey
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The three listings the board cannot show, and the one-line row summary.
 *
 * The board cannot show everything, and the three things it leaves out are three
 * *different* questions rather than one "everything else" — which is why these are three
 * functions and not one `outstanding`. An earlier `backlog()` returned everything that was
 * not done, which quietly re-listed every board card a second time; that is the bug the
 * separation exists to make impossible, and it is the first test below that would catch
 * its return.
 *
 * Grouping is asserted here rather than through a view, because the whole point of putting
 * it in the domain is that the board, the backlog and the archive cannot disagree. A view
 * that filtered and sorted its own copy would be individually correct and jointly wrong.
 */
class ProjectionsTest {

    private val projectID = randomProjectID()
    private val origin = Instant.fromEpochSeconds(1_700_000_000)

    /**
     * Pinned to UTC so a month boundary is a fact in the test rather than a property of
     * wherever the suite happens to be running. The original did the same thing with a
     * `Calendar`; a [TimeZone] is the narrower thing that actually moves the answer, since
     * the grouping reads year and month as numbers and never formats anything.
     */
    private val utc = TimeZone.UTC

    /** Two hours east, as a fixed offset rather than a zone name. */
    private val twoHoursEast = FixedOffsetTimeZone(UtcOffset(hours = 2))

    /**
     * 23:30 UTC on the last day of March. Still March in UTC; already 1 April two hours
     * east. One instant, two answers — the fixture the whole injected-zone argument rests
     * on, so it is built once here rather than in each test that needs it.
     */
    private val monthBoundary = LocalDateTime(2026, 3, 31, 23, 30).toInstant(utc)

    /** Noon rather than midnight, so a zone's offset cannot move the value across a day. */
    private fun atNoon(year: Int, month: Int, day: Int): Instant =
        LocalDateTime(year, month, day, 12, 0).toInstant(utc)

    /**
     * The first instant of a month, which is what the projection groups under.
     *
     * Built rather than spelled out, so the expectation cannot drift from the calendar's own
     * idea of a month start — and built with `atStartOfDayIn` rather than assuming
     * midnight, because that assumption is false in any zone whose offset changes inside the
     * month.
     */
    private fun monthStart(year: Int, month: Int, zone: TimeZone = utc): Instant =
        LocalDate(year, month, 1).atStartOfDayIn(zone)

    private fun item(
        key: String,
        status: WorkItemStatus,
        rank: Int = 0,
        priority: Priority = Priority.Medium,
        createdOffsetSeconds: Long = 0,
        updatedAt: Instant = origin,
        resolvedAt: Instant? = null,
    ) = WorkItem(
        projectID = projectID,
        key = WorkItemKey(key),
        title = key,
        status = status,
        priority = priority,
        rank = rank,
        createdAt = origin + createdOffsetSeconds.seconds,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt,
    )

    private fun keys(items: List<WorkItem>): List<String> = items.map { it.key.rawValue }

    // MARK: Backlog

    @Test
    fun `the backlog lists only untriaged work and nothing the board is showing`() {
        val projection = BacklogProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Todo),
                item("TT-3", WorkItemStatus.InProgress),
                item("TT-4", WorkItemStatus.InReview),
                item("TT-5", WorkItemStatus.Done, resolvedAt = origin),
                item("TT-6", WorkItemStatus.Closed, resolvedAt = origin),
            ),
        )

        assertEquals(listOf("TT-1"), keys(projection.untriaged))
        assertEquals(1, projection.itemCount)
        assertFalse(projection.isEmpty)
    }

    @Test
    fun `a project with nothing untriaged has an empty backlog rather than a full board`() {
        val projection = BacklogProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Todo),
                item("TT-2", WorkItemStatus.Done, resolvedAt = origin),
            ),
        )

        assertTrue(projection.isEmpty)
        assertEquals(0, projection.itemCount)
    }

    @Test
    fun `an empty backlog knows it is empty`() {
        assertTrue(BacklogProjection(project = null, items = emptyList()).isEmpty)
    }

    @Test
    fun `untriaged work orders by priority rather than by board rank`() {
        // The backlog is not a column, so manual rank there is only insertion order. The ranks
        // below are inverted against priority on purpose: honouring them would put the low
        // item first, which is exactly the bug this ordering exists to avoid.
        val projection = BacklogProjection(
            project = null,
            items = listOf(
                item("TT-3", WorkItemStatus.Backlog, rank = 0, priority = Priority.Low),
                item(
                    "TT-1",
                    WorkItemStatus.Backlog,
                    rank = 1,
                    priority = Priority.Critical,
                    createdOffsetSeconds = 60,
                ),
                item("TT-2", WorkItemStatus.Backlog, rank = 2, priority = Priority.Critical),
            ),
        )

        assertEquals(listOf("TT-1", "TT-2", "TT-3"), keys(projection.untriaged))
    }

    @Test
    fun `the sectioned backlog can be reordered by the selection the screen passes in`() {
        // The screen holds the sort selection and asks the projection for the list in it; the
        // projection's own default is what the first frame shows, so the two have to be able to
        // disagree.
        val projection = BacklogSectionedProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Backlog, priority = Priority.Low, createdOffsetSeconds = 60),
                item("TT-2", WorkItemStatus.Backlog, priority = Priority.Critical),
            ),
        )

        assertEquals(listOf("TT-2", "TT-1"), keys(projection.backlog))
        assertEquals(
            listOf("TT-1", "TT-2"),
            keys(
                projection.backlogIn(
                    BacklogSortOrder(listOf(BacklogSortKey(BacklogSortBasis.CreatedAt))),
                ),
            ),
        )
    }

    // MARK: Sectioned backlog

    @Test
    fun `the sectioned backlog splits backlog board and closed into three disjoint partitions`() {
        val projection = BacklogSectionedProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Todo),
                item("TT-3", WorkItemStatus.InProgress),
                item("TT-4", WorkItemStatus.InReview),
                item("TT-5", WorkItemStatus.Done, resolvedAt = origin),
                item("TT-6", WorkItemStatus.Closed, resolvedAt = origin),
            ),
        )

        assertEquals(listOf("TT-1"), keys(projection.backlog))
        assertEquals(listOf("TT-2", "TT-3", "TT-4", "TT-5"), keys(projection.kanban))
        assertEquals(listOf("TT-6"), keys(projection.archived))
        assertEquals(1, projection.backlogItemCount)
        assertEquals(4, projection.kanbanItemCount)
        assertEquals(1, projection.archivedItemCount)
        assertEquals(6, projection.totalItemCount)
        assertFalse(projection.backlogIsEmpty)
        assertFalse(projection.kanbanIsEmpty)
        assertFalse(projection.archivedIsEmpty)
        assertFalse(projection.allSectionsEmpty)
        assertTrue(projection.hasAnyContent)
        assertTrue(projection.backlog.intersect(projection.kanban).isEmpty())
        assertTrue(projection.backlog.intersect(projection.archived).isEmpty())
        assertTrue(projection.kanban.intersect(projection.archived).isEmpty())
    }

    @Test
    fun `the kanban partition reads back in board order then rank`() {
        // The board column is the primary order and the rank the secondary, so a Todo card at
        // rank 1 comes before an InProgress card at any rank, and same-column cards sort by rank.
        // Written deliberately out of order to make a positional sort pass.
        val projection = BacklogSectionedProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.InProgress, rank = 2),
                item("TT-2", WorkItemStatus.Todo, rank = 3),
                item("TT-3", WorkItemStatus.Done, rank = 1),
                item("TT-4", WorkItemStatus.Todo, rank = 1),
                item("TT-5", WorkItemStatus.Backlog),
                item("TT-6", WorkItemStatus.Closed, resolvedAt = origin),
            ),
        )

        assertEquals(
            listOf("TT-4", "TT-2", "TT-1", "TT-3"),
            keys(projection.kanban),
        )
        // The untriaged and the archived partitions neither gain nor reorder on-board items:
        // the three partitions are the same pile seen three ways, not three separate listings.
        assertEquals(listOf("TT-5"), keys(projection.backlog))
        assertEquals(listOf("TT-6"), keys(projection.archived))
    }

    @Test
    fun `a backlog-only project has nothing on the board or in the archived section`() {
        val projection = BacklogSectionedProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Backlog),
            ),
        )

        assertEquals(2, projection.backlogItemCount)
        assertEquals(0, projection.kanbanItemCount)
        assertEquals(0, projection.archivedItemCount)
        assertTrue(projection.kanbanIsEmpty)
        assertTrue(projection.archivedIsEmpty)
        assertFalse(projection.allSectionsEmpty)
        assertEquals(BacklogSection.Backlog, projection.currentSection)
    }

    @Test
    fun `a completely empty backlog shows nothing and starts on the backlog section`() {
        val projection = BacklogSectionedProjection(
            project = null,
            items = emptyList(),
        )

        assertTrue(projection.backlogIsEmpty)
        assertTrue(projection.kanbanIsEmpty)
        assertTrue(projection.archivedIsEmpty)
        assertTrue(projection.allSectionsEmpty)
        assertFalse(projection.hasAnyContent)
        assertEquals(BacklogSection.Backlog, projection.currentSection)
    }

    @Test
    fun `archived items come back newest first`() {
        val projection = BacklogSectionedProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 1, 10)),
                item("TT-2", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 3, 1)),
                item("TT-3", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 2, 15)),
            ),
        )

        assertEquals(listOf("TT-2", "TT-3", "TT-1"), keys(projection.archived))
    }

    @Test
    fun `the sectioned backlog stays the same when dual-authored closed items share a moment`() {
        // Closed is a `data class`-derivable partition, like the backlog's untriaged. When
        // two items close at the same instant the secondary sort is the wire value, which is
        // stable and so deterministic per fixture. The point is the same order every read:
        // a backlog section that re-sorts on its own would disagree with a test that reads
        // the domain value.
        val projection = BacklogSectionedProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 3, 14)),
                item("TT-2", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 3, 14)),
            ),
        )

        assertEquals(listOf("TT-1", "TT-2"), keys(projection.archived))
        assertEquals(BacklogSectionedProjection(null, emptyList<WorkItem>()), BacklogSectionedProjection(null, emptyList<WorkItem>()))
    }

    // MARK: Archive

    @Test
    fun `the archive holds done and closed work and nothing still in flight`() {
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Todo),
                item("TT-3", WorkItemStatus.InProgress),
                item("TT-4", WorkItemStatus.InReview),
                item("TT-5", WorkItemStatus.Done, resolvedAt = atNoon(2026, 3, 2)),
                item("TT-6", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 3, 9)),
            ),
            timeZone = utc,
        )

        assertEquals(2, projection.itemCount)
        assertEquals(
            listOf("TT-5", "TT-6"),
            projection.months.flatMap { keys(it.items) }.sorted(),
        )
    }

    @Test
    fun `resolved work is grouped by the month it finished not the month it was touched`() {
        // Touched in May, finished in March: the archive should say March. Filing by
        // `updatedAt` would reshuffle a finished task into whatever month it was last
        // opened in, which is not a month anybody means.
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item(
                    "TT-1",
                    WorkItemStatus.Done,
                    updatedAt = atNoon(2026, 5, 20),
                    resolvedAt = atNoon(2026, 3, 2),
                ),
            ),
            timeZone = utc,
        )

        assertEquals(listOf(monthStart(2026, 3)), projection.months.map { it.monthStart })
    }

    @Test
    fun `work resolved without a resolution date is filed by when we last heard of it`() {
        // A terminal status with no `resolvedAt` is the invariant violation a row written
        // before the column existed arrives with. `WorkItem.archivedOn` falls back to
        // `updatedAt` so the item is filed rather than dropped — and the store's own read
        // path reconciles it, so this state is transient in production and permanent in a
        // hand-built fixture, which is the only way to test it.
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Done, updatedAt = atNoon(2026, 7, 14)),
            ),
            timeZone = utc,
        )

        assertEquals(listOf(monthStart(2026, 7)), projection.months.map { it.monthStart })
    }

    @Test
    fun `months run newest first because that is the direction an archive is read`() {
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Done, resolvedAt = atNoon(2025, 11, 3)),
                item("TT-2", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 3, 9)),
                item("TT-3", WorkItemStatus.Done, resolvedAt = atNoon(2026, 1, 22)),
            ),
            timeZone = utc,
        )

        assertEquals(
            listOf(
                monthStart(2026, 3),
                monthStart(2026, 1),
                monthStart(2025, 11),
            ),
            projection.months.map { it.monthStart },
        )
    }

    @Test
    fun `two items finished in the same month share one section newest first within it`() {
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Done, resolvedAt = atNoon(2026, 3, 2)),
                item("TT-2", WorkItemStatus.Closed, resolvedAt = atNoon(2026, 3, 20)),
                item("TT-3", WorkItemStatus.Done, resolvedAt = atNoon(2026, 3, 11)),
            ),
            timeZone = utc,
        )

        assertEquals(1, projection.months.size)
        assertEquals(3, projection.months.first().count)
        assertEquals(listOf("TT-2", "TT-3", "TT-1"), keys(projection.months.first().items))
    }

    @Test
    fun `a month boundary falls on the first instant so the 1st groups with itself`() {
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Done, resolvedAt = atNoon(2026, 3, 1)),
                item("TT-2", WorkItemStatus.Done, resolvedAt = atNoon(2026, 3, 31)),
                item("TT-3", WorkItemStatus.Done, resolvedAt = atNoon(2026, 4, 1)),
            ),
            timeZone = utc,
        )

        assertEquals(2, projection.months.size)
        assertEquals(
            listOf(monthStart(2026, 4), monthStart(2026, 3)),
            projection.months.map { it.monthStart },
        )
    }

    @Test
    fun `a project with nothing resolved has no months at all`() {
        val projection = ArchiveProjection(
            project = null,
            items = listOf(
                item("TT-1", WorkItemStatus.Todo),
                item("TT-2", WorkItemStatus.Backlog),
            ),
            timeZone = utc,
        )

        assertTrue(projection.isEmpty)
        assertTrue(projection.months.isEmpty())
    }

    @Test
    fun `the time zone decides which month a boundary instant belongs to`() {
        // Same instant, same work item, different month — which is what makes the injected
        // zone load-bearing rather than a parameter that gets defaulted away.
        val inUTC = ArchiveProjection(
            project = null,
            items = listOf(item("TT-1", WorkItemStatus.Done, resolvedAt = monthBoundary)),
            timeZone = utc,
        )
        val east = ArchiveProjection(
            project = null,
            items = listOf(item("TT-1", WorkItemStatus.Done, resolvedAt = monthBoundary)),
            timeZone = twoHoursEast,
        )

        assertEquals(listOf(monthStart(2026, 3)), inUTC.months.map { it.monthStart })
        assertEquals(
            listOf(monthStart(2026, 4, twoHoursEast)),
            east.months.map { it.monthStart },
        )
        assertNotEquals(
            inUTC.months.first().monthStart,
            east.months.first().monthStart,
        )
    }

    @Test
    fun `the same items cut in two zones are two different projections`() {
        // The zone is a constructor field rather than a value consumed and forgotten, so
        // equality stays honest: a locale change really does produce different groupings,
        // and a projection that pretended otherwise would leave a stale grouping on screen
        // because the parameter change looked like a no-op to a skip-if-equal check.
        val items = listOf(item("TT-1", WorkItemStatus.Done, resolvedAt = monthBoundary))

        val inUTC = ArchiveProjection(null, items, utc)
        val east = ArchiveProjection(null, items, twoHoursEast)

        assertNotEquals(inUTC, east)
        assertNotEquals(inUTC.monthStartOfFirst(), east.monthStartOfFirst())
    }

    // MARK: Board

    @Test
    fun `the board has one column per board status in board order`() {
        val projection = BoardProjection(
            project = null,
            items = listOf(item("TT-1", WorkItemStatus.Done, resolvedAt = origin)),
        )

        assertEquals(WorkItemStatus.boardOrder, projection.columns.map { it.status })
    }

    @Test
    fun `an empty lane is still a column so a card has somewhere to land`() {
        val projection = BoardProjection(
            project = null,
            items = listOf(item("TT-1", WorkItemStatus.InProgress)),
        )

        val todo = projection.columns.single { it.status == WorkItemStatus.Todo }
        assertTrue(todo.isEmpty)
        assertEquals(0, todo.count)
        assertEquals(
            WorkItemStatus.boardOrder.size,
            projection.columns.size,
            "a column that appears only once it has content gives drag and drop no target",
        )
    }

    @Test
    fun `cards within a column come back in rank order`() {
        val projection = BoardProjection(
            project = null,
            items = listOf(
                item("TT-3", WorkItemStatus.Todo, rank = 3),
                item("TT-1", WorkItemStatus.Todo, rank = 1),
                item("TT-2", WorkItemStatus.Todo, rank = 2),
            ),
        )

        val todo = projection.columns.single { it.status == WorkItemStatus.Todo }
        assertEquals(listOf("TT-1", "TT-2", "TT-3"), keys(todo.items))
        assertFalse(todo.isEmpty)
    }

    @Test
    fun `the board count leaves out work that takes no column`() {
        val items = listOf(
            item("TT-1", WorkItemStatus.Backlog),
            item("TT-2", WorkItemStatus.Todo),
            item("TT-3", WorkItemStatus.InProgress),
            item("TT-4", WorkItemStatus.InReview),
            item("TT-5", WorkItemStatus.Done, resolvedAt = origin),
            item("TT-6", WorkItemStatus.Closed, resolvedAt = origin),
        )

        val board = BoardProjection(project = null, items = items)

        // A badge on the board is a count of what is on the board. Counting the backlog
        // into it would report six cards for a board showing four, and the two numbers are
        // both correct — they are just answering different questions.
        assertEquals(4, board.itemCount)
    }

    @Test
    fun `only delivered work appears in two listings because that overlap is intended`() {
        val items = listOf(
            item("TT-1", WorkItemStatus.Backlog),
            item("TT-2", WorkItemStatus.Todo),
            item("TT-3", WorkItemStatus.InProgress),
            item("TT-4", WorkItemStatus.InReview),
            item("TT-5", WorkItemStatus.Done, resolvedAt = origin),
            item("TT-6", WorkItemStatus.Closed, resolvedAt = origin),
        )
        val board = BoardProjection(project = null, items = items)
        val backlog = BacklogProjection(project = null, items = items)
        val archive = ArchiveProjection(project = null, items = items, timeZone = utc)

        val onBoard = board.columns.flatMap { keys(it.items) }.toSet()
        val untriaged = keys(backlog.untriaged).toSet()
        val filed = archive.months.flatMap { keys(it.items) }.toSet()

        // `Done` is a column *and* archived — a lane for what was just finished against a
        // dated history of everything that was. `Closed` is the archive's alone, and the
        // backlog overlaps neither, which is the separation that stops the backlog
        // re-listing a card the board is already showing.
        assertEquals(setOf("TT-5"), onBoard intersect filed)
        assertTrue(untriaged.intersect(onBoard).isEmpty())
        assertTrue(untriaged.intersect(filed).isEmpty())
        assertEquals(
            items.size,
            onBoard.union(untriaged).union(filed).size,
            "an item that appears in no listing at all is a hole, not a design",
        )
    }

    // MARK: ProjectSummary

    @Test
    fun `a summary copies the project rather than reading through to it`() {
        val project = Project(
            keyPrefix = "APOLLO",
            name = "Apollo",
            details = "Moon shots",
            createdAt = origin,
            updatedAt = origin,
        )
        val summary = ProjectSummary(project, itemCount = 3)

        project.name = "Artemis"
        project.details = "Lunar landings"
        project.isArchived = true

        // The row is already displaying this. A summary that read through to a mutable
        // project would change under it, and the list would need a `State` around a value
        // it believes is immutable.
        assertEquals("Apollo", summary.name)
        assertEquals("Moon shots", summary.details)
        assertFalse(summary.isArchived)
        assertEquals(project.id, summary.id)
        assertEquals("APOLLO", summary.keyPrefix)
    }

    @Test
    fun `a summary carries the number of items under it`() {
        val project = Project(
            keyPrefix = "APOLLO",
            name = "Apollo",
            createdAt = origin,
            updatedAt = origin,
        )

        assertEquals(7, ProjectSummary(project, itemCount = 7).itemCount)
    }

    // MARK: The one thing all four share

    @Test
    fun `a projection is usable before the project has loaded`() {
        // Every screen reads its items first and its project second, so `null` here is the
        // normal loading state rather than a missing value. A projection that required a
        // project would force a screen to render nothing at all until both had arrived.
        val items = listOf(item("TT-1", WorkItemStatus.Todo))

        assertEquals(1, BoardProjection(project = null, items = items).itemCount)
        assertEquals(0, BacklogProjection(project = null, items = items).itemCount)
        assertTrue(ArchiveProjection(project = null, items = items, timeZone = utc).isEmpty)
    }

    @Test
    fun `two projections over equal input are equal so a recomposition can be skipped`() {
        val items = listOf(
            item("TT-1", WorkItemStatus.Todo),
            item("TT-2", WorkItemStatus.Backlog),
            item("TT-3", WorkItemStatus.Done, resolvedAt = atNoon(2026, 3, 2)),
        )
        val board = BoardProjection(project = null, items = items)
        val again = BoardProjection(project = null, items = items)

        assertEquals(board, again)
        // The derived grouping is rebuilt for every instance, so equality over the *input*
        // only buys the skip if the derived content is equal too. A projection that
        // compared equal while its columns disagreed would let a changed board through a
        // skip-if-unchanged check.
        assertEquals(board.columns, again.columns)
        assertEquals(BacklogProjection(null, items), BacklogProjection(null, items))
        assertEquals(ArchiveProjection(null, items, utc), ArchiveProjection(null, items, utc))
    }

    @Test
    fun `a store that returns the same rows in a different order is a different projection`() {
        // The documented cost of being a `data class` over the input rather than over the
        // grouping, pinned here so the trade is visible rather than assumed: one wasted
        // recomposition when a query hands back its own rows in a new order, in exchange
        // for a state object that never recomposes for content it already shows.
        val items = listOf(
            item("TT-1", WorkItemStatus.Todo, rank = 1),
            item("TT-2", WorkItemStatus.Todo, rank = 2),
        )
        val reordered = items.reversed()

        assertNotEquals(BoardProjection(null, items), BoardProjection(null, reordered))
        // Cheap rather than wrong: the derived columns are identical either way, so the
        // wasted recomposition re-renders exactly what was already on screen.
        assertEquals(
            BoardProjection(null, items).columns,
            BoardProjection(null, reordered).columns,
        )
    }
}

/** The one month the projection actually grouped into, for a single-item fixture. */
private fun ArchiveProjection.monthStartOfFirst(): Instant? = months.firstOrNull()?.monthStart
