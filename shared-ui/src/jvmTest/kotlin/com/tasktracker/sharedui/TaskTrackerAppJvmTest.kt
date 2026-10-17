package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi

/**
 * [ShellScenarios] on the JVM, inherited from [TaskTrackerAppScenarios].
 *
 *
 * The JVM host is the fast loop for the shell: the same scenarios run on the iOS simulator
 * * (`TaskTrackerAppIosTest`) and the Android host (`TaskTrackerAppAndroidHostTest`), and
 * * this one runs in seconds with no simulator, so a change to the app root is verified here
 * * first.
 *
 * No `commonTest` entry point, for the reason [ShellScenarios] gives: an Android-host
 * Compose UI test only runs under Robolectric, and that runner annotation is not
 * expressible in `commonTest`.
 */
@OptIn(ExperimentalTestApi::class)
class TaskTrackerAppJvmTest : TaskTrackerAppScenarios()

/**
 * The JVM host carries a 1000dp shell, so the wide-layout scenarios run here.
 *
 * This host is also the Desktop app's own suite — `:shared-ui`'s `jvmTest` *is* the Desktop test
 * host — so "the rail exists at desktop widths" is asserted on the same host that runs the
 * Desktop app, which is the only arrangement in which that claim is worth anything.
 */
internal actual fun thisHostExpressesDesktopWidth(): Boolean = true

/**
 * The JVM host presses through the synthetic input on the dispatcher the shell registers with, so
 * the scenario's back assertions run here.
 */
internal actual fun thisHostDeliversSystemBack(): Boolean = true

/** No-op: this host expresses a desktop width, so there is nothing to skip. */
internal actual fun assumeHostExpressesDesktopWidth() = Unit

/** No-op: this host delivers a system back press, so there is nothing to skip. */
internal actual fun assumeHostDeliversSystemBack() = Unit
