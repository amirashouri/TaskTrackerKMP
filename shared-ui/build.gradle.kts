plugins {
    id("tasktracker.kmp.library")
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:routing"))
    commonMainImplementation(project(":packages:di"))
    commonMainImplementation(project(":composition-root"))
    commonMainImplementation(project(":packages:feature-projects-presentation"))
    commonMainImplementation(project(":packages:feature-auth-presentation"))
    commonMainImplementation(project(":packages:feature-settings-presentation"))
}

// The single framework the SwiftUI Liquid Glass shell links against. iOS entry
// points (MainViewController / ScreenViewController / TabRootViewController)
// are exported from here; Swift never sees a Gradle module, only this.
kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
}
