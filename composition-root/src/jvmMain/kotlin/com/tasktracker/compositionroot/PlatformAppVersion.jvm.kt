package com.tasktracker.compositionroot

import com.tasktracker.featuresettingsdomain.AppVersion

/**
 * The version the release was cut as, read from this module's own jar manifest.
 *
 * `Implementation-Version` is stamped by `composition-root/build.gradle.kts` from the same
 * `-PtasktrackerVersion` the release workflow passes to the packaging tasks. The packaged
 * Desktop app runs from jars, so the manifest travels with it and the Settings About row
 * can finally report a real number — the `actual` used to answer blanks because a JVM host
 * was only a test or a desktop preview.
 *
 * A classes-directory host (the test suite, an IDE run) has no manifest, so
 * `Package.implementationVersion` is `null` and the answer is still blanks. That keeps the
 * row hidden rather than printing a placeholder, which is the same rule [AppVersion.display]
 * states: a fallback number would be a number nobody shipped.
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
