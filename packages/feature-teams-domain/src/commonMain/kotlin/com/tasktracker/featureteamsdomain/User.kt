package com.tasktracker.featureteamsdomain

import com.tasktracker.core.UserID
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A person in the user directory.
 *
 * ## preferredGreeting duplicates the auth rule on purpose
 *
 * The screens need a name to call a directory entry, and the auth module's
 * [com.tasktracker.featureauthdomain.UserIdentity.preferredGreeting] states that rule once,
 * next to its data. This type lives in a different module — `feature-teams-domain` may not
 * depend on `feature-auth-domain` — so the same three-line `when` is written here rather than
 * the view layer rebuilding it. The original's defect, called out in UserIdentity's own KDoc,
 * was two screens each growing a slightly different version of the rule; this keeps one source
 * per type, and a type is the narrowest boundary two modules that cannot see each other can
 * share across.
 */
@Serializable
data class User(
    val id: UserID,
    val email: String,
    val displayName: String? = null,
    val avatarId: String? = null,
    val position: String? = null,
    val status: UserStatus = UserStatus.Active,
    /**
     * The account's system role, as the member list displays it.
     *
     * Defaulted so a directory payload or a cached row that predates this field still
     * decodes, as [MemberRole.Member] rather than as a failure — the same rule
     * `UserIdentity.role` follows. The signed-in account's own role is read from the access
     * token's claim, not from here; this field is about *other* rows in the list.
     */
    val role: MemberRole = MemberRole.Member,
    val lastSeen: Instant? = null,
) {

    /**
     * What to call this person.
     *
     * Mirrors [com.tasktracker.featureauthdomain.UserIdentity.preferredGreeting]: a nonblank
     * displayName wins, otherwise the email prefix, otherwise the whole address.
     * `substringBefore('@')` returns the whole string when there is no `@`, so the last arm
     * never renders an empty greeting even from a malformed cached row.
     */
    val preferredGreeting: String
        get() = when {
            !displayName.isNullOrEmpty() -> displayName
            else -> email.substringBefore('@')
        }
}
