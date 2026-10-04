package com.tasktracker.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Android's counterpart to the original's `UIColor` semantic colours.
 *
 * Each token resolves a *theme attribute*, never a literal. The attributes are the
 * platform's own names for the same roles UIKit gives `systemBackground` and
 * `secondarySystemBackground`, so the app follows the device's light/dark setting and
 * any dynamic theming the user has applied, exactly as the SwiftUI original does.
 *
 * `colorBackgroundFloating` is Android's closest match for `secondarySystemBackground`:
 * it is the theme's "one step above the page" surface. Material's `colorSurface` would
 * also be defensible, but pulling in Material purely to name a colour would reintroduce
 * the dependency the design decision removed.
 */
@Composable
actual fun ttCanvas(): Color = dynamicOrPinned(
    pinned = { ttFixedColours.canvas(it) },
    dynamic = { themeColor(android.R.attr.colorBackground) },
)

@Composable
actual fun ttSurface(): Color = dynamicOrPinned(
    pinned = { ttFixedColours.surface(it) },
    dynamic = { themeColor(android.R.attr.colorBackgroundFloating) },
)

/**
 * Android has no `separator` resource, so the hairline is derived from the theme's
 * secondary text colour at low alpha. That keeps it adapting with the theme, which a
 * fixed grey would not: on a dark canvas a hardcoded divider is a visible white line.
 */
/**
 * Android's own error colour, so a failure banner follows the platform theme the way every
 * other token here does. Material's `colorError` is the attribute Android has always had
 * for this role, and reading it costs nothing — no Material *dependency*, only the theme
 * attribute.
 */
@Composable
actual fun ttError(): Color = dynamicOrPinned(
    pinned = { ttFixedColours.error(it) },
    dynamic = { themeColor(android.R.attr.colorError) },
)

@Composable
actual fun ttLabel(): Color = dynamicOrPinned(
    pinned = { ttFixedColours.label(it) },
    dynamic = { themeColor(android.R.attr.textColorPrimary) },
)

/**
 * Android's `textColorSecondary` is an opaque colour where iOS's `secondaryLabel` is the
 * label at 60%, so the pinned entry cannot be this attribute's value — a dark appearance
 * would paint a mid-grey that is lighter than the dark label it is supposed to subordinate
 * to. `ttFixedColours` holds the iOS-shaped pair instead, which is what makes a pinned
 * appearance identical on both platforms.
 */
@Composable
actual fun ttLabelSecondary(): Color = ttFixedColours.labelSecondary(ttIsDarkTheme())

@Composable
actual fun ttBodyLabel(): Color = ttFixedColours.bodyLabel(ttIsDarkTheme())

@Composable
actual fun ttSeparator(): Color = dynamicOrPinned(
    // The pinned values are UIKit's `separator` pair, not a darkening of Android's
    // `textColorSecondary`: a pinned appearance has to resolve to the same colour the other
    // two targets paint, and the two platforms disagree about what a hairline is.
    pinned = { ttFixedColours.separator(it) },
    dynamic = { themeColor(android.R.attr.textColorSecondary, alpha = 0.20f) },
)

@Composable
private fun themeColor(attr: Int, alpha: Float = 1f): Color {
    val context = LocalContext.current
    return Color(resolveThemeColor(context, attr, alpha))
}

/**
 * Resolves [attr] from [context]'s theme.
 *
 * `LocalContext` is the Activity for an Activity-hosted composition, but it degrades to
 * the application context in other cases, and the application context still carries the
 * manifest's theme — so the lookup works either way. `obtainStyledAttributes` on a
 * context whose theme declares nothing yields a zero, which would be a transparent
 * canvas and a silently invisible app, so the fallback is a visible near-black rather
 * than a transparent one.
 */
private fun resolveThemeColor(context: Context, attr: Int, alpha: Float): Int {
    val typedValue = TypedValue()
    val resolved = context.theme.resolveAttribute(attr, typedValue, true)
    if (!resolved) return fallbackColor()

    val base = if (typedValue.resourceId != 0) {
        androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
    } else {
        typedValue.data
    }
    return applyAlpha(base, alpha)
}

private fun applyAlpha(color: Int, alpha: Float): Int {
    val a = ((color ushr 24) and 0xFF) * alpha
    return (a.toInt() shl 24) or (color and 0x00FFFFFF)
}

/** Near-black rather than transparent, so a missing theme cannot blank the app. */
private fun fallbackColor(): Int = 0xFF1C1C1E.toInt()

@Composable
actual fun SystemBarAppearance(isDark: Boolean) {
    val view = LocalView.current
    DisposableEffect(isDark) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.window
        window?.let {
            WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightStatusBars = !isDark
        }
        onDispose {}
    }
}
