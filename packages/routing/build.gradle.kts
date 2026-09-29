plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonTestImplementation(libs.kotlinx.coroutines.test)
}
