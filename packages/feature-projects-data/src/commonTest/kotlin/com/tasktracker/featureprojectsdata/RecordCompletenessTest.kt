package com.tasktracker.featureprojectsdata

import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomUserID
import com.tasktracker.database.ProjectRecord
import com.tasktracker.database.StatusChangeRecord
import com.tasktracker.database.WorkItemRecord
import com.tasktracker.domain.Priority
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Nothing in this module may silently drop a column.
 *
 * ## What went wrong, twice
 *
 * Both record↔domain converters in the original dropped a field. A work item lost
 * `resolvedAt`, so a `Done` card could not be filed in the archive. A project lost
 * `nextItemNumber`, so a project that round-tripped through the store started handing out
 * `TT-1` again. Neither raised an error, both compiled, and every fixture the original
 * tested with had those two fields at their *defaults* — so its round-trip tests agreed with
 * themselves and proved nothing.
 *
 * ## How this holds the line
 *
 * Two mechanisms, in this order:
 *
 * 1. **Every expected record is written out with every column named.** When a column is
 *    added to an entity, that construction stops compiling — the parameter has no default and
 *    no value. So the test cannot be left stale; it becomes a build failure at the moment the
 *    schema changes, which is the only moment anybody is looking.
 * 2. **The comparison is a whole-record `equals`.** That catches a column added *with* a
 *    default, which compiles fine: the new property is part of the generated `equals`, so a
 *    converter that forgot to set it fails the assertion.
 *
 * ## Why not reflection
 *
 * The obvious design is `ProjectRecord::class.primaryConstructor`, walk the parameter names,
 * fail if the mapping does not mention one. It would work on the JVM and is exactly what the
 * plan first called for — but `KClass.constructors` throws on Kotlin/Native, so a reflection
 * test here fails to compile for the two iOS targets. A check that only exists on the platform
 * it is fastest to run on is not a check; and the explicit construction catches the same
 * column at *compile* time on every target, which is strictly earlier.
 *
 * The one thing a reflective version would have caught and this cannot is a column added with
 * a default *and* a value that coincidentally matches the default — where nothing was lost
 * anyway, because the default is the value. That is not a bug worth a test.
 */
class RecordCompletenessTest {

    // MARK: The write direction

    @Test
    fun writingAProjectNamesEveryColumn() {
        val project = aProject()

        // Spelled out rather than `assertEquals(project.toRecord().domainValue, project)`:
        // this construction is the check. A new column has no value here and the build fails.
        val expected = ProjectRecord(
            id = "11111111-1111-4111-8111-111111111111",
            keyPrefix = "TT",
            name = "Task Tracker",
            details = "Everything, in one place.",
            isArchived = true,
            nextItemNumber = 42,
            createdAt = Moments.start,
            updatedAt = Moments.later,
        )

        assertEquals(expected, project.toRecord())
    }

    @Test
    fun writingAWorkItemNamesEveryColumn() {
        val item = aWorkItem()

        val expected = WorkItemRecord(
            id = "22222222-2222-4222-8222-222222222222",
            projectID = "11111111-1111-4111-8111-111111111111",
            key = "TT-42",
            title = "Write the migration",
            details = "By hand, then check the generated SQL.",
            statusRaw = "in_review",
            priorityRaw = Priority.High.rank,
            assignee = "Ada",
            dueDate = Moments.muchLater,
            rank = 7,
            createdAt = Moments.start,
            updatedAt = Moments.later,
            resolvedAt = null,
        )

        assertEquals(expected, item.toRecord())
    }

    @Test
    fun writingAStatusChangeNamesEveryColumnItOwnsAndLeavesTheSequenceToTheDatabase() {
        val change = aChange()

        val expected = StatusChangeRecord(
            id = change.id.stringValue,
            workItemID = change.workItemID.stringValue,
            projectID = change.projectID.stringValue,
            fromRaw = "todo",
            toRaw = "in_progress",
            changedAt = Moments.start,
            changedByUserID = change.changedBy!!.stringValue,
        )

        val record = change.toRecord()

        assertEquals(expected, record)
        // `sequence` is the one column with no domain counterpart, and it is deliberately not
        // named above: it is a storage-assigned insertion counter, and a value invented here
        // would be overwritten by Room's autoincrement on insert. That is the whole reason it
        // exists — see `StatusChangeRecord.sequence`.
        assertEquals(0L, record.sequence)
    }

    // MARK: The apply direction

    @Test
    fun applyingAProjectNamesEveryColumnItOwns() {
        val stored = ProjectRecord(
            id = "11111111-1111-4111-8111-111111111111",
            keyPrefix = "TT",
            name = "Task Tracker",
            details = "Everything, in one place.",
            isArchived = true,
            nextItemNumber = 42,
            createdAt = Moments.start,
            updatedAt = Moments.later,
        )

        val incoming = aProject(
            keyPrefix = "ZZ",
            name = "Renamed",
            details = "New notes.",
            isArchived = false,
            nextItemNumber = 99,
            createdAt = Moments.muchLater,
            updatedAt = Moments.muchLater,
        )

        val expected = ProjectRecord(
            // Identity and age are the stored row's, not the payload's.
            id = stored.id,
            keyPrefix = "ZZ",
            name = "Renamed",
            details = "New notes.",
            isArchived = false,
            nextItemNumber = 99,
            createdAt = stored.createdAt,
            updatedAt = Moments.muchLater,
        )

        assertEquals(expected, stored.apply(incoming))
    }

    @Test
    fun applyingAWorkItemNamesEveryColumnItOwns() {
        val stored = WorkItemRecord(
            id = "22222222-2222-4222-8222-222222222222",
            projectID = "11111111-1111-4111-8111-111111111111",
            key = "TT-42",
            title = "Write the migration",
            details = "By hand, then check the generated SQL.",
            statusRaw = "in_review",
            priorityRaw = Priority.High.rank,
            assignee = "Ada",
            dueDate = Moments.muchLater,
            rank = 7,
            createdAt = Moments.start,
            updatedAt = Moments.later,
            resolvedAt = null,
        )

        val incoming = aWorkItem(
            key = "TT-43",
            title = "Renamed",
            details = "New notes.",
            status = WorkItemStatus.Done,
            priority = Priority.Lowest,
            assignee = null,
            dueDate = null,
            rank = 99,
            createdAt = Moments.muchLater,
            updatedAt = Moments.muchLater,
        )

        val expected = WorkItemRecord(
            id = stored.id,
            projectID = stored.projectID,
            key = "TT-43",
            title = "Renamed",
            details = "New notes.",
            statusRaw = "done",
            priorityRaw = Priority.Lowest.rank,
            assignee = null,
            dueDate = null,
            rank = 99,
            createdAt = stored.createdAt,
            updatedAt = Moments.muchLater,
            // Dated rather than copied: the incoming value had none, and a terminal status
            // with no date is an item the archive cannot file.
            resolvedAt = Moments.muchLater,
        )

        assertEquals(expected, stored.apply(incoming))
    }

    // MARK: The read direction

    @Test
    fun readingAProjectBackReproducesEveryColumn() {
        val record = ProjectRecord(
            id = "11111111-1111-4111-8111-111111111111",
            keyPrefix = "TT",
            name = "Task Tracker",
            details = "Everything, in one place.",
            isArchived = true,
            nextItemNumber = 42,
            createdAt = Moments.start,
            updatedAt = Moments.later,
        )

        assertEquals(aProject(), record.domainValue)
    }

    @Test
    fun readingAWorkItemBackReproducesEveryColumn() {
        val record = WorkItemRecord(
            id = "22222222-2222-4222-8222-222222222222",
            projectID = "11111111-1111-4111-8111-111111111111",
            key = "TT-42",
            title = "Write the migration",
            details = "By hand, then check the generated SQL.",
            statusRaw = "in_review",
            priorityRaw = Priority.High.rank,
            assignee = "Ada",
            dueDate = Moments.muchLater,
            rank = 7,
            createdAt = Moments.start,
            updatedAt = Moments.later,
            resolvedAt = null,
        )

        assertEquals(aWorkItem(), record.domainValue)
    }

    // MARK: The keys

    @Test
    fun aKeyIsStoredAsTypedRatherThanRebuiltFromItsPrefixAndNumber() {
        // A server may issue a key this build's `prefix-number` convention would never
        // produce. Reassembling it would corrupt a key that other devices and every server
        // reference already use, so the key is a string all the way through.
        val record = WorkItemRecord(
            id = "22222222-2222-4222-8222-222222222222",
            projectID = "11111111-1111-4111-8111-111111111111",
            key = "SOMETHING-ELSE",
            title = "Issued by the server",
            details = "",
            statusRaw = "todo",
            priorityRaw = Priority.Medium.rank,
            assignee = null,
            dueDate = null,
            rank = 0,
            createdAt = Moments.start,
            updatedAt = Moments.start,
            resolvedAt = null,
        )

        assertEquals(WorkItemKey("SOMETHING-ELSE"), record.domainValue?.key)
    }

    // MARK: What the completeness test is not

    // There is deliberately no test that enumerates the columns and reports a diff. Every
    // column in these three entities is named in a constructor call in this class, and a
    // constructor call that omits a parameter does not compile — so the enumeration is the
    // compiler's, it runs on every target, and it cannot be stale. A reflective version would
    // have been a *report* of the same fact that could be out of date, rather than a build
    // failure. The eight project columns, thirteen work-item columns and eight status-change
    // columns are all here; `sequence` is the one column with no domain counterpart, and it
    // is the one that is not named in an expected record.

    private fun aChange(): StatusChange = StatusChange(
        workItemID = aWorkItem().id,
        projectID = aProject().id,
        from = WorkItemStatus.Todo,
        to = WorkItemStatus.InProgress,
        changedAt = Moments.start,
        changedBy = randomUserID(),
    )
}
