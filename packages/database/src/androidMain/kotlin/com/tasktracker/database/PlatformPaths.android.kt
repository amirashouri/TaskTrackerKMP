package com.tasktracker.database

import com.tasktracker.core.AppError
import java.io.File

/**
 * App-private files storage, which is the Android equivalent of application support:
 * the only location an app may write to with no permission, and removed on uninstall,
 * which is the behaviour `Application Support` has on Apple platforms.
 *
 * Needs a `Context` because nothing in common code can see one, so [host] must be the
 * application's context. The error says exactly that rather than falling back to a path
 * that would fail later with something less actionable.
 */
actual fun applicationSupportDirectory(host: Any?): String {
    val context = host as? android.content.Context
        ?: throw AppError.Storage(
            "Android needs an application Context to locate the database. " +
                "Call TaskTrackerDatabase.Configuration.applicationDefault(context).",
        )
    return context.filesDir.absolutePath
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
