package com.tasktracker.compositionroot

import com.tasktracker.featuresettingsdomain.AppVersion

/**
 * No bundle to ask.
 *
 * A JVM host is a test or a desktop preview, neither of which is an installed app, so the
 * honest answer is blanks — which is the same answer a bundle with no version keys gives,
 * and which makes the About row hide itself. Reading a property file here would be a second
 * source of truth for a version that the platform entry point already knows.
 */
actual fun platformAppVersion(@Suppress("UNUSED_PARAMETER") host: Any?): AppVersion =
    AppVersion(marketing = "", build = "")