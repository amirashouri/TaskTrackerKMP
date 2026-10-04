package com.tasktracker.database

/**
 * The app's on-disk store location.
 *
 * An `expect` because there is no common answer: iOS and the JVM have an
 * application-support directory, Android has an app-private files directory reachable
 * only through a `Context`, and the original used `URL.applicationSupportDirectory` on
 * whichever platform it was running on.
 *
 * [host] exists for the same reason `:packages:keychain` and `:packages:user-defaults`
 * take one: common code cannot see `android.content.Context`. Android's implementation
 * needs it and says so if it is missing; the others ignore it.
 */
expect fun applicationSupportDirectory(host: Any? = null): String

/**
 * Creates the directory that will hold [path], failing if it cannot.
 *
 * SQLite will not create a missing parent directory, and the failure surfaces as a
 * confusing "unable to open database file" from deep inside the driver. Called before the
 * store is opened so an unwritable location is reported as what it is.
 */
expect fun createParentDirectory(path: String)

/**
 * Removes the store at [path] and anything SQLite left beside it.
 *
 * `internal` because nothing in the app deletes the store — the file is meant to outlive
 * the process. It exists for [SprintMigrationTest], which builds a real version-3 file on
 * disk and has to take it away afterwards: a test that leaves a store behind is a test
 * whose *next* run starts from whatever the last one left, which is how a migration test
 * quietly stops testing anything.
 *
 * The `-wal` and `-shm` sidecars are named rather than hoped for. Deleting only the `.store`
 * file would leave the write-ahead log to be replayed into the next file that reuses the
 * path, and the result would look exactly like a row that came back from nowhere.
 *
 * Not an error if the file is absent. A cleanup step that can fail is a cleanup step that
 * eventually fails and is then removed.
 */
internal expect fun deleteStoreFile(path: String)
