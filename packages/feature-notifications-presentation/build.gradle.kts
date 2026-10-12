plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:routing"))
    commonMainImplementation(project(":packages:di"))
    commonMainImplementation(project(":packages:feature-notifications-domain"))
}