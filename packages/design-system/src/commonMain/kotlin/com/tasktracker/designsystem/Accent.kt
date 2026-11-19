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

/** Alternative accent palette options. */
enum class AccentPalette(
    val wireValue: String,
    val title: String,
    val light: Color,
    val dark: Color,
) {
    Classic("classic", "Classic", TaskTrackerAccent.Light, TaskTrackerAccent.Dark),
    ClassicLight("classic-light", "C-Light", Color(0xFF49688F), Color(0xFF416A9A)),
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

/** Resolves active theme accent color based on current dark theme state and palette. */
@Composable
fun ttAccent(palette: AccentPalette = LocalAccentPalette.current): Color =
    if (ttIsDarkTheme()) palette.dark else palette.light