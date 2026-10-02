package com.tasktracker.featureprojectspresentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A scope that starts work as soon as it is launched, but still ends with the test.
 *
 * ## Why this exists
 *
 * Every model here is observed, not awaited: `observe(scope)` launches the first read and
 * the change subscription, and returns immediately. A test therefore launches something
 * and then asserts on what it did, without suspending in between.
 *
 * Launching onto [backgroundScope]'s own dispatcher does not work for that shape. It is the
 * standard test dispatcher, which does not run the task until the test body suspends - so
 * the first read had not happened, the `Broadcaster` had no subscribers, and
 * `subscriberCount` read 0. The first draft of these tests failed 14 of 15 that way, which
 * is a test asserting on launch order rather than on the model.
 *
 * `:packages:core`'s `BroadcasterAndIdentifierTest` already established the fix for the
 * same reason ("Unconfined so the collectors are already subscribed by the time the send
 * happens"), and this is that idiom in one place so no test has to remember it.
 *
 * The `+` replaces only the dispatcher. The job still belongs to
 * [TestScope.backgroundScope], so a collector is still cancelled when the test ends, and
 * `observe`'s cancellation tests keep working.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.eagerScope(): CoroutineScope = CoroutineScope(
    backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
)
