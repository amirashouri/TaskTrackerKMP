plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:sync"))
    commonMainImplementation(project(":packages:network"))
    commonMainImplementation(project(":packages:keychain"))
    // The cached profile, and nothing else: `AuthRepository` writes the signed-in identity
    // to user defaults so the shell has something to render before the database is opened.
    // Secrets stay in the keychain, which is the split `DefaultsKey.CachedProfile` names.
    commonMainImplementation(project(":packages:user-defaults"))
    commonMainImplementation(project(":packages:feature-auth-domain"))

    // Declared here, as every other module that touches them does, rather than exported from
    // the modules that own them. `TokenVault` serialises the two locks that collapse
    // concurrent refreshes, and `HttpAuthRemote`'s private wire shapes are
    // `@Serializable` — `:packages:core` carries both as `implementation`, so neither
    // reaches this module's compile classpath through it.
    //
    // Neither is in the "may depend on" list of `verifyArchitectureGraph` and neither is
    // covered by the import guard, and that is the right answer for both: this module is the
    // adapter for those two seams, so naming them is its job rather than an exception to the
    // layering.
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
}
