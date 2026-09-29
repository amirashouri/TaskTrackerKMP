package com.tasktracker.database

import com.tasktracker.core.AppError
import java.io.File

/**
 * The same directory the original read from `URL.applicationSupportDirectory`, which is
 * `$HOME/Library/Application Support` on macOS and `%APPDATA%` elsewhere — the
 * Foundation mapping, reimplemented because `java.io` has no equivalent of it.
 */
actual fun applicationSupportDirectory(host: Any?): String {
    val home = System.getProperty("user.home")
        ?: throw AppError.Storage("Could not locate a home directory for the database.")
    val appData = System.getenv("APPDATA")
    return if (appData != null) "$appData/TaskTracker" else "$home/Library/Application Support"
}

actual fun createParentDirectory(path: String) {
    val parent = File(path).parentFile ?: return
    // `mkdirs` returns false for a directory that already exists, so the check has to be
    // "does it exist now", not "did I just make it". A plain file sitting where a
    // directory has to be is the case the original's test used, and it must fail here
    // rather than surface as an opaque SQLite error later.
    if (parent.isDirectory) return
    if (!parent.mkdirs() && !parent.isDirectory) {
        throw AppError.Storage("Could not create the database directory: ${parent.path}")
    }
}
