plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(libs.ktor.client.core)
    commonMainImplementation(libs.ktor.client.content.negotiation)
    commonMainImplementation(libs.ktor.serialization.json)
    commonMainImplementation(libs.ktor.client.logging)
    commonTestImplementation(libs.ktor.client.mock)

    // Ktor is an API, not an engine: ktor-client-core alone cannot open a socket, and the
    // engine is not a common dependency because the transport is the one thing that
    // genuinely differs per platform. Darwin's engine is Foundation's URLSession and
    // OkHttp's is a JVM/Android HTTP stack, so each lives in the source set that actually
    // runs it rather than in commonMain behind a factory.
    add("iosMainImplementation", libs.ktor.client.darwin)
    add("androidMainImplementation", libs.ktor.client.okhttp)
    add("jvmMainImplementation", libs.ktor.client.okhttp)
}
