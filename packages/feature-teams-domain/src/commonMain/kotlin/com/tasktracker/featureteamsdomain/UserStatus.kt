package com.tasktracker.featureteamsdomain

import kotlinx.serialization.Serializable

/**
 * Where a directory entry sits in the member directory.
 *
 * `Active` is the default for a freshly-seeded identity: the session cache only carries an
 * account that has signed in here. A member-list payload (when one exists) is what promotes
 * and demotes between these values.
 *
 * ## Why `Deleted` is a status and not a missing row
 *
 * The server never removes a member row. A removed account keeps its row — `sync_log` and
 * `project_members` still reference it, and the email must not be free to register again —
 * and is marked `deleted` instead, which is the one value that refuses its next sign-in and
 * refresh. Modelling deletion as an absent row here would put the client's picture out of
 * step with a server that still holds and counts the account. See `entities.StatusDeleted`.
 *
 * ## Why [wireValue] is a separate mapping from the serialized name
 *
 * This enum is `@Serializable` and a persisted [User] row stores it under its Kotlin name
 * (`"Active"`, `"Inactive"`), which is existing on-device data that must keep decoding. The
 * directory API spells the same states in lower case (`"active"`), so the HTTP spelling is a
 * method beside the enum rather than a `@SerialName` on each constant: the two spellings
 * answer to two different readers — the database and the wire — and must be free to differ.
 */
@Serializable
enum class UserStatus(val wireValue: String) {
    Active("active"),
    Inactive("inactive"),
    Deleted("deleted");

    companion object {
        /**
         * Maps the directory API's `status` field.
         *
         * Total, and deliberately so: an unrecognised value — a status a future server adds,
         * a misspelling, an absent field — resolves to [Active] rather than throwing. The
         * member list must render every row the server sends, and the safe reading of a
         * status this build cannot name is "not deleted": a row wrongly shown as active only
         * mislabels an account the server still refuses to sign in, whereas one wrongly shown
         * as deleted hides a live account behind a bucket the admin did not choose.
         */
        fun fromWireValue(value: String?): UserStatus =
            entries.firstOrNull { it.wireValue == value } ?: Active
    }
}
