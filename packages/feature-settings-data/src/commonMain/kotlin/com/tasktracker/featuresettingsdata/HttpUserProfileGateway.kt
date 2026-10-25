package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.UserPosition
import com.tasktracker.featuresettingsdomain.UserProfileChanges
import com.tasktracker.featuresettingsdomain.UserProfileGateway
import com.tasktracker.featuresettingsdomain.UserStatus
import com.tasktracker.network.Endpoint
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.jsonBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [UserProfileGateway] over `GET` and `PATCH /v1/me`.
 *
 * The two routes take no user id: the server resolves the account from the access token, which
 * is what makes "a member may edit their own profile" a property of the route rather than a
 * permission check this client could get wrong.
 *
 * ## The PATCH body is built key by key
 *
 * The shared encoder has `encodeDefaults = true`, so a request DTO whose fields defaulted to
 * `null` would send every key as an explicit `null`. That happens to be a no-op on this server,
 * but it is not what "only what changed" means and it would turn every avatar pick into a
 * five-field write. A [kotlinx.serialization.json.JsonObject] built from non-null changes sends
 * exactly the keys the user touched.
 *
 * ## The response is strict
 *
 * `JSONCoding` leaves `ignoreUnknownKeys` off, so a key this DTO does not declare fails the
 * decode instead of being silently dropped. That is the point: the settings screen would
 * otherwise show a profile missing whatever the server had gained, with no signal that the two
 * sides disagree.
 */
class HttpUserProfileGateway(
    private val client: HttpClient,
) : UserProfileGateway {

    override suspend fun load(): UserDetails =
        client.send(GetSelfProfileEndpoint().request, UserProfileResponse.serializer())
            .toUserDetails()

    override suspend fun update(changes: UserProfileChanges): UserDetails {
        val payload = buildJsonObject {
            changes.fullName?.let { put("display_name", it) }
            // The column holds the human label the admin form writes ("Product owner"), not
            // the enum's internal `wireValue`; `fromLabel` is the inverse of this mapping.
            changes.position?.let { put("position", it.displayName) }
            changes.avatarId?.let { put("avatar_id", it.toString()) }
            changes.status?.let { put("presence", it.wireValue) }
            changes.statusText?.let { put("presence_note", it) }
        }
        val body = jsonBody(payload)
            ?: throw AppError.Validation("Could not prepare the profile update.")

        return client.send(
            PatchSelfProfileEndpoint(body).request,
            UserProfileResponse.serializer(),
        ).toUserDetails()
    }

    override suspend fun changePassword(newPassword: String, oldPasswordOrCode: String) {
        client.send(
            PostSelfPasswordEndpoint(
                ChangePasswordRequestBody(
                    oldPasswordOrCode = oldPasswordOrCode,
                    newPassword = newPassword,
                ),
            ).request,
            ChangePasswordResponse.serializer(),
        )
    }
}

private const val SELF_PROFILE_PATH = "/v1/me"

private class GetSelfProfileEndpoint : Endpoint {
    override val path: String = SELF_PROFILE_PATH
    override val method: HttpMethod = HttpMethod.Get
    override val isIdempotent: Boolean = true
}

/**
 * A PATCH whose body assigns absolute values, so replaying it lands on the same row state.
 *
 * `HttpMethod.Patch` is non-idempotent by default precisely because PATCH can mean "apply this
 * delta to whatever is there"; this one cannot, because the body carries only the fields the
 * user changed and their new values. Opting in lets the retry policy recover a dropped
 * connection instead of surfacing it as a failed save.
 */
private class PatchSelfProfileEndpoint(
    override val body: ByteArray,
) : Endpoint {
    override val path: String = SELF_PROFILE_PATH
    override val method: HttpMethod = HttpMethod.Patch
    override val isIdempotent: Boolean = true
}

/**
 * `POST /v1/me/password` — the change-password action, a route of its own rather than an
 * overload of the profile PATCH, because it is an action with its own body and a `PATCH /v1/me`
 * is already the profile write this client decodes.
 *
 * Left at the verb's default of *not* replayable, deliberately. The credential in the body is
 * consumed by success — a reset code is single-use and the old password stops matching — so a
 * response lost to a dropped connection cannot be resent safely: the replay would be refused
 * and the screen would report a failure for a change that actually landed.
 */
private class PostSelfPasswordEndpoint(
    private val payload: ChangePasswordRequestBody,
) : Endpoint {
    override val path: String = "$SELF_PROFILE_PATH/password"
    override val method: HttpMethod = HttpMethod.Post
    override val body: ByteArray? = jsonBody(payload)
}

/**
 * The change-password body, transcribed from the server's `changePasswordRequest`.
 *
 * Both keys are always present — neither may default away, because the server refuses a
 * request missing either — and the `@SerialName` list is the whole mapping: Kotlin does not
 * match JSON keys case-insensitively, so a renamed key is a rejected request rather than a
 * silently ignored field.
 */
@Serializable
private data class ChangePasswordRequestBody(
    @SerialName("old_password_or_code") val oldPasswordOrCode: String,
    @SerialName("new_password") val newPassword: String,
)

/**
 * The change-password response: the caller's own row, with the new hash.
 *
 * `password_hash` is declared for the same reason every other key is — `JSONCoding` leaves
 * `ignoreUnknownKeys` off, so a key this DTO does not name fails the decode. The value is
 * never read: the server includes it by contract (specs/t8.1-change-password.md, the one
 * endpoint whose response carries the hash) and the client mirrors the contract rather than
 * opting out of it. Defaults keep the decode total if the server ever omits one.
 */
@Serializable
private data class ChangePasswordResponse(
    @SerialName("id") val id: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("password_hash") val passwordHash: String = "",
)

/**
 * The client's transcription of the server's self-profile shape.
 *
 * Every field has a default, per the shared contract: the server may omit an empty optional
 * key (`omitempty`), and an installed client must keep decoding when it does. The `@SerialName`
 * list is the whole mapping — Kotlin does not match case-insensitively, so a renamed key is a
 * decoding failure rather than a silently ignored field.
 */
@Serializable
private data class UserProfileResponse(
    @SerialName("id") val id: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("position") val position: String = "",
    @SerialName("avatar_id") val avatarId: String = "",
    @SerialName("presence") val presence: String = "active",
    @SerialName("presence_note") val presenceNote: String = "",
)

private fun UserProfileResponse.toUserDetails(): UserDetails = UserDetails(
    fullName = displayName,
    // An admin's free text ("Senior Designer") matches no choice; `fromLabel` returns null and
    // the picker shows no selection rather than pretending the row is blank and clearing it.
    position = UserPosition.fromLabel(position),
    avatarId = avatarId.trim().toIntOrNull()?.takeIf { it in 0..9 },
    email = email,
    status = UserStatus.fromPresence(presence),
    statusText = presenceNote,
)
