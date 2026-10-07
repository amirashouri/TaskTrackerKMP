package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing

/**
 * The selected option's wash, as a share of the accent.
 *
 * 25%: composited over the pixel test's near-black backdrop it lands at a channel distance of
 * 21 for the *dimmest* palette (the default Classic, 0xFF5F6873) and 47 for the most vivid, so
 * every palette clears the test's 12-channel bar with margin, while the row still reads as
 * glass rather than as a filled chip.
 *
 * `internal` so [ThemeOptionScenarios] measures the same alpha the render paints.
 */
internal const val SelectedOptionFillAlpha = 0.25f

/**
 * The shared option-row surface for both dropdowns: clipped, and washed in the accent when
 * selected — deliberately unbordered.
 *
 * One function because the two dropdowns are one control and changed together when the capsule
 * border was removed on request; a ring spelled in two copies is a ring that gets removed once
 * and then still drawn somewhere.
 */
@Composable
internal fun Modifier.dropdownOptionSurface(isSelected: Boolean): Modifier =
    clip(RoundedCornerShape(ttRadius.control))
        .background(
            color = if (isSelected) ttAccent().copy(alpha = SelectedOptionFillAlpha) else Color.Transparent,
            shape = RoundedCornerShape(ttRadius.control),
        )

internal object SettingsDropdownPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val preferredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
            LayoutDirection.Rtl -> anchorBounds.left
        }
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val y = if (below + popupContentSize.height <= windowSize.height) below else above
        return IntOffset(
            x = preferredX.coerceIn(0, maxX),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** The theme dropdown's own width, shared so the two popups on this page are the same size. */
internal val ThemeDropdownWidth = 216.dp

/** The shared popup column: glass surface, control rounding, and the option inset. */
@Composable
internal fun SettingsDropdownColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(ThemeDropdownWidth)
            .ttGlassSurface(
                shape = RoundedCornerShape(ttRadius.control),
                fillAlpha = 0.81f,
            )
            .padding(vertical = ttSpacing.xs),
        content = { content() },
    )
}
