package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamsdomain.User
import kotlin.coroutines.cancellation.CancellationException

/**
 * [TeamPort] for a device with a backend: the directory is the server's, the signed-in
 * account still resolves locally.
 *
 * ## One fetch, filtered here
 *
 * The picker asks for a blank listing when it opens and then one query per typing pause. The
 * server has a search endpoint, but calling it per keystroke would make the dropdown's latency
 * the network's. Instead this repository fetches the whole live directory once, caches it, and
 * applies the same case-insensitive name-or-email filter [RoomTeamRepository] documents. A
 * blank query is the refresh point — the moment the picker opens is the moment to notice a
 * member the admin added a minute ago.
 *
 * ## Selected ids resolve through the same cache
 *
 * The member picker's pills resolve their ids through [users], not through its search seam, so
 * a selected collaborator keeps a name after the list has been refiltered. That lookup would
 * otherwise be the one path the server directory never reaches, which is exactly how a picked
 * member's pill would vanish.
 *
 * ## The session account is always resolvable
 *
 * [local] answers the signed-in identity without a request. A server that cannot answer falls
 * back to it rather than throwing: the picker has no error surface, and an unreachable server
 * must not make the form unusable. The fallback is honest — it is the one person the device
 * can actually name offline.
 */
class ServerTeamRepository(
    private val local: TeamPort,
    private val remote: HttpTeamRemote,
) : TeamPort {

    private var directory: List<User>? = null

    override suspend fun user(id: UserID): User? =
        local.user(id)
            ?: directory().firstOrNull { it.id == id }

    override suspend fun users(ids: List<UserID>): List<User> {
        if (ids.isEmpty()) return emptyList()

        val known = local.users(ids).associateBy { it.id }
        val fetched = directory().associateBy { it.id }
        return ids.mapNotNull { id -> known[id] ?: fetched[id] }
    }

    override suspend fun searchMembers(query: String): List<User> {
        // A blank query is the picker opening, so refresh; a typed query reuses the fetch
        // rather than costing a request per keystroke.
        val candidates = if (query.isBlank()) refreshedDirectory() else directoryOrNull()
        return if (candidates == null) {
            local.searchMembers(query)
        } else {
            searchDirectory(candidates, query)
        }
    }

    /**
     * The cached listing, fetched on first use.
     *
     * Deliberately does not refetch on a miss: an id the directory has never named is a
     * deleted member or a stale row, and asking the server again on every card would turn a
     * board's name resolution into a request storm. The blank-query refresh point is what
     * notices additions.
     */
    private suspend fun directory(): List<User> = directoryOrNull().orEmpty()

    private suspend fun directoryOrNull(): List<User>? = directory ?: refreshedDirectory()

    private suspend fun refreshedDirectory(): List<User>? = try {
        remote.searchMembers("").also { directory = it }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: AppError) {
        // Null rather than an empty list: "the server did not answer" and "the server
        // says there is nobody" are different, and the first should keep the cached
        // directory (or fall back to the session account).
        directory
    }
}

/**
 * The directory filter the picker's dropdown expects, shared with the session-only repository.
 *
 * Case-insensitive substring on the display name or the email, then sorted by greeting so the
 * list is stable across keystrokes and hosts.
 */
internal fun searchDirectory(candidates: List<User>, query: String): List<User> {
    val normalised = query.trim().lowercase()
    val matches = if (normalised.isEmpty()) {
        candidates
    } else {
        candidates.filter { user ->
            user.displayName.orEmpty().lowercase().contains(normalised) ||
                user.email.lowercase().contains(normalised)
        }
    }
    return matches.sortedBy { it.preferredGreeting.lowercase() }
}
