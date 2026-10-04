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
    // For `ProjectRepositoryPort` only: the shell resolves the port to build the
    // `ProjectEnvironment`, and the environment is typed on the port rather than on the Room
    // repository, so this is the edge that keeps a `:packages:feature-projects-data` import
    // from compiling here. `verifyArchitectureGraph` pins that pairing.
    commonMainImplementation(project(":packages:feature-projects-domain"))
    commonMainImplementation(project(":packages:feature-auth-domain"))
    commonMainImplementation(project(":packages:feature-auth-presentation"))

    // For `UserDefaultsStore`: the shell owns the persisted appearance and the last-visited
    // project, because both are properties of the *device* rather than of any feature. The
    // theme key in particular cannot go through the settings port - it has to be applied
    // before the settings screen exists, and `UserDefaultsStore.removeAll` runs on sign-out,
    // so an appearance stored as a `DefaultsKey` member would be wiped with the session.
    commonMainImplementation(project(":packages:user-defaults"))

    // For `SettingsPort`: the settings screen asks for the port, and the shell is what
    // provides it. Named here because the navigation host hands a subtree a
    // `SettingsEnvironment`; `:packages:feature-settings-data` deliberately is not an edge,
    // which is what keeps the screen unable to reach the live service.
    commonMainImplementation(project(":packages:feature-settings-domain"))
    commonMainImplementation(project(":packages:feature-settings-presentation"))

    // For `LocalDate`, and only for that: the sprint editor sheet is typed on the calendar
    // type `:packages:design-system` chose in 23.4, and `SprintEditorModel` holds its draft
    // in that type, so *resolving the call* needs the class here even though no import
    // names it. Kotlin resolves a signature before it type-checks an argument, so an
    // `implementation` dependency one module down is not enough — and the alternative,
    // having the shell re-declare its own date type, would put two notions of "a day" in
    // the app.
    //
    // A **library** edge, so `allowedLibraryTable` and `packageOwnersTable` are untouched:
    // `verifyArchitectureGraph` filters `ProjectDependency`, and neither it nor
    // `verifyModuleImports` reads a `libs.` coordinate.
    commonMainImplementation(libs.kotlinx.datetime)
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
