package com.tasktracker.featureprojectsdata

import com.tasktracker.core.ProjectID
import kotlinx.serialization.Serializable

/**
 * The wire payload of an [OutboxOperation.Invite] row.
 *
 * Deliberately not the `Project` entity: an invitation carries the project's id and the
 * address to email, and nothing else. Handing the server the whole project would make an
 * invite a partial upsert in disguise — the server would have to know which fields to
 * ignore — and it would put every future column of [com.tasktracker.domain.Project] on
 * the wire for an operation that only ever needed two values.
 */
@Serializable
data class ProjectInvite(
    val projectID: ProjectID,
    val email: String,
)
