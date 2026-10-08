package com.tasktracker.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The version-4 schema, as executable SQL, in one place.
 *
 * ## Why this exists, for the reason [VersionThreeSchema] does
 *
 * To test that a store written by version 4 opens correctly at version 5, something has to
 * *write* a version-4 store, and Room's `MigrationTestHelper` — the something — is
 * Android-only while this module's tests never run on the Android host. So the schema is
 * transcribed from `schemas/…/4.json` and executed through the same bundled driver the app
 * uses; the store is then opened by [TaskTrackerDatabase.open] and Room runs **its own
 * generated** `AutoMigration(4, 5)`. Nothing about the migration is stubbed.
 *
 * ## What version 4 adds over version 3
 *
 * The `sprints` table, plus `storyPoints` and `sprintID` on `work_items` — the 3 → 4
 * migration's whole content. This literal is the shape a real v4 store is in *before* the
 * goal column arrives, which is exactly the state the 4 → 5 assertions need: a sprint row
 * written without a `goal` column has to come back with `goal == null` rather than lose the
 * row.
 *
 * ## The literal is a liability, and it is checked like the v3 one
 *
 * `SprintSchemaDriftTest` executes this object and compares what SQLite actually created
 * against the committed `4.json`, for the same argument [VersionThreeSchema] makes: a
 * hand-written copy that is never checked goes on claiming a version that never existed
 * while the migration test stays green against it. The PRAGMA readers are borrowed from
 * [VersionThreeSchema] rather than duplicated — they take a connection and a table name and
 * read whatever they are handed, so there is no version-specific logic in them to copy.
 */
internal object VersionFourSchema {

    /** Every statement version 4 made, transcribed from `schemas/…/4.json`. */
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
          `resolvedAt` INTEGER, `storyPoints` INTEGER, `sprintID` TEXT, PRIMARY KEY(`id`))
        """,
        "CREATE INDEX IF NOT EXISTS `index_work_items_projectID_statusRaw` " +
            "ON `work_items` (`projectID`, `statusRaw`)",
        "CREATE INDEX IF NOT EXISTS `index_work_items_sprintID` " +
            "ON `work_items` (`sprintID`)",
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
        """
        CREATE TABLE IF NOT EXISTS `sprints` (
          `id` TEXT NOT NULL, `projectID` TEXT NOT NULL, `name` TEXT NOT NULL,
          `goalStoryPoints` INTEGER NOT NULL, `startDate` INTEGER NOT NULL,
          `endDate` INTEGER NOT NULL, `owner` TEXT, `isFinished` INTEGER NOT NULL,
          `finishedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
          PRIMARY KEY(`id`))
        """,
        "CREATE INDEX IF NOT EXISTS `index_sprints_projectID` " +
            "ON `sprints` (`projectID`)",
    )

    /** The tables version 4 had, in the order [statements] creates them. */
    val tables: List<String> = listOf(
        "outbox", "sync_metadata", "session", "projects", "work_items", "status_changes",
        "sprints",
    )

    /** The version every assertion about this store depends on. */
    const val VERSION = 4

    /**
     * Creates the whole schema on [connection] and stamps the version.
     *
     * `PRAGMA user_version` is the only thing Room reads to decide whether to migrate;
     * everything else is the shape the committed `4.json` describes.
     */
    fun create(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
        connection.execSQL("PRAGMA user_version = $VERSION")
    }
}
