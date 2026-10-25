package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * The member directory as an admin screen sees it: reads resolve locally, writes go to the
 * server.
 *
 * ## Why this exists rather than `HttpMemberAdminRemote` alone
 *
 * [MemberAdminPort] extends [TeamPort], so an implementation owes the three reads as well as
 * the four admin calls — and the reads are not the remote's to answer: `user`/`users` resolve a
 * `UserID` the app already knows (an assignee, a sprint owner), which the session-backed
 * [TeamPort] answers without a round trip. This class is the seam that satisfies the whole port
 * by holding one of each: [team] for the reads, [remote] for the admin calls. It is the same
 * shape `AuthRepository` takes over `AuthRemotePort` + `TokenVault`.
 *
 * The split is load-bearing rather than cosmetic. The admin surface is the only writer here, and
 * keeping it behind this class means a picker that holds a plain [TeamPort] — which is every
 * other consumer — has no method that could delete a member.
 *
 * ## The admin calls are online-only, by design
 *
 * Nothing here queues an outbox row or caches the server's answer. The member list is an
 * administrative view of the live directory, so a failed write is surfaced to the admin rather
 * than replayed later: a delete the server never saw is not a delete, and an offline queue that
 * claimed otherwise would show a member as removed while their session still worked.
 *
 * Every override re-states `@Throws(AppError, CancellationException)` even though the interface
 * already carries it. The annotation is not inherited by an override, and Kotlin/Native rejects
 * `@Throws` on a suspend function unless cancellation is among the listed classes — so naming it
 * per method is what keeps the platform export honest, the same way `AuthRepository` does it.
 */
class MemberAdminRepository(
    private val remote: HttpMemberAdminRemote,
    private val team: TeamPort,
) : MemberAdminPort {

    @Throws(AppError::class, CancellationException::class)
    override suspend fun user(id: UserID): User? = team.user(id)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun users(ids: List<UserID>): List<User> = team.users(ids)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun searchMembers(query: String): List<User> = team.searchMembers(query)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun members(status: UserStatus): List<User> = remote.members(status)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): User = remote.createMember(email = email, password = password, role = role, position = position)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun softDeleteMember(id: UserID) {
        remote.softDeleteMember(id)
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun reactivateMember(id: UserID): User = remote.reactivateMember(id)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun generateResetCode(id: UserID): String = remote.generateResetCode(id)
}
