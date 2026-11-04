package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.core.parseUserID
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server's `userResponse`, translated field by field.
 *
 * Shared by the admin member API and the collaborator directory: both read the same
 * payload, and a second copy would be a second place for a `@SerialName` to drift.
 *
 * ## `createdAt` is declared and then dropped
 *
 * The server puts `createdAt` on every user payload. With `ignoreUnknownKeys` off, leaving it
 * out of this type would fail every member list rather than skip the field — so it is declared
 * to be decodable and then not carried into the domain `User`, which has no such property.
 * `HttpAuthRemote` does the same with the `userId`/`email`/`displayName` trio in its token
 * envelope.
 *
 * ## The timestamps are ISO-8601 UTC, like the sync envelope
 *
 * A user is never synced, but the project unified its wire format: `lastSeen`/`createdAt` are
 * whole-second ISO-8601 strings, identical to the sync entities. They are `String`s parsed with
 * [Instant.parse] rather than `Long`s, so a number on the wire would be a decode failure — which
 * is what `ignoreUnknownKeys` off is meant to catch.
 */
@Serializable
internal data class MemberResponse(
    val id: String,
    val email: String,
    @SerialName("displayName") val displayName: String? = null,
    val position: String? = null,
    @SerialName("avatarId") val avatarId: String? = null,
    val status: String? = null,
    val role: String? = null,
    @SerialName("lastSeen") val lastSeen: String? = null,
    @SerialName("createdAt") val createdAt: String,
)

/**
 * The domain row this payload describes.
 *
 * `status` and `role` go through the domain's own `fromWire`/`fromWireValue` so an unknown
 * value fails closed to the same default it would anywhere else in the app, and an absent one
 * (both are `omitempty` server-side) reads as its default rather than as a parse error.
 */
internal fun MemberResponse.toUser(): User = User(
    id = memberID(),
    email = email,
    displayName = displayName,
    avatarId = avatarId,
    position = position,
    status = UserStatus.fromWireValue(status),
    role = MemberRole.fromWire(role),
    lastSeen = lastSeen?.let { Instant.parse(it) },
)

/**
 * An id the directory cannot parse is a decoding failure, not a `null` user: the row exists and
 * the server named it, so dropping it silently would make the member list quietly incomplete.
 */
private fun MemberResponse.memberID(): UserID = parseUserID(id)
    ?: throw AppError.Decoding("Member id \"$id\" is not a UUID.")
