plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:sync"))
    // `LiveSettingsService` is the adapter between the platform connectivity watcher and the
    // settings feature's own vocabulary, so it names `NetworkMonitor` and `Connectivity`.
    commonMainImplementation(project(":packages:network"))
    commonMainImplementation(project(":packages:feature-settings-domain"))
    commonMainImplementation(project(":packages:user-defaults"))

    // `:packages:core` carries coroutines as `implementation`, so the `Flow` types in the
    // port's signatures are not re-exported to this module's compile classpath.
    commonMainImplementation(libs.kotlinx.coroutines.core)

    commonTestImplementation(libs.kotlinx.coroutines.test)
    commonTestImplementation(project(":packages:user-defaults"))
}
