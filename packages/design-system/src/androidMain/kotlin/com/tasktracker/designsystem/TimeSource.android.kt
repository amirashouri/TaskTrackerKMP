package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import android.os.SystemClock

@Composable
actual fun currentNanoTime(): Long = SystemClock.uptimeMillis() * 1_000_000