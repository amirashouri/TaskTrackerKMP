package com.tasktracker.compositionroot

import com.tasktracker.featuresettingsdomain.AppVersion

/**
 * The app's version, read from this module's own jar manifest.
 *
 * `Implementation-Version` is stamped by `composition-root/build.gradle.kts`: the release
 * tag when the release workflow passes `-PtasktrackerVersion`, otherwise the declared
 * project version (`1.0.0`). The packaged Desktop app and Android Studio's hot-reload run
 * both launch from jars — `hotRunJvm` copies this jar onto its classpath — so the manifest
 * travels with the app and the Settings About row reports a real number.
 *
 * A classes-directory host (the test suite, a plain IDE run) has no manifest, so
 * `Package.implementationVersion` is `null` and the answer is blanks. That keeps the row
 * hidden rather than printing a placeholder for a build that declares no version at all.
 *
 * [VersionAnchor] rather than `AppVersion` as the anchor: the version belongs to this
 * module's jar, and anchoring on a type from `:packages:feature-settings-domain` would read
 * whatever that dependency's manifest happened to carry.
 */
actual fun platformAppVersion(@Suppress("UNUSED_PARAMETER") host: Any?): AppVersion =
    jvmAppVersion(VersionAnchor::class.java.`package`?.implementationVersion)

/**
 * Maps a jar's `Implementation-Version` to the About row's value.
 *
 * `null`, empty, and whitespace all mean "no version": [AppVersion.display] is then `null`
 * and the row hides, rather than rendering a marketing value made of spaces.
 */
internal fun jvmAppVersion(implementationVersion: String?): AppVersion =
    AppVersion(marketing = implementationVersion.orEmpty().trim(), build = "")

/** The class whose package names this module's jar. See [platformAppVersion]. */
private object VersionAnchor
