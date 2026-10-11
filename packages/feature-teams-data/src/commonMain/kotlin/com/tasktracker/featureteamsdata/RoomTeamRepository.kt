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
