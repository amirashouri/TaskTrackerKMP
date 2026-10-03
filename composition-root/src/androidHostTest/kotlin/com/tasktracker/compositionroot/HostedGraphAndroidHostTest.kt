package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [HostedGraphScenarios] on the Android host, under a Robolectric sandbox.
 *
 * This is the class the five assertions needed all along: they resolve `TaskTrackerDatabase` and
 * `NetworkMonitor`, both of which need a `Context` here, and `@RunWith(RobolectricTestRunner)`
 * is what creates the sandbox that provides one. It cannot be written in `commonTest`, which is
 * why the bodies live in the scenario object and this file contributes only entry points — the
 * same split `:shared-ui` uses.
 *
 * ## `@Config(sdk = [34])` and no `GraphicsMode`
 *
 * The SDK pin matches `:shared-ui`'s Android host suite, so the two agree about the platform the
 * host is standing in for. `GraphicsMode(NATIVE)` is deliberately absent: nothing here renders,
 * so there is no frame to rasterise and asking for a native graphics mode would only slow the
 * class down. This is a graph and database suite, not a Compose one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostedGraphAndroidHostTest {

    @Test
    fun `the session collaborators are registered and the coordinator is app scoped`() = runTest {
        HostedGraphScenarios.theSessionCollaboratorsResolveAndTheCoordinatorIsAppScoped(this)
    }

    @Test
    fun `starting the graph starts the looping services`() = runTest {
        HostedGraphScenarios.startingTheGraphStartsTheLoopingServices(this)
    }

    @Test
    fun `the settings port resolves and is one instance`() = runTest {
        HostedGraphScenarios.theSettingsPortResolvesAndIsOneInstance(this)
    }

    @Test
    fun `the sync chain is registered and shares one coordinator and one monitor`() = runTest {
        HostedGraphScenarios.theSyncChainIsRegisteredAndSharesOneCoordinatorAndOneMonitor(this)
    }

    @Test
    fun `the outbox is left alone`() = runTest {
        HostedGraphScenarios.theOutboxIsLeftAlone(this)
    }
}
