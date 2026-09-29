plugins {
    id("tasktracker.kmp.room")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
}
