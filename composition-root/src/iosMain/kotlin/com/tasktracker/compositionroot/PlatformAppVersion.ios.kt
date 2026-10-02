package com.tasktracker.compositionroot

import com.tasktracker.featuresettingsdomain.AppVersion
import platform.Foundation.NSBundle

/**
 * The version from the main bundle, matching what the App Store shows.
 *
 * `objectForInfoDictionaryKey` rather than the generated `infoDictionary` because the
 * generated property is `[String : Any]?` and needs a cast for every key anyway; the
 * accessor returns the value already unwrapped to `Any?`, so a non-string reads as absent
 * through the `as? String` below.
 *
 * The two keys are the ones Xcode reads out of `Info.plist`. Both absent means an
 * unbundled host, which is what a JVM or Android test build looks like from here — and the
 * empty [AppVersion] it produces is what hides the About row instead of printing blanks.
 */
actual fun platformAppVersion(@Suppress("UNUSED_PARAMETER") host: Any?): AppVersion {
    val bundle = NSBundle.mainBundle
    return AppVersion(
        marketing = (bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String).orEmpty(),
        build = (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String).orEmpty(),
    )
}