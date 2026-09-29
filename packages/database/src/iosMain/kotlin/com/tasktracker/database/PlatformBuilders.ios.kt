package com.tasktracker.database

import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Kotlin/Native has no reflection, so the generated implementation is instantiated
 * through the constructor Room generates for exactly this purpose — which is why
 * [TaskTrackerDatabase] is annotated `@ConstructedBy` — rather than by the reflective
 * default the JVM and Android builders use. The class is written by KSP into this source
 * set, so it is named here rather than passed in.
 */
actual fun inMemoryDatabaseBuilder(host: Any?): RoomDatabase.Builder<TaskTrackerDatabase> =
    Room.inMemoryDatabaseBuilder { TaskTrackerDatabaseConstructor.initialize() }

actual fun fileDatabaseBuilder(
    name: String,
    host: Any?,
): RoomDatabase.Builder<TaskTrackerDatabase> =
    Room.databaseBuilder(name) { TaskTrackerDatabaseConstructor.initialize() }
