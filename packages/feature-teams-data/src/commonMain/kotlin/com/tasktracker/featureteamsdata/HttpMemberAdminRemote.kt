package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.core.parseUserID
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.network.Endpoint
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import com.tasktracker.network.jsonBody
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/** The one collection path every admin member call is built from. */
private const val ADMIN_USERS: String = "/v1/admin/users"

/**
 * The wire shapes the admin member API speaks.
 *
 * Private to the data layer, and `@SerialName`-annotated on every snake_case key: a Go
 * `encoding/json` tag and a Kotlin property name agree only by accident, and `JSONCoding`
 * leaves `ignoreUnknownKeys` off, so a key this file does not name is not skipped — it is a
 * decoding failure. That strictness is the reason `created_at` is declared here at all.
 */
@Serializable
private data class CreateMemberRequestBody(
    val email: String,
    val password: String,
    /**
     * Omitted when absent rather than sent as `null`, so an empty position reaches the server
     * the way its own `omitempty` expects — as no key, not as a null the handler has to read
     * as absence. `EncodeDefault.NEVER` is needed because `JSONCoding` sets
     * `encodeDefaults = true` for the rest of the app.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val position: String? = null,
    val role: String,
)

/**
 * A status write. The server accepts `active` or `inactive` on this endpoint, and the only
 * caller here — [HttpMemberAdminRemote.reactivateMember] — sends `active`: revoking access is
 * `DELETE`, a different endpoint because it also revokes the member's sessions.
 */
@Serializable
private data class MemberStatusRequestBody(
    val status: String,
)

/**
 * The server's `userResponse`, translated field by field.
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
 * A user is never synced, and this admin view is its only wire shape, so the server sends
 * `last_seen`/`created_at` as plain epoch millis (`entities/models.go`). That is why these are
 * `Long`s converted with [Instant.fromEpochMilliseconds] rather than `Instant` properties
 * carrying `InstantWireSerializer` — the latter would be the sync format and would fail to
 * parse a number.
 */
@Serializable
private data class MemberResponse(
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
private fun MemberResponse.toUser(): User = User(
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

/**
 * `GET /v1/admin/users?status=…`.
 *
 * Only the two buckets the server serves. The caller is expected to have already refused
 * `inactive`; see [HttpMemberAdminRemote.members].
 */
private class ListMembersEndpoint(private val bucket: String) : Endpoint {
    override val path: String = ADMIN_USERS
    override val method: HttpMethod = HttpMethod.Get
    override val queryItems: List<HttpQueryItem> = listOf(HttpQueryItem(name = "status", value = bucket))
    override val requiresAuth: Boolean = true
}

/**
 * `POST /v1/admin/users`.
 *
 * Left at the verb's default of *not* replayable, deliberately. A create that lost its response
 * and was retried either registers a second account or is refused because the email is already
 * taken — both worse than reporting a failure the admin resolves by looking at the list they
 * already have.
 */
private class CreateMemberEndpoint(private val payload: CreateMemberRequestBody) : Endpoint {
    override val path: String = ADMIN_USERS
    override val method: HttpMethod = HttpMethod.Post
    override val body: ByteArray? = jsonBody(payload)
    override val requiresAuth: Boolean = true
}

/**
 * `DELETE /v1/admin/users/{id}`.
 *
 * Replayable at the verb's default because it is a soft delete: the server marks the row
 * `deleted` and answers `204` whether it was live or already removed, so a repeated call is a
 * no-op rather than a second state change.
 */
private class DeleteMemberEndpoint(id: UserID) : Endpoint {
    override val path: String = "$ADMIN_USERS/${id.stringValue}"
    override val method: HttpMethod = HttpMethod.Delete
    override val requiresAuth: Boolean = true
}

/**
 * `PATCH /v1/admin/users/{id}` with `{"status":"active"}`, the reactivation the API offers.
 *
 * Opted into replay explicitly, unlike its verb's default: setting the status to `active` twice
 * leaves the same row standing, so the only thing the default would buy is a dropped connection
 * read as a failed reactivation — an admin told a member could not be restored when they in fact
 * already were.
 */
private class ReactivateMemberEndpoint(
    id: UserID,
    private val payload: MemberStatusRequestBody,
) : Endpoint {
    override val path: String = "$ADMIN_USERS/${id.stringValue}"
    override val method: HttpMethod = HttpMethod.Patch
    override val body: ByteArray? = jsonBody(payload)
    override val requiresAuth: Boolean = true
    override val isIdempotent: Boolean? = true
}

/**
 * The admin member API, over the authenticated client.
 *
 * The transport half of `MemberAdminPort`: this class knows the paths, the verbs and the JSON,
 * and nothing about the local store. `MemberAdminRepository` composes it with the session-backed
 * `TeamPort` to satisfy the port's three inherited reads, which are not this class's to answer.
 *
 * Every call sets `requiresAuth = true`, which is what routes it through `AuthInterceptor`: an
 * admin endpoint without a bearer token answers `401` and the interceptor refreshes once before
 * giving up, exactly as the sync transport relies on.
 *
 * @param client the **authenticated** client — the one built with `AuthInterceptor`. The plain
 *   client `HttpAuthRemote` is handed would send these with no token at all.
 */
class HttpMemberAdminRemote(private val client: HttpClient) {

    /**
     * The members in one bucket.
     *
     * [UserStatus.Inactive] is refused here rather than forwarded. The server's list endpoint
     * answers `400` for it — `active` is the whole non-deleted bucket and `inactive` is a state
     * one row carries — and mapping it to the active bucket instead would return a plausible
     * wrong answer to a caller who asked for a specific set. Throwing keeps the port's contract
     * ("the transport must not forward it") a fact rather than a comment.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun members(status: UserStatus): List<User> {
        val bucket = when (status) {
            UserStatus.Active -> "active"
            UserStatus.Deleted -> "deleted"
            UserStatus.Inactive -> throw AppError.Validation(
                "The member API lists the active or the deleted bucket; \"inactive\" is a " +
                    "state a row carries, not a bucket it can be listed under.",
            )
        }
        return client.send(
            ListMembersEndpoint(bucket).request,
            decoding = ListSerializer(MemberResponse.serializer()),
        ).map { it.toUser() }
    }

    @Throws(AppError::class, CancellationException::class)
    suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): User = client.send(
        CreateMemberEndpoint(
            CreateMemberRequestBody(
                email = email,
                password = password,
                position = position,
                role = role.wireValue,
            ),
        ).request,
        decoding = MemberResponse.serializer(),
    ).toUser()

    /** The server answers `204` with no body; the raw send discards the empty reply. */
    @Throws(AppError::class, CancellationException::class)
    suspend fun softDeleteMember(id: UserID) {
        client.send(DeleteMemberEndpoint(id).request)
    }

    @Throws(AppError::class, CancellationException::class)
    suspend fun reactivateMember(id: UserID): User = client.send(
        ReactivateMemberEndpoint(
            id = id,
            payload = MemberStatusRequestBody(status = UserStatus.Active.wireValue),
        ).request,
        decoding = MemberResponse.serializer(),
    ).toUser()
}
