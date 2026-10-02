package com.tasktracker.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A multi-consumer event channel.
 *
 * A single channel cannot fan out, so anything that needs more than one listener —
 * sync status, session invalidation — needs a real broadcast, one queue per
 * subscriber rather than one shared cursor. The original spelled this out as a
 * dictionary of `AsyncStream.Continuation`s; [callbackFlow] gives the same shape with
 * the registration and the cleanup handled by structured concurrency.
 *
 * ## Why not `MutableSharedFlow`
 *
 * It is the obvious choice and it is the wrong one. It has no `complete()`, so there
 * is no way to express [finish] — and "this session's stream is over, stop waiting"
 * is a real state here, not a nicety. A view that subscribes after a sign-out would
 * hang forever on a stream that can only ever be silent. `callbackFlow` also
 * guarantees the subscriber is unregistered when the collector's coroutine is
 * cancelled, which is the leak a hand-rolled dictionary is one forgotten `onTermination`
 * away from.
 *
 * @param Element must be immutable — every subscriber receives the same instance,
 *   so a mutable one would be mutated by whoever got there last.
 */
@OptIn(ExperimentalUuidApi::class)
class Broadcaster<Element> {

    private val subscribers = mutableMapOf<Uuid, Channel<Element>>()

    private var finished = false

    /**
     * A fresh stream per caller. Each collection registers its own subscriber, and
     * cancelling the collecting coroutine unregisters it — so [subscriberCount]
     * tracks live collectors rather than ever having been created.
     *
     * Returns an already-finished stream once [finish] has been called, rather than
     * registering a queue that nothing will ever close. A view that subscribes after
     * a sign-out has to see completion, not wait on a channel that is never written
     * to and never closed.
     *
     * `flow { }` rather than `callbackFlow { }`: `callbackFlow` demands the block end
     * in `awaitClose`, which cannot be reached once the channel is closed and drained
     * — the normal completion path would throw instead. `consumeAsFlow` completes
     * exactly when the channel does, which is the behaviour wanted here.
     */
    fun stream(): Flow<Element> = flow {
        if (finished) return@flow

        val id = Uuid.random()
        val channel = Channel<Element>(Channel.UNLIMITED)
        subscribers[id] = channel
        try {
            channel.consumeAsFlow().collect { emit(it) }
        } finally {
            // In a `finally`, not after the collect: a collector cancelled mid-stream
            // throws out of it, and an unregister placed after it would run only on
            // normal completion. That is precisely the leak this class is supposed to
            // make impossible — a screen dismissed during a sync would leave a queue
            // behind that nothing drains, and `subscriberCount` would keep counting it.
            subscribers.remove(id)
        }
    }

    /**
     * Live subscriber count. A subscriber is only counted once collection has
     * started, so a test asserting on this must have a collector running — obtaining
     * the [Flow] alone registers nothing.
     */
    val subscriberCount: Int get() = subscribers.size

    /** Emits to every current subscriber, and never blocks. */
    fun send(element: Element) {
        subscribers.values.forEach { channel ->
            // Unbounded, so this cannot fail. If it ever could, dropping the event
            // silently is the one acceptable outcome for a status broadcast — the
            // next event supersedes it.
            channel.trySend(element)
        }
    }

    /**
     * Ends every stream. Collectors see a normal completion, and a subscriber that
     * starts afterwards gets an immediately-finished stream.
     *
     * Not reversible, and not meant to be. One [Broadcaster] lives for one session;
     * a new session gets a new instance from `:packages:di`. Restarting this one
     * would resurrect queues that a cancelled collector had already abandoned.
     */
    fun finish() {
        finished = true
        // Snapshotted, and the map emptied, *before* any channel is closed. Closing a channel
        // completes the collector, whose `finally` removes its own entry from `subscribers` —
        // so iterating the live map while closing throws ConcurrentModificationException on the
        // JVM, the moment a collector is actually parked on its channel. Clearing first also
        // makes the unregistering `remove` a no-op rather than a rehash.
        //
        // `try { } finally { }` because a close that throws must not leave the broadcaster
        // looking finished while its queues are still open.
        val open = subscribers.values.toList()
        try {
            subscribers.clear()
            open.forEach { it.close() }
        } catch (failure: Exception) {
            open.forEach { runCatching { it.close() } }
            throw failure
        }
    }
}
