package com.tasktracker.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android shares the JVM engine.
 *
 * `OkHttp.create {}` is the only honest option on Android: the default engine factory for
 * a KMP Android target is the CIO/OkHttp pair, and OkHttp is the one AndroidX expects.
 */
actual fun createDefaultHttpEngine(): HttpClientEngine = OkHttp.create { }
