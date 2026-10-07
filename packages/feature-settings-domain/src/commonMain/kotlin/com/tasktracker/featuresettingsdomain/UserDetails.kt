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
    }
}

/**
 * The user's presence state, as shown in Settings.
 */

@Serializable
enum class UserStatus(val displayName: String) {
    /** Reading, available for work. */
    Active("Active"),
    /** Not here right now. */
    Away("Away"),
    /** Temporarily unavailable, will return soon. */
    BeRightBack("Be Right Back"),
    /** Unable to take on work right now. */
    Busy("Busy");

    companion object {
        /** Every case, in display order. */
        val all: List<UserStatus> = listOf(Active, Away, BeRightBack, Busy)

        /** Parses a stored name, falling back to [Active] for `null` or unknown values. */
        fun fromWireValue(value: String?): UserStatus =
            all.firstOrNull { it.name == value } ?: Active
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
