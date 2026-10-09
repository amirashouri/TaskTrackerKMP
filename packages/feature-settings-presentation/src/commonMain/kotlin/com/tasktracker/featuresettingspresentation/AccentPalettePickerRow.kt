package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlin.math.roundToInt

@Composable
internal fun AccentPalettePickerRow(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
) {
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<AccentPalette, IntRect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lens = ttLensPlacement(positions, selected, density)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = AccentPalette.pickerTitle,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentPalette.all.forEach { palette ->
                    val isSelected = palette == selected
                    val cellShape = RoundedCornerShape(ttRadius.control)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(cellShape)
                            .selectable(selected = isSelected, role = Role.RadioButton) {
                                onSelect(palette)
                            }
                            .onGloballyPositioned { coordinates ->
                                val parent = containerCoordinates
                                if (parent != null) {
                                    val position = parent.localPositionOf(coordinates, Offset.Zero)
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
                                .padding(vertical = ttSpacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag(AccentSwatchTag(palette))
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
                            ttText(
                                text = palette.title,
                                modifier = Modifier.testTag(AccentLabelTag(palette)),
                                style = ttTypography.footnote,
                                color = ttSelectionLabelInk(isSelected),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
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
                    .testTag(AccentGlassTag(selected))
                    .ttGlassLens(
                        shape = RoundedCornerShape(ttRadius.control),
                        washAlpha = ttGlass.SOLID_LENS_ALPHA,
                        rimAlpha = ttGlass.SOLID_LENS_RIM_ALPHA,
                    ),
            )
        }
    }
}

/** The tag for one palette's swatch, so a rendered test can read the glass lens off it. */
internal fun AccentSwatchTag(palette: AccentPalette): String = "settings.accent.${palette.wireValue}"

/** The tag for one palette's glass pane, so a test can read the rectangle's coverage. */
internal fun AccentGlassTag(palette: AccentPalette): String = "settings.accent.glass.${palette.wireValue}"

/** The tag for one palette's label, so a test can prove the pane reaches the text. */
internal fun AccentLabelTag(palette: AccentPalette): String = "settings.accent.label.${palette.wireValue}"
