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
