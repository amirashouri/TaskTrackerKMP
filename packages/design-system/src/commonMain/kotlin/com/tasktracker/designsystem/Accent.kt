package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand accent color tokens for light and dark appearances. */
object TaskTrackerAccent {
    val Light: Color = Color(0xFF34537A)

    /**
     * The light value's hue at a chroma the dark canvas can carry, rather than a grey.
     *
     * The previous `#5F6873` was chosen only to clear the white-label bar, and it did: 5.55:1
     * on the label and 3.26:1 on black. But it is almost neutral (HSL saturation 0.037), so
     * the default accent changed from navy to grey the moment the user entered dark mode —
     * the one token that is supposed to be the brand. `#44688F` is the same hue (211.2° to
     * the light value's 213.4°), measures 5.80:1 against the white label and 3.62:1 on black,
     * and sits at luminance 0.131 inside the 0.10–0.1833 window where both bars are
     * simultaneously satisfiable. It also keeps 4.86:1 for that label through the 8% white
     * lens, which the brighter `#4E74A8` did not (4.11:1). `AccentContrastTest` holds the
     * bars and the window; `GlassFillContrastTest` holds the lens.
     */
    val Dark: Color = Color(0xFF44688F)
    val all: List<Color> = listOf(Light, Dark)
}

/**
 * Alternative accent palette options.
 *
 * A constant is a *slot*, not a colour. [wireValue] is the identity a stored preference
 * contains, and it never moves; [title], [light] and [dark] are presentation and may change
 * freely. Recolouring a palette edits those three and nothing else, so a stored choice
 * survives a rename — the thing that would break if the identity were the constant's `name`
 * or `ordinal`.
 *
 * The slots differ by hue because the contrast contract pins every accent into the
 * 0.10–0.1833 luminance window (4.5:1 for the white label, 3:1 against the canvas), which
 * both the light and the dark value must sit inside. Lightness is not an axis a palette can
 * vary on; see `AccentContrastTest`.
 *
 * `wireValue` keeps its name for symmetry with [ThemeScheme.wireValue]; it is local
 * storage, not a server contract.
 */
enum class AccentPalette(
    val wireValue: String,
    val title: String,
    val light: Color,
    val dark: Color,
) {
    /** The default brand blue. */
    Slot1("accent-1", "Classic", TaskTrackerAccent.Light, TaskTrackerAccent.Dark),

    /**
     * The green slot, which replaced a second navy.
     *
     * The old pair shared Classic's 213° hue and its dark value sat 0.007 luminance away, so
     * in dark mode the picker showed three swatches that were the same colour.
     */
    Slot2("accent-2", "Emerald", Color(0xFF0A5C3E), Color(0xFF047857)),
    Slot3("accent-3", "Music", Color(0xFFB0004B), Color(0xFFD81B60)),
    Slot4("accent-4", "Sunset", Color(0xFFA8320A), Color(0xFFCA3F12)),
    Slot5("accent-5", "Violet", Color(0xFF5B21B6), Color(0xFF8845D2)),

    /**
     * The warm slot, which replaced the third navy (`#234D82` / `#45658F`) — the one whose
     * dark value was 0.006 luminance from Classic's. See [Slot2].
     */
    Slot6("accent-6", "Amber", Color(0xFF7A5200), Color(0xFF8A6100)),
    ;

    companion object {
        val all: List<AccentPalette> = entries

        fun fromStoredValue(value: String?): AccentPalette =
            entries.firstOrNull { it.wireValue == value } ?: Slot1
    }
}

/** Resolves active theme accent color based on current dark theme state and palette. */
@Composable
fun ttAccent(palette: AccentPalette = LocalAccentPalette.current): Color =
    if (ttIsDarkTheme()) palette.dark else palette.light