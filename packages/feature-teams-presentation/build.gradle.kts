plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:feature-teams-domain"))

    // For `UserPosition.all`, the job titles the add-member form offers. A deliberate
    // cross-feature read of a vocabulary this module does not own — the same shape as
    // `feature-projects-presentation`'s edge to `feature-teams-domain`. The server stores the
    // position as free text, so this is the client's list of suggestions rather than a closed
    // set, and it already exists beside the shape that first needed it.
    commonMainImplementation(project(":packages:feature-settings-domain"))

    // For `observe(scope)` on `MemberAdminModel`.
    commonMainImplementation(libs.kotlinx.coroutines.core)

    commonTestImplementation(libs.kotlinx.coroutines.test)
}
