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
    commonMainImplementation(project(":packages:feature-projects-domain"))

    // Declared here, as every other module that touches them does, rather than exported from
    // the modules that own them. Neither is an accidental leak: the repository names
    // `TaskTrackerDatabase`, whose supertype is `androidx.room.RoomDatabase`, so the compiler
    // cannot resolve the class without Room on the classpath even though no file in this
    // module imports a Room type; and `JSONCoding.json` is a `kotlinx.serialization.json.Json`,
    // which the outbox payloads and the sync sink are encoded and decoded with.
    //
    // Neither is in the "may depend on" list of `verifyArchitectureGraph` and neither is
    // covered by the import guard, and that is the right answer for both: this module is the
    // persistence adapter *for* this store, so reaching the store and the wire format is its
    // job rather than an exception to the layering.
    commonMainImplementation(libs.room.runtime)
    commonMainImplementation(libs.kotlinx.serialization.json)
}

/**
 * The android target's host unit tests are compiled but not run.
 *
 * Every test in this module opens a store, and Room's Android builder needs a real
 * `Context` to do it. A host JVM unit test cannot produce one: the `android.jar` on the
 * unit-test classpath is a stub whose constructors throw `RuntimeException("Stub!")`, so
 * there is nothing to hand `inMemoryDatabaseBuilder`. The same caveat, and the same
 * reasoning, as `:packages:database` — including that leaving the task enabled would fail
 * the whole suite for one wiring reason and hide whether the behaviour is right.
 *
 * The android target is still fully compiled, so a mistake in an android actual breaks the
 * build; only its *runtime* is unproven here, and Phase 14 closes that gap on a device.
 */
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        enabled = false
    }
}
