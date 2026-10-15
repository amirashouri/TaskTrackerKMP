package com.tasktracker.database

import androidx.room.useWriterConnection
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage

/**
 * Empties the store without closing it.
 *
 * ## Why this is an interface rather than a method on [TaskTrackerDatabase]
 *
 * The one caller is `:composition-root`'s session teardown, and that class is tested from a
 * `commonTest` source set where no `TaskTrackerDatabase` can be built — Android's host needs a
 * `Context` and the tests deliberately do not have one. A method on the database would force
 * every teardown test to stand up a real store to assert a decision that has nothing to do
 * with Room, so the seam is a one-method type the teardown can be handed a counter for.
 *
 * ## Why it erases rows rather than deleting the file
 *
 * The database is an app-scoped singleton and the repositories, the sync coordinator and the
 * shell all hold it. Dropping the file would leave every one of them pointing at a store that
 * no longer exists, and re-registering the binding mid-process is not something this container
 * supports. Emptying the rows gives the same guarantee the user asked for — nothing of theirs
 * is left on this device — while the store the app is already holding stays usable, which is
 * what lets the sign-out finish rather than crashing the process that asked for it.
 */
interface LocalStoreEraser {

    /** Removes every row this app instance holds. Idempotent on an empty store. */
    suspend fun eraseAll()
}

/**
 * [LocalStoreEraser] over the real store.
 *
 * ## The table list comes from the schema, not from a constant
 *
 * The alternative — ten hand-written `DELETE FROM` statements — is one forgotten line away
 * from a force sign-out that leaves a table behind, and the failure would be invisible: the
 * user is signed out, the app looks clean, and a future schema addition silently survives the
 * wipe. Reading `sqlite_master` means a table added to [TaskTrackerDatabase]'s `entities` is
 * covered the day it lands, with no second place to remember.
 *
 * ## `room_master_table` is excluded, and that exclusion is load-bearing
 *
 * Room writes its identity hash there and validates it when the store is opened. Deleting
 * that row does not fail at the wipe — it fails on the *next launch*, with Room refusing to
 * open a store it cannot verify. A force sign-out that bricks the app on restart is a far
 * worse outcome than the one it exists to fix, so the name is filtered out by the query
 * rather than by a `if` a later edit could reorder.
 *
 * ## No `PRAGMA foreign_keys` dance, because there are no foreign keys
 *
 * `:packages:database`'s own rules forbid them: cross-table references are plain indexed
 * strings, so the repository is what notices a sprint going away, not the schema. Deleting in
 * any order is therefore safe, and the `DELETE FROM` statements need no sequence.
 */
class RoomLocalStoreEraser(private val database: TaskTrackerDatabase) : LocalStoreEraser {

    override suspend fun eraseAll() {
        database.useWriterConnection { connection ->
            val tables = connection.usePrepared(TABLE_NAMES_SQL) { statement ->
                buildList {
                    while (statement.step()) add(statement.getText(0))
                }
            }
            // One statement per table, and no transaction around them: the caller is a
            // sign-out that has already stopped the sync engine, so there is no writer left to
            // interleave with, and a half-erased store is still a store the app can open.
            tables.forEach { table ->
                connection.usePrepared("DELETE FROM \"$table\"") { statement -> statement.step() }
            }
            TTLogger.database.info {
                logMessage {
                    append("store: erased ")
                    append(tables.size)
                    append(" tables")
                }
            }
        }
    }

    private companion object {
        /**
         * Every table except SQLite's own and Room's identity row.
         *
         * `sqlite_%` covers `sqlite_sequence` and `sqlite_stat*`; `room_master_table` is the
         * one ordinary-looking name that has to be named explicitly. See the class note.
         */
        const val TABLE_NAMES_SQL: String =
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'room_master_table'"
    }
}
