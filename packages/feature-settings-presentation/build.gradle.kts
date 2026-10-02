plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:routing"))
    commonMainImplementation(project(":packages:di"))
    commonMainImplementation(project(":packages:feature-settings-domain"))

    // For `observe(scope)` on `SettingsModel` and the `Flow` types the port hands back.
    commonMainImplementation(libs.kotlinx.coroutines.core)

    commonTestImplementation(libs.kotlinx.coroutines.test)
}
