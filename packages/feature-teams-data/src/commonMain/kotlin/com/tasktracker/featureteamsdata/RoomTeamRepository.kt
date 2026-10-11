package com.tasktracker.featureteamsdata

import com.tasktracker.core.UserID
import com.tasktracker.database.CachedSession
import com.tasktracker.database.SessionCache
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus

/**
 * TeamPort backed by the session cache.
 *
 * The signed-in identity is the one user the directory can always return: it is written to
 * SessionCache on sign-in (SessionCoordinator caches UserIdentity), and a UserID is a
 * distinct value type, so matching against it is exact rather than a fragile string compare
 * against a name two people could share. Other members land in a dedicated users table once a
 * sync transport exists; until then they are unknown here and the lookup returns null for them.
 */
class RoomTeamRepository(
    private val sessionCache: SessionCache? = null,
) : TeamPort {

    override suspend fun user(id: UserID): User? {
        val session = sessionCache?.current() ?: return null
        return if (session.userID == id.stringValue) fromSession(session, id) else null
    }

    override suspend fun users(ids: List<UserID>): List<User> {
        val session = sessionCache?.current() ?: return emptyList()
        return ids.mapNotNull { id ->
            if (session.userID == id.stringValue) fromSession(session, id) else null
        }
    }

    /**
     * The member picker's candidate list: the signed-in user plus the fake directory,
     * filtered by [query], sorted by name.
     *
     * The session user is merged in rather than special-cased into a separate row, so a
     * fresh install — where they are the only real person in the app — still shows a list
     * the search box can filter. Ordering is [FakeMemberDirectory]'s job and stays
     * alphabetical across the merge, which means the backend can replace the fake's
     * `people` with its own list and no caller can tell the swap happened.
     */
    override suspend fun searchMembers(query: String): List<User> {
        val session = sessionCache?.current()
        val sessionUser = session?.let { fromSession(it, UserID(it.userID)) }
        val candidates = listOfNotNull(sessionUser) + FakeMemberDirectory.people
        return FakeMemberDirectory.search(candidates, query)
    }

    private fun fromSession(session: CachedSession, id: UserID): User = User(
        id = id,
        email = session.email,
        displayName = session.displayName,
        // The session cache carries only identity fields; avatar, position and team status
        // are member-list data and stay at their defaults until such a list arrives.
        avatarId = null,
        position = null,
        status = UserStatus.Active,
        lastSeen = session.lastSignedInAt,
    )
}
