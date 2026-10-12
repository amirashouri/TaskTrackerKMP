plugins {
    id("tasktracker.kmp.library")
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
}
