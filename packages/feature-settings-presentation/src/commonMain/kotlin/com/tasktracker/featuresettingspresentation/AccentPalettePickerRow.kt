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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ttRadius.control))
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            onSelect(palette)
                        }
                        .padding(vertical = ttSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(listOf(palette.light, palette.dark)),
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ttAccent() else ttSeparator(),
                                shape = CircleShape,
                            ),
                    )
                    ttText(
                        text = palette.title,
                        style = ttTypography.footnote,
                        color = if (isSelected) ttAccent() else ttLabelSecondary(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
