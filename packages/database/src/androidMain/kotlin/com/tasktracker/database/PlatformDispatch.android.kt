package com.tasktracker.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val databaseQueryDispatcher: CoroutineDispatcher = Dispatchers.IO
