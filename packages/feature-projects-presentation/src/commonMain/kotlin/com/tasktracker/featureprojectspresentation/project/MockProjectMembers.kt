package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.UserID

/** A directory entry returned by the current local stand-in for the future members API. */
data class ProjectMember(
    val id: UserID,
    val name: String,
    val email: String,
)

/** Mock directory isolated behind the same query shape a backend search can later replace. */
object MockProjectMembers {
    val members: List<ProjectMember> = listOf(
        ProjectMember(UserID("10000000-0000-4000-8000-000000000001"), "Ada Lovelace", "ada@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000002"), "Alan Turing", "alan@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000003"), "Grace Hopper", "grace@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000004"), "Katherine Johnson", "katherine@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000005"), "Margaret Hamilton", "margaret@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000006"), "Donald Knuth", "donald@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000007"), "Barbara Liskov", "barbara@example.com"),
        ProjectMember(UserID("10000000-0000-4000-8000-000000000008"), "Edsger Dijkstra", "edsger@example.com"),
    )

    fun search(query: String): List<ProjectMember> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return members.filter {
            it.name.contains(term, ignoreCase = true) || it.email.contains(term, ignoreCase = true)
        }
    }
}
