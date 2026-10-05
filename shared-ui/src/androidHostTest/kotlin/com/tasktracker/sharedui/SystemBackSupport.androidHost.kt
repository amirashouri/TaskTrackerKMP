package com.tasktracker.sharedui

import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.navigationevent.NavigationEventDispatcher

/**
 * The real chain, not a simulation: the androidx `BackHandler` this host runs
 * registers with the activity's dispatcher, so the press has to enter there.
 * Measured on this host — a synthetic `dispatchOnBackCompleted()` on the
 * dispatcher the scenario provides fires nothing (that dispatcher's history
 * stays empty), while `activity.onBackPressed()` fires the handler. The common
 * declaration carries the full account.
 *
 * At the tab root the shell's handler is disabled, so the press falls through
 * to the activity's own default, exactly as it does on a device.
 */
@OptIn(ExperimentalTestApi::class)
internal actual fun ComposeUiTest.pressSystemBack(dispatcher: NavigationEventDispatcher) {
    val activity = requireNotNull((this as AndroidComposeUiTest<*>).activity) {
        "the Android compose test host must have an activity to press back on"
    }
    activity.onBackPressed()
}