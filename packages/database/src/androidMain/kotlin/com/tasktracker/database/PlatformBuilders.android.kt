package com.tasktracker.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.tasktracker.core.AppError

/**
 * Android is the one platform whose builder needs a `Context` — the classic
 * `Room.databaseBuilder(context, klass, name)` shape, kept for compatibility with the rest
 * of the AndroidX database stack.
 *
 * A missing context is reported here, by name, because a store that cannot be opened is
 * otherwise indistinguishable from a store that is broken, and the caller would go looking
 * in the database rather than at its own wiring.
 */
actual fun inMemoryDatabaseBuilder(host: Any?): RoomDatabase.Builder<TaskTrackerDatabase> =
    Room.inMemoryDatabaseBuilder<TaskTrackerDatabase>(context(host))

actual fun fileDatabaseBuilder(
    name: String,
    host: Any?,
): RoomDatabase.Builder<TaskTrackerDatabase> =
    Room.databaseBuilder<TaskTrackerDatabase>(context(host), name)

private fun context(host: Any?): android.content.Context = host as? android.content.Context
    ?: throw AppError.Storage(
        "Android needs an application Context to open the database. " +
            "Pass one to DatabaseConfiguration.applicationDefault(context).",
    )
