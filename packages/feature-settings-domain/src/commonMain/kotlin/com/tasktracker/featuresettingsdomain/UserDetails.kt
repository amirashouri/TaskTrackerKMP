package com.tasktracker.featuresettingsdomain

/**
 * The user's role/position in the team.
 */
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
 * Basic user details shown in Settings.
 */
data class UserDetails(
    val fullName: String = "",
    val position: UserPosition? = null,
    val avatarId: Int? = null,
)
