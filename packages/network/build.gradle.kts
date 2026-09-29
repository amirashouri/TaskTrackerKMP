plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonTestImplementation(libs.ktor.client.mock)

    // Ktor is an API, not an engine: ktor-client-core alone cannot open a socket, and the
    // engine is not a common dependency because the transport is the one thing that
    // genuinely differs per platform. Darwin's engine is Foundation's URLSession — the
    // original's transport, unchanged — and OkHttp's is the JVM/Android HTTP stack that
    // AndroidX already assumes. Each lives in the source set that actually runs it, and
    // tests inject MockEngine rather than either, so no test depends on a real socket.
    add("iosMainImplementation", libs.ktor.client.darwin)
    add("androidMainImplementation", libs.ktor.client.okhttp)
    add("jvmMainImplementation", libs.ktor.client.okhttp)

    // ktor-client-content-negotiation is deliberately absent. The original encoded and
    // decoded through its own JSONCoding, and so does this: ContentNegotiation would be a
    // second JSON configuration, with its own leniency settings, one call away from the
    // strict one that makes AppError.Decoding reachable. Same reasoning as omitting
    // ktor-client-logging — it would be a second path to the log that bypasses the
    // redaction in :packages:core's logMessage builder, and this module logs the two
    // events that matter through TTLogger instead.
}
