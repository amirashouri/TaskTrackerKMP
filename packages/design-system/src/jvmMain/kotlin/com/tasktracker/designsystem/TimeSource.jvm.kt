package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

@Composable
actual fun currentNanoTime(): Long = System.nanoTime()