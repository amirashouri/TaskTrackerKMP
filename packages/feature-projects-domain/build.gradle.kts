plugins {
    id("tasktracker.kmp.library")
}

/**
 * Android resource processing, which `com.android.kotlin.multiplatform.library` leaves
 * **off** by default — a library that ships no `res/` pays nothing for a merge step and an
 * `R` class it never reads.
 *
 * This module is the one that needs it: `AttachmentOpener.android` hands a file to another
 * app through a `FileProvider`, and a provider is declared with
 * `android:resource="@xml/attachment_handoff_paths"` — a resource reference, not a path
 * string. Without this block the manifest merges and then fails to link, which is what the
 * first attempt did: `AAPT: error: resource xml/attachment_handoff_paths not found`.
 *
 * Enabled here rather than in the convention plugin for the reason that plugin exists — the
 * other eighteen modules declare dependencies and nothing else, and turning resource merging
 * on for all of them would be a build cost for a capability one of them uses.
 */
kotlin {
    android {
        androidResources {
            enable = true
        }
    }
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.datetime)

    commonTestImplementation(libs.kotlinx.coroutines.test)

    // `FilePicker.android` registers on the foreground activity's `ActivityResultRegistry`
    // — the Activity Result API, which lives in androidx.activity rather than the SDK.
    // A library edge, so `allowedLibraryTable` is untouched: `verifyArchitectureGraph`
    // filters `ProjectDependency`.
    androidMainImplementation(libs.androidx.activity.compose)

    // `AttachmentOpener.android` hands a file to another app through a `FileProvider`,
    // which is androidx.core's and not the SDK's. Same reasoning as the edge above.
    androidMainImplementation(libs.androidx.core.ktx)
}
