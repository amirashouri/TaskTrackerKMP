package com.tasktracker.database

import androidx.room.Room
import androidx.room.RoomDatabase

// The host is ignored: the JVM builder has no Context parameter, and the same reflection
// default works here as on Android.
actual fun inMemoryDatabaseBuilder(host: Any?): RoomDatabase.Builder<TaskTrackerDatabase> =
    Room.inMemoryDatabaseBuilder<TaskTrackerDatabase>()

actual fun fileDatabaseBuilder(
    name: String,
    host: Any?,
): RoomDatabase.Builder<TaskTrackerDatabase> =
    Room.databaseBuilder<TaskTrackerDatabase>(name)
