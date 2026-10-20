package com.tasktracker.compositionroot

import android.content.Context
import android.content.pm.PackageManager

/**
 * The API root from the application manifest's `<meta-data>`.
 *
 * `com.tasktracker.API_BASE_URL` is declared in `androidApp`'s manifest and filled from the
 * `tasktracker.apiBaseUrl` Gradle property, so a deployment names its server at build time
 * (`-Ptasktracker.apiBaseUrl=https://server.example.com`) rather than editing a source file.
 * The meta-data is read through [Context] rather than from `BuildConfig` because this function
 * lives in `:composition-root` and the app's `BuildConfig` is not visible from another module.
 *
 * ## The fallback is `10.0.2.2`, not `localhost`
 *
 * An Android emulator runs its own network stack: `localhost` inside it is the emulator, not
 * the developer's machine. `10.0.2.2` is the emulator's alias for its host, so that is the
 * address the local Go server is reachable at. A physical device reaches neither and must be
 * given the server's real address through the property above.
 *
 * Every failure path — no host, an unreadable package, a blank value — falls back rather than
 * throwing: this runs while the graph is being described, and a missing API root is not worth
 * refusing to start the app over.
 *
 * A server address saved in Settings wins over the manifest value. That preference is read
 * through [storedApiBaseUrl] — and because it is part of what this function returns, the
 * local-network permission decision in `MainActivity` covers it too.
 *
 * Split into [androidApiBaseUrl] and this `actual` so the rule is a pure function of the
 * configured string, which a test can drive without a manifest.
 */
actual fun platformApiBaseUrl(host: Any?): String =
    apiBaseUrlWithOverride(
        buildTime = androidApiBaseUrl(readConfiguredApiBaseUrl(host)),
        storedOverride = storedApiBaseUrl(host),
    )

/** The manifest `<meta-data>` name that overrides [platformApiBaseUrl] on Android. */
internal const val API_BASE_URL_META_DATA_KEY: String = "com.tasktracker.API_BASE_URL"

/**
 * The Android fallback: the emulator's alias for the machine running the local Go server.
 *
 * Deliberately not [DEFAULT_LOCAL_API_BASE_URL] — see this file's header for why `localhost`
 * cannot work here.
 */
internal const val DEFAULT_ANDROID_API_BASE_URL: String = "http://10.0.2.2:8080"

/** [platformApiBaseUrl] as a function of the value the manifest supplied. */
internal fun androidApiBaseUrl(configured: String?): String =
    configured?.takeIf { it.isNotBlank() } ?: DEFAULT_ANDROID_API_BASE_URL

/**
 * The meta-data value, or `null` when there is nothing to read.
 *
 * `runCatching` rather than a checked call: `getApplicationInfo` throws for a package the
 * manager does not know, which is exactly what a test host looks like from here.
 */
private fun readConfiguredApiBaseUrl(host: Any?): String? {
    val context = host as? Context ?: return null
    val applicationInfo = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    }.getOrNull() ?: return null
    return applicationInfo.metaData?.getString(API_BASE_URL_META_DATA_KEY)
}
