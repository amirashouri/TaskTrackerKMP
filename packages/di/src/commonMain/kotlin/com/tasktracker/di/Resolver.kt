package com.tasktracker.di

import kotlin.reflect.KClass

/**
 * The container contract the composition root is written against.
 *
 * This mirrors the original `DIContainer` in
 * `Packages/DependencyInjection/Sources/DependencyInjection/DIContainer.swift`, but as
 * a narrow interface over Koin rather than a hand-rolled registry. Only this interface
 * is visible outside `:packages:di`; Koin types never leak into a presentation module,
 * so swapping the backing library stays a single-module change.
 *
 * The four behaviours kept from the original are the ones callers actually depend on:
 * explicit startup, the app/session/transient scopes, [forceResolve] for wiring bugs,
 * and a *throwing* cycle error rather than a stack overflow.
 */
interface Resolver {
    /**
     * Resolves [klass], or throws [DiException.Unregistered] if nothing is registered.
     *
     * @throws DiException.CircularDependency if a factory asks for a type that is
     *   already being built further up the same resolution.
     * @throws DiException.NotStarted if called before [start].
     */
    fun <T : Any> resolve(klass: KClass<T>): T

    /** Resolves [klass] or returns null. For optional collaborators, not for wiring. */
    fun <T : Any> getOrNull(klass: KClass<T>): T?

    /**
     * Resolves [klass], or fails the process if it cannot.
     *
     * The original used `fatalError` here for a specific reason: view models resolve
     * during `body` evaluation, and throwing from inside a Compose `body` produces a
     * crash with no usable context, whereas a process-level failure here names the
     * missing registration. On Android and iOS this becomes a crash carrying the same
     * message, so the diagnostic survives.
     */
    fun <T : Any> forceResolve(klass: KClass<T>): T

    /**
     * Starts the container. Registration happens before this; resolution does not.
     *
     * The split is the point. A container that resolved during registration could not
     * detect a cycle, because the first factory call would land before the stack was
     * meaningful. Requiring an explicit start means every resolve is a real, complete
     * edge in a graph that is already fully described.
     *
     * @throws IllegalStateException if called twice.
     */
    fun start()

    /** True once [start] has been called. */
    val isStarted: Boolean

    /**
     * Drops every `session`-scoped instance while keeping the registrations, so the next
     * resolve rebuilds from the same factory. Called on sign-out so nothing holding
     * user-scoped state survives into the next user's session.
     */
    fun resetSessionScope()

    /** Drops `app` and `session` instances, keeping registrations. For a database wipe. */
    fun invalidateInstances()

    /** Releases the backing container. The resolver is unusable afterwards. */
    fun stop()

    fun isRegistered(klass: KClass<*>): Boolean

    /** Sorted registration names, for the debug tooling that dumps the graph. */
    val registeredNames: List<String>
}
