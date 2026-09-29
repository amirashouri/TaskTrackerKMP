plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:sync"))
    commonMainImplementation(project(":packages:network"))
    commonMainImplementation(project(":packages:keychain"))
    commonMainImplementation(project(":packages:feature-auth-domain"))
}
