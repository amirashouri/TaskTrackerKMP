package com.tasktracker.featureteamsdomain

import com.tasktracker.core.UserID

/**
 * The read-only view of the user directory.
 *
 * Resolves a [UserID] — the value carried by a work item's assignee, a sprint's owner, and the
 * signed-in session — into the [User] profile the screens display. The signed-in account is
 * always resolvable because it is seeded onto the session cache on sign-in. With a backend
 * bound the directory is the server's live account list; without one it is the session cache,
 * and [RoomTeamRepository] answers for the signed-in person alone.
 */
interface TeamPort {

    /** The profile for [id], or `null` if no such user is known here. */
    suspend fun user(id: UserID): User?

    /** Every profile whose id is in [ids], in the requested order, with unknown ids omitted. */
    suspend fun users(ids: List<UserID>): List<User>

    /**
     * Members matching [query], for the member picker's search box.
     *
     * A blank query returns everyone known; otherwise the match is a case-insensitive
     * substring on the display name or the email. With a backend this is the server's live
     * account directory; offline it is the signed-in account. The picker filters a real list
     * through one method either way.
     */
    suspend fun searchMembers(query: String): List<User>
}
