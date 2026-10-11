plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    // `User` is `@Serializable` for the same reason `UserIdentity` is: the directory has a
    // stable wire form rather than a class layout. The serialization plugin is applied to
    // every module by `tasktracker.kmp.library`, but the runtime library is not, so it is
    // named here — mirroring `feature-auth-domain`.
    commonMainImplementation(libs.kotlinx.serialization.json)
}
