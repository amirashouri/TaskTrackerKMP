package com.tasktracker.sharedui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput

/**
 * The JVM host's `BackHandler` registers with the dispatcher the scenario
 * provides through `LocalNavigationEventDispatcherOwner`, so dispatching on it
 * is the same chain a device's back button takes. The common declaration
 * carries the measurement behind the split.
 */
@OptIn(ExperimentalTestApi::class)
internal actual fun ComposeUiTest.pressSystemBack(dispatcher: NavigationEventDispatcher) {
    val input = object : NavigationEventInput() {
        fun press() = dispatchOnBackCompleted()
    }
    dispatcher.addInput(input)
    input.press()
    dispatcher.removeInput(input)
}