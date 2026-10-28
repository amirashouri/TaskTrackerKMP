import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// ---------------------------------------------------------------------------
// Compose resources, packaged by hand because the plugin does not.
//
// `stringResource` reads its value at runtime out of the APK's assets, at
//
//     assets/composeResources/<packageOfResClass>/values/<file>.<qualifier>.cvr
//
// and :packages:design-system — the only module that configures `compose.resources`,
// and therefore the only one that produces these files — never gets them packaged
// here. Compose Multiplatform 1.12.1 registers a task to do it
// (`copyAndroidMainComposeResourcesToAndroidAssets`) and wires it with
// `variant.sources.assets?.addGeneratedSourceDirectory(...)`. That `?.` is the bug:
// on this AGP the assets source is null, so the wiring is silently skipped, the task
// is left with an unset `outputDirectory`, and it never appears in any task graph.
//
// The result is an APK with no Compose resources at all, and the failure surfaces at
// runtime rather than at build time: the first `stringResource` in the first frame
// throws `MissingResourceException` and the process dies on launch, with a stack trace
// that points at Compose rather than at the build. Every Android-host UI test in this
// build fails the same way, which is how this was found.
//
// So the copy is done here, from the one directory that is produced
// (`preparedResources/commonMain`), re-rooted under the resource package name. The
// `strings.commonMain.cvr` qualifier is correct for Android: it is the *common* set,
// and there is no Android-specific one to prefer.
// ---------------------------------------------------------------------------
/**
 * Reads `tasktracker.apiBaseUrl` from `local.properties` (gitignored, machine-specific).
 *
 * Used to pick up the server address for local development against a physical device without
 * committing the address to `gradle.properties` or passing it on every command line. Returns
 * `null` when the file or the key is absent, which is the normal state on CI.
 *
 * Parsed with [java.util.Properties] because `local.properties` is a Java properties file, and
 * the `tasktracker.apiBaseUrl = value` spelling with whitespace is legal there. A hand-rolled
 * `startsWith("tasktracker.apiBaseUrl=")` match silently ignored it and fell through to the
 * emulator default (`10.0.2.2`), which reads on a phone as "could not reach the server" with
 * no sign that the configured address was never used.
 */
fun localPropertiesApiBaseUrl(): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    val properties = Properties()
    file.inputStream().use(properties::load)
    return properties.getProperty("tasktracker.apiBaseUrl")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

val composeResourceOwner = project(":packages:design-system")
val generatedComposeAssets = layout.buildDirectory.dir("generated/composeAssets")

val packageComposeResourcesIntoAssets = tasks.register<Sync>("packageComposeResourcesIntoAssets") {
    description = "Copies the shared Compose resources into assets, which the Compose " +
        "Multiplatform plugin leaves unwired on this AGP version."
    group = "compose resources"

    // `preparedResources/<sourceSet>/composeResources` is the root the plugin stops at, so
    // every file below it is already relative to `composeResources` — which is why the
    // destination can simply re-root it under the package name.
    from(
        composeResourceOwner.layout.buildDirectory.dir(
            "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
        ),
    )
    into(generatedComposeAssets.map { it.dir("composeResources/com.tasktracker.resources") })

    // A plain build-directory path carries no task dependency, so a clean build would copy
    // whatever happened to be there (nothing) and package an APK with no resources in it.
    dependsOn(":packages:design-system:prepareComposeResourcesTaskForCommonMain")
}

android {
    namespace = "com.tasktracker.android"

    defaultConfig {
        applicationId = "com.tasktracker.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        // The API root this build talks to, surfaced to `platformApiBaseUrl` through the
        // manifest meta-data declared in `src/main/AndroidManifest.xml`. Override it for a real
        // deployment with `-Ptasktracker.apiBaseUrl=https://server.example.com` (or a
        // `tasktracker.apiBaseUrl` entry in `gradle.properties`).
        //
        // For local development against a physical device, add the same key to
        // `local.properties` (gitignored) so Android Studio's Run/Debug button picks it up
        // without a command-line flag or a committed machine-specific address:
        //
        //     tasktracker.apiBaseUrl=http://192.168.1.42:8080
        //
        // `local.properties` is read here but not by `findProperty`, so it is merged in
        // explicitly as a second-priority source — command-line `-P` wins, then
        // `local.properties`, then nothing (the Kotlin fallback).
        //
        // Deliberately empty when unset, so the fallback is the Kotlin default in
        // `PlatformApiBaseUrl.android.kt` and not a second copy of the same address here: a
        // value baked in at this layer would drift from the one the resolver's tests pin.
        manifestPlaceholders["tasktrackerApiBaseUrl"] =
            (findProperty("tasktracker.apiBaseUrl") as String?)
                ?.takeIf { it.isNotBlank() }
                ?: localPropertiesApiBaseUrl()
                ?: ""
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(25)
}

/**
 * Hands the generated resources to AGP through the variant API.
 *
 * The older `android.sourceSets` route is not available here: AGP 9 refuses a `Provider` on
 * that API outright ("You cannot add Provider instances to the Android SourceSet API"),
 * because it cannot tell a read-only generated directory from a read-write static one. The
 * variant API can, which is the same reason the Compose plugin's own wiring — the `?.` in
 * `AndroidResources.kt` that silently did nothing — is written against `Sources`.
 *
 * `addStaticSourceDirectory` rather than `addGeneratedSourceDirectory` because the latter
 * wants the producing task's `DirectoryProperty`, and `Sync` exposes a plain `File` as its
 * destination. The cost is that the dependency is not inferred, so it is declared below.
 */
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(
            generatedComposeAssets.get().asFile.path,
        )
    }
}

/**
 * Orders the asset merge after the copy.
 *
 * Without this the merge is free to run first, because nothing in its inputs mentions the
 * copy task — and the failure mode of getting it wrong is a *build that succeeds* and an APK
 * with no resources in it, which then crashes on the first string read. Named rather than
 * matched by type because the merge task's type is internal to AGP and moves between
 * versions; `merge<Variant>Assets` is the name AGP has used throughout.
 */
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(packageComposeResourcesIntoAssets)
    }
}

dependencies {
    // The shell is above the architecture, not inside it. It holds the graph and renders
    // the shared tree, so it names both modules directly rather than reaching them through
    // a transitively-leaking implementation dependency.
    implementation(project(":composition-root"))
    implementation(project(":shared-ui"))
    implementation(project(":packages:di"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // No Koin and no Ktor here. The container is reached through the Resolver interface
    // in :packages:di and the HTTP client is built in :packages:network; a second copy of
    // either in the app module would be a graph that behaves differently from the one the
    // composition root describes.
}
