package com.tasktracker.database

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Where Room runs its queries.
 *
 * An `expect` because `Dispatchers.IO` is declared `internal` in the coroutines library's
 * *common* source set and only exposed per platform — it is not a type common code is
 * allowed to name, even though every target here has one.
 */
internal expect val databaseQueryDispatcher: CoroutineDispatcher
