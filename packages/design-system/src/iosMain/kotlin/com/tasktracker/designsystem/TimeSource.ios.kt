package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent

@Composable
actual fun currentNanoTime(): Long = (CFAbsoluteTimeGetCurrent() * 1_000_000_000).toLong()