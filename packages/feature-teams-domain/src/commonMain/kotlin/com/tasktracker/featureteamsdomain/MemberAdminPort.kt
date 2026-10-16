package com.tasktracker.featureteamsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import kotlin.coroutines.cancellation.CancellationException

/**
 * The member directory as an administrator sees it.
 *
 * [TeamPort] is the read-only view the rest of the app resolves names through — the assignee
 * picker, a sprint owner, the signed-in session. This is that same directory with the writes
 * an admin screen needs, which is what [TeamPort]'s KDoc means by "the seam the backend
 * member API replaces". It is a separate interface rather than new methods on [TeamPort]
 * because the picker must not be able to reach a delete: the read surface stays narrow, the
 * admin surface states itself, and a screen's dependency names which one it holds.
 *
 * Every method is `suspend` and `@Throws(AppError, CancellationException)`. Kotlin/Native
 * rejects `@Throws` on a suspend function unless cancellation is among the listed classes,
 * and naming it states the real contract — a cancelled call is not a failed one.
 */
interface MemberAdminPort : TeamPort {

    /**
     * The members in [status]'s bucket, for the members screen's Active/Deleted control.
     *
     * Only [UserStatus.Active] and [UserStatus.Deleted] name a bucket the server serves. The
     * server's `active` bucket is every non-deleted member — an [UserStatus.Inactive] member
     * is still one an admin can manage — so `inactive` is a state a row may carry, not a
     * bucket to ask for. [UserStatus.Inactive] is therefore not a valid argument here and the
     * transport must not forward it.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun members(status: UserStatus): List<User>

    /**
     * Creates an account and returns it as it now stands in the directory.
     *
     * [role] decides whether the new row may itself administer; [position] is the free-text
     * job title the list shows. The account starts active, so the created row belongs in the
     * active bucket.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): User

    /**
     * Marks [id] deleted, which is what refuses its next sign-in and refresh.
     *
     * A soft delete: the row survives under the deleted bucket so it can be found and
     * restored later. The server answers `204` with no body — deleting is idempotent in
     * effect and there is no updated row to hand back — so this returns nothing and the
     * caller moves the row to the deleted bucket on its own.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun softDeleteMember(id: UserID)

    /**
     * Restores a deleted [id], returning it to the active bucket.
     *
     * The inverse of [softDeleteMember] and a plain update on the server, which answers with
     * the member as it now stands so the caller can move the row between buckets without a
     * refetch.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun reactivateMember(id: UserID): User
}
