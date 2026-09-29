package com.tasktracker.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * The JVM engine is OkHttp.
 *
 * `ktor-client-cio` would be the smaller JVM-only option, but the Android target needs
 * OkHttp anyway — it is the engine the rest of the Android HTTP stack assumes — and one
 * engine for both JVM and Android means a test run on the host is exercising the same
 * transport the app will.
 *
 * `OkHttp.create {}` rather than `HttpClient(OkHttp).engine`: the engine *factory* is what
 * the platform hands over, and a `HttpClient` built here would be a second one, with its
 * own plugin set, that nothing ever uses.
 */
actual fun createDefaultHttpEngine(): HttpClientEngine = OkHttp.create { }
