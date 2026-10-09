package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The ink for a label that marks a selected item, shared by every surface with a glass selection
 * lens — the tab bar ([TTGlassTabBar]) and the settings accent picker.
 *
 * It resolves per appearance for an arithmetic reason rather than taste, and the arithmetic is
 * the same wherever the selected item's own colour was [ttAccent]. Under a glass lens the
 * backdrop is not the opaque surface [ttLabel] and [ttLabelSecondary] were chosen against, so
 * both have to be re-derived:
 *
 * - **Selected, dark.** [ttLabel] — white. The accent itself misses the 4.5:1 normal text needs on
 *   both glass-selection surfaces, by different amounts because the backdrops differ: 4.22:1 under
 *   the picker's pane and 2.83:1 on the tab bar's fill. White clears it on both with room to
 *   spare (15.33:1 on the picker's canvas).
 * - **Selected, light.** [ttAccent] mixed [SELECTION_LABEL_SELECTED_DARKENING] toward [ttLabel].
 *   The raw accent clears 4.5:1 on the JVM but misses it on iOS, whose rasteriser draws a
 *   caption-sized stroke at roughly 85% coverage and therefore reads lighter than the token it
 *   was given — the same token on the same backdrop measures 5.37:1 on the JVM and 3.98:1 on
 *   iOS. Darkening keeps the hue recognisably the accent and clears the bar on both.
 * - **Unselected, dark.** [ttLabelSecondary] — the platform's grey, which lands on a near-black
 *   backdrop at roughly 5.9:1 and needs no correction.
 * - **Unselected, light.** [ttLabel] at [SELECTION_LABEL_INACTIVE_ALPHA]. `secondaryLabel` is the
 *   label at 60%, chosen against an *opaque* `systemBackground`; under a translucent glass wash
 *   the effective backdrop is a mid grey around `#C5CEDA`, where 60% measures 2.78:1. 82% clears
 *   the bar on the worse rasteriser while still sitting below the selected ink above it.
 *
 * Every number here is held on both rasterisers by `TabLensContrastTest`, which renders the real
 * tab bar, and by `AccentSwatchScenarios`, which renders the picker's label under its own pane.
 */
@Composable
fun ttSelectionLabelInk(selected: Boolean): Color =
    if (selected) {
        if (ttIsDarkTheme()) {
            ttLabel()
        } else {
            lerp(ttAccent(), ttLabel(), SELECTION_LABEL_SELECTED_DARKENING)
        }
    } else {
        if (ttIsDarkTheme()) {
            ttLabelSecondary()
        } else {
            ttLabel().copy(alpha = SELECTION_LABEL_INACTIVE_ALPHA)
        }
    }

/**
 * How much of [ttLabel] an unselected label carries on the light appearance.
 *
 * The value is set by the *worse* of the two rasterisers, not the better one. The same token and
 * the same backdrop measure 6.07:1 on the JVM and 4.36:1 on the iOS simulator, because a small
 * glyph's stroke reaches full coverage on one and not the other, and a stroke that is 82%
 * covered is a lighter ink than the token it was drawn with. Tuning to the JVM number would have
 * left iOS short, which is the whole reason the contrast tests run on both.
 */
private const val SELECTION_LABEL_INACTIVE_ALPHA = 0.82f

/**
 * How far the light-appearance selected label is mixed from [ttAccent] toward [ttLabel].
 *
 * A fifth. Enough to clear 4.5:1 on the iOS rasteriser, which draws a caption-sized stroke at
 * roughly 85% coverage and therefore reads lighter than the token it was given, while leaving the
 * hue recognisably the accent.
 */
private const val SELECTION_LABEL_SELECTED_DARKENING = 0.20f
