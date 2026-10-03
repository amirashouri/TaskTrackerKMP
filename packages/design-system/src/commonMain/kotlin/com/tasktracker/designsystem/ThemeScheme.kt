package com.tasktracker.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The appearance the user has chosen, and how it is stored.
 *
 * ## Three cases, not a boolean
 *
 * A `isDark: Boolean` cannot express "follow the system", because that is not the absence
 * of a choice — it is a third choice, and the one most people want. The closest a two-way
 * switch gets is to store whichever state the device happened to be in the last time the
 * user looked, which is how an app ends up permanently light for anyone who opens it at
 * night and permanently dark for anyone who opens it at lunch. `System` is that option,
 * made explicit, and it is the default.
 *
 * ## The stored value is the contract
 *
 * [wireValue] is what lands in the plist, and the original wrote the same three strings.
 * They are declared here, on the enum, rather than in the screen that reads them, so the
 * reader and the writer cannot drift apart and a rename cannot half-land: a build that
 * changed the literals would read every existing install's `dark` as unrecognised and
 * silently reset it. [fromStoredValue] treats exactly that as the failure it is, and
 * falls back to [System] rather than throwing — a preference nobody can parse must not
 * stop the app from launching.
 *
 * ## Why the key is not in `DefaultsKey`
 *
 * `DefaultsKey` is session-scoped state: sign-out walks the enum and clears it, which is
 * right for `LastSeenProject` and `CachedProfile`. An appearance choice is a property of
 * the device, not of the account, so it is stored through the store's un-enummed
 * overloads and survives by construction. `PLAN.md` records the same reasoning in the
 * original's own terms.
 */
enum class ThemeScheme(val wireValue: String, val title: String) {

    /** No opinion: follow the platform, live, including while it is being changed. */
    System("system", "System"),

    /** Pinned light, whatever the platform says. */
    Light("light", "Light"),

    /** Pinned dark, whatever the platform says. */
    Dark("dark", "Dark"),
    ;

    /**
     * The appearance to paint, or `null` to follow the platform.
     *
     * `null` is the load-bearing part. It is the difference between "no opinion" and
     * "pinned light", and a design system that collapsed it to a non-null default would
     * make [System] unreachable — the app would stop following the system while claiming
     * to. [ttIsDarkTheme] is what actually consumes it.
     */
    val colorScheme: ttColorScheme?
        get() = when (this) {
            System -> null
            Light -> ttColorScheme.Light
            Dark -> ttColorScheme.Dark
        }

    /**
     * Resolves a stored value, falling back to [System] for anything unrecognised.
     *
     * Every unreadable input resolves the same way, deliberately: absent, empty, a value
     * from a future build, a value with the wrong case, and a value that is not a theme
     * name at all. They are indistinguishable to the app, and they all mean the same thing
     * — the user has not expressed a preference this build can honour.
     *
     * A missing *case* is a programming error and stays an error: [of] is `require`, not a
     * silent fallback, because a call site naming a scheme that no longer exists is a bug
     * to fix at compile time rather than a value to guess at.
     */
    companion object {

        /**
         * The plist key, matching the original's `ThemeScheme.defaultsKey` exactly.
         *
         * Not in `DefaultsKey`, and the reason is in that type's docs: sign-out clears
         * every enum member, and an appearance preference must outlive the session.
         */
        const val defaultsKey: String = "com.tasktracker.settings.themeScheme"

        /** Every scheme, in the order a picker should present them: system first. */
        val all: List<ThemeScheme> = entries

        /** See [fromStoredValue]; the exhaustive form, for a literal that must be known. */
        fun of(wireValue: String): ThemeScheme =
            requireNotNull(entries.firstOrNull { it.wireValue == wireValue }) {
                "\"$wireValue\" is not a theme scheme; the wire values are " +
                    entries.joinToString { it.wireValue }
            }

        /** See the class docs. Unknown, absent and unparseable all become [System]. */
        fun fromStoredValue(wireValue: String?): ThemeScheme =
            entries.firstOrNull { it.wireValue == wireValue } ?: System
    }
}

/**
 * The two pinned appearances, as a value the tokens can read.
 *
 * Not `androidx.compose.material3.ColorScheme`: this build has no Material3 dependency,
 * on purpose, because Material3 would replace the adaptive semantic colours in
 * [ttCanvas] and friends with a fixed scheme — the exact divergence from the original
 * that the design decision rejected. A two-case type is also the honest shape here:
 * there is nothing else to describe, and naming it `ColorScheme` would imply a palette
 * this design system deliberately does not have.
 */
enum class ttColorScheme {
    Light,
    Dark,
    ;

    /** The opposite appearance, for the "pinned the other way" case. */
    val opposite: ttColorScheme
        get() = if (this == Light) Dark else Light
}

/**
 * The appearance in force, [ThemeScheme.System] by default.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`, because nothing reads this
 * to decide *whether* to recompose — every reader is a colour token that must be recomposed
 * when the answer changes anyway. A `staticCompositionLocalOf` is cheaper for a value this
 * coarse, and it also makes a missing provider a compile-and-test-visible default instead
 * of a silent one.
 */
val LocalThemeScheme = staticCompositionLocalOf { ThemeScheme.System }

/** The accent palette selected for the current app composition. */
val LocalAccentPalette = staticCompositionLocalOf { AccentPalette.Classic }

/**
 * Whether the app is painting dark right now, in [scheme] terms.
 *
 * This is the single place "what appearance is in force" is decided, and every appearance-
 * dependent token reads it. The alternative — each token calling `isSystemInDarkTheme()`
 * itself, as the platform actuals did before [ThemeScheme] existed — cannot honour a
 * pinned appearance at all, and would leave `Light` and `Dark` as settings that change
 * nothing.
 */
@Composable
fun ttIsDarkTheme(scheme: ThemeScheme = LocalThemeScheme.current): Boolean = when (scheme) {
    ThemeScheme.System -> isSystemInDarkTheme()
    ThemeScheme.Light -> false
    ThemeScheme.Dark -> true
}

/**
 * Pins the appearance for this subtree, and nothing above or beside it.
 *
 * The *root* applies the stored scheme so a new feature cannot forget; previews and tests
 * pin it locally instead. [ThemeScheme.System] is a valid argument and means "stop
 * overriding", which is what makes it safe for a preview to wrap something the real root
 * also wraps.
 */
@Composable
fun ttTheme(
    scheme: ThemeScheme,
    accentPalette: AccentPalette = AccentPalette.Classic,
    glassIntensity: Float = 1f,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalThemeScheme provides scheme,
        LocalAccentPalette provides accentPalette,
        // Provided here rather than by the shell wrapping this call, because this is already
        // the one place the three appearance inputs are assembled — a second provider around
        // it would be a second place to forget. Defaulted to full glass so the splash, the
        // previews and every test host that does not ask for a different amount keep painting
        // the measured `ttGlass` values.
        LocalGlassIntensity provides glassIntensity.coerceIn(0f, 1f),
        content = content,
    )
}

/**
 * The platform's dynamic answer while following the system, the pinned value otherwise.
 *
 * Lives here rather than in a platform file because it is the one piece of the pinned-versus-
 * dynamic rule that all three targets need, and two copies of a rule about which value wins
 * is how a target ends up quietly disagreeing about what "dark" means.
 *
 * [dynamic] is `@Composable` because on Android it reads `LocalContext`; [pinned] is not,
 * because [ttFixedColours] is a plain lookup. The split is deliberate rather than
 * convenient — it keeps the pinned path from being able to reach a platform at all, which is
 * the property that makes a pinned appearance identical on every target.
 */
@Composable
internal inline fun dynamicOrPinned(
    pinned: (dark: Boolean) -> Color,
    dynamic: @Composable () -> Color,
): Color = when (LocalThemeScheme.current.colorScheme) {
    null -> dynamic()
    ttColorScheme.Light -> pinned(false)
    ttColorScheme.Dark -> pinned(true)
}

/**
 * The light and dark values of one semantic colour, used when the appearance is **pinned**.
 *
 * ## Why a pinned appearance cannot use the platform's dynamic colour
 *
 * `ttCanvas` is `UIColor.systemBackground` on iOS and `?android:attr/colorBackground` on
 * Android, and both are *dynamic* — they resolve against whatever the platform currently
 * says. That is exactly right while following the system, and impossible while pinned: the
 * platform has no way to be told "the user wants light and the system is dark", and
 * `resolvedColorWithTraitCollection` or `resolveAttribute` will hand back the system's
 * answer regardless. A pinned dark appearance on a light device would then paint the light
 * canvas with a dark accent on it, and every contrast pairing in `AccentContrastTest` would
 * be asserting against a canvas nobody sees.
 *
 * So a pinned appearance stops consulting the platform and uses these values, and only a
 * following appearance uses the dynamic one. The two are the same colours the platform
 * reports for that appearance — the jvm target, which has no platform theme to ask, is
 * built from exactly these pairs, which is what keeps the three targets agreeing.
 *
 * Canvas light is white and canvas dark is black because that is what `systemBackground`
 * reports in each appearance; the iOS dark `CanvasBackground` colorset is pure black, and
 * `AccentContrastTest` measures the dark accent against it.
 */
object ttFixedColours {

    /** `ttCanvas`, pinned. */
    val canvasLight: Color = Color(0xFFFFFFFF)
    val canvasDark: Color = Color(0xFF000000)

    /** `ttSurface`, pinned: UIKit's `secondarySystemBackground` in each appearance. */
    val surfaceLight: Color = Color(0xFFF2F2F7)
    val surfaceDark: Color = Color(0xFF1C1C1E)

    /**
     * `ttSeparator`, pinned: UIKit's `separator` resolved in each appearance, kept as a
     * pre-composited RGBA rather than an opaque grey.
     *
     * A solid grey is the wrong shape for a hairline. What makes a separator sit *behind*
     * content instead of drawing a line through it is its alpha, not its hue, so both
     * entries carry an alpha below 1 and a caller that composites them over a canvas gets
     * something close to the platform's answer.
     *
     * The values are UIKit's: `(60, 60, 67)` at 0.29 on light and `(84, 84, 88)` at 0.36
     * on dark, with the alpha byte rounded to `0x4A` and `0x5C`. Two notes on that, because
     * the first draft of this file got both wrong:
     *
     *  - they are **tinted**, not black and white. A separator that is pure black at low
     *    alpha reads as dirt on a light canvas in a way the tinted one does not.
     *  - the alpha is not the *red* byte. `separator` is famously `60, 60, 67` in light, and
     *    `Color(0x3C000000)` — which is what this file had — is 60 read as the alpha with
     *    the hue thrown away. It is a plausible-looking number for a token nobody had
     *    checked, which is why `PinnedAppearanceTest` now asserts the alpha rather than
     *    trusting this comment.
     *
     * The tint is the one number here not traceable to the original, which resolved
     * `UIColor.separator` at runtime rather than spelling it out; these are the documented
     * values for it.
     */
    val separatorLight: Color = Color(0x4A3C3C43)
    val separatorDark: Color = Color(0x5C545458)

    /**
     * `ttLabel`, pinned: UIKit's `label` is black in light and white in dark, and Android's
     * `textColorPrimary` matches.
     */
    val labelLight: Color = Color(0xFF000000)
    val labelDark: Color = Color(0xFFFFFFFF)

    /**
     * `ttLabelSecondary`, pinned: `secondaryLabel` at 60% — `(60, 60, 67)` on light and
     * `(235, 235, 245)` on dark — rather than an opaque grey.
     *
     * Pre-composited the same way [separatorLight] is, as an RGBA with alpha below 1, so a
     * caller that draws it over a surface gets a colour that works on either background
     * instead of one that only looks right on a single one. The `0x99` alpha is 0.60 of 255.
     *
     * The 60% is what makes this token *secondary* — and it is also this token's one
     * accessibility cost, which a caller needs before using it. Composited over its canvas
     * it measures **3.44:1** on light and **3.64:1** on dark, which clears WCAG's 3:1
     * large-text bar and does *not* clear the 4.5:1 body-text bar. That is not a rounding
     * error in the port; it is what UIKit's own `secondaryLabel` measures, and the original
     * used it on footnotes and captions only. Those are 11-13sp, below the size WCAG calls
     * "large", so the honest statement is:
     *
     *  - correct for footnotes, captions, and other de-emphasised supporting text;
     *  - not correct for body copy, which is what [labelLight] / [labelDark] are for.
     *
     * `PinnedAppearanceTest` pins the ratio so a change to either the alpha or the canvas
     * is a deliberate one.
     */
    val labelSecondaryLight: Color = Color(0x993C3C43)
    val labelSecondaryDark: Color = Color(0x99EBEBF5)

    /**
     * `ttError`, pinned: UIKit's `systemRed` in each appearance.
     *
     * The original writes `Color.red`, which is a *fixed* colour on every platform rather
     * than a semantic one — so unlike every other token here it does not change with the
     * appearance. UIKit still has an adaptive answer, and it is the better one: `systemRed`
     * is `#FF3B30` on light and `#FF453A` on dark, so the two are not the same red. Pinning
     * therefore resolves the platform's pair rather than one value used twice, which is what
     * keeps a pinned dark appearance from painting a light-appearance red on a black canvas.
     *
     * The values are the documented iOS ones and are among the few numbers in this file not
     * traceable to the original, which delegated to `Color.red`; see `PinnedAppearanceTest`.
     */
    val errorLight: Color = Color(0xFFFF3B30)
    val errorDark: Color = Color(0xFFFF453A)

    /** [errorLight] or [errorDark], for the appearance in force. */
    fun error(dark: Boolean): Color = if (dark) errorDark else errorLight

    /** [labelLight] or [labelDark], for the appearance in force. */
    fun label(dark: Boolean): Color = if (dark) labelDark else labelLight

    /** [labelSecondaryLight] or [labelSecondaryDark], for the appearance in force. */
    fun labelSecondary(dark: Boolean): Color =
        if (dark) labelSecondaryDark else labelSecondaryLight

    /** [canvasLight] or [canvasDark], for the appearance in force. */
    fun canvas(dark: Boolean): Color = if (dark) canvasDark else canvasLight

    /** [surfaceLight] or [surfaceDark], for the appearance in force. */
    fun surface(dark: Boolean): Color = if (dark) surfaceDark else surfaceLight

    /** [separatorLight] or [separatorDark], for the appearance in force. */
    fun separator(dark: Boolean): Color = if (dark) separatorDark else separatorLight
}
