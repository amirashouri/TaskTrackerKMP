pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "TaskTrackerKMP"

// --- 19 library modules, 1:1 with the 19 SwiftPM targets -------------------
// SwiftPM target boundaries are what enforce the architecture in the original.
// A Gradle module is the compiler-enforced equivalent, so the port keeps one
// module per original target rather than collapsing by package.
//
// Feature modules are flat (`feature-projects-domain`, not
// `feature-projects:domain`) because *project names must be unique across the
// whole build*. Two projects sharing a simple name — `:packages:domain` and
// `:packages:feature-projects:domain` — make AGP 9 report a bogus task cycle,
// `bundleAndroidMainClassesToCompileJar <-> compileAndroidMain`, in the second
// one, which then fails the entire build. Flattening keeps every name unique
// while still naming each module after the SwiftPM target it replaces.

include(":packages:core")
include(":packages:domain")
include(":packages:design-system")
include(":packages:routing")
include(":packages:database")
include(":packages:network")
include(":packages:sync")
include(":packages:keychain")
include(":packages:user-defaults")
include(":packages:di")

include(":packages:feature-projects-domain")
include(":packages:feature-projects-data")
include(":packages:feature-projects-presentation")

include(":packages:feature-auth-domain")
include(":packages:feature-auth-data")
include(":packages:feature-auth-presentation")

include(":packages:feature-settings-domain")
include(":packages:feature-settings-data")
include(":packages:feature-settings-presentation")

include(":packages:feature-teams-domain")
include(":packages:feature-teams-data")
include(":packages:feature-teams-presentation")

include(":packages:feature-notifications-domain")
include(":packages:feature-notifications-data")
include(":packages:feature-notifications-presentation")

// --- 4 new modules ----------------------------------------------------------
// AGP 9 forbids the Android application plugin inside a multiplatform module,
// so the Android entry point cannot live in :shared-ui. :composition-root is
// separate so the object graph stays the one module allowed to see everything.

include(":composition-root")
include(":shared-ui")
include(":androidApp")

