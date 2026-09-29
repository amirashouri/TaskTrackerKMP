package com.tasktracker.core

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Injectable wall clock. JWT expiry logic depends on "now", so tests must be able
 * to move time forward without sleeping.
 *
 * A `suspend` `now()` rather than a plain function because the original protocol
 * was `async`; keeping it `suspend` means the protocol can later be implemented on
 * top of a platform clock that must suspend (a device with no RTC, say) without
 * changing every call site.
 */
interface DateProvider {
    suspend fun now(): Instant
}

/** The real clock. */
class SystemDateProvider : DateProvider {
    override suspend fun now(): Instant = Clock.System.now()
}

/**
 * Manually advanced clock for tests.
 *
 * Lives in `main` rather than `test` on purpose: the original ships `TestDateProvider`
 * from the core module because the sync engine's test suite is in a different module
 * from the clock it drives. Hiding it in `commonTest` would make that unpossible.
 */
class TestDateProvider(private var current: Instant) : DateProvider {
    constructor() : this(Instant.fromEpochSeconds(1_760_000_000))

    override suspend fun now(): Instant = current

    fun advance(bySeconds: Long) {
        current += bySeconds.seconds
    }

    fun set(to: Instant) {
        current = to
    }
}
