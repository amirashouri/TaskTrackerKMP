plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    // `AuthSessionPort.sessionUpdates` is a `Flow`, and `UserIdentity` is `@Serializable`
    // because the data layer caches it in user defaults. `:packages:core` carries both as
    // `implementation`, so they are not re-exported to this module's compile classpath.
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
}
