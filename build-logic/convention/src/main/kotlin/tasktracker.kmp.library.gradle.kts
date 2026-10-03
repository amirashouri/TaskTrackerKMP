/**
 * Base convention for every shared library module in TaskTrackerKMP.
 *
 * There is exactly one convention plugin per *kind* of module (library / room /
 * compose) rather than one per module, so a module's build file stays a
 * declaration of its dependencies and nothing else. The architecture is
 * enforced by which modules declare which dependencies, not by convention
 * plugins that would hide them.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // Applied to every library rather than per-module, for the same reason there is
    // one convention plugin per kind of module: a module's build file is a
    // declaration of dependencies and nothing else. The plugin is inert until a
    // source file actually carries @Serializable, so the modules that do not use it
    // pay only a resolved classpath entry.
    //
    // It has to live here rather than be aliased per module because the wire shapes
    // span layers: an @Serializable DTO written in :packages:network is decoded by
    // :packages:sync, and a data class in :packages:database is encoded for the
    // outbox payload. Per-module opt-in would make "is this module serializable" a
    // thing each build file has to remember.
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Namespace is derived from the Gradle path so all 19 modules stay consistent
// without 19 hand-written declarations that could drift.
//   :packages:feature-projects-domain -> com.tasktracker.featureprojectsdomain
// The path is flattened (hyphens dropped, not turned into separators) because
// module names are already unique across the build, and this keeps the Android
// namespace identical to the original SwiftPM target name, lowercased.
//   See settings.gradle.kts: project names must be globally unique. AGP 9
//   reports two same-named sibling projects as a bogus
//   bundleAndroidMainClassesToCompileJar <-> compileAndroidMain cycle.
// Named moduleNamespace rather than namespace: inside the androidLibrary
// lambda the receiver's own `namespace` property shadows an outer `namespace`,
// so the shorter name would silently assign the property to itself.
val moduleNamespace: String =
    providers.gradleProperty("tasktracker.namespace").orNull
        ?: "com.tasktracker." + project.path
            .removePrefix(":")
            .removePrefix("packages:")
            .replace("-", "")
            .replace(":", ".")
            .lowercase()
            .trim('.')

group = "com.tasktracker"
version = "1.0.0"

kotlin {
    jvmToolchain(25)

    // `android` rather than the older `androidLibrary` block: KGP 2.3.20 deprecates
    // `androidLibrary` in favour of `android` for the KMP Android target.
    android {
        namespace = moduleNamespace
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        // commonTest exists so the same test runs on the JVM and on iOS; without this the
        // Android target would silently skip it, and an expect/actual pair that is wrong on
        // Android would only fail on the two other hosts.
        withHostTest {
            // Robolectric resolves the host's resources through the merged test APK, and
            // without them a Compose UI test on this target cannot render. Harmless for the
            // plain JVM unit tests that make up the rest of the host suite: they read no
            // resources, so the merged manifest simply sits unused.
            isIncludeAndroidResources = true
        }
    }

    // A JVM target is not part of the shipped app; it exists so commonTest runs
    // on the host in seconds instead of requiring a simulator or emulator. Every
    // future-phase use-case and domain test runs here, so this is the project's
    // fast feedback loop.
    jvm()

    // iosX64 is intentionally absent: Compose Multiplatform 1.12.1 publishes
    // no iosX64 variants (Apple dropped the Intel simulator, and CMP followed),
    // so declaring the target would make every native dependency unresolvable.
    iosArm64()
    iosSimulatorArm64()

    // The shared `iosMain` / `androidMain` / `jvmMain` source sets are created here
    // explicitly rather than left to the plugin's implicit default.
    //
    // Without this call the plugin registers only per-target source sets -
    // `iosArm64Main`, `iosSimulatorArm64Main`, `jvmMain` - with no `iosMain` at all. That
    // fails silently in the worst possible way: a file in `src/iosMain/kotlin` is simply
    // never compiled, so an `actual` that only exists there is dead code, and the module
    // builds clean for iOS with no implementation of its `expect` declarations until a
    // consumer that needs them trips the "no actual declaration" error. `:packages:
    // design-system` had exactly that - `Colors.ios.kt` sat in `src/iosMain` and had
    // never been compiled by anything.
    //
    // Every platform-specific `actual` in this project depends on this call existing.
    applyDefaultHierarchyTemplate()

    compilerOptions {
        // expect/actual pairs are central to keychain, user-defaults, the
        // design-system's semantic colours and the iOS glass interop.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
}

/**
 * A stale-framework guard, per iOS target, for any module that publishes a `framework`.
 *
 * A framework binary is a *build output that another build system consumes by path*, which is
 * what makes it different from every other artifact here: Xcode finds it at a fixed location
 * rather than asking Gradle for it. Nothing in either build fails when it is out of date, because
 * linking an old binary is a successful link. `VerifyIosFrameworkFreshnessTask` is what turns that
 * silence into a failure — see that file for why it compares timestamps rather than symbols.
 *
 * Registered per framework rather than once per module so the task name states the target
 * (`verifyIosSimulatorArm64FrameworkIsFresh`), which is the thing a caller needs to name in a
 * command and the thing that has to differ between simulator and device.
 *
 * Deliberately *not* attached to `check`. `check` runs on the JVM and Android hosts, where no
 * framework is linked; wiring this into it would fail every non-iOS build by default and train
 * people to ignore it.
 *
 * Registered once rather than by walking `binaries`, and for the same reason
 * `tasktracker.kmp.library` names the executable rather than discovering it: a module declares
 * its framework in its own build file, and a convention plugin cannot know that a given module
 * has one. Walking the binary map from here asks each module's configuration for an entry that
 * only some of them have, and the one that does not has is not a list — it is a build-wide
 * `Key ... is missing in the map` during plugin application, which is a long way from a useful
 * error message.
 *
 * `:shared-ui` is the module that publishes `ComposeApp`; it is named in the module's own build
 * file, which is where this decision belongs. This registration is the *check*, not the
 * declaration, and it is registered unconditionally so a module that stops publishing a
 * framework fails this check rather than silently losing its guard.
 */
tasks.register<com.tasktracker.architecture.VerifyIosFrameworkFreshnessTask>(
    "verifyIosSimulatorArm64FrameworkIsFresh"
) {
    group = "verification"
    description =
        "Fails if the linked simulator framework is older than :${project.name}'s Kotlin sources."
    frameworkBinary.set(
        layout.buildDirectory.file(
            "bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp",
        ),
    )
    sourceRoot.set(layout.projectDirectory.dir("src"))
    targetName.set("iosSimulatorArm64")
}
