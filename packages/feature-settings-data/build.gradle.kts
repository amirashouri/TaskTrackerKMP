plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:sync"))
    commonMainImplementation(project(":packages:feature-settings-domain"))
}
