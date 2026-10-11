package com.tasktracker.featureprojectsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseSprintID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.core.randomUserID
import com.tasktracker.database.CachedSession
import com.tasktracker.database.OutboxRecord
import com.tasktracker.database.SessionCache
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * One store, one bus, one repository — the arrangement every test in this module needs.
 *
 * Each [Harness] gets its own in-memory store, so tests are independent and safe to run in
 * parallel. That is a property of `TaskTrackerDatabase.inMemory()` rather than of this class,
 * and it is the reason there is no `beforeEach` tearing anything down: there is nothing
 * shared to tear down.
 *
 * The repository and the [sink] are built over the *same* bus and the *same* store, because
 * that is the production wiring and most of the interesting tests are about the two of them
 * disagreeing if it is ever wrong — a pull that announces on a channel no screen listens to
 * is the bug this arrangement makes writable.
 */
internal class Harness(
    val database: TaskTrackerDatabase = TaskTrackerDatabase.inMemory(),
    val bus: ProjectChangeBus = ProjectChangeBus(),
    val session: StubSessionCache = StubSessionCache(),
) {
    val clock: TestDateProvider = TestDateProvider()

    val repository: RoomProjectRepository = RoomProjectRepository(
        database = database,
        changeBus = bus,
        sessionCache = session,
        dateProvider = clock,
    )

    val sink: LocalDataSyncSink = LocalDataSyncSink(database, bus)

    /**
     * Everything the outbox holds, oldest first.
     *
     * "Oldest" is the DAO's `ORDER BY createdAt ASC, id ASC`, and with this harness's fixed
     * clock several rows share one `createdAt` — so the tie is broken by a *random* id and the
     * order between them is total but arbitrary. A test that writes twice at the same instant
     * and then asserts on the order of the two rows is asserting on `Uuid.random()`. Give the
     * writes different instants, or assert on the set.
     */
    suspend fun queued(): List<OutboxRecord> = database.outboxDao().all()

    suspend fun queueDepth(): Int = database.outboxDao().count()

    /** Decodes a queued payload, so an assertion can be about the value rather than the bytes. */
    suspend fun queuedProject(index: Int = 0): Project =
        JSONCoding.json.decodeFromString(Project.serializer(), queued()[index].payload.decodeToString())

    suspend fun queuedWorkItem(index: Int = 0): WorkItem =
        JSONCoding.json.decodeFromString(WorkItem.serializer(), queued()[index].payload.decodeToString())

    /** As [queuedProject], for a row the test already has in hand. */
    fun decodeProject(record: OutboxRecord): Project =
        JSONCoding.json.decodeFromString(Project.serializer(), record.payload.decodeToString())

    suspend fun queuedSprint(index: Int = 0): Sprint =
        JSONCoding.json.decodeFromString(Sprint.serializer(), queued()[index].payload.decodeToString())

    /** As [queuedSprint], for a row the test already has in hand. */
    fun decodeSprint(record: OutboxRecord): Sprint =
        JSONCoding.json.decodeFromString(Sprint.serializer(), record.payload.decodeToString())

    /** As [queuedWorkItem], for a row the test already has in hand. */
    fun decodeWorkItem(record: OutboxRecord): WorkItem =
        JSONCoding.json.decodeFromString(WorkItem.serializer(), record.payload.decodeToString())
}

/**
 * A session cache a test drives directly.
 *
 * A class rather than a lambda so a test can sign a user in partway through, which is how the
 * "a history row with no author, because no session was cached" case is set up. The
 * production `RoomSessionCache` could express that too, but only by writing a row through a
 * DAO inside a transaction — this is one line, and the assertion is about the repository.
 */
internal class StubSessionCache(
    private var session: CachedSession? = null,
) : SessionCache {

    override suspend fun current(): CachedSession? = session

    override suspend fun save(session: CachedSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }

    /** Signs in a user whose id is both returned and stored, so a test can assert on it. */
    fun signIn(userID: String = randomUserID().stringValue): String {
        session = CachedSession(
            userID = userID,
            email = "amir@example.com",
            displayName = "Amir",
            lastSignedInAt = Moments.start,
            serverVersion = 1,
        )
        return userID
    }
}

/**
 * The instants the tests use, named so an assertion can say which one it meant.
 *
 * Not `Instant.fromEpochSeconds(1_760_000_000)` written out at each call site: a reader
 * cannot tell two such literals apart, and "the same instant" is load-bearing in the history
 * tests, where the *only* thing being tested is what happens when two rows share one.
 */
internal object Moments {
    val start: Instant = Instant.fromEpochSeconds(1_760_000_000)
    val later: Instant = Instant.fromEpochSeconds(1_760_000_600)
    val muchLater: Instant = Instant.fromEpochSeconds(1_760_003_600)

    /** A date every seeded due date lands near, so a relative-date test has something to say. */
    val day: Instant = start + 1.days
}

/**
 * Collects [flow] into [into] for the lifetime of the test, starting immediately.
 *
 * Two details that are both load-bearing. It runs in `backgroundScope`, so the collector is
 * cancelled when the test ends and never leaks into the next one — and it is started on an
 * `UnconfinedTestDispatcher`, because a `ProjectChangeBus` registers a subscriber *when
 * collection starts* and drops any event sent to nobody. A collector that had not run yet
 * when the event was sent would see nothing, and the test would be asserting on a bus that
 * was never listening.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> TestScope.collecting(flow: Flow<T>, into: MutableList<T>): Job =
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { into += it } }

/**
 * A project with every field deliberately unlike its default.
 *
 * The defaults are the trap. A fixture that leaves `details` or `nextItemNumber` at a default
 * round-trips through any mapping at all and still looks correct — which is exactly how the
 * original's `nextItemNumber` went missing: every sample value it was tested with had the
 * counter at 1. So a fixture whose job is to prove a converter copies everything puts a
 * non-default in every column it has.
 */
internal fun aProject(
    id: ProjectID = parseProjectID("11111111-1111-4111-8111-111111111111")!!,
    keyPrefix: String = "TT",
    name: String = "Task Tracker",
    details: String = "Everything, in one place.",
    isArchived: Boolean = true,
    nextItemNumber: Int = 42,
    createdAt: Instant = Moments.start,
    updatedAt: Instant = Moments.later,
): Project = Project(
    id = id,
    keyPrefix = keyPrefix,
    name = name,
    details = details,
    isArchived = isArchived,
    nextItemNumber = nextItemNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** As [aProject], for a work item. */
internal fun aWorkItem(
    id: WorkItemID = parseWorkItemID("22222222-2222-4222-8222-222222222222")!!,
    projectID: ProjectID = parseProjectID("11111111-1111-4111-8111-111111111111")!!,
    key: String = "TT-42",
    title: String = "Write the migration",
    details: String = "By hand, then check the generated SQL.",
    status: WorkItemStatus = WorkItemStatus.InReview,
    priority: Priority = Priority.High,
    assignee: String? = "Ada",
    dueDate: Instant? = Moments.muchLater,
    rank: Int = 7,
    createdAt: Instant = Moments.start,
    updatedAt: Instant = Moments.later,
    resolvedAt: Instant? = null,
    // Non-default on purpose: the completeness test has to *name* these two, and a default
    // of null would let an `expected` record omit them and match a mapping that dropped
    // both. A value here is what makes forgetting one a visible difference.
    storyPoints: Int? = 5,
    sprintID: SprintID? = parseSprintID("33333333-3333-4333-8333-333333333333"),
    // Non-default for exactly the reason the two above are: this column encodes `null` for an
    // empty list, so a fixture with no blockers would let an `expected` record that omits the
    // column match a mapping that silently dropped it. Two ids also prove the join order.
    blockedByIDs: List<WorkItemID> = listOf(
        parseWorkItemID("44444444-4444-4444-8444-444444444444")!!,
        parseWorkItemID("55555555-5555-4555-8555-555555555555")!!,
    ),
    // Non-default, and carrying a non-null `until`, for the same reason the three above
    // are: both new columns encode `null` for the absent state, so a fixture with no
    // recurrence would let an `expected` record omit them and match a mapping that
    // dropped both. The `until` is what makes the *pair* — cadence string plus end
    // instant — the thing that has to round-trip, rather than either half alone.
    recurrence: Recurrence? = Recurrence.Weekly(3, until = Moments.muchLater),
): WorkItem = WorkItem(
    id = id,
    projectID = projectID,
    key = WorkItemKey(key),
    title = title,
    details = details,
    status = status,
    priority = priority,
    assignee = assignee,
    dueDate = dueDate,
    recurrence = recurrence,
    rank = rank,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolvedAt = resolvedAt,
    storyPoints = storyPoints,
    sprintID = sprintID,
    blockedByIDs = blockedByIDs,
)

/** As [aProject], for a sprint. */
internal fun aSprint(
    id: SprintID = parseSprintID("33333333-3333-4333-8333-333333333333")!!,
    projectID: ProjectID = parseProjectID("11111111-1111-4111-8111-111111111111")!!,
    name: String = "Sprint 1",
    goalStoryPoints: Int = 21,
    // Non-default for the reason `aWorkItem`'s two are: the completeness test has to *name*
    // it, and a default of null would let an `expected` record omit it and match a mapping
    // that dropped it. A value here is what makes forgetting it a visible difference.
    goal: String? = "Ship the migration",
    startDate: Instant = Moments.start,
    endDate: Instant = Moments.day,
    owner: String? = "Ada",
    isFinished: Boolean = false,
    finishedAt: Instant? = null,
    createdAt: Instant = Moments.start,
    updatedAt: Instant = Moments.later,
): Sprint = Sprint(
    id = id,
    projectID = projectID,
    name = name,
    goalStoryPoints = goalStoryPoints,
    goal = goal,
    startDate = startDate,
    endDate = endDate,
    owner = owner,
    isFinished = isFinished,
    finishedAt = finishedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
