package com.tasktracker.di

/**
 * Failures the container raises, mirroring the original `DIError`.
 *
 * Every case is a distinct type rather than a message so tests assert on the *kind* of
 * wiring failure instead of on prose that a refactor could reword.
 */
sealed class DiException(message: String) : Exception(message) {
    /** Nothing is registered for the requested type. */
    class Unregistered(type: String) : DiException("DI: no registration for $type")

    /**
     * A factory asked for a type that is already being built further up the same
     * resolution. The message is the full chain, in the order it was entered, because
     * the innermost pair is rarely the useful part.
     */
    class CircularDependency(chain: String) :
        DiException("DI: circular dependency: $chain")

    /** The factory itself threw. [type] is what was being built when it did. */
    class FactoryFailed(type: String, cause: String) :
        DiException("DI: factory for $type failed: $cause")

    /** Resolution was attempted before [Resolver.start]. */
    class NotStarted : DiException("DI: resolve called before the container was started")
}
