package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's accent: the one fill in the whole design system that carries a label.
 *
 * This is the only colour token that is **not** resolved from the platform's own theme,
 * and the reason is worth stating because it is the opposite of the reasoning in
 * `Colors.kt`. `ttCanvas`, `ttSurface` and `ttSeparator` are roles rather than choices —
 * whatever the system calls "the page background" is right by definition, and asking
 * Android for `?android:attr/colorAccent` or iOS for `tintColor` would hand the brand
 * over to an OEM theme or to a user's tint setting. This is a decision the product made,
 * so it is written down here as a value.
 *
 * ## Why this is a token and not an asset catalog entry
 *
 * The original resolved the accent through
 * `ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME`, so it lived in
 * `AccentColor.colorset/Contents.json` in the app target rather than in `Palette.swift`.
 * That forced `AccentContrastTests` to read the JSON off disk, located from `#filePath` —
 * a package test reaching outside itself, deliberately: a test that hardcoded the two
 * hexes would have stayed green after somebody edited the catalog, which is the exact
 * failure it exists to catch. The original's `AGENTS.md` names this trade-off and says
 * promoting the accent to a real token would give two sources of truth instead. The
 * token is the better trade, and it is taken here: [Light] and [Dark] are `const`-adjacent
 * plain values in `commonMain`, so `AccentContrastTest` asserts the numbers the token
 * actually paints, and the Android and iOS app targets derive their asset catalogs from
 * these values rather than the other way round.
 *
 * ## The pairing is a constraint, not a preference
 *
 * [ttOnAccent] is white, so the accent must clear two bars at once:
 *
 *  - at least **4.5:1** against white, for the label drawn on it, and
 *  - at least **3:1** against the canvas it sits on, or the control carrying that label
 *    is invisible.
 *
 * The second bar is the one a checker measuring only against white would miss.
 * Genuine charcoal, roughly `#2B3037` through `#4C545D`, clears the text rule easily and
 * fails visibility, which renders the primary button invisible on a black canvas.
 *
 * Each bar is measured **per appearance**, because that is the only pairing that occurs:
 * [Light] appears on a light canvas and [Dark] on a dark one. [Light] is 7.89:1 against
 * both white and a white canvas; [Dark] is 5.65:1 against white for the label and
 * 3.72:1 against the dark canvas for visibility, tightening to 3.34:1 against Android's
 * `#121212` dark theme canvas, which is why `AccentContrastTest` checks that colour too.
 * Comparing [Light] against a dark canvas is not a stricter check - it measures a
 * combination that cannot happen, and it reports a correct colour as failing.
 * `AccentContrastTest` enforces all of it, so this file cannot drift unnoticed.
 */
object TaskTrackerAccent {

    /** The brand accent on a light canvas: sRGB 0.204, 0.325, 0.478. */
    val Light: Color = Color(0xFF34537A)

    /** The brand accent on a dark canvas: sRGB 0.373, 0.408, 0.451. */
    val Dark: Color = Color(0xFF5F6873)

    /**
     * Both variants, so a test can assert the constraint against every value the app
     * can paint rather than only against the one a given test happens to resolve.
     */
    val all: List<Color> = listOf(Light, Dark)
}

/** Accent colour sets intended for trying alternate light and dark app appearances. */
enum class AccentPalette(
    val wireValue: String,
    val title: String,
    val light: Color,
    val dark: Color,
) {
    Classic("classic", "Classic", TaskTrackerAccent.Light, TaskTrackerAccent.Dark),
    ClassicLight("classic-light", "C-Light", Color(0xFF49688F), Color(0xFF6C7582)),
    Music("music", "Music", Color(0xFFB0004B), Color(0xFFD81B60)),
    Sunset("sunset", "Sunset", Color(0xFFA8320A), Color(0xFFCA3F12)),
    Violet("violet", "Violet", Color(0xFF5B21B6), Color(0xFF8845D2)),
    NavyBlue("navy-blue", "Navy Blue", Color(0xFF234D82), Color(0xFF45658F)),
    ;

    companion object {
        const val pickerTitle: String = "Accent palette"
        val all: List<AccentPalette> = entries

        fun fromStoredValue(value: String?): AccentPalette =
            entries.firstOrNull { it.wireValue == value } ?: Classic
    }
}

/**
 * The accent for the current appearance.
 *
 * `@Composable` for the same reason [ttCanvas] is: the answer depends on the current
 * configuration, and a `val` read once keeps painting the light accent through a
 * dark-mode switch.
 *
 * Reads [ttIsDarkTheme] rather than calling `isSystemInDarkTheme` directly, which is the
 * whole difference between a pinned appearance and no pinned appearance: a direct call
 * would ignore `ThemeScheme` entirely, and `Light` and `Dark` would be settings that save
 * and change nothing. The contrast bars in the class docs are stated per appearance for
 * the same reason — an accent has to clear them against the canvas it is actually on, and
 * "actually on" is now something the user can choose.
 */
@Composable
fun ttAccent(palette: AccentPalette = LocalAccentPalette.current): Color =
    if (ttIsDarkTheme()) palette.dark else palette.light
