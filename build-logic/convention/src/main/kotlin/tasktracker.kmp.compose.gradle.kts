/**
 * Convention for every module that renders Compose UI: :packages:design-system,
 * the nine feature presentation modules, :composition-root and :shared-ui.
 *
 * Note what is *absent*: this plugin brings in Compose, not Room, Ktor or Koin.
 * A presentation module gets its data by depending on its own feature's domain
 * module, so an accidental presentation -> data edge fails at compile time
 * rather than being quietly satisfied by a convention plugin.
 */
plugins {
    id("tasktracker.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // Deliberately no Material3. The original's colours are UIKit *semantic*
    // colours (ttCanvas -> systemBackground, ttSurface -> secondarySystemBackground),
    // not hex values, and the port keeps that property by resolving them through
    // expect/actual per platform. Material3 would replace adaptive semantic
    // colours with a fixed scheme, which is the divergence the design decision
    // explicitly rejected. runtime/foundation/ui are all the design system needs;
    // Material3 gets added per-component later only if a component genuinely
    // requires it, and it would need its own pinned version track.
    add("commonMainImplementation", libs.compose.runtime)
    add("commonMainImplementation", libs.compose.foundation)
    add("commonMainImplementation", libs.compose.ui)
    add("commonMainImplementation", libs.androidx.lifecycle.viewmodel)
    add("commonMainImplementation", libs.androidx.lifecycle.viewmodel.compose)
    add("commonMainImplementation", libs.androidx.lifecycle.runtime)

    // @Preview support. Both were already in the catalog and unreferenced, so the build had
    // no preview support at all despite every Swift view in the original having one — and a
    // previews-only gap is the kind that never gets closed, because a component with no
    // preview still compiles and still ships.
    //
    // `ui-tooling-preview` everywhere, because the `@Preview` annotation is what the
    // previews pane and every `#Preview` in the original map onto, and it does publish for
    // all four targets.
    add("commonMainImplementation", libs.compose.uiToolingPreview)

    // The Compose resources *runtime*, with no `compose.resources` configuration.
    //
    // These are two different things and the difference is the whole point. Configuring the
    // `compose.resources` extension makes a module generate its own
    // `com.tasktracker.resources.*ResourceCollectors`, and two of those collide at dex
    // merge ("defined more than once") - so exactly one module in the build may do it, and
    // :packages:design-system is that one. Depending on the *library* only puts
    // `stringResource` and friends on the compile classpath; it generates nothing.
    //
    // It is here because :packages:design-system declares the runtime as
    // `commonMainImplementation`, and KMP `implementation` is not transitive to a consumer's
    // compile classpath. So `Res` resolved from the feature modules - the generated class is
    // part of design-system's published API - while `stringResource` did not, and the first
    // feature module to read a string failed on the second line of its first file.
    add("commonMainImplementation", libs.compose.resources)

    add("commonTestImplementation", libs.compose.uiTest)
    // No ui-test-manifest: that artifact is Android-only and was never published
    // multiplatform, so it cannot resolve for the iOS test compilations.
    // No `compose.resources` configuration either, for the reason given above: every
    // module that configures it generates its own
    // com.tasktracker.resources.*ResourceCollectors, and two of those collide at dex
    // merge ("defined more than once"). Exactly one module owns the strings -
    // see :packages:design-system. The runtime library is still on the classpath, so
    // any module can *read* the generated `Res`.

    // The host's native skiko, for the JVM target's Compose UI tests only.
    //
    // `ui-test` above brings the *API*, and on the JVM that API renders through skiko, whose
    // native library arrives in a separate artifact. Without this every `runComposeUiTest`
    // on the JVM dies identically in its constructor:
    //
    //   LibraryLoadException: Cannot find libskiko-macos-arm64.dylib.sha256,
    //   proper native dependency missing.
    //
    // which reads as a test bug and is not one. It went unnoticed because the only modules
    // with `ui-test` on the classpath had no Compose UI tests to run — the dependency was
    // declared in the convention and never exercised until the first screen arrived.
    //
    // Scoped to `jvmTest` deliberately: iOS and Android supply their own natives, and putting
    // a host dylib on either would be dead weight at best.
    add("jvmTestImplementation", compose.desktop.currentOs)

    // The Android host's half of a Compose UI test.
    //
    // Two dependencies, for two different failures. `ui-test-manifest` contributes the
    // activity a Compose test needs to host into, and without it the Android host throws
    // IllegalStateException("No compose views found") from deep inside the test environment.
    // Robolectric supplies the rest of the framework: the host's `android.jar` is a stub
    // whose `Build.FINGERPRINT` is null, and Compose's own `RobolectricIdlingStrategy`
    // dereferences it unconditionally, so every test in the suite died with
    //
    //   NullPointerException: Cannot invoke "String.toLowerCase()" because
    //   "android.os.Build.FINGERPRINT" is null
    //
    // before reaching a single assertion. Neither error points at the missing dependency.
    //
    // Both are scoped to `androidHostTest` rather than `commonTest` for the reason the
    // ui-test-manifest note above gives: they are Android-only, and the multiplatform
    // coordinates do not resolve for the iOS test compilations.
    add("androidHostTestImplementation", libs.compose.testManifest)
    add("androidHostTestImplementation", libs.robolectric)
}

/**
 * Robolectric on the Android host, for assertions that are not Compose UI tests.
 *
 * Deliberately separate from [tasktracker.kmp.compose]: a module can have a Robolectric test
 * that never composes anything. `:composition-root`'s graph tests need `RuntimeEnvironment`'s
 * `Context` so Room can open a store, and that module has no Compose UI test to justify the
 * dependency. A module that needs neither pays for neither.
 */
plugins.withId("tasktracker.kmp.library") {
    dependencies {
        add("androidHostTestImplementation", libs.robolectric)
    }
}

/**
 * Puts the shared Compose resources where the Android host test can actually read them.
 *
 * ## The failure this exists to fix
 *
 * Every Compose UI test on the Android host that renders a string died with
 *
 *     MissingResourceException: Missing resource with path:
 *     composeResources/com.tasktracker.resources/values/strings.commonMain.cvr
 *
 * 61 of them, across the four modules whose tests compose UI. It is not a test bug: the same
 * missing asset crashed the shipped app on its first frame (see docs/PORT-LOG.md, Phase 12), and
 * `:androidApp` was fixed by hand. This is that same fix, applied to the test hosts.
 *
 * ## Why it has to be a copy, and not a source directory
 *
 * The Compose Multiplatform plugin already registers
 * `copyAndroidMainComposeResourcesToAndroidAssets` per library and tries to wire it with
 * `componentSources.assets?.addGeneratedSourceDirectory(...)`. That `?.` never fires on
 * AGP 9.4.1, so the task is left with an unset `outputDirectory` and running it directly
 * fails with *"Assign a value to 'outputDirectory'"*. Wiring it by hand is not possible
 * either, and all three escapes were checked against `gradle-api-9.4.1` rather than assumed:
 *
 *  - `onVariants` yields **only** the `androidMain` variant. There is no `hostTestVariants`
 *    accessor on the KMP components extension, so the host test variant is unreachable.
 *  - On that variant `variant.sources.assets` is **null** (`sources.resources` is not), so
 *    there is no source directory to add a generated or static one to.
 *  - `KotlinMultiplatformAndroidLibraryExtension` has no `sourceSets`, so the legacy
 *    `android.sourceSets` route that `:androidApp` could not use either is closed here too.
 *
 * ## Why the copy target is AGP's own output directory
 *
 * `DefaultAndroidResourceReader` resolves every resource through
 * `context.assets.open(path)` — verified by disassembling the class, and there is no
 * classpath fallback — and Robolectric 4.16.1 has no `assets` option on `@Config` or in
 * `robolectric.properties`. Robolectric's asset directory therefore comes solely from
 * `android_merged_assets` in the generated `test_config.properties`, which points at
 * `intermediates/assets/androidHostTest/mergeAndroidHostTestAssets`. Writing there is the
 * only place the reader looks.
 *
 * ## `Copy`, not `Sync`
 *
 * `mergeAndroidHostTestAssets` legitimately produces `PublicSuffixDatabase.list` alongside
 * the empty tree. A `Sync` into that directory would delete anything it did not put there,
 * so this is a `Copy`: it adds the Compose resources and touches nothing else.
 */
val copyComposeResourcesIntoAndroidHostTestAssets by tasks.registering(Copy::class) {
    description = "Copies the shared Compose resources into the Android host test's merged " +
        "assets, which the Compose Multiplatform plugin leaves unwired on AGP 9."

    // `preparedResources/commonMain/composeResources` is the root the plugin still produces
    // for an Android target, and everything below it is already relative to
    // `composeResources` — so the package name can simply be re-rooted on the way in, which
    // is what makes the path Compose asks for resolve.
    val resourceOwner = project(":packages:design-system")
    from(
        resourceOwner.layout.buildDirectory.dir(
            "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
        ),
    ) {
        into("composeResources/com.tasktracker.resources")
    }
    into(layout.buildDirectory.dir("intermediates/assets/androidHostTest/mergeAndroidHostTestAssets"))

    // A plain build-directory path carries no task dependency, so on a clean build this would
    // copy whatever happened to be there — nothing — and the tests would fail exactly as they
    // did before, with no indication that a clean build was the cause.
    dependsOn(":packages:design-system:prepareComposeResourcesTaskForCommonMain")
}

// Order matters and nothing in the inputs implies it: the merge task owns the destination
// directory and may clean it, so the copy has to land after it. `finalizedBy` rather than
// `dependsOn` so the copy still runs when the test task is reached without the merge.
tasks.matching { it.name == "mergeAndroidHostTestAssets" }.configureEach {
    finalizedBy(copyComposeResourcesIntoAndroidHostTestAssets)
}

// Writing into another task's output directory is an implicit dependency, and Gradle 9
// rejects it outright ("uses this output of task ... without declaring an explicit or
// implicit dependency") rather than letting it become a race. The consumer here is
// `packageAndroidHostTestForUnitTest`, which builds the `apk-for-local-test.ap_` that
// `test_config.properties` names as `android_resource_apk`.
tasks.matching { it.name == "packageAndroidHostTestForUnitTest" }.configureEach {
    mustRunAfter(copyComposeResourcesIntoAndroidHostTestAssets)
}

tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    dependsOn(copyComposeResourcesIntoAndroidHostTestAssets)
}
