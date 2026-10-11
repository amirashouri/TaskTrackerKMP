package com.tasktracker.featureteamsdomain

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.UserID
import com.tasktracker.core.randomUserID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UserTests {

    private val fixedNow: Instant = Instant.fromEpochSeconds(1_600_000_000)

    @Test
    fun preferredGreetingUsesDisplayNameWhenPresent() {
        val user = aUser(displayName = "Mina")

        assertEquals("Mina", user.preferredGreeting)
    }

    @Test
    fun preferredGreetingFallsBackToEmailPrefix() {
        val user = aUser(displayName = null, email = "amir@example.com")

        assertEquals("amir", user.preferredGreeting)
    }

    @Test
    fun preferredGreetingReturnsWholeEmailWhenThereIsNoAt() {
        // `substringBefore` returns the whole string when the delimiter is absent, so a cached
        // profile with a malformed address degrades to the address rather than an empty greeting.
        val user = aUser(displayName = null, email = "amir")

        assertEquals("amir", user.preferredGreeting)
    }

    @Test
    fun aUserRoundTripsThroughJson() {
        val original = aUser()

        val restored = JSONCoding.json.decodeFromString(
            User.serializer(),
            JSONCoding.json.encodeToString(User.serializer(), original),
        )

        assertEquals(original, restored)
    }

    private fun aUser(
        id: UserID = randomUserID(),
        email: String = "amir@example.com",
        displayName: String? = "Mina",
        avatarId: String? = "avatar-1",
        position: String? = "Lead",
        status: UserStatus = UserStatus.Active,
        lastSeen: Instant? = fixedNow,
    ) = User(
        id = id,
        email = email,
        displayName = displayName,
        avatarId = avatarId,
        position = position,
        status = status,
        lastSeen = lastSeen,
    )
}