plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonTestImplementation(libs.kotlinx.coroutines.test)

    // The macOS keychain actual. `Security.framework` is a C API with no JDK binding, so
    // the Desktop keychain reaches it through JNA rather than being an in-memory stub.
    // `jvmMain` only: Android has `AndroidKeyStore`, and iOS uses the framework directly.
    //
    // A **library** edge, so `allowedLibraryTable` and `packageOwnersTable` are untouched:
    // `verifyArchitectureGraph` filters `ProjectDependency`, and neither it nor
    // `verifyModuleImports` reads a `libs.` coordinate.
    jvmMainImplementation(libs.jna)
}
