plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    // `SettingsPort.connectivityUpdates` and `syncUpdates` are `Flow`s. `:packages:core`
    // carries coroutines as `implementation`, so it is not re-exported here.
    commonMainImplementation(libs.kotlinx.coroutines.core)
    // `UserDetails` and `UserPosition` are @Serializable so they can be persisted via
    // `UserDefaultsStore`; `:packages:core` carries `kotlinx.serialization` as `implementation`,
    // not `api`, so this module cannot reach it through that edge.
    commonMainImplementation(libs.kotlinx.serialization.json)

    commonTestImplementation(libs.kotlinx.coroutines.test)
}
