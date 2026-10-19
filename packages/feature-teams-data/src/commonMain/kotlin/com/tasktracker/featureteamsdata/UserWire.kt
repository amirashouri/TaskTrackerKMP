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
 * ## `created_at` is declared and then dropped
 *
 * The server puts `created_at` on every user payload. With `ignoreUnknownKeys` off, leaving it
 * out of this type would fail every member list rather than skip the field — so it is declared
 * to be decodable and then not carried into the domain `User`, which has no such property.
 * `HttpAuthRemote` does the same with the `user_id`/`email`/`display_name` trio in its token
 * envelope.
 *
 * ## The timestamps are epoch millis, not the sync envelope's ISO-8601
 *
 * A user is never synced, and this is its only wire shape, so the server sends
 * `last_seen`/`created_at` as plain epoch millis (`entities/models.go`). That is why these are
 * `Long`s converted with [Instant.fromEpochMilliseconds] rather than `Instant` properties
 * carrying `InstantWireSerializer` — the latter would be the sync format and would fail to
 * parse a number.
 */
@Serializable
internal data class MemberResponse(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String? = null,
    val position: String? = null,
    @SerialName("avatar_id") val avatarId: String? = null,
    val status: String? = null,
    val role: String? = null,
    @SerialName("last_seen") val lastSeen: Long? = null,
    @SerialName("created_at") val createdAt: Long,
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
    lastSeen = lastSeen?.let(Instant::fromEpochMilliseconds),
)

/**
 * An id the directory cannot parse is a decoding failure, not a `null` user: the row exists and
 * the server named it, so dropping it silently would make the member list quietly incomplete.
 */
private fun MemberResponse.memberID(): UserID = parseUserID(id)
    ?: throw AppError.Decoding("Member id \"$id\" is not a UUID.")
