package com.tasktracker.featuresettingsdomain

import com.tasktracker.core.AppError
import kotlin.coroutines.cancellation.CancellationException

/**
 * The signed-in user's own profile on the server.
 *
 * A gateway rather than a port, because nothing above the data layer consumes it: the screen
 * talks to [SettingsPort], the service owns the read-through and the write-through, and this is
 * the seam where the service meets the wire. Keeping it separate is what lets
 * `LiveSettingsService` compute a delta and restore a confirmed snapshot without the HTTP
 * details leaking into the feature's domain.
 *
 * [load] and [update] are both about the *caller's* row. Neither takes a user id, and that is
 * the contract rather than an omission: `GET`/`PATCH /v1/me` resolve the account from the
 * access token, so a member cannot reach another member's profile through this interface.
 * [changePassword] follows the same rule on its own route.
 */
interface UserProfileGateway {

    /**
     * Reads the caller's profile.
     *
     * @throws AppError when the server cannot be reached or answers with something else.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun load(): UserDetails

    /**
     * Writes the fields in [changes] and returns the server's confirmed profile.
     *
     * The server applies only the keys present in the body, so an unchanged field is not sent
     * at all — a presence note cannot erase a position an admin set, and an avatar pick cannot
     * reset a display name.
     *
     * @throws AppError when the server rejects or cannot receive the write.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun update(changes: UserProfileChanges): UserDetails

    /**
     * Changes the caller's password after verifying identity with [oldPasswordOrCode]: either
     * their current password or a time-limited, single-use code an admin generated for them.
     *
     * The server resolves the account from the access token — the same rule [load] and [update]
     * follow — and consumes a code that verifies, so one code authorises exactly one change.
     * A refusal arrives as [AppError.Validation] carrying the server's own sentence (wrong
     * credential, spent or expired code, password the policy refuses), which the screen may
     * show verbatim: `400` is one of the statuses whose wording is part of the API contract.
     *
     * @throws AppError.Validation when the server refuses the credential or the new password.
     * @throws AppError when the server cannot be reached.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun changePassword(newPassword: String, oldPasswordOrCode: String)
}

/**
 * The subset of [UserDetails] a commit may write.
 *
 * Every field is nullable, and `null` means "this edit did not touch it". That distinction is
 * the whole reason the type exists: `UserDetails` describes what the screen shows, where an
 * empty `statusText` is a real value to display, while a change set describes what the user
 * *did*, where an untouched note and a note cleared to empty are different requests.
 *
 * `email` is deliberately absent. It is read-only in Settings, and leaving it out of the type
 * makes it impossible to send one by accident.
 */
data class UserProfileChanges(
    val fullName: String? = null,
    val position: UserPosition? = null,
    val avatarId: Int? = null,
    val status: UserStatus? = null,
    val statusText: String? = null,
) {
    /** Whether this change set asks for no write at all. */
    val isEmpty: Boolean
        get() = fullName == null &&
            position == null &&
            avatarId == null &&
            status == null &&
            statusText == null
}