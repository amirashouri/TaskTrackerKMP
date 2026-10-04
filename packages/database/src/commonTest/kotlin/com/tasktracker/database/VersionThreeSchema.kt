package com.tasktracker.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The version-3 schema, as executable SQL, in one place.
 *
 * ## Why a hand-written copy exists at all
 *
 * To test that a store written by version 3 opens correctly at version 4, something has to
 * *write* a version-3 store. Room's `MigrationTestHelper` would do it, and it is Android
 * only — and this module's tests cannot run on the Android host, because Room's Android
 * builder needs a real `Context` that a host JVM test has no way to produce
 * (`build.gradle.kts` disables `testAndroidHostTest` for exactly that reason). Using the
 * helper would put the one test that catches a destructive migration behind a platform this
 * project cannot run Room tests on.
 *
 * So the schema is transcribed here, from `schemas/…/3.json`, and executed through the same
 * bundled driver the app uses. Nothing about the migration is stubbed: the store is opened
 * by [TaskTrackerDatabase.open] and Room runs its own generated `AutoMigration(3, 4)`.
 *
 * ## All six tables and all four indices, not the two the assertions read
 *
 * Room validates the entire schema after a migration, so an omitted table or index fails
 * the open rather than quietly weakening the test. Listing them all means the literal has
 * to be right about things no assertion mentions — which is the point, because a store with
 * a missing index still *works*.
 *
 * ## The literal is a liability, so it is checked against the JSON
 *
 * Somebody adds a column in a later version and this keeps claiming the old shape, so the
 * migration test goes on passing against a version 3 that never existed. `SprintSchemaDriftTest`
 * executes this same object and compares what SQLite actually created against the committed
 * `3.json`, which is the only thing that makes the literal trustworthy.
 */
internal object VersionThreeSchema {

    /** Every statement version 3 made, with `${TABLE_NAME}` replaced by real table names. */
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `outbox` (
          `id` TEXT NOT NULL, `entityKind` TEXT NOT NULL, `entityID` TEXT NOT NULL,
          `operation` TEXT NOT NULL, `payload` BLOB NOT NULL, `userID` TEXT,
          `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL,
          `lastAttemptAt` INTEGER, `lastError` TEXT, PRIMARY KEY(`id`))
        """,
        """
        CREATE TABLE IF NOT EXISTS `sync_metadata` (
          `scope` TEXT NOT NULL, `cursor` TEXT, `lastSyncedAt` INTEGER,
          `lastPushedAt` INTEGER, `serverVersion` INTEGER NOT NULL, `lastError` TEXT,
          PRIMARY KEY(`scope`))
        """,
        """
        CREATE TABLE IF NOT EXISTS `session` (
          `userID` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT,
          `lastSignedInAt` INTEGER NOT NULL, `serverVersion` INTEGER NOT NULL,
          PRIMARY KEY(`userID`))
        """,
        """
        CREATE TABLE IF NOT EXISTS `projects` (
          `id` TEXT NOT NULL, `keyPrefix` TEXT NOT NULL, `name` TEXT NOT NULL,
          `details` TEXT NOT NULL, `memberIDs` TEXT NOT NULL DEFAULT '',
          `isArchived` INTEGER NOT NULL, `nextItemNumber` INTEGER NOT NULL,
          `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))
        """,
        """
        CREATE TABLE IF NOT EXISTS `work_items` (
          `id` TEXT NOT NULL, `projectID` TEXT NOT NULL, `key` TEXT NOT NULL,
          `title` TEXT NOT NULL, `details` TEXT NOT NULL, `statusRaw` TEXT NOT NULL,
          `priorityRaw` INTEGER NOT NULL, `assignee` TEXT, `dueDate` INTEGER,
          `rank` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
          `resolvedAt` INTEGER, PRIMARY KEY(`id`))
        """,
        "CREATE INDEX IF NOT EXISTS `index_work_items_projectID_statusRaw` " +
            "ON `work_items` (`projectID`, `statusRaw`)",
        """
        CREATE TABLE IF NOT EXISTS `status_changes` (
          `sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id` TEXT NOT NULL,
          `workItemID` TEXT NOT NULL, `projectID` TEXT NOT NULL, `fromRaw` TEXT,
          `toRaw` TEXT NOT NULL, `changedAt` INTEGER NOT NULL, `changedByUserID` TEXT)
        """,
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_status_changes_id` " +
            "ON `status_changes` (`id`)",
        "CREATE INDEX IF NOT EXISTS `index_status_changes_workItemID` " +
            "ON `status_changes` (`workItemID`)",
        "CREATE INDEX IF NOT EXISTS `index_status_changes_projectID` " +
            "ON `status_changes` (`projectID`)",
    )

    /** The tables version 3 had, in the order [statements] creates them. */
    val tables: List<String> = listOf(
        "outbox", "sync_metadata", "session", "projects", "work_items", "status_changes",
    )

    /** The version every assertion about this store depends on. */
    const val VERSION = 3

    /**
     * Creates the whole schema on [connection] and stamps the version.
     *
     * `PRAGMA user_version` is the only thing Room reads to decide whether to migrate;
     * everything else is the shape the committed `3.json` describes.
     */
    fun create(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
        connection.execSQL("PRAGMA user_version = $VERSION")
    }

    /**
     * One column of `PRAGMA table_info`, as SQLite reports it.
     *
     * Read from a live database rather than from the literal's text, so the drift guard
     * compares what SQLite *made* against what the JSON *says* — parsing the DDL string
     * itself would compare the literal with itself and find nothing.
     */
    fun columnsOf(connection: SQLiteConnection, table: String): List<Column> =
        connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        Column(
                            name = statement.getText(1),
                            type = statement.getText(2),
                            notNull = statement.getLong(3) != 0L,
                            // `getText` on a NULL column throws rather than returning null,
                            // so the null check is explicit. A column with no default and
                            // a column whose default is the empty string are different
                            // columns, and conflating them is how a drift guard becomes
                            // a test that agrees with whatever it is shown.
                            defaultValue = if (statement.isNull(4)) null else statement.getText(4),
                            primaryKeyPosition = statement.getLong(5).toInt(),
                        ),
                    )
                }
            }
        }

    /**
     * Every **explicit** index on [table], as `name(unique, columns)`.
     *
     * SQLite's own `sqlite_autoindex_*` are filtered out, and that filter is load-bearing
     * for the drift guard: SQLite synthesises one for every `PRIMARY KEY` on a non-integer
     * column, so `outbox` grows an index the committed JSON never mentions. Comparing those
     * would report a difference on every table forever and train a reader to ignore the
     * guard, which is worse than not having one. What Room emits into `3.json` is the
     * schema; what SQLite synthesises is its implementation of it.
     */
    fun indexesOf(connection: SQLiteConnection, table: String): List<String> {
        // Two passes, and the order matters. `PRAGMA index_info` needs the same connection,
        // and running it while an `index_list` statement is still stepping reuses the
        // connection underneath it — which on the bundled driver threw rather than
        // returning the wrong rows, so the list is drained first and the columns are read
        // afterwards.
        val listed = connection.prepare("PRAGMA index_list(`$table`)").use { statement ->
            buildList {
                while (statement.step()) {
                    val name = statement.getText(1)
                    val unique = statement.getLong(2) != 0L
                    add(name to unique)
                }
            }
        }
        return listed
            .filterNot { (name, _) -> name.startsWith("sqlite_autoindex") }
            .map { (name, unique) ->
                "$name${if (unique) " UNIQUE" else ""}(${columnsOfIndex(connection, name)})"
            }
    }

    private fun columnsOfIndex(connection: SQLiteConnection, index: String): String =
        connection.prepare("PRAGMA index_info(`$index`)").use { statement ->
            buildList {
                while (statement.step()) add(statement.getText(2))
            }.joinToString(", ")
        }

    /** A column as SQLite describes it. Compared field by field against the schema JSON. */
    data class Column(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKeyPosition: Int,
    ) {
        /** The single line a failure message can carry. */
        override fun toString(): String = buildString {
            append(name).append(' ').append(type)
            if (notNull) append(" NOT NULL")
            if (defaultValue != null) append(" DEFAULT ").append(defaultValue)
            if (primaryKeyPosition > 0) append(" PK").append(primaryKeyPosition)
        }
    }
}