package com.tasktracker.featureauthdata

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Starts collecting [flow] into [into] for the lifetime of the test.
 *
 * `backgroundScope` so the collector is cancelled when the test body ends — a `launch` on the
 * test scope would instead be waited for, and an unbounded collect over a bus that goes quiet
 * is a test that never finishes.
 *
 * `UnconfinedTestDispatcher` so the subscription is registered before the caller's next line,
 * which is the whole point: `Broadcaster` registers on collection rather than on stream
 * creation, so a `send` issued before the collector is live is *dropped*, and a test that
 * wrote `val states = bus.states()` and then sent would be asserting against a bus nobody was
 * listening to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> TestScope.collecting(flow: Flow<T>, into: MutableList<T>): Job =
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { into += it } }
