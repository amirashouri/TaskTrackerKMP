plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    // `SettingsPort.connectivityUpdates` and `syncUpdates` are `Flow`s. `:packages:core`
    // carries coroutines as `implementation`, so it is not re-exported here.
    commonMainImplementation(libs.kotlinx.coroutines.core)

    commonTestImplementation(libs.kotlinx.coroutines.test)
}
