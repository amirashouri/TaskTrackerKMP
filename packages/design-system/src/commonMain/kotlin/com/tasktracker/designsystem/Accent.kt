package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand accent color tokens for light and dark appearances. */
object TaskTrackerAccent {
    val Light: Color = Color(0xFF34537A)
    val Dark: Color = Color(0xFF5F6873)
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

/** Resolves active theme accent color based on current dark theme state and palette. */
@Composable
fun ttAccent(palette: AccentPalette = LocalAccentPalette.current): Color =
    if (ttIsDarkTheme()) palette.dark else palette.light