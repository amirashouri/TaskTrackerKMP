package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIsDarkTheme
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.UserPosition
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_position_label
import org.jetbrains.compose.resources.stringResource

const val SettingsPositionRowTag = "settings.positionRow"

/**
 * The position picker, drawn in the theme dropdown's vocabulary: icon well, label, the current
 * value right-aligned under the same chevron, and the same Popup.
 */
@Composable
internal fun UserPositionPickerRow(
    selected: UserPosition?,
    onSelect: (UserPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                ) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = ttSettingsIcons.account, tint = ttAccent())
            ttText(
                text = stringResource(Res.string.settings_position_label),
                style = ttTypography.body,
                color = ttLabel(),
            )
            Spacer(modifier = Modifier.weight(1f))
            ttText(
                text = selected?.displayName ?: "—",
                style = ttTypography.subheadline,
                color = ttLabelSecondary(),
            )
            ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier.size(ttSpacing.sm * 1.5f),
            )
        }

        if (expanded) {
            Popup(
                popupPositionProvider = SettingsDropdownPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                SettingsDropdownColumn {
                    UserPosition.all.forEach { position ->
                        UserPositionOption(
                            position = position,
                            isSelected = position == selected,
                            onClick = {
                                onSelect(position)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPositionOption(
    position: UserPosition,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isDarkTheme = ttIsDarkTheme()
    val optionLabel = if (isDarkTheme) ttLabel() else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropdownOptionSurface(isSelected = isSelected)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = position.displayName,
            style = ttTypography.body,
            color = if (isSelected && isDarkTheme) ttAccent() else optionLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}
