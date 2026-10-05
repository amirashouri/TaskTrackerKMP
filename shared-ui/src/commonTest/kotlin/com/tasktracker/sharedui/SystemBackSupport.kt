package com.tasktracker.sharedui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.navigationevent.NavigationEventDispatcher

/**
 * One system back press, simulated the way this host receives one.
 *
 * `expect` rather than one body for all three hosts because the hosts reach
 * `BackHandler` through different dispatchers, and the difference was measured,
 * not assumed. On the Android host an explicit `LocalNavigationEventDispatcherOwner`
 * provide *is* the one the composition local resolves (a diagnostic printed
 * `resolved isOurOwner=true dispatcherIsOurs=true`), but the androidx `BackHandler`
 * that the platform delegate installs registers with the *activity's* dispatcher:
 * the provided dispatcher's history stayed empty, a synthetic
 * `dispatchOnBackCompleted()` on it fired no handler, and `activity.onBackPressed()`
 * did. On the JVM and iOS hosts the JetBrains `BackHandler` registers with the
 * provided dispatcher, so the synthetic dispatch is the real chain there.
 *
 * The JVM/iOS body subclasses [androidx.navigationevent.NavigationEventInput]
 * because its `dispatch*` methods are `protected` — the same seam the activity's
 * back chain uses — and `dispatchOnBackCompleted` injects the non-predictive
 * `dispatchOnStarted` the state machine requires, so a plain tap needs no setup.
 *
 * [dispatcher] is unused on the Android host, where the press goes through the
 * activity instead; it is kept in the signature so the scenario reads the same
 * on every host.
 */
@OptIn(ExperimentalTestApi::class)
internal expect fun ComposeUiTest.pressSystemBack(dispatcher: NavigationEventDispatcher)