package com.tasktracker.featureteamsdomain

import com.tasktracker.core.UserID

/**
 * The read-only view of the user directory.
 *
 * Resolves a [UserID] — the value carried by a work item's assignee, a sprint's owner, and the
 * signed-in session — into the [User] profile the screens display. The signed-in account is
 * always resolvable because it is seeded onto the session cache on sign-in; other members are
 * `null` here until a sync transport lands a member list (see [RoomTeamRepository]).
 */
interface TeamPort {

    /** The profile for [id], or `null` if no such user is known here. */
    suspend fun user(id: UserID): User?

    /** Every profile whose id is in [ids], in the requested order, with unknown ids omitted. */
    suspend fun users(ids: List<UserID>): List<User>
}
