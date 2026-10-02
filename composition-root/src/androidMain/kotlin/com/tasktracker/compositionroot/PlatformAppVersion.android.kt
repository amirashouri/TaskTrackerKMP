package com.tasktracker.compositionroot

import android.content.Context
import com.tasktracker.featuresettingsdomain.AppVersion

/**
 * The version from `PackageManager`, read off the installed package.
 *
 * `getPackageInfo` rather than the deprecated `versionName`/`versionCode` fields, which is
 * what AGP's own lint asks for on API 34+.
 *
 * Every failure path returns blanks rather than throwing: a missing host, a package that
 * cannot describe itself, or a field of an unexpected type all end at an empty
 * [AppVersion], whose `display` is `null` and whose About row therefore hides itself. This
 * function runs while the graph is being described, and a version string is not worth
 * refusing to start the app over.
 */
actual fun platformAppVersion(host: Any?): AppVersion {
    val context = host as? Context ?: return AppVersion(marketing = "", build = "")
    val manager = context.packageManager ?: return AppVersion(marketing = "", build = "")
    val packageInfo = runCatching { manager.getPackageInfo(context.packageName, 0) }.getOrNull()
        ?: return AppVersion(marketing = "", build = "")
    return AppVersion(
        marketing = packageInfo.versionName.orEmpty(),
        build = packageInfo.longVersionCode.toString(),
    )
}