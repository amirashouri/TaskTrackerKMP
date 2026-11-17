package com.tasktracker.sharedui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput

/**
 * One system back press, simulated on the dispatcher the composition registered its handler with.
 *
 * `setUpShell` provides the scenario's `LocalNavigationEventDispatcherOwner`, and every host's
 * `BackHandler` joins that dispatcher, so one synthetic completion is the real chain on the JVM,
 * the iOS simulator and the Android host alike. The input subclasses
 * [androidx.navigationevent.NavigationEventInput] because its `dispatch*` methods are `protected`
 * — the same seam the activity's back chain uses — and `dispatchOnBackCompleted` injects the
 * non-predictive `dispatchOnStarted` the state machine requires, so a plain tap needs no setup.
 *
 * The Android host used to press through `activity.onBackPressed()` instead, on the theory that
 * its `BackHandler` ignored the provided owner and registered with the activity. With
 * `activity-compose` 1.12 that is false: `BackHandler` prefers
 * `LocalNavigationEventDispatcherOwner`, and it is the *activity's* dispatcher that never sees
 * the handler. Pressing the activity left the router untouched and was recorded as a host
 * limitation, which skipped the back assertions instead of testing them.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.pressSystemBack(dispatcher: NavigationEventDispatcher) {
    val input = object : NavigationEventInput() {
        fun press() = dispatchOnBackCompleted()
    }
    dispatcher.addInput(input)
    input.press()
    dispatcher.removeInput(input)
}
