plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(libs.koin.core)

    commonTestImplementation(libs.koin.test)
}
