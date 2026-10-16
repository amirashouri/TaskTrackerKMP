plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:database"))
    // The admin member API's transport. `MemberAdminRepository` implements the domain's
    // `MemberAdminPort` by composing it with the session-backed `TeamPort` for reads, so the
    // write half needs the client `:packages:network` exposes.
    commonMainImplementation(project(":packages:network"))
    commonMainImplementation(project(":packages:feature-teams-domain"))

    // Named rather than exported from `:packages:core`, which is what every module that
    // declares a `@Serializable` wire shape does: core carries the runtime as
    // `implementation`, so it does not reach this module's compile classpath through it, and
    // `HttpMemberAdminRemote`'s private DTOs are `@Serializable`.
    commonMainImplementation(libs.kotlinx.serialization.json)
}
