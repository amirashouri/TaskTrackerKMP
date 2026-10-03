package com.tasktracker.compositionroot

/**
 * The Android host cannot open a store: `sqlite-bundled`'s `sqliteJni` is not on this target's
 * `java.library.path`, so `inTransaction` throws `AppError.Storage` before anything else runs.
 *
 * This is the same limitation that makes `:packages:database` disable `testAndroidHostTest`, and
 * it is *not* the `Context` problem — that one is fixed, and the other four hosted scenarios pass
 * on this target because of it. See the common declaration for the full reasoning and for what
 * the skip costs.
 */
internal actual fun thisHostOpensRoomStores(): Boolean = false
