package com.tasktracker.database

/**
 * Where and how the store is persisted.
 *
 * A top-level type rather than a nested class on [TaskTrackerDatabase], because callers
 * name it on its own — a test that only wants to describe a location should not have to
 * reach through the database it is describing.
 *
 * [name] is ignored when [inMemory] is true, exactly as the original ignored its `url` for
 * an in-memory store. The original's `cloudKitEnabled` flag has no equivalent here and is
 * gone: it defaulted to off, and Room has no CloudKit mirroring to turn off. Why it was
 * off is preserved in [TaskTrackerDatabase] — mirroring would fight the local-first
 * outbox, which is the source of truth for unsynced writes.
 */
data class DatabaseConfiguration(
    val name: String,
    val inMemory: Boolean = false,
    /**
     * Whatever the platform needs to build a store: a `Context` on Android, nothing on the
     * JVM or iOS. Typed as `Any?` because common code cannot see `android.content.Context`,
     * and the platform implementation is the one that says so when it is missing.
     */
    val host: Any? = null,
) {
    companion object {
        /**
         * The app's on-disk store, under the platform's application-support directory.
         *
         * Assembled from [applicationSupportDirectory] rather than hard-coded, because that
         * directory does not exist under the same name — or with the same permissions — on
         * all three targets. `Application Support` is a macOS/iOS convention; on Android
         * the equivalent is app-private storage, which is the only location an app may
         * write to without a permission prompt.
         */
        fun applicationDefault(host: Any? = null): DatabaseConfiguration =
            DatabaseConfiguration(
                name = applicationSupportDirectory(host) + "/TaskTracker/TaskTracker.store",
                host = host,
            )

        /**
         * No disk at all.
         *
         * Carries an empty name rather than a throwaway path, so there is nothing that
         * could be pointed at a real file. See [TaskTrackerDatabase.open] for why that
         * matters more than it looks.
         */
        val inMemory: DatabaseConfiguration = DatabaseConfiguration(name = "", inMemory = true)
    }
}
