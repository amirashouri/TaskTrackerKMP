package com.tasktracker.featureteamsdomain

import kotlinx.serialization.Serializable

/**
 * Where a directory entry sits, in the absence of a server member list.
 *
 * `Active` is the default for a freshly-seeded identity: the session cache only carries an
 * account that has signed in here. A member-list payload (when one exists) is what promotes
 * and demotes between these two.
 */
@Serializable
enum class UserStatus {
    Active,
    Inactive,
}
