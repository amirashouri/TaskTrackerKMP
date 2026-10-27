package com.tasktracker.sharedui

import androidx.compose.runtime.Composable

/**
 * The Desktop app has no Reduce Motion preference this shell reads, so the answer is no.
 *
 * Not because desktop platforms have no such switch — macOS has one under Accessibility, and
 * Windows has an animation-effects toggle — but because neither is reachable from the JVM, and
 * the shell cannot invent a setting the operating system keeps to itself. The honest answer is
 * that the seam is here and unused: when the Desktop app grows a preference for this, this
 * function is where it is read, and no call site changes.
 *
 * The JVM host is also the one that runs the shell's test suite, so `false` is what the
 * scenarios see unless one hands in a preference of its own.
 */
@Composable
actual fun platformReducedMotion(): Boolean = false
