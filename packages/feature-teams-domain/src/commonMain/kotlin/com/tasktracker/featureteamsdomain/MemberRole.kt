package com.tasktracker.featureteamsdomain

import kotlinx.serialization.Serializable

/**
 * A member's account role, as the member directory carries it.
 *
 * ## Why this duplicates `UserRole`
 *
 * The auth feature already names this vocabulary — `featureauthdomain.UserRole` — and the two
 * describe the same server claim. `feature-teams-domain` may not depend on
 * `feature-auth-domain`: the architecture graph forbids the edge, and [User]'s own KDoc
 * records the precedent of restating a small rule here rather than reaching across a boundary
 * two modules cannot see through. The enum is therefore stated beside the payload that carries
 * it, on [User.role].
 *
 * ## Why an unrecognised value is [Member]
 *
 * [fromWire] fails to the lowest privilege for the same reason its auth twin does: the value
 * decides what the member screen offers to manage, and a role this build cannot understand
 * must not read as the one that may administer.
 */
@Serializable
enum class MemberRole(val wireValue: String) {
    Admin("admin"),
    Member("member");

    companion object {
        /** Maps the directory API's `role` field; anything unrecognised (or absent) is [Member]. */
        fun fromWire(value: String?): MemberRole =
            entries.firstOrNull { it.wireValue == value } ?: Member
    }
}
