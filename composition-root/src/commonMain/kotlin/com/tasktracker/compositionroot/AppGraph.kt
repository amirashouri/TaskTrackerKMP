package com.tasktracker.compositionroot

import com.tasktracker.di.Resolver
import com.tasktracker.di.KoinResolver

/**
 * The one module allowed to see every concrete type, and therefore the only place the
 * object graph is described. This is the Kotlin counterpart of the original's
 * `App/CompositionRoot.swift`.
 *
 * Registrations are added here, in one place, in dependency order. Nothing else may
 * construct a concrete dependency: a presentation module asks its own domain module for a
 * port, and the binding from a port to an implementation exists only inside this file.
 * That is what keeps the SwiftPM target boundaries enforceable under Gradle — a
 * presentation module does not merely avoid importing a data module, it has no edge to it
 * to import from.
 */
object AppGraph {
    /**
     * Builds the container from every registration and then starts it.
     *
     * Registration and start are separate calls on purpose. A container that resolved
     * during registration could not detect a dependency cycle, because the first factory
     * call would land before the graph was fully described. Splitting them means every
     * resolve is a real edge in a finished description.
     *
     * The graph is currently empty. Each feature's ports and implementations land here as
     * they are ported; nothing else in the build may call `KoinResolver` directly, and
     * `AppGraphAccessTest` fails if that starts happening.
     */
    fun start(): Resolver = KoinResolver.build {}.also { it.start() }
}
