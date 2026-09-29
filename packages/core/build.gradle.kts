plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(libs.kotlinx.atomicfu)
}
