package com.tasktracker.designsystem

import android.content.Context
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
actual fun ttCanvas(): Color = themeColor(android.R.attr.colorBackground)

@Composable
actual fun ttSurface(): Color = themeColor(android.R.attr.colorBackgroundFloating)

/**
 * Android has no `separator` resource, so the hairline is derived from the theme's
 * secondary text colour at low alpha. That keeps it adapting with the theme, which a
 * fixed grey would not: on a dark canvas a hardcoded divider is a visible white line.
 */
@Composable
actual fun ttSeparator(): Color = themeColor(android.R.attr.textColorSecondary, alpha = 0.20f)

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
