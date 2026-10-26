package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * [HostedGraphScenarios] on the JVM.
 *
 * The JVM needs no host: its keychain and preferences backends take nothing, and
 * `DatabaseConfiguration` builds without a context on every target but Android. So this is the
 * fast copy of the five assertions, and the Android host is the one that runs them under a
 * Robolectric sandbox.
 *
 * No `commonTest` entry point, because the Android-host class needs a runner annotation that a
 * common source set cannot carry — see [HostedGraphScenarios] for the full reasoning.
 */
class HostedGraphScenariosTest {

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

    @Test
    fun `the sample seed only runs without a backend`() = runTest {
        HostedGraphScenarios.theSampleSeedOnlyRunsWithoutABackend(this)
    }

    @Test
    fun `a pulled sprint lands in the store`() = runTest {
        HostedGraphScenarios.aPulledSprintLandsInTheStore(this)
    }
}
