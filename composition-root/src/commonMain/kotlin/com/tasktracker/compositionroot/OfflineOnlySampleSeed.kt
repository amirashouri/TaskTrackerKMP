package com.tasktracker.compositionroot

import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/**
 * The projects port with its demo board switched off.
 *
 * Delegates every method to [delegate] except `seedSampleDataIfNeeded`, which answers `false`
 * and writes nothing.
 *
 * ## Why the sample board needs a switch at all
 *
 * `RoomProjectRepository` does not write its sample board behind the store's back: it queues
 * every row through the outbox, so sample data behaves like any other write the day a server is
 * attached — which is the promise its own KDoc makes. This class exists for the other edge of
 * that promise. With a real backend bound, the first sync pushes the demo board to the server,
 * for an account whose owner never asked to populate it.
 *
 * So the sample board is an *offline* fixture. [AppGraph] already decides whether a backend is
 * bound — `PlatformBindings.apiBaseUrl` — and wraps the real repository in this decorator on
 * exactly that condition. Offline, the real repository is bound unchanged and the demo still
 * seeds.
 *
 * ## Why a decorator rather than a flag on the repository
 *
 * A `seedSampleDataIsEnabled` argument on `RoomProjectRepository` would move the question "is a
 * backend bound?" — which only the composition root can answer — into `:feature-projects-data`,
 * and would change a public constructor in a second module. Interface delegation keeps the
 * decision at the composition point, in the one place that already branches on the same value,
 * and leaves the repository and its own tests untouched.
 *
 * ## Why `false` rather than an exception
 *
 * `false` is the port's own word for "nothing was seeded" — the answer a store that already has
 * projects gets — so this is indistinguishable from the idempotent no-op that callers already
 * rely on. Both call sites (`NavigationHost`'s project-scoped tab and `ProjectsListModel`'s
 * reload) discard the result, and neither has anything to recover from.
 */
internal class OfflineOnlySampleSeed(
    private val delegate: ProjectRepositoryPort,
) : ProjectRepositoryPort by delegate {

    override suspend fun seedSampleDataIfNeeded(): Boolean = false
}
