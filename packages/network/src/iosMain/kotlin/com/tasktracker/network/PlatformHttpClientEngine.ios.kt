package com.tasktracker.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS runs on Foundation's `URLSession`, which is Darwin engine.
 *
 * Worth stating because it is the same engine as the original's, and it is not a
 * coincidence: `URLSession` handles ATS, the system proxy, background sessions and
 * `NWPathMonitor`-drated reachability, and a hand-rolled engine would have to reimplement
 * all four to match what the app already did on iOS.
 */
actual fun createDefaultHttpEngine(): HttpClientEngine = Darwin.create { }
