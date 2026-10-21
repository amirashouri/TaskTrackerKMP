package com.tasktracker.featuresettingsdomain

import kotlinx.serialization.Serializable

/**
 * The user's role/position in the team.
 */
@Serializable
enum class UserPosition(val wireValue: String, val displayName: String) {
    Developer("developer", "Developer"),
    Designer("designer", "Designer"),
    ProductOwner("product_owner", "Product owner"),
    DataEngineer("data_engineer", "Data engineer"),
    Manager("manager", "Manager");

    companion object {
        val all: List<UserPosition> = listOf(Developer, Designer, ProductOwner, DataEngineer, Manager)

        fun fromWireValue(value: String): UserPosition? = all.firstOrNull { it.wireValue == value }

        /**
         * Parses the label stored in the server's free-text `position` column.
         *
         * The admin form writes a human label ("Product owner"), not the enum's internal
         * `wireValue`, so the label is what round-trips. Free text an admin typed by hand
         * ("Senior Designer") matches nothing and returns `null`; the caller must treat that as
         * "not representable here", not as "clear it".
         */
        fun fromLabel(value: String?): UserPosition? {
            val label = value?.trim().orEmpty()
            if (label.isEmpty()) return null
            return all.firstOrNull { it.displayName.equals(label, ignoreCase = true) }
                ?: fromWireValue(label.lowercase())
        }
    }
}

/**
 * The user's presence state, as shown in Settings.
 */

@Serializable
enum class UserStatus(val displayName: String, val wireValue: String) {
    /** Reading, available for work. */
    Active("Active", "active"),
    /** Not here right now. */
    Away("Away", "away"),
    /** Temporarily unavailable, will return soon. */
    BeRightBack("Be Right Back", "be_right_back"),
    /** Unable to take on work right now. */
    Busy("Busy", "busy");

    companion object {
        /** Every case, in display order. */
        val all: List<UserStatus> = listOf(Active, Away, BeRightBack, Busy)

        /**
         * Parses the **persisted enum name** (`"Active"`, `"BeRightBack"`) stored in
         * `DefaultsKey.UserProfile`, falling back to [Active] for `null` or unknown values.
         *
         * Not the server's wire value: use [fromPresence] for that. The two vocabularies differ
         * (`Active` vs `active`, `BeRightBack` vs `be_right_back`), and changing this parser to
         * the wire spelling would invalidate every profile already saved on a device.
         */
        fun fromWireValue(value: String?): UserStatus =
            all.firstOrNull { it.name == value } ?: Active

        /** Parses the server's `presence` value, falling back to [Active]. */
        fun fromPresence(value: String?): UserStatus =
            all.firstOrNull { it.wireValue == value?.trim() } ?: Active
    }
}

/**
 * Basic user details shown in Settings.
 *
 * ## Email is carried here, not fetched from the session
 *
 * The signed-in identity owns the canonical email, but the settings screen is the one place
 * that shows it when there is no display name — and it must keep showing it after sign-out and
 * back in with a different account, because the screen is a property of the device, not of the
 * session. So it is stored here and seeded from the identity on sign-in, never read live from
 * the session.
 */
@Serializable
data class UserDetails(
    val fullName: String = "",
    val position: UserPosition? = null,
    val avatarId: Int? = null,
    val email: String = "",
    /**
     * The user's current presence state. `null` means "no explicit choice" and falls back to
     * [UserStatus.Active] at runtime.
     */
    val status: UserStatus? = null,
    /**
     * Free text explaining the current status — why the user is away, for example
     * "On vacation" or "Sick".
     */
    val statusText: String = "",
)
