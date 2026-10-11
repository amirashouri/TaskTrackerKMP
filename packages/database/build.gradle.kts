plugins {
    id("tasktracker.kmp.room")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    // Room's transaction primitives are suspend, and so is every DAO method.
    commonMainImplementation(libs.kotlinx.coroutines.core)
}

/**
 * The android target's host unit tests are compiled but not run.
 *
 * Room's Android builder is the classic one — `databaseBuilder(context, klass, name)` — so
 * opening any store, in memory or not, needs a real `Context`. A host JVM unit test has no
 * way to produce one: the `android.jar` on the unit-test classpath is a stub whose methods
 * and constructors throw `RuntimeException("Stub!")`, so a hand-rolled `ContextWrapper`
 * would fail just as hard as no context at all. Robolectric would fix it and is the right
 * answer if this module ever wants host-side Android coverage, but it is a new dependency
 * and a download-at-test-time one, which is not a decision to slip into a phase about
 * transaction semantics.
 *
 * Every test in this module opens a store, so leaving the task enabled fails the whole
 * suite for one wiring reason and hides whether the behaviour is right — which is the worse
 * outcome. The android target is still fully compiled, including Room's generated code for
 * it, so a mistake in the android actual breaks the build; only its *runtime* is unproven,
 * and that is the same caveat the keychain and user-defaults platform actuals carry until
 * Phase 14 runs a real device.
 */
// Matched by name rather than by `tasks.named`, which fails at configuration time because
// the AGP plugin registers the task later than a build script is evaluated.
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        enabled = false
    }
}
