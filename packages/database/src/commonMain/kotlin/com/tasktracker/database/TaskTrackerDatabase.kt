package com.tasktracker.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tasktracker.core.AppError

/**
 * The app's single store.
 *
 * **Why one store, and why it is declared here.**
 *
 * The original declared its schema at runtime: `ModelSchemaRegistry.build()` took a list
 * of `@Model` types, and a feature's data module handed its own types in at launch, so
 * `:packages:database` never had to know a feature existed. That was a workaround for
 * SwiftData, not a goal.
 *
 * Room has no equivalent. `@Database(entities = [...])` is read by the annotation
 * processor at compile time, and the set of tables cannot be a runtime value. A
 * runtime list would force one `@Database` per feature, and that would break the one
 * property this module exists to protect: a repository has to write its entity *and* the
 * outbox row inside a single transaction, which is only possible when both tables live
 * in the same database. Two databases means two transactions, and the queue can then
 * disagree with the store — a mutation saved with nothing queued to push it, which is
 * the exact failure the outbox exists to prevent.
 *
 * So the dependency still points one way (a feature knows about `:packages:database`,
 * never the reverse) but registration is a one-line edit to [entities] when a feature
 * data module lands, in phases 8 and 11. The behaviour tests that would have covered
 * the runtime registry are in `TransactionTest` instead, where they assert the property
 * that actually matters: two tables, one transaction, a throw leaves neither behind.
 */
/**
 * The factory Kotlin/Native needs, declared once and generated per target.
 *
 * `RoomDatabaseConstructor` is an interface Room generates `actual` implementations of —
 * one per target, each instantiating that target's generated `TaskTrackerDatabase_Impl`.
 * Writing the `expect` object here and linking it with
 * [TaskTrackerDatabase]'s `@ConstructedBy` is what makes those actuals be generated at
 * all; without it Room refuses to process a `@Database` for a non-Android target, since
 * there is no reflection there to find the implementation.
 *
 * On the JVM and Android the builders never touch this: their reflective default does the
 * same job. It exists for Native, which has no reflection.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object TaskTrackerDatabaseConstructor : RoomDatabaseConstructor<TaskTrackerDatabase> {
    override fun initialize(): TaskTrackerDatabase
}

@ConstructedBy(TaskTrackerDatabaseConstructor::class)
@Database(
    entities = [
        OutboxRecord::class,
        SyncMetadataRecord::class,
        SessionRecord::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class TaskTrackerDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao

    abstract fun syncMetadataDao(): SyncMetadataDao

    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * Opens a store, mapping every failure to [AppError.Storage].
         *
         * **An in-memory store must be built with the in-memory builder.** Pointing a
         * file-backed store at `/dev/null` — the tempting shortcut — does not produce an
         * in-memory store: it produces a real SQLite store whose journal, WAL and
         * shared-memory sidecar files are all created on the null device. Several of
         * those open at once in one process crash the test runner with SIGSEGV, and a
         * preview or a test can silently lose writes that a genuine in-memory store would
         * have kept. The original lost a build to this; [Configuration.inMemory] therefore
         * carries no usable name at all, so there is no way to ask for the null device by
         * accident.
         */
        fun open(configuration: DatabaseConfiguration): TaskTrackerDatabase {
            if (configuration.inMemory) {
                return try {
                    inMemoryDatabaseBuilder(configuration.host)
                        .setDriver(BundledSQLiteDriver())
                        .setQueryCoroutineContext(databaseQueryDispatcher)
                        .build()
                } catch (failure: Throwable) {
                    throw AppError.Storage("Could not open the in-memory database: ${failure.readable()}")
                }
            }
            return try {
                // The parent directory has to exist before SQLite opens the file, and
                // every failure in here - including one from this call - is a storage
                // failure, because a store that cannot be opened is not a recoverable
                // condition and must never be mistaken for an auth or network problem.
                createParentDirectory(configuration.name)
                fileDatabaseBuilder(configuration.name, configuration.host)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(databaseQueryDispatcher)
                    .build()
            } catch (failure: Throwable) {
                throw AppError.Storage("Could not open the local database: ${failure.readable()}")
            }
        }

        /**
         * A throwaway store for tests and previews.
         *
         * Each call gets its own store, which is what makes tests independent and safe
         * to run in parallel — a property the original had to document as a hazard
         * ("one container per process") because its in-memory store was process-wide.
         */
        fun inMemory(): TaskTrackerDatabase = open(DatabaseConfiguration.inMemory)
    }
}
