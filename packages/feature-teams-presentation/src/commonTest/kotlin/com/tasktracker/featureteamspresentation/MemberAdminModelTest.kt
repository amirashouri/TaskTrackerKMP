package com.tasktracker.featureteamspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [MemberAdminModel] against a port that answers from scripted values.
 *
 * ## Why the fake is here rather than published by the domain module
 *
 * `:packages:feature-teams-data` has its own recording port for the HTTP tests, and it is not a
 * published fixture. What this one needs to prove is different anyway: not that a request was
 * *shaped* correctly — that is the transport's test — but that the model reads the bucket it
 * says it is on, moves rows the way the port's own KDoc promises, and holds a failure instead of
 * throwing it. So the recording here is of *which* calls were made and with what, and the
 * fixtures are the three statuses a row can carry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberAdminModelTest {

    private val ada = user("00000000-0000-4000-8000-000000000001", "ada@example.com", UserStatus.Active)
    private val grace = user("00000000-0000-4000-8000-000000000002", "grace@example.com", UserStatus.Inactive)
    private val alan = user("00000000-0000-4000-8000-000000000003", "alan@example.com", UserStatus.Deleted)
    private val newHire = user("00000000-0000-4000-8000-000000000004", "new@example.com", UserStatus.Active)

    /**
     * The segment the screen opens on.
     *
     * Asserted with no `observe` and no call to the port, so what it pins is the *default in the
     * model* rather than anything a load could have set. Reverting the default to `Deleted` is
     * the mutation this exists to catch.
     */
    @Test
    fun theScreenStartsWithTheActiveBucket() {
        val model = MemberAdminModel(RecordingMemberAdminPort())

        assertEquals(MembersBucket.Active, model.bucket)
        assertEquals(UserStatus.Active, MembersBucket.Active.status)
    }

    @Test
    fun theTwoBucketsAreTheTwoThePortServesAndNeitherIsInactive() = runTest {
        val port = RecordingMemberAdminPort()
        val model = MemberAdminModel(port)

        model.observe(this)
        advanceUntilIdle()

        assertEquals(listOf(UserStatus.Active), port.membersRequests)
        assertTrue(
            MembersBucket.entries.none { it.status == UserStatus.Inactive },
            "no bucket may ask for the inactive status: the port refuses it, and the screen " +
                "must not be able to spell it",
        )
    }

    @Test
    fun observingPublishesTheRowsTheBucketHolds() = runTest {
        val port = RecordingMemberAdminPort(
            byStatus = mapOf(
                UserStatus.Active to listOf(ada, grace),
                UserStatus.Deleted to listOf(alan),
            ),
        )
        val model = MemberAdminModel(port)

        model.observe(this)
        advanceUntilIdle()

        assertEquals(listOf(ada, grace), model.members)
        assertNull(model.listFailure)
        model.stopObserving()
    }

    @Test
    fun switchingToDeletedReadsTheDeletedBucketAndShowsItsRows() = runTest {
        val port = RecordingMemberAdminPort(
            byStatus = mapOf(
                UserStatus.Active to listOf(ada),
                UserStatus.Deleted to listOf(alan),
            ),
        )
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()

        model.selectBucket(MembersBucket.Deleted)
        advanceUntilIdle()

        assertEquals(MembersBucket.Deleted, model.bucket)
        assertEquals(listOf(alan), model.members)
        assertEquals(listOf(UserStatus.Active, UserStatus.Deleted), port.membersRequests)
    }

    /**
     * A second tap on the segment already selected is answered with a fresh read, not a no-op.
     *
     * The picker reports a tap on the selected segment the same way it reports any other, so a
     * silent return would leave the screen showing whatever a failed earlier load left behind
     * with nothing the user could do about it.
     */
    @Test
    fun choosingTheBucketAlreadyOnScreenReadsItAgain() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Active to listOf(ada)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()

        model.selectBucket(MembersBucket.Active)
        advanceUntilIdle()

        assertEquals(listOf(UserStatus.Active, UserStatus.Active), port.membersRequests)
    }

    @Test
    fun aDeletedRowOffersReactivateAndALiveRowOffersDelete() {
        assertEquals(MemberRowAction.Reactivate, memberRowAction(alan))
        assertEquals(MemberRowAction.Delete, memberRowAction(ada))
        // An inactive member is still one an admin can remove: `inactive` is a state a row
        // carries, not a bucket, and the server's active bucket is every non-deleted member.
        assertEquals(MemberRowAction.Delete, memberRowAction(grace))
    }

    @Test
    fun aFailedLoadIsHeldAsAFailureAndTheOldRowsAreDropped() = runTest {
        val port = RecordingMemberAdminPort(
            byStatus = mapOf(UserStatus.Active to listOf(ada), UserStatus.Deleted to listOf(alan)),
        )
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        assertEquals(listOf(ada), model.members)

        port.membersFailure = AppError.Transport("connection reset")
        model.selectBucket(MembersBucket.Deleted)
        advanceUntilIdle()

        assertIs<AppError.Transport>(model.listFailure)
        // The active rows are gone: a failed load of Deleted that kept the Active rows on screen
        // would show members that are not in the bucket the control now names.
        assertEquals(emptyList<User>(), model.members)
        assertEquals(MembersBucket.Deleted, model.bucket)
    }

    /**
     * A cancelled load is rethrown, not held as a failure.
     *
     * The screen is disposed while this call is in flight whenever the user leaves the members
     * page, and reaching for the bucket is exactly what `selectBucket` does on the way out. A
     * model that absorbed the cancellation would put an "offline" banner over whatever the user
     * navigated to next — reporting the exit as a server failure.
     *
     * The `try`/`catch` is the assertion, not noise: the exception is caught here rather than
     * left to escape into `runTest`, which is what makes it possible to assert both that it was
     * thrown at all and that nothing was held.
     */
    @Test
    fun aCancelledLoadIsRethrownRatherThanHeldAsAFailure() = runTest {
        val port = RecordingMemberAdminPort()
        port.membersFailure = CancellationException("the screen was disposed mid-load")
        val model = MemberAdminModel(port)

        val thrown = try {
            model.reload()
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertNotNull(thrown, "a cancellation must be rethrown, not absorbed")
        assertNull(model.listFailure)
        assertFalse(model.isLoading, "the rethrown cancellation must still clear the in-flight flag")
    }

    @Test
    fun dismissingTheFailureClearsIt() = runTest {
        val port = RecordingMemberAdminPort(membersFailure = AppError.Server(500, "boom"))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        assertIs<AppError.Server>(model.listFailure)

        model.dismissListFailure()

        assertNull(model.listFailure)
    }

    @Test
    fun theTrashButtonRaisesAConfirmationAndSendsNothing() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Active to listOf(ada)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()

        model.requestDelete(ada)
        advanceUntilIdle()

        assertSame(ada, model.pendingDeletion)
        assertEquals(emptyList<UserID>(), port.deletedIDs)
        assertEquals(listOf(ada), model.members)
    }

    @Test
    fun cancellingTheConfirmationSendsNothing() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Active to listOf(ada)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        model.requestDelete(ada)

        model.cancelDelete()
        advanceUntilIdle()

        assertNull(model.pendingDeletion)
        assertEquals(emptyList<UserID>(), port.deletedIDs)
        assertEquals(listOf(ada), model.members)
    }

    @Test
    fun confirmingTheDeletionCallsThePortAndDropsTheRow() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Active to listOf(ada, grace)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        model.requestDelete(ada)

        model.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf(ada.id), port.deletedIDs)
        assertEquals(listOf(grace), model.members)
        assertNull(model.pendingDeletion)
        assertNull(model.listFailure)
    }

    @Test
    fun aFailedDeletionKeepsTheRowAndRaisesTheFailure() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Active to listOf(ada)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        port.deleteFailure = AppError.Forbidden
        model.requestDelete(ada)

        model.confirmDelete()
        advanceUntilIdle()

        assertIs<AppError.Forbidden>(model.listFailure)
        // The row stays: a row that vanished on a failed delete would be the client lying about
        // what the server did.
        assertEquals(listOf(ada), model.members)
        assertNull(model.pendingDeletion)
    }

    @Test
    fun reactivatingTakesTheRowOutOfTheDeletedBucket() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Deleted to listOf(alan)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        model.selectBucket(MembersBucket.Deleted)
        advanceUntilIdle()
        assertEquals(listOf(alan), model.members)

        model.reactivate(alan)
        advanceUntilIdle()

        assertEquals(listOf(alan.id), port.reactivatedIDs)
        assertEquals(emptyList<User>(), model.members)
        assertNull(model.listFailure)
    }

    /**
     * A failed reactivation keeps the row and raises the failure.
     *
     * The mirror of [aFailedDeletionKeepsTheRowAndRaisesTheFailure], and the reason it exists at
     * all: `reactivate` is the one command whose failure path had no test, and the row it acts on
     * is already *gone* from the list — dropping it again on a failed call would look like a
     * success. A restore the server refused must leave the deleted row where it was.
     */
    @Test
    fun aFailedReactivationKeepsTheRowAndRaisesTheFailure() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Deleted to listOf(alan)))
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        model.selectBucket(MembersBucket.Deleted)
        advanceUntilIdle()
        port.reactivateFailure = AppError.Forbidden

        model.reactivate(alan)
        advanceUntilIdle()

        assertIs<AppError.Forbidden>(model.listFailure)
        // The row stays: the server refused, so the member is still deleted, and a list that
        // had already dropped them would be the client showing a restore that did not happen.
        assertEquals(listOf(alan), model.members)
        assertEquals(MembersBucket.Deleted, model.bucket)
    }

    @Test
    fun creatingAppendsTheNewMemberAndRecordsWhatWasSent() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Active to listOf(ada)))
        port.created = newHire
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()

        val created = model.createMember(
            email = newHire.email,
            password = "correct horse",
            role = MemberRole.Admin,
            position = "Product owner",
        )
        advanceUntilIdle()

        assertTrue(created)
        assertEquals(listOf(ada, newHire), model.members)
        assertEquals(
            listOf(CreateCall(newHire.email, "correct horse", MemberRole.Admin, "Product owner")),
            port.createCalls,
        )
    }

    /**
     * A new account is active, so it does not belong under Deleted.
     *
     * Appending it there would show an active member beneath a Deleted heading — a row the next
     * read of that bucket would silently remove.
     */
    @Test
    fun creatingWhileTheDeletedBucketIsOnScreenLeavesThatListAlone() = runTest {
        val port = RecordingMemberAdminPort(byStatus = mapOf(UserStatus.Deleted to listOf(alan)))
        port.created = newHire
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        model.selectBucket(MembersBucket.Deleted)
        advanceUntilIdle()

        val created = model.createMember(newHire.email, "correct horse", MemberRole.Member, null)
        advanceUntilIdle()

        assertTrue(created)
        assertEquals(listOf(alan), model.members)
        assertEquals(1, port.createCalls.size)
    }

    /**
     * A create that lands counts, and one that fails does not.
     *
     * The count is what the wide window's pane keys its form on: a failure that counted would
     * compose an empty form and throw away the values the admin was correcting, and a success that
     * did not would leave the account just created in the fields for the next tap on Create.
     */
    @Test
    fun onlyAFinishedCreateIsCounted() = runTest {
        val port = RecordingMemberAdminPort()
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        port.created = newHire
        assertEquals(0, model.completedCreates, "nothing has been created yet")

        model.createMember(newHire.email, "correct horse", MemberRole.Member, null)
        advanceUntilIdle()

        assertEquals(
            1,
            model.completedCreates,
            "a create that landed must count, or the pane's next form is the account just made",
        )

        port.createFailure = AppError.Validation("That email is already registered.")
        model.createMember("ada@example.com", "hunter2", MemberRole.Member, null)
        advanceUntilIdle()

        assertEquals(
            1,
            model.completedCreates,
            "a failed create must not count, or the pane replaces the form the admin is " +
                "correcting with an empty one",
        )
    }

    @Test
    fun aFailedCreateHoldsTheFailureAndKeepsTheFormOpen() = runTest {
        val port = RecordingMemberAdminPort()
        port.createFailure = AppError.Validation("That email is already registered.")
        val model = MemberAdminModel(port)
        model.observe(this)
        advanceUntilIdle()
        model.openAddForm()

        val created = model.createMember("ada@example.com", "hunter2", MemberRole.Member, null)
        advanceUntilIdle()

        assertFalse(created)
        assertIs<AppError.Validation>(model.createFailure)
        assertTrue(model.isAddFormOpen, "a failed create must not close the form, or the values typed into it are lost")
        assertEquals(emptyList<User>(), model.members)
    }

    @Test
    fun openingTheFormClearsWhateverTheLastOneFailedWith() = runTest {
        val port = RecordingMemberAdminPort()
        port.createFailure = AppError.Transport("offline")
        val model = MemberAdminModel(port)
        model.createMember("ada@example.com", "hunter2", MemberRole.Member, null)
        advanceUntilIdle()
        assertIs<AppError.Transport>(model.createFailure)

        model.openAddForm()
        advanceUntilIdle()

        assertNull(model.createFailure)
        assertTrue(model.isAddFormOpen)
    }

    @Test
    fun closingTheFormForgetsItsFailure() = runTest {
        val port = RecordingMemberAdminPort()
        port.createFailure = AppError.Transport("offline")
        val model = MemberAdminModel(port)
        model.openAddForm()
        model.createMember("ada@example.com", "hunter2", MemberRole.Member, null)
        advanceUntilIdle()

        model.closeAddForm()
        advanceUntilIdle()

        assertFalse(model.isAddFormOpen)
        assertNull(model.createFailure)
    }
}

/** One `createMember` call, as the port received it. */
data class CreateCall(
    val email: String,
    val password: String,
    val role: MemberRole,
    val position: String?,
)

/**
 * A [MemberAdminPort] whose answers are scripted and whose calls are recorded.
 *
 * Every failure slot is a `Throwable` rather than an [AppError] so a test can also script the
 * one failure the model must *not* hold: cancellation.
 */
class RecordingMemberAdminPort(
    private val byStatus: Map<UserStatus, List<User>> = emptyMap(),
    var created: User? = null,
    var membersFailure: Throwable? = null,
    var createFailure: Throwable? = null,
    var deleteFailure: Throwable? = null,
    var reactivateFailure: Throwable? = null,
) : MemberAdminPort {

    /** Every status asked for, in order, so a re-read is visible rather than inferred. */
    val membersRequests = mutableListOf<UserStatus>()

    val createCalls = mutableListOf<CreateCall>()

    val deletedIDs = mutableListOf<UserID>()

    val reactivatedIDs = mutableListOf<UserID>()

    override suspend fun members(status: UserStatus): List<User> {
        membersRequests += status
        membersFailure?.let { throw it }
        return byStatus[status].orEmpty()
    }

    override suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): User {
        createCalls += CreateCall(email, password, role, position)
        createFailure?.let { throw it }
        return created ?: error("RecordingMemberAdminPort.created was not set for this test")
    }

    override suspend fun softDeleteMember(id: UserID) {
        deletedIDs += id
        deleteFailure?.let { throw it }
    }

    override suspend fun reactivateMember(id: UserID): User {
        reactivatedIDs += id
        reactivateFailure?.let { throw it }
        return byStatus[UserStatus.Deleted]?.firstOrNull { it.id == id }
            ?.copy(status = UserStatus.Active)
            ?: user(id.stringValue, "restored@example.com", UserStatus.Active)
    }

    // The read-only half of `TeamPort`, which the members screen never uses.

    override suspend fun user(id: UserID): User? = null

    override suspend fun users(ids: List<UserID>): List<User> = emptyList()

    override suspend fun searchMembers(query: String): List<User> = emptyList()
}

private fun user(rawID: String, email: String, status: UserStatus) = User(
    id = UserID(rawID),
    email = email,
    status = status,
)
