package com.tasktracker.featureteamspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The two buckets the members screen can show.
 *
 * A type of its own rather than a bare [UserStatus] because the port serves exactly two
 * buckets and refuses a third: `members(Inactive)` is a `400`, since every non-deleted member
 * — inactive ones included — belongs to the active bucket. Spelling the buckets here makes
 * that refusal unrepresentable in the screen. There is no value of this enum that maps to
 * [UserStatus.Inactive], so the segmented control cannot ask for one and no call site has to
 * remember not to.
 */
enum class MembersBucket(val status: UserStatus) {
    /** Everyone the server still lets sign in, of any [UserStatus] other than deleted. */
    Active(UserStatus.Active),

    /** The soft-deleted rows, kept so they can be restored. */
    Deleted(UserStatus.Deleted),
}

/**
 * Drives the members screen: two buckets, the rows in the chosen one, and the four admin
 * commands.
 *
 * ## Why the buckets are a type here and a status at the port
 *
 * [MembersBucket] is the screen's two-way switch; [UserStatus] is the server's three-way
 * vocabulary. Keeping them apart is what stops a `when` in the UI from having to answer for
 * [UserStatus.Inactive], which is a status a row may *carry* but never a bucket to ask for.
 *
 * ## Why a delete removes the row locally instead of refetching
 *
 * `softDeleteMember` answers `204` with no body — there is nothing to hand back — and the port
 * says the caller moves the row itself. A refetch would be a second round trip to learn
 * something the client was just told, and it would make the row disappear only after the
 * network agreed. Reactivation is the mirror: it returns the updated member, so the row is
 * dropped from the deleted bucket without asking again. Both are local, and both are what a
 * caller sees immediately.
 *
 * ## Why a create appends only into the bucket it belongs to
 *
 * A new account is created active, so `createMember`'s reply belongs in the active bucket and
 * nowhere else. If the admin is looking at Deleted when they add someone, appending would
 * show an active member under a Deleted heading — a row the next refetch would remove. The
 * guard is one comparison, and it makes the local list and the server agree without a fetch.
 *
 * ## Observation is the caller's scope, as in the settings and projects features
 *
 * `observe(scope)` rather than a scope of its own, so the model cannot outlive the composition
 * that shows it, and [stopObserving] is the one place the load's job is cancelled.
 */
class MemberAdminModel(private val port: MemberAdminPort) {

    /** Which bucket the list is showing. Read-only to the screen, which renders it. */
    var bucket: MembersBucket by mutableStateOf(MembersBucket.Active)
        private set

    /** The rows in [bucket], newest load wins. */
    var members: List<User> by mutableStateOf(emptyList())
        private set

    /** Whether a bucket load is in flight, for the spinner in place of the list. */
    var isLoading: Boolean by mutableStateOf(false)
        private set

    /**
     * The last list-level failure — a load, a delete, or a reactivate — or `null`.
     *
     * Held as the [AppError] rather than as a sentence, for the reason `SettingsModel`
     * documents: a model is a plain class and must stay testable without a composition, so it
     * cannot read a resource, and formatting here would put the wording in a second place from
     * every other user-facing sentence in the app. The screen maps it to [AppError.message].
     */
    var listFailure: AppError? by mutableStateOf(null)
        private set

    /**
     * The member whose deletion is being confirmed, or `null` when no confirmation is up.
     *
     * Non-null is what draws the popup. It is held rather than left to the screen because the
     * *row* is what the confirmation is worded from, and the screen must not hold a second
     * copy of the list it is rendering.
     */
    var pendingDeletion: User? by mutableStateOf(null)
        private set

    /** Whether the add-member form is on screen. */
    var isAddFormOpen: Boolean by mutableStateOf(false)
        private set

    /** Whether a create is in flight, for the form's button. */
    var isCreating: Boolean by mutableStateOf(false)
        private set

    /**
     * The last create failure, or `null`.
     *
     * Separate from [listFailure] because it renders inside the form: a message that appeared
     * behind the dialog as a banner would be a message the user never reads.
     */
    var createFailure: AppError? by mutableStateOf(null)
        private set

    /**
     * How many creates have finished successfully.
     *
     * The wide window keeps the form in its pane for as long as the section is open, so a
     * successful create cannot empty the fields the way the phone's form is emptied — by leaving
     * the composition. The pane keys the form on this count instead, which starts the next create
     * from an empty form rather than from the account just made, where a second tap on Create
     * would be a second account for one address.
     */
    var completedCreates: Int by mutableStateOf(0)
        private set

    private var observation: Job? = null

    /**
     * Loads the default bucket and starts watching nothing else.
     *
     * There is no stream to subscribe to: the member directory is an online view with no local
     * cache and no outbox, so the only thing observation means here is the first load. It is
     * idempotent, and safe to call again after [stopObserving].
     */
    fun observe(scope: CoroutineScope) {
        if (observation != null) return
        observation = scope.launch { load(bucket) }
    }

    fun stopObserving() {
        observation?.cancel()
        observation = null
    }

    /** Re-reads the bucket on screen. */
    suspend fun reload() {
        load(bucket)
    }

    /**
     * Switches buckets and reads the new one.
     *
     * No early return when [next] is already [bucket]: a picker reports a tap on the selected
     * segment the same way it reports any other, and a silent no-op would leave the screen
     * showing whatever a failed earlier load left behind with no way to retry it. Reading again
     * is one request and always truthful.
     */
    suspend fun selectBucket(next: MembersBucket) {
        bucket = next
        load(next)
    }

    /** Clears [listFailure], for a banner the user has read. */
    fun dismissListFailure() {
        listFailure = null
    }

    // MARK: - Delete

    /** Raises the confirmation for [user]. Sends nothing. */
    fun requestDelete(user: User) {
        pendingDeletion = user
    }

    /** Lowers the confirmation without deleting. */
    fun cancelDelete() {
        pendingDeletion = null
    }

    /**
     * Soft-deletes the member the confirmation names and drops the row.
     *
     * The row is removed locally rather than refetched: the server confirms with `204` and no
     * body, so there is nothing to reconcile against, and the row no longer belongs in the
     * active bucket the moment the call succeeds. A failure leaves the list exactly as it was
     * and raises [listFailure] — a row that vanished on a failed delete would be the client
     * lying about what the server did.
     */
    suspend fun confirmDelete() {
        val target = pendingDeletion ?: return
        pendingDeletion = null
        try {
            port.softDeleteMember(target.id)
            members = members.filterNot { it.id == target.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            listFailure = failure
        }
    }

    /** Restores [user] and drops its row from the deleted bucket. */
    suspend fun reactivate(user: User) {
        try {
            val restored = port.reactivateMember(user.id)
            members = members.filterNot { it.id == restored.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            listFailure = failure
        }
    }

    // MARK: - Create

    /** Opens the add-member form. */
    fun openAddForm() {
        createFailure = null
        isAddFormOpen = true
    }

    /** Closes the add-member form without creating anything. */
    fun closeAddForm() {
        isAddFormOpen = false
        createFailure = null
    }

    /**
     * Creates an account and, on success, appends it if it belongs in the bucket on screen.
     *
     * Returns whether the call succeeded, so the caller can close the form on `true` without
     * re-reading [createFailure]. A success is also counted in [completedCreates], which is what
     * gives a form that is never torn down — the wide window's pane — empty fields again. A
     * failure leaves the form open with [createFailure] set — the fields keep their values, so
     * the admin corrects a typo rather than retyping the form.
     * [MemberRole] and the position are the account's own, not this screen's.
     */
    suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): Boolean {
        isCreating = true
        createFailure = null
        return try {
            val created = port.createMember(email, password, role, position)
            if (created.status == bucket.status) members = members + created
            completedCreates++
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            createFailure = failure
            false
        } finally {
            isCreating = false
        }
    }

    private suspend fun load(target: MembersBucket) {
        isLoading = true
        listFailure = null
        try {
            members = port.members(target.status)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            // The rows from the previous bucket are dropped rather than left standing: a failed
            // load of Deleted that kept the Active rows on screen would show members that are
            // not in the bucket the control now names.
            members = emptyList()
            listFailure = failure
        } finally {
            isLoading = false
        }
    }
}
