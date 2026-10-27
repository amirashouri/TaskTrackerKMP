package com.tasktracker.sharedui

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.featuresettingspresentation.ResetCodeCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
 * [PasswordResetCodeModel] against a port that answers from scripted values.
 *
 * ## What these pin, and why it is not the transport
 *
 * `:packages:feature-teams-data` already proves the request's shape — the route, the verb, the
 * member in the path. What can go wrong *here* is the model's own bookkeeping: loading the
 * directory before anyone asked, minting for the member that is selected rather than the one
 * that was selected a tap ago, and turning a refusal into a crash. Each test names the defect it
 * would catch.
 *
 * ## Why the fake is local rather than shared with the members screen's
 *
 * `MemberAdminModelTest` has its own `RecordingMemberAdminPort`, and it is not a published
 * fixture: test source sets are not dependencies. This one records the two calls the sheet makes
 * and nothing else, which is also the honest statement of how narrow this model is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetCodeModelTest {

    private val ada = user("00000000-0000-4000-8000-000000000001", "ada@example.com")
    private val grace = user("00000000-0000-4000-8000-000000000002", "grace@example.com")

    /**
     * A fresh model has not touched the port.
     *
     * The load is deferred to the segment being opened, so a Settings visit by an administrator
     * who never changes anyone's password must cost nothing. Reverting
     * [PasswordResetCodeModel.ensureMembersLoaded] to an eager load is the mutation this
     * catches — and it would show up here as one request before any call.
     */
    @Test
    fun theDirectoryIsNotReadUntilTheGeneratorAsksForIt() = runTest {
        val port = RecordingMemberAdminPort(members = listOf(ada, grace))
        val model = PasswordResetCodeModel(port)

        assertTrue(port.membersRequests.isEmpty(), "a model that has never been asked read the directory")
        assertTrue(model.panel.candidates.isEmpty())
        assertNull(model.panel.selected)
        assertNull(model.panel.minted)
        assertFalse(model.panel.isLoadingCandidates)
    }

    /**
     * Opening the segment reads the active bucket, once, however many times it is opened.
     *
     * A second read would be a second round trip for an answer already on screen, and a
     * directory read on every segment switch is how a form starts to feel slow.
     */
    @Test
    fun openingTheGeneratorReadsTheActiveBucketOnce() = runTest {
        val port = RecordingMemberAdminPort(members = listOf(ada, grace))
        val model = PasswordResetCodeModel(port)

        model.ensureMembersLoaded(this)
        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        assertEquals(listOf(UserStatus.Active), port.membersRequests)
        assertEquals(listOf("ada", "grace"), model.panel.candidates.map { it.label })
        assertFalse(model.panel.isLoadingCandidates)
    }

    /**
     * The directory is reduced to the three strings the sheet may know.
     *
     * The greeting is the label and the address the detail, because the sheet cannot see a
     * `User` — this mapping is the whole boundary, so it is worth asserting rather than
     * assuming: a `null` display name must fall back to the address prefix, which is what
     * `preferredGreeting` decides, not what this model decides.
     */
    @Test
    fun aDirectoryRowBecomesACandidateWithItsGreetingAndAddress() = runTest {
        val named = user("00000000-0000-4000-8000-000000000003", "grace@example.com", "Grace")
        val port = RecordingMemberAdminPort(members = listOf(named))
        val model = PasswordResetCodeModel(port)

        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        assertEquals(
            ResetCodeCandidate(id = named.id.stringValue, label = "Grace", detail = "grace@example.com"),
            model.panel.candidates.single(),
        )
    }

    /**
     * A refused load is held and leaves no rows.
     *
     * The rows are dropped rather than kept: a stale directory would offer a member who may have
     * been deleted since, and the code minted for them would be refused after the administrator
     * had already handed it over.
     */
    @Test
    fun aRefusedLoadIsHeldAndLeavesTheListEmpty() = runTest {
        val refusal = AppError.Forbidden
        val port = RecordingMemberAdminPort(members = listOf(ada), membersFailure = refusal)
        val model = PasswordResetCodeModel(port)

        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        assertTrue(model.panel.candidates.isEmpty())
        assertSame(refusal, model.panel.failure)
        assertFalse(model.panel.isLoadingCandidates)
    }

    /**
     * A failed load is retried on the next ask; a successful one is not.
     *
     * The distinction is what makes the deferred load safe: the flag that stops a second read is
     * set by an answer, not by an attempt, so a directory that could not be reached is asked
     * again the next time the administrator opens the segment rather than staying empty until
     * the app restarts.
     */
    @Test
    fun aFailedLoadIsAskedAgainAndASuccessIsNot() = runTest {
        val port = RecordingMemberAdminPort(members = listOf(ada), membersFailure = AppError.Offline)
        val model = PasswordResetCodeModel(port)

        model.ensureMembersLoaded(this)
        advanceUntilIdle()
        assertEquals(1, port.membersRequests.size)

        port.membersFailure = null
        model.ensureMembersLoaded(this)
        advanceUntilIdle()
        assertEquals(2, port.membersRequests.size)
        assertEquals(1, model.panel.candidates.size)

        model.ensureMembersLoaded(this)
        advanceUntilIdle()
        assertEquals(2, port.membersRequests.size, "a loaded directory was read again")
    }

    /**
     * The mint names the member that is selected now, and the code comes back with them.
     *
     * The pair travels together because the sentence that delivers the code names the person: a
     * code shown without knowing whose it is cannot be handed to anyone.
     */
    @Test
    fun mintingUsesTheSelectedMemberAndHoldsTheCodeWithThem() = runTest {
        val port = RecordingMemberAdminPort(members = listOf(ada, grace), resetCode = "code-for-grace")
        val model = PasswordResetCodeModel(port)
        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        model.select(model.panel.candidates.single { it.label == "grace" })
        model.generate()

        assertEquals(listOf(grace.id), port.resetCodeRequests)
        val minted = assertNotNull(model.panel.minted, "the minted code never reached the sheet")
        assertEquals("grace", minted.forMember.label)
        assertEquals("code-for-grace", minted.code)
        assertFalse(model.panel.isGenerating)
        assertNull(model.panel.failure)
    }

    /**
     * Choosing someone else drops the code that was minted for the previous choice.
     *
     * Without this, an administrator who mints for Ada and then picks Grace still has Ada's code
     * on screen under Grace's name — and the Copy button beside it would put the wrong person's
     * credential in the clipboard. That is the defect this exists to catch.
     */
    @Test
    fun choosingAnotherMemberDropsTheCodeMintedForTheLastOne() = runTest {
        val port = RecordingMemberAdminPort(members = listOf(ada, grace), resetCode = "code-for-ada")
        val model = PasswordResetCodeModel(port)
        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        model.select(model.panel.candidates.single { it.label == "ada" })
        model.generate()
        assertNotNull(model.panel.minted)

        model.select(model.panel.candidates.single { it.label == "grace" })

        assertNull(model.panel.minted, "the previous member's code survived the selection")
    }

    /** Nothing selected, nothing sent: the button is disabled, and the guard keeps that true. */
    @Test
    fun mintingWithNothingSelectedSendsNothing() = runTest {
        val port = RecordingMemberAdminPort(members = listOf(ada))
        val model = PasswordResetCodeModel(port)
        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        model.generate()

        assertTrue(port.resetCodeRequests.isEmpty())
        assertNull(model.panel.minted)
        assertFalse(model.panel.isGenerating)
    }

    /**
     * A refusal is held, no code is shown, and the button becomes usable again.
     *
     * `isGenerating` is cleared in a `finally` for exactly this: a refusal that left the button
     * spinning would look like a request still in flight, and the administrator would wait for
     * an answer that already arrived.
     */
    @Test
    fun aRefusedMintIsHeldAndShowsNoCode() = runTest {
        val refusal = AppError.Validation("That member no longer exists.")
        val port = RecordingMemberAdminPort(members = listOf(ada), resetCodeFailure = refusal)
        val model = PasswordResetCodeModel(port)
        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        model.select(model.panel.candidates.single())
        model.generate()

        assertNull(model.panel.minted)
        assertSame(refusal, model.panel.failure)
        assertFalse(model.panel.isGenerating)
    }

    /**
     * A cancelled mint is rethrown rather than held as a failure.
     *
     * The sheet is disposed while a mint is in flight whenever the administrator closes it mid
     * request. Holding that as a refusal would arm a failure banner for a sheet that no longer
     * exists — the same rule `SettingsModel.signOut` states.
     */
    @Test
    fun aCancelledMintIsRethrown() = runTest {
        val port = RecordingMemberAdminPort(
            members = listOf(ada),
            resetCodeFailure = CancellationException("disposed"),
        )
        val model = PasswordResetCodeModel(port)
        model.ensureMembersLoaded(this)
        advanceUntilIdle()
        model.select(model.panel.candidates.single())

        val thrown = runCatching { model.generate() }.exceptionOrNull()

        assertIs<CancellationException>(thrown, "a cancelled mint was held as a failure")
        assertNull(model.panel.failure)
    }

    /**
     * A load cancelled by the destination leaving does not count as a loaded directory.
     *
     * `stopObserving` cancels the read in flight, and the "already loaded" flag must not have
     * been set by that attempt: if it were, the next visit to the sheet would show an empty
     * picker forever, because nothing would ask again. This is the reason the flag is set by an
     * answer inside `load()` rather than when the job is launched.
     */
    @Test
    fun aLoadCancelledByTheDestinationLeavingIsAskedAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        val port = RecordingMemberAdminPort(members = listOf(ada), gate = gate)
        val model = PasswordResetCodeModel(port)

        model.ensureMembersLoaded(this)
        runCurrent()
        model.stopObserving()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.panel.candidates.isEmpty(), "a cancelled load still filled the picker")

        model.ensureMembersLoaded(this)
        advanceUntilIdle()

        assertEquals(2, port.membersRequests.size, "the cancelled load was treated as a loaded one")
        assertEquals(1, model.panel.candidates.size)
    }
}

/**
 * The two calls the generator makes, plus the failures it can be handed.
 *
 * Mutable failure fields rather than constructor-only ones, because one test flips a refusal
 * back to a success to prove the retry happens.
 */
private class RecordingMemberAdminPort(
    private val members: List<User> = emptyList(),
    var membersFailure: Throwable? = null,
    var resetCodeFailure: Throwable? = null,
    var resetCode: String = "reset-code-under-test",
    /** When set, the directory read suspends until it completes — for the cancellation test. */
    private val gate: CompletableDeferred<Unit>? = null,
) : MemberAdminPort {

    val membersRequests = mutableListOf<UserStatus>()

    val resetCodeRequests = mutableListOf<UserID>()

    override suspend fun members(status: UserStatus): List<User> {
        membersRequests += status
        gate?.await()
        membersFailure?.let { throw it }
        return members
    }

    override suspend fun generateResetCode(id: UserID): String {
        resetCodeRequests += id
        resetCodeFailure?.let { throw it }
        return resetCode
    }

    // The rest of the admin surface, and the read-only directory beneath it, are not this
    // model's business: it holds one command, so the fake implements one.

    override suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): User = error("the reset-code model does not create members")

    override suspend fun softDeleteMember(id: UserID) = error("the reset-code model does not delete members")

    override suspend fun reactivateMember(id: UserID): User = error("the reset-code model does not restore members")

    override suspend fun user(id: UserID): User? = null

    override suspend fun users(ids: List<UserID>): List<User> = emptyList()

    override suspend fun searchMembers(query: String): List<User> = emptyList()
}

private fun user(rawID: String, email: String, displayName: String? = null) = User(
    id = UserID(rawID),
    email = email,
    displayName = displayName,
    status = UserStatus.Active,
)
