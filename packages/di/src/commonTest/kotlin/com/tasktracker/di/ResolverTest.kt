package com.tasktracker.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Contract tests for the Koin-backed [Resolver], checked against the behaviours the
 * original `DIContainer` guaranteed. These are the tests that make the swap to Koin
 * safe: they assert the *policy* the wrapper owns, not Koin's own test suite.
 */
private var nextSessionId = 0

class ResolverTest {

    private class Counter {
        var instances: Int = 0
            private set

        init {
            instances++
        }
    }

    private class SessionThing {
        val id: Int = nextSessionId++
    }

    private class Repo
    private class Service(val repo: Repo)
    private class Value

    // Two mutually dependent types, for the two-step cycle.
    private class A
    private class B

    /**
     * Lets a test register a factory whose *declared* type is not the type the factory
     * builds. A cycle between two types cannot be expressed otherwise: the container's
     * `app<T>` requires the factory to return T, so A's factory has to hand back an A
     * after first forcing B into existence.
     */
    private class Slot {
        lateinit var value: Any

        @Suppress("UNCHECKED_CAST")
        fun <T> unchecked(): T = value as T
    }

    @Test
    fun `resolve before start fails rather than returning a half-built graph`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }

        assertFalse(resolver.isStarted)
        assertFailsWith<DiException.NotStarted> { resolver.resolve(Repo::class) }
    }

    @Test
    fun `start is idempotent only in the sense that a second call is rejected`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }
        resolver.start()

        assertTrue(resolver.isStarted)
        assertFailsWith<IllegalStateException> { resolver.start() }
    }

    @Test
    fun `app scope returns the same instance every time`() {
        val counter = Counter()
        val resolver = KoinResolver.build { app { counter } }
        resolver.start()

        assertSame(resolver.resolve(Counter::class), resolver.resolve(Counter::class))
    }

    @Test
    fun `transient scope returns a fresh instance every time`() {
        val resolver = KoinResolver.build { transient { Counter() } }
        resolver.start()

        assertNotSame(resolver.resolve(Counter::class), resolver.resolve(Counter::class))
    }

    @Test
    fun `session scope is cached until the session is reset`() {
        val resolver = KoinResolver.build { session { SessionThing() } }
        resolver.start()

        val first = resolver.resolve(SessionThing::class)
        assertSame(first, resolver.resolve(SessionThing::class))

        resolver.resetSessionScope()

        assertNotSame(first, resolver.resolve(SessionThing::class))
    }

    @Test
    // Kotlin/Native rejects commas in function names, so this one reads as two clauses
    // joined by "and" rather than the comma a JVM-only test could get away with.
    fun `session ids are distinct across a reset and so the instance was rebuilt`() {
        val resolver = KoinResolver.build { session { SessionThing() } }
        resolver.start()

        val first = resolver.resolve(SessionThing::class).id
        resolver.resetSessionScope()
        val second = resolver.resolve(SessionThing::class).id

        assertTrue(second > first, "expected a new instance after reset, got id $second after $first")
    }

    @Test
    fun `resetSessionScope leaves app-scoped instances alone`() {
        val counter = Counter()
        val resolver = KoinResolver.build {
            app { counter }
            session { SessionThing() }
        }
        resolver.start()

        val appInstance = resolver.resolve(Counter::class)
        resolver.resolve(SessionThing::class)

        resolver.resetSessionScope()

        assertSame(appInstance, resolver.resolve(Counter::class))
    }

    @Test
    fun `a factory may resolve its own dependencies`() {
        val resolver = KoinResolver.build {
            app<Repo> { Repo() }
            app { Service(resolver.resolve(Repo::class)) }
        }
        resolver.start()

        val service = resolver.resolve(Service::class)

        // Resolved *after* the service, so this is the cached app-scoped instance the
        // service's factory was handed. Same reference is the whole assertion: a factory
        // that built its own Repo would pass a `notSame` check here.
        assertSame(resolver.resolve(Repo::class), service.repo)
    }

    @Test
    fun `a cycle throws CircularDependency with the full chain instead of overflowing`() {
        val slot = Slot()
        val resolver = KoinResolver.build {
            // Each factory forces the other type into existence before producing its own,
            // so the resolver is re-entered with a type already on the stack.
            app<A> {
                slot.value = resolver.resolve(B::class)
                slot.unchecked()
            }
            app<B> {
                slot.value = resolver.resolve(A::class)
                slot.unchecked()
            }
        }
        resolver.start()

        val error = assertFailsWith<DiException.CircularDependency> { resolver.resolve(A::class) }

        assertTrue(
            error.message.orEmpty().contains("A -> B -> A"),
            "expected the chain in the message, got: ${error.message}",
        )
    }

    @Test
    fun `a self-referential registration throws rather than recursing forever`() {
        val resolver = KoinResolver.build { app<Value> { resolver.resolve(Value::class) } }
        resolver.start()

        assertFailsWith<DiException.CircularDependency> { resolver.resolve(Value::class) }
    }

    @Test
    fun `an unregistered type throws Unregistered`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }
        resolver.start()

        assertFailsWith<DiException.Unregistered> { resolver.resolve(Service::class) }
    }

    @Test
    fun `forceResolve raises DiFatal so a mis-wired graph cannot render silently`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }
        resolver.start()

        assertFailsWith<DiFatal> { resolver.forceResolve(Service::class) }
    }

    @Test
    fun `getOrNull returns null for an unregistered type without throwing`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }
        resolver.start()

        assertEquals(null, resolver.getOrNull(Service::class))
        assertSame(resolver.resolve(Repo::class), resolver.getOrNull(Repo::class))
    }

    @Test
    fun `getOrNull does not swallow a cycle`() {
        val resolver = KoinResolver.build { app<Value> { resolver.resolve(Value::class) } }
        resolver.start()

        assertFailsWith<DiException.CircularDependency> { resolver.getOrNull(Value::class) }
    }

    @Test
    fun `invalidateInstances drops app instances but keeps the graph usable`() {
        val resolver = KoinResolver.build {
            app { Counter() }
            session { SessionThing() }
        }
        resolver.start()

        val counter = resolver.resolve(Counter::class)
        resolver.resolve(SessionThing::class)

        resolver.invalidateInstances()

        assertNotSame(counter, resolver.resolve(Counter::class))
        assertTrue(resolver.isStarted)
        assertSame(
            resolver.resolve(Counter::class),
            resolver.resolve(Counter::class),
            "app scope must be cached again after a rebuild",
        )
    }

    @Test
    fun `a duplicate registration is rejected at composition time`() {
        assertFailsWith<IllegalStateException> {
            KoinResolver.build {
                app<Repo> { Repo() }
                app<Repo> { Repo() }
            }
        }
    }

    @Test
    fun `registeredNames is sorted for the debug dump`() {
        val resolver = KoinResolver.build {
            app<Repo> { Repo() }
            app<Value> { Value() }
        }

        assertEquals(listOf("Repo", "Value"), resolver.registeredNames)
    }

    @Test
    fun `isRegistered answers without starting the container`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }

        assertTrue(resolver.isRegistered(Repo::class))
        assertFalse(resolver.isRegistered(Service::class))
    }

    @Test
    fun `stop makes the container unusable`() {
        val resolver = KoinResolver.build { app<Repo> { Repo() } }
        resolver.start()
        resolver.stop()

        assertFalse(resolver.isStarted)
        assertFailsWith<DiException.NotStarted> { resolver.resolve(Repo::class) }
    }
}
