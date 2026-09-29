package com.tasktracker.di

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.reflect.KClass

/**
 * Koin-backed [Resolver].
 *
 * Koin owns the *recipes*: every registration is a Koin definition, and Koin does the
 * caching for the `app` scope (`single`). This wrapper owns the *policy* the original
 * `DIContainer` had and Koin has no equivalent for:
 *
 *  - the routing table that says which scope a type belongs to,
 *  - the `session` instance cache, including dropping it on sign-out,
 *  - cycle detection, so a self-referential factory throws
 *    [DiException.CircularDependency] instead of overflowing the stack,
 *  - translation of Koin's exception types into the original's [DiException] cases, and
 *  - the explicit [start] barrier between registering and resolving.
 *
 * `session` is cached here rather than in a Koin `Scope` on purpose. A named Koin scope
 * is only populated through `ScopeRegistry.loadScopes`, which is `@KoinInternalApi`;
 * reaching for it would couple the port to a detail Koin reserves the right to change.
 * The behaviour is the part worth keeping and it is small enough to own outright.
 *
 * Every resolution goes through this type, including resolutions triggered from inside a
 * factory, because factories are handed the [Resolver] rather than the `Koin` instance.
 * That is what makes the cycle check reliable: each factory hop re-enters here and lands
 * on the stack, so the chain is known exactly.
 *
 * Thread confinement: like the original `@MainActor DIContainer`, an instance is meant to
 * be built and driven from one thread. The resolution stack is a plain field for that
 * reason; making it a `ThreadLocal` would hide a real misuse rather than fix it.
 */
class KoinResolver private constructor() : Resolver {

    private val modules: MutableList<Module> = mutableListOf()
    private val scopes: MutableMap<KClass<*>, DiScope> = LinkedHashMap()
    private val sessionInstances: MutableMap<KClass<*>, Any> = LinkedHashMap()
    private val resolutionStack: ArrayDeque<KClass<*>> = ArrayDeque()

    private var application: KoinApplication = koinApplication { }
    private var koinOrNull: Koin? = null

    override var isStarted: Boolean = false
        private set

    override fun start() {
        check(!isStarted) { "DI: the container was already started" }
        rebuild()
        isStarted = true
    }

    override fun <T : Any> resolve(klass: KClass<T>): T {
        val koin = koinOrNull ?: throw DiException.NotStarted()
        val scope = scopes[klass]
            ?: throw DiException.Unregistered(klass.simpleName ?: "<anonymous>")

        // The check is on the type, not the scope, so a cycle that hops between an
        // app-scoped and a session-scoped type is still caught.
        if (klass in resolutionStack) {
            val chain = (resolutionStack + klass)
                .joinToString(" -> ") { it.simpleName ?: "<anonymous>" }
            throw DiException.CircularDependency(chain)
        }

        @Suppress("UNCHECKED_CAST")
        sessionInstances[klass]?.let { return it as T }

        resolutionStack.addLast(klass)
        try {
            val instance: T = koin.get(klass, null, null)
            if (scope == DiScope.Session) sessionInstances[klass] = instance
            return instance
        } catch (e: DiException) {
            throw e
        } catch (e: Throwable) {
            // Koin wraps whatever a factory threw, so a DiException raised by a nested
            // resolve arrives buried under InstanceCreationException. The wrapper's own
            // failure is the useful one; Koin's text on top of it is noise.
            diCauseOf(e)?.let { throw it }
            throw DiException.FactoryFailed(
                klass.simpleName ?: "<anonymous>",
                e.message ?: e::class.simpleName.orEmpty(),
            )
        } finally {
            resolutionStack.removeLast()
        }
    }

    /** Walks the cause chain for a [DiException], so the wrapper's failure is not lost. */
    private fun diCauseOf(error: Throwable): DiException? {
        var current: Throwable? = error
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is DiException) return current
            current = current.cause
        }
        return null
    }

    override fun <T : Any> getOrNull(klass: KClass<T>): T? {
        if (!isStarted || klass !in scopes) return null
        return try {
            resolve(klass)
        } catch (e: DiException.NotStarted) {
            null
        } catch (e: DiException.Unregistered) {
            null
        }
    }

    override fun <T : Any> forceResolve(klass: KClass<T>): T =
        try {
            resolve(klass)
        } catch (e: DiException) {
            // The original used fatalError here: a view model resolves during Compose
            // `body` evaluation, where a thrown exception surfaces as an opaque crash.
            // Failing with the registration name keeps the diagnostic.
            throw DiFatal("DI: cannot resolve ${klass.simpleName}: ${e.message}")
        }

    override fun resetSessionScope() {
        // Registrations are untouched: the next resolve rebuilds from the same factory,
        // which is the point of dropping the instance rather than the recipe.
        sessionInstances.clear()
    }

    override fun invalidateInstances() {
        sessionInstances.clear()
        application.close()
        // Registrations survive in [modules] and [scopes]; only the built instances go.
        // Rebuilding is the honest way to say "forget every app-scoped singleton" while
        // keeping the graph: the recipes are re-installed, nothing is re-registered.
        application = koinApplication { }
        if (isStarted) rebuild()
    }

    override fun stop() {
        application.close()
        koinOrNull = null
        sessionInstances.clear()
        isStarted = false
    }

    override fun isRegistered(klass: KClass<*>): Boolean = klass in scopes

    override val registeredNames: List<String>
        get() = scopes.keys.map { it.simpleName ?: "<anonymous>" }.sorted()

    private fun rebuild() {
        application.modules(modules)
        application.createEagerInstances()
        koinOrNull = application.koin
    }

    /** @suppress called only from [ResolverBuilder], whose inline helpers need it. */
    @PublishedApi
    internal fun record(klass: KClass<*>, scope: DiScope) {
        check(klass !in scopes) {
            "DI: ${klass.simpleName} is already registered. Duplicate registrations usually " +
                "mean a feature assembly ran twice."
        }
        scopes[klass] = scope
    }

    /** @suppress called only from [ResolverBuilder], whose inline helpers need it. */
    @PublishedApi
    internal fun addModule(module: Module) {
        modules += module
    }

    companion object {
        /**
         * Builds a stopped [Resolver]. Call [Resolver.start] once the graph is fully
         * described and the process is ready to resolve.
         */
        fun build(configure: ResolverBuilder.() -> Unit): Resolver =
            KoinResolver().also { ResolverBuilder(it).configure() }
    }
}

/** Accumulates Koin modules and the scope routing table while a [Resolver] is built. */
class ResolverBuilder internal constructor(
    @PublishedApi internal val resolver: KoinResolver,
) {
    /**
     * One instance for the whole process. For stateless services and stores that own
     * long-lived state, such as the database and the token vault.
     */
    inline fun <reified T : Any> app(noinline of: (Resolver) -> T) {
        resolver.record(T::class, DiScope.App)
        resolver.addModule(module { single<T> { of(resolver) } })
    }

    /**
     * One instance per session, dropped by [Resolver.resetSessionScope] on sign-out.
     *
     * Caching is what makes the drop possible: a scope that built a fresh instance per
     * resolution would have nothing to discard, which is exactly the failure the
     * original `DIContainer` doc comment records.
     */
    inline fun <reified T : Any> session(noinline of: (Resolver) -> T) {
        resolver.record(T::class, DiScope.Session)
        // Registered with Koin as an uncached factory; the wrapper caches it for the
        // lifetime of the session. See KoinResolver's kdoc.
        resolver.addModule(module { factory<T> { of(resolver) } })
    }

    /** A fresh instance per resolution, never cached. For value-like helpers. */
    inline fun <reified T : Any> transient(noinline of: (Resolver) -> T) {
        resolver.record(T::class, DiScope.Transient)
        resolver.addModule(module { factory<T> { of(resolver) } })
    }
}

/** A registration's lifetime, mirroring the original `Scope` enum. */
enum class DiScope { App, Session, Transient }

/**
 * A wiring bug that must not be swallowed by a `body` evaluation.
 *
 * [Resolver.forceResolve] throws this instead of returning a fallback, matching the
 * original's `fatalError`: returning anything else would let a mis-wired view model
 * render an empty screen and the real cause would be gone.
 */
class DiFatal(message: String) : Error(message)
