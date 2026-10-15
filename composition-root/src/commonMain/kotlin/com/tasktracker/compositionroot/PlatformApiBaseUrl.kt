package com.tasktracker.compositionroot

/**
 * The API root this build talks to, read from the platform's own configuration surface.
 *
 * ## Why the composition root owns this
 *
 * It is the one module allowed to name platform APIs, and every configuration surface here is
 * a platform API — an environment variable on the JVM, `NSBundle` on iOS, `PackageManager` on
 * Android. Reading it in a shell would put a platform call in a module that exists precisely so
 * platform calls stay out, and would give each shell its own copy of the same decision. One
 * function, called by all three entry points, is what makes "the API root" a single thing.
 *
 * ## Every platform names a real backend, and the fallback is the local server
 *
 * A returned URL binds the real `HttpAuthRemote` / `HttpSyncTransport` pair; there is no
 * "unset" answer here. Each platform falls back to the address a developer running the app
 * beside the local Go server needs — `http://localhost:8080` where the host network is shared
 * (JVM, iOS simulator) and `http://10.0.2.2:8080` on Android, where the emulator reaches its
 * host through that alias. A real deployment overrides the value through its platform's own
 * mechanism, listed on each `actual`.
 *
 * The offline demo is therefore no longer what a launched app gets. It stays reachable by
 * calling [AppGraph.start] with `apiBaseUrl = null` directly, which is what the graph's own
 * tests do, but no entry point takes that path any more.
 *
 * [host] carries the Android `Context`; other platforms ignore it. Typed `Any?` because common
 * code cannot name `Context`, matching [platformAppVersion].
 */
expect fun platformApiBaseUrl(host: Any? = null): String

/**
 * Where the local Go server listens, and the fallback on every platform whose host network is
 * shared with it: the JVM and the iOS simulator both reach the host's `localhost` directly.
 *
 * Android does **not** use this: an emulator's `localhost` is the emulator itself, so its
 * fallback is the host alias `10.0.2.2`. See `PlatformApiBaseUrl.android.kt`.
 */
internal const val DEFAULT_LOCAL_API_BASE_URL: String = "http://localhost:8080"
