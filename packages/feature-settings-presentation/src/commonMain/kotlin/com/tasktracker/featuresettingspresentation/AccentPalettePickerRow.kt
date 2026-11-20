package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassLens
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLensPlacement
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSelectionLabelInk
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_accent_label
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun AccentPalettePickerRow(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
    showNames: Boolean = true,
    tagPrefix: String = ACCENT_TAG_PREFIX,
) {
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<AccentPalette, IntRect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lens = ttLensPlacement(positions, selected, density)
    // With names the labels need three columns on a phone. Without them six swatches fit one
    // row, which is what the sidebar's compact panel wants — the lens and the hit targets stay
    // the same, only the caption goes away.
    val columns = if (showNames) GRID_COLUMNS else AccentPalette.all.size

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = stringResource(Res.string.settings_accent_label),
            style = ttTypography.subheadline,
            color = ttLabel(),
        )
        // One glass slab wrapping the whole row, with a lens that travels between palettes —
        // the same moving-lens grammar as the segmented picker and the flow picker, and the
        // reason it was not already here: this row used `matchParentSize()` on the selected
        // cell, which is a static pane. A lens that sits still while the selection changes is
        // a highlight that appears; one that travels, widens, and settles is material being
        // displaced, which is the whole point of the treatment.
        //
        // The slab is one element rather than one per cell for the same reason the segmented
        // picker uses one: without a `GlassEffectContainer` there is nothing to merge
        // neighbouring materials, so per-cell glass reads as six separate pills in a row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .ttGlassSurface(
                    shape = RoundedCornerShape(ttRadius.control),
                    borderWidth = ttGlass.hairlineWidth,
                )
                .padding(ttSpacing.hairlineInset)
                .onGloballyPositioned { containerCoordinates = it },
        ) {
            // Three columns rather than six: at 320dp a six-cell row left roughly 48dp per
            // cell, and two-line labels with an ellipsis were the only way "Classic Light"
            // could fit. Two rows of three give each label about 96dp, so it reads in full on
            // one line, and the cell grows to roughly 96x70dp — well past the 44dp target.
            Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
                AccentPalette.all.chunked(columns).forEach { rowPalettes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowPalettes.forEach { palette ->
                            AccentCell(
                                palette = palette,
                                isSelected = palette == selected,
                                onSelect = onSelect,
                                positions = positions,
                                containerCoordinates = containerCoordinates,
                                showNames = showNames,
                                tagPrefix = tagPrefix,
                            )
                        }
                        // A short row is padded so its cells keep the same width as the rows
                        // above; in the compact one-row grid there is no short row to pad.
                        repeat(columns - rowPalettes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            // The lens is a sibling drawn *after* the swatch and the label, so one pane covers
            // both rather than just the circle. It travels between palettes with the same
            // spring the segmented and flow pickers use; see [ttLensPlacement] for the shared
            // four-value animation and why the values are animated rather than computed once.
            Box(
                modifier = Modifier
                    .offset(x = lens.x, y = lens.y)
                    .size(lens.width, lens.height)
                    .testTag(AccentGlassTag(selected, tagPrefix))
                    .ttGlassLens(
                        shape = RoundedCornerShape(ttRadius.control),
                        washAlpha = ttGlass.SOLID_LENS_ALPHA,
                        rimAlpha = ttGlass.SOLID_LENS_RIM_ALPHA,
                    ),
            )
        }
    }
}

/**
 * One palette cell, as a [RowScope] extension so `weight` means the row's width.
 *
 * This is not cosmetic. Written inline inside the row's `forEach`, both this row and the
 * outer `Column` are implicit receivers with a `weight` member, and the Column's resolved:
 * it means height there, so each cell measured at the row's full width and the three cells
 * sat on top of one another. The receiver on this function makes the row's weight
 * unambiguous, which is also why the lens rectangle is a cell rather than the whole row.
 */
@Composable
private fun RowScope.AccentCell(
    palette: AccentPalette,
    isSelected: Boolean,
    onSelect: (AccentPalette) -> Unit,
    positions: MutableMap<AccentPalette, IntRect>,
    containerCoordinates: LayoutCoordinates?,
    showNames: Boolean,
    tagPrefix: String,
) {
    val cellShape = RoundedCornerShape(ttRadius.control)
    Box(
        modifier = Modifier
            .weight(1f)
            .then(if (showNames) Modifier else Modifier.height(MIN_COMPACT_CELL_HEIGHT))
            .testTag(AccentCellTag(palette, tagPrefix))
            .clip(cellShape)
            .selectable(selected = isSelected, role = Role.RadioButton) {
                onSelect(palette)
            }
            .onGloballyPositioned { coordinates ->
                if (containerCoordinates != null) {
                    val position = containerCoordinates.localPositionOf(coordinates, Offset.Zero)
                    positions[palette] = IntRect(
                        left = position.x.roundToInt(),
                        top = position.y.roundToInt(),
                        right = position.x.roundToInt() + coordinates.size.width,
                        bottom = position.y.roundToInt() + coordinates.size.height,
                    )
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showNames) {
                        Modifier.padding(vertical = ttSpacing.sm)
                    } else {
                        // Arrangement.Center only has an effect if this column occupies the cell's
                        // full 44dp; wrapping to the swatch's 24dp left the swatch high and the
                        // travelling lens a few dp below it.
                        Modifier.fillMaxHeight()
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (showNames) {
                Arrangement.spacedBy(ttSpacing.xs)
            } else {
                // With no caption below it, the swatch belongs in the middle of the 44dp cell;
                // leaving it top-aligned made the travelling lens read as offset downward.
                Arrangement.Center
            },
        ) {
            Box(
                modifier = Modifier
                    .size(if (showNames) 32.dp else COMPACT_SWATCH_SIZE)
                    .testTag(AccentSwatchTag(palette, tagPrefix))
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(palette.light, palette.dark)),
                    )
                    .then(
                        if (isSelected) {
                            Modifier
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = ttSeparator(),
                                shape = CircleShape,
                            )
                        },
                    ),
            )
            if (showNames) {
                ttText(
                    text = palette.title,
                    modifier = Modifier.testTag(AccentLabelTag(palette, tagPrefix)),
                    style = ttTypography.footnote,
                    color = ttSelectionLabelInk(isSelected),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val GRID_COLUMNS = 3
private const val ACCENT_TAG_PREFIX = "settings.accent"
private val COMPACT_SWATCH_SIZE = 24.dp
private val MIN_COMPACT_CELL_HEIGHT = 44.dp

/** The tag for one palette's whole tappable cell, so its 44dp target can be measured. */
internal fun AccentCellTag(
    palette: AccentPalette,
    tagPrefix: String = ACCENT_TAG_PREFIX,
): String = "$tagPrefix.cell.${palette.wireValue}"

/** The tag for one palette's swatch, so a rendered test can read the glass lens off it. */
internal fun AccentSwatchTag(
    palette: AccentPalette,
    tagPrefix: String = ACCENT_TAG_PREFIX,
): String = "$tagPrefix.${palette.wireValue}"

/** The tag for one palette's glass pane, so a test can read the rectangle's coverage. */
internal fun AccentGlassTag(
    palette: AccentPalette,
    tagPrefix: String = ACCENT_TAG_PREFIX,
): String = "$tagPrefix.glass.${palette.wireValue}"

/** The tag for one palette's label, so a test can prove the pane reaches the text. */
internal fun AccentLabelTag(
    palette: AccentPalette,
    tagPrefix: String = ACCENT_TAG_PREFIX,
): String = "$tagPrefix.label.${palette.wireValue}"
