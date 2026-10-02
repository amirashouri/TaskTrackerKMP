package com.tasktracker.compositionroot

import com.tasktracker.featuresettingsdomain.AppVersion

/**
 * The version the About row reports, read from the running app's own metadata.
 *
 * ## Why the composition root owns this
 *
 * It is the one module allowed to name platform APIs, and a bundle's version *is* a
 * platform API — `PackageManager` on Android, `NSBundle` on iOS. Reading it anywhere else
 * would put a platform call in a module that exists precisely so platform calls stay out.
 *
 * ## Both parts come back empty when absent, which hides the row
 *
 * `AppVersion.display` returns `null` for a blank marketing version and the About row
 * hides itself rather than inventing a number nobody shipped. A test host has no bundle to
 * ask, and reporting `""` there is what makes that path reachable in a test at all — the
 * alternative, throwing, would put a test-only failure on the startup path.
 *
 * [host] carries the Android `Context`; other platforms ignore it. Typed `Any?` because
 * common code cannot name `Context`, matching [com.tasktracker.userdefaults.platformPreferences].
 */
expect fun platformAppVersion(host: Any? = null): AppVersion