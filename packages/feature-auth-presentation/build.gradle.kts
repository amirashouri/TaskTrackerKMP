plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    // `:packages:domain`, `:packages:routing` and `:packages:di` are gone, where the sibling
    // `feature-settings-presentation` still carries them. None of the three is reachable
    // from this module's code, and an allowed dependency that nothing uses is a permission
    // rather than a constraint: the next change that reaches for `:packages:routing` would
    // compile, and the point of the table is that it would not.
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:feature-auth-domain"))
}
