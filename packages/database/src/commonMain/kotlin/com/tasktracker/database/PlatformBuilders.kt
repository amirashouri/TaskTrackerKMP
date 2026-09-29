package com.tasktracker.database

import androidx.room.RoomDatabase

/**
 * Room's builder entry points are declared per platform rather than in `commonMain`, so
 * opening a store is an `expect` like every other thing that has no common answer.
 *
 * Two things differ per platform. The *factory*: on the JVM and Android Room instantiates
 * the generated implementation by reflection, and on Kotlin/Native there is no reflection
 * to do it with, so each platform supplies the constructor itself. And the *host*: Android's
 * builder is the classic one and requires a `Context`, while the other two have no such
 * parameter — so the host is threaded through as `Any?` for the same reason
 * `:packages:keychain` and `:packages:user-defaults` do it, and only Android reads it.
 */
expect fun inMemoryDatabaseBuilder(host: Any? = null): RoomDatabase.Builder<TaskTrackerDatabase>

/**
 * A builder for a file-backed store named [name].
 *
 * Room rejects a blank name and `:memory:` outright rather than treating them as a
 * filename, which is the second half of why [TaskTrackerDatabase.Configuration.inMemory]
 * carries an empty name: there is no string that could be passed here to get the behaviour
 * the in-memory builder gives.
 */
expect fun fileDatabaseBuilder(
    name: String,
    host: Any? = null,
): RoomDatabase.Builder<TaskTrackerDatabase>
