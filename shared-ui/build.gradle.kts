import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("tasktracker.kmp.library")
    id("tasktracker.kmp.compose")
}

// The installer version, and only the installer version. `jpackage` validates it per
// format — Windows wants three numeric parts, and Debian wants a leading digit — so the
// release workflow strips the tag's leading `v` and passes the rest here as
// `-PtasktrackerVersion=0.1.6`. The fallback keeps a plain
// `./gradlew :shared-ui:packageDmg` working on a developer machine.
val tagVersion = (findProperty("tasktrackerVersion") as String? ?: "0.0.0").removePrefix("v")

// macOS rejects a zero major outright: jpackage stops with "The first number in an
// app-version cannot be zero or negative", which is every tag this project has cut so
// far (v0.1.3, v0.1.6). A zero major is bumped to 1 and the rest is left alone, so
// `v0.1.6` packages as `1.1.6`: valid for `CFBundleShortVersionString`, still ordered
// across releases, and the release asset is renamed back to the tag by the workflow.
// The desktop About row reads its version from the bundle only on Android, so nothing
// in the app displays this number.
val desktopPackageVersion = tagVersion.split(".").let { parts ->
    if (parts.first() == "0") (listOf("1") + parts.drop(1)).joinToString(".") else tagVersion
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
    // For `TeamPort`, resolved and handed to the projects environment.
    commonMainImplementation(project(":packages:feature-teams-domain"))
    commonMainImplementation(project(":packages:feature-teams-presentation"))

    // For `NotificationPort`: the inbox screen asks for the port, and the shell provides it.
    commonMainImplementation(project(":packages:feature-notifications-domain"))
    commonMainImplementation(project(":packages:feature-notifications-presentation"))

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

    // For `BackHandler`: system back is a shell concern — pop exactly one
    // route, never leave the tab — which is a rule about the shell's own
    // stacks and not about any feature screen.
    //
    // A **library** edge, so `allowedLibraryTable` and `packageOwnersTable` are
    // untouched: `verifyArchitectureGraph` filters `ProjectDependency`, and neither
    // it nor `verifyModuleImports` reads a `libs.` coordinate.
    commonMainImplementation(libs.compose.backhandler)
    // `BackHandler`'s POM carries `navigationevent-compose` at *runtime*
    // scope, which keeps it off the compile classpath — and the shell
    // imports `LocalNavigationEventDispatcherOwner` to hand the handler a
    // dispatcher owner. Same library-edge reasoning as the line above.
    commonMainImplementation(libs.navigationevent.compose)

    // The Desktop app classpath. `compose.desktop.currentOs` is already on the
    // `jvmTest` classpath; the Desktop *app* classpath is the one that does not have
    // it yet. Added here (not re-added only to `jvmTest`) so the entry point in
    // `jvmMain` compiles against the Desktop runtime, and so the existing JVM test
    // path is not the only place this dependency ever gets exercised.
    //
    // A library edge, so `allowedLibraryTable` and `packageOwnersTable` are
    // untouched: `verifyArchitectureGraph` filters `ProjectDependency`, and neither
    // it nor `verifyModuleImports` reads a `libs.` coordinate.
    jvmMainImplementation(compose.desktop.currentOs)
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

// Compose Hot Reload — and with it Android Studio's auto-generated "shared-ui"
// run configuration — launches `hotRunJvm`, not `runDesktop` below. That task
// resolves its entry point from (highest first) `--mainClass`/`-PmainClass`,
// then `-Dcompose.mainClass`, then this `application` block; with none set it
// aborts with "Missing 'mainClass' property". Naming the entry point here, in
// the one place the Compose Gradle plugin reads it, is what makes the AS Run
// button (and `./gradlew :shared-ui:hotRunJvm`) work at all.
compose.desktop {
    application {
        mainClass = "com.tasktracker.sharedui.DesktopMainKt"

        // The packaged macOS .app icon. Without this, Finder and the Dock show the
        // default Java icon instead of the TaskTracker mark. The source PNG is the
        // 1024px render from tools/generate_app_icon.py; iconutil packed it into
        // AppIcon.icns at shared-ui/src/jvmMain/resources/AppIcon.icns.
        nativeDistributions {
            // One packaging task is registered per *declared* format, and Compose 1.12
            // defaults this set to empty: without this line `packageDmg` (and
            // `packageDeb` / `packageMsi`) do not exist at all — the build exposes only
            // the deprecated `package` alias and a `packageDistributionForCurrentOS`
            // that depends on nothing, so the release job fails with
            // "task 'packageDmg' not found in project ':shared-ui'".
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // Both feed `jpackage --name/--version`, and therefore both the installer
            // file name and the `.app` bundle name. Left unset they fall back to the
            // module name, which is how a `.dmg` would have shipped as
            // `shared-ui-1.0.0.dmg`. Pinned so the release asset is
            // `TaskTracker-<version>.dmg` and TaskTracker.app is what the user drags
            // to /Applications.
            packageName = "TaskTracker"
            packageVersion = desktopPackageVersion
            macOS {
                iconFile = file("src/jvmMain/resources/AppIcon.icns")
            }
            windows {
            }
            linux {
            }
        }
    }
}

// The Desktop run task. The Desktop entry point exists in `jvmMain`, but without
// this task Gradle knows how to compile it and not how to launch it. This is the
// Desktop equivalent of `:androidApp:installDebug` and of Xcode's build-and-run:
// one task, one launchable window, no manual classpath wiring.
//
// Implementation: a plain `JavaExec`. The plan left the choice open between this
// and Compose's own utility, and `JavaExec` is the one that does not depend on a
// Compose Gradle plugin utility landing in the right form for this build — it
// points directly at the `jvmMain` runtime classpath the entry point already
// compiles against.
tasks.register<JavaExec>("runDesktop") {
    group = "run"
    description = "Launches the Compose Desktop shell (shared-ui jvmMain DesktopMainKt)"
    mainClass.set("com.tasktracker.sharedui.DesktopMainKt")
    classpath = sourceSets.named("jvmMain").get().runtimeClasspath
    standardInput = System.`in`
}
