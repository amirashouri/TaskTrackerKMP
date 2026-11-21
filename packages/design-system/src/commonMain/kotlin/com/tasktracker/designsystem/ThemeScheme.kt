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
 * overloads and survives by construction. `docs/PORT-LOG.md` records the same reasoning in the
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
val LocalAccentPalette = staticCompositionLocalOf { AccentPalette.Slot1 }

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
    accentPalette: AccentPalette = AccentPalette.Slot1,
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
     * `ttLabelSecondary`: a supporting label, composited the same way [separatorLight] is.
     *
     * ## The two appearances are not the same colour, or the same fraction
     *
     * Dark is UIKit's `secondaryLabel` at 60% — `(235, 235, 245)` at `0x99`, measuring
     * **6.36:1** on the canvas. Light is body ink, `(28, 28, 30)`, at **78%** (`0xC7`):
     * **8.31:1** on the canvas and **4.74:1** over the rendered glass fill.
     *
     * Light used to be `(52, 52, 58)` at 60%, the platform's `secondaryLabel` darkened by
     * eight channels. That measured 3.68:1 on the canvas, but only **2.55:1** over the
     * glass the captions actually sit on — the token carries 11–13sp supporting text and
     * eleven project screens put it over a glass surface. Raising the alpha alone was not
     * enough: at that RGB it needed about 95% to clear 4.5:1 over the glass, which is a
     * secondary label in name only. Body ink at 78% clears both surfaces and still sits
     * visibly below [labelLight]'s 21:1 on the canvas, which is the hierarchy this token
     * exists to express.
     *
     * The asymmetry is inherent to the colour pair rather than a defect: `secondaryLabel`
     * is a dark grey on light and a pale grey on dark, and 60% of white on black is much
     * lighter than 60% of black on white. Any statement about this token has to be
     * per-appearance. `GlassSurfacePixelTest` holds the glass reading and
     * `PinnedAppearanceTest` holds the canvas pairing at 4.5:1.
     */
    val labelSecondaryLight: Color = Color(0xC71C1C1E)
    val labelSecondaryDark: Color = Color(0x99EBEBF5)

    /**
     * `ttError`, pinned: a deep red that carries a white label.
     *
     * ## Why this is not UIKit's `systemRed`
     *
     * It was: `#FF3B30` on light and `#FF453A` on dark, the documented `systemRed` pair. Those
     * are the platform's own values and they are correct for the platform's own components —
     * but a platform does not choose a red *for a white label on it*, because it has no
     * filled destructive button to label. Measured, a white `ttOnAccent` label on them:
     *
     * | | was | now |
     * | --- | --- | --- |
     * | light | **3.55:1** | **5.11:1** |
     * | dark | **3.41:1** | **4.76:1** |
     *
     * Three call sites paint that pairing — `ProjectsListScreen.kt:340`,
     * `WorkItemEditor.kt:453` and `:625`, all `TTGlassButtonStyle(tint = ttError(), prominent =
     * true)` — so a destructive action's label was below the 4.5:1 body-text bar in *both*
     * appearances. Dark was the worse of the two, which is the opposite of every other pairing
     * in this package and is what made it look like rounding rather than a gap.
     *
     * ## Why darkening was safe, which was not obvious
     *
     * Darkening the red helps white-on-red and *hurts* red-on-black, so the fix could have
     * traded one failure for another. It does not, because both bars are satisfiable at once:
     * white-on-red ≥ 4.5:1 needs relative luminance ≤ 0.18333, and red-on-black ≥ 3:1 needs
     * ≥ 0.10. The window between them is where both of these sit.
     *
     * | pairing | light | dark | bar |
     * | --- | --- | --- | --- |
     * | white label on the fill | 5.11:1 | 4.76:1 | 4.5 |
     * | the glyph against the canvas | 5.11:1 | 4.41:1 | 3.0 |
     * | `ttLabel` ink on the 10% fill | 17.98:1 | 19.92:1 | 4.5 |
     *
     * Hue is UIKit's `systemRed` hue, held at **3.2°**, and saturation is **0.74** rather than
     * 1.0. The desaturation is deliberate: clamping lightness alone lands on `#ED0D00`, a
     * fully-saturated primary that satisfies the number and looks like an alarm. These are a
     * shade deeper and noticeably calmer.
     *
     * The two differ (`#D3291F` / `#DC2B21`), which is what `PinnedAppearanceTest` requires of
     * every token here and what the platform pair did.
     *
     * ## Why the platform no longer supplies this
     *
     * `ttError` used to resolve `UIColor.systemRed` on iOS and `android.R.attr.colorError` on
     * Android, so **a pinned appearance and a following appearance got different reds** — and
     * following-the-system would have kept handing back the failing `#FF3B30` regardless of
     * anything written here. That is the same situation as [labelSecondary], which already
     * ignores the dynamic path because a platform's answer is a role and this is a decision.
     * `ttError` is a pairing constraint, exactly as [ttOnAccent] is, so it is now a token in
     * both directions. See `Accent.kt` for the same argument about the accent.
     */
    val errorLight: Color = Color(0xFFD3291F)
    val errorDark: Color = Color(0xFFDC2B21)

    /** [errorLight] or [errorDark], for the appearance in force. */
    fun error(dark: Boolean): Color = if (dark) errorDark else errorLight

    /**
     * The four presence colours, per appearance.
     *
     * The first set was fixed Material 500/600 values. Measured on the light canvas, only the
     * red cleared the text bar: amber was 1.94:1, grey 2.68:1 and green 3.30:1. The settings
     * row painted a status *name* in these, so three of four were below 4.5:1 at 15sp.
     *
     * These pairs clear 4.5:1 on their own canvas and 3:1 on the card surface, so a status
     * may be used as a dot or as a label without a second token. The dark values are the
     * lighter Material 300/400 steps; the light ones are the 700/800 steps, which is the
     * direction both appearances need.
     */
    val statusActiveLight: Color = Color(0xFF2E7D32)
    val statusActiveDark: Color = Color(0xFF66BB6A)
    val statusBeRightBackLight: Color = Color(0xFFA35F00)
    val statusBeRightBackDark: Color = Color(0xFFFFB74D)
    val statusBusyLight: Color = Color(0xFFC62828)
    val statusBusyDark: Color = Color(0xFFEF5350)
    val statusAwayLight: Color = Color(0xFF6B6B70)
    val statusAwayDark: Color = Color(0xFFBDBDBD)

    /** [statusActiveLight] or [statusActiveDark], for the appearance in force. */
    fun statusActive(dark: Boolean): Color = if (dark) statusActiveDark else statusActiveLight

    /** [statusBeRightBackLight] or [statusBeRightBackDark]. */
    fun statusBeRightBack(dark: Boolean): Color =
        if (dark) statusBeRightBackDark else statusBeRightBackLight

    /** [statusBusyLight] or [statusBusyDark]. */
    fun statusBusy(dark: Boolean): Color = if (dark) statusBusyDark else statusBusyLight

    /** [statusAwayLight] or [statusAwayDark]. */
    fun statusAway(dark: Boolean): Color = if (dark) statusAwayDark else statusAwayLight

    /** [labelLight] or [labelDark], for the appearance in force. */
    fun label(dark: Boolean): Color = if (dark) labelDark else labelLight

    /** [labelSecondaryLight] or [labelSecondaryDark], for the appearance in force. */
    fun labelSecondary(dark: Boolean): Color =
        if (dark) labelSecondaryDark else labelSecondaryLight

    /** Body-text ink over the opaque card surface, by appearance. */
    val bodyLabelLight: Color = Color(0xFF1C1C1E)
    val bodyLabelDark: Color = Color(0xFFF2F2F7)

    /**
     * Body-text ink over the opaque card surface, for the appearance in force.
     *
     * The same role [labelSecondary] fills over an opaque background — a footnote or
     * caption under a title — except this is the white/ink pairing that also clears
     * WCAG's 4.5:1 body-text bar, so it is the token a screen that puts supporting
     * text over a card reaches for. White ink over an opaque dark surface is 17:1 in
     * both appearances, and black ink over an opaque light surface is the same, so the
     * pairing is not appearance-dependent: the ink is the surface and the surface is
     * the ink, and that is what keeps the two always legible.
     *
     * The card's own fill is [surface]. A label over a *translucent glass bar* is a
     * different question: the bar is not opaque, and at low glass intensity the
     * material is a flat slab of the accent that white ink measures 5.6:1 against —
     * below the 4.5:1 bar. A glass bar is not a body-text surface, and the slider's
     * 20% mark exists precisely to show why. `ttBodyLabel` is not a substitute for
     * that; it is the token a screen reaches for when it wants supporting text over a
     * card, where the surface is opaque and the pairing is honest.
     */
    fun bodyLabel(dark: Boolean): Color =
        if (dark) bodyLabelDark else bodyLabelLight

    /** [canvasLight] or [canvasDark], for the appearance in force. */
    fun canvas(dark: Boolean): Color = if (dark) canvasDark else canvasLight

    /** [surfaceLight] or [surfaceDark], for the appearance in force. */
    fun surface(dark: Boolean): Color = if (dark) surfaceDark else surfaceLight

    /** [separatorLight] or [separatorDark], for the appearance in force. */
    fun separator(dark: Boolean): Color = if (dark) separatorDark else separatorLight
}
