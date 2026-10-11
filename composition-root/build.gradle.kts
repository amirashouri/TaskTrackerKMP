plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:routing"))
    commonMainImplementation(project(":packages:di"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:network"))
    commonMainImplementation(project(":packages:sync"))
    commonMainImplementation(project(":packages:keychain"))
    commonMainImplementation(project(":packages:user-defaults"))
    commonMainImplementation(project(":packages:feature-projects-domain"))
    commonMainImplementation(project(":packages:feature-projects-data"))
    commonMainImplementation(project(":packages:feature-projects-presentation"))
    commonMainImplementation(project(":packages:feature-auth-domain"))
    commonMainImplementation(project(":packages:feature-auth-data"))
    commonMainImplementation(project(":packages:feature-auth-presentation"))
    commonMainImplementation(project(":packages:feature-settings-domain"))
    commonMainImplementation(project(":packages:feature-settings-data"))
    commonMainImplementation(project(":packages:feature-settings-presentation"))
    commonMainImplementation(project(":packages:feature-teams-domain"))
    commonMainImplementation(project(":packages:feature-teams-data"))
}
