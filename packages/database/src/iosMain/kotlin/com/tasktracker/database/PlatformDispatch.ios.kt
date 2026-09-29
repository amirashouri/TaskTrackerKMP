package com.tasktracker.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// `Dispatchers.IO` is not reachable from Kotlin/Native either — it is internal there too,
// and on Native the default pool is already sized for blocking work. `Dispatchers.Default`
// is the platform's own recommendation for a dispatcher that has to wait on I/O, so the
// common code names one thing and each platform picks the right one.
internal actual val databaseQueryDispatcher: CoroutineDispatcher = Dispatchers.Default
