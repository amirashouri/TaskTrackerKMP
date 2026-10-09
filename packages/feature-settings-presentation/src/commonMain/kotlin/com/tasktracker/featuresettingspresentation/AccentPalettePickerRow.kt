package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassLens
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSelectionLabelInk
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography

@Composable
internal fun AccentPalettePickerRow(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = AccentPalette.pickerTitle,
            style = ttTypography.subheadline,
            color = ttLabel(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
            AccentPalette.all.forEach { palette ->
                val isSelected = palette == selected
                val cellShape = RoundedCornerShape(ttRadius.control)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(cellShape)
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            onSelect(palette)
                        },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
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
                    // The lens is a sibling drawn *after* the swatch and the label, so one pane
                    // covers both rather than just the circle. `matchParentSize` keeps it off the
                    // cell's measurement, so the rectangle is the cell and the cell is sized by
                    // its own content.
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .testTag(AccentGlassTag(palette))
                                .ttGlassLens(
                                    shape = cellShape,
                                    washAlpha = ttGlass.SOLID_LENS_ALPHA,
                                    rimAlpha = ttGlass.SOLID_LENS_RIM_ALPHA,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** The tag for one palette's swatch, so a rendered test can read the glass lens off it. */
internal fun AccentSwatchTag(palette: AccentPalette): String = "settings.accent.${palette.wireValue}"

/** The tag for one palette's glass pane, so a test can read the rectangle's coverage. */
internal fun AccentGlassTag(palette: AccentPalette): String = "settings.accent.glass.${palette.wireValue}"

/** The tag for one palette's label, so a test can prove the pane reaches the text. */
internal fun AccentLabelTag(palette: AccentPalette): String = "settings.accent.label.${palette.wireValue}"
