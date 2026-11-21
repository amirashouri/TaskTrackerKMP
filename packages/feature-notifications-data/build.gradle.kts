plugins {
    id("tasktracker.kmp.library")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:sync"))
    commonMainImplementation(project(":packages:feature-notifications-domain"))
    commonMainImplementation(libs.room.runtime)
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
}

/**
 * The android target's host unit tests are compiled but not run.
 *
 * Every test in this module opens an in-memory store, and Room's Android builder needs a
 * real `Context` — the `android.jar` on the host unit-test classpath is a stub whose
 * constructors throw. The same caveat, and the same reasoning, as `:packages:database`
 * and `:packages:feature-projects-data`. The android target is still fully compiled, so
 * a mistake in the android actual breaks the build; only its runtime is unproven here.
 */
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        enabled = false
    }
}