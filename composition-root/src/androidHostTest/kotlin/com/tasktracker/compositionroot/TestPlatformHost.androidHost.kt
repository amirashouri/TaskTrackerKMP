package com.tasktracker.compositionroot

import org.robolectric.RuntimeEnvironment

/**
 * A `Context` for Room on the Android test host, or `Unit` when this target has none.
 *
 * ## Why this usually returns `Unit`, and why that is not a shortcut
 *
 * `RuntimeEnvironment.getApplication()` reads from a Robolectric sandbox, and a sandbox is
 * created by the
 * runner from `@RunWith(RobolectricTestRunner::class)`. The tests this file serves live in
 * `commonTest`, and a runner annotation is not expressible in a common source set. So by the
 * time they ask, nothing has built an application, and the honest answer is "there is none".
 * `androidx.test:core`'s `ApplicationProvider` is no help here: it reads the same sandbox, and
 * is not a dependency this module has any reason to take.
 *
 * The alternative was a hand-rolled `ContextWrapper` over `null`. It would have satisfied
 * Room's signature and then failed somewhere deeper, which is strictly worse than failing
 * here with a message about a real missing thing: a green test that opened a database against
 * a context nobody built is a test that stopped checking anything.
 *
 * ## What this costs
 *
 * Graph assertions that resolve [com.tasktracker.database.TaskTrackerDatabase] cannot run on
 * `testAndroidHostTest`, because Room's `inMemoryDatabaseBuilder` takes a `Context` and refuses
 * to open without one. That is a capability gap in this test target, not a wiring fault: the
 * same assertions pass on JVM and iOS, where the store needs no context at all. Fixing it
 * properly means giving this target a runner — a `jvmTest`/`iosTest`/`androidHostTest` split
 * for the database-resolving cases, or an instrumentation runner for the whole target.
 * `:composition-root`'s `AppGraphAccessTest` is already `jvmTest` rather than `commonTest` for
 * exactly this class of reason.
 *
 * ## Why a null is not returned instead
 *
 * `PlatformBindings.host` is `Any?` because production passes `null` on iOS, where it means
 * "this backend needs nothing". Returning `Unit` here says the same thing — a real value was
 * supplied, and the backend simply does not read it — and keeps the distinction from
 * `applicationDefault` needing to special-case an Android host that was never given a context.
 */
internal actual fun testPlatformHost(): Any {
    return runCatching { RuntimeEnvironment.getApplication() }.getOrNull() ?: Unit
}
