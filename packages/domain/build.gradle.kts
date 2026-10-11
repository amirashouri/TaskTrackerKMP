plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    // Declared here rather than inherited from `:packages:core`: a KMP
    // `commonMainImplementation` dependency is not `api`, so core's own
    // serialization dependency is not on this module's compile classpath. Without
    // this, `@Serializable` on a domain entity does not resolve.
    commonMainImplementation(libs.kotlinx.serialization.json)
    // Recurrence's next-occurrence rule is calendar arithmetic — day-of-week
    // steps, month-length clamping — and `kotlin.time.Instant` alone cannot do
    // any of it. Declared here rather than relying on a transitive edge because
    // `commonMainImplementation` is not `api`.
    commonMainImplementation(libs.kotlinx.datetime)
}
