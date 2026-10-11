package com.tasktracker.featureteamsdata

import com.tasktracker.core.UserID
import com.tasktracker.core.randomUserID
import com.tasktracker.database.CachedSession
import com.tasktracker.database.SessionCache
import kotlin.time.Instant

internal class StubSessionCache(
    private var session: CachedSession? = null,
) : SessionCache {

    override suspend fun current(): CachedSession? = session

    override suspend fun save(session: CachedSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }

    /** Signs in a user and returns the id, so a test can assert on it. */
    fun signIn(
        userID: UserID = randomUserID(),
        email: String = "amir@example.com",
        displayName: String? = "Amir",
    ): UserID {
        session = CachedSession(
            userID = userID.stringValue,
            email = email,
            displayName = displayName,
            lastSignedInAt = Moments.start,
            serverVersion = 1,
        )
        return userID
    }
}

internal object Moments {
    val start: Instant = Instant.fromEpochSeconds(1_760_000_000)
}
