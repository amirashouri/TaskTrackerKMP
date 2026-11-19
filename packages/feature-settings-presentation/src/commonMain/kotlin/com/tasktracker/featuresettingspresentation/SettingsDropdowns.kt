package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassLens
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLensPlacement
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import kotlin.math.roundToInt

/** Stands in for "no selection" in [SettingsDropdownColumn]'s lens placement. */
private object NoSelection

/** The travelling lens inside the dropdown pane, addressable the way the navigation's is. */
const val SettingsDropdownLensTag = "settings.dropdown.lens"

/**
 * Where [SettingsDropdownColumn] hands its option rows the place to report their bounds.
 *
 * A composition local rather than a parameter on every option: the options are already separate
 * composables with their own call sites (three pickers, three shapes of row), and threading a
 * measurement callback through each would put the lens in three places instead of one. `null`
 * outside a pane, which is what a standalone [ThemePickerOption] in a test sees.
 */
internal val LocalDropdownOptionRows =
    compositionLocalOf<MutableMap<Any, LayoutCoordinates>?> { null }

/**
 * Marks an option row as one the pane's lens travels between, under [key].
 *
 * The row itself draws nothing: the pane draws one glass lens under whichever row is selected,
 * and moves it when the selection changes. That is the navigation's arrangement — one material
 * under a moving selection rather than a highlight switching on inside each cell — and it is why
 * this modifier no longer carries a wash of its own.
 */
@Composable
internal fun Modifier.dropdownOption(key: Any): Modifier {
    val rows = LocalDropdownOptionRows.current
    return if (rows == null) this else this.onGloballyPositioned { rows[key] = it }
}

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

/**
 * The shared popup: glass surface, control rounding, the option inset, and the selection lens.
 *
 * [width] is a parameter rather than a constant because the member picker in the
 * change-password sheet lists names and addresses, which do not fit the theme dropdown's
 * 216dp. Everything else about the popups — surface, thickness, rounding, inset, lens — is
 * still one copy, which is what this function exists to keep true.
 *
 * [selection] is the key of the option that is currently chosen, or `null` for a list with no
 * selection. It is `Any` rather than a type parameter so the three pickers can each pass their
 * own enum without this function knowing any of them.
 *
 * ## The lens and its measurements
 *
 * The pane measures each option row and draws one lens under the selected one, the same
 * `ttLensPlacement` the navigation and the segmented pickers use: the lens springs between rows
 * instead of each row fading its own wash in, and it carries the same wash, rim and motion trail
 * as the rest of the app. The rows hand over their `LayoutCoordinates` and the rectangles are
 * built *here*, in composition, rather than inside the callbacks — a row can be measured before
 * the pane is, and a callback that wanted the pane's coordinates would then record nothing and
 * never run again. Read this way the order the two arrive in does not matter.
 */
@Composable
internal fun SettingsDropdownColumn(
    selection: Any?,
    width: Dp = ThemeDropdownWidth,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rowCoordinates = remember { mutableStateMapOf<Any, LayoutCoordinates>() }
    var paneCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pane = paneCoordinates
    val rowRects: Map<Any, IntRect> = if (pane == null) {
        emptyMap()
    } else {
        rowCoordinates.mapValues { (_, row) ->
            val topLeft = pane.localPositionOf(row, Offset.Zero)
            val left = topLeft.x.roundToInt()
            val top = topLeft.y.roundToInt()
            IntRect(
                left = left,
                top = top,
                right = left + row.size.width,
                bottom = top + row.size.height,
            )
        }
    }
    // A sentinel rather than a nullable call: `ttLensPlacement` remembers its animated values
    // across recompositions, so it has to be called unconditionally. Nothing is selected in that
    // case and the lens below is not composed at all.
    val lens = ttLensPlacement(rowRects, selection ?: NoSelection, density)
    val shape = RoundedCornerShape(ttRadius.control)

    Column(
        modifier = Modifier
            .width(width)
            .ttGlassSurface(
                shape = shape,
                thickness = ttGlass.GlassThickness.THICK,
                base = ttSurface().copy(alpha = DROPDOWN_BASE_ALPHA),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { paneCoordinates = it },
        ) {
            // Drawn before the rows and behind them, and only once the row it marks has been
            // measured: a lens born at the pane's top-left corner would slide in from there on
            // the first frame of every opening.
            if (selection != null && selection in rowRects) {
                Box(
                    modifier = Modifier
                        .size(lens.width, lens.height)
                        .offset(x = lens.x, y = lens.y)
                        .testTag(SettingsDropdownLensTag)
                        .ttGlassLens(shape, velocityX = lens.velocityX),
                )
            }

            CompositionLocalProvider(LocalDropdownOptionRows provides rowCoordinates) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ttSpacing.xs),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * How much of [ttSurface] the popup's glass is backed with, on a 0..1 scale.
 *
 * A popup is not a bar. The tab bar and the sidebar sit on the canvas at the edge of the
 * screen, where the glass has one calm thing behind it; a dropdown covers the settings list
 * it was opened from, and every degree of transparency lets that list's rows and separators
 * ghost through the option text. In the light theme the accent wash alone composites to a
 * pale blue-grey over white, which left white option labels reading at about 2:1 — legible
 * as shapes, not as words. Backing the pane with its own surface makes the popup an opaque
 * material with the glass's rim and highlight still on top, so the labels are read against
 * one known fill instead of against whatever is underneath.
 *
 * Not 1f: the visible hair of the page at the edges is what keeps a floating menu reading as
 * glass rather than as a `Surface`, and [ttGlassSurface] uses [ttGlass.LENS_ALPHA] as its
 * white wash on top of a base, so the last few percent of the fill is already covered.
 */
internal const val DROPDOWN_BASE_ALPHA: Float = 0.94f
