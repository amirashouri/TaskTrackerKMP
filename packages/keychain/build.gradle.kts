plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonTestImplementation(libs.kotlinx.coroutines.test)
}
