package com.tasktracker.sharedui

import androidx.compose.runtime.Composable

/**
 * Android has no OS-level Reduce Transparency switch, so the answer is no.
 *
 * This is a finding rather than an unimplemented seam: iOS's switch is a real user
 * preference that every app inherits, while Android expresses the same intent through
 * per-app Material You theming, not a system accessibility toggle. Reading a private
 * `Settings.Global` key to invent one would be worse than the honest `false`, because no
 * Android user can change that key from Accessibility. If Android grows the switch, this
 * function is where it is read and no call site changes.
 */
@Composable
actual fun platformReduceTransparency(): Boolean = false
