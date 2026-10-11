package com.tasktracker.featureteamsdata

import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus

/**
 * The member list [RoomTeamRepository.searchMembers] answers from, until the backend's
 * member API replaces it.
 *
 * FAKE BY DESIGN, AND MARKED AS SUCH: these six people are not accounts and no session will
 * ever match one of their ids. The point is that the picker's search box filters a real
 * `List<User>` through a real port method rather than a mock living inside the UI — when
 * the backend lands, only this file's source changes and every caller stays put.
 *
 * The ids are fixed UUIDs rather than random ones, so a test that asserts "Ada is in the
 * results" does not have to re-derive them, and two hosts answering the same query produce
 * the same list.
 *
 * First and last names are carried as one display name rather than two fields because
 * [User] already has the field every screen renders; when the backend arrives its
 * firstName/lastName map onto it here rather than rippling a two-field split through every
 * call site.
 */
internal object FakeMemberDirectory {

    val people: List<User> = listOf(
        fakeUser("0a000000-0000-4000-8000-000000000001", "Ada Lovelace", "ada@example.com"),
        fakeUser("0a000000-0000-4000-8000-000000000002", "Grace Hopper", "grace@example.com"),
        fakeUser("0a000000-0000-4000-8000-000000000003", "Alan Turing", "alan@example.com"),
        fakeUser("0a000000-0000-4000-8000-000000000004", "Katherine Johnson", "katherine@example.com"),
        fakeUser("0a000000-0000-4000-8000-000000000005", "Barbara Liskov", "barbara@example.com"),
        fakeUser("0a000000-0000-4000-8000-000000000006", "Radia Perlman", "radia@example.com"),
    )

    /**
     * [candidates] (the session user plus [people]) filtered by [query] and sorted by name,
     * so the dropdown is stable across hosts and across keystrokes.
     */
    fun search(candidates: List<User>, query: String): List<User> {
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

    private fun fakeUser(id: String, name: String, email: String): User = User(
        id = com.tasktracker.core.UserID(id),
        email = email,
        displayName = name,
        avatarId = null,
        position = null,
        status = UserStatus.Active,
        lastSeen = null,
    )
}
