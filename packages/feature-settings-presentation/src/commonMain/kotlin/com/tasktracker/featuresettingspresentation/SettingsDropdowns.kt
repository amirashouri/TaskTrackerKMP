package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSelectionWash
import com.tasktracker.designsystem.ttSpacing

/**
 * The shared option-row surface for both dropdowns: clipped, and washed in the accent when
 * selected — deliberately unbordered.
 *
 * One function because the two dropdowns are one control and changed together when the capsule
 * border was removed on request; a ring spelled in two copies is a ring that gets removed once
 * and then still drawn somewhere.
 *
 * The wash itself is [ttSelectionWash] — the alpha, the spring, and the fact that it animates
 * are all the design system's to own. This function is what stays here: the rounding, the clip,
 * and the transparent resting state.
 */
@Composable
internal fun Modifier.dropdownOptionSurface(isSelected: Boolean): Modifier =
    clip(RoundedCornerShape(ttRadius.control))
        .ttSelectionWash(selected = isSelected)

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
