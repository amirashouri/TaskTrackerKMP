package com.tasktracker.featureteamsdata

import com.tasktracker.core.UserID
import com.tasktracker.database.CachedSession
import com.tasktracker.database.SessionCache
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus

/**
 * TeamPort backed by the session cache: the offline directory.
 *
 * The signed-in identity is the one user the directory can always return: it is written to
 * SessionCache on sign-in (SessionCoordinator caches UserIdentity), and a UserID is a
 * distinct value type, so matching against it is exact rather than a fragile string compare
 * against a name two people could share. With a backend bound, [ServerTeamRepository] wraps
 * this repository and answers from the server's directory; this half remains the always-there
 * identity lookup and the offline fallback.
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
     * The offline candidate list: the signed-in user, filtered by [query].
     *
     * A fresh demo install has exactly one person the app can honestly name, so that is the
     * list the search box filters. Once [ServerTeamRepository] is bound the backend directory
     * replaces this for search and resolution; this method stays as its unreachable-server
     * fallback rather than inventing colleagues that are not accounts.
     */
    override suspend fun searchMembers(query: String): List<User> {
        val session = sessionCache?.current() ?: return emptyList()
        val sessionUser = fromSession(session, UserID(session.userID))
        return searchDirectory(listOf(sessionUser), query)
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
