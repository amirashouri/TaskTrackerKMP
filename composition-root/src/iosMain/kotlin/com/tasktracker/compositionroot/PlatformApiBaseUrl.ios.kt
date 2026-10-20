package com.tasktracker.compositionroot

import platform.Foundation.NSBundle

/**
 * The API root from the app bundle's `Info.plist`.
 *
 * `TaskTrackerApiBaseUrl` is the key a deployment sets to point the app at its own server; it
 * is wired to the `TASKTRACKER_API_BASE_URL` Xcode build setting in `iosApp/project.yml`, so a
 * build can name a server without editing the plist. An absent or blank key falls back to the
 * local server — the iOS simulator shares the host's network, so `localhost` reaches the Go
 * server running beside it.
 *
 * `objectForInfoDictionaryKey` rather than the generated `infoDictionary`, matching
 * [platformAppVersion]: the accessor returns the value already unwrapped to `Any?`, so a
 * non-string reads as absent through the `as? String` below.
 *
 * Split into [iosApiBaseUrl] and this `actual` so the rule is a pure function of a key reader.
 * A test host has no bundle to ask, so a test would otherwise be unable to reach either branch.
 *
 * A server address saved in Settings wins over the plist. That preference is read through
 * [storedApiBaseUrl]; the plist stays the build-time answer for a build a customer has not
 * configured yet.
 */
actual fun platformApiBaseUrl(host: Any?): String =
    apiBaseUrlWithOverride(
        buildTime = iosApiBaseUrl { key ->
            NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String
        },
        storedOverride = storedApiBaseUrl(host),
    )

/** The `Info.plist` key that overrides [platformApiBaseUrl] on iOS. */
internal const val API_BASE_URL_PLIST_KEY: String = "TaskTrackerApiBaseUrl"

/** [platformApiBaseUrl] as a function of the plist lookup it was given. */
internal fun iosApiBaseUrl(readKey: (String) -> String?): String =
    readKey(API_BASE_URL_PLIST_KEY)?.takeIf { it.isNotBlank() } ?: DEFAULT_LOCAL_API_BASE_URL
